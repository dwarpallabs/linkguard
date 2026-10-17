package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.MiniJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Runs SQL against the LinkGuard D1 database through Cloudflare's REST API. */
fun interface Database {
    /** Executes one SQL statement and returns its result rows. */
    fun query(sql: String): List<Map<String, Any?>>
}

class D1(private val accountId: String, private val databaseId: String, private val apiToken: String) : Database {

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

    override fun query(sql: String): List<Map<String, Any?>> {
        val body = """{"sql":${jsonString(sql)}}"""
        val request = HttpRequest.newBuilder(
            URI("https://api.cloudflare.com/client/v4/accounts/$accountId/d1/database/$databaseId/query"),
        )
            .header("Authorization", "Bearer $apiToken")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMinutes(2))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val json = MiniJson.parse(response.body()) as? Map<*, *>
        // Never echo the SQL or response body: they can contain list entries, and CI logs are public.
        check(response.statusCode() == 200 && json?.get("success") == true) { "D1 query failed: HTTP ${response.statusCode()}" }
        val result = (json["result"] as? List<*>)?.firstOrNull() as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        return (result?.get("results") as? List<Map<String, Any?>>).orEmpty()
    }

    private fun jsonString(s: String) = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }
}

/**
 * Applies a [FeedResult] to the server lists. Only rows with `source = 'feed'` are ever inserted,
 * refreshed or deleted: manual, report, seed and brand entries are never touched.
 *
 * Writes are kept small (D1 bills every row written, plus its indexes and the version trigger):
 * only hosts not yet on the list are inserted, at most [maxNew] per run, and a host already listed
 * is refreshed only once its last refresh is [REFRESH_AFTER_SECONDS] old. Feed entries expire
 * [EXPIRY_SECONDS] after they were last refreshed.
 *
 * SQL is built with literal values, so every value is validated first ([FeedBuilder.HOST] /
 * [FeedBuilder.DOMAIN] / [FeedBuilder.FEED_NAME] / hex) and anything else is rejected rather than escaped.
 */
class ListWriter(
    private val db: Database,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val maxNew: Int = DEFAULT_MAX_NEW,
) {

    data class Summary(
        val allowAdded: Int,
        val allowRemoved: Int,
        val blockInserted: Int,
        val blockRefreshed: Int,
        val blockDeferred: Int,
        val blockExpired: Int,
        val feedsSkipped: List<String>,
    )

    /** One existing blocklist row, as far as the writer cares. */
    data class Listed(val source: String, val feed: String?, val lastSeen: Long)

    fun existingAllowlist(): Map<String, String> =
        db.query("SELECT domain, source FROM allowlist").associate { it["domain"] as String to it["source"] as String }

    /** Every blocklist row, read in pages so no single response gets large. */
    fun existingBlocklist(): Map<String, Listed> {
        val out = HashMap<String, Listed>()
        var after = ""
        while (true) {
            val page = db.query(
                "SELECT domain, source, feed, last_seen FROM blocklist WHERE domain > ${lit(after)} ORDER BY domain LIMIT $PAGE",
            )
            for (r in page) {
                out[r["domain"] as String] = Listed(r["source"] as String, r["feed"] as String?, (r["last_seen"] as? Number)?.toLong() ?: 0L)
            }
            if (page.size < PAGE) return out
            after = (page.last()["domain"] as String).also { require(FeedBuilder.HOST.matches(it)) { "unexpected domain in blocklist" } }
        }
    }

    /**
     * Feeds whose size today is implausible next to what the list already holds from them (a broken
     * download or a format change): they are left alone today and their rows age out normally.
     */
    fun implausibleFeeds(result: FeedResult, existing: Map<String, Listed>): List<String> {
        val before = existing.values.filter { it.source == "feed" }.groupingBy { it.feed }.eachCount()
        val today = result.blockHosts.values.groupingBy { it }.eachCount()
        return (before.keys.filterNotNull() + today.keys).distinct().sorted().filter { feed ->
            val was = before[feed] ?: 0
            val now = today[feed] ?: 0
            was >= SANITY_MIN && (now < was / 5 || now > was * 3)
        }
    }

    fun apply(result: FeedResult, existingAllow: Map<String, String>, existingBlock: Map<String, Listed>): Summary {
        val t = now()

        // Allowlist: add new top sites, remove ones that dropped out (feed rows only).
        val currentFeed = existingAllow.filterValues { it == "feed" }.keys
        val toAdd = result.allowDomains - existingAllow.keys
        val toRemove = currentFeed - result.allowDomains
        toAdd.chunked(BATCH).forEach { chunk ->
            db.query("INSERT OR IGNORE INTO allowlist (domain, hash, source, added_at) VALUES " +
                chunk.joinToString(",") { d -> "(${lit(domain(d))}, '${hash(d)}', 'feed', $t)" })
        }
        toRemove.chunked(BATCH).forEach { chunk ->
            db.query("DELETE FROM allowlist WHERE source = 'feed' AND domain IN (" + chunk.joinToString(",") { lit(domain(it)) } + ")")
        }

        // Blocklist.
        val skipped = implausibleFeeds(result, existingBlock)
        val wanted = result.blockHosts.filterValues { it !in skipped }
        val expires = t + EXPIRY_SECONDS
        val fresh = wanted.keys.filter { it !in existingBlock }.sorted()
        val inserted = fresh.take(maxNew)
        inserted.chunked(BATCH).forEach { chunk ->
            db.query(
                "INSERT OR IGNORE INTO blocklist (domain, hash, prefix, source, feed, added_at, last_seen, expires_at) VALUES " +
                    chunk.joinToString(",") { h ->
                        val hash = hash(host(h))
                        "(${lit(h)}, '$hash', '${hash.take(8)}', 'feed', ${lit(feed(wanted.getValue(h)))}, $t, $t, $expires)"
                    },
            )
        }
        val stale = wanted.keys.filter { h ->
            existingBlock[h]?.let { it.source == "feed" && t - it.lastSeen >= REFRESH_AFTER_SECONDS } == true
        }.sorted()
        stale.chunked(BATCH).forEach { chunk ->
            db.query(
                "UPDATE blocklist SET last_seen = $t, expires_at = $expires WHERE source = 'feed' AND domain IN (" +
                    chunk.joinToString(",") { lit(host(it)) } + ")",
            )
        }
        val expired = db.query("SELECT COUNT(*) AS n FROM blocklist WHERE source = 'feed' AND expires_at < $t")
            .firstOrNull()?.get("n").let { (it as? Number)?.toInt() ?: 0 }
        if (expired > 0) db.query("DELETE FROM blocklist WHERE source = 'feed' AND expires_at < $t")

        return Summary(toAdd.size, toRemove.size, inserted.size, stale.size, fresh.size - inserted.size, expired, skipped)
    }

    private fun domain(d: String) = d.also { require(FeedBuilder.DOMAIN.matches(it)) { "invalid domain rejected" } }
    private fun host(h: String) = h.also { require(FeedBuilder.HOST.matches(it)) { "invalid host rejected" } }
    private fun feed(f: String) = f.also { require(FeedBuilder.FEED_NAME.matches(it)) { "invalid feed name rejected" } }
    private fun lit(validated: String) = "'$validated'" // only ever called on values matching [a-z0-9.-]
    private fun hash(value: String) = com.dwarpallabs.linkguard.core.sha256Hex(value)

    companion object {
        const val BATCH = 250
        const val PAGE = 20_000
        const val EXPIRY_SECONDS = 30L * 86_400
        const val REFRESH_AFTER_SECONDS = 7L * 86_400
        /** Inserts per run; a big new feed fills in over several nights rather than in one burst. */
        const val DEFAULT_MAX_NEW = 100_000
        /** Feeds smaller than this are too small for the size check to mean anything. */
        const val SANITY_MIN = 1_000
    }
}
