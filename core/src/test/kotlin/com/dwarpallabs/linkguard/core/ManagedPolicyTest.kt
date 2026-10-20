package com.dwarpallabs.linkguard.core

import com.dwarpallabs.linkguard.core.TestSupport.data
import com.dwarpallabs.linkguard.core.TestSupport.url
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagedPolicyTest {

    private fun parse(text: String) = ManagedPolicy.parseList(text, data.psl)

    @Test
    fun parsesWhatAdminsType() {
        val text = """
            example.com
            https://Login.Example.NET/path?q=1   # pasted link
            *.wild.org, .dotted.in; spaced.co.in	tabbed.com
            co.in
            localhost
            103.21.58.12
            example.com
        """.trimIndent()
        assertEquals(
            listOf("example.com", "login.example.net", "wild.org", "dotted.in", "spaced.co.in", "tabbed.com"),
            parse(text),
        )
    }

    @Test
    fun emptyAndBlankAreNothing() {
        assertEquals(emptyList<String>(), parse(""))
        assertEquals(emptyList<String>(), parse("  \n # only a comment\n"))
        assertEquals(emptyList<String>(), ManagedPolicy.parseList(null, data.psl))
        assertFalse(ManagedPolicy.NONE.isActive)
    }

    @Test
    fun internationalNamesAreStoredAsPunycode() {
        assertEquals(listOf("xn--80ak6aa92e.com"), parse("https://аррӏе.com/"))
    }

    @Test
    fun capsTheNumberOfEntries() {
        val text = (1..ManagedPolicy.MAX_ENTRIES + 50).joinToString("\n") { "site$it.example.com" }
        assertEquals(ManagedPolicy.MAX_ENTRIES, parse(text).size)
    }

    @Test
    fun entriesCoverSubdomainsButNotLookalikes() {
        val policy = ManagedPolicy(blocked = listOf("bad.example.com"))
        assertTrue(policy.blocks(url("https://bad.example.com/")))
        assertTrue(policy.blocks(url("https://www.bad.example.com/x")))
        assertFalse(policy.blocks(url("https://example.com/")))
        assertFalse(policy.blocks(url("https://notbad.example.com/")))
        assertFalse(policy.blocks(url("https://bad.example.com.evil.net/")))
    }

    @Test
    fun blockingWinsOverTrusting() {
        val policy = ManagedPolicy(blocked = listOf("x.example.com"), allowed = listOf("example.com"))
        assertTrue(policy.blocks(url("https://x.example.com/")))
        assertFalse(policy.allows(url("https://x.example.com/")))
        assertTrue(policy.allows(url("https://y.example.com/")))
    }

    @Test
    fun upiIsNeverMatched() {
        val policy = ManagedPolicy(blocked = listOf("ybl"), allowed = listOf("ybl"))
        assertFalse(policy.blocks(url("upi://pay?pa=shop@ybl")))
        assertFalse(policy.allows(url("upi://pay?pa=shop@ybl")))
    }

    @Test
    fun keepChecksOnOverridesTheUsersChoice() {
        val off = setOf(CheckId.DOMAIN_AGE, CheckId.REPUTATION)
        assertEquals(off, ManagedPolicy.NONE.disabled(off))
        assertEquals(emptySet<CheckId>(), ManagedPolicy(keepChecksOn = true).disabled(off))
        assertTrue(ManagedPolicy(keepChecksOn = true).isActive)
    }

    private val engine = LinkEngine(data, http = null, registrationDates = null)

    @Test
    fun blockedWebsiteGetsTheStrongOrganisationWarning() {
        val policy = ManagedPolicy(blocked = parse("youtube.com"))
        // Even an allowlisted website: the organisation decides for its phones.
        val verdict = engine.evaluate("https://www.youtube.com/watch?v=1", managed = policy)
        assertEquals(Verdict.Level.STRONG, verdict.level)
        assertEquals("managed.block", verdict.top?.reason)
        assertEquals(LinkEngine.MANAGED_BLOCK_MESSAGE, verdict.top?.messageKey)
    }

    @Test
    fun trustedWebsiteIsSilentButAppDownloadsStillWarn() {
        val lookalike = "https://sbi-staff-portal.xyz/login"
        assertTrue(engine.evaluate(lookalike).level != Verdict.Level.SILENT, "fixture must warn without a policy")
        val policy = ManagedPolicy(allowed = parse("sbi-staff-portal.xyz"))
        assertEquals(Verdict.Level.SILENT, engine.evaluate(lookalike, managed = policy).level)
        val apk = engine.evaluate("https://sbi-staff-portal.xyz/StaffApp.apk", managed = policy)
        assertEquals(CheckId.APK_DOWNLOAD, apk.top?.check)
    }

    @Test
    fun keepChecksOnRunsChecksTheUserSwitchedOff() {
        val raw = "http://sbi-rewards-redeem.xyz/claim"
        val off = setOf(CheckId.BRAND_LOOKALIKE, CheckId.WEIRD_HOST)
        assertEquals(Verdict.Level.SILENT, engine.evaluate(raw, disabled = off).level)
        assertEquals(Verdict.Level.STRONG, engine.evaluate(raw, disabled = off, managed = ManagedPolicy(keepChecksOn = true)).level)
    }

    @Test
    fun noPolicyChangesNothing() {
        val raw = "http://sbi-rewards-redeem.xyz/claim"
        assertEquals(engine.evaluate(raw), engine.evaluate(raw, managed = ManagedPolicy.NONE))
    }
}
