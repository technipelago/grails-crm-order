/*
 * Copyright (c) 2012 Goran Ehrsson.
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
import grails.plugins.crm.core.SearchUtils
import grails.plugins.crm.core.TenantUtils
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod

/**
 * Order management features.
 */
class CrmOrderService {

    def grailsApplication
    def crmCoreService
    def crmTagService
    def sequenceGeneratorService

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
    def list(Map params = [:]) {
        list([:], params)
    }

    /**
     * Find CrmOrder instances filtered by query.
     *
     * @param query filter parameters
     * @param params pagination parameters
     * @return List or CrmOrder domain instances
     */
    def list(Map query, Map params) {
        def tagged

        if (query.tags) {
            tagged = crmTagService.findAllByTag(CrmOrder, query.tags).collect { it.id }
            if (!tagged) {
                tagged = [0L] // Force no search result.
            }
        }

        CrmOrder.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (tagged) {
                inList('id', tagged)
            }
            if (query.number) {
                ilike('number', SearchUtils.wildcard(query.number))
            }
            if (query.customer) {
                or {
                    eq('customerFirstName', query.customer)
                    eq('customerLastName', query.customer)
                    eq('customerCompany', query.customer)
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
            if(query.reference) {
                eq('customerRef', crmCoreService.getReferenceIdentifier(query.reference))
            }
        }
    }

    CrmOrder getOrder(Long id) {
        CrmOrder.get(id)
    }

    CrmOrder findByNumber(String number, Long tenant = null) {
        if (tenant == null) {
            tenant = TenantUtils.tenant
        }
        CrmOrder.findByNumberAndTenantId(number, tenant)
    }

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

    List<CrmOrderType> listOrderType(Boolean enabled = null, CrmOrderType mandatory = null, Map params = [:]) {
        def result = CrmOrderType.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (enabled != null) {
                eq('enabled', enabled)
            }
        }
        if (mandatory && !result.contains(mandatory)) {
            result << mandatory
        }
        result
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

    List<CrmOrderStatus> listOrderStatus(Boolean enabled = null, CrmOrderStatus mandatory = null, Map params = [:]) {
        def result = CrmOrderStatus.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (enabled != null) {
                eq('enabled', enabled)
            }
        }
        if (mandatory && !result.contains(mandatory)) {
            result << mandatory
        }
        result
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

    List<CrmDeliveryType> listDeliveryType(Boolean enabled = null, CrmDeliveryType mandatory = null, Map params = [:]) {
        def result = CrmDeliveryType.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (enabled != null) {
                eq('enabled', enabled)
            }
        }
        if (mandatory && !result.contains(mandatory)) {
            result << mandatory
        }
        result
    }
}
