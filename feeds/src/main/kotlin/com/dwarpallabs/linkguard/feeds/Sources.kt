package com.dwarpallabs.linkguard.feeds

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

/** Feed downloads. Each source's terms: see README "Data sources". */
object Sources {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private const val USER_AGENT = "LinkGuard feeds (+https://github.com/dwarpallabs/linkguard)"

    /** abuse.ch URLhaus: currently online malware URLs. Free for not-for-profit use with an Auth-Key. */
    fun urlhausOnline(authKey: String): List<String> {
        val request = HttpRequest.newBuilder(URI("https://urlhaus.abuse.ch/downloads/csv_online/"))
            .header("Auth-Key", authKey)
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofMinutes(2))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "URLhaus download failed: HTTP ${response.statusCode()}" }
        return parseUrlhausCsv(response.body())
    }

    /** Tranco research top-sites list (latest), as ranked registrable domains. */
    fun trancoTop(limit: Int): List<String> {
        val request = HttpRequest.newBuilder(URI("https://tranco-list.eu/top-1m.csv.zip"))
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofMinutes(5))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) { "Tranco download failed: HTTP ${response.statusCode()}" }
        return parseTrancoZip(response.body(), limit)
    }

    /** URLhaus CSV: `id,dateadded,url,url_status,...`, quoted fields, `#` comment lines. Returns the URL column. */
    fun parseUrlhausCsv(csv: String): List<String> = csv.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line -> csvFields(line).getOrNull(2)?.takeIf { it.isNotBlank() } }
        .toList()

    /** Tranco zip: one CSV entry of `rank,domain` lines. */
    fun parseTrancoZip(zip: ByteArray, limit: Int): List<String> {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            input.nextEntry ?: error("empty Tranco zip")
            return input.bufferedReader().lineSequence()
                .mapNotNull { it.substringAfter(',', "").trim().takeIf(String::isNotEmpty) }
                .take(limit)
                .toList()
        }
    }

    /** Minimal CSV field splitter: handles quoted fields and doubled quotes. */
    internal fun csvFields(line: String): List<String> {
        val fields = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        fields += sb.toString()
        return fields
    }
}
