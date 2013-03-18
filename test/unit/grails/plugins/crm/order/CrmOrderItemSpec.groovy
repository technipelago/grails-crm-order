package grails.plugins.crm.order

import spock.lang.Specification

/**
 * Created with IntelliJ IDEA.
 * User: goran
 * Date: 2013-03-18
 * Time: 20:58
 * To change this template use File | Settings | File Templates.
 */
class CrmOrderItemSpec extends Specification {

    def "CrmOrderItem#getPriceVAT"() {
        when:
        def item = new CrmOrderItem(quantity: 5, price: 100, vat: 0.25)

        then: "price per unit + VAT"
        item.priceVAT == 125

        when:
        item.vat = 0

        then: "no VAT"
        item.priceVAT == 100

        when:
        item.vat = null

        then: "no VAT"
        item.priceVAT == 100
    }

    def "CrmOrderItem#getDiscountPrice"() {
        when:
        def item = new CrmOrderItem(quantity: 5, price: 100, discount: 0.5, vat: 0.25)

        then: "price per unit - discount"
        item.discountPrice == 50

        when:
        item.vat = 0

        then: "VAT should not matter"
        item.discountPrice == 50

        when:
        item.quantity = 2

        then: "quantity should not matter"
        item.discountPrice == 50

        when:
        item.discount = 10

        then: "10 (amount, not percent) off original price"
        item.discountPrice == 90
    }

    def "CrmOrderItem#getDiscountPriceVAT"() {
        when:
        def item = new CrmOrderItem(quantity: 5, price: 100, discount: 0.5, vat: 0.25)

        then: "price per unit - discount + VAT"
        item.discountPriceVAT == 62.50

        when:
        item.vat = 0

        then: "no VAT"
        item.discountPriceVAT == 50

        when:
        item.quantity = 2
        item.vat = 0.25

        then: "quantity should not matter"
        item.discountPriceVAT == 62.50

        when:
        item.discount = 10

        then: "10 (amount, not percent) off original price"
        item.discountPriceVAT == 112.50
    }
}
