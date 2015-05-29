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

import grails.plugins.crm.core.CrmEmbeddedAddress
import grails.plugins.crm.core.AuditEntity
import grails.plugins.crm.core.TenantEntity
import grails.plugins.sequence.SequenceEntity
import grails.plugins.crm.core.Pair

/**
 * Order domain class.
 */
@TenantEntity
@AuditEntity
@SequenceEntity
class CrmOrder {

    public static final int PAYMENT_STATUS_UNKNOWN = 0
    public static final int PAYMENT_STATUS_OPEN = 1
    public static final int PAYMENT_STATUS_WAIT = 11
    public static final int PAYMENT_STATUS_CASH = 12
    public static final int PAYMENT_STATUS_PARTIAL = 31
    public static final int PAYMENT_STATUS_FULL = 35

    public static final int EVENT_RESET = 0
    public static final int EVENT_CHANGED = 1
    public static final int EVENT_PUBLISHED = 2

    public static final List<String> BIND_WHITELIST = ['number', 'orderDate', 'deliveryDate', 'deliveryRef',
            'reference1', 'reference2', 'reference3', 'reference4', 'campaign', 'username', 'orderType', 'orderStatus', 'deliveryType',
            'customerNumber', 'customerRef', 'customerFirstName', 'customerLastName', 'customerCompany', 'customerTel', 'customerEmail',
            'invoice', 'delivery', 'totalAmount', 'totalVat', 'currency'
    ].asImmutable()

    private def _crmCoreService

    String number
    java.sql.Date orderDate
    java.sql.Date deliveryDate

    String reference1
    String reference2
    String reference3
    String reference4

    String campaign
    String username

    CrmOrderType orderType
    CrmOrderStatus orderStatus
    CrmDeliveryType deliveryType
    String deliveryRef

    String customerNumber
    String customerRef
    String customerFirstName
    String customerLastName
    String customerCompany
    String customerTel
    String customerEmail

    CrmEmbeddedAddress invoice
    CrmEmbeddedAddress delivery

    String currency

    Double totalAmount = 0
    Double totalVat = 0

    int paymentStatus = PAYMENT_STATUS_UNKNOWN
    Date paymentDate
    String paymentType
    String paymentId
    Double payedAmount = 0

    int event = EVENT_RESET

    static embedded = ['invoice', 'delivery']

    static hasMany = [items: CrmOrderItem]

    static constraints = {
        number(maxSize: 20, nullable: true, unique: 'tenantId')
        orderDate()
        deliveryDate(nullable: true)
        reference1(maxSize: 80, nullable: true)
        reference2(maxSize: 80, nullable: true)
        reference3(maxSize: 80, nullable: true)
        reference4(maxSize: 80, nullable: true)
        campaign(maxSize: 40, nullable: true)
        username(maxSize: 80, nullable: true)
        orderType()
        orderStatus()
        deliveryType(nullable: true)
        deliveryRef(maxSize: 80, nullable: true)
        customerNumber(maxSize: 20, nullable: true)
        customerRef(maxSize: 80, nullable: true)
        customerFirstName(maxSize: 80, nullable: true)
        customerLastName(maxSize: 80, nullable: true)
        customerCompany(maxSize: 80, nullable: true)
        customerTel(maxSize: 20, nullable: true)
        customerEmail(maxSize: 80, nullable: true, email: true)
        currency(maxSize: 4, nullable: true)
        totalAmount(min: -999999d, max: 999999d, scale: 2)
        totalVat(min: -999999d, max: 999999d, scale: 2)
        invoice(nullable: true)
        delivery(nullable: true)
        paymentStatus(min: PAYMENT_STATUS_UNKNOWN, max: PAYMENT_STATUS_FULL)
        paymentDate(nullable: true)
        paymentType(maxSize: 40, nullable: true)
        paymentId(maxSize: 80, nullable: true)
        payedAmount(min: -999999d, max: 999999d, scale: 2)
    }

    static mapping = {
        sort 'number'
        customerRef index: 'crm_order_customer_idx'
        items sort: 'orderIndex', 'asc'
    }

    static transients = ['customer', 'customerName', 'deliveryContact', 'totalAmountVAT', 'totalDiscount', 'totalDiscountVAT',
            'dao', 'syncPending', 'syncPublished']

    static taggable = true
    static attachmentable = true
    static dynamicProperties = true

    // Lazy injection of service.
    private def getCrmCoreService() {
        if (_crmCoreService == null) {
            _crmCoreService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('crmCoreService')
        }
        _crmCoreService
    }

    transient Object getCustomer() {
        getCrmCoreService().getReference(customerRef)
    }

    transient void setCustomer(Object arg) {
        customerRef = getCrmCoreService().getReferenceIdentifier(arg)
    }

    transient Object getDeliveryContact() {
        getCrmCoreService().getReference(deliveryRef)
    }

    transient void setDeliveryContact(Object arg) {
        deliveryRef = getCrmCoreService().getReferenceIdentifier(arg)
    }

    transient Double getTotalAmountVAT() {
        def p = totalAmount ?: 0
        def v = totalVat ?: 0
        return p + v
    }

    transient Double getTotalDiscount() {
        items ? items.sum { it.getDiscountValue() } : 0
    }

    transient Double getTotalDiscountVAT() {
        items ? items.sum { it.getDiscountValueVAT() } : 0
    }

    transient String getCustomerName() {
        def s = new StringBuilder()
        if (customerFirstName) {
            s.append(customerFirstName)
        }
        if (customerLastName) {
            if (s.length()) {
                s.append(' ')
            }
            s.append(customerLastName)
        }
        if (s.length() == 0 && customerRef?.startsWith('crmContact@')) {
            def c = getCustomer()
            if (c) {
                s << c.toString()
            }
        }
        s.toString()
    }

    def beforeValidate() {
        if (!number) {
            number = getNextSequenceNumber()
        }

        def (tot, vat) = calculateAmount()
        totalAmount = tot
        totalVat = vat

        if ((invoice == null) && customerRef?.startsWith('crmContact@')) {
            // HACK! If customer is a CrmContact, copy it's address.
            def customerAddress = getCustomer()?.address
            if (customerAddress) {
                invoice = new CrmEmbeddedAddress(customerAddress)
            }
        }
    }

    protected Pair<Double, Double> calculateAmount() {
        Double sum = 0
        Double vat = 0
        for (item in items) {
            sum += item.totalPrice
            vat += item.totalVat
        }
        new Pair(sum, vat)
    }

    String toString() {
        number.toString()
    }

    transient Map<String, Object> getDao() {
        def map = BIND_WHITELIST.inject([:]) { m, i ->
            if (i != 'invoice' && i != 'delivery') {
                def v = this."$i"
                if (v != null) {
                    m[i] = v
                }
            }
            m
        }
        map.id = id
        map.customer = getCustomer()?.getDao()
        map.customerName = getCustomerName()
        if(invoice != null) {
            map.invoice = invoice.getDao()
        }
        if (delivery != null) {
            map.delivery = delivery.getDao()
        }
        map.totalAmountVAT = getTotalAmountVAT()

        if(paymentDate || payedAmount) {
            map.payedAmount = payedAmount
            map.paymentDate = paymentDate
            map.paymentType = paymentType
            map.paymentId = paymentId
        }

        map.items = items?.collect { it.getDao() }
        return map
    }

    transient boolean isSyncPending() {
        event == EVENT_CHANGED
    }

    void setSyncPending() {
        event = EVENT_CHANGED
    }

    transient boolean isSyncPublished() {
        event == EVENT_PUBLISHED
    }

    void setSyncPublished() {
        event = EVENT_PUBLISHED
    }

    void setNoSync() {
        event = EVENT_RESET
    }
}
