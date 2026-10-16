// JMH benchmarks for the platform-independent (pure Kotlin/JVM) logic of the app modules.
// The production sources are compiled directly from their Android modules so the
// benchmarks always measure the current code without requiring the Android SDK.
plugins {
    kotlin("jvm") version "2.4.20"
    id("me.champeau.jmh") version "0.7.3"
}

kotlin {
    jvmToolchain(21)
}

val repoRoot = rootDir.parentFile

// Android-free production sources exercised by the benchmarks.
val productionSources =
    mapOf(
        "core/common/src/main/java" to
            listOf("com/parsfilo/contentapp/core/common/logging/PrivacySafeLog.kt"),
        "feature/qibla/src/main/java" to
            listOf("com/parsfilo/contentapp/feature/qibla/QiblaMath.kt"),
        "feature/content/src/main/java" to
            listOf("com/parsfilo/contentapp/feature/content/ui/NativeAdInsertionPolicy.kt"),
        "feature/ads/src/main/java" to
            listOf("com/parsfilo/contentapp/feature/ads/AdLoadBackoffPolicy.kt"),
        "feature/prayertimes/src/main/java" to
            listOf(
                "com/parsfilo/contentapp/feature/prayertimes/data/PrayerDateTime.kt",
                "com/parsfilo/contentapp/feature/prayertimes/data/PrayerCachePolicy.kt",
                "com/parsfilo/contentapp/feature/prayertimes/data/PrayerLocationMatcher.kt",
                "com/parsfilo/contentapp/feature/prayertimes/data/HijriCalendarConverter.kt",
                "com/parsfilo/contentapp/feature/prayertimes/model/PrayerModels.kt",
            ),
    )

val syncProductionSources by tasks.registering(Sync::class) {
    description = "Copies the benchmarked Android-free production sources into the JMH source set."
    productionSources.forEach { (root, files) ->
        from(repoRoot.resolve(root)) { include(files) }
    }
    into(layout.buildDirectory.dir("generated/productionSources"))
}

sourceSets {
    named("jmh") {
        kotlin.srcDir(syncProductionSources)
    }
}

jmh {
    jmhVersion.set("0.2.1")
    warmupIterations.set(2)
    warmup.set("1s")
    iterations.set(3)
    timeOnIteration.set("1s")
    fork.set(1)
    forceGC.set(true)
    resultFormat.set("JSON")
}
