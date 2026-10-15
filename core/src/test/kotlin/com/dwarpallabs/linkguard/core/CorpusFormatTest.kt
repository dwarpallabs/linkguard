package com.dwarpallabs.linkguard.core

import java.io.File
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Keeps the corpus files well-formed until Milestone 2 asserts verdicts against them.
 * Each non-comment line is `url<TAB>expected_check<TAB>note`.
 */
class CorpusFormatTest {

    private val corpusDir = File(System.getProperty("corpus.dir"))
    private val knownChecks = setOf(
        "allowlist", "unwrap", "apk_download", "brand_lookalike",
        "upi_intent", "domain_age", "reputation", "weird_host",
    )
    private val schemes = setOf("http", "https", "upi")

    @TestFactory
    fun everyCorpusLineIsWellFormed(): List<DynamicTest> =
        listOf("must_block.txt", "must_pass.txt", "ambiguous.txt").flatMap { name ->
            val lines = File(corpusDir, name).readLines()
                .withIndex()
                .filter { (_, line) -> line.isNotBlank() && !line.startsWith("#") }
            assertTrue(lines.isNotEmpty(), "$name has no entries")
            lines.map { (index, line) ->
                DynamicTest.dynamicTest("$name:${index + 1}") {
                    val fields = line.split('\t')
                    assertEquals(3, fields.size, "expected 3 tab-separated fields")
                    val (url, check, note) = fields
                    assertTrue(URI(url).scheme in schemes, "unsupported scheme in $url")
                    assertTrue(check in knownChecks, "unknown check '$check'")
                    assertTrue(note.isNotBlank(), "missing note")
                }
            }
        }
}
