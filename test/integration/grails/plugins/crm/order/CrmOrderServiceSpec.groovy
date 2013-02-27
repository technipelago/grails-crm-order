package grails.plugins.crm.order

/**
 * Tests for CrmOrderService.
 */
class CrmOrderServiceSpec extends grails.plugin.spock.IntegrationSpec {

    def crmOrderService

    def "create order"() {
        given:
        def t = crmOrderService.createOrderType(name: "Web Order", true)
        def s = crmOrderService.createOrderStatus(name: "Order", true)

        when:
        def o = crmOrderService.createOrder(customer: "ACME Inc.", orderType: t, orderStatus: s, true)

        then:
        o.ident() != null

        when:
        crmOrderService.addOrderItem(o, [orderIndex: 1, productId: "water", productName: "Fresh water", unit: "l", quantity: 10, price: 10, vat: 0.25], false)
        crmOrderService.addOrderItem(o, [orderIndex: 2, productId: "air", productName: "Fresh air", unit: "m3", quantity: 100, price: 1, vat: 0.25], false)

        then:
        o.items.size() == 2
        o.totalAmount == 200
        o.totalVat == 50
    }
}
