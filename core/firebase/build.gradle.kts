plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.parsfilo.contentapp.core.firebase"
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
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics) {
        exclude(group = "androidx.privacysandbox.ads", module = "ads-adservices")
        exclude(group = "androidx.privacysandbox.ads", module = "ads-adservices-java")
    }
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.auth) {
        exclude(group = "com.google.android.gms", module = "play-services-identity-credentials")
    }
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)

    implementation(libs.timber)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
