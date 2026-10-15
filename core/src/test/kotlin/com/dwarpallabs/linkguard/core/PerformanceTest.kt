package com.dwarpallabs.linkguard.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The local (non-network) part of a verdict must be cheap: Milestone 3 has a 300 ms budget per tap,
 * most of which belongs to network checks and a 2 GB phone is far slower than a dev machine.
 */
class PerformanceTest {

    @Test
    fun localEvaluationIsFast() {
        val start = System.nanoTime()
        val data = EngineData.loadBundled()
        val loadMs = (System.nanoTime() - start) / 1_000_000
        val engine = LinkEngine(data, http = null, registrationDates = null)

        val urls = listOf("must_block.txt", "must_pass.txt", "ambiguous.txt")
            .flatMap { TestSupport.corpus(it) }
            .map { it.url }
        repeat(3) { urls.forEach(engine::evaluate) }

        val rounds = 10
        val t0 = System.nanoTime()
        repeat(rounds) { urls.forEach(engine::evaluate) }
        val perUrlMs = (System.nanoTime() - t0) / 1e6 / (rounds * urls.size)

        println("data load: $loadMs ms, evaluate: ${"%.3f".format(perUrlMs)} ms/url over ${urls.size} urls")
        assertTrue(perUrlMs < 20.0) { "local evaluation too slow: $perUrlMs ms/url" }
    }
}
