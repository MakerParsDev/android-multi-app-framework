plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Supplied only for local, explicit debug device testing. Never commit a device hash.
val umpTestDeviceHash =
    providers
        .gradleProperty("UMP_TEST_DEVICE_HASH")
        .orNull
        .orEmpty()
        .trim()
require(umpTestDeviceHash.isEmpty() || Regex("[A-Fa-f0-9]{32}").matches(umpTestDeviceHash)) {
    "UMP_TEST_DEVICE_HASH must be a 32-character hexadecimal debug device hash."
}

// Local QA opt-in only: lets a physical debug build exercise the treatment
// without touching the live Remote Config template or exposing real users.
val demandAwareDebugOverride =
    providers
        .gradleProperty("ADS_DEMAND_AWARE_DEBUG_OVERRIDE")
        .orNull
        .orEmpty()
        .trim()
require(demandAwareDebugOverride in listOf("", "true", "false")) {
    "ADS_DEMAND_AWARE_DEBUG_OVERRIDE must be true or false"
}

android {
    namespace = "com.parsfilo.contentapp.feature.ads"
    compileSdk = requiredToolchainInt("toolchain.android.compileSdk")
    buildToolsVersion = requiredToolchainProperty("toolchain.android.buildTools")

    defaultConfig {
        minSdk = requiredToolchainInt("toolchain.android.minSdk")

        // Performance/Baseline Profile plugins create synthetic build types such as
        // nonMinifiedRelease. Default to no QA identifiers or debug-only overrides
        // for every variant; explicit debug build-type fields override these values.
        buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"\"")
        buildConfigField("boolean", "ADS_DEMAND_AWARE_DEBUG_OVERRIDE", "false")

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

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"$umpTestDeviceHash\"")
            buildConfigField(
                "boolean",
                "ADS_DEMAND_AWARE_DEBUG_OVERRIDE",
                if (demandAwareDebugOverride == "true") "true" else "false",
            )
        }
        getByName("release") {
            // Never carry device identifiers into production APKs/AABs.
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"\"")
            buildConfigField("boolean", "ADS_DEMAND_AWARE_DEBUG_OVERRIDE", "false")
        }
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
