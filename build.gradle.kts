import groovy.json.JsonSlurper
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.room) apply false
    // ── Quality Tools ──
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover)

}

// Test JVM paralelliğini de sınırla
val cores = Runtime.getRuntime().availableProcessors()
tasks.withType<Test>().configureEach {
    maxParallelForks = (cores / 3).coerceAtLeast(1)
}

val testsDisabledForBuild = providers.gradleProperty("disableTests").map(String::toBoolean).orElse(false)
val javaToolchainVersion = requiredToolchainInt("toolchain.java.major")

val dependencyPolicyFile = layout.projectDirectory.file("config/dependency-policy.json")
val transitivePrereleaseAllowlistCoordinates: Set<String> = run {
    val policy = JsonSlurper().parseText(
        providers.fileContents(dependencyPolicyFile).asText.get(),
    ) as? Map<*, *> ?: error("Dependency policy root must be a JSON object")
    val entries = policy["transitive_prerelease_allowlist"] as? List<*>
        ?: error("Dependency policy must define transitive_prerelease_allowlist")
    entries.map { rawEntry ->
        val entry = rawEntry as? Map<*, *>
            ?: error("Each transitive prerelease allowlist entry must be an object")
        entry["coordinate"]?.toString()?.takeIf(String::isNotBlank)
            ?: error("Each transitive prerelease allowlist entry must define coordinate")
    }.toSet()
}
val allowedPreReleaseGroups = transitivePrereleaseAllowlistCoordinates
    .filter { it.endsWith(":*") }
    .map { it.removeSuffix(":*") }
    .toSet()
val allowedPreReleaseModules = transitivePrereleaseAllowlistCoordinates
    .filterNot { it.endsWith(":*") }
    .toSet()

val preReleaseVersionRegex = Regex(
    """(?:^|[.\-])(?:alpha|beta|rc|cr|preview|eap|snapshot|milestone|m\d+)(?:[.\-]?\d*)?(?:$|[.\-])""",
    RegexOption.IGNORE_CASE,
)


val expectedAppDebugLintTasks =
    AppFlavors.all.map { flavor ->
        ":app:lint${flavor.name.replaceFirstChar { it.titlecase() }}Debug"
    }
val expectedAppDebugUnitTestTasks =
    AppFlavors.all.map { flavor ->
        ":app:test${flavor.name.replaceFirstChar { it.titlecase() }}DebugUnitTest"
    }
val expectedAppDebugReportVariants =
    AppFlavors.all.map { flavor -> "${flavor.name}Debug" }

val verifyQualityGateTaskCoverage = tasks.register<VerifyQualityGateTaskCoverage>(
    "verifyQualityGateTaskCoverage",
) {
    group = "verification"
    description = "Fail when a required flavor or module verification task is missing"
    expectedAppLintTasks.set(expectedAppDebugLintTasks)
    expectedAppUnitTestTasks.set(expectedAppDebugUnitTestTasks)
    expectedVersionValidationTasks.set(listOf(":app:validateFlavorVersions"))
    expectedLibraryLintTasks.convention(emptyList())
    actualLibraryLintTasks.convention(emptyList())
    expectedLibraryUnitTestTasks.convention(emptyList())
    actualLibraryUnitTestTasks.convention(emptyList())
    actualAppLintTasks.convention(emptyList())
    actualAppUnitTestTasks.convention(emptyList())
    actualVersionValidationTasks.convention(emptyList())
    testsDisabled.set(testsDisabledForBuild)
}

val allDebugUnitTestsTask = tasks.register("allDebugUnitTests") {
    group = "verification"
    description = "Run unit tests for every Android debug variant and module"
    dependsOn(verifyQualityGateTaskCoverage)
}

val allDebugLintTask = tasks.register("allDebugLint") {
    group = "verification"
    description = "Run blocking Android Lint for every application debug flavor and library module"
    dependsOn(verifyQualityGateTaskCoverage)
}

val allKtlintCheckTask = tasks.register("allKtlintCheck") {
    group = "verification"
    description = "Run blocking ktlint checks for every Kotlin subproject"
}

val qualityCheckTask = tasks.register("qualityCheck") {
    group = "verification"
    description =
        "Blocking full verification: metadata, Detekt, ktlint, all debug unit tests/lint, and Kover"
    dependsOn("detekt", allDebugUnitTestsTask, allDebugLintTask)
}

val pythonExecutable = providers.gradleProperty("pythonExecutable")
    .orElse(providers.environmentVariable("PYTHON"))
    .orElse(
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "python"
        } else {
            "python3"
        },
    )

val validateAppAdsTxtTask = tasks.register<Exec>("validateAppAdsTxt") {
    group = "verification"
    description = "Validate app-ads.txt seller rows and required AdMob publisher line"
    commandLine(
        pythonExecutable.get(),
        "scripts/ci/validate_app_ads_txt.py",
        "--mode",
        "strict",
    )
}

val validateAndroidToolchainConfigTask = tasks.register<Exec>("validateAndroidToolchainConfig") {
    group = "verification"
    description = "Validate centralized Android/JVM toolchain configuration and pipeline wiring"
    commandLine(pythonExecutable.get(), "scripts/ci/validate_android_toolchain_config.py")
}

val validateSecretScanPolicyTask = tasks.register<Exec>("validateSecretScanPolicy") {
    group = "verification"
    description = "Validate pinned secret scanner, owned baseline, and deterministic ignore file"
    inputs.files(
        layout.projectDirectory.file("config/secret-scan-policy.json"),
        layout.projectDirectory.file(".gitleaks.toml"),
        layout.projectDirectory.file(".gitleaksignore"),
        layout.projectDirectory.file("scripts/ci/secret_scan_policy.py"),
        layout.projectDirectory.file("scripts/ci/validate_secret_scan_policy.py"),
    )
    outputs.file(layout.buildDirectory.file("reports/security/secret-scan-policy.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_secret_scan_policy.py")
}

val validateTrackedSensitiveFilesTask = tasks.register<Exec>("validateTrackedSensitiveFiles") {
    group = "verification"
    description = "Block tracked environment files, signing keys, and credential configuration files"
    inputs.files(
        layout.projectDirectory.file("scripts/ci/tracked_sensitive_files.py"),
        layout.projectDirectory.file("scripts/ci/validate_tracked_sensitive_files.py"),
    )
    outputs.file(layout.buildDirectory.file("reports/security/tracked-sensitive-files.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_tracked_sensitive_files.py")
}

val validateSupplyChainPolicyTask = tasks.register<Exec>("validateSupplyChainPolicy") {
    group = "verification"
    description = "Validate Gradle wrapper integrity and dependency verification review policy"
    inputs.files(
        layout.projectDirectory.file("config/supply-chain-policy.json"),
        layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties"),
        layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.jar"),
        layout.projectDirectory.file("scripts/ci/validate_supply_chain_policy.py"),
    )
    outputs.file(layout.buildDirectory.file("reports/security/supply-chain-policy.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_supply_chain_policy.py")
}

val validateSecurityPipelineTask = tasks.register<Exec>("validateSecurityPipeline") {
    group = "verification"
    description = "Validate full-history Azure checkout and pre-secret security gate ordering"
    inputs.files(
        fileTree(layout.projectDirectory.dir("azure-pipelines")) { include("*.yml") },
        fileTree(layout.projectDirectory.dir("pipelines")) {
            include("azure-pipelines*.yml", "templates/**/*.yml")
        },
        layout.projectDirectory.file("scripts/ci/validate_security_pipeline.py"),
    )
    outputs.file(layout.buildDirectory.file("reports/security/security-pipeline.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_security_pipeline.py")
}

val validateSecretOwnershipTask = tasks.register<Exec>("validateSecretOwnership") {
    group = "verification"
    description = "Validate canonical credential stores, ownership, rotation, and migration deadline"
    inputs.files(
        layout.projectDirectory.file("config/secret-ownership.json"),
        layout.projectDirectory.file("scripts/ci/validate_secret_ownership.py"),
    )
    outputs.file(layout.buildDirectory.file("reports/security/secret-ownership.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_secret_ownership.py")
}

val validateCloudflareAppCheckConfigTask = tasks.register<Exec>("validateCloudflareAppCheckConfig") {
    group = "verification"
    description = "Validate Cloudflare App Check project and app allowlist against the Firebase catalog"
    inputs.files(
        layout.projectDirectory.file("config/firebase-apps.json"),
        layout.projectDirectory.file("side-projects/cloudflare/workers/admin-api/wrangler.toml"),
        layout.projectDirectory.file("scripts/ci/validate_cloudflare_app_check_config.py"),
    )
    commandLine(pythonExecutable.get(), "scripts/ci/validate_cloudflare_app_check_config.py")
}

val validateSecureDeviceRegistrationTask = tasks.register<Exec>("validateSecureDeviceRegistration") {
    group = "verification"
    description = "Enforce App Check-protected backend-only device registration"
    inputs.files(
        layout.projectDirectory.file("core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/di/PushRegistrationModule.kt"),
        layout.projectDirectory.file("core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/push/HttpPushRegistrationSender.kt"),
        layout.projectDirectory.file("side-projects/cloudflare/workers/admin-api/src/index.ts"),
        layout.projectDirectory.file("side-projects/cloudflare/workers/admin-api/src/deviceRegistration.ts"),
        layout.projectDirectory.file("side-projects/cloudflare/workers/admin-api/wrangler.toml"),
        layout.projectDirectory.file("side-projects/firebase/firestore.rules"),
        layout.projectDirectory.file("scripts/ci/validate_secure_device_registration.py"),
    )
    val reportFile = layout.buildDirectory.file("reports/security/secure-device-registration.json")
    outputs.file(reportFile)
    commandLine(
        pythonExecutable.get(),
        "scripts/ci/validate_secure_device_registration.py",
        "--report",
        reportFile.get().asFile.absolutePath,
    )
}


val validateSystemReceiverManifestsTask = tasks.register("validateSystemReceiverManifests") {
    group = "verification"
    description = "Validate merged system reschedule receivers for both affected flavors"
    dependsOn(":app:validateSystemReceiverManifests")
}

val validateSideProjectQualityContractTask = tasks.register<Exec>("validateSideProjectQualityContract") {
    group = "verification"
    description = "Validate Firebase/Cloudflare quality scripts, CI wiring, and deploy gates"
    inputs.files(
        layout.projectDirectory.file("side-projects/admin-notifications/package.json"),
        layout.projectDirectory.file("side-projects/cloudflare/workers/admin-api/package.json"),
        layout.projectDirectory.file("side-projects/cloudflare/workers/content-api/package.json"),
        layout.projectDirectory.file("side-projects/cloudflare/workers/ssv-callback/package.json"),
        layout.projectDirectory.file("side-projects/firebase/functions/package.json"),
        layout.projectDirectory.file("side-projects/firebase/firebase.json"),
        layout.projectDirectory.file("side-projects/audit-policy.json"),
        layout.projectDirectory.file("scripts/ci/run_side_project_quality.sh"),
        layout.projectDirectory.file("scripts/ci/validate_side_project_audits.py"),
        layout.projectDirectory.file("scripts/ci/validate_side_project_audits_test.py"),
        layout.projectDirectory.file("scripts/ci/deploy_verified_side_project.mjs"),
        layout.projectDirectory.file("scripts/ci/check_side_project_deployment_drift.py"),
        layout.projectDirectory.file("scripts/ci/validate_side_project_endpoint_contracts.py"),
        layout.projectDirectory.file("scripts/ci/side_project_quality_contract_test.py"),
        layout.projectDirectory.file("scripts/azure/quality.sh"),
        layout.projectDirectory.file("scripts/azure/release.sh"),
        fileTree(layout.projectDirectory.dir("azure-pipelines")) { include("*.yml") },
        fileTree(layout.projectDirectory.dir("pipelines")) { include("azure-pipelines*.yml") },
    )
    commandLine(pythonExecutable.get(), "scripts/ci/side_project_quality_contract_test.py")
}

val validatePrivacySafeLoggingTask = tasks.register<Exec>("validatePrivacySafeLogging") {
    group = "verification"
    description = "Block raw identifiers, coordinates, URLs, and file paths in production logs"
    inputs.files(
        fileTree(layout.projectDirectory) {
            include("app/**/src/main/**/*.kt", "app/**/src/release/**/*.kt")
            include("core/**/src/main/**/*.kt", "core/**/src/release/**/*.kt")
            include("feature/**/src/main/**/*.kt", "feature/**/src/release/**/*.kt")
            exclude("**/build/**")
        },
        layout.projectDirectory.file("scripts/ci/validate_privacy_safe_logging.py"),
    )
    outputs.file(layout.buildDirectory.file("reports/security/privacy-safe-logging.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_privacy_safe_logging.py")
}

val auditDependencyCatalogTask = tasks.register<Exec>("auditDependencyCatalog") {
    group = "verification"
    description = "Enforce stable dependency policy and generate version-catalog audit reports"
    inputs.files(
        layout.projectDirectory.file("gradle/libs.versions.toml"),
        dependencyPolicyFile,
        layout.projectDirectory.file("scripts/ci/audit_dependency_catalog.py"),
        layout.projectDirectory.file("scripts/ci/dependency_catalog_audit.py"),
        layout.projectDirectory.file("scripts/ci/dependency_catalog_parser.py"),
        layout.projectDirectory.file("scripts/ci/dependency_policy.py"),
        layout.projectDirectory.file("scripts/ci/dependency_catalog_inventory.py"),
        layout.projectDirectory.file("scripts/ci/dependency_catalog_report.py"),
    )
    outputs.files(
        layout.buildDirectory.file("reports/dependencies/catalog-audit.json"),
        layout.buildDirectory.file("reports/dependencies/catalog-audit.md"),
    )
    commandLine(pythonExecutable.get(), "scripts/ci/audit_dependency_catalog.py")
}

val validateLintBaselinesTask = tasks.register<Exec>("validateLintBaselines") {
    group = "verification"
    description = "Validate Android Lint baseline inventory and block debt growth"
    commandLine(pythonExecutable.get(), "scripts/ci/validate_lint_baselines.py")
}

val validateAnalyticsGovernanceTask = tasks.register<Exec>("validateAnalyticsGovernance") {
    group = "verification"
    description = "Validate analytics events, parameters, privacy policy, and GA4 governance manifest"
    inputs.files(
        layout.projectDirectory.file("config/analytics-governance.json"),
        layout.projectDirectory.file(
            "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsContract.kt",
        ),
        layout.projectDirectory.file(
            "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsPayloadPolicy.kt",
        ),
        fileTree(layout.projectDirectory) {
            include("app/**/src/main/**/*.kt")
            include("core/**/src/main/**/*.kt")
            include("feature/**/src/main/**/*.kt")
            exclude("**/build/**")
        },
        layout.projectDirectory.file("scripts/ci/analytics_governance.py"),
        layout.projectDirectory.file("scripts/ci/validate_analytics_governance.py"),
        layout.projectDirectory.file("docs/ANALYTICS_GOVERNANCE.md"),
    )
    outputs.file(layout.buildDirectory.file("reports/analytics/contract.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_analytics_governance.py")
}

val validateRemoteConfigGovernanceTask = tasks.register<Exec>("validateRemoteConfigGovernance") {
    group = "verification"
    description = "Validate per-app Remote Config conditions, safety bounds, Play versions, and template drift"
    inputs.files(
        layout.projectDirectory.file("config/remote-config/governance.json"),
        layout.projectDirectory.file("config/remote-config/play-production-version-codes.json"),
        layout.projectDirectory.file("config/remote-config/template.json"),
        layout.projectDirectory.file("config/remote-config/history/template-v3.json"),
        layout.projectDirectory.file("config/firebase-apps.json"),
        layout.projectDirectory.file(".ci/apps.json"),
        layout.projectDirectory.file("app-versions.properties"),
        layout.projectDirectory.file("scripts/ci/remote_config_governance.py"),
        layout.projectDirectory.file("scripts/ci/validate_remote_config_governance.py"),
        layout.projectDirectory.file("docs/REMOTE_CONFIG_GOVERNANCE.md"),
        layout.projectDirectory.file("docs/REMOTE_CONFIG_DEPLOYMENT_IMPACT.md"),
        layout.projectDirectory.file("app/src/main/java/com/parsfilo/contentapp/update/UpdatePolicy.kt"),
        layout.projectDirectory.file(
            "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdsPolicyProvider.kt",
        ),
    )
    outputs.file(layout.buildDirectory.file("reports/remote-config/governance.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_remote_config_governance.py")
}

val validateAdMobInventoryTask = tasks.register<Exec>("validateAdMobInventory") {
    group = "verification"
    description = "Validate AdMob app ownership, flavor resources, cleanup candidates, and audit freshness"
    inputs.files(
        layout.projectDirectory.file("config/admob-inventory.json"),
        layout.projectDirectory.file(".ci/apps.json"),
        fileTree(layout.projectDirectory.dir("app/src")) {
            include("*/res/values/ads.xml")
        },
        fileTree(layout.projectDirectory.dir("scripts/ci")) {
            include("validate_admob_inventory.py")
            include("admob_inventory_*.py")
        },
        layout.projectDirectory.file("docs/ADMOB_INVENTORY.md"),
    )
    val reportFile = layout.buildDirectory.file("reports/admob/inventory.json")
    outputs.file(reportFile)
    commandLine(
        pythonExecutable.get(),
        "scripts/ci/validate_admob_inventory.py",
        "--output",
        reportFile.get().asFile.absolutePath,
    )
}

val validateAdMobWeeklyBaselineTask = tasks.register<Exec>("validateAdMobWeeklyBaseline") {
    group = "verification"
    description = "Regenerate the source-controlled two-week AdMob trend and bounded experiment baseline"
    inputs.files(
        layout.projectDirectory.file("config/admob-weekly-baseline-2026-07-12.json"),
        layout.projectDirectory.file("config/admob-inventory.json"),
        layout.projectDirectory.file("scripts/ci/admob_weekly_analysis.py"),
        layout.projectDirectory.file("scripts/ci/admob_weekly_report.py"),
        layout.projectDirectory.file("scripts/ci/check_admob_weekly_optimization.py"),
        layout.projectDirectory.file("docs/ADMOB_REVENUE_OPTIMIZATION.md"),
    )
    val reportFile = layout.buildDirectory.file("reports/admob/weekly-baseline.json")
    val markdownFile = layout.buildDirectory.file("reports/admob/weekly-baseline.md")
    outputs.files(reportFile, markdownFile)
    commandLine(
        pythonExecutable.get(),
        "scripts/ci/check_admob_weekly_optimization.py",
        "--fixture-json",
        "config/admob-weekly-baseline-2026-07-12.json",
        "--week-end",
        "2026-07-12",
        "--out-json",
        reportFile.get().asFile.absolutePath,
        "--out-markdown",
        markdownFile.get().asFile.absolutePath,
        "--check-markdown",
        "docs/ADMOB_REVENUE_OPTIMIZATION.md",
    )
}

val validateRuntimeObservabilityTask = tasks.register<Exec>("validateRuntimeObservability") {
    group = "verification"
    description = "Validate runtime signal ownership, release identity, flavor coverage, and health thresholds"
    inputs.files(
        layout.projectDirectory.file("config/runtime-observability-policy.json"),
        layout.projectDirectory.file("config/firebase-apps.json"),
        layout.projectDirectory.file("buildSrc/src/main/kotlin/FlavorConfig.kt"),
        layout.projectDirectory.file("scripts/ci/runtime_health_policy.py"),
        layout.projectDirectory.file("scripts/ci/validate_runtime_observability.py"),
        layout.projectDirectory.file("app/build.gradle.kts"),
        layout.projectDirectory.file("app/src/main/java/com/parsfilo/contentapp/App.kt"),
        layout.projectDirectory.file(
            "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/RuntimeObservability.kt",
        ),
        layout.projectDirectory.file(
            "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/di/RuntimeObservabilityModule.kt",
        ),
        layout.projectDirectory.file(
            "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/config/RemoteConfigManager.kt",
        ),
        layout.projectDirectory.file(
            "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/push/PushRegistrationManager.kt",
        ),
        layout.projectDirectory.file(
            "feature/billing/src/main/java/com/parsfilo/contentapp/feature/billing/BillingPurchaseVerifier.kt",
        ),
        layout.projectDirectory.file(
            "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdManager.kt",
        ),
        layout.projectDirectory.file(
            "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdRevenueLogger.kt",
        ),
        layout.projectDirectory.file(
            "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsConsent.kt",
        ),
        layout.projectDirectory.file("azure-pipelines/release-health.yml"),
        layout.projectDirectory.file("docs/RELEASE_HEALTH_RUNBOOK.md"),
    )
    outputs.file(layout.buildDirectory.file("reports/observability/policy-validation.json"))
    commandLine(pythonExecutable.get(), "scripts/ci/validate_runtime_observability.py")
}

val evaluateRuntimeHealthExampleTask = tasks.register<Exec>("evaluateRuntimeHealthExample") {
    group = "verification"
    description = "Exercise the post-release health decision engine with the committed example snapshot"
    inputs.files(
        layout.projectDirectory.file("config/runtime-observability-policy.json"),
        layout.projectDirectory.file("config/runtime-health-snapshot.example.json"),
        layout.projectDirectory.file("scripts/ci/runtime_health_policy.py"),
        layout.projectDirectory.file("scripts/ci/evaluate_release_health.py"),
        layout.projectDirectory.file("buildSrc/src/main/kotlin/FlavorConfig.kt"),
    )
    outputs.files(
        layout.buildDirectory.file("reports/release-health/decision.json"),
        layout.buildDirectory.file("reports/release-health/decision.md"),
    )
    commandLine(
        pythonExecutable.get(),
        "scripts/ci/evaluate_release_health.py",
        "--snapshot",
        "config/runtime-health-snapshot.example.json",
        "--expected-checkpoint",
        "24",
        "--fail-on",
        "hotfix",
    )
}

val testCiPythonScriptsTask = tasks.register<Exec>("testCiPythonScripts") {
    group = "verification"
    description = "Run CI helper regression tests, including Android toolchain bootstrap tests"
    commandLine(
        pythonExecutable.get(),
        "-m",
        "unittest",
        "discover",
        "-s",
        "scripts/ci",
        "-p",
        "*_test.py",
    )
}

val staticQualityCheckTask = tasks.register("staticQualityCheck") {
    group = "verification"
    description = "Run metadata, Detekt, ktlint, version, and toolchain checks without Android lint/tests"
    dependsOn(
        validateAppAdsTxtTask,
        validateAndroidToolchainConfigTask,
        validateSecretScanPolicyTask,
        validateTrackedSensitiveFilesTask,
        validateSupplyChainPolicyTask,
        validateSecurityPipelineTask,
        validateSecretOwnershipTask,
        validateCloudflareAppCheckConfigTask,
        validateSecureDeviceRegistrationTask,
        validateSystemReceiverManifestsTask,
        validateSideProjectQualityContractTask,
        validatePrivacySafeLoggingTask,
        auditDependencyCatalogTask,
        validateLintBaselinesTask,
        validateAnalyticsGovernanceTask,
        validateRemoteConfigGovernanceTask,
        validateAdMobInventoryTask,
        validateAdMobWeeklyBaselineTask,
        validateRuntimeObservabilityTask,
        evaluateRuntimeHealthExampleTask,
        testCiPythonScriptsTask,
        verifyQualityGateTaskCoverage,
        allKtlintCheckTask,
        "detekt",
        ":app:validateFlavorVersions",
    )
}

qualityCheckTask.configure {
    dependsOn(
        validateAppAdsTxtTask,
        validateAndroidToolchainConfigTask,
        validateSecretScanPolicyTask,
        validateTrackedSensitiveFilesTask,
        validateSupplyChainPolicyTask,
        validateSecurityPipelineTask,
        validateSecretOwnershipTask,
        validateCloudflareAppCheckConfigTask,
        validateSecureDeviceRegistrationTask,
        validateSystemReceiverManifestsTask,
        validateSideProjectQualityContractTask,
        validatePrivacySafeLoggingTask,
        auditDependencyCatalogTask,
        validateLintBaselinesTask,
        validateAnalyticsGovernanceTask,
        validateRemoteConfigGovernanceTask,
        validateAdMobInventoryTask,
        validateAdMobWeeklyBaselineTask,
        validateRuntimeObservabilityTask,
        evaluateRuntimeHealthExampleTask,
        testCiPythonScriptsTask,
    )
}

// ═══════════════════════════════════════════════════════════════
// ▸ 1. Detekt — Balanced defaults plus project-specific correctness rules
// ═══════════════════════════════════════════════════════════════
detekt {
    source.setFrom(
        fileTree(rootDir) {
            include("app/src/**/*.kt", "core/*/src/**/*.kt", "feature/*/src/**/*.kt")
        })
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/detekt-baseline.xml")
    parallel = true
    buildUponDefaultConfig = true
    autoCorrect = false
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        html.outputLocation.set(project.layout.buildDirectory.file("reports/detekt/detekt.html"))
    }
    jvmTarget = javaToolchainVersion.toString()
    // Any new static-analysis finding blocks the verification gate.
    ignoreFailures = false
}

// ═══════════════════════════════════════════════════════════════
// ▸ 2. Subproject quality config (Android Lint + ktlint)
// ═══════════════════════════════════════════════════════════════
subprojects {
    // Stabilize R8 inputs by pinning versions across the graph.
    val libsCatalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

    val coroutinesVersion = libsCatalog.findVersion("coroutines").get().requiredVersion
    val credentialsVersion = libsCatalog.findVersion("credentialsVersion").get().requiredVersion
    val googleIdVersion = libsCatalog.findVersion("googleidVersion").get().requiredVersion

    configurations.configureEach {
        resolutionStrategy {
            componentSelection {
                all {
                    val moduleCoordinate = "${candidate.group}:${candidate.module}"
                    val isAllowedPreRelease =
                        candidate.group in allowedPreReleaseGroups || moduleCoordinate in allowedPreReleaseModules
                    if (preReleaseVersionRegex.containsMatchIn(candidate.version) && !isAllowedPreRelease) {
                        reject(
                            "Pre-release dependencies are not allowed: " + "${candidate.group}:${candidate.module}:${candidate.version}"
                        )
                    }
                }
            }
            force(
                "org.jetbrains.kotlinx:kotlinx-coroutines-bom:$coroutinesVersion",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion",
                "org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion",
                "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion",
                "androidx.credentials:credentials:$credentialsVersion",
                "androidx.credentials:credentials-play-services-auth:$credentialsVersion",
                "com.google.android.libraries.identity.googleid:googleid:$googleIdVersion"
            )
        }
    }

    // ── ktlint (koşulsuz, configuration-cache uyumlu) ──
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // ✅ Plugin apply edildikten sonra extension kesin var
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            android.set(true)
            outputToConsole.set(true)

            // Formatting is always blocking. Exploratory formatting uses ktlintFormat instead.
            ignoreFailures.set(false)

            reporters {
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
            }
        }

        rootProject.tasks.named(allKtlintCheckTask.name).configure {
            dependsOn(tasks.named("ktlintCheck"))
        }
        rootProject.tasks.named(qualityCheckTask.name).configure {
            dependsOn(tasks.named("ktlintCheck"))
        }
    }

    // ── Java Toolchain 21 Enforce ──
    extensions.findByType<JavaPluginExtension>()?.toolchain?.languageVersion?.set(
        JavaLanguageVersion.of(javaToolchainVersion)
    )
    extensions.findByType<KotlinBaseExtension>()?.jvmToolchain(javaToolchainVersion)

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")

            // Compose Compiler Metrics
            if (project.findProperty("composeCompilerReports") == "true") {
                freeCompilerArgs.addAll(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" + project.layout.buildDirectory.dir(
                        "compose_reports"
                    ).get().asFile.absolutePath,
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" + project.layout.buildDirectory.dir(
                        "compose_metrics"
                    ).get().asFile.absolutePath
                )
            }
        }
    }

    // ── Test Control (enabled by default, optional disable via -PdisableTests=true) ──
    val disableTests = (findProperty("disableTests") as String?)?.toBoolean() == true
    if (disableTests) {
        tasks.withType<Test>().configureEach { enabled = false }
        tasks.matching { it.name.contains("UnitTest") }.configureEach { enabled = false }
    }

    // ── Android Lint for library modules ──
    plugins.withId("com.android.library") {
        @Suppress("UNCHECKED_CAST") val androidExt =
            extensions.getByName("android") as com.android.build.api.dsl.LibraryExtension

        androidExt.packaging {
            jniLibs {
                // Prebuilt libs may not be strip-compatible; keep symbols to avoid noisy warnings.
                keepDebugSymbols += setOf(
                    "**/libandroidx.graphics.path.so", "**/libdatastore_shared_counter.so"
                )
            }
        }

        androidExt.lint {
            abortOnError = true
            checkAllWarnings = true
            warningsAsErrors = false
            checkDependencies = false
            htmlReport = true
            xmlReport = true
            sarifReport = true
            val lintBaseline = file("lint-baseline.xml")
            if (lintBaseline.exists()) {
                baseline = lintBaseline
            }
            // Ignore gRPC/Firebase library issues with javax.naming (not available on Android)
            disable.add("InvalidPackage")
        }

        // ProGuard Consumer Rules
        val consumerRules = file("consumer-rules.pro")
        if (consumerRules.exists()) {
            androidExt.defaultConfig.consumerProguardFiles(consumerRules)
        }

    }
}

// Resolve required Android tasks after every project has registered its variants. TaskProvider lookups
// validate task existence without realizing the tasks during unrelated commands such as `gradlew help`.
gradle.projectsEvaluated {
    val appProject = project(":app")
    val androidLibraryProjects =
        subprojects
            .filter { it.plugins.hasPlugin("com.android.library") }
            .sortedBy { it.path }

    val appLintTaskProviders =
        expectedAppDebugLintTasks.map { taskPath ->
            appProject.tasks.named(taskPath.substringAfterLast(':'))
        }
    val appUnitTestTaskProviders =
        expectedAppDebugUnitTestTasks.map { taskPath ->
            appProject.tasks.named(taskPath.substringAfterLast(':'))
        }

    val expectedLibraryLintTaskPaths =
        androidLibraryProjects.map { libraryProject -> "${libraryProject.path}:lintDebug" }
    val expectedLibraryUnitTestTaskPaths =
        androidLibraryProjects.map { libraryProject -> "${libraryProject.path}:testDebugUnitTest" }
    val libraryLintTaskProviders =
        androidLibraryProjects.map { libraryProject -> libraryProject.tasks.named("lintDebug") }
    val libraryUnitTestTaskProviders =
        androidLibraryProjects.map { libraryProject -> libraryProject.tasks.named("testDebugUnitTest") }
    val versionValidationTask = appProject.tasks.named("validateFlavorVersions")

    verifyQualityGateTaskCoverage.configure {
        actualAppLintTasks.set(expectedAppDebugLintTasks)
        actualAppUnitTestTasks.set(expectedAppDebugUnitTestTasks)
        expectedLibraryLintTasks.set(expectedLibraryLintTaskPaths)
        actualLibraryLintTasks.set(expectedLibraryLintTaskPaths)
        expectedLibraryUnitTestTasks.set(expectedLibraryUnitTestTaskPaths)
        actualLibraryUnitTestTasks.set(expectedLibraryUnitTestTaskPaths)
        actualVersionValidationTasks.set(listOf(":app:validateFlavorVersions"))
    }

    appLintTaskProviders.forEach { taskProvider ->
        taskProvider.configure { dependsOn(verifyQualityGateTaskCoverage) }
    }
    appUnitTestTaskProviders.forEach { taskProvider ->
        taskProvider.configure { dependsOn(verifyQualityGateTaskCoverage) }
    }
    libraryLintTaskProviders.forEach { taskProvider ->
        taskProvider.configure { dependsOn(verifyQualityGateTaskCoverage) }
    }
    libraryUnitTestTaskProviders.forEach { taskProvider ->
        taskProvider.configure { dependsOn(verifyQualityGateTaskCoverage) }
    }
    versionValidationTask.configure { dependsOn(verifyQualityGateTaskCoverage) }

    allDebugLintTask.configure {
        dependsOn(appLintTaskProviders)
        dependsOn(libraryLintTaskProviders)
    }
    allDebugUnitTestsTask.configure {
        dependsOn(appUnitTestTaskProviders)
        dependsOn(libraryUnitTestTaskProviders)
    }
    qualityCheckTask.configure { dependsOn(versionValidationTask) }
}

// qualityCheck task is registered near the top so subproject hooks can safely depend on it.

// ═══════════════════════════════════════════════════════════════
// ▸ 3b. Kover — Debug unit-test coverage aggregation
//    Run: ./gradlew :koverXmlReportQuality   (CI)
//    Run: ./gradlew :koverHtmlReportQuality  (local preview in build/reports/kover/html)
// ═══════════════════════════════════════════════════════════════

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

kover {
    merge {
        subprojects()
        createVariant("quality") {
            when (project.path) {
                ":app" -> add(expectedAppDebugReportVariants)
                ":", ":core", ":feature" -> Unit
                else -> add("debug", optional = true)
            }
        }
    }
    reports {
        filters {
            excludes {
                annotatedBy("Generated", "Composable")
                packages(
                    "*.BuildConfig",
                    "hilt_aggregated_deps.*",
                    "*.di",
                    "*.di.*",
                )
                // Exclude generated Room DAO implementations
                classes("*_Impl", "*_Impl\$*")
            }
        }
        variant("quality") {
            html {
                htmlDir.set(layout.buildDirectory.dir("reports/kover/html"))
            }
            xml {
                xmlFile.set(layout.buildDirectory.file("reports/kover/xml/coverage.xml"))
            }
            verify {
                rule("Minimum aggregate quality line coverage") {
                    bound {
                        minValue = 8
                    }
                }
            }
        }
    }
}

val validateCriticalCoverageTask = tasks.register<Exec>("validateCriticalCoverage") {
    group = "verification"
    description = "Enforce class and package coverage thresholds for critical runtime decisions"
    dependsOn("koverXmlReportQuality")
    commandLine(
        pythonExecutable.get(),
        "scripts/ci/validate_critical_coverage.py",
        "--report",
        layout.buildDirectory.file("reports/kover/xml/coverage.xml").get().asFile.absolutePath,
        "--config",
        "config/critical-coverage.json",
    )
}

qualityCheckTask.configure {
    dependsOn(
        "koverVerifyQuality",
        "koverXmlReportQuality",
        "koverHtmlReportQuality",
        validateCriticalCoverageTask,
    )
}

// ═══════════════════════════════════════════════════════════════
// ▸ 4. printFlavors — Utility for CI/CD to get flavor list
//    Usage: ./gradlew -q printFlavors
// ═══════════════════════════════════════════════════════════════
tasks.register("printFlavors") {
    description = "Prints all product flavors as a JSON array for CI matrix generation"
    group = "help"
    doLast {
        // Output format: ["flavor1","flavor2"]
        val flavors = AppFlavors.all.joinToString(
            prefix = "[", separator = ",", postfix = "]"
        ) { "\"${it.name}\"" }
        println(flavors)
    }
}

// ═══════════════════════════════════════════════════════════════
// ▸ 5. composeReports — Generate Compose Compiler stability & metrics reports
//    Usage: ./gradlew composeReports
//    Output: build/compose_reports/ and build/compose_metrics/ in each module
// ═══════════════════════════════════════════════════════════════
tasks.register("composeReports") {
    description = "Generate Compose Compiler stability/metrics reports for all modules"
    group = "verification"
    doLast {
        println("═══════════════════════════════════════════════════════")
        println("  Compose Compiler Reports")
        println("═══════════════════════════════════════════════════════")
        println()
        println("To generate reports, run:")
        println("  ./gradlew assembleRelease -PcomposeCompilerReports=true")
        println()
        println("Reports will be generated in each module's build directory:")
        println("  <module>/build/compose_reports/    (stability reports)")
        println("  <module>/build/compose_metrics/    (metrics reports)")
        println()
        println("Key files to look for:")
        println("  *-composables.txt    — List of all composables with stability info")
        println("  *-composables.csv    — CSV of composable metrics (restartable, skippable)")
        println("  *-classes.txt        — Class stability analysis")
        println("  *-module.json        — Module-level summary metrics")
        println()

        // Find and list existing report directories
        var found = false
        subprojects.forEach { sub ->
            val reportsDir = sub.layout.buildDirectory.dir("compose_reports").get().asFile
            val metricsDir = sub.layout.buildDirectory.dir("compose_metrics").get().asFile
            if (reportsDir.exists() || metricsDir.exists()) {
                found = true
                println("Found reports for :${sub.name}")
                if (reportsDir.exists()) {
                    reportsDir.listFiles()?.forEach { f -> println("  📄 ${f.name}") }
                }
                if (metricsDir.exists()) {
                    metricsDir.listFiles()?.forEach { f -> println("  📊 ${f.name}") }
                }
                println()
            }
        }
        if (!found) {
            println("No compose reports found yet. Run the assembleRelease command above first.")
        }
    }
}
