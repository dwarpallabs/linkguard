package com.dwarpallabs.linkguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Was that link a scam?" memory: one website name, never a link, deleted on answer or after 7 days. */
class AfterOpenStoreTest {

    private class MapKv : KeyValue {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private var clock = 1_800_000_000_000L
    private val kv = MapKv()
    private val token = ReporterToken(ReporterToken.newSecret())
    private val store = AfterOpenStore(kv, token) { clock }

    @Test
    fun remembersTheWebsiteAndReasons() {
        store.remember("SBI-kyc-verify.xyz", listOf("brand_lookalike.typo", "domain_age.young"))
        assertEquals(PendingQuestion("sbi-kyc-verify.xyz", clock, listOf("brand_lookalike.typo", "domain_age.young")), store.pending())
    }

    @Test
    fun encodeDecodeRoundTripAndRejectsGarbage() {
        val q = PendingQuestion("paytm-kyc-update.in", 42L, listOf("weird_host.many_subdomains"))
        assertEquals(q, PendingQuestion.decode(q.encode()))
        assertEquals(q.copy(reasons = emptyList()), PendingQuestion.decode(q.copy(reasons = emptyList()).encode()))
        assertNull(PendingQuestion.decode(null))
        assertNull(PendingQuestion.decode("https://x.com/a?b\n1\n"))
        assertNull(PendingQuestion.decode("x.com\nnot-a-number\n"))
        assertNull(PendingQuestion.decode("x.com\n1\nok,bad code?"))
    }

    @Test
    fun neverStoresAnythingThatIsNotAWebsiteName() {
        store.remember("https://evil.top/login?otp=1#x", emptyList())
        assertTrue(kv.map.isEmpty())
        store.remember("evil.top", listOf("upi_intent.pay?am=1", "brand_lookalike.typo"))
        val stored = kv.map.values.joinToString("|")
        for (c in listOf("/", "?", "#", "@", "=", "otp", "login")) assertFalse("'$c' in $stored", stored.contains(c))
        assertEquals(listOf("brand_lookalike.typo"), store.pending()!!.reasons)
    }

    @Test
    fun aNewerOpenReplacesTheOlderQuestion() {
        store.remember("first-scam.top", emptyList())
        clock += 1_000
        store.remember("second-scam.top", emptyList())
        assertEquals("second-scam.top", store.pending()!!.domain)
    }

    @Test
    fun expiresAfterSevenDaysAndIsDeleted() {
        store.remember("old-scam.top", emptyList())
        clock += AfterOpenStore.MAX_AGE_MS
        assertEquals("old-scam.top", store.pending()?.domain)
        clock += 1
        assertNull(store.pending())
        assertFalse(kv.map.containsKey(AfterOpenStore.KEY_PENDING))
    }

    @Test
    fun aClockThatWentBackwardsDropsTheQuestion() {
        store.remember("future.top", emptyList())
        clock -= 60_000
        assertNull(store.pending())
    }

    @Test
    fun anAnsweredWebsiteIsNeverAskedAgainAndIsKeptOnlyAsAHash() {
        store.remember("asked-once.top", emptyList())
        store.answer(store.pending()!!)
        assertNull(store.pending())
        store.remember("asked-once.top", emptyList())
        assertNull(store.pending())
        assertFalse(kv.map.values.any { it.contains("asked-once") })
        assertTrue(kv.map[AfterOpenStore.KEY_ASKED]!!.contains(token.forDomain("asked-once.top")))
    }

    @Test
    fun answeringAnOlderQuestionKeepsTheNewerOne() {
        store.remember("older.top", emptyList())
        val older = store.pending()!!
        store.remember("newer.top", emptyList())
        store.answer(older)
        assertEquals("newer.top", store.pending()!!.domain)
    }

    @Test
    fun askedListIsCapped() {
        repeat(AfterOpenStore.MAX_ASKED + 5) { i -> store.answer(PendingQuestion("site$i.top", clock, emptyList())) }
        val asked = kv.map[AfterOpenStore.KEY_ASKED]!!.split('\n')
        assertEquals(AfterOpenStore.MAX_ASKED, asked.size)
        assertFalse(asked.contains(token.forDomain("site0.top")))
        assertTrue(asked.contains(token.forDomain("site${AfterOpenStore.MAX_ASKED + 4}.top")))
    }
}
