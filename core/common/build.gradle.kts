plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.parsfilo.contentapp.core.common"
    compileSdk = requiredToolchainInt("toolchain.android.compileSdk")
    buildToolsVersion = requiredToolchainProperty("toolchain.android.buildTools")

    defaultConfig {
        minSdk = requiredToolchainInt("toolchain.android.minSdk")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
        targetCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.javax.inject)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
