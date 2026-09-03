plugins {
    // Produces the executable service artifact and provides Spring Boot task conventions.
    id("org.springframework.boot") version "3.5.16"
    // Keeps transitive Spring dependency versions aligned with the selected Boot release.
    id("io.spring.dependency-management") version "1.1.7"
    // Compiles the production and test source sets written in Kotlin.
    kotlin("jvm") version "2.2.21"
    // Opens Spring-managed classes where runtime proxy generation requires it.
    kotlin("plugin.spring") version "2.2.21"
    // Supplies JPA-compatible no-argument constructors and open entity classes at compile time.
    kotlin("plugin.jpa") version "2.2.21"
}

group = "com.orderflow"
version = "0.0.1-SNAPSHOT"

kotlin {
    // Java 21 is the compilation and runtime baseline for this service.
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // Runtime adapters: HTTP/validation for inbound REST and JPA for outbound PostgreSQL access.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Flyway owns schema evolution; the PostgreSQL module adds database-specific migration support.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Kotlin reflection is required by Spring when inspecting Kotlin declarations and constructors.
    implementation(kotlin("reflect"))

    // The JDBC driver is needed only when the application connects to PostgreSQL at runtime.
    runtimeOnly("org.postgresql:postgresql")

    // JUnit 5, Spring test facilities, Kotlin assertions, and Kotlin-friendly port mocking.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.mockk:mockk:1.14.6")

    // Integration tests launch an isolated real PostgreSQL instance when Docker is available.
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
}

tasks.withType<Test> {
    // Ensures Gradle discovers tests using the JUnit Platform/JUnit 5 engine.
    useJUnitPlatform()
}
