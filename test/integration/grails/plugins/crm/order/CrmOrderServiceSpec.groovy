package grails.plugins.crm.order

import grails.plugins.crm.core.CrmValidationException
import grails.test.spock.IntegrationSpec
import spock.lang.Shared

/**
 * Tests for CrmOrderService.
 */
class CrmOrderServiceSpec extends IntegrationSpec {

    def crmOrderService

    @Shared
            type
    @Shared
            status

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
        crmOrderService.count([customer: "Dummy"]) == 5
    }

    def "order address as keys"() {
        when:
        def o = crmOrderService.saveOrder(null, [orderDate: new Date(), customerCompany: "ACME Inc.", orderType: type, orderStatus: status,
                'invoice.addressee': 'ACME Financials', 'invoice.postalCode': '12345', 'invoice.city': 'Groovytown',
                'delivery.addressee': 'ACME Laboratories', 'delivery.postalCode': '12355', 'delivery.city': 'Groovytown'])


        then:
        !o.hasErrors()
        o.ident()
        o.invoice.postalCode == '12345'
        o.delivery.postalCode == '12355'
    }

    def "order address as Map"() {
        when:
        def o = crmOrderService.saveOrder(null, [orderDate: new Date(), customerCompany: "ACME Inc.", orderType: type, orderStatus: status,
                invoice: [addressee: 'ACME Financials', postalCode: '12345', city: 'Groovytown'],
                delivery: [addressee: 'ACME Laboratories', postalCode: '12355', city: 'Groovytown']])

        then:
        !o.hasErrors()
        o.ident()
        o.invoice.postalCode == '12345'
        o.delivery.postalCode == '12355'
    }

    def "order items as List of Maps"() {
        when:
        def o = crmOrderService.saveOrder(null, [orderDate: new Date(), customerCompany: "ACME Inc.", orderType: type, orderStatus: status,
                invoice: [addressee: 'ACME Financials', postalCode: '12345', city: 'Groovytown'],
                items: [[orderIndex: 1, productId: "water", productName: "Fresh water", unit: "l", quantity: 10, price: 10, vat: 0.25],
                        [orderIndex: 2, productId: "air", productName: "Fresh air", unit: "m3", quantity: 100, price: 1, vat: 0.25],
                        [orderIndex: 3, productId: "food", productName: "Fresh food", unit: "kg", quantity: 2, price: 100, vat: 0.12]]
        ])

        then:
        !o.hasErrors()
        o.ident()
        o.items.size() == 3
        o.items.find { it.productId == "water" }
        o.items.find { it.productId == "air" }
        o.items.find { it.productId == "food" }
    }

    def "create order with discount"() {
        when:
        def o = crmOrderService.saveOrder(null, [orderDate: new Date(), customerCompany: "ACME Inc.", orderType: type, orderStatus: status,
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

    def "validation failure"() {
        given:
        def type = crmOrderService.createOrderType(name: "Test", true)
        def status = crmOrderService.createOrderStatus(name: "Order", true)

        when:
        crmOrderService.saveOrder(null, [orderType: type, orderStatus: status,
                customerFirstName: "Joe", customerLastName: "Average", customerCompany: "Company Inc.",
                invoice: [address1: "Main Road 1234", postalCode: "12345", city: "Smallville"],
                customerTel: "+4685551234", customerEmail: "joe.average@company.com", currency: "SEK",
                items: [[orderIndex: 1, /*productId: "iPhone4s",*/ productName: "iPhone 4S 16 GB Black Unlocked",
                        unit: "item", quantity: 1, price: 3068.8, vat: 0.25]]])
        then: "productId is (intentionally) missing"
        def exception = thrown(CrmValidationException)
        exception.message == "crmOrder.validation.error"
    }

    def "set order to payed"() {
        when:
        def o = crmOrderService.saveOrder(null, [orderDate: new Date(), customerCompany: "ACME Inc.", orderType: type, orderStatus: status,
                                                 totalAmount: 1234.00, totalVat: 308.50,
                        'invoice.addressee': 'ACME Financials', 'invoice.postalCode': '12345', 'invoice.city': 'Groovytown',
                        'delivery.addressee': 'ACME Laboratories', 'delivery.postalCode': '12355', 'delivery.city': 'Groovytown'])
        then:
        o.totalAmountVAT == 1542.50
        o.payedAmount == 0.0
        o.paymentDate == null
        o.paymentStatus == CrmOrder.PAYMENT_STATUS_UNKNOWN
        o.paymentType == null
        o.paymentId == null

        when: "pay less than total amount minus 'crm.order.payment.margin' (default=0.5)"
        crmOrderService.orderPayed(o, 1540, "test", "42")

        then: "the order is only partially payed"
        o.payedAmount == 1540.0
        o.paymentDate != null
        o.paymentStatus == CrmOrder.PAYMENT_STATUS_PARTIAL
        o.paymentType == "test"
        o.paymentId == "42"

        when: "pay exactly on the margin"
        crmOrderService.orderPayed(o, 1542, "test", "42")

        then: "the order is considered fully payed (we give away 0.50)"
        o.payedAmount == 1542.0
        o.paymentStatus == CrmOrder.PAYMENT_STATUS_FULL
    }
}
