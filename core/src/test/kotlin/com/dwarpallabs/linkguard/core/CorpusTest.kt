package com.dwarpallabs.linkguard.core

import com.dwarpallabs.linkguard.core.TestSupport.corpus
import com.dwarpallabs.linkguard.core.TestSupport.engine
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** The contract from CLAUDE.md: every check change must keep both corpus files green. */
class CorpusTest {

    @TestFactory
    fun mustBlock(): List<DynamicTest> = corpus("must_block.txt").map { entry ->
        dynamicTest("block ${entry.url}") {
            val verdict = engine.evaluate(entry.url)
            assertTrue(verdict.score >= Verdict.STRONG_THRESHOLD) {
                "${entry.note}: score ${verdict.score} < 0.7, signals=${describe(verdict)}"
            }
            if (entry.check == CheckId.UNWRAP.code) {
                val unwrap = verdict.signals.first { it.check == CheckId.UNWRAP }
                assertNotEquals("unwrap.hops_0", unwrap.reason) { "${entry.note}: expected redirects to be followed" }
            } else {
                assertTrue(verdict.signals.any { it.check.code == entry.check && it.score >= Verdict.STRONG_THRESHOLD }) {
                    "${entry.note}: expected ${entry.check} to fire at >= 0.7, signals=${describe(verdict)}"
                }
            }
        }
    }

    @TestFactory
    fun mustPass(): List<DynamicTest> = corpus("must_pass.txt").map { entry ->
        dynamicTest("pass ${entry.url}") {
            val verdict = engine.evaluate(entry.url)
            assertTrue(verdict.score < Verdict.SOFT_THRESHOLD) {
                "${entry.note}: score ${verdict.score} >= 0.3, signals=${describe(verdict)}"
            }
        }
    }

    @TestFactory
    fun ambiguousIsLoggedNotAsserted(): List<DynamicTest> = corpus("ambiguous.txt").map { entry ->
        dynamicTest("ambiguous ${entry.url}") {
            val verdict = engine.evaluate(entry.url)
            println("ambiguous ${verdict.level} ${"%.1f".format(verdict.score)} ${entry.url} — ${entry.note} ${describe(verdict)}")
        }
    }

    private fun describe(verdict: Verdict) =
        verdict.signals.filter { it.fired }.joinToString(prefix = "[", postfix = "]") { "${it.reason}=${it.score}" }
}
