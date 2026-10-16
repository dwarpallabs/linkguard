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
 * SQL is built with literal values, so every value is validated first ([FeedBuilder.HOST] /
 * [FeedBuilder.DOMAIN] / hex) and anything else is rejected rather than escaped.
 */
class ListWriter(private val db: Database, private val now: () -> Long = { System.currentTimeMillis() / 1000 }) {

    data class Summary(val allowAdded: Int, val allowRemoved: Int, val blockUpserted: Int, val blockExpired: Int)

    fun existingAllowlist(): Map<String, String> =
        db.query("SELECT domain, source FROM allowlist").associate { it["domain"] as String to it["source"] as String }

    fun apply(result: FeedResult, existing: Map<String, String>): Summary {
        val t = now()

        // Allowlist: add new top sites, remove ones that dropped out (feed rows only).
        val currentFeed = existing.filterValues { it == "feed" }.keys
        val toAdd = result.allowDomains - existing.keys
        val toRemove = currentFeed - result.allowDomains
        toAdd.chunked(BATCH).forEach { chunk ->
            db.query("INSERT OR IGNORE INTO allowlist (domain, hash, source, added_at) VALUES " +
                chunk.joinToString(",") { d -> "(${lit(domain(d))}, '${hash(d)}', 'feed', $t)" })
        }
        toRemove.chunked(BATCH).forEach { chunk ->
            db.query("DELETE FROM allowlist WHERE source = 'feed' AND domain IN (" + chunk.joinToString(",") { lit(domain(it)) } + ")")
        }

        // Blocklist: upsert hosts seen today; expiry = 30 days after last seen. A host already on the
        // list from another source keeps that source and is not touched.
        val expires = t + EXPIRY_SECONDS
        result.blockHosts.sorted().chunked(BATCH).forEach { chunk ->
            db.query(
                "INSERT INTO blocklist (domain, hash, prefix, source, added_at, last_seen, expires_at) VALUES " +
                    chunk.joinToString(",") { h ->
                        val hash = hash(host(h))
                        "(${lit(h)}, '$hash', '${hash.take(8)}', 'feed', $t, $t, $expires)"
                    } +
                    " ON CONFLICT (domain) DO UPDATE SET last_seen = excluded.last_seen, expires_at = excluded.expires_at" +
                    " WHERE blocklist.source = 'feed'",
            )
        }
        val expired = db.query("SELECT COUNT(*) AS n FROM blocklist WHERE source = 'feed' AND expires_at < $t")
            .firstOrNull()?.get("n").let { (it as? Number)?.toInt() ?: 0 }
        if (expired > 0) db.query("DELETE FROM blocklist WHERE source = 'feed' AND expires_at < $t")

        return Summary(toAdd.size, toRemove.size, result.blockHosts.size, expired)
    }

    private fun domain(d: String) = d.also { require(FeedBuilder.DOMAIN.matches(it)) { "invalid domain rejected" } }
    private fun host(h: String) = h.also { require(FeedBuilder.HOST.matches(it)) { "invalid host rejected" } }
    private fun lit(validated: String) = "'$validated'" // only ever called on values matching [a-z0-9.-]
    private fun hash(value: String) = com.dwarpallabs.linkguard.core.sha256Hex(value)

    companion object {
        const val BATCH = 250
        const val EXPIRY_SECONDS = 30L * 86_400
    }
}
