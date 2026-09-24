pluginManagement {
    repositories {
        // Los plugins del compilador de Kotlin y sus artefactos BOM transitivos se publican en Maven Central.
        mavenCentral()
        // Conserva la fuente estándar para Spring Boot y otros artefactos marcadores de plugins de Gradle.
        gradlePluginPortal()
    }
}

rootProject.name = "notification-service"
