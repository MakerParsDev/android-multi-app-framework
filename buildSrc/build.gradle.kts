// buildSrc contains Kotlin classes and Gradle task types, not precompiled script plugins.
// Use the security-fixed Kotlin/JVM plugin directly; Gradle kotlin-dsl
// plugin 6.6.4 transitively reintroduces Kotlin Gradle Plugin 2.3.21.
plugins {
    kotlin("jvm") version "2.4.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
}

kotlin {
    jvmToolchain(21)
}
