plugins {
    // Builds the executable microservice artifact and supplies Spring Boot task conventions.
    id("org.springframework.boot") version "3.5.16"
    // Aligns compatible versions across the Spring dependency ecosystem.
    id("io.spring.dependency-management") version "1.1.7"
    // Compiles production and test source sets written in Kotlin.
    kotlin("jvm") version "2.2.21"
    // Opens Spring-managed Kotlin classes where runtime proxy generation requires it.
    kotlin("plugin.spring") version "2.2.21"
    // Supplies JPA-compatible no-argument constructors and open entity classes at compile time.
    kotlin("plugin.jpa") version "2.2.21"
}

group = "com.orderflow"
version = "0.0.1-SNAPSHOT"

kotlin {
    // Java 21 is the compilation and runtime baseline for this independently deployable service.
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // Outbound persistence adapter: Spring transactions, Hibernate, JDBC, and Spring Data repositories.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Flyway executes versioned schema migrations, with explicit PostgreSQL database support.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    // Spring uses Kotlin reflection when resolving constructors, nullability, and bean metadata.
    implementation(kotlin("reflect"))
    // JDBC driver is required when the running service connects to PostgreSQL.
    runtimeOnly("org.postgresql:postgresql")

    // JUnit 5, Spring integration-test support, Kotlin assertions, and Kotlin-friendly port mocks.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.mockk:mockk:1.14.6")
    // Starts an isolated real PostgreSQL instance for persistence and migration verification.
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
}

tasks.withType<Test> {
    // Discovers all tests through the JUnit Platform/JUnit 5 engine.
    useJUnitPlatform()
}
