package com.dwarpallabs.linkguard

import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.LinkEngine
import com.dwarpallabs.linkguard.core.Signal
import com.dwarpallabs.linkguard.core.Verdict
import com.dwarpallabs.linkguard.ui.InterstitialModel
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap A6: "trusted by me" and "my brands", kept only as HMACs on the phone. */
class LocalMemoryTest {

    private class MapKv : KeyValue {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private val kv = MapKv()
    private val memory = LocalMemory(kv, ReporterToken(ReporterToken.newSecret()))

    @Test
    fun trustedWebsitesAreCountedAndClearedButNeverNamed() {
        memory.trust("lg-fine-shop.example")
        memory.trust("LG-Fine-Shop.example")
        memory.trust("lg-other.example")
        assertTrue(memory.isTrusted("lg-fine-shop.example"))
        assertEquals(2, memory.trustedCount())
        assertFalse(kv.map.values.any { "lg-" in it || "example" in it })
        memory.untrust("lg-fine-shop.example")
        assertFalse(memory.isTrusted("lg-fine-shop.example"))
        memory.clearTrusted()
        assertEquals(0, memory.trustedCount())
    }

    @Test
    fun aBrandBecomesMineOnTheThirdOpenAndIsStoredOnlyAsAHash() {
        val all = data.brands.map { it.name }
        assertFalse(memory.countOpen("SBI"))
        assertFalse(memory.countOpen("SBI"))
        assertEquals(emptySet<String>(), memory.myBrands(all))
        assertTrue(memory.countOpen("SBI"))
        assertFalse(memory.countOpen("SBI"))
        assertEquals(setOf("SBI"), memory.myBrands(all))
        assertFalse(kv.map.values.any { "SBI" in it || "sbi" in it })
        memory.clearBrands()
        assertEquals(emptySet<String>(), memory.myBrands(all))
    }

    @Test
    fun garbageInTheFileIsIgnored() {
        kv.map[LocalMemory.KEY_TRUSTED] = "not-a-hash\n"
        kv.map[LocalMemory.KEY_BRANDS] = "x=3\nnonsense"
        assertEquals(0, memory.trustedCount())
        assertEquals(emptySet<String>(), memory.myBrands(data.brands.map { it.name }))
    }

    @Test
    fun trustSilencesOnlySoftWarningsWithoutApkOrUpi() {
        fun verdict(vararg signals: Signal) = Verdict.of(signals.toList(), null)
        val soft = Signal(CheckId.DOMAIN_AGE, 0.5, "domain_age.young", "warn_domain_age")
        val strong = Signal(CheckId.BRAND_LOOKALIKE, 0.8, "brand_lookalike.keyword", "warn_brand_lookalike")
        val upi = Signal(CheckId.UPI_INTENT, 0.7, "upi_intent.pay", "warn_upi_intent")
        val apk = Signal(CheckId.APK_DOWNLOAD, 0.9, "apk_download.ext", "warn_apk_download")
        assertTrue(InterceptViewModel.trustSilences(verdict(soft)))
        assertFalse(InterceptViewModel.trustSilences(verdict(strong)))
        assertFalse(InterceptViewModel.trustSilences(verdict(upi)))
        assertFalse(InterceptViewModel.trustSilences(verdict(soft, apk)))
        assertFalse(InterceptViewModel.trustSilences(verdict()))
    }

    @Test
    fun aFakeOfMyBrandGetsTheSharperSentence() {
        val engine = LinkEngine(data, null, null)
        val model = InterstitialModel.from(engine.evaluate("http://hdfc-kyc-update.top/verify", myBrands = setOf("HDFC Bank")))
        assertEquals(Reason.BrandLookalike("HDFC Bank", mine = true), model.reason)
        assertTrue(model.strong)
    }

    private companion object {
        val data: EngineData by lazy { EngineData.loadBundled() }
    }
}
