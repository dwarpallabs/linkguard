package com.dwarpallabs.linkguard.core

import java.net.IDN

/**
 * Finds the links in a shared message, on the phone, for "Check with LinkGuard".
 *
 * Finds `http://`, `https://` and `upi://` links anywhere in the text, plus bare website names
 * such as `sbi-kyc.top/update`, `wa.me/919876543210` or `t.me/joinchat/x`, which chat apps turn into
 * links. A bare name counts only when it ends in a real public suffix, so `Rs.500`, `e.g.` or
 * `photo.jpg` are not links. Zero-width characters that scammers put inside links to hide them from
 * filters are removed. Links are returned in order, without duplicates, at most [max].
 *
 * The text itself is never kept, logged or sent anywhere.
 */
object LinkExtractor {

    const val MAX_LINKS = 10

    /** The links found, ready for [LinkEngine.evaluate]; [more] when the text had more than were kept. */
    data class Result(val links: List<String>, val more: Boolean)

    fun extract(text: String, psl: PublicSuffixList, max: Int = MAX_LINKS): Result {
        val found = LinkedHashMap<String, String>()
        var more = false
        for (token in clean(text.take(MAX_TEXT)).split(SEPARATOR)) {
            if (token.isEmpty()) continue
            val link = schemeLink(token, psl) ?: bareLink(token, psl) ?: continue
            val key = NormalisedUrl.parse(link, psl)?.toUrlString() ?: link
            if (key in found) continue
            if (found.size == max) {
                more = true
                break
            }
            found[key] = link
        }
        return Result(found.values.toList(), more)
    }

    /** `…https://x…` or `…upi://pay?…` anywhere in a token, e.g. after "Link:" or inside "(…)". */
    private fun schemeLink(token: String, psl: PublicSuffixList): String? {
        val start = SCHEME.find(token) ?: return null
        val link = trimEnd(cut(token.substring(start.range.first)))
        val parsed = NormalisedUrl.parse(link, psl) ?: return null
        if (parsed.isWeb && parsed.registrableDomain == null && !parsed.isIpLiteral) return null
        return link
    }

    /** `www.x.com`, `x.co.in/path`, `wa.me/91…`, or an IP address with a path; opened as `http://`. */
    private fun bareLink(token: String, psl: PublicSuffixList): String? {
        val candidate = trimEnd(cut(token.trimStart { it in LEADING })).takeIf { it.isNotEmpty() } ?: return null
        // A bare IP address is a link only with a path ("103.21.4.5/kyc"); alone it may be a version or a price.
        if (IP_WITH_PATH.matches(candidate)) return "http://$candidate"
        val match = BARE.matchEntire(candidate) ?: return null
        val host = match.groupValues[1]
        val ascii = try {
            IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (_: IllegalArgumentException) {
            return null
        }
        val suffix = psl.listedSuffix(ascii) ?: return null
        if (ascii == suffix) return null
        val name = ascii.removeSuffix(".$suffix").substringAfterLast('.')
        if (name.none { it.isLetter() }) return null
        return "http://$candidate"
    }

    /** Removes the invisible characters scammers insert to break up a link; keeps text otherwise. */
    private fun clean(text: String): String = buildString(text.length) {
        for (i in text.indices) {
            val c = text[i]
            when {
                c in ALWAYS_INVISIBLE -> Unit
                // Joiners shape Indic words, so they are only dropped when they touch ASCII (a link).
                c == ZWNJ || c == ZWJ -> {
                    val before = text.getOrNull(i - 1)
                    val after = text.getOrNull(i + 1)
                    if (!(before?.isAsciiLinkChar() == true || after?.isAsciiLinkChar() == true)) append(c)
                }
                c in FULL_STOPS -> append('.')
                else -> append(c)
            }
        }
    }

    /**
     * Ends a link where the surrounding sentence carries on without a space, e.g. "sbi.co/kycपर".
     * A website name may itself be written in an Indian script ("स्टेटबैंक.भारत"), so inside the
     * name a script change after ".", ":" or "/" is allowed; after the name, any change ends the link.
     */
    private fun cut(s: String): String {
        val nameStart = s.indexOf("://").let { if (it < 0) 0 else it + 3 }
        val nameEnd = s.indexOfAny(charArrayOf('/', '?', '#'), nameStart).let { if (it < 0) s.length else it }
        for (i in s.indices) {
            val c = s[i]
            if (c in STOP) return s.substring(0, i)
            if (i > 0 && c.isMessageScript()) {
                val prev = s[i - 1]
                val inName = i in nameStart until nameEnd
                if (prev.code < 128 && !(inName && prev in ".:/")) return s.substring(0, i)
            }
        }
        return s
    }

    /** Drops sentence punctuation after a link; keeps a ")" that closes a "(" inside the link. */
    private fun trimEnd(s: String): String {
        var end = s.length
        while (end > 0) {
            val c = s[end - 1]
            val unbalanced = c == ')' && s.substring(0, end).count { it == '(' } < s.substring(0, end).count { it == ')' }
            if (c in TRAILING || unbalanced) end-- else break
        }
        return s.substring(0, end)
    }

    private fun Char.isAsciiLinkChar() = code < 128 && (isLetterOrDigit() || this in "./:-_?=&#%@")

    /** Letters of the scripts LinkGuard's messages are written in, which never belong in an ASCII link. */
    private fun Char.isMessageScript(): Boolean {
        val c = code
        return c in 0x0900..0x0DFF || c in 0x0600..0x06FF || c in 0x1C50..0x1C7F || c in 0xABC0..0xABFF
    }

    private const val MAX_TEXT = 20_000
    private const val ZWNJ = '‌'
    private const val ZWJ = '‍'
    private val ALWAYS_INVISIBLE = setOf('​', '⁠', '﻿', '­', '᠎')
    private val FULL_STOPS = setOf('。', '．', '｡')
    private val SEPARATOR = Regex("""[\s  -   　]+""")
    private val SCHEME = Regex("""(?i)(https?|upi|tez|phonepe|paytmmp)://""")
    private val STOP = setOf('।', '॥', '"', '\'', '<', '>', '‘', '’', '“', '”', '|', '`', '、', '،')
    private val TRAILING = ".,;:!?*…]}".toSet() + setOf('۔', '؟')

    private val LEADING = "([{<*_\"'‘“¿¡".toSet()
    private val BARE = Regex("""^((?:[\p{L}\p{M}\p{N}](?:[\p{L}\p{M}\p{N}-]*[\p{L}\p{M}\p{N}])?\.)+[\p{L}\p{M}]{2,63})(:\d{1,5})?([/?#]\S*)?$""")
    private val IP_WITH_PATH = Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?[/?#]\S*$""")

}
