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
import grails.plugins.crm.core.DateUtils
import grails.plugins.crm.core.SearchUtils
import grails.plugins.crm.core.TenantUtils
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod

/**
 * Order management features.
 */
class CrmOrderService {

    def crmTagService

    @Listener(namespace = "crmOrder", topic = "enableFeature")
    def enableFeature(event) {
        // event = [feature: feature, tenant: tenant, role:role, expires:expires]
        TenantUtils.withTenant(event.tenant) {
            crmTagService.createTag(name: CrmOrder.name, multiple: true)
            createOrderType(name: "Order", true)
            createOrderStatus(name: "Order", param: 'order', true)
            createOrderStatus(name: "Payed", param: 'payed', true)
            createOrderStatus(name: "Delivered", param: 'delivered', true)
            createOrderStatus(name: "Cancelled", param: 'cancel', true)
            createDeliveryType(name: "Standard", param: 'default', true)
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
                    eq('customerRef', query.customer)
                    eq('customerEmail', query.customer)
                }
            }
            if (params.fromDate && params.toDate) {
                def d1 = DateUtils.parseSqlDate(params.fromDate)
                def d2 = DateUtils.parseSqlDate(params.toDate)
                between('orderDate', d1, d2)
            } else if (params.fromDate) {
                def d1 = DateUtils.parseSqlDate(params.fromDate)
                ge('orderDate', d1)
            } else if (params.toDate) {
                def d2 = DateUtils.parseSqlDate(params.toDate)
                le('orderDate', d2)
            }
        }
    }

    CrmOrder createOrder(Map params, boolean save = false) {
        def tenant = TenantUtils.tenant
        def m = new CrmOrder()
        def args = [m, params]
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

    void cancelOrder(CrmOrder order) {
        throw new UnsupportedOperationException("CrmOrderService#cancelOrder() not implemented")
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
}
