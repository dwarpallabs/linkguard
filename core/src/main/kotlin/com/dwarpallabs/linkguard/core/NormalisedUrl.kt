package com.dwarpallabs.linkguard.core

import java.net.IDN
import java.net.URLDecoder

/**
 * A parsed, canonical URL. Parsing is lenient (RFC 3986 appendix B) so that anything a browser
 * would open can be inspected.
 *
 * [toString] never includes the URL itself, so an accidental log line cannot leak it.
 */
class NormalisedUrl private constructor(
    /** Lowercase scheme: `http`, `https` or `upi`. */
    val scheme: String,
    /** Lowercase ASCII host (punycode for IDNs), no trailing dot. For `upi://pay` this is `pay`. */
    val host: String,
    /** Host decoded to Unicode, for homograph checks and display. */
    val unicodeHost: String,
    /** Explicit non-default port, or -1. */
    val port: Int,
    /** Raw `user:pass` part before `@`, if any. Browsers hide it, scammers use it to show a fake host. */
    val userInfo: String?,
    val path: String,
    val query: String?,
    val fragment: String?,
    val isIpLiteral: Boolean,
    /** eTLD+1, e.g. `irctc.co.in`; null for IP literals, `upi:` and bare public suffixes. */
    val registrableDomain: String?,
    /** Public suffix of the host, e.g. `co.in`; null for IP literals and `upi:`. */
    val publicSuffix: String?,
    /** `Content-Type` seen while unwrapping redirects, if a response revealed it. */
    val contentType: String? = null,
) {
    val isWeb: Boolean get() = scheme == "http" || scheme == "https"

    /** A UPI payment or mandate request: `upi://` or an app's own scheme for one (`tez://upi/pay`, …). */
    val isUpi: Boolean get() = scheme in UPI_SCHEMES

    /** Host labels in front of the registrable domain: `a.b.example.com` → 2. */
    val subdomainDepth: Int
        get() = registrableDomain?.let { host.removeSuffix(it).count { c -> c == '.' } } ?: 0

    /** Path and query, percent-decoded where possible, lowercase. Used for keyword and `.apk` checks. */
    val decodedPathAndQuery: String by lazy {
        val raw = path + (query?.let { "?$it" } ?: "")
        try {
            URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            raw
        }.lowercase()
    }

    /** Query parameters, percent-decoded. Later duplicates win. */
    val queryParams: Map<String, String> by lazy {
        query.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            val value = if ('=' in pair) pair.substringAfter('=') else ""
            decode(key).lowercase() to decode(value)
        }
    }

    /** Re-assembled URL string, with an ASCII host. */
    fun toUrlString(): String = buildString {
        append(scheme).append("://")
        userInfo?.let { append(it).append('@') }
        append(if (isIpLiteral && ':' in host) "[$host]" else host)
        if (port != -1) append(':').append(port)
        append(path)
        query?.let { append('?').append(it) }
        fragment?.let { append('#').append(it) }
    }

    fun withContentType(type: String?): NormalisedUrl = NormalisedUrl(
        scheme, host, unicodeHost, port, userInfo, path, query, fragment,
        isIpLiteral, registrableDomain, publicSuffix, type,
    )

    override fun toString(): String = "NormalisedUrl(scheme=$scheme, <redacted>)"

    override fun equals(other: Any?): Boolean =
        other is NormalisedUrl && other.toUrlString() == toUrlString() && other.contentType == contentType

    override fun hashCode(): Int = toUrlString().hashCode()

    companion object {
        private val URI_PATTERN = Regex("""^([a-zA-Z][a-zA-Z0-9+.\-]*):(?://([^/?#]*))?([^?#]*)(?:\?([^#]*))?(?:#(.*))?$""")
        private val DOTTED_NUMERIC = Regex("""^(0x[0-9a-f]+|[0-9]+)(\.(0x[0-9a-f]+|[0-9]+)){0,3}$""")
        /** UPI requests: the standard scheme plus Google Pay's, PhonePe's and Paytm's own. */
        val UPI_SCHEMES = setOf("upi", "tez", "phonepe", "paytmmp")
        private val SUPPORTED = setOf("http", "https") + UPI_SCHEMES

        /** Parses [raw], or returns null if it is not an http, https or upi URL with a usable host. */
        fun parse(raw: String, psl: PublicSuffixList): NormalisedUrl? {
            val match = URI_PATTERN.matchEntire(raw.trim()) ?: return null
            val scheme = match.groupValues[1].lowercase()
            if (scheme !in SUPPORTED) return null
            val authority = match.groups[2]?.value ?: return null

            val userInfo = authority.substringBeforeLast('@', "").ifEmpty { null }
            val hostPort = authority.substringAfterLast('@')
            val (rawHost, port) = splitHostPort(hostPort, scheme) ?: return null

            val path = match.groupValues[3]
            val query = match.groups[4]?.value
            val fragment = match.groups[5]?.value

            if (scheme in UPI_SCHEMES) {

                val host = rawHost.lowercase()
                return NormalisedUrl(scheme, host, host, -1, null, path, query, fragment, false, null, null)
            }

            val decodedHost = decode(rawHost).lowercase().trimEnd('.')
            if (decodedHost.isEmpty()) return null

            val ipv6 = decodedHost.startsWith("[") && decodedHost.endsWith("]")
            val isIp = ipv6 || DOTTED_NUMERIC.matches(decodedHost)
            val asciiHost = when {
                ipv6 -> decodedHost.substring(1, decodedHost.length - 1)
                isIp -> decodedHost
                else -> try {
                    IDN.toASCII(decodedHost, IDN.ALLOW_UNASSIGNED).lowercase()
                } catch (_: IllegalArgumentException) {
                    return null
                }
            }
            if (asciiHost.isEmpty()) return null
            val unicodeHost = if (isIp) asciiHost else IDN.toUnicode(asciiHost, IDN.ALLOW_UNASSIGNED)

            return NormalisedUrl(
                scheme = scheme,
                host = asciiHost,
                unicodeHost = unicodeHost,
                port = port,
                userInfo = userInfo,
                path = path,
                query = query,
                fragment = fragment,
                isIpLiteral = isIp,
                registrableDomain = if (isIp) null else psl.registrableDomain(asciiHost),
                publicSuffix = if (isIp) null else psl.publicSuffix(asciiHost),
            )
        }

        private fun splitHostPort(hostPort: String, scheme: String): Pair<String, Int>? {
            val portStart = if (hostPort.startsWith("[")) {
                hostPort.indexOf("]:").let { if (it == -1) -1 else it + 1 }
            } else {
                hostPort.lastIndexOf(':')
            }
            if (portStart == -1) return hostPort to -1
            val host = hostPort.substring(0, portStart)
            val portText = hostPort.substring(portStart + 1)
            if (portText.isEmpty()) return host to -1
            val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val default = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
            return host to if (default) -1 else port
        }

        private fun decode(text: String): String = try {
            URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            text
        }
    }
}
