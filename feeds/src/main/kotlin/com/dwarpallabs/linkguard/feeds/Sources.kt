package com.dwarpallabs.linkguard.feeds

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

/** Feed downloads. Each source's terms: see feeds/SOURCES.md. */
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

    /**
     * PhishDestroy destroylist (MIT): phishing and scam root domains that currently resolve
     * ("online"), curated, hosting providers excluded. https://github.com/phishdestroy/destroylist
     */
    fun phishDestroyOnline(): List<String> {
        val request = HttpRequest.newBuilder(URI("https://raw.githubusercontent.com/phishdestroy/destroylist/main/rootlist/online_root_domains.txt"))
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofMinutes(2))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "PhishDestroy download failed: HTTP ${response.statusCode()}" }
        return parseDomainList(response.body())
    }

    /**
     * PhishTank (Cisco Talos): verified phishing URLs that are currently online. Free, including
     * commercial use (FAQ). Its key registration is closed, and without a key it allows a few
     * downloads a day, so this runs once a day. Entries are kept private, never republished: the
     * terms don't clearly allow redistribution (see feeds/SOURCES.md).
     */
    fun phishTankOnline(): List<String> {
        val request = HttpRequest.newBuilder(URI("https://data.phishtank.com/data/online-valid.csv.gz"))
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofMinutes(3))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) { "PhishTank download failed: HTTP ${response.statusCode()}" }
        val text = java.util.zip.GZIPInputStream(ByteArrayInputStream(response.body())).bufferedReader().readText()
        return parsePhishTankCsv(text)
    }

    /**
     * PhishTank CSV: phish_id,url,phish_detail_url,submission_time,verified,verification_time,online,target.
     * Only rows that are verified and online; the header and anything malformed are skipped.
     */
    fun parsePhishTankCsv(csv: String): List<String> = csv.lineSequence()
        .filter { it.isNotBlank() }
        .map { csvFields(it) }
        .filter { it.size >= 7 && it[0] != "phish_id" && it[4] == "yes" && it[6] == "yes" }
        .mapNotNull { it[1].trim().takeIf { url -> url.isNotEmpty() } }
        .toList()

    /** One domain per line; blank lines and `#` comments ignored. */

    fun parseDomainList(text: String): List<String> = text.lineSequence()
        .map { it.substringBefore('#').trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toList()

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
