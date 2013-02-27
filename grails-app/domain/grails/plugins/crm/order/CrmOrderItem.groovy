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

/**
 *
 * @author Goran Ehrsson
 * @since 0.1
 */
class CrmOrderItem {
    Integer orderIndex
    String productId
    String productName
    String unit
    Float quantity
    Float backorder
    Float price
    Float vat

    static belongsTo = [order: CrmOrder]

    static constraints = {
        orderIndex()
        productId(maxSize: 40, blank: false)
        productName(maxSize: 255, blank: false)
        unit(maxSize: 40, nullable: false, blank: false)
        quantity(nullable: false, min: -999999f, max: 999999f, scale: 2)
        backorder(nullable: true, min: -999999f, max: 999999f, scale: 2)
        price(nullable: false, min: -999999f, max: 999999f, scale: 2)
        vat(nullable: false, min: 0f, max: 1f, scale: 2)
    }

    static transients = ['totalPrice', 'totalVat']

    transient Float getTotalPrice() {
        (quantity ?: 0) * (price ?: 0)
    }

    transient Float getTotalVat() {
        getTotalPrice() * vat
    }

    String toString() {
        "$quantity $unit $productId"
    }
}

