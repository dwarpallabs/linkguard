package com.dwarpallabs.linkguard

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.Http
import com.dwarpallabs.linkguard.core.HttpResponse
import com.dwarpallabs.linkguard.core.LinkEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reports from the warning screen and the "Was that link a scam?" card send the website only:
 * never the path, query, fragment, port, user info or a subdomain of the link the user tapped.
 */
class ReportPayloadTest {

    private val redirects = object : Http {
        override fun head(url: String, timeoutMs: Int) = when (url) {
            "https://bit.ly/scam" -> HttpResponse(301, location = "http://login.sbi-kyc-verify.xyz/secret/path?acct=9988776655#otp")
            else -> null
        }
        override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int, headers: Map<String, String>): HttpResponse? = null
    }

    private val engine = LinkEngine(data, redirects, null)

    /** Every private part of each test link; none may appear anywhere in what is sent. */
    private val cases = listOf(
        Case(
            "https://login.sbi-kyc-verify.xyz:8443/secret/path?acct=9988776655&otp=4321#frag",
            "https://sbi-kyc-verify.xyz/",
            listOf("login.", "8443", "secret", "path", "acct", "9988776655", "otp", "4321", "frag", "?", "#"),
        ),
        Case(
            "http://www.sbi.co.in:pass@evil-host.top/steal?card=4111111111111111",
            "https://evil-host.top/",
            listOf("sbi.co.in", "pass", "steal", "card", "4111111111111111", "@"),
        ),
        Case(
            "https://bit.ly/scam",
            "https://sbi-kyc-verify.xyz/",
            listOf("bit.ly", "login.", "secret", "path", "acct", "9988776655", "otp", "#"),
        ),
        Case(
            "http://203.0.113.7/hdfc/login.php?user=ravi",
            "https://203.0.113.7/",
            listOf("hdfc", "login", "php", "user", "ravi", "?"),
        ),
    )

    data class Case(val link: String, val website: String, val secrets: List<String>)

    @Test
    fun reportSendsTheWebsiteOnly() {
        for (c in cases) {
            val verdict = engine.evaluate(c.link)
            val website = InterceptViewModel.reportWebsite(c.link, verdict, data.psl)
            assertEquals(c.link, c.website, website)
            val domain = Reporter.reportDomain(com.dwarpallabs.linkguard.core.NormalisedUrl.parse(website!!, data.psl))
            val body = ReportClient.json(website, InterceptViewModel.reportNote(verdict), "0.4.0", domain, domain?.let { "ab".repeat(32) })
            // The scheme's "//" and the trailing "/" are the only slashes; nothing after the website.
            assertEquals(c.link, website, Regex("\"url\":\"([^\"]*)\"").find(body)!!.groupValues[1])
            for (s in c.secrets) assertFalse("${c.link}: '$s' leaked into $body", body.contains(s))
        }
    }

    @Test
    fun ipAddressIsSentButNeverCounted() {
        val link = "http://203.0.113.7/hdfc/login.php?user=ravi"
        assertNull(InterceptViewModel.reportDomain(link, engine.evaluate(link), data.psl))
    }

    @Test
    fun cardReportBuildsTheSameWebsiteLink() {
        assertEquals("https://sbi-kyc-verify.xyz/", Reporter.websiteLink("sbi-kyc-verify.xyz"))
        val note = InterceptViewModel.reportNote(listOf("brand_lookalike.typo", "upi_intent.pay/../x?y", "reputation.blocklist"))
        assertEquals("LinkGuard warned: brand_lookalike.typo, reputation.blocklist", note)
    }

    private companion object {
        val data: EngineData by lazy { EngineData.loadBundled() }
    }
}
