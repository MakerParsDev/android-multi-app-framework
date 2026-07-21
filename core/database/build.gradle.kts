plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.parsfilo.contentapp.core.database"
    compileSdk = requiredToolchainInt("toolchain.android.compileSdk")
    buildToolsVersion = requiredToolchainProperty("toolchain.android.buildTools")

    defaultConfig {
        minSdk = requiredToolchainInt("toolchain.android.minSdk")
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
        targetCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:model"))

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
