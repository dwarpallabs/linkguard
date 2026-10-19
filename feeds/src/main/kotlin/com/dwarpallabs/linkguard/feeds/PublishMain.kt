package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.checks.BrandMatcher
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.system.exitProcess

/**
 * Builds today's public dataset into a folder (roadmap A3):
 *
 *   ./gradlew :feeds:run --args="publish <dir>"
 *
 * Writes confirmed.txt, latest.json, CHANGELOG.md and reports-summary.json for the
 * dwarpallabs/linkguard-data release, or only `unchanged` when nothing differs from the latest
 * release. Needs CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID and LINKGUARD_D1_ID. Reads the
 * previous release's files from GitHub (public) to diff against.
 *
 * Prints counts only. The files themselves are the publication; the CI log is not.
 */
object PublishMain {

    const val DATA_REPO = "dwarpallabs/linkguard-data"

    fun run(args: List<String>) {
        val out = File(args.firstOrNull() ?: run {
            System.err.println("usage: publish <output dir>")
            exitProcess(2)
        })
        val env = System.getenv()
        val missing = listOf("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID", "LINKGUARD_D1_ID").filter { env[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            System.err.println("publish needs: ${missing.joinToString()}")
            exitProcess(2)
        }
        val db = D1(env["CLOUDFLARE_ACCOUNT_ID"]!!, env["LINKGUARD_D1_ID"]!!, env["CLOUDFLARE_API_TOKEN"]!!)
        val result = build(db, ::latestAsset, Instant.now().epochSecond)
        out.mkdirs()
        result.files.forEach { (name, text) -> File(out, name).writeText(text) }
        println("confirmed: ${result.count} websites (${result.added} added, ${result.removed} removed)")
        println("reports summary: ${result.weeks} weeks")
        println(if (result.changed) "changed: publish" else "unchanged: nothing to publish")
    }

    data class Result(
        val files: Map<String, String>,
        val count: Int,
        val added: Int,
        val removed: Int,
        val weeks: Int,
        val changed: Boolean,
    )

    /** Everything but the I/O at the edges, so tests can run it on a fake database. */
    fun build(db: Database, previousAsset: (String) -> String?, now: Long): Result {
        val data = EngineData.loadBundled()
        val rows = blocklistRows(db)
        val excluded = column(db, "SELECT domain FROM dismissed") + column(db, "SELECT domain FROM allowlist")
        val domains = Publication.select(rows, excluded, now)
        val date = Instant.ofEpochSecond(now).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().toString()

        val previousTxt = previousAsset("confirmed.txt")
        val previous = previousTxt?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }?.toList()
        val confirmed = Publication.confirmedTxt(domains, date)
        val changelog = Publication.changelog(previous, domains, date, previousAsset("CHANGELOG.md"))
        val summary = Publication.reportsSummary(reports(db), BrandMatcher(data.brands), data.psl, previousAsset("reports-summary.json"), now)
        val previousSummary = previousAsset("reports-summary.json")

        val listChanged = changelog != null
        val summaryChanged = previousSummary == null || withoutTimestamp(previousSummary) != withoutTimestamp(summary)
        val changed = listChanged || summaryChanged
        val before = previous.orEmpty().toSet()
        val files = if (!changed) {
            mapOf("unchanged" to "")
        } else {
            mapOf(
                "confirmed.txt" to confirmed,
                "latest.json" to Publication.latestJson(confirmed, domains.size, now),
                "CHANGELOG.md" to (changelog ?: previousAsset("CHANGELOG.md").orEmpty()),
                "reports-summary.json" to summary,
            )
        }
        return Result(
            files = files,
            count = domains.size,
            added = domains.count { it !in before },
            removed = before.count { it !in domains.toSet() },
            weeks = Regex("\"week\":").findAll(summary).count(),
            changed = changed,
        )
    }

    private fun withoutTimestamp(json: String) = json.replace(Regex("\"generated_at\":\"[^\"]*\""), "")

    /** Blocklist rows that could be published, read in pages so no response gets large. */
    private fun blocklistRows(db: Database): List<Publication.Row> {
        val rows = mutableListOf<Publication.Row>()
        var after = ""
        while (true) {
            val page = db.query(
                "SELECT domain, source, feed, added_at, last_seen FROM blocklist " +
                    "WHERE (source IN ('manual', 'report') OR (source = 'feed' AND feed IN (${Publication.PUBLISHABLE_FEEDS.joinToString { "'$it'" }}))) " +
                    "AND domain > '${after.also { require(it.isEmpty() || FeedBuilder.HOST.matches(it)) }}' ORDER BY domain LIMIT ${ListWriter.PAGE}",
            )
            page.forEach { r ->
                rows += Publication.Row(
                    domain = r["domain"] as String,
                    source = r["source"] as String,
                    feed = r["feed"] as String?,
                    addedAt = (r["added_at"] as Number).toLong(),
                    lastSeen = (r["last_seen"] as Number?)?.toLong(),
                )
            }
            if (page.size < ListWriter.PAGE) return rows
            after = page.last()["domain"] as String
        }
    }

    /** Counted reports (from builds that send a token), only to be counted per brand and week. */
    private fun reports(db: Database): List<Publication.Report> {
        val rows = mutableListOf<Publication.Report>()
        var afterId = 0L
        while (true) {
            val page = db.query(
                "SELECT id, domain, created_at FROM reports WHERE reporter_token IS NOT NULL AND domain IS NOT NULL " +
                    "AND id > $afterId ORDER BY id LIMIT ${ListWriter.PAGE}",
            )
            page.forEach { r -> rows += Publication.Report(r["domain"] as String, (r["created_at"] as Number).toLong()) }
            if (page.size < ListWriter.PAGE) return rows
            afterId = (page.last()["id"] as Number).toLong()
        }
    }

    private fun column(db: Database, sql: String): Set<String> = db.query(sql).mapNotNull { it["domain"] as? String }.toSet()

    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()

    /** A file from the latest public release, or null before the first one. */
    internal fun latestAsset(name: String): String? {
        val request = HttpRequest.newBuilder(URI("https://github.com/$DATA_REPO/releases/latest/download/$name"))
            .header("User-Agent", "LinkGuard feeds (+https://github.com/dwarpallabs/linkguard)")
            .timeout(Duration.ofMinutes(1))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            200 -> response.body()
            404 -> null
            else -> error("could not read the latest $name: HTTP ${response.statusCode()}")
        }
    }
}
