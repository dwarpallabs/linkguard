package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import kotlin.system.exitProcess

/**
 * Daily feeds run.
 *
 *   ./gradlew :feeds:run                     dry run: fetch, filter, print counts; writes nothing
 *   ./gradlew :feeds:run --args="--apply"    also write to the server database
 *
 * Environment: URLHAUS_AUTH_KEY (abuse.ch Auth-Key); for --apply also CLOUDFLARE_API_TOKEN,
 * CLOUDFLARE_ACCOUNT_ID and LINKGUARD_D1_ID.
 *
 * Output is counts only. Never print list entries: CI logs of this public repo are public, and the
 * lists are kept private on purpose.
 */
fun main(args: Array<String>) {
    val apply = "--apply" in args
    val env = System.getenv()

    val data = EngineData.loadBundled()
    val neverAllow = EngineData::class.java.classLoader!!.getResourceAsStream("linkguard/never_allow.txt")!!
        .bufferedReader().readLines()
        .map { it.substringBefore('#').trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    val urlhausKey = env["URLHAUS_AUTH_KEY"].orEmpty()
    val malwareUrls = if (urlhausKey.isNotBlank()) {
        Sources.urlhausOnline(urlhausKey)
    } else {
        println("URLHAUS_AUTH_KEY not set: skipping URLhaus")
        emptyList()
    }
    val ranked = Sources.trancoTop(TOP_SITES)

    val writer = if (apply) {
        val missing = listOf("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID", "LINKGUARD_D1_ID").filter { env[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            System.err.println("--apply needs: ${missing.joinToString()}")
            exitProcess(2)
        }
        ListWriter(D1(env["CLOUDFLARE_ACCOUNT_ID"]!!, env["LINKGUARD_D1_ID"]!!, env["CLOUDFLARE_API_TOKEN"]!!))
    } else {
        null
    }
    val existing = writer?.existingAllowlist().orEmpty()

    val result = FeedBuilder(data, neverAllow, TOP_SITES)
        .build(malwareUrls, ranked, existing.filterValues { it != "feed" }.keys)
    result.stats.forEach { (name, n) -> println("$name: $n") }

    if (writer == null) {
        println("dry run: nothing written (use --apply)")
        return
    }
    if (urlhausKey.isBlank()) {
        System.err.println("refusing to --apply without URLhaus: the feed blocklist would expire unrefreshed")
        exitProcess(2)
    }
    val summary = writer.apply(result, existing)
    println("allowlist: +${summary.allowAdded} -${summary.allowRemoved}")
    println("blocklist: ${summary.blockUpserted} feed hosts refreshed, ${summary.blockExpired} expired removed")
}

private const val TOP_SITES = 10_000
