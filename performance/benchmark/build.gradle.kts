plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.parsfilo.contentapp.performance"
    compileSdk = requiredToolchainInt("toolchain.android.compileSdk")
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = requiredToolchainInt("toolchain.android.minSdk")
        targetSdk = requiredToolchainInt("toolchain.android.targetSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "app"
    productFlavors {
        AppFlavors.all.forEach { config ->
            create(config.name) {
                dimension = "app"
                testInstrumentationRunnerArguments["performanceFamily"] = config.contentFamily
                testInstrumentationRunnerArguments["performanceFlavor"] = config.name
                testInstrumentationRunnerArguments["performancePackage"] = config.packageName
            }
        }
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api33") {
                    device = "Pixel 6"
                    apiLevel = 33
                    systemImageSource = "aosp"
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api33"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.runner)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
}
