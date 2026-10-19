package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.MiniJson
import com.dwarpallabs.linkguard.core.NormalisedUrl
import com.dwarpallabs.linkguard.core.PublicSuffixList
import com.dwarpallabs.linkguard.core.checks.BrandMatcher
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The weekly public scam report (roadmap A8), as Markdown. Pure: [WeeklyMain] gathers the inputs.
 *
 * Built only from data that is already public (confirmed.txt, its CHANGELOG.md and
 * reports-summary.json) plus counts about campaigns. It never names a website that is not in the
 * published confirmed.txt.
 */
object WeeklyReport {

    /** One active scam campaign, without its websites: how big it is and when it was reported. */
    data class Campaign(val websites: Int, val firstReport: Long?, val lastReport: Long?)

    data class Week(val id: String, val monday: LocalDate) {
        val sunday: LocalDate get() = monday.plusDays(6)
        val previousId: String get() = idOf(monday.minusWeeks(1))
    }

    /** The week (Monday to Sunday, India time) that ended before [now]. */
    fun lastFullWeek(now: Long): Week {
        val today = Instant.ofEpochSecond(now).atZone(IST).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        return Week(idOf(monday), monday)
    }

    fun idOf(date: LocalDate): String =
        "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun build(
        week: Week,
        summaryJson: String?,
        changelog: String?,
        confirmedTxt: String?,
        campaigns: List<Campaign>,
        matcher: BrandMatcher,
        psl: PublicSuffixList,
    ): String {
        val weeks = summaryWeeks(summaryJson)
        val brands = weeks[week.id].orEmpty()
        val reports = brands.values.sum()
        val before = weeks[week.previousId]?.values?.sum()
        val confirmed = confirmedTxt?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }?.toSet().orEmpty()
        val added = addedBetween(changelog, week.monday, week.sunday).filter { it in confirmed }.distinct().sorted()

        return buildString {
            append("# LinkGuard scam report: week ${week.id.substringAfter("-W").toInt()}, ${week.monday.get(IsoFields.WEEK_BASED_YEAR)}\n\n")
            append("${day(week.monday)} to ${day(week.sunday)} ${week.sunday.year} (India time).\n\n")

            append("## In short\n\n")
            append("- **${n(reports)} ${plural(reports, "report", "reports")}** from LinkGuard users${change(reports, before)}.\n")
            append("- **${n(added.size)} scam ${plural(added.size, "website", "websites")} confirmed** and added to the public list.\n")
            val websites = campaigns.sumOf { it.websites }
            append("- **${n(campaigns.size)} scam ${plural(campaigns.size, "campaign", "campaigns")}** active: groups of scam websites that share their hosting or page (${n(websites)} ${plural(websites, "website", "websites")} in all).\n\n")

            append("## Brands scammers pretended to be\n\n")
            val named = brands.filterKeys { it != OTHER }.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(10)
            if (named.isEmpty()) {
                append("No reports named a brand this week.\n\n")
            } else {
                append("Reports from LinkGuard users, by the brand the scam website imitated.\n\n| Brand | Reports |\n| --- | ---: |\n")
                named.forEach { (b, c) -> append("| ${cell(b)} | ${n(c)} |\n") }
                brands[OTHER]?.let { append("| No brand, or another brand | ${n(it)} |\n") }
                append('\n')
            }

            append("## Newly confirmed scam websites\n\n")
            if (added.isEmpty()) {
                append("None this week.\n\n")
            } else {
                append("Most common endings:\n\n| Ending | Websites | Share |\n| --- | ---: | ---: |\n")
                added.groupingBy { psl.publicSuffix(it) }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5)
                    .forEach { (tld, c) -> append("| .${cell(tld)} | ${n(c)} | ${c * 100 / added.size}% |\n") }
                append('\n')
                val examples = added.mapNotNull { d ->
                    NormalisedUrl.parse("https://$d/", psl)?.let(matcher::matchHost)?.let { d to it.brand.name }
                }.take(10)
                if (examples.isNotEmpty()) {
                    append("Some of them pretended to be:\n\n")
                    examples.forEach { (d, b) -> append("- `$d`: ${cell(b)}\n") }
                    append('\n')
                }
            }

            append("## Campaigns\n\n")
            if (campaigns.isEmpty()) {
                append("No active campaigns this week.\n\n")
            } else {
                append("| Campaign | Websites | Reported between |\n| --- | ---: | --- |\n")
                campaigns.sortedByDescending { it.websites }.take(10).forEachIndexed { i, c ->
                    val span = if (c.firstReport != null && c.lastReport != null) "${dayOf(c.firstReport)} and ${dayOf(c.lastReport)}" else "not reported"
                    append("| ${i + 1} | ${n(c.websites)} | $span |\n")
                }
                append('\n')
            }

            append("## About this report\n\n")
            append("LinkGuard is a free, open-source Android app that checks links before they open. This report counts what LinkGuard users reported and what was confirmed this week. It names only websites that are already on the public list. ")
            append("The data is at [dwarpallabs/linkguard-data](https://github.com/dwarpallabs/linkguard-data) under the ODbL 1.0. ")
            append("Please credit it as: *Scam website data: LinkGuard by Dwarpal Labs (ODbL 1.0), including PhishDestroy (MIT).*\n")
        }
    }

    /** Websites listed under "### Added" in changelog sections dated [from]..[to]. */
    fun addedBetween(changelog: String?, from: LocalDate, to: LocalDate): List<String> {
        if (changelog == null) return emptyList()
        val out = mutableListOf<String>()
        var inWeek = false
        var inAdded = false
        for (line in changelog.lineSequence()) {
            when {
                line.startsWith("## ") -> {
                    val date = runCatching { LocalDate.parse(line.removePrefix("## ").trim()) }.getOrNull()
                    inWeek = date != null && !date.isBefore(from) && !date.isAfter(to)
                    inAdded = false
                }
                line.startsWith("### ") -> inAdded = line.trim() == "### Added"
                inWeek && inAdded && line.startsWith("- ") -> line.removePrefix("- ").trim().takeIf { FeedBuilder.HOST.matches(it) }?.let(out::add)
            }
        }
        return out
    }

    private fun summaryWeeks(json: String?): Map<String, Map<String, Int>> {
        val root = json?.let { runCatching { MiniJson.parse(it) }.getOrNull() } as? Map<*, *> ?: return emptyMap()
        return (root["weeks"] as? List<*>).orEmpty().mapNotNull { w ->
            val m = w as? Map<*, *> ?: return@mapNotNull null
            val id = m["week"] as? String ?: return@mapNotNull null
            id to (m["brands"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> (k as? String)?.let { b -> (v as? Number)?.toInt()?.let { b to it } } }.toMap()
        }.toMap()
    }

    private fun change(now: Int, before: Int?): String {
        if (before == null || before == 0) return ""
        val pct = (now - before) * 100 / before
        return when {
            pct > 0 -> " ($pct% more than the week before)"
            pct < 0 -> " (${-pct}% fewer than the week before)"
            else -> " (as many as the week before)"
        }
    }

    private fun plural(n: Int, one: String, other: String) = if (n == 1) one else other
    private fun n(value: Int) = "%,d".format(Locale.ENGLISH, value)
    private fun cell(s: String) = s.replace("|", "\\|").replace("\n", " ")
    private fun day(d: LocalDate) = d.format(DAY)
    private fun dayOf(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(IST).toLocalDate().format(DAY)

    private const val OTHER = "other"
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
}
