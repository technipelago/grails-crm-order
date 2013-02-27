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

    def crmCoreService

    String number
    String description

    java.sql.Date orderDate
    java.sql.Date deliveryDate

    String reference1
    String reference2
    String reference3
    String reference4

    CrmOrderType orderType
    CrmOrderStatus orderStatus
    //CrmDeliveryTerm deliveryTerm
    CrmDeliveryType deliveryType

    String customerRef
    String customerTel
    String customerEmail

    CrmEmbeddedAddress invoice
    CrmEmbeddedAddress delivery

    Float totalAmount = 0f
    Float totalVat = 0f

    static embedded = ['invoice', 'delivery']

    static hasMany = [items: CrmOrderItem]

    static constraints = {
        number(maxSize: 20, nullable: true, unique: 'tenantId')
        description(maxSize: 2000, nullable: true, widget: 'textarea')
        orderDate(nullable: true)
        deliveryDate(nullable: true)
        reference1(maxSize: 80, nullable: true)
        reference2(maxSize: 80, nullable: true)
        reference3(maxSize: 80, nullable: true)
        reference4(maxSize: 80, nullable: true)
        orderType()
        orderStatus()
        deliveryType(nullable:true)
        customerRef(maxSize: 80, nullable: true)
        customerTel(maxSize: 20, nullable: true)
        customerEmail(maxSize: 80, nullable: true, email: true)
        totalAmount(min: -999999f, max: 999999f, scale: 2)
        totalVat(min: -999999f, max: 999999f, scale: 2)
        invoice(nullable: true)
        delivery(nullable: true)
    }

    static mapping = {
        sort 'number'
        customerRef index: 'crm_order_customer_idx'
        items sort: 'orderIndex', 'asc'
    }

    static transients = ['customer']

    static taggable = true
    static attachmentable = true
    static dynamicProperties = true

    transient Object getCustomer() {
        crmCoreService.getReference(customerRef)
    }

    transient void setCustomer(Object arg) {
        customerRef = crmCoreService.getReferenceIdentifier(arg)
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
            def customerAddress = customer?.address
            if (customerAddress) {
                invoice = new CrmEmbeddedAddress(customerAddress)
            }
        }
    }

    Pair<Float, Float> calculateAmount() {
        Float sum = 0f
        Float vat = 0f
        for (item in items) {
            sum += item.totalPrice
            vat += item.totalVat
        }
        return new Pair(sum, vat)
    }

    String toString() {
        number.toString()
    }
}
