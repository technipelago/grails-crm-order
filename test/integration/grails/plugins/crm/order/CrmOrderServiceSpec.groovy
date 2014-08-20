package grails.plugins.crm.order

import spock.lang.Shared

/**
 * Tests for CrmOrderService.
 */
class CrmOrderServiceSpec extends grails.plugin.spock.IntegrationSpec {

    def crmOrderService

    @Shared type
    @Shared status

    def setup() {
        type = crmOrderService.createOrderType(name: "Web Order", true)
        status = crmOrderService.createOrderStatus(name: "Order", true)
    }

    def "test list and count"() {
        when:
        crmOrderService.createOrder(orderDate: new Date(), customerCompany: "ACME Inc.", orderType: type, orderStatus: status,
                'invoice.addressee': 'ACME Financials', 'invoice.postalCode': '12345', 'invoice.city': 'Groovytown',
                'delivery.addressee': 'ACME Laboratories', 'delivery.postalCode': '12355', 'delivery.city': 'Groovytown',
                true)
        5.times {
            crmOrderService.createOrder(orderDate: new Date(), customerCompany: "Dummy Inc.", orderType: type, orderStatus: status,
                    'invoice.addressee': 'Dummy Financials', 'invoice.postalCode': '12345', 'invoice.city': 'Dummytown',
                    'delivery.addressee': 'Dummy Laboratories', 'delivery.postalCode': '12355', 'delivery.city': 'Dummytown',
                    true)
        }
        then:
        crmOrderService.count() == 6
        crmOrderService.count([customer:"Dummy"]) == 5
    }

    def "create order with discount"() {
        when:
        def o = crmOrderService.save(new CrmOrder(), [orderDate: new Date(), customerCompany: "ACME Inc.", orderType: type, orderStatus: status,
                'invoice.addressee': 'ACME Financials', 'invoice.postalCode': '12345', 'invoice.city': 'Groovytown',
                'delivery.addressee': 'ACME Laboratories', 'delivery.postalCode': '12355', 'delivery.city': 'Groovytown',
                campaign: "test"])

        then:
        !o.hasErrors()
        o.ident()
        o.orderDate
        o.invoice.postalCode == '12345'
        o.delivery.postalCode == '12355'
        o.totalAmount == 0
        o.totalVat == 0

        when:
        crmOrderService.addOrderItem(o, [orderIndex: 1, productId: "water", productName: "Fresh water", unit: "l", quantity: 10, price: 10, vat: 0.25], false)
        crmOrderService.addOrderItem(o, [orderIndex: 2, productId: "air", productName: "Fresh air", unit: "m3", quantity: 100, price: 1, vat: 0.25, discount: 0.5], false)
        crmOrderService.addOrderItem(o, [orderIndex: 3, productId: "food", productName: "Fresh food", unit: "kg", quantity: 2, price: 100, vat: 0.12], false)

        then:
        o.items.size() == 3
        o.totalAmount == 350
        o.totalVat == 61.5
        o.totalAmountVAT == 411.50
    }
}
