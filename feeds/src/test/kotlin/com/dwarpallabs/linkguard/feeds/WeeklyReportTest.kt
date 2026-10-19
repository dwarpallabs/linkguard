package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.checks.BrandMatcher
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeeklyReportTest {

    private val data = EngineData.loadBundled()
    private val matcher = BrandMatcher(data.brands)
    private val ist = ZoneId.of("Asia/Kolkata")

    // Monday 20 October 2026, 09:00 IST: the report covers Monday 12 to Sunday 18 October.
    private val runAt = LocalDate.of(2026, 10, 20).atTime(9, 0).atZone(ist).toEpochSecond()
    private val week = WeeklyReport.lastFullWeek(runAt)

    private val summary = """{"weeks":[
        {"week":"${week.previousId}","total":10,"brands":{"SBI":6,"other":4}},
        {"week":"${week.id}","total":15,"brands":{"SBI":7,"HDFC Bank":5,"other":3}}]}"""

    private val changelog = """# Changelog

## 2026-10-19

1 added, 0 removed, 3 in total.

### Added

- lg-next-week.example

## 2026-10-15

2 added, 1 removed, 2 in total.

### Added

- sbi-kyc-update.top
- lg-plain-scam.xyz

### Removed

- lg-removed.example

## 2026-10-10

### Added

- lg-week-before.example
"""

    private val confirmed = "# header\nsbi-kyc-update.top\nlg-plain-scam.xyz\nlg-next-week.example\nlg-week-before.example\n"

    private fun report(campaigns: List<WeeklyReport.Campaign> = emptyList(), confirmedTxt: String = confirmed) =
        WeeklyReport.build(week, summary, changelog, confirmedTxt, campaigns, matcher, data.psl)

    @Test
    fun weekIsTheMondayToSundayBeforeTheRun() {
        assertEquals(LocalDate.of(2026, 10, 12), week.monday)
        assertEquals(LocalDate.of(2026, 10, 18), week.sunday)
        assertEquals("2026-W42", week.id)
    }

    @Test
    fun countsBrandsAndTheChange() {
        val md = report()
        assertTrue(md.contains("# LinkGuard scam report: week 42, 2026"), md)
        assertTrue(md.contains("**15 reports** from LinkGuard users (50% more than the week before)"), md)
        assertTrue(md.contains("| SBI | 7 |") && md.contains("| HDFC Bank | 5 |"), md)
        assertTrue(md.indexOf("| SBI |") < md.indexOf("| HDFC Bank |"))
        assertTrue(md.contains("| No brand, or another brand | 3 |"))
    }

    @Test
    fun onlyThisWeeksAddsAndOnlyWebsitesOnThePublicList() {
        val md = report()
        assertTrue(md.contains("**2 scam websites confirmed**"), md)
        assertTrue(md.contains("- `sbi-kyc-update.top`: SBI"), md)
        for (d in listOf("lg-next-week.example", "lg-week-before.example", "lg-removed.example")) assertFalse(md.contains(d), d)
        // Not on the current public list: never named, and not counted.
        val withoutOne = report(confirmedTxt = "lg-plain-scam.xyz\n")
        assertFalse(withoutOne.contains("sbi-kyc-update.top"))
        assertTrue(withoutOne.contains("**1 scam website confirmed**"), withoutOne)
    }

    @Test
    fun endingsAreCountedWithShares() {
        val md = report()
        assertTrue(md.contains("| .top | 1 | 50% |") && md.contains("| .xyz | 1 | 50% |"), md)
    }

    @Test
    fun campaignsAreCountsAndDatesOnly() {
        val day = 86_400L
        val md = report(listOf(WeeklyReport.Campaign(4, runAt - 6 * day, runAt - 2 * day), WeeklyReport.Campaign(9, null, null)))
        assertTrue(md.contains("**2 scam campaigns** active") && md.contains("(13 websites in all)"), md)
        assertTrue(md.indexOf("| 1 | 9 | not reported |") < md.indexOf("| 2 | 4 | 14 October and 18 October |"), md)
    }

    @Test
    fun aQuietWeekStillMakesAReport() {
        val md = WeeklyReport.build(week, null, null, null, emptyList(), matcher, data.psl)
        assertTrue(md.contains("**0 reports**") && md.contains("None this week.") && md.contains("No active campaigns this week."), md)
    }
}
