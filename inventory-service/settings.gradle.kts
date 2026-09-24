pluginManagement {
    repositories {
        // Los plugins del compilador de Kotlin y sus artefactos BOM transitivos se publican en Maven Central.
        mavenCentral()
        // Conserva la fuente estándar de Spring Boot y de otros artefactos marcadores de plugins de Gradle.
        gradlePluginPortal()
    }
}

// Define el nombre de compilación Gradle independiente usado en los artefactos generados y la salida de compilación.
rootProject.name = "inventory-service"
