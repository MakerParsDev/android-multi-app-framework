package com.parsfilo.contentapp.benchmarks

import com.parsfilo.contentapp.feature.qibla.QiblaMath
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import kotlin.random.Random

@State(Scope.Benchmark)
open class QiblaMathBenchmark {
    private lateinit var latitudes: DoubleArray
    private lateinit var longitudes: DoubleArray
    private lateinit var headings: FloatArray

    @Setup
    fun setup() {
        val random = Random(42)
        latitudes = DoubleArray(SAMPLES) { random.nextDouble(-89.0, 89.0) }
        longitudes = DoubleArray(SAMPLES) { random.nextDouble(-180.0, 180.0) }
        headings = FloatArray(SAMPLES) { random.nextFloat() * 720f - 360f }
    }

    @Benchmark
    fun qiblaBearing(bh: Blackhole) {
        for (i in 0 until SAMPLES) {
            bh.consume(QiblaMath.qiblaBearingDegrees(latitudes[i], longitudes[i]))
        }
    }

    /** Simulates a stream of compass sensor updates smoothed towards the qibla bearing. */
    @Benchmark
    fun compassSmoothing(): Float {
        val target = QiblaMath.qiblaBearingDegrees(41.0082, 28.9784)
        var current = 0f
        var relative = 0f
        for (i in 0 until SAMPLES) {
            current = QiblaMath.smoothDegrees(current, headings[i], 0.15f)
            relative += QiblaMath.relativeClockwiseDegrees(current, target)
        }
        return relative
    }

    private companion object {
        const val SAMPLES = 1_000
    }
}
