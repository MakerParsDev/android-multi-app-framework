// Standalone JVM build for CodSpeed JMH benchmarks.
// Run from the repository root with: ./gradlew -p benchmarks jmh
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "contentapp-benchmarks"

// CodSpeed fork of JMH (git submodule) replaces the upstream JMH artifacts.
includeBuild("../third-party/codspeed-jvm/jmh-fork") {
    dependencySubstitution {
        substitute(module("org.openjdk.jmh:jmh-core")).using(project(":jmh-core"))
        substitute(module("org.openjdk.jmh:jmh-generator-annprocess")).using(project(":jmh-generator-annprocess"))
        substitute(module("org.openjdk.jmh:jmh-generator-bytecode")).using(project(":jmh-generator-bytecode"))
        substitute(module("org.openjdk.jmh:jmh-generator-reflection")).using(project(":jmh-generator-reflection"))
        substitute(module("org.openjdk.jmh:jmh-generator-asm")).using(project(":jmh-generator-asm"))
    }
}
