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
    /** TLD → RDAP base URL (https only, ending in `/`). */
    val rdapServers: Map<String, String>,
) {
    companion object {
        private const val PREFIX = "linkguard/"

        /**
         * Loads the lists bundled by the `data` module from the classpath. [extraSources] are
         * consulted before the bundled lists, in order.
         */
        fun loadBundled(extraSources: List<ListSource> = emptyList()): EngineData {
            val brands = Brand.parseTsv(lines("brands.tsv"))
            val allow = lines("allowlist.txt").cleaned().toList() +
                brands.flatMap { it.officialDomains }
            val bundled = DomainListSource(allow, lines("blocklist_sha256.txt").cleaned().toList())
            return EngineData(
                psl = PublicSuffixList(lines("public_suffix_list.dat")),
                lists = LayeredLists(extraSources + bundled),
                brands = brands,
                shorteners = lines("shorteners.txt").cleaned().toHashSet(),
                fileHosts = lines("file_hosts.txt").cleaned().toHashSet(),
                freeTlds = lines("free_tlds.txt").cleaned().toHashSet(),
                rdapServers = RdapBootstrap.parse(text("rdap_bootstrap.json")),
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
