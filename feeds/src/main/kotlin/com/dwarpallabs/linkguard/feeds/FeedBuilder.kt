package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.NormalisedUrl

/** What one pipeline run wants the server lists to contain. */
data class FeedResult(
    /** Hosts (or IPs) to block, from the scam/malware feeds. */
    val blockHosts: Set<String>,
    /** Registrable domains to allow, from the top-sites list. */
    val allowDomains: Set<String>,
    val stats: Map<String, Int>,
)

/**
 * Turns raw feed data into list entries, using `core`'s normalisation so the server lists follow
 * exactly the same domain rules as the phone. Pure: no network, no database.
 */
class FeedBuilder(
    private val data: EngineData,
    private val neverAllow: Set<String>,
    private val topSites: Int = 10_000,
) {

    fun build(malwareUrls: List<String>, rankedDomains: List<String>, existingAllow: Set<String> = emptySet()): FeedResult {
        val stats = linkedMapOf<String, Int>()

        // Safe list: the top sites, minus anything where strangers can publish content.
        val ranked = rankedDomains.asSequence().map { it.trim().lowercase().trimEnd('.') }.filter { it.isNotEmpty() }.take(topSites).toList()
        val allow = ranked.filter { domain ->
            DOMAIN.matches(domain) &&
                data.psl.registrableDomain(domain) == domain &&
                domain !in data.shorteners && domain !in data.fileHosts && domain !in neverAllow
        }.toSet()
        stats["tranco_ranked"] = ranked.size
        stats["allow_after_filters"] = allow.size

        // Blocklist: hosts from malware/scam URLs, except where the host belongs to a site that is
        // allowed or shared by many users (blocking it would warn on everyone's links there).
        val immune = allow + existingAllow + data.brands.flatMap { it.officialDomains }.filterNot { it.startsWith(".") }
        val restrictedSuffixes = EngineData.RESTRICTED_SUFFIXES + data.brands.flatMap { it.officialDomains }.filter { it.startsWith(".") }
        var unparseable = 0
        var skippedShared = 0
        val hosts = HashSet<String>()
        for (raw in malwareUrls) {
            val url = NormalisedUrl.parse(raw, data.psl)
            if (url == null || !url.isWeb || ':' in url.host) { unparseable++; continue }
            val host = url.host
            val registrable = url.registrableDomain
            val shared = registrable in immune || host in immune ||
                restrictedSuffixes.any { host.endsWith(it) } ||
                host in data.shorteners || registrable in data.shorteners ||
                host in data.fileHosts || registrable in data.fileHosts ||
                host in neverAllow || registrable in neverAllow
            if (shared) { skippedShared++; continue }
            if (HOST.matches(host)) hosts += host else unparseable++
        }
        stats["malware_urls"] = malwareUrls.size
        stats["block_hosts"] = hosts.size
        stats["skipped_shared_or_allowed"] = skippedShared
        stats["skipped_unparseable"] = unparseable
        return FeedResult(hosts, allow, stats)
    }

    companion object {
        /** Lowercase ASCII hostnames (punycode) and IPv4 literals only: safe to embed in SQL. */
        val HOST = Regex("""^(?=.{1,253}$)[a-z0-9-]+(\.[a-z0-9-]+)*$""")
        val DOMAIN = Regex("""^(?=.{1,253}$)([a-z0-9-]{1,63}\.)+[a-z0-9-]{2,63}$""")
    }
}
