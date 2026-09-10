pluginManagement {
    repositories {
        // Kotlin compiler plugins and their transitive BOM artifacts are published to Maven Central.
        mavenCentral()
        // Retains the standard source for Spring Boot and other Gradle plugin marker artifacts.
        gradlePluginPortal()
    }
}

rootProject.name = "payment-service"
