package com.dwarpallabs.linkguard.core.checks

import com.dwarpallabs.linkguard.core.TestSupport
import com.dwarpallabs.linkguard.core.TestSupport.url
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpiIntentCheckTest {

    private val check = UpiIntentCheck(BrandMatcher(TestSupport.data.brands))

    private fun run(link: String) = check.run(url(link))

    @Test
    fun plainPaymentRequestIsSoft() {
        val s = run("upi://pay?pa=shop.owner@okaxis&pn=Ravi%20Stores&am=250")
        assertEquals(UpiIntentCheck.SCORE, s.score)
        assertEquals("warn_upi_intent", s.messageKey)
        assertEquals("Ravi Stores", s.details["payee_name"])
    }

    @Test
    fun mandateIsStronger() {
        val s = run("upi://mandate?pa=autopay@ybl&pn=Loan%20Settlement&am=1999")
        assertEquals(UpiIntentCheck.MANDATE_SCORE, s.score)
        assertEquals("upi_intent.mandate", s.reason)
        assertEquals("mandate", s.details["kind"])
        assertEquals("upi_intent.mandate", run("tez://upi/mandate?pa=x@okaxis&pn=Gym").reason)
    }

    @Test
    fun brandNameOnSomeoneElsesAccountIsStrong() {
        val s = run("tez://upi/pay?pa=refund.helpdesk@axl&pn=SBI%20Refund%20Cell&am=1")
        assertEquals(UpiIntentCheck.BRAND_SCORE, s.score)
        assertEquals("warn_upi_brand", s.messageKey)
        assertEquals("SBI", s.details["brand"])
        assertEquals("HDFC Bank", run("phonepe://pay?pa=bill.update@ibl&pn=HDFC%20Bank%20KYC").details["brand"])
    }

    @Test
    fun brandAccountOrUnrelatedNameIsNotAMismatch() {
        // The UPI ID belongs to the brand (its keyword is in the address).
        assertNull(run("upi://pay?pa=sbicard.payments@sbi&pn=SBI%20Card").details["brand"])
        // A word that only contains a short brand keyword is not the brand.
        assertNull(run("upi://pay?pa=owner@okaxis&pn=Sbiroti%20Foods").details["brand"])
        assertNull(run("upi://pay?pa=owner@okaxis&pn=Ravi%20Stores").details["brand"])
        assertNull(UpiIntentCheck.brandMismatch("", "x@ybl", BrandMatcher(TestSupport.data.brands)))
    }

    @Test
    fun appSchemesAreUpiAndWebLinksAreNot() {
        for (link in listOf("tez://upi/pay?pa=a@okaxis", "phonepe://pay?pa=a@ybl", "paytmmp://pay?pa=a@paytm")) {
            assertTrue(url(link).isUpi, link)
            assertTrue(run(link).fired, link)
        }
        assertTrue(!run("https://www.npci.org.in/upi/mandate").fired)
    }
}
