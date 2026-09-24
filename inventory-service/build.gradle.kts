plugins {
    // Genera el artefacto ejecutable del servicio y proporciona las convenciones de tareas de Spring Boot.
    id("org.springframework.boot") version "3.5.16"
    // Mantiene alineadas las versiones de las dependencias transitivas de Spring con la versión de Boot seleccionada.
    id("io.spring.dependency-management") version "1.1.7"
    // Compila los conjuntos de código fuente de producción y pruebas escritos en Kotlin.
    kotlin("jvm") version "2.2.21"
    // Abre las clases gestionadas por Spring cuando la generación de proxies en tiempo de ejecución lo requiere.
    kotlin("plugin.spring") version "2.2.21"
    // Proporciona constructores sin argumentos compatibles con JPA y abre las clases de entidad durante la compilación.
    kotlin("plugin.jpa") version "2.2.21"
}

group = "com.orderflow"
version = "0.0.1-SNAPSHOT"

kotlin {
    // Java 21 es la versión base de compilación y ejecución de este servicio.
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // Adaptadores de ejecución: HTTP/validación para REST de entrada y JPA para el acceso de salida a PostgreSQL.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Flyway gestiona la evolución del esquema; el módulo de PostgreSQL añade compatibilidad con migraciones específicas.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Spring necesita la reflexión de Kotlin para inspeccionar declaraciones y constructores de Kotlin.
    implementation(kotlin("reflect"))

    // El controlador JDBC solo es necesario cuando la aplicación se conecta a PostgreSQL durante la ejecución.
    runtimeOnly("org.postgresql:postgresql")

    // Durante el desarrollo local, Spring Boot inicia el servicio PostgreSQL declarado en compose.yaml
    // antes de crear el contexto de aplicación y lo detiene cuando finaliza la aplicación.
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // JUnit 5, utilidades de prueba de Spring, aserciones de Kotlin y simulación de puertos compatible con Kotlin.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.mockk:mockk:1.14.6")

    // Las pruebas de integración inician una instancia real y aislada de PostgreSQL cuando Docker está disponible.
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:kafka:1.21.4")
}

tasks.withType<Test> {
    // Garantiza que Gradle descubra las pruebas mediante el motor de JUnit Platform/JUnit 5.
    useJUnitPlatform()
}
