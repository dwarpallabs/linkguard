package com.dwarpallabs.linkguard

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.HttpResponse
import com.dwarpallabs.linkguard.core.Http
import com.dwarpallabs.linkguard.core.LinkEngine
import com.dwarpallabs.linkguard.core.ServerVerdicts
import com.dwarpallabs.linkguard.ui.InterstitialModel
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** App-side logic on top of the real bundled engine data, run on the JVM. */
class InterceptLogicTest {

    private val redirects = object : Http {
        override fun head(url: String, timeoutMs: Int) = when (url) {
            "https://bit.ly/scam" -> HttpResponse(301, location = "http://sbi-kyc-verify.xyz/login")
            "https://is.gd/pay" -> HttpResponse(301, location = "upi://pay?pa=collect.now@ybl&pn=Electricity%20Board&am=4999")
            else -> null
        }
        override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int, headers: Map<String, String>): HttpResponse? = null
    }

    private val engine = LinkEngine(data, redirects, null)

    @Test
    fun brandLookalikeModel() {
        val model = InterstitialModel.from(engine.evaluate("http://hdfc-kyc-update.top/verify"))
        assertTrue(model.strong)
        assertEquals("hdfc-kyc-update.top", model.host)
        assertEquals(Reason.BrandLookalike("HDFC Bank"), model.reason)
    }

    @Test
    fun homographHostIsShownInPunycode() {
        val model = InterstitialModel.from(engine.evaluate("http://аirtel.com/recharge"))
        assertEquals("xn--irtel-3ve.com", model.host)
    }

    @Test
    fun upiRedirectModelHasPayeeAndAmountButNoHost() {
        val model = InterstitialModel.from(engine.evaluate("https://is.gd/pay"))
        assertNull(model.host)
        assertEquals(Reason.Upi("Electricity Board", "collect.now@ybl", "4999"), model.reason)
    }

    @Test
    fun forwardsOriginalUrlUnlessRedirectsWereFollowed() {
        val safe = "https://www.irctc.co.in/nget/train-search?x=1"
        assertEquals(safe, InterceptViewModel.forwardUrl(safe, engine.evaluate(safe)))
        assertEquals(
            "http://sbi-kyc-verify.xyz/login",
            InterceptViewModel.forwardUrl("https://bit.ly/scam", engine.evaluate("https://bit.ly/scam")),
        )
    }

    @Test
    fun reportJsonEscapesUserText() {
        val json = ReportClient.json("http://sbi-kyc.xyz/a?b=\"c\"", "got it on\nWhatsApp \\ \u0001", "0.2.0")
        assertEquals(
            "{\"url\":\"http://sbi-kyc.xyz/a?b=\\\"c\\\"\",\"note\":\"got it on\\nWhatsApp \\\\ \\u0001\",\"app_version\":\"0.2.0\"}",
            json,
        )
    }

    @Test
    fun reportIsSentOnlyToTheReportEndpoint() {
        val posts = mutableListOf<String>()
        val http = object : Http {
            override fun head(url: String, timeoutMs: Int): HttpResponse? = null
            override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int, headers: Map<String, String>): HttpResponse? = null
            override fun post(url: String, contentType: String, body: String, timeoutMs: Int): HttpResponse? {
                posts += url
                return HttpResponse(202)
            }
        }
        assertTrue(ReportClient(http, "https://api.example").send("http://x.top/", "", "0.2.0", "x.top", "ab".repeat(32)))
        assertEquals(listOf("https://api.example/v1/report"), posts)
    }

    @Test
    fun reporterTokenIsStablePerDomainAndUnlinkableAcrossDomains() {
        val secret = ReporterToken.newSecret()
        val token = ReporterToken(secret)
        val a1 = token.forDomain("sbi-kyc.top")
        assertEquals(a1, token.forDomain("SBI-KYC.top"))
        assertEquals(64, a1.length)
        assertTrue(a1 != token.forDomain("hdfc-kyc.top"))
        assertTrue(a1 != ReporterToken(ReporterToken.newSecret()).forDomain("sbi-kyc.top"))
    }

    @Test
    fun reportCarriesTokenButNeverTheSecret() {
        val secret = ReporterToken.newSecret()
        val token = ReporterToken(secret).forDomain("sbi-kyc.top")
        val body = ReportClient.json("http://login.sbi-kyc.top/x", "", "0.2.0", "sbi-kyc.top", token)
        assertTrue(body.contains("\"domain\":\"sbi-kyc.top\""))
        assertTrue(body.contains("\"token\":\"$token\""))
        val hexSecret = secret.joinToString("") { "%02x".format(it) }
        val b64Secret = java.util.Base64.getEncoder().encodeToString(secret)
        assertTrue(!body.contains(hexSecret) && !body.contains(b64Secret))
    }

    @Test
    fun communityVerdictBecomesSoftCommunityReason() {
        val h = com.dwarpallabs.linkguard.core.sha256Hex("lg-reported-three.example")
        val engine = LinkEngine(data, null, null, reputation = { _, _ -> ServerVerdicts(reported = mapOf(h to 3)) })
        val model = InterstitialModel.from(engine.evaluate("https://lg-reported-three.example/"))
        assertEquals(Reason.Community(3), model.reason)
        assertTrue(!model.strong)
    }

    private companion object {
        val data: EngineData by lazy { EngineData.loadBundled() }
    }
}
