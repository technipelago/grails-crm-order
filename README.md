# Grails CRM - Order Management Plugin

CRM = [Customer Relationship Management](http://en.wikipedia.org/wiki/Customer_relationship_management)

Grails CRM is a set of [Grails Web Application Framework](http://www.grails.org/)
plugins that makes it easy to develop web application with CRM functionality.
With CRM we mean features like:

- Contact Management
- Task/Todo Lists
- Project Management


## Order Management for Grails CRM
This plugin provides the "headless" part of Grails CRM order management (i.e domains and services).
The companion plugin **crm-order-ui** provides user the interface for order management.

## Examples

    def type = crmOrderService.createOrderType(name: "Web Order", true)
    def status = crmOrderService.createOrderStatus(name: "Order", true)
    def order = crmOrderService.createOrder(orderType: type, orderStatus: status,
        customerCompany: "ACME Inc.", customerFirstName: "Joe", customerLastName: "Average",
        'invoice.addressee': 'ACME Financials', 'invoice.postalCode': '12345', 'invoice.city': 'Groovytown',
        'delivery.addressee': 'ACME Laboratories', 'delivery.postalCode': '12355', 'delivery.city': 'Groovytown', true)
    crmOrderService.addOrderItem(order, [productId: "pc", productName: "Personal Computer", quantity: 10, price: 499, vat: 0.15], true)
