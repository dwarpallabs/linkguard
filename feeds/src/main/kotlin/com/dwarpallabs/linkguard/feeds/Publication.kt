package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.MiniJson
import com.dwarpallabs.linkguard.core.NormalisedUrl
import com.dwarpallabs.linkguard.core.PublicSuffixList
import com.dwarpallabs.linkguard.core.checks.BrandMatcher
import com.dwarpallabs.linkguard.core.sha256Hex
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

/**
 * The published dataset (roadmap A3): what goes into `confirmed.txt`, its `latest.json`, the
 * day's `CHANGELOG.md` section and `reports-summary.json`. Pure functions, so every rule that
 * decides what becomes public is unit-tested; [PublishMain] only reads the database and writes files.
 *
 * Published: scam websites confirmed by Dwarpal Labs (added by a maintainer, or confirmed from
 * user reports) and PhishDestroy's curated list (MIT). Never published: community reports that
 * nobody confirmed, URLhaus entries (their terms don't clearly allow redistribution), dismissed
 * websites and anything on the allowlist.
 */
object Publication {

    /** A blocklist row, as far as publication cares. Times are Unix seconds. */
    data class Row(val domain: String, val source: String, val feed: String?, val addedAt: Long, val lastSeen: Long?)

    /** One counted report: the website and when it was (last) reported. Only ever used to count. */
    data class Report(val domain: String, val createdAt: Long)

    /** A new entry waits this long before it is published, as URLhaus and PhishTank do. */
    const val DELAY_SECONDS = 48 * 60 * 60L

    /** An entry drops out this long after it was last confirmed (added, or seen again in its feed). */
    const val EXPIRY_SECONDS = 30 * 24 * 60 * 60L

    /** Weeks of report counts kept in reports-summary.json. */
    const val SUMMARY_WEEKS = 104

    /** Feeds whose licence allows republishing. */
    val PUBLISHABLE_FEEDS = setOf("phishdestroy")

    /** Sources Dwarpal Labs confirmed itself: a maintainer added it, or confirmed it from reports. */
    val OWN_SOURCES = setOf("manual", "report")

    /** The websites in today's confirmed.txt, sorted. [excluded] = dismissed or allowlisted. */
    fun select(rows: List<Row>, excluded: Set<String>, now: Long): List<String> = rows.asSequence()
        .filter { it.source in OWN_SOURCES || (it.source == "feed" && it.feed in PUBLISHABLE_FEEDS) }
        .filter { now - it.addedAt >= DELAY_SECONDS }
        .filter { now - (it.lastSeen ?: it.addedAt) <= EXPIRY_SECONDS }
        .map { it.domain.lowercase() }
        .filter { FeedBuilder.HOST.matches(it) && '.' in it }
        .filter { it !in excluded }
        .toSortedSet()
        .toList()

    fun confirmedTxt(domains: List<String>, date: String): String = buildString {
        append("# LinkGuard confirmed scam websites, ").append(date).append('\n')
        append("# One website per line. Licence: ODbL 1.0. Includes PhishDestroy (MIT). See README.\n")
        domains.forEach { append(it).append('\n') }
    }

    fun latestJson(confirmedTxt: String, count: Int, generatedAt: Long): String =
        """{"file":"confirmed.txt","sha256":"${sha256Hex(confirmedTxt)}","count":$count,""" +
            """"generated_at":"${Instant.ofEpochSecond(generatedAt)}","delay_hours":${DELAY_SECONDS / 3600},""" +
            """"expiry_days":${EXPIRY_SECONDS / 86_400},"licence":"ODbL-1.0"}""" + "\n"

    /** Today's section on top of the previous changelog; null when nothing changed. */
    fun changelog(previous: List<String>?, current: List<String>, date: String, previousChangelog: String?): String? {
        val before = previous.orEmpty().toSet()
        val now = current.toSet()
        val added = (now - before).sorted()
        val removed = (before - now).sorted()
        if (previous != null && added.isEmpty() && removed.isEmpty()) return null
        val section = buildString {
            append("## ").append(date).append("\n\n")
            append("${added.size} added, ${removed.size} removed, ${current.size} in total.\n")
            if (added.isNotEmpty()) append("\n### Added\n\n").append(added.joinToString("") { "- $it\n" })
            if (removed.isNotEmpty()) append("\n### Removed\n\n").append(removed.joinToString("") { "- $it\n" })
        }
        val header = "# Changelog\n\nDaily additions to and removals from confirmed.txt.\n\n"
        val older = previousChangelog?.substringAfter(header, previousChangelog)?.trim().orEmpty()
        return header + section + (if (older.isEmpty()) "" else "\n$older\n")
    }

    /**
     * Reports per impersonated brand per ISO week (India time), with no websites: a report about a
     * site that imitates no brand counts as "other". Merged with [previousJson] so weeks older than
     * the server keeps reports for stay in the file.
     */
    fun reportsSummary(reports: List<Report>, matcher: BrandMatcher, psl: PublicSuffixList, previousJson: String?, generatedAt: Long): String {
        val weeks = sortedMapOf<String, MutableMap<String, Int>>()
        parse(previousJson).forEach { (week, brands) -> weeks[week] = brands.toMutableMap() }
        val fresh = reports.groupBy { week(it.createdAt) }.mapValues { (_, rows) ->
            rows.groupingBy { brandOf(it.domain, matcher, psl) }.eachCount()
        }
        // The server keeps 30 days of reports, so every week it still has is recomputed from scratch.
        fresh.forEach { (week, brands) -> weeks[week] = brands.toMutableMap() }
        val kept = weeks.entries.toList().takeLast(SUMMARY_WEEKS)
        return buildString {
            append("""{"generated_at":"${Instant.ofEpochSecond(generatedAt)}","timezone":"Asia/Kolkata","weeks":[""")
            kept.forEachIndexed { i, (week, brands) ->
                if (i > 0) append(',')
                append("""{"week":"$week","total":${brands.values.sum()},"brands":{""")
                append(brands.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .joinToString(",") { (b, n) -> "${quote(b)}:$n" })
                append("}}")
            }
            append("]}\n")
        }
    }

    fun week(seconds: Long): String {
        val date = Instant.ofEpochSecond(seconds).atZone(IST).toLocalDate()
        return "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
    }

    private fun brandOf(domain: String, matcher: BrandMatcher, psl: PublicSuffixList): String =
        NormalisedUrl.parse("https://$domain/", psl)?.let(matcher::matchHost)?.brand?.name ?: OTHER

    private fun parse(json: String?): Map<String, Map<String, Int>> {
        val root = json?.let { runCatching { MiniJson.parse(it) }.getOrNull() } as? Map<*, *> ?: return emptyMap()
        val weeks = root["weeks"] as? List<*> ?: return emptyMap()
        return weeks.mapNotNull { w ->
            val m = w as? Map<*, *> ?: return@mapNotNull null
            val week = (m["week"] as? String)?.takeIf { WEEK.matches(it) } ?: return@mapNotNull null
            val brands = (m["brands"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val n = (v as? Number)?.toInt() ?: return@mapNotNull null
                (k as? String)?.let { it to n }
            }?.toMap().orEmpty()
            week to brands
        }.toMap()
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private const val OTHER = "other"
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val WEEK = Regex("""^\d{4}-W\d{2}$""")
}
