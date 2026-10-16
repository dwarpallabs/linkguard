package com.dwarpallabs.linkguard.core

import java.net.IDN

/**
 * Mozilla Public Suffix List (https://publicsuffix.org), used to find the registrable domain
 * ("eTLD+1") of a host: `icici.bank-secure-login.com` → `bank-secure-login.com`,
 * `onlinesbi.sbi` → `onlinesbi.sbi`, `www.irctc.co.in` → `irctc.co.in`.
 */
class PublicSuffixList(lines: Sequence<String>) {

    private val rules = HashSet<String>(16_384)
    private val wildcards = HashSet<String>(256)
    private val exceptions = HashSet<String>(64)

    init {
        for (raw in lines) {
            val line = raw.trim().substringBefore(' ')
            if (line.isEmpty() || line.startsWith("//")) continue
            when {
                line.startsWith("!") -> ascii(line.substring(1))?.let { exceptions += it }
                line.startsWith("*.") -> ascii(line.substring(2))?.let { wildcards += it }
                else -> ascii(line)?.let { rules += it }
            }
        }
    }

    companion object {
        /**
         * Fast path for the build-time compact file (one ASCII rule per line, no comments): a plain
         * index loop, because this runs on every cold start and is mostly interpreted on low-end phones.
         */
        fun fromCompact(text: String): PublicSuffixList {
            val list = PublicSuffixList(emptySequence())
            var start = 0
            while (start < text.length) {
                var end = text.indexOf('\n', start)
                if (end == -1) end = text.length
                if (end > start) {
                    when (text[start]) {
                        '!' -> list.exceptions.add(text.substring(start + 1, end))
                        '*' -> list.wildcards.add(text.substring(start + 2, end))
                        else -> list.rules.add(text.substring(start, end))
                    }
                }
                start = end + 1
            }
            return list
        }
    }

    /** The public suffix of an ASCII, lowercase [host], e.g. `co.in`. Unknown TLDs count as suffixes. */
    fun publicSuffix(host: String): String {
        val labels = host.split('.')
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in exceptions) return labels.subList(i + 1, labels.size).joinToString(".")
            val parent = labels.subList(i + 1, labels.size).joinToString(".")
            if (candidate in rules || (i + 1 < labels.size && parent in wildcards)) return candidate
        }
        return labels.last()
    }

    /** The registrable domain of [host], or null if [host] is itself a public suffix. */
    fun registrableDomain(host: String): String? {
        val suffix = publicSuffix(host)
        if (host == suffix) return null
        val prefix = host.removeSuffix(".$suffix")
        return prefix.substringAfterLast('.') + "." + suffix
    }

    /** ASCII form of a rule; null for the few rules java.net.IDN (IDNA2003) cannot convert. */
    private fun ascii(rule: String): String? {
        if (rule.all { it.code < 128 }) return rule.lowercase()
        return try {
            IDN.toASCII(rule, IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
