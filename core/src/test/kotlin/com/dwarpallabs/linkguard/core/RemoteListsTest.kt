package com.dwarpallabs.linkguard.core

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteListsTest {

    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val otherKeys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** Signs like the Worker: WebCrypto ECDSA P-256 / SHA-256, raw r||s, base64. */
    private fun serverSign(body: String, privateKey: java.security.PrivateKey = keys.private): String {
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey); update(body.toByteArray()); sign()
        }
        return Base64.getEncoder().encodeToString(AllowlistBundle.derToP1363(der))
    }

    private val body = "linkguard-allowlist 1 1760000000-2\n" +
        sha256("flipkart.com") + "\n" + sha256("irctc.co.in") + "\n"

    @Test
    fun validBundleVerifies() {
        val parsed = AllowlistBundle.verifyAndParse(body, serverSign(body), keys.public)!!
        assertEquals("1760000000-2", parsed.version)
        assertEquals(setOf(sha256("flipkart.com"), sha256("irctc.co.in")), parsed.hashes)
    }

    @Test
    fun tamperedBundleIsRejected() {
        val signature = serverSign(body)
        val tampered = body + sha256("sbi-kyc-scam.top") + "\n"
        assertNull(AllowlistBundle.verifyAndParse(tampered, signature, keys.public))
    }

    @Test
    fun wrongKeyOrMissingSignatureIsRejected() {
        assertNull(AllowlistBundle.verifyAndParse(body, serverSign(body, otherKeys.private), keys.public))
        assertNull(AllowlistBundle.verifyAndParse(body, null, keys.public))
        assertNull(AllowlistBundle.verifyAndParse(body, "not-base64!!", keys.public))
    }

    @Test
    fun signatureConversionRoundTrips() {
        repeat(50) {
            val der = Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(byteArrayOf(it.toByte())); sign() }
            val raw = AllowlistBundle.derToP1363(der)
            assertEquals(64, raw.size)
            assertTrue(Signature.getInstance("SHA256withECDSA").run {
                initVerify(keys.public); update(byteArrayOf(it.toByte())); verify(AllowlistBundle.p1363ToDer(raw))
            })
        }
    }

    @Test
    fun publicKeyRoundTripsThroughBase64Spki() {
        val spki = Base64.getEncoder().encodeToString(keys.public.encoded)
        assertNotNull(AllowlistBundle.verifyAndParse(body, serverSign(body), AllowlistBundle.publicKey(spki)))
    }

    @Test
    fun hashedAllowlistMatchesHostOrRegistrableDomain() {
        val list = HashedAllowlist()
        assertNull(list.opinion("www.flipkart.com", "flipkart.com"))
        list.replace("v1", setOf(sha256("flipkart.com")))
        assertEquals(Opinion.ALLOW, list.opinion("www.flipkart.com", "flipkart.com"))
        assertEquals(Opinion.ALLOW, list.opinion("flipkart.com", "flipkart.com"))
        assertNull(list.opinion("flipkart.com.evil.top", "evil.top"))
        assertEquals("v1", list.version)
    }

    @Test
    fun downloadedAllowlistTakesEffectWithoutRebuildingTheEngine() {
        val downloaded = HashedAllowlist()
        val data = EngineData.loadBundled(downloadedAllowlist = downloaded)
        val engine = LinkEngine(data, null, null)
        val raw = "https://my-new-shop.xyz/"
        assertEquals(0.4, engine.evaluate(raw).score) // throwaway TLD
        downloaded.replace("v2", setOf(sha256("my-new-shop.xyz")))
        assertEquals(0.0, engine.evaluate(raw).score)
    }

    @Test
    fun restrictedSuffixesAreBuiltIn() {
        val engine = LinkEngine(EngineData.loadBundled(), null, null)
        assertEquals(0.0, engine.evaluate("http://sbi.bank.in/login").score)
        assertTrue(engine.evaluate("http://sbi-bank.in.lg-test.top/login").score >= 0.7)
    }

    @Test
    fun lookupClientSendsOnlyPrefixes() {
        val urls = mutableListOf<String>()
        val http = object : Http {
            override fun head(url: String, timeoutMs: Int): HttpResponse? = null
            override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int, headers: Map<String, String>): HttpResponse? {
                urls += url
                return HttpResponse(200, body = sha256("lg-test-phish.com") + "\n")
            }
        }
        val h = sha256("lg-test-phish.com")
        val result = HttpReputationLookup(http, "https://api.example").lookup(listOf(h.take(8)), 500)
        assertEquals(setOf(h), result?.blocked)
        assertEquals(listOf("https://api.example/v1/lookup?p=${h.take(8)}"), urls)
    }

    @Test
    fun reputationCacheAsksOncePerPrefixUntilExpiry() {
        var calls = 0
        var now = 0L
        val cache = MemoryCachedReputation({ _, _ -> calls++; ServerVerdicts() }, ttlMs = 1_000, clock = { now })
        repeat(3) { cache.lookup(listOf("aaaaaaaa"), 100) }
        assertEquals(1, calls)
        now = 2_000
        cache.lookup(listOf("aaaaaaaa"), 100)
        assertEquals(2, calls)
    }

    @Test
    fun lookupResponseParsing() {
        val a = sha256("a.example"); val b = sha256("b.example"); val c = sha256("c.example")
        val v = ServerVerdicts.parse("$a\tblock\n$b\treported\t7\n$c\nnot-a-hash\treported\t9\n$b\treported\tx")
        assertEquals(setOf(a, c), v.blocked)
        assertEquals(mapOf(b to 7), v.reported)
    }

    @Test
    fun cacheKeepsCommunityCountsPerPrefix() {
        val h = sha256("lg-reported-three.example")
        var calls = 0
        val cache = MemoryCachedReputation({ _, _ -> calls++; ServerVerdicts(reported = mapOf(h to 3)) })
        assertEquals(3, cache.lookup(listOf(h.take(8)), 100)?.reported?.get(h))
        assertEquals(3, cache.lookup(listOf(h.take(8)), 100)?.reported?.get(h))
        assertEquals(1, calls)
    }

    @Test
    fun strongLocalVerdictSkipsServerLookups() {
        val server = FakeReputation(emptySet())
        val engine = LinkEngine(TestSupport.data, null, null, server)
        engine.evaluate("http://hdfc-kyc-update.top/verify") // brand lookalike 0.8 already strong
        assertEquals(emptyList<String>(), server.prefixesSeen)
        engine.evaluate("https://unknown-shop.example/")
        assertTrue(server.prefixesSeen.isNotEmpty())
    }

    @Test
    fun parsesCampaignLinesAndIgnoresUnknownTypes() {
        val a = sha256("lg-campaign-one.example")
        val b = sha256("lg-other.example")
        val v = ServerVerdicts.parse("$a\tcampaign\t7\n$b\tsomething-new\t3\n$b\tcampaign\t0\n$b\tcampaign\tx")
        assertEquals(mapOf(a to 7), v.campaign)
        assertTrue(v.blocked.isEmpty() && v.reported.isEmpty())
    }

    @Test
    fun campaignIsStrongAndRanksBetweenBlocklistAndCommunity() {
        val h = sha256("lg-campaign-one.example")
        fun verdict(sv: ServerVerdicts) = LinkEngine(TestSupport.data, null, null, reputation = { _, _ -> sv })
            .evaluate("http://lg-campaign-one.example/kyc").signals.first { it.check == CheckId.REPUTATION }
        val campaign = verdict(ServerVerdicts(campaign = mapOf(h to 7), reported = mapOf(h to 30)))
        assertEquals(com.dwarpallabs.linkguard.core.checks.ReputationCheck.CAMPAIGN_SCORE, campaign.score)
        assertEquals("warn_campaign", campaign.messageKey)
        assertEquals("7", campaign.details["count"])
        assertTrue(campaign.score > Verdict.STRONG_THRESHOLD)
        assertEquals("warn_reputation", verdict(ServerVerdicts(blocked = setOf(h), campaign = mapOf(h to 7))).messageKey)
    }

    @Test
    fun cacheKeepsCampaignVerdicts() {
        val h = sha256("lg-campaign-one.example")
        var calls = 0
        val cache = MemoryCachedReputation({ _, _ -> calls++; ServerVerdicts(campaign = mapOf(h to 7)) })
        assertEquals(7, cache.lookup(listOf(h.take(8)), 100)?.campaign?.get(h))
        assertEquals(7, cache.lookup(listOf(h.take(8)), 100)?.campaign?.get(h))
        assertEquals(1, calls)
    }

    @Test
    fun partnerSourceIsShownForBlockedDomains() {
        val h = sha256("lg-partner-scam.example")
        val v = ServerVerdicts.parse("$h\tblock\n$h\tsource\tHDFC\u0007 Bank Fraud Desk\n${sha256("x.example")}\tsource\t")
        assertEquals(mapOf(h to "HDFC Bank Fraud Desk"), v.sources)
        val signal = LinkEngine(TestSupport.data, null, null, reputation = { _, _ -> v })
            .evaluate("https://lg-partner-scam.example/kyc").signals.first { it.check == CheckId.REPUTATION }
        assertEquals("warn_reputation", signal.messageKey)
        assertEquals("HDFC Bank Fraud Desk", signal.details["source"])
        // Blocked without a partner: no source.
        val plain = LinkEngine(TestSupport.data, null, null, reputation = { _, _ -> ServerVerdicts(blocked = setOf(h)) })
            .evaluate("https://lg-partner-scam.example/kyc").signals.first { it.check == CheckId.REPUTATION }
        assertTrue(plain.details["source"] == null)
    }
}
