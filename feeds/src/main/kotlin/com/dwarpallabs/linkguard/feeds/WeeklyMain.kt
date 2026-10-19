package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.checks.BrandMatcher
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Writes last week's public scam report (roadmap A8) as `<dir>/reports/<YYYY-Www>.md`:
 *
 *   ./gradlew :feeds:run --args="weekly <dir>"
 *
 * Inputs: the latest dwarpallabs/linkguard-data release (public) and campaign counts from the
 * server database (CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, LINKGUARD_D1_ID). Prints counts only.
 */
object WeeklyMain {

    fun run(args: List<String>) {
        val out = File(args.firstOrNull() ?: run {
            System.err.println("usage: weekly <output dir>")
            exitProcess(2)
        })
        val env = System.getenv()
        val missing = listOf("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID", "LINKGUARD_D1_ID").filter { env[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            System.err.println("weekly needs: ${missing.joinToString()}")
            exitProcess(2)
        }
        val db = D1(env["CLOUDFLARE_ACCOUNT_ID"]!!, env["LINKGUARD_D1_ID"]!!, env["CLOUDFLARE_API_TOKEN"]!!)
        val week = WeeklyReport.lastFullWeek(Instant.now().epochSecond)
        val data = EngineData.loadBundled()
        val campaigns = campaigns(db)
        val report = WeeklyReport.build(
            week,
            summaryJson = PublishMain.latestAsset("reports-summary.json"),
            changelog = PublishMain.latestAsset("CHANGELOG.md"),
            confirmedTxt = PublishMain.latestAsset("confirmed.txt"),
            campaigns = campaigns,
            matcher = BrandMatcher(data.brands),
            psl = data.psl,
        )
        val file = File(out, "reports/${week.id}.md").apply { parentFile.mkdirs() }
        file.writeText(report)
        println("weekly report ${week.id}: ${report.lines().size} lines, ${campaigns.size} campaigns")
    }

    /** One row per campaign: how many websites, and when their reports came in. Never the websites. */
    fun campaigns(db: Database): List<WeeklyReport.Campaign> = db.query(
        "SELECT c.cluster AS cluster, COUNT(DISTINCT c.domain) AS websites, MIN(r.created_at) AS first, MAX(r.created_at) AS last " +
            "FROM campaigns c LEFT JOIN reports r ON r.domain = c.domain AND r.reporter_token IS NOT NULL GROUP BY c.cluster",
    ).map { r ->
        WeeklyReport.Campaign(
            websites = (r["websites"] as Number).toInt(),
            firstReport = (r["first"] as Number?)?.toLong(),
            lastReport = (r["last"] as Number?)?.toLong(),
        )
    }
}
