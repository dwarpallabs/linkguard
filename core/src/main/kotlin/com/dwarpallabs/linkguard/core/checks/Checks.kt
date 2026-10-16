package com.dwarpallabs.linkguard.core.checks

import com.dwarpallabs.linkguard.core.Check
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.NormalisedUrl
import com.dwarpallabs.linkguard.core.HttpReputationLookup
import com.dwarpallabs.linkguard.core.Opinion
import com.dwarpallabs.linkguard.core.ReputationLookup
import com.dwarpallabs.linkguard.core.Signal
import com.dwarpallabs.linkguard.core.sha256

/** Hard allowlist. A hit makes the engine stop and forward silently. Always scores 0. */
class AllowlistCheck(private val data: EngineData) : Check {

    fun isAllowed(url: NormalisedUrl): Boolean =
        url.isWeb && !url.isIpLiteral &&
            data.lists.opinion(url.host, url.registrableDomain) == Opinion.ALLOW

    override fun run(url: NormalisedUrl): Signal =
        if (isAllowed(url)) Signal(CheckId.ALLOWLIST, 0.0, "allowlist.hit", null) else Signal.none(CheckId.ALLOWLIST)
}

/** Links that download an Android app: the most common Indian scam payload ("install this KYC app"). */
class ApkDownloadCheck(private val data: EngineData) : Check {

    override fun run(url: NormalisedUrl): Signal {
        if (!url.isWeb) return Signal.none(CheckId.APK_DOWNLOAD)
        val path = url.path.lowercase()
        val pathAndQuery = url.decodedPathAndQuery
        val reason = when {
            path.endsWith(".apk") || pathAndQuery.substringBefore('?').endsWith(".apk") -> "path"
            url.query != null && Regex("""\.apk(\W|$)""").containsMatchIn(pathAndQuery.substringAfter('?')) -> "path"
            url.contentType?.lowercase()?.startsWith(APK_MIME) == true -> "content_type"
            isFileHost(url) && "apk" in pathAndQuery -> "file_host"
            else -> return Signal.none(CheckId.APK_DOWNLOAD)
        }
        return Signal(CheckId.APK_DOWNLOAD, SCORE, "apk_download.$reason", "warn_apk_download")
    }

    private fun isFileHost(url: NormalisedUrl) =
        url.host in data.fileHosts || url.registrableDomain in data.fileHosts

    companion object {
        const val SCORE = 0.9
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}

/** Host imitates a bank, wallet, courier or government brand. */
class BrandLookalikeCheck(private val matcher: BrandMatcher) : Check {

    override fun run(url: NormalisedUrl): Signal {
        if (!url.isWeb || url.isIpLiteral) return Signal.none(CheckId.BRAND_LOOKALIKE)
        val match = matcher.matchHost(url) ?: return Signal.none(CheckId.BRAND_LOOKALIKE)
        return Signal(
            CheckId.BRAND_LOOKALIKE, SCORE, "brand_lookalike.${match.kind.reason}", "warn_brand_lookalike",
            mapOf("brand" to match.brand.name),
        )
    }

    companion object {
        const val SCORE = 0.8
    }
}

/** A UPI payment or mandate link: money leaves the account if the user approves it. */
class UpiIntentCheck : Check {

    override fun run(url: NormalisedUrl): Signal {
        if (url.scheme != "upi") return Signal.none(CheckId.UPI_INTENT)
        val params = url.queryParams
        val details = buildMap {
            params["pa"]?.let { put("payee_address", it.take(MAX_DETAIL)) }
            params["pn"]?.let { put("payee_name", it.take(MAX_DETAIL)) }
            params["am"]?.let { put("amount", it.take(MAX_DETAIL)) }
            params["tn"]?.let { put("note", it.take(MAX_DETAIL)) }
        }
        return Signal(CheckId.UPI_INTENT, SCORE, "upi_intent.${url.host.ifEmpty { "pay" }}", "warn_upi_intent", details)
    }

    companion object {
        const val SCORE = 0.7
        private const val MAX_DETAIL = 64
    }
}

/**
 * Hosts real businesses rarely use: raw IPs, `user@host` tricks, very deep subdomains, throwaway TLDs.
 * Weak on its own (0.4); strong (0.8) when the URL also names a brand anywhere.
 */
class WeirdHostCheck(private val data: EngineData, private val matcher: BrandMatcher) : Check {

    override fun run(url: NormalisedUrl): Signal {
        if (!url.isWeb) return Signal.none(CheckId.WEIRD_HOST)
        val reason = when {
            url.userInfo != null -> "userinfo"
            url.isIpLiteral -> "ip"
            url.subdomainDepth >= DEEP_SUBDOMAINS -> "deep_subdomains"
            url.publicSuffix in data.freeTlds -> "free_tld"
            else -> return Signal.none(CheckId.WEIRD_HOST)
        }
        val text = listOfNotNull(url.userInfo, url.unicodeHost, url.decodedPathAndQuery).joinToString(" ")
        val brand = matcher.mentionedIn(text)
        return if (brand != null) {
            Signal(CheckId.WEIRD_HOST, WITH_BRAND, "weird_host.${reason}_with_brand", "warn_weird_host", mapOf("brand" to brand.name))
        } else {
            Signal(CheckId.WEIRD_HOST, ALONE, "weird_host.$reason", "warn_weird_host")
        }
    }

    companion object {
        const val ALONE = 0.4
        const val WITH_BRAND = 0.8
        private const val DEEP_SUBDOMAINS = 4
    }
}

/**
 * What the LinkGuard server knows about the domain, asked by 4-byte hash prefix so the server never
 * learns which domain was opened:
 *  - on the blocklist (feeds or confirmed) → strong warning;
 *  - otherwise reported by 3+ distinct LinkGuard users this week → a *soft* warning that says how many.
 *    Community reports alone can never produce a strong warning (capped at [COMMUNITY_MAX]).
 * Local list sources (tests, future offline bundle) are consulted first.
 */
class ReputationCheck(
    private val data: EngineData,
    private val remote: ReputationLookup? = null,
    private val timeoutMs: Int = 1_500,
) : Check {

    override fun run(url: NormalisedUrl): Signal {
        if (!url.isWeb) return Signal.none(CheckId.REPUTATION)
        if (data.lists.opinion(url.host, url.registrableDomain) == Opinion.BLOCK) return blocked()
        if (remote == null) return Signal.none(CheckId.REPUTATION)
        val hashes = listOfNotNull(url.host, url.registrableDomain).distinct().map(::sha256)
        val prefixes = hashes.map { it.take(HttpReputationLookup.PREFIX_LENGTH) }.distinct()
        val verdicts = remote.lookup(prefixes, timeoutMs) ?: return Signal.none(CheckId.REPUTATION)
        if (hashes.any { it in verdicts.blocked }) return blocked()
        val reporters = hashes.maxOfOrNull { verdicts.reported[it] ?: 0 } ?: 0
        val score = communityScore(reporters)
        if (score == 0.0) return Signal.none(CheckId.REPUTATION)
        return Signal(
            CheckId.REPUTATION, score, "reputation.community", "warn_community",
            mapOf("count" to reporters.toString()),
        )
    }

    private fun blocked() = Signal(CheckId.REPUTATION, SCORE, "reputation.blocklist", "warn_reputation")

    companion object {
        const val SCORE = 0.9
        const val COMMUNITY_MIN_REPORTERS = 3
        const val COMMUNITY_MAX = 0.6

        /** 3–9 reporters → 0.4, 10–24 → 0.5, 25+ → 0.6. Always below the strong threshold. */
        fun communityScore(reporters: Int): Double = when {
            reporters < COMMUNITY_MIN_REPORTERS -> 0.0
            reporters < 10 -> 0.4
            reporters < 25 -> 0.5
            else -> COMMUNITY_MAX
        }
    }
}
