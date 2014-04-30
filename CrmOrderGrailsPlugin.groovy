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

class CrmOrderGrailsPlugin {
    def groupId = "grails.crm"
    def version = "1.2.0"
    def grailsVersion = "2.2 > *"
    def dependsOn = [:]
    def pluginExcludes = [
            "grails-app/conf/ApplicationResources.groovy",
            "grails-app/services/grails/plugins/crm/order/TestSecurityService.groovy",
            "grails-app/views/error.gsp"
    ]
    def title = "Grails CRM Order Management Plugin"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
Simple order management for GR8 CRM.
This plugin provides the "headless" part of GR8 CRM order management (i.e domains and services).
The companion plugin crm-order-ui provides user the interface for order management.
'''
    def documentation = "http://grails.org/plugin/crm-order"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/technipelago/grails-crm-order/issues"]
    def scm = [url: "https://github.com/technipelago/grails-crm-order"]

    def features = {
        crmOrder {
            description "Order Management Services"
            hidden true
        }
    }
}
