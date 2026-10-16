package com.dwarpallabs.linkguard.core

import java.security.MessageDigest

enum class Opinion { ALLOW, BLOCK }

/** One source of allow/block decisions about domains. */
interface ListSource {
    /** Opinion on [host] (ASCII) and its [registrableDomain], or null if this source has none. */
    fun opinion(host: String, registrableDomain: String?): Opinion?
}

/**
 * Ordered list sources: the first source with an opinion wins, so a higher-priority source can
 * override a lower one in either direction.
 */
class LayeredLists(private val sources: List<ListSource>) {
    fun opinion(host: String, registrableDomain: String?): Opinion? =
        sources.firstNotNullOfOrNull { it.opinion(host, registrableDomain) }
}

/**
 * Plain allowlist plus a hashed blocklist. Within one source, ALLOW wins over BLOCK, so community
 * or feed entries can never flip an allowlisted domain.
 *
 * Allow entries are registrable domains or hosts (`irctc.co.in`), or suffixes starting with a dot
 * (`.gov.in`) that match every host under them. Block entries are lowercase hex SHA-256 of a host
 * or registrable domain, so the bundle does not ship a readable list of scam domains.
 */
class DomainListSource(allow: Collection<String>, blockSha256: Collection<String>) : ListSource {

    private val exact = allow.filterNot { it.startsWith(".") }.map { it.lowercase() }.toHashSet()
    private val suffixes = allow.filter { it.startsWith(".") }.map { it.lowercase() }
    private val blocked = blockSha256.map { it.lowercase() }.toHashSet()

    override fun opinion(host: String, registrableDomain: String?): Opinion? {
        if (host in exact || registrableDomain in exact) return Opinion.ALLOW
        if (suffixes.any { host.endsWith(it) }) return Opinion.ALLOW
        if (blocked.isEmpty()) return null
        if (sha256(host) in blocked || (registrableDomain != null && sha256(registrableDomain) in blocked)) {
            return Opinion.BLOCK
        }
        return null
    }
}

/** A brand that scammers imitate, with the keywords that identify it and the domains it really uses. */
data class Brand(val name: String, val keywords: List<String>, val officialDomains: List<String>) {

    /** True if [host] / [registrableDomain] belongs to this brand. Entries starting with `.` match as suffixes. */
    fun owns(host: String, registrableDomain: String?): Boolean = officialDomains.any { domain ->
        if (domain.startsWith(".")) host.endsWith(domain) else domain == registrableDomain || domain == host
    }

    companion object {
        /** Parses `name<TAB>kw1,kw2<TAB>domain1,domain2` lines. */
        fun parseTsv(lines: Sequence<String>): List<Brand> = lines
            .map { it.trimEnd('\r', '\n', ' ') }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                // The official-domains field may be empty (e.g. government brands covered by .gov.in).
                val fields = line.split('\t')
                require(fields.size in 2..3) { "brands.tsv: expected 3 tab-separated fields" }
                Brand(fields[0].trim(), splitList(fields[1]), splitList(fields.getOrElse(2) { "" }))
            }
            .toList()

        private fun splitList(field: String) =
            field.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    }
}

/** Lowercase hex SHA-256 of [text]'s UTF-8 bytes; the hash every list and lookup uses. */
fun sha256Hex(text: String): String = sha256(text)

internal fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
