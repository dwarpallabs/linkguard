package com.dwarpallabs.linkguard.core

/**
 * Everything the checks consult that is not code: suffix list, allow/block lists, brands and
 * host lists. Load once per process; it is immutable and thread-safe.
 */
class EngineData(
    val psl: PublicSuffixList,
    val lists: LayeredLists,
    val brands: List<Brand>,
    val shorteners: Set<String>,
    val fileHosts: Set<String>,
    val freeTlds: Set<String>,
    rdapServers: () -> Map<String, String>,
) {
    /** TLD → RDAP base URL (https only, ending in `/`). Loaded on first use: only DomainAge needs it. */
    val rdapServers: Map<String, String> by lazy(rdapServers)

    companion object {
        private const val PREFIX = "linkguard/"

        /**
         * Registrations under these suffixes are restricted to Indian government bodies and
         * RBI-regulated banks, so every host under them is allowed.
         */
        val RESTRICTED_SUFFIXES = listOf(".gov.in", ".nic.in", ".bank.in", ".mil.in")

        /**
         * Loads the lists bundled by the `data` module from the classpath.
         *
         * The allowlist and blocklist are not bundled: the allowlist arrives as a signed, hashed
         * download ([downloadedAllowlist]) and the blocklist stays on the server (see
         * [ReputationLookup]). Bundled are only public facts: brands' official domains and the
         * restricted suffixes above. [extraSources] are consulted first, in order.
         */
        fun loadBundled(
            extraSources: List<ListSource> = emptyList(),
            downloadedAllowlist: HashedAllowlist? = null,
        ): EngineData {
            val brands = Brand.parseTsv(lines("brands.tsv"))
            val builtIn = DomainListSource(RESTRICTED_SUFFIXES + brands.flatMap { it.officialDomains }, emptyList())
            return EngineData(
                psl = PublicSuffixList.fromCompact(text("public_suffixes.txt")),
                lists = LayeredLists(extraSources + listOfNotNull(downloadedAllowlist) + builtIn),
                brands = brands,
                shorteners = lines("shorteners.txt").cleaned().toHashSet(),
                fileHosts = lines("file_hosts.txt").cleaned().toHashSet(),
                freeTlds = lines("free_tlds.txt").cleaned().toHashSet(),
                rdapServers = { RdapBootstrap.parseTsv(lines("rdap_servers.tsv")) },
            )
        }

        private fun stream(name: String) =
            EngineData::class.java.classLoader?.getResourceAsStream(PREFIX + name)
                ?: error("missing bundled resource $PREFIX$name; is the data module on the classpath?")

        private fun text(name: String): String = stream(name).bufferedReader().use { it.readText() }

        private fun lines(name: String): Sequence<String> = text(name).lineSequence()

        private fun Sequence<String>.cleaned() =
            map { it.substringBefore('#').trim().lowercase() }.filter { it.isNotEmpty() }
    }
}
