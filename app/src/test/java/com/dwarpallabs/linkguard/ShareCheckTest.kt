package com.dwarpallabs.linkguard

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.Http
import com.dwarpallabs.linkguard.core.HttpResponse
import com.dwarpallabs.linkguard.core.LinkEngine
import com.dwarpallabs.linkguard.core.LinkExtractor
import com.dwarpallabs.linkguard.ui.components.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A shared message goes through the extractor and the real engine; each link becomes one result card. */
class ShareCheckTest {

    private val redirects = object : Http {
        override fun head(url: String, timeoutMs: Int) = when (url) {
            "https://bit.ly/scam" -> HttpResponse(301, location = "http://sbi-kyc-verify.xyz/login")
            "https://is.gd/pay" -> HttpResponse(301, location = "upi://pay?pa=collect.now@ybl&pn=Electricity%20Board&am=4999")
            else -> null
        }
        override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int, headers: Map<String, String>): HttpResponse? = null
    }

    private val engine = LinkEngine(data, redirects, null)

    private fun results(text: String) = LinkExtractor.extract(text, data.psl).links.map { link ->
        ShareCheckActivity.toResult(link, engine.evaluate(link), data.psl)
    }

    @Test
    fun aMixedMessageGivesOneCardPerLinkInOrder() {
        val cards = results(
            "प्रिय ग्राहक, आपका खाता बंद होगा। अभी अपडेट करें hdfc-kyc-update.top/verify। " +
                "IRCTC टिकट: https://www.irctc.co.in/nget/ और रिफंड: https://bit.ly/scam",
        )
        assertEquals(listOf(Status.DANGER, Status.SAFE, Status.DANGER), cards.map { it.status })
        assertEquals("hdfc-kyc-update.top", cards[0].copyName)
        assertEquals("irctc.co.in", cards[1].copyName)
        // A short link shows where it really goes.
        assertEquals("sbi-kyc-verify.xyz", cards[2].model.domain)
        assertTrue(cards.all { it.canReport })
    }

    @Test
    fun aUpiRequestCannotBeReportedOrCopied() {
        val card = results("Pay upi://pay?pa=collect.now@ybl&pn=Electricity%20Board&am=4999 today").single()
        assertTrue(card.status != Status.SAFE)
        assertNull(card.copyName)
        assertFalse(card.canReport)
    }

    @Test
    fun aShortLinkToAUpiRequestDoesNotReportTheShortener() {
        val card = results("Refund: https://is.gd/pay").single()
        assertFalse(card.canReport)
    }

    private companion object {
        val data: EngineData by lazy { EngineData.loadBundled() }
    }
}
