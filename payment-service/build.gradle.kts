plugins {
    // Construye el artefacto ejecutable del microservicio y proporciona las convenciones de tareas de Spring Boot.
    id("org.springframework.boot") version "3.5.16"
    // Alinea las versiones compatibles en todo el ecosistema de dependencias de Spring.
    id("io.spring.dependency-management") version "1.1.7"
    // Compila los conjuntos de fuentes de producción y pruebas escritos en Kotlin.
    kotlin("jvm") version "2.2.21"
    // Abre las clases de Kotlin administradas por Spring cuando lo requiere la generación de proxies en tiempo de ejecución.
    kotlin("plugin.spring") version "2.2.21"
    // Proporciona en tiempo de compilación constructores sin argumentos compatibles con JPA y clases de entidad abiertas.
    kotlin("plugin.jpa") version "2.2.21"
}

group = "com.orderflow"
version = "0.0.1-SNAPSHOT"

kotlin {
    // Java 21 es la base de compilación y ejecución de este servicio desplegable de forma independiente.
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // Adaptador de persistencia de salida: transacciones de Spring, Hibernate, JDBC y repositorios de Spring Data.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // Flyway ejecuta migraciones versionadas del esquema, con compatibilidad explícita para PostgreSQL.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    // Spring usa la reflexión de Kotlin al resolver constructores, anulabilidad y metadatos de beans.
    implementation(kotlin("reflect"))
    // El controlador JDBC es necesario cuando el servicio en ejecución se conecta a PostgreSQL.
    runtimeOnly("org.postgresql:postgresql")

    // JUnit 5, compatibilidad de Spring con pruebas de integración, aserciones de Kotlin y simulaciones de puertos adaptadas a Kotlin.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.mockk:mockk:1.14.6")
    // Inicia una instancia real y aislada de PostgreSQL para verificar la persistencia y las migraciones.
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:kafka:1.21.4")
}

tasks.withType<Test> {
    // Descubre todas las pruebas mediante el motor JUnit Platform/JUnit 5.
    useJUnitPlatform()
}
