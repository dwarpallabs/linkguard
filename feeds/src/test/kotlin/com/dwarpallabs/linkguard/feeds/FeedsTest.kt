package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.sha256Hex
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedsTest {

    private val data = EngineData.loadBundled()
    private val neverAllow = setOf("blogspot.com", "wordpress.com")
    private val urls = Sources.parseUrlhausCsv(javaClass.getResource("/urlhaus_sample.csv")!!.readText())
    private val ranked = listOf("google.com", "lg-popular-site.example", "bit.ly", "blogspot.com", "github.com", "www.example.org", "co.in", "Example.NET.")

    @Test
    fun parsesUrlhausCsv() {
        assertEquals(12, urls.size)
        assertEquals("http://lg-feed-dropper.example/app/KYC-Update.apk", urls[0])
    }

    @Test
    fun csvHandlesQuotesAndCommas() {
        assertEquals(listOf("a", "b,c", "d\"e", ""), Sources.csvFields("\"a\",\"b,c\",\"d\"\"e\","))
    }

    @Test
    fun parsesTrancoZip() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("top-1m.csv"))
                z.write("1,google.com\n2,facebook.com\n3,example.org\n".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()
        assertEquals(listOf("google.com", "facebook.com"), Sources.parseTrancoZip(zip, 2))
    }

    @Test
    fun allowlistKeepsRegistrableTopSitesOnly() {
        val result = FeedBuilder(data, neverAllow, topSites = 100).build(emptyList(), ranked)
        assertEquals(setOf("google.com", "lg-popular-site.example", "example.net"), result.allowDomains)
        // bit.ly (shortener), blogspot.com (never_allow), github.com (file host),
        // www.example.org (not a registrable domain), co.in (public suffix) are all dropped.
    }

    @Test
    fun allowlistRespectsTopN() {
        assertEquals(setOf("google.com"), FeedBuilder(data, neverAllow, topSites = 1).build(emptyList(), ranked).allowDomains)
    }

    @Test
    fun blocklistSkipsSharedAllowedAndBrandHosts() {
        val result = FeedBuilder(data, neverAllow, topSites = 100).build(urls, ranked)
        assertEquals(
            setOf(
                "lg-feed-dropper.example", "198.51.100.23", "mixed-case.lg-feed-host.example",
                "lg-attacker.github.io", "scam-shop.blogspot.com", "login.sbi.bank.in.lg-evil.example",
            ),
            result.blockHosts,
        )
        // github.com / drive.google.com (file hosts), cdn.lg-popular-site.example (top site) and
        // netbanking.hdfcbank.com (brand) are skipped; the IPv6 literal is not a plain hostname.
        // lg-attacker.github.io and scam-shop.blogspot.com are their own registrable domains (both
        // github.io and blogspot.com are public suffixes), so blocking them touches nobody else.
        assertEquals(12, result.stats["malware_urls"])
    }

    @Test
    fun existingManualAllowlistEntriesAreImmune() {
        val result = FeedBuilder(data, neverAllow).build(urls, emptyList(), existingAllow = setOf("lg-feed-dropper.example"))
        assertFalse("lg-feed-dropper.example" in result.blockHosts)
    }

    /** Records SQL instead of sending it. */
    private class FakeDb(private val rows: Map<String, List<Map<String, Any?>>> = emptyMap()) : Database {
        val statements = mutableListOf<String>()
        override fun query(sql: String): List<Map<String, Any?>> {
            statements += sql
            return rows.entries.firstOrNull { sql.startsWith(it.key) }?.value.orEmpty()
        }
    }

    @Test
    fun writerDiffsAllowlistAndTouchesOnlyFeedRows() {
        val db = FakeDb(mapOf("SELECT COUNT(*)" to listOf(mapOf("n" to 2.0))))
        val existing = mapOf("google.com" to "feed", "old-site.example" to "feed", "irctc.co.in" to "seed")
        val result = FeedResult(setOf("lg-feed-dropper.example"), setOf("google.com", "new-site.example"), emptyMap())
        val summary = ListWriter(db, now = { 1_000_000L }).apply(result, existing)
        assertEquals(ListWriter.Summary(allowAdded = 1, allowRemoved = 1, blockUpserted = 1, blockExpired = 2), summary)
        val sql = db.statements.joinToString("\n")
        assertTrue("'new-site.example', '${sha256Hex("new-site.example")}', 'feed', 1000000" in sql)
        assertTrue("DELETE FROM allowlist WHERE source = 'feed' AND domain IN ('old-site.example')" in sql)
        assertFalse("irctc.co.in" in sql)
        assertTrue("WHERE blocklist.source = 'feed'" in sql)
        assertTrue("1000000, 1000000, ${1_000_000L + ListWriter.EXPIRY_SECONDS})" in sql)
        assertTrue("DELETE FROM blocklist WHERE source = 'feed' AND expires_at < 1000000" in sql)
    }

    @Test
    fun writerRejectsAnythingThatIsNotAPlainHostname() {
        val db = FakeDb()
        val evil = FeedResult(setOf("x.example'); DROP TABLE reports; --"), emptySet(), emptyMap())
        assertThrows(IllegalArgumentException::class.java) { ListWriter(db).apply(evil, emptyMap()) }
        assertTrue(db.statements.none { "DROP" in it })
    }

    @Test
    fun writerBatchesLargeLists() {
        val db = FakeDb()
        val many = (1..600).map { "lg-host-$it.example" }.toSet()
        ListWriter(db).apply(FeedResult(many, emptySet(), emptyMap()), emptyMap())
        assertEquals(3, db.statements.count { it.startsWith("INSERT INTO blocklist") })
    }
}
