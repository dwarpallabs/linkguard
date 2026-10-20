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
        val result = FeedBuilder(data, neverAllow, topSites = 100).build(emptyMap(), ranked)
        assertEquals(setOf("google.com", "lg-popular-site.example", "example.net"), result.allowDomains)
        // bit.ly (shortener), blogspot.com (never_allow), github.com (file host),
        // www.example.org (not a registrable domain), co.in (public suffix) are all dropped.
    }

    @Test
    fun allowlistRespectsTopN() {
        assertEquals(setOf("google.com"), FeedBuilder(data, neverAllow, topSites = 1).build(emptyMap(), ranked).allowDomains)
    }

    @Test
    fun blocklistSkipsSharedAllowedAndBrandHosts() {
        val result = FeedBuilder(data, neverAllow, topSites = 100).build(mapOf("urlhaus" to urls), ranked)
        assertEquals(
            setOf(
                "lg-feed-dropper.example", "198.51.100.23", "mixed-case.lg-feed-host.example",
                "lg-attacker.github.io", "scam-shop.blogspot.com", "login.sbi.bank.in.lg-evil.example",
            ),
            result.blockHosts.keys,
        )
        assertTrue(result.blockHosts.values.all { it == "urlhaus" })
        // github.com / drive.google.com (file hosts), cdn.lg-popular-site.example (top site) and
        // netbanking.hdfcbank.com (brand) are skipped; the IPv6 literal is not a plain hostname.
        // lg-attacker.github.io and scam-shop.blogspot.com are their own registrable domains (both
        // github.io and blogspot.com are public suffixes), so blocking them touches nobody else.
        assertEquals(12, result.stats["urlhaus_entries"])
    }

    @Test
    fun existingManualAllowlistEntriesAreImmune() {
        val result = FeedBuilder(data, neverAllow).build(mapOf("urlhaus" to urls), emptyList(), existingAllow = setOf("lg-feed-dropper.example"))
        assertFalse("lg-feed-dropper.example" in result.blockHosts)
    }

    @Test
    fun parsesPhishDestroyDomainList() {
        assertEquals(listOf("lg-fake-kyc.example", "lg-upi-refund.example"), Sources.parseDomainList("# header\nLG-Fake-KYC.example\n\nlg-upi-refund.example  # note\n"))
    }

    @Test
    fun bareDomainsFromAPhishingFeedAreBlockedButPopularSitesNeverAre() {
        val feeds = mapOf("phishdestroy" to listOf("lg-fake-kyc.example", "lg-ranked-but-not-allowed.example", "sub.lg-ranked-but-not-allowed.example"))
        // Ranked 3rd, but only the top 2 are allowlisted: it is still immune from blocking.
        val result = FeedBuilder(data, neverAllow, topSites = 2).build(feeds, listOf("google.com", "example.net", "lg-ranked-but-not-allowed.example"))
        assertEquals(mapOf("lg-fake-kyc.example" to "phishdestroy"), result.blockHosts)
        assertFalse("lg-ranked-but-not-allowed.example" in result.allowDomains)
        assertEquals(2, result.stats["phishdestroy_skipped_shared_or_allowed"])
    }

    @Test
    fun aHostListedByTwoFeedsKeepsTheFirst() {
        val feeds = linkedMapOf("urlhaus" to listOf("http://lg-both.example/x.apk"), "phishdestroy" to listOf("lg-both.example"))
        val result = FeedBuilder(data, neverAllow).build(feeds, emptyList())
        assertEquals(mapOf("lg-both.example" to "urlhaus"), result.blockHosts)
        assertEquals(0, result.stats["phishdestroy_block_hosts"])
        // Still commercial-safe: PhishDestroy lists it too, even though URLhaus was first.
        assertEquals(setOf("lg-both.example"), result.commercialHosts)
    }

    @Test
    fun urlhausOnlyHostsAreNotCommercial() {
        val feeds = linkedMapOf(
            "urlhaus" to listOf("http://lg-malware.example/a.apk"),
            "phishtank" to listOf("http://lg-phish.example/login"),
        )
        val result = FeedBuilder(data, neverAllow).build(feeds, emptyList())
        assertEquals(setOf("lg-phish.example"), result.commercialHosts)
        assertEquals(1, result.stats["block_hosts_commercial"])
    }

    /** Records SQL instead of sending it. */
    private class FakeDb(private val rows: Map<String, List<Map<String, Any?>>> = emptyMap()) : Database {
        val statements = mutableListOf<String>()
        override fun query(sql: String): List<Map<String, Any?>> {
            statements += sql
            return rows.entries.firstOrNull { sql.startsWith(it.key) }?.value.orEmpty()
        }
    }

    private val t = 10_000_000L

    @Test
    fun writerDiffsAllowlistAndTouchesOnlyFeedRows() {
        val db = FakeDb(mapOf("SELECT COUNT(*)" to listOf(mapOf("n" to 2.0))))
        val existing = mapOf("google.com" to "feed", "old-site.example" to "feed", "irctc.co.in" to "seed")
        val result = FeedResult(mapOf("lg-feed-dropper.example" to "phishdestroy"), setOf("google.com", "new-site.example"), emptyMap())
        val summary = ListWriter(db, now = { t }).apply(result, existing, emptyMap())
        assertEquals(ListWriter.Summary(1, 1, blockInserted = 1, blockRefreshed = 0, blockDeferred = 0, blockExpired = 2, feedsSkipped = emptyList()), summary)
        val sql = db.statements.joinToString("\n")
        assertTrue("'new-site.example', '${sha256Hex("new-site.example")}', 'feed', $t" in sql)
        assertTrue("DELETE FROM allowlist WHERE source = 'feed' AND domain IN ('old-site.example')" in sql)
        assertFalse("irctc.co.in" in sql)
        assertTrue("'feed', 'phishdestroy', $t, $t, ${t + ListWriter.EXPIRY_SECONDS}, 0)" in sql)
        assertTrue("DELETE FROM blocklist WHERE source = 'feed' AND expires_at < $t" in sql)
    }

    @Test
    fun writerSetsTheCommercialFlagOnlyWhereItChanged() {
        val db = FakeDb()
        val existing = mapOf(
            "lg-now-commercial.example" to ListWriter.Listed("feed", "urlhaus", t, commercial = false),
            "lg-no-longer.example" to ListWriter.Listed("feed", "phishdestroy", t, commercial = true),
            "lg-unchanged.example" to ListWriter.Listed("feed", "phishdestroy", t, commercial = true),
            "lg-manual.example" to ListWriter.Listed("manual", null, t, commercial = true),
        )
        val hosts = (existing.keys + "lg-new-commercial.example").associateWith { "urlhaus" }
        val commercial = setOf("lg-now-commercial.example", "lg-unchanged.example", "lg-new-commercial.example")
        val summary = ListWriter(db, now = { t }).apply(FeedResult(hosts, emptySet(), emptyMap(), commercial), emptyMap(), existing)
        assertEquals(2, summary.commercialChanged)
        val sql = db.statements.joinToString("\n")
        assertTrue("UPDATE blocklist SET commercial = 1 WHERE source = 'feed' AND domain IN ('lg-now-commercial.example')" in sql)
        assertTrue("UPDATE blocklist SET commercial = 0 WHERE source = 'feed' AND domain IN ('lg-no-longer.example')" in sql)
        assertTrue("'lg-new-commercial.example'" in sql && ", 1)" in sql)
        assertFalse("lg-unchanged.example" in sql)
        assertFalse("lg-manual.example" in sql)
    }

    @Test
    fun writerInsertsOnlyNewHostsAndRefreshesWeekOldOnes() {
        val db = FakeDb()
        val existing = mapOf(
            "lg-recent.example" to ListWriter.Listed("feed", "urlhaus", t - 86_400),
            "lg-week-old.example" to ListWriter.Listed("feed", "urlhaus", t - ListWriter.REFRESH_AFTER_SECONDS),
            "lg-manual.example" to ListWriter.Listed("manual", null, 0),
        )
        val hosts = listOf("lg-new.example", "lg-recent.example", "lg-week-old.example", "lg-manual.example").associateWith { "urlhaus" }
        val summary = ListWriter(db, now = { t }).apply(FeedResult(hosts, emptySet(), emptyMap()), emptyMap(), existing)
        assertEquals(1, summary.blockInserted)
        assertEquals(1, summary.blockRefreshed)
        val sql = db.statements.joinToString("\n")
        assertTrue("INSERT OR IGNORE INTO blocklist" in sql && "'lg-new.example'" in sql)
        assertTrue("UPDATE blocklist SET last_seen = $t, expires_at = ${t + ListWriter.EXPIRY_SECONDS} WHERE source = 'feed' AND domain IN ('lg-week-old.example')" in sql)
        assertFalse("lg-recent.example" in sql)
        assertFalse("lg-manual.example" in sql)
    }

    @Test
    fun writerCapsInsertsPerRun() {
        val db = FakeDb()
        val hosts = (1..30).associate { "lg-host-$it.example" to "phishdestroy" }
        val summary = ListWriter(db, now = { t }, maxNew = 10).apply(FeedResult(hosts, emptySet(), emptyMap()), emptyMap(), emptyMap())
        assertEquals(10, summary.blockInserted)
        assertEquals(20, summary.blockDeferred)
    }

    @Test
    fun aFeedThatSuddenlyShrinksOrBalloonsIsLeftAloneThatDay() {
        val listed = (1..2_000).associate { "lg-old-$it.example" to ListWriter.Listed("feed", "phishdestroy", t) } +
            (1..2_000).associate { "lg-mal-$it.example" to ListWriter.Listed("feed", "urlhaus", t) }
        // phishdestroy collapses to 100 entries (broken download); urlhaus is normal.
        val today = (1..100).associate { "lg-new-$it.example" to "phishdestroy" } + (1..2_000).associate { "lg-mal-$it.example" to "urlhaus" }
        val db = FakeDb()
        val summary = ListWriter(db, now = { t }).apply(FeedResult(today, emptySet(), emptyMap()), emptyMap(), listed)
        assertEquals(listOf("phishdestroy"), summary.feedsSkipped)
        assertEquals(0, summary.blockInserted)
        assertTrue(db.statements.none { "lg-new-" in it })
    }

    @Test
    fun existingBlocklistIsReadInPages() {
        val page = (1..ListWriter.PAGE).map { mapOf("domain" to "lg-$it.example", "source" to "feed", "feed" to "urlhaus", "last_seen" to 1.0) }
        var calls = 0
        val db = Database { sql ->
            calls++
            if ("domain > ''" in sql) page else listOf(mapOf("domain" to "zz.example", "source" to "manual", "feed" to null, "last_seen" to null))
        }
        val all = ListWriter(db).existingBlocklist()
        assertEquals(ListWriter.PAGE + 1, all.size)
        assertEquals(2, calls)
        assertEquals(ListWriter.Listed("manual", null, 0), all["zz.example"])
    }

    @Test
    fun writerRejectsAnythingThatIsNotAPlainHostname() {
        val db = FakeDb()
        val evil = FeedResult(mapOf("x.example'); DROP TABLE reports; --" to "urlhaus"), emptySet(), emptyMap())
        assertThrows(IllegalArgumentException::class.java) { ListWriter(db).apply(evil, emptyMap(), emptyMap()) }
        assertTrue(db.statements.none { "DROP" in it })
    }

    @Test
    fun writerRejectsAnUnsafeFeedName() {
        val evil = FeedResult(mapOf("lg-x.example" to "x'); DROP TABLE reports; --"), emptySet(), emptyMap())
        assertThrows(IllegalArgumentException::class.java) { ListWriter(FakeDb()).apply(evil, emptyMap(), emptyMap()) }
    }

    @Test
    fun writerBatchesLargeLists() {
        val db = FakeDb()
        val many = (1..600).associate { "lg-host-$it.example" to "urlhaus" }
        ListWriter(db).apply(FeedResult(many, emptySet(), emptyMap()), emptyMap(), emptyMap())
        assertEquals(3, db.statements.count { it.startsWith("INSERT OR IGNORE INTO blocklist") })
    }

    @Test
    fun parsesPhishTankVerifiedOnlineRowsOnly() {
        val urls = Sources.parsePhishTankCsv(javaClass.getResource("/phishtank_sample.csv")!!.readText())
        assertEquals(
            listOf(
                "http://lg-pt-bank-verify.example/login.php",
                "https://lg-pt-quoted.example/a,b",
                "https://sites.google.com/view/lg-pt-shared-host",
            ),
            urls,
        )
    }

    @Test
    fun phishTankHostsAreBlockedButNeverASharedPlatform() {
        val urls = Sources.parsePhishTankCsv(javaClass.getResource("/phishtank_sample.csv")!!.readText())
        val result = FeedBuilder(data, neverAllow).build(mapOf("phishtank" to urls), ranked)
        assertEquals(mapOf("lg-pt-bank-verify.example" to "phishtank", "lg-pt-quoted.example" to "phishtank"), result.blockHosts)
        assertEquals(1, result.stats["phishtank_skipped_shared_or_allowed"])
    }

    @Test
    fun phishTankIsNeverPublished() {
        assertFalse("phishtank" in Publication.PUBLISHABLE_FEEDS)
        val row = Publication.Row("lg-pt-bank-verify.example", "feed", "phishtank", 0, 0)
        assertTrue(Publication.select(listOf(row), emptySet(), 3 * 86_400L).isEmpty())
    }
}
