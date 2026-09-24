plugins {
    // Compila los conjuntos de fuentes de producción y pruebas con Java 21.
    java
    // Compila y ejecuta el servicio ejecutable de Spring Boot.
    id("org.springframework.boot") version "3.5.16"
    // Alinea versiones compatibles de las dependencias transitivas de Spring y Testcontainers.
    id("io.spring.dependency-management") version "1.1.7"
}

// Coordenadas Maven y versión de desarrollo del artefacto de este microservicio independiente.
group = "com.orderflow"
version = "0.0.1-SNAPSHOT"

java {
    // El código de dominio usa API de Java 21 como List.getFirst(); por ello, la compilación requiere esta cadena de herramientas.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    // Todas las dependencias de código abierto declaradas se resuelven desde Maven Central.
    mavenCentral()
}

dependencies {
    // Adaptador HTTP de entrada y entorno de ejecución de servlets integrado.
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Jakarta Bean Validation utilizado por los records de petición REST.
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Adaptador de persistencia de salida, transacciones, Hibernate y repositorios de Spring Data.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    // Migraciones versionadas de base de datos y compatibilidad de Flyway específica para PostgreSQL.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    // El controlador JDBC es necesario en tiempo de ejecución, pero el código fuente de producción no lo referencia.
    runtimeOnly("org.postgresql:postgresql")

    // JUnit 5, AssertJ, Mockito y utilidades de Spring para pruebas de integración.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Compatibilidad con una instancia real y desechable de PostgreSQL para las pruebas de integración.
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}

tasks.withType<Test> {
    // Descubre y ejecuta las pruebas mediante JUnit Platform en lugar del ejecutor antiguo de JUnit.
    useJUnitPlatform()
}
