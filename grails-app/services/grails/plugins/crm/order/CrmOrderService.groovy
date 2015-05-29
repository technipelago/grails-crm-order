/*
 * Copyright (c) 2014 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.order

import grails.events.Listener
import grails.plugins.crm.core.CrmEmbeddedAddress
import grails.plugins.crm.core.DateUtils
import grails.plugins.crm.core.PagedResultList
import grails.plugins.crm.core.SearchUtils
import grails.plugins.crm.core.TenantUtils
import grails.plugins.crm.core.CrmValidationException
import grails.plugins.selection.Selectable
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod

/**
 * Order management features.
 */
class CrmOrderService {

    def grailsApplication
    def crmCoreService
    def crmTagService
    def crmSecurityService
    def sequenceGeneratorService
    def messageSource

    @Listener(namespace = "crmOrder", topic = "enableFeature")
    def enableFeature(event) {
        // event = [feature: feature, tenant: tenant, role:role, expires:expires]
        def tenant = event.tenant
        TenantUtils.withTenant(tenant) {
            // Initialize a number sequence for CrmOrder
            def config = grailsApplication.config.crm.order.sequence
            def start = config.start ?: 1
            def format = config.format ?: "%s"
            sequenceGeneratorService.initSequence(CrmOrder, null, tenant, start, format)

            // Create generic tag for CrmOrder
            crmTagService.createTag(name: CrmOrder.name, multiple: true)

            // Create default lookup codes.
            createOrderType(name: "Order", param: "order", true)
            createOrderStatus(name: "Order", param: 'order', true)
            createDeliveryType(name: "Standard", param: 'default', true)

            log.debug "crmOrderService finished setup in tenant ${event.tenant}"
        }
    }

    @Listener(namespace = "crmTenant", topic = "requestDelete")
    def requestDeleteTenant(event) {
        def tenant = event.id
        def count = 0
        count += CrmOrder.countByTenantId(tenant)
        count += CrmOrderStatus.countByTenantId(tenant)
        count += CrmOrderType.countByTenantId(tenant)
        count += CrmDeliveryType.countByTenantId(tenant)
        count ? [namespace: 'crmOrder', topic: 'deleteTenant'] : null
    }

    @Listener(namespace = "crmOrder", topic = "deleteTenant")
    def deleteTenant(event) {
        def tenant = event.id
        def count = CrmOrder.countByTenantId(tenant)
        // Remove all campaigns
        CrmOrder.findAllByTenantId(tenant)*.delete()
        // Remove types and statuses.
        CrmOrderStatus.findAllByTenantId(tenant)*.delete()
        CrmOrderType.findAllByTenantId(tenant)*.delete()
        CrmDeliveryType.findAllByTenantId(tenant)*.delete()
        log.warn("Deleted $count campaigns in tenant $tenant")
    }

    /**
     * Empty query = search all records.
     *
     * @param params pagination parameters
     * @return List or CrmOrder domain instances
     */
    @Selectable
    def list(Map params = [:]) {
        executeCriteria('list', [:], params)
    }

    /**
     * Find CrmOrder instances filtered by query.
     *
     * @param query filter parameters
     * @param params pagination parameters
     * @return List or CrmOrder domain instances
     */
    @Selectable
    def list(Map query, Map params) {
        executeCriteria('list', query, params)
    }

    /**
     * Count number of CrmOrder instances filtered by query.
     *
     * @param query filter parameters
     * @return number of CrmOrder
     */
    def count(Map query = [:]) {
        executeCriteria('count', query, null)
    }

    private Object executeCriteria(String criteriaMethod, Map query, Map params) {
        def tagged

        if (query.tags) {
            tagged = crmTagService.findAllByTag(CrmOrder, query.tags).collect { it.id }
            if (!tagged) {
                // No need to continue with the query if tags don't match.
                return new PagedResultList([])
            }
        }

        final Closure criteria = {
            eq('tenantId', TenantUtils.tenant)
            if (query.id) {
                eq('id', Long.valueOf(query.id))
            } else if(tagged) {
                inList('id', tagged)
            }
            if (query.number) {
                eq('number', query.number)
            }
            if (query.customer) {
                if (crmCoreService.isDomainClass(query.customer)
                        || crmCoreService.isDomainReference(query.customer)) {
                    eq('customerRef', crmCoreService.getReferenceIdentifier(query.customer))
                } else {
                    or {
                        ilike('customerFirstName', SearchUtils.wildcard(query.customer))
                        ilike('customerLastName', SearchUtils.wildcard(query.customer))
                        ilike('customerCompany', SearchUtils.wildcard(query.customer))
                    }
                }
            }
            if (query.address) {
                or {
                    ilike('invoice.address1', SearchUtils.wildcard(query.address))
                    ilike('invoice.address2', SearchUtils.wildcard(query.address))
                    ilike('invoice.postalCode', SearchUtils.wildcard(query.address))
                    ilike('invoice.city', SearchUtils.wildcard(query.address))

                    ilike('delivery.address1', SearchUtils.wildcard(query.address))
                    ilike('delivery.address2', SearchUtils.wildcard(query.address))
                    ilike('delivery.postalCode', SearchUtils.wildcard(query.address))
                    ilike('delivery.city', SearchUtils.wildcard(query.address))
                }
            }
            if (query.email) {
                ilike('customerEmail', SearchUtils.wildcard(query.email))
            }
            if (query.telephone) {
                ilike('customerTel', SearchUtils.wildcard(query.telephone))
            }
            if (query.campaign) {
                ilike('campaign', SearchUtils.wildcard(query.campaign))
            }
            if (query.fromDate && query.toDate) {
                def d1 = DateUtils.parseSqlDate(query.fromDate)
                def d2 = DateUtils.parseSqlDate(query.toDate)
                between('orderDate', d1, d2)
            } else if (query.fromDate) {
                def d1 = DateUtils.parseSqlDate(query.fromDate)
                ge('orderDate', d1)
            } else if (query.toDate) {
                def d2 = DateUtils.parseSqlDate(query.toDate)
                le('orderDate', d2)
            }
            if (query.type) {
                orderType {
                    or {
                        eq('param', query.type)
                        ilike('name', SearchUtils.wildcard(query.type))
                    }
                }
            }

            if (query.status) {
                orderStatus {
                    or {
                        eq('param', query.status)
                        ilike('name', SearchUtils.wildcard(query.status))
                    }
                }
            }

            if (query.delivery) {
                deliveryType {
                    or {
                        eq('param', query.delivery)
                        ilike('name', SearchUtils.wildcard(query.delivery))
                    }
                }
            }
        }

        switch (criteriaMethod) {
            case 'count':
                return CrmOrder.createCriteria().count(criteria)
            case 'get':
                return CrmOrder.createCriteria().get(params, criteria)
            default:
                return CrmOrder.createCriteria().list(params, criteria)
        }
    }

    CrmOrder getOrder(Long id, Long tenant = null) {
        if (tenant == null) {
            tenant = TenantUtils.tenant
        }
        CrmOrder.findByIdAndTenantId(id, tenant)
    }

    CrmOrder findByNumber(String number, Long tenant = null) {
        if (tenant == null) {
            tenant = TenantUtils.tenant
        }
        CrmOrder.findByNumberAndTenantId(number, tenant)
    }

    private CrmOrder useOrderInstance(CrmOrder crmOrder = null) {
        def tenant = TenantUtils.tenant
        if (crmOrder == null) {
            crmOrder = new CrmOrder()
        }
        if (crmOrder.tenantId) {
            if (crmOrder.tenantId != tenant) {
                throw new IllegalStateException("The current tenant is [$tenant] and the specified domain instance belongs to another tenant [${crmOrder.tenantId}]")
            }
        } else {
            crmOrder.tenantId = tenant
        }
        crmOrder
    }

    CrmOrder saveOrder(CrmOrder crmOrder, Map params) {
        crmOrder = useOrderInstance(crmOrder)
        def tenant = TenantUtils.tenant
        def currentUser = crmSecurityService.getUserInfo()
        def oldStatus = crmOrder.orderStatus

        try {
            bindDate(crmOrder, 'orderDate', params.remove('orderDate'), currentUser?.timezone)
            bindDate(crmOrder, 'deliveryDate', params.remove('deliveryDate'), currentUser?.timezone)
        } catch (CrmValidationException e) {
            throw new CrmValidationException(e.message, crmOrder)
        }

        // Bind "normal" properties.
        def args = [crmOrder, params, [include: CrmOrder.BIND_WHITELIST]]
        new BindDynamicMethod().invoke(crmOrder, 'bind', args.toArray())

        // Bind invoice address
        if (!crmOrder.invoice) {
            crmOrder.invoice = new CrmEmbeddedAddress()
        }
        if(params.invoice instanceof Map) {
            args = [crmOrder.invoice, params.invoice, [include: ['addressee'] + CrmEmbeddedAddress.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(crmOrder.invoice, 'bind', args.toArray())
        } else {
            args = [crmOrder.invoice, params, 'invoice']
            new BindDynamicMethod().invoke(crmOrder.invoice, 'bind', args.toArray())
        }

        // Bind delivery address.
        if (!crmOrder.delivery) {
            crmOrder.delivery = new CrmEmbeddedAddress()
        }
        if(params.delivery instanceof Map) {
            args = [crmOrder.delivery, params.delivery, [include: ['addressee'] + CrmEmbeddedAddress.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(crmOrder.delivery, 'bind', args.toArray())
        } else {
            args = [crmOrder.delivery, params, 'delivery']
            new BindDynamicMethod().invoke(crmOrder.delivery, 'bind', args.toArray())
        }

        if (!crmOrder.invoice.addressee) {
            crmOrder.invoice.addressee = crmOrder.customerName
        }

        // If delivery address is empty, copy invoice address (if configured to do so).
        if (crmOrder.delivery.empty && grailsApplication.config.crm.order.delivery.address.copy == 'invoice') {
            crmOrder.invoice.copyTo(crmOrder.delivery)
            if (!crmOrder.delivery.addressee) {
                crmOrder.delivery.addressee = crmOrder.invoice.addressee ?: crmOrder.customerName
            }
        }

        // Bind items.
        if(params.items instanceof List) {
            for(row in params.items) {
                def item = new CrmOrderItem(order: crmOrder)
                args = [item, row, [include: CrmOrderItem.BIND_WHITELIST]]
                new BindDynamicMethod().invoke(item, 'bind', args.toArray())
                if(!item.hasErrors()) {
                    crmOrder.addToItems(item)
                }
            }
        } else {
            args = [crmOrder, params, 'items']
            new BindDynamicMethod().invoke(crmOrder, 'bind', args.toArray())
        }
        if (!crmOrder.username) {
            crmOrder.username = currentUser?.username
        }

        if (!crmOrder.orderStatus) {
            crmOrder.orderStatus = CrmOrderStatus.withNewSession {
                CrmOrderStatus.createCriteria().get() {
                    eq('tenantId', tenant)
                    order 'orderIndex', 'asc'
                    maxResults 1
                }
            }
        }
        if (!crmOrder.orderType) {
            crmOrder.orderType = CrmOrderType.withNewSession {
                CrmOrderType.createCriteria().get() {
                    eq('tenantId', tenant)
                    order 'orderIndex', 'asc'
                    maxResults 1
                }
            }
        }
        if (!crmOrder.deliveryType) {
            crmOrder.deliveryType = CrmDeliveryType.withNewSession {
                CrmDeliveryType.createCriteria().get() {
                    eq('tenantId', tenant)
                    order 'orderIndex', 'asc'
                    maxResults 1
                }
            }
        }

        //if (!crmOrder.currency) {
        //    crmOrder.currency = grailsApplication.config.crm.currency.default ?: "EUR"
        //}

        if (!crmOrder.orderDate) {
            crmOrder.orderDate = new java.sql.Date(System.currentTimeMillis())
        }

        // If the order is new or it's status has changed, set the EVENT_CHANGED flag.
        if (grailsApplication.config.crm.order.changeEvent) {
            def newStatus = crmOrder.orderStatus
            if (crmOrder.id == null || oldStatus != newStatus) {
                crmOrder.event = CrmOrder.EVENT_CHANGED
            }
        }

        if (crmOrder.save()) {
            return crmOrder
        } else {
            // Eager fetch associations to avoid LazyInitializationException
            crmOrder.items?.size()
        }

        throw new CrmValidationException('crmOrder.validation.error', crmOrder)
    }

    private void bindDate(def target, String property, Object value, TimeZone timezone = null) {
        if (value) {
            def tenant = crmSecurityService.getCurrentTenant()
            def locale = tenant?.localeInstance ?: Locale.getDefault()
            try {
                if (value instanceof Date) {
                    target[property] = new java.sql.Date(value.time)
                } else {
                    target[property] = DateUtils.parseSqlDate(value.toString(), timezone)
                }
            } catch (Exception e) {
                def entityName = messageSource.getMessage('crmOrder.label', null, 'Order', locale)
                def propertyName = messageSource.getMessage('crmOrder.' + property + '.label', null, property, locale)
                target.errors.rejectValue(property, 'default.invalid.date.message', [propertyName, entityName, value.toString(), e.message].toArray(), "Invalid date: {2}")
                throw new CrmValidationException('crmOrder.invalid.date.message', target)
            }
        } else {
            target[property] = null
        }
    }

    /**
     * Create a new CrmOrder instance.
     *
     * @deprecated use {@link #save(CrmOrder, Map)} instead
     * @param params property values
     * @param save true if the instance should be persisted immediately
     * @return the created order instance
     */
    CrmOrder createOrder(Map params, boolean save = false) {
        def tenant = TenantUtils.tenant
        def m = new CrmOrder(invoice: new CrmEmbeddedAddress(), delivery: new CrmEmbeddedAddress())

        // Bind invoice address
        def args = [m.invoice, params, 'invoice']
        new BindDynamicMethod().invoke(m.invoice, 'bind', args.toArray())

        // Bind delivery date
        args = [m.delivery, params, 'delivery']
        new BindDynamicMethod().invoke(m.delivery, 'bind', args.toArray())

        // Bind all other properties
        def date = params.orderDate
        if (date?.class == Date.class) {
            params.orderDate = new java.sql.Date(date.clearTime().time)
        }
        args = [m, params, [include: CrmOrder.BIND_WHITELIST]]
        new BindDynamicMethod().invoke(m, 'bind', args.toArray())

        m.tenantId = tenant

        if (save) {
            m.save()
        } else {
            m.validate()
            m.clearErrors()
        }
        return m
    }

    CrmOrderItem addOrderItem(CrmOrder order, Map params, boolean save = false) {
        def m = new CrmOrderItem(order: order)
        def args = [m, params]
        new BindDynamicMethod().invoke(m, 'bind', args.toArray())
        if (m.validate()) {
            order.addToItems(m)
            if (order.validate() && save) {
                order.save()
            }
        }
        return m
    }

    CrmOrderType getOrderType(String param) {
        CrmOrderType.findByParamAndTenantId(param, TenantUtils.tenant, [cache: true])
    }

    CrmOrderType createOrderType(Map params, boolean save = false) {
        if (!params.param) {
            params.param = StringUtils.abbreviate(params.name?.toLowerCase(), 20)
        }
        def tenant = TenantUtils.tenant
        def m = CrmOrderType.findByParamAndTenantId(params.param, tenant)
        if (!m) {
            m = new CrmOrderType()
            def args = [m, params, [include: CrmOrderType.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(m, 'bind', args.toArray())
            m.tenantId = tenant
            if (params.enabled == null) {
                m.enabled = true
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    List<CrmOrderType> listOrderType(String name, Map params = [:]) {
        CrmOrderType.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (name) {
                or {
                    ilike('name', SearchUtils.wildcard(name))
                    eq('param', name)
                }
            }
        }
    }

    CrmOrderStatus getOrderStatus(String param) {
        CrmOrderStatus.findByParamAndTenantId(param, TenantUtils.tenant, [cache: true])
    }

    CrmOrderStatus createOrderStatus(Map params, boolean save = false) {
        if (!params.param) {
            params.param = StringUtils.abbreviate(params.name?.toLowerCase(), 20)
        }
        def tenant = TenantUtils.tenant
        def m = CrmOrderStatus.findByParamAndTenantId(params.param, tenant)
        if (!m) {
            m = new CrmOrderStatus()
            def args = [m, params, [include: CrmOrderStatus.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(m, 'bind', args.toArray())
            m.tenantId = tenant
            if (params.enabled == null) {
                m.enabled = true
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    List<CrmOrderStatus> listOrderStatus(String name, Map params = [:]) {
        CrmOrderStatus.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (name) {
                or {
                    ilike('name', SearchUtils.wildcard(name))
                    eq('param', name)
                }
            }
        }
    }

    CrmDeliveryType getDeliveryType(String param) {
        CrmDeliveryType.findByParamAndTenantId(param, TenantUtils.tenant, [cache: true])
    }

    CrmDeliveryType createDeliveryType(Map params, boolean save = false) {
        if (!params.param) {
            params.param = StringUtils.abbreviate(params.name?.toLowerCase(), 20)
        }
        def tenant = TenantUtils.tenant
        def m = CrmDeliveryType.findByParamAndTenantId(params.param, tenant)
        if (!m) {
            m = new CrmDeliveryType()
            def args = [m, params, [include: CrmDeliveryType.BIND_WHITELIST]]
            new BindDynamicMethod().invoke(m, 'bind', args.toArray())
            m.tenantId = tenant
            if (params.enabled == null) {
                m.enabled = true
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    List<CrmDeliveryType> listDeliveryType(String name, Map params = [:]) {
        CrmDeliveryType.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (name) {
                or {
                    ilike('name', SearchUtils.wildcard(name))
                    eq('param', name)
                }
            }
        }
    }

    void orderPayed(final CrmOrder order, Double amount = null, String paymentType = null, String paymentId = null) {
        if(amount == null) {
            amount = order.totalAmountVAT
        }
        order.paymentDate = new Date()
        order.payedAmount = Math.round(amount)
        order.paymentStatus = isFullyPayed(order) ? CrmOrder.PAYMENT_STATUS_FULL : CrmOrder.PAYMENT_STATUS_PARTIAL
        order.paymentType = paymentType
        order.paymentId = paymentId

        order.setSyncPending() // Will trigger Visma/SPCS sync.

        order.save(flush: true)

        event(for: "crmOrder", topic: "payed", data: [tenant: order.tenantId, id: order.id])
    }

    private boolean isFullyPayed(final CrmOrder order) {
        Integer total = Math.round(order.totalAmountVAT ?: 0)
        Integer payed = (order.payedAmount ?: 0) + 1
        return payed >= total
    }
}
