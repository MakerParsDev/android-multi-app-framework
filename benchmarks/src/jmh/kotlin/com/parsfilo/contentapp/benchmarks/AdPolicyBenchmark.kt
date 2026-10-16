package com.parsfilo.contentapp.benchmarks

import com.parsfilo.contentapp.feature.ads.AdLoadBackoffPolicy
import com.parsfilo.contentapp.feature.ads.AdLoadBackoffState
import com.parsfilo.contentapp.feature.content.ui.shouldInsertInlineFeedAdAfterItem
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import kotlin.random.Random

@State(Scope.Benchmark)
open class AdPolicyBenchmark {
    /** Lays out ad slots for a long content feed (e.g. a 286-verse surah). */
    @Benchmark
    fun inlineFeedAdLayout(): Int {
        val total = 286
        var ads = 0
        for (index in 0 until total) {
            if (shouldInsertInlineFeedAdAfterItem(index, total)) ads++
        }
        return ads
    }

    /** Runs a sequence of consecutive load failures through the backoff policy. */
    @Benchmark
    fun loadBackoffSequence(): Boolean {
        val random = Random(7)
        var state = AdLoadBackoffState()
        var now = 0L
        var allowed = false
        repeat(32) { attempt ->
            state = AdLoadBackoffPolicy.onLoadFailure(now, state, if (attempt % 2 == 0) 3 else 0, random)
            now += 10_000L
            allowed = AdLoadBackoffPolicy.canLoad(now, state) || allowed
        }
        return allowed
    }
}
