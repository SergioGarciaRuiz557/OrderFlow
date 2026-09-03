plugins {
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
}

group = "com.orderflow"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(kotlin("reflect"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test-junit5"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

