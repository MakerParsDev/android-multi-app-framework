import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.play.publisher)
}

/* =========================
   ENV & VERSION PROPERTIES
   ========================= */

val envFile: File? = rootProject.file(".env")
val envProps = Properties()
if (envFile?.exists() == true) {
    envFile.inputStream().use { envProps.load(it) }
}

val fallbackVersionCode = 1
val fallbackVersionName = "1.0.0"

val appVersionsFile: File = rootProject.file("app-versions.properties")
val appVersionsProps = Properties()
if (appVersionsFile.exists()) {
    appVersionsFile.inputStream().use { appVersionsProps.load(it) }
}

fun pick(name: String): String? {
    val gradleValue = providers.gradleProperty(name).orNull
    if (!gradleValue.isNullOrBlank()) return gradleValue

    val environmentValue = providers.environmentVariable(name).orNull
    if (!environmentValue.isNullOrBlank()) return environmentValue

    val envFileValue = envProps.getProperty(name)?.trim('"')
    if (!envFileValue.isNullOrBlank()) return envFileValue

    return null
}

fun asBuildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun normalizedTaskName(taskName: String): String = taskName.substringAfterLast(':')

fun isReleaseBuildLikeTask(taskName: String): Boolean =
    normalizedTaskName(taskName).let { normalized ->
        (normalized.startsWith("assemble") || normalized.startsWith("bundle") || normalized.startsWith("publish")) &&
            normalized.contains("Release")
    }

fun isReleasePublishTask(taskName: String): Boolean =
    normalizedTaskName(taskName).let { normalized ->
        normalized.startsWith("publish") && normalized.contains("Release")
    }

val pushRegistrationUrlValue = pick("PUSH_REGISTRATION_URL").orEmpty().trim()
val purchaseVerificationUrlValue = pick("PURCHASE_VERIFICATION_URL").orEmpty().trim()
val releaseRevisionValue =
    sequenceOf(
        pick("RELEASE_REVISION"),
        pick("BUILD_SOURCEVERSION"),
        pick("GITHUB_SHA"),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty().ifBlank { "local" }
val releaseTrackValue = pick("PLAY_TRACK").orEmpty().ifBlank { "internal" }
val ciSmokeEnabled =
    providers
        .gradleProperty("ciSmoke")
        .map { raw ->
            raw.toBooleanStrictOrNull()
                ?: error("Gradle property ciSmoke must be true or false (resolved='$raw')")
        }.orElse(false)
if (pushRegistrationUrlValue.isBlank()) {
    logger.warn("⚠️ PUSH_REGISTRATION_URL is empty. Push registration requests will be skipped.")
}
if (purchaseVerificationUrlValue.isBlank()) {
    logger.warn("⚠️ PURCHASE_VERIFICATION_URL is empty. Purchase verification requests will be skipped.")
}

data class ResolvedVersion(
    val versionCode: Int,
    val versionName: String,
)

fun resolvedFlavorVersion(flavorName: String): ResolvedVersion {
    val defaultCode = fallbackVersionCode
    val defaultName = fallbackVersionName

    val codeKey = "$flavorName.versionCode"
    val nameKey = "$flavorName.versionName"

    val codeRaw = appVersionsProps.getProperty(codeKey)?.trim().orEmpty()
    val nameRaw = appVersionsProps.getProperty(nameKey)?.trim().orEmpty()

    val code =
        if (codeRaw.isNotEmpty()) {
            codeRaw.toIntOrNull()
                ?: error("Invalid int for '$codeKey' in app-versions.properties: '$codeRaw'")
        } else {
            defaultCode
        }

    val name = nameRaw.ifEmpty { defaultName }

    require(code > 0) { "versionCode must be > 0 for flavor '$flavorName' (resolved=$code)" }
    require(name.isNotBlank()) { "versionName must not be blank for flavor '$flavorName'" }

    return ResolvedVersion(versionCode = code, versionName = name)
}

val validateFlavorVersions =
    tasks.register("validateFlavorVersions") {
        group = "verification"
        description = "Validates app-versions.properties contains versionCode/versionName for every flavor."
        val requiredFlavors = AppFlavors.all.map { it.name }
        val appVersionsFilePath = appVersionsFile.absolutePath

        doLast {
            val versionsFile = File(appVersionsFilePath)
            val props =
                Properties().apply {
                    if (versionsFile.exists()) {
                        versionsFile.inputStream().use { load(it) }
                    }
                }

            val missingKeys = mutableListOf<String>()
            requiredFlavors.forEach { flavor ->
                val codeKey = "$flavor.versionCode"
                val nameKey = "$flavor.versionName"
                if (props.getProperty(codeKey).isNullOrBlank()) {
                    missingKeys += codeKey
                }
                if (props.getProperty(nameKey).isNullOrBlank()) {
                    missingKeys += nameKey
                }
            }

            if (!versionsFile.exists()) {
                throw GradleException(
                    buildString {
                        appendLine("Missing required file: ${versionsFile.absolutePath}")
                        appendLine("Expected per-flavor keys:")
                        appendLine("  <flavor>.versionCode=123")
                        appendLine("  <flavor>.versionName=1.2.3")
                    },
                )
            }

            if (missingKeys.isNotEmpty()) {
                val sampleFlavor = requiredFlavors.firstOrNull() ?: "exampleFlavor"
                throw GradleException(
                    buildString {
                        appendLine("Missing required flavor version keys in ${versionsFile.absolutePath}:")
                        missingKeys.forEach { appendLine("  - $it") }
                        appendLine("Expected format example:")
                        appendLine("  $sampleFlavor.versionCode=123")
                        appendLine("  $sampleFlavor.versionName=1.2.3")
                    },
                )
            }
        }
    }

val validateReleaseConfig =
    tasks.register("validateReleaseConfig") {
        group = "verification"
        description = "Validates release signing and runtime endpoint configuration."

        val keystorePath = pick("KEYSTORE_FILE").orEmpty().trim()
        val keystoreFile = keystorePath.takeIf { it.isNotBlank() }?.let { project.file(it) }
        val keystorePassword = pick("KEYSTORE_PASSWORD").orEmpty()
        val keyAlias = pick("KEY_ALIAS").orEmpty()
        val keyPassword = pick("KEY_PASSWORD").orEmpty()
        val pushRegistrationUrl = pushRegistrationUrlValue
        val purchaseVerificationUrl = purchaseVerificationUrlValue

        doLast {
            val errors = mutableListOf<String>()

            if (keystorePath.isBlank()) {
                errors += "Missing KEYSTORE_FILE (required for release/publish tasks)"
            } else if (keystoreFile?.exists() != true) {
                errors += "KEYSTORE_FILE does not exist: $keystorePath"
            }

            if (keystorePassword.isBlank()) {
                errors += "Missing KEYSTORE_PASSWORD (required for release/publish tasks)"
            }
            if (keyAlias.isBlank()) {
                errors += "Missing KEY_ALIAS (required for release/publish tasks)"
            }
            if (keyPassword.isBlank()) {
                errors += "Missing KEY_PASSWORD (required for release/publish tasks)"
            }
            if (pushRegistrationUrl.isBlank()) {
                errors += "Missing PUSH_REGISTRATION_URL (required for release/publish tasks)"
            } else if (!pushRegistrationUrl.startsWith("https://", ignoreCase = true)) {
                errors += "PUSH_REGISTRATION_URL must start with https:// (resolved='$pushRegistrationUrl')"
            }
            if (purchaseVerificationUrl.isBlank()) {
                errors += "Missing PURCHASE_VERIFICATION_URL (required for release/publish tasks)"
            } else if (!purchaseVerificationUrl.startsWith("https://", ignoreCase = true)) {
                errors += "PURCHASE_VERIFICATION_URL must start with https:// (resolved='$purchaseVerificationUrl')"
            }

            if (errors.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("Release/publish configuration validation failed:")
                        errors.forEach { appendLine("  - $it") }
                        appendLine()
                        appendLine(
                            "Tip: app/build.gradle.kts reads values from -P, environment variables, then .env.",
                        )
                    },
                )
            }
        }
    }

val validatePublishConfig =
    tasks.register("validatePublishConfig") {
        group = "verification"
        description = "Validates Play Publisher credentials for publishRelease tasks."

        val playServiceAccountJsonPath = pick("PLAY_SERVICE_ACCOUNT_JSON").orEmpty().trim()
        val playServiceAccountJsonFile =
            playServiceAccountJsonPath.takeIf { it.isNotBlank() }?.let { project.file(it) }

        doLast {
            val errors = mutableListOf<String>()
            if (playServiceAccountJsonPath.isBlank()) {
                errors += "Missing PLAY_SERVICE_ACCOUNT_JSON (required for publishRelease tasks)"
            } else if (playServiceAccountJsonFile?.exists() != true) {
                errors += "PLAY_SERVICE_ACCOUNT_JSON file does not exist: $playServiceAccountJsonPath"
            }
            if (errors.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("Publish configuration validation failed:")
                        errors.forEach { appendLine("  - $it") }
                    },
                )
            }
        }
    }

android {
    namespace = "com.parsfilo.contentapp"
    compileSdk = requiredToolchainInt("toolchain.android.compileSdk")
    buildToolsVersion = requiredToolchainProperty("toolchain.android.buildTools")

    bundle {
        language {
            // Avoid Play-generated locale split anomalies by shipping app strings in the base APK.
            enableSplit = false
        }
    }

    flavorDimensions += "app"

    productFlavors {
        AppFlavors.all.forEach { config ->
            create(config.name) {
                dimension = "app"
                applicationId = config.packageName
                resValue("string", "app_name", config.displayName)
                buildConfigField("String", "FLAVOR_NAME", asBuildConfigString(config.name))
                buildConfigField("String", "PRODUCT_DISPLAY_NAME", asBuildConfigString(config.displayName))
                buildConfigField("String", "CONTENT_FAMILY", asBuildConfigString(config.contentFamily))
                buildConfigField("String", "MONETIZATION_PROFILE", asBuildConfigString(config.monetizationProfile))
                buildConfigField("String", "NOTIFICATION_PROFILE", asBuildConfigString(config.notificationProfile))
                buildConfigField("String", "BILLING_PROFILE", asBuildConfigString(config.billingProfile))
                buildConfigField("String", "THEME_TOKEN_KEY", asBuildConfigString(config.themeTokenKey))
                buildConfigField(
                    "String",
                    "PRODUCT_CAPABILITIES_CSV",
                    asBuildConfigString(config.capabilityFlags.joinToString(",")),
                )

                val resolved = resolvedFlavorVersion(config.name)
                versionCode = resolved.versionCode
                versionName = resolved.versionName

                // Audio dosya adı (varsa)
                val audioFile = config.audioFileName ?: "content_audio.mp3"
                buildConfigField("String", "AUDIO_FILE_NAME", asBuildConfigString(audioFile))
                buildConfigField("boolean", "IS_PRAYER_TIMES_FLAVOR", "${config.isPrayerTimesFlavor}")
            }
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = pick("KEYSTORE_FILE")

            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = pick("KEYSTORE_PASSWORD") ?: ""
                keyAlias = pick("KEY_ALIAS") ?: ""
                keyPassword = pick("KEY_PASSWORD") ?: ""
            } else {
                logger.warn("⚠️ Release signing config missing - unsigned build")
            }
        }
    }

    defaultConfig {
        minSdk = requiredToolchainInt("toolchain.android.minSdk")
        targetSdk = requiredToolchainInt("toolchain.android.targetSdk")

        // Fallback defaults. Flavors override via app-versions.properties.
        versionCode = fallbackVersionCode
        versionName = fallbackVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // ✅ DOĞRU buildConfigField
        buildConfigField(
            "String",
            "APP_SHARE_URL_BASE",
            "\"https://play.google.com/store/apps/details?id=\"",
        )

        buildConfigField("String", "ASSET_PACK_NAME", "\"audioassets\"")
        buildConfigField("boolean", "USE_ASSET_PACK_AUDIO", "false")
        buildConfigField(
            "String",
            "PUSH_REGISTRATION_URL",
            asBuildConfigString(pushRegistrationUrlValue),
        )
        buildConfigField(
            "String",
            "PURCHASE_VERIFICATION_URL",
            asBuildConfigString(purchaseVerificationUrlValue),
        )
        buildConfigField(
            "String",
            "RELEASE_REVISION",
            asBuildConfigString(releaseRevisionValue),
        )
        buildConfigField(
            "String",
            "RELEASE_TRACK",
            asBuildConfigString(releaseTrackValue),
        )
        buildConfigField("boolean", "CI_SMOKE", "false")
    }

    buildTypes {
        debug {
            val smoke = ciSmokeEnabled.get()
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            buildConfigField("boolean", "CI_SMOKE", smoke.toString())
            manifestPlaceholders["ciSmokeFirebaseDisabled"] = smoke.toString()
            manifestPlaceholders["ciSmokeFirebaseEnabled"] = (!smoke).toString()
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            if (!pick("KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            buildConfigField("boolean", "USE_TEST_ADS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
        targetCompatibility = JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols +=
                setOf(
                    "**/libandroidx.graphics.path.so",
                    "**/libdatastore_shared_counter.so",
                )
        }
    }

    testOptions {
        managedDevices {
            localDevices {
                create("ciPixel2Api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86_64"
                }
            }
        }
    }

    lint {
        abortOnError = true
        checkAllWarnings = true
        warningsAsErrors = false
        checkDependencies = true
        htmlReport = true
        xmlReport = true
        sarifReport = true
        baseline = file("lint-baseline.xml")
        // gRPC/Firebase transitively references javax.naming for desktop DNS fallback;
        // Android never executes that path and the library module gate already suppresses it.
        disable.add("InvalidPackage")
    }
}

val validateSystemReceiverManifests =
    tasks.register<ValidateSystemReceiverManifestsTask>("validateSystemReceiverManifests") {
        group = "verification"
        description = "Validate merged system reschedule receivers for both affected flavors"
        pythonExecutable.set(
            rootProject.providers
                .gradleProperty("pythonExecutable")
                .orElse(rootProject.providers.environmentVariable("PYTHON"))
                .orElse(
                    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                        "python"
                    } else {
                        "python3"
                    },
                ),
        )
        validatorScript.set(
            rootProject.layout.projectDirectory.file("scripts/ci/validate_system_receiver_manifests.py"),
        )
        reportFile.set(layout.buildDirectory.file("reports/security/system-receiver-manifests.json"))
    }

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        when (variant.name) {
            "zikirmatikDebug" ->
                validateSystemReceiverManifests.configure {
                    zikirmatikManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                }
            "namazvakitleriDebug" ->
                validateSystemReceiverManifests.configure {
                    namazvakitleriManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                }
        }
    }
}

/* =========================
   KOTLIN (android DIŞINDA!)
   ========================= */

kotlin {
    jvmToolchain(requiredToolchainInt("toolchain.java.major"))
}

/* =========================
   PLAY PUBLISHER
   ========================= */

play {
    val serviceAccountJson = pick("PLAY_SERVICE_ACCOUNT_JSON")

    if (!serviceAccountJson.isNullOrBlank() && file(serviceAccountJson).exists()) {
        serviceAccountCredentials.set(file(serviceAccountJson))
    } else {
        logger.warn("⚠️ Play Console service account not configured")
    }

    // Default to internal; pipelines can still override with -PPLAY_TRACK=production/beta/etc.
    track.set(releaseTrackValue)
    defaultToAppBundles.set(true)
}

/* =========================
   DEPENDENCIES
   ========================= */

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:firebase"))
    implementation(project(":core:auth"))

    implementation(project(":feature:content"))
    implementation(project(":feature:audio"))
    implementation(project(":feature:ads"))
    implementation(project(":feature:billing"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:messages"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:otherapps"))
    implementation(project(":feature:prayertimes"))
    implementation(project(":feature:qibla"))
    implementation(project(":feature:counter"))
    implementation(project(":feature:quran"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.play.services.ads)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.play.asset.delivery.ktx) {
        exclude(group = "com.google.android.play", module = "core-common")
    }

    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks
    .matching { task ->
        isReleaseBuildLikeTask(task.name)
    }.configureEach {
        dependsOn(validateFlavorVersions)
        dependsOn(validateReleaseConfig)
    }

tasks
    .matching { task ->
        isReleasePublishTask(task.name)
    }.configureEach {
        dependsOn(validatePublishConfig)
    }
