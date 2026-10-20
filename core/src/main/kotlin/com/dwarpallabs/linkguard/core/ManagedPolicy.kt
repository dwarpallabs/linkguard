package com.dwarpallabs.linkguard.core

/**
 * What an organisation set for LinkGuard through managed configuration (an MDM's app config):
 * websites to warn about, websites to trust, and whether the checks must stay on.
 *
 * An entry matches its own host and every host under it (`example.com` also covers
 * `pay.example.com`). Blocking wins over trusting. A blocked website gets a strong warning that
 * still offers *Open anyway*; a trusted one is forwarded like an allowlisted one, so app
 * downloads are still checked. Lives in memory only, never on the server.
 */
class ManagedPolicy(
    blocked: Collection<String> = emptyList(),
    allowed: Collection<String> = emptyList(),
    /** True when people on the phone may not switch checks off. */
    val keepChecksOn: Boolean = false,
) {
    private val blocked = blocked.toHashSet()
    private val allowed = allowed.toHashSet()

    val blockedCount: Int get() = blocked.size
    val allowedCount: Int get() = allowed.size

    /** True when the organisation set anything at all, so Settings can say it is managed. */
    val isActive: Boolean get() = keepChecksOn || blocked.isNotEmpty() || allowed.isNotEmpty()

    fun blocks(url: NormalisedUrl): Boolean = url.isWeb && matches(blocked, url.host)

    fun allows(url: NormalisedUrl): Boolean = url.isWeb && !blocks(url) && matches(allowed, url.host)

    /** The checks to skip: the user's choice, unless the organisation keeps them on. */
    fun disabled(userDisabled: Set<CheckId>): Set<CheckId> = if (keepChecksOn) emptySet() else userDisabled

    private fun matches(entries: Set<String>, host: String): Boolean {
        if (entries.isEmpty()) return false
        var h = host
        while (true) {
            if (h in entries) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    companion object {
        val NONE = ManagedPolicy()

        /** At most this many entries per list are read, so a huge config cannot exhaust memory. */
        const val MAX_ENTRIES = 10_000

        /**
         * Parses an admin-typed list: websites separated by new lines, commas, semicolons or spaces.
         * Accepts `example.com`, `.example.com`, `*.example.com` and pasted links
         * (`https://example.com/login`); `#` starts a comment. Entries that are not a website
         * (a bare word, a public suffix such as `co.in`, an IP address) are dropped.
         */
        fun parseList(text: String?, psl: PublicSuffixList): List<String> {
            if (text.isNullOrBlank()) return emptyList()
            return text.lineSequence()
                .map { it.substringBefore('#') }
                .flatMap { it.split(',', ';', ' ', '\t').asSequence() }
                .map { it.trim().removePrefix("*").removePrefix(".") }
                .filter { it.isNotEmpty() }
                .mapNotNull { entry ->
                    val raw = if (entry.contains("://")) entry else "https://$entry"
                    NormalisedUrl.parse(raw, psl)?.takeIf { it.isWeb && !it.isIpLiteral && it.registrableDomain != null }?.host
                }
                .distinct()
                .take(MAX_ENTRIES)
                .toList()
        }
    }
}
