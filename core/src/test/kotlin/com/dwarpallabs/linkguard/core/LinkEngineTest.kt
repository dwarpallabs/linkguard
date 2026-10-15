package com.dwarpallabs.linkguard.core

import com.dwarpallabs.linkguard.core.TestSupport.data
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkEngineTest {

    private fun engine(http: Http? = FakeHttp.fromResource("fake_redirects.tsv"), dates: FakeRegistrationDates? = null) =
        LinkEngine(data, http, dates)

    @Test
    fun unparseableInputIsSilent() {
        val verdict = engine().evaluate("not a url")
        assertEquals(Verdict.Level.SILENT, verdict.level)
        assertNull(verdict.finalUrl)
    }

    @Test
    fun levelsFollowClaudeMdThresholds() {
        assertEquals(Verdict.Level.SILENT, Verdict.levelFor(0.29))
        assertEquals(Verdict.Level.SOFT, Verdict.levelFor(0.3))
        assertEquals(Verdict.Level.SOFT, Verdict.levelFor(0.7))
        assertEquals(Verdict.Level.STRONG, Verdict.levelFor(0.71))
    }

    @Test
    fun verdictIsMaxNotSum() {
        // Free TLD + brand (0.8) and brand lookalike (0.8) both fire: score stays 0.8.
        val verdict = engine().evaluate("http://sbi-rewards-redeem.xyz/claim")
        assertTrue(verdict.signals.count { it.score == 0.8 } >= 2)
        assertEquals(0.8, verdict.score)
    }

    @Test
    fun allowlistShortCircuitsAndSkipsNetworkChecks() {
        val dates = FakeRegistrationDates(emptyMap())
        val verdict = engine(dates = dates).evaluate("https://www.irctc.co.in/")
        assertEquals(0.0, verdict.score)
        assertTrue(verdict.signals.any { it.reason == "allowlist.hit" })
        assertEquals(emptyList<String>(), dates.asked)
    }

    @Test
    fun apkRunsBeforeAllowlist() {
        val verdict = engine().evaluate("https://github.com/someone/repo/releases/download/v1/Rewards.apk")
        assertEquals(0.9, verdict.score)
    }

    @Test
    fun unwrapFeedsFinalUrlToChecks() {
        val verdict = engine().evaluate("https://bit.ly/3xAmPl3")
        assertEquals("sbi-rewards-redeem.xyz", verdict.finalUrl?.host)
        assertEquals("unwrap.hops_1", verdict.signals.first { it.check == CheckId.UNWRAP }.reason)
    }

    @Test
    fun redirectToUpiIsEvaluatedAsUpi() {
        val verdict = engine().evaluate("https://is.gd/lgtestupi")
        assertEquals("upi", verdict.finalUrl?.scheme)
        assertEquals("Electricity Board", verdict.top?.details?.get("payee_name"))
    }

    @Test
    fun onlyShortenersAreContacted() {
        val http = FakeHttp.fromResource("fake_redirects.tsv")
        val e = engine(http)
        e.evaluate("https://bit.ly/3xAmPl3")
        e.evaluate("http://sbi-rewards-redeem.xyz/claim")
        e.evaluate("https://www.example.com/")
        assertEquals(listOf("https://bit.ly/3xAmPl3"), http.requested)
    }

    @Test
    fun offlineEngineStillRunsLocalChecks() {
        val verdict = LinkEngine(data, null, null).evaluate("http://hdfc-kyc-update.top/verify")
        assertEquals(Verdict.Level.STRONG, verdict.level)
    }

    @Test
    fun coreHasNoAndroidImports() {
        val offenders = File(System.getProperty("core.main.dir")).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readLines().any { it.startsWith("import android") } }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
