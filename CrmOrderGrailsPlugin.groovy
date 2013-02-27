class CrmOrderGrailsPlugin {
    // Dependency group
    def groupId = "grails.crm"
    // the plugin version
    def version = "1.0-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/conf/ApplicationResources.groovy",
            "grails-app/services/grails/plugins/crm/order/TestSecurityService.groovy",
            "grails-app/views/error.gsp"
    ]

    def title = "Grails CRM Order Management Plugin"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
Simple order management for Grails CRM.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/crm-order"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/goeh/grails-crm-order/issues"]
    def scm = [url: "https://github.com/goeh/grails-crm-order"]

    def features = {
        crmOrder {
            description "Order Management"
            link controller: "crmOrder", action: "index"
            permissions {
                guest "crmOrder:index,list,show"
                user "crmOrder:*"
                admin "crmOrder:*"
            }
            hidden true
        }
    }
}
