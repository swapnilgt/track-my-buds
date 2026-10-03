plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("jacoco")
}

group = "com.trackmybuds"
version = "0.0.1-SNAPSHOT"

// Spring Boot's dependency-management plugin manages its own (older) Testcontainers
// version and otherwise wins over the version.ref pinned in libs.versions.toml.
// Overriding this property is Spring Boot's documented mechanism for bumping a
// BOM-managed dependency: https://docs.spring.io/spring-boot/gradle-plugin/managing-dependencies.html#dependency-versions.overriding
extra["testcontainers.version"] = libs.versions.testcontainers.get()

kotlin {
    jvmToolchain(21)
}

// kotlin-jpa adds a no-arg constructor for @Entity, but neither it nor kotlin-spring
// opens entity classes; Hibernate needs them non-final to create lazy proxies.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.bundles.observability)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.logstash.logback.encoder)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    // Testcontainers module versions come from Spring Boot's dependency management,
    // pinned to the catalog value via extra["testcontainers.version"] above — so an
    // explicit BOM platform import is redundant.
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// XML is what Codecov (and most coverage services) consume; HTML stays for local viewing.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
