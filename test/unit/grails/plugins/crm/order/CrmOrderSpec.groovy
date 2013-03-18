package grails.plugins.crm.order

import grails.test.mixin.Mock
import spock.lang.Specification

/**
 * Test spec for CrmOrder
 */
@Mock([CrmOrder, CrmOrderItem])
class CrmOrderSpec extends Specification {

    def "test total amount"() {
        when:
        def order = new CrmOrder(totalAmount: 100, totalVat: 25)

        then:
        order.totalAmountVAT == 125
    }


    def "test discount"() {
        given:
        def order = new CrmOrder(number: 1234)
        order.addToItems(new CrmOrderItem(quantity: 2, discount: 0.5, price: 100, vat: 0.25))
        order.addToItems(new CrmOrderItem(quantity: 9, price: 100, vat: 0.25))

        when:
        order.beforeValidate()

        then: "make sure getTotalAmountVAT() works ok"
        order.totalAmount == 1000
        order.totalVat == 250
        order.totalAmountVAT == 1250
        order.totalDiscount == 100
        order.totalDiscountVAT == 125
    }
}
