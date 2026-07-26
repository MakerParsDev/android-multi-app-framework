plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.parsfilo.contentapp.feature.dynamicaudio"
    compileSdk = requiredToolchainInt("toolchain.android.compileSdk")

    defaultConfig {
        minSdk = requiredToolchainInt("toolchain.android.minSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
        targetCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":feature:audio"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
}
