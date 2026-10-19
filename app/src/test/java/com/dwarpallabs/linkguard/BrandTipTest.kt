package com.dwarpallabs.linkguard

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.ui.BrandTip
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap B6: which warnings get the tip, which website it names, and once per brand per 30 days. */
class BrandTipTest {

    private val brands = EngineData.loadBundled().brands

    @Test
    fun fakeBrandWarningsGetTheBrandsRealWebsite() {
        assertEquals(BrandTip("HDFC Bank", "hdfcbank.com"), BrandTip.of(Reason.BrandLookalike("HDFC Bank"), brands))
        // SBI's list starts with the ".sbi" suffix; the tip names its first full domain.
        assertEquals("sbi.co.in", BrandTip.of(Reason.WeirdHost("SBI"), brands)?.site)
        assertEquals("SBI", BrandTip.of(Reason.Upi("SBI Refund", "x@axl", "1", brand = "SBI"), brands)?.brand)
    }

    @Test
    fun brandsWithoutAListedWebsiteGetTheShortTip() {
        val tip = BrandTip.of(Reason.BrandLookalike("EPFO"), brands)
        assertEquals("EPFO", tip?.brand)
        assertNull(tip?.site)
    }

    @Test
    fun otherWarningsGetNoTip() {
        assertNull(BrandTip.of(Reason.DomainAge(3), brands))
        assertNull(BrandTip.of(Reason.WeirdHost(null), brands))
        assertNull(BrandTip.of(Reason.Upi("Shop", "s@ybl", "1"), brands))
        assertNull(BrandTip.of(Reason.ApkDownload, brands))
    }

    @Test
    fun oncePerBrandPerThirtyDays() {
        val kv = object : KeyValue {
            val m = mutableMapOf<String, String>()
            override fun get(key: String) = m[key]
            override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
        }
        val memory = LocalMemory(kv, ReporterToken(ReporterToken.newSecret()))
        val day = 86_400L
        val t0 = 1_800_000_000L
        assertTrue(memory.shouldShowBrandTip("SBI", t0))
        memory.markBrandTipShown("SBI", t0)
        assertFalse(memory.shouldShowBrandTip("SBI", t0 + 29 * day))
        assertTrue(memory.shouldShowBrandTip("HDFC Bank", t0 + day))
        assertTrue(memory.shouldShowBrandTip("SBI", t0 + 30 * day))
        assertFalse(kv.m.values.any { "SBI" in it })
    }
}
