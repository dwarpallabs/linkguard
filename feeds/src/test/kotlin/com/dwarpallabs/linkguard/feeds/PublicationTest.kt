package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.checks.BrandMatcher
import com.dwarpallabs.linkguard.core.sha256Hex
import com.dwarpallabs.linkguard.feeds.Publication.Row
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicationTest {

    private val data = EngineData.loadBundled()
    private val day = 86_400L
    private val now = 1_800_000_000L

    private fun row(domain: String, source: String = "manual", feed: String? = null, ageDays: Double = 3.0, seenDaysAgo: Double? = null) =
        Row(domain, source, feed, now - (ageDays * day).toLong(), seenDaysAgo?.let { now - (it * day).toLong() })

    @Test
    fun publishesOwnConfirmationsAndPhishDestroyOnly() {
        val rows = listOf(
            row("lg-manual.example"),
            row("lg-report.example", source = "report"),
            row("lg-phishdestroy.example", source = "feed", feed = "phishdestroy", seenDaysAgo = 1.0),
            row("lg-urlhaus.example", source = "feed", feed = "urlhaus", seenDaysAgo = 1.0),
            row("lg-unknown-feed.example", source = "feed", feed = null, seenDaysAgo = 1.0),
        )
        assertEquals(
            listOf("lg-manual.example", "lg-phishdestroy.example", "lg-report.example"),
            Publication.select(rows, emptySet(), now),
        )
    }

    @Test
    fun fortyEightHourDelay() {
        val rows = listOf(row("lg-new.example", ageDays = 1.9), row("lg-old-enough.example", ageDays = 2.0))
        assertEquals(listOf("lg-old-enough.example"), Publication.select(rows, emptySet(), now))
    }

    @Test
    fun thirtyDayExpiryFromTheLastConfirmation() {
        val rows = listOf(
            row("lg-expired-manual.example", ageDays = 30.5),
            row("lg-fresh-manual.example", ageDays = 29.0),
            // Added long ago but still listed by its feed yesterday: still current.
            row("lg-still-listed.example", source = "feed", feed = "phishdestroy", ageDays = 90.0, seenDaysAgo = 1.0),
            row("lg-gone-from-feed.example", source = "feed", feed = "phishdestroy", ageDays = 90.0, seenDaysAgo = 31.0),
        )
        assertEquals(listOf("lg-fresh-manual.example", "lg-still-listed.example"), Publication.select(rows, emptySet(), now))
    }

    @Test
    fun dismissedAllowlistedAndMalformedAreNeverPublished() {
        val rows = listOf(
            row("lg-dismissed.example"), row("lg-allowlisted.example"), row("LG-Upper.Example"),
            row("bad domain;drop"), row("localhost"),
        )
        assertEquals(listOf("lg-upper.example"), Publication.select(rows, setOf("lg-dismissed.example", "lg-allowlisted.example"), now))
    }

    @Test
    fun latestJsonDescribesTheFile() {
        val txt = Publication.confirmedTxt(listOf("a.example", "b.example"), "2026-10-20")
        val json = Publication.latestJson(txt, 2, now)
        assertTrue(json.contains("\"sha256\":\"${sha256Hex(txt)}\""))
        assertTrue(json.contains("\"count\":2"))
        assertTrue(json.contains("\"delay_hours\":48") && json.contains("\"expiry_days\":30"))
        assertTrue(txt.lines().drop(2).take(2) == listOf("a.example", "b.example"))
    }

    @Test
    fun changelogListsAddsAndRemovesNewestFirst() {
        val first = Publication.changelog(null, listOf("a.example"), "2026-10-20", null)!!
        assertTrue(first.contains("## 2026-10-20") && first.contains("- a.example"))
        val second = Publication.changelog(listOf("a.example"), listOf("b.example"), "2026-10-21", first)!!
        assertTrue(second.indexOf("## 2026-10-21") < second.indexOf("## 2026-10-20"))
        assertTrue(second.contains("1 added, 1 removed, 1 in total."))
        assertEquals(1, Regex("# Changelog").findAll(second).count())
        assertNull(Publication.changelog(listOf("b.example"), listOf("b.example"), "2026-10-22", second))
    }

    @Test
    fun reportsSummaryCountsBrandsPerWeekAndHasNoWebsites() {
        val matcher = BrandMatcher(data.brands)
        val reports = listOf(
            Publication.Report("sbi-kyc-update.top", now),
            Publication.Report("sbi-reward-points.xyz", now - 3600),
            Publication.Report("hdfc-netbanking-verify.top", now),
            Publication.Report("lg-no-brand.example", now),
        )
        val json = Publication.reportsSummary(reports, matcher, data.psl, null, now)
        val week = Publication.week(now)
        assertTrue(json.contains("\"week\":\"$week\",\"total\":4"), json)
        assertTrue(json.contains("\"other\":1"), json)
        for (r in reports) assertFalse(json.contains(r.domain), "website leaked: ${r.domain}")
        assertFalse(json.contains(".top") || json.contains(".xyz") || json.contains(".example"))
    }

    @Test
    fun reportsSummaryKeepsOlderWeeksFromThePreviousFile() {
        val matcher = BrandMatcher(data.brands)
        val previous = """{"generated_at":"x","weeks":[{"week":"2025-W01","total":2,"brands":{"other":2}},{"week":"bad","brands":{}}]}"""
        val json = Publication.reportsSummary(listOf(Publication.Report("lg-x.example", now)), matcher, data.psl, previous, now)
        assertTrue(json.contains("\"week\":\"2025-W01\",\"total\":2"), json)
        assertFalse(json.contains("\"bad\""))
        assertTrue(json.indexOf("2025-W01") < json.indexOf(Publication.week(now)))
    }

    @Test
    fun buildOnAFakeDatabaseAndNothingChangedTheNextDay() {
        val db = Database { sql ->
            when {
                sql.startsWith("SELECT domain, source") && "domain > ''" in sql -> listOf(
                    mapOf("domain" to "lg-manual.example", "source" to "manual", "feed" to null, "added_at" to now - 3 * day, "last_seen" to null),
                    mapOf("domain" to "lg-allowed.example", "source" to "manual", "feed" to null, "added_at" to now - 3 * day, "last_seen" to null),
                )
                sql.startsWith("SELECT domain, source") -> emptyList()
                sql == "SELECT domain FROM dismissed" -> emptyList()
                sql == "SELECT domain FROM allowlist" -> listOf(mapOf("domain" to "lg-allowed.example"))
                sql.startsWith("SELECT id, domain, created_at FROM reports") && "id > 0 " in sql ->
                    listOf(mapOf("id" to 1L, "domain" to "sbi-kyc-update.top", "created_at" to now))
                sql.startsWith("SELECT id, domain") -> emptyList()
                else -> error("unexpected query")
            }
        }
        val first = PublishMain.build(db, { null }, now)
        assertTrue(first.changed)
        assertEquals(setOf("confirmed.txt", "latest.json", "CHANGELOG.md", "reports-summary.json"), first.files.keys)
        assertEquals(1, first.count)
        assertTrue(first.files.getValue("confirmed.txt").contains("lg-manual.example"))
        assertFalse(first.files.getValue("confirmed.txt").contains("lg-allowed.example"))

        val second = PublishMain.build(db, { name -> first.files[name] }, now + 60)
        assertFalse(second.changed)
        assertEquals(setOf("unchanged"), second.files.keys)
    }
}
