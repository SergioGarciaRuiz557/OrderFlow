plugins {
    // Compiles the Java 21 production and test source sets.
    java
    // Builds and runs the executable Spring Boot service.
    id("org.springframework.boot") version "3.5.16"
    // Aligns compatible versions of Spring and Testcontainers transitive dependencies.
    id("io.spring.dependency-management") version "1.1.7"
}

// Maven coordinates and development version of this independent microservice artifact.
group = "com.orderflow"
version = "0.0.1-SNAPSHOT"

java {
    // Domain code uses Java 21 APIs such as List.getFirst(); builds therefore require this toolchain.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    // All declared open-source dependencies are resolved from Maven Central.
    mavenCentral()
}

dependencies {
    // Inbound HTTP adapter and embedded servlet runtime.
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Jakarta Bean Validation used by REST request records.
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Outbound persistence adapter, transactions, Hibernate, and Spring Data repositories.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Versioned database migrations plus PostgreSQL-specific Flyway support.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    // JDBC driver is needed at runtime but is not referenced by production source code.
    runtimeOnly("org.postgresql:postgresql")

    // JUnit 5, AssertJ, Mockito, and Spring integration-test facilities.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Disposable real PostgreSQL support for integration tests.
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test> {
    // Discovers and runs tests through the JUnit Platform rather than the legacy JUnit runner.
    useJUnitPlatform()
}
