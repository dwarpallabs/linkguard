package com.dwarpallabs.linkguard.core.checks

import com.dwarpallabs.linkguard.core.Brand
import com.dwarpallabs.linkguard.core.NormalisedUrl
import java.text.Normalizer

/**
 * Finds brand keywords in hosts and free text.
 *
 * Keywords of 4 characters or fewer (`sbi`, `jio`) must be a whole token, so `lesbian` or `wsbi`
 * never match. Longer keywords also match inside a hyphen-less label (`income-tax-refund` →
 * `incometax`) or one edit away from a token (`phonpe` → `phonepe`).
 */
class BrandMatcher(private val brands: List<Brand>) {

    enum class Kind(val reason: String) {
        EXACT("keyword"), TYPO("typo"), HOMOGRAPH("homograph"), DIGIT_SWAP("digit_swap")
    }

    data class Match(val brand: Brand, val keyword: String, val kind: Kind)

    /** A brand imitated by [url]'s host, ignoring the brand's own domains. */
    fun matchHost(url: NormalisedUrl): Match? {
        val owned = brands.filter { it.owns(url.host, url.registrableDomain) }
        val candidates = brands - owned.toSet()
        if (candidates.isEmpty()) return null

        val labels = hostLabels(url)
        val isPunycode = url.host.split('.').any { it.startsWith("xn--") }
        val variants = variants(labels, isPunycode)
        // Exact hits in any variant first, so "hdfcb4nk" reads as a digit swap rather than a typo.
        for (wanted in listOf(Hit.EXACT, Hit.TYPO)) {
            for ((variant, kind) in variants) {
                for (brand in candidates) {
                    for (keyword in brand.keywords) {
                        if (matchLabels(variant, keyword) != wanted) continue
                        val reported = if (wanted == Hit.TYPO && kind == Kind.EXACT) Kind.TYPO else kind
                        return Match(brand, keyword, reported)
                    }
                }
            }
        }
        return null
    }

    /** First brand whose keyword appears as a token (or, for long keywords, a substring) of [text]. */
    fun mentionedIn(text: String): Brand? {
        val lower = text.lowercase()
        val tokens = lower.split(NON_ALNUM).filter { it.isNotEmpty() }.toSet()
        val squashed = lower.replace(NON_ALNUM, "")
        return brands.firstOrNull { brand ->
            brand.keywords.any { kw -> if (kw.length <= SHORT) kw in tokens else kw in squashed }
        }
    }

    private enum class Hit { EXACT, TYPO }

    private fun matchLabels(labels: List<String>, keyword: String): Hit? {
        val tokens = labels.flatMap { it.split('-') }.filter { it.isNotEmpty() }
        if (keyword.length <= SHORT) return if (keyword in tokens) Hit.EXACT else null
        if (labels.any { keyword in it.replace("-", "") }) return Hit.EXACT
        if (tokens.any { it.length >= SHORT + 1 && editDistanceIsOne(it, keyword) }) return Hit.TYPO
        return null
    }

    /** Host labels without the public suffix, in Unicode, lowercase. */
    private fun hostLabels(url: NormalisedUrl): List<String> {
        val suffixLabels = url.publicSuffix?.count { it == '.' }?.plus(1) ?: 0
        val labels = url.unicodeHost.lowercase().split('.')
        return labels.dropLast(suffixLabels.coerceAtMost(labels.size - 1))
    }

    private fun variants(labels: List<String>, isPunycode: Boolean): List<Pair<List<String>, Kind>> {
        val base = if (isPunycode) Kind.HOMOGRAPH else Kind.EXACT
        val folded = labels.map(::foldConfusables)
        val result = mutableListOf(labels to base)
        if (folded != labels) result += folded to Kind.HOMOGRAPH
        for (digitMap in DIGIT_MAPS) {
            val swapped = folded.map { label -> label.map { digitMap[it] ?: it }.joinToString("") }
            if (swapped != folded) result += swapped to Kind.DIGIT_SWAP
        }
        return result
    }

    private fun foldConfusables(label: String): String {
        val stripped = Normalizer.normalize(label, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        return stripped.map { CONFUSABLES[it] ?: it }.joinToString("")
            .replace("rn", "m")
            .replace("vv", "w")
    }

    companion object {
        private const val SHORT = 4
        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        /** Cyrillic and Greek letters that render like Latin ones. */
        private val CONFUSABLES = mapOf(
            'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x',
            'і' to 'i', 'ј' to 'j', 'ԁ' to 'd', 'ѕ' to 's', 'һ' to 'h', 'ӏ' to 'l', 'ԛ' to 'q', 'ԝ' to 'w',
            'α' to 'a', 'ο' to 'o', 'ρ' to 'p', 'ι' to 'i', 'κ' to 'k', 'ν' to 'v', 'τ' to 't', 'υ' to 'u',
            'ı' to 'i',
        )

        /** Digit swaps scammers use. `1` reads as both `l` and `i`, so both are tried. */
        private val DIGIT_MAPS = listOf(
            mapOf('0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '8' to 'b'),
            mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '8' to 'b'),
        )

        internal fun editDistanceIsOne(a: String, b: String): Boolean {
            if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
            var i = 0
            var j = 0
            var edits = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) { i++; j++; continue }
                if (++edits > 1) return false
                when {
                    a.length > b.length -> i++
                    a.length < b.length -> j++
                    else -> { i++; j++ }
                }
            }
            return edits + (a.length - i) + (b.length - j) == 1
        }
    }
}
