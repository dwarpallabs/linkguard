package com.dwarpallabs.linkguard.core

import java.net.URI
import java.net.URISyntaxException

/** Where a link really goes after following shortener redirects. */
internal data class UnwrapResult(val url: NormalisedUrl, val hops: Int)

/**
 * Follows redirects through known link shorteners (bit.ly, t.co, ...) so every check sees the real
 * destination.
 *
 * Privacy: only hosts on the shortener list are ever contacted, always over HTTPS, with HEAD
 * requests and no cookies or Referer. The final destination is never requested: LinkGuard must
 * not touch a possibly malicious server before the user decides to open it.
 */
internal class Unwrapper(
    private val http: Http,
    private val data: EngineData,
    private val maxHops: Int = 5,
    private val budgetMs: Long = 2_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Follows redirects for at most [budgetMs] (capped at the constructor's budget). */
    fun unwrap(start: NormalisedUrl, budgetMs: Long = this.budgetMs): UnwrapResult {
        val deadline = clock() + minOf(budgetMs, this.budgetMs)
        var current = start
        var hops = 0
        while (hops < maxHops && current.isWeb && isShortener(current)) {
            val remaining = deadline - clock()
            if (remaining <= 0) break
            val response = http.head(httpsUrl(current), remaining.toInt()) ?: break
            if (response.status in 300..399 && response.location != null) {
                val next = resolve(current, response.location) ?: break
                hops++
                current = next
            } else {
                if (response.status in 200..299) current = current.withContentType(response.contentType)
                break
            }
        }
        return UnwrapResult(current, hops)
    }

    private fun isShortener(url: NormalisedUrl) =
        url.host in data.shorteners || url.registrableDomain in data.shorteners

    private fun httpsUrl(url: NormalisedUrl): String =
        url.toUrlString().replaceFirst(Regex("^http:"), "https:")

    private fun resolve(base: NormalisedUrl, location: String): NormalisedUrl? {
        val absolute = try {
            URI(base.toUrlString()).resolve(location.trim()).toString()
        } catch (_: URISyntaxException) {
            location.trim()
        } catch (_: IllegalArgumentException) {
            location.trim()
        }
        return NormalisedUrl.parse(absolute, data.psl)
    }
}
