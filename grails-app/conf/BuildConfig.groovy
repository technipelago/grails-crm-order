grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.6

grails.project.dependency.resolver = "maven"
grails.project.dependency.resolution = {
	inherits("global") {}
	log "warn"
	legacyResolve false
	repositories {
		grailsCentral()
		mavenLocal()
		mavenCentral()
	}

	plugins {
		build ":tomcat:7.0.55"
		build ":release:3.0.1"
		runtime ":hibernate4:4.3.6.1"

		test(":codenarc:0.22") { export = false }

		compile ":crm-core:2.4.1"
		compile ":crm-tags:2.4.1"

		compile ":sequence-generator:1.2"
		compile ":selection:0.9.8"
	}
}
