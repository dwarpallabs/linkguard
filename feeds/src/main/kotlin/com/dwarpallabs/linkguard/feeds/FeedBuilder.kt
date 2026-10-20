package com.dwarpallabs.linkguard.feeds

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.NormalisedUrl

/** What one pipeline run wants the server lists to contain. */
data class FeedResult(
    /** Hosts (or IPs) to block, each with the feed that listed it first. */
    val blockHosts: Map<String, String>,
    /** Registrable domains to allow, from the top-sites list. */
    val allowDomains: Set<String>,
    val stats: Map<String, Int>,
    /**
     * Block hosts that at least one [FeedBuilder.COMMERCIAL_FEEDS] feed lists, whichever feed listed
     * them first. Only these may be served to paying customers of the keyed lookup tier.
     */
    val commercialHosts: Set<String> = emptySet(),
    /** A commercial-safe feed was not downloaded this run, so [commercialHosts] may be incomplete. */
    val commercialFeedsMissing: Boolean = false,
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

    /**
     * @param feeds feed name → its entries (full URLs or bare domains), in priority order.
     * @param rankedDomains the top-sites ranking. The first [topSites] become the allowlist; every
     *   ranked site, allowlisted or not, is immune from blocking, so a feed can never put a popular
     *   real website behind a warning.
     */
    fun build(feeds: Map<String, List<String>>, rankedDomains: List<String>, existingAllow: Set<String> = emptySet()): FeedResult {
        val stats = linkedMapOf<String, Int>()

        // Safe list: the top sites, minus anything where strangers can publish content.
        val ranked = rankedDomains.asSequence().map { it.trim().lowercase().trimEnd('.') }.filter { DOMAIN.matches(it) }.toList()
        val allow = ranked.take(topSites).filter { domain ->
            data.psl.registrableDomain(domain) == domain &&
                domain !in data.shorteners && domain !in data.fileHosts && domain !in neverAllow
        }.toSet()
        stats["tranco_ranked"] = ranked.size
        stats["allow_after_filters"] = allow.size

        // Blocklist: hosts from the feeds, except where the host belongs to a popular, allowed or
        // shared site (blocking it would warn on everyone's links there).
        val immune = ranked.toSet() + allow + existingAllow + data.brands.flatMap { it.officialDomains }.filterNot { it.startsWith(".") }
        val restrictedSuffixes = EngineData.RESTRICTED_SUFFIXES + data.brands.flatMap { it.officialDomains }.filter { it.startsWith(".") }
        val hosts = LinkedHashMap<String, String>()
        val commercial = HashSet<String>()
        for ((feed, entries) in feeds) {
            var unparseable = 0
            var skippedShared = 0
            var added = 0
            for (raw in entries) {
                val entry = raw.trim()
                val url = NormalisedUrl.parse(if ("://" in entry) entry else "http://$entry/", data.psl)
                if (url == null || !url.isWeb || ':' in url.host) { unparseable++; continue }
                val host = url.host
                val registrable = url.registrableDomain
                val shared = registrable in immune || host in immune ||
                    restrictedSuffixes.any { host.endsWith(it) } ||
                    host in data.shorteners || registrable in data.shorteners ||
                    host in data.fileHosts || registrable in data.fileHosts ||
                    host in neverAllow || registrable in neverAllow
                if (shared) { skippedShared++; continue }
                if (!HOST.matches(host)) { unparseable++; continue }
                if (hosts.putIfAbsent(host, feed) == null) added++
                if (feed in COMMERCIAL_FEEDS) commercial += host
            }
            stats["${feed}_entries"] = entries.size
            stats["${feed}_block_hosts"] = added
            stats["${feed}_skipped_shared_or_allowed"] = skippedShared
            stats["${feed}_skipped_unparseable"] = unparseable
        }
        stats["block_hosts"] = hosts.size
        stats["block_hosts_commercial"] = commercial.size
        return FeedResult(hosts, allow, stats, commercial, commercialFeedsMissing = !feeds.keys.containsAll(COMMERCIAL_FEEDS))
    }

    companion object {
        /** Lowercase ASCII hostnames (punycode) and IPv4 literals only: safe to embed in SQL. */
        val HOST = Regex("""^(?=.{1,253}$)[a-z0-9-]+(\.[a-z0-9-]+)*$""")
        val DOMAIN = Regex("""^(?=.{1,253}$)([a-z0-9-]{1,63}\.)+[a-z0-9-]{2,63}$""")
        /** Feed names are stored in the database and used in log keys. */
        val FEED_NAME = Regex("""^[a-z][a-z0-9]{1,30}$""")

        /**
         * Feeds whose terms allow commercial use (feeds/SOURCES.md): PhishDestroy (MIT) and PhishTank
         * ("free to everyone"). URLhaus is free for not-for-profit use only, so a host only URLhaus
         * lists is never served to paying customers.
         */
        val COMMERCIAL_FEEDS = setOf("phishdestroy", "phishtank")
    }
}
