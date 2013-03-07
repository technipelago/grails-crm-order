package grails.plugins.crm.order

/**
 * Tests for CrmOrderService.
 */
class CrmOrderServiceSpec extends grails.plugin.spock.IntegrationSpec {

    def crmOrderService

    def "create order with discount"() {
        given:
        def t = crmOrderService.createOrderType(name: "Web Order", true)
        def s = crmOrderService.createOrderStatus(name: "Order", true)

        when:
        def o = crmOrderService.createOrder(orderDate: new Date(), customer: "ACME Inc.", orderType: t, orderStatus: s,
                'invoice.addressee': 'ACME Financials', 'invoice.postalCode': '12345', 'invoice.city': 'Groovytown',
                'delivery.addressee': 'ACME Laboratories', 'delivery.postalCode': '12355', 'delivery.city': 'Groovytown',
                campaign: "test",
                true)

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
