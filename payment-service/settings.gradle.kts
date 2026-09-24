pluginManagement {
    repositories {
        // Los plugins del compilador de Kotlin y sus artefactos BOM transitivos se publican en Maven Central.
        mavenCentral()
        // Conserva el origen estándar de Spring Boot y de otros artefactos marcadores de plugins de Gradle.
        gradlePluginPortal()
    }
}

rootProject.name = "payment-service"
