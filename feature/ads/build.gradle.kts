plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.parsfilo.contentapp.feature.ads"
    compileSdk = requiredToolchainInt("toolchain.android.compileSdk")
    buildToolsVersion = requiredToolchainProperty("toolchain.android.buildTools")

    defaultConfig {
        minSdk = requiredToolchainInt("toolchain.android.minSdk")

        // AdMob IDs — injected from local.properties (not hardcoded in source)
        fun adMobField(
            name: String,
            key: String,
        ) {
            val value = AdMobConfig.getProperty(rootProject, key)
            buildConfigField("String", name, "\"$value\"")
        }

        adMobField("ADMOB_APP_ID", "ADMOB_APP_ID")
        adMobField("ADMOB_BANNER_ID", "ADMOB_BANNER_ID")
        adMobField("ADMOB_INTERSTITIAL_ID", "ADMOB_INTERSTITIAL_ID")
        adMobField("ADMOB_NATIVE_ID", "ADMOB_NATIVE_ID")
        adMobField("ADMOB_REWARDED_ID", "ADMOB_REWARDED_ID")
        adMobField("ADMOB_REWARDED_INTERSTITIAL_ID", "ADMOB_REWARDED_INTERSTITIAL_ID")
        adMobField("ADMOB_APP_OPEN_ID", "ADMOB_APP_OPEN_ID")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
        targetCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:firebase"))
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))

    implementation(libs.play.services.ads)
    implementation(libs.play.services.appset)
    implementation(libs.user.messaging.platform)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
}
