package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import kotlin.system.exitProcess

/**
 * Daily feeds run.
 *
 *   ./gradlew :feeds:run                     dry run: fetch, filter, print counts; writes nothing
 *   ./gradlew :feeds:run --args="--apply"    also write to the server database
 *   ./gradlew :feeds:run --args="publish d"  build the public dataset into folder d (see [PublishMain])
 *   ./gradlew :feeds:run --args="weekly d"   write last week's public scam report into d (see [WeeklyMain])
 *
 * Environment: URLHAUS_AUTH_KEY (abuse.ch Auth-Key; without it URLhaus is skipped); for --apply
 * also CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID and LINKGUARD_D1_ID. FEEDS_MAX_NEW optionally
 * caps blocklist inserts per run.
 *
 * Feeds are independent: one that fails to download is skipped and its rows simply age out, while
 * the others are still applied. The top-sites list is required (it drives the allowlist and keeps
 * popular sites from ever being blocked), so without it nothing is written.
 *
 * Output is counts only. Never print list entries: CI logs of this public repo are public, and the
 * lists are kept private on purpose.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "publish") return PublishMain.run(args.drop(1))
    if (args.firstOrNull() == "weekly") return WeeklyMain.run(args.drop(1))
    val apply = "--apply" in args

    val env = System.getenv()

    val data = EngineData.loadBundled()
    val neverAllow = EngineData::class.java.classLoader!!.getResourceAsStream("linkguard/never_allow.txt")!!
        .bufferedReader().readLines()
        .map { it.substringBefore('#').trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    val feeds = linkedMapOf<String, List<String>>()
    fun fetch(name: String, download: () -> List<String>) {
        try {
            feeds[name] = download()
        } catch (e: Exception) {
            // The message names the feed and HTTP status only; never a list entry.
            println("$name: skipped (${e.javaClass.simpleName}: ${e.message})")
        }
    }
    val urlhausKey = env["URLHAUS_AUTH_KEY"].orEmpty()
    if (urlhausKey.isNotBlank()) fetch("urlhaus") { Sources.urlhausOnline(urlhausKey) } else println("urlhaus: skipped (URLHAUS_AUTH_KEY not set)")
    fetch("phishdestroy") { Sources.phishDestroyOnline() }
    fetch("phishtank") { Sources.phishTankOnline() }

    val ranked = Sources.trancoTop(IMMUNE_TOP_SITES)

    val writer = if (apply) {
        val missing = listOf("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID", "LINKGUARD_D1_ID").filter { env[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            System.err.println("--apply needs: ${missing.joinToString()}")
            exitProcess(2)
        }
        val maxNew = env["FEEDS_MAX_NEW"]?.toIntOrNull() ?: ListWriter.DEFAULT_MAX_NEW
        ListWriter(D1(env["CLOUDFLARE_ACCOUNT_ID"]!!, env["LINKGUARD_D1_ID"]!!, env["CLOUDFLARE_API_TOKEN"]!!), maxNew = maxNew)
    } else {
        null
    }
    val existingAllow = writer?.existingAllowlist().orEmpty()

    val result = FeedBuilder(data, neverAllow, ALLOW_TOP_SITES)
        .build(feeds, ranked, existingAllow.filterValues { it != "feed" }.keys)
    result.stats.forEach { (name, n) -> println("$name: $n") }

    if (writer == null) {
        println("dry run: nothing written (use --apply)")
        return
    }
    if (feeds.isEmpty()) {
        System.err.println("refusing to --apply with no feed downloaded: nothing would be refreshed")
        exitProcess(2)
    }
    val summary = writer.apply(result, existingAllow, writer.existingBlocklist())
    println("allowlist: +${summary.allowAdded} -${summary.allowRemoved}")
    println("blocklist: ${summary.blockInserted} new, ${summary.blockRefreshed} refreshed, ${summary.blockDeferred} deferred to the next run, ${summary.blockExpired} expired removed")
    println("blocklist: commercial flag changed on ${summary.commercialChanged} rows")
    summary.feedsSkipped.forEach { println("$it: not applied today (size changed implausibly)") }
}

/** Top sites that go on the allowlist. */
private const val ALLOW_TOP_SITES = 10_000

/** Top sites that can never be blocked by a feed. */
private const val IMMUNE_TOP_SITES = 100_000
