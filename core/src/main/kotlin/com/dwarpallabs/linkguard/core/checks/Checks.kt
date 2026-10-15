package com.dwarpallabs.linkguard.core.checks

import com.dwarpallabs.linkguard.core.Check
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.NormalisedUrl
import com.dwarpallabs.linkguard.core.Opinion
import com.dwarpallabs.linkguard.core.Signal

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

/** Domain is on a blocklist (bundled feeds today; Safe Browsing in the play flavour later). */
class ReputationCheck(private val data: EngineData) : Check {

    override fun run(url: NormalisedUrl): Signal {
        if (!url.isWeb) return Signal.none(CheckId.REPUTATION)
        return if (data.lists.opinion(url.host, url.registrableDomain) == Opinion.BLOCK) {
            Signal(CheckId.REPUTATION, SCORE, "reputation.blocklist", "warn_reputation")
        } else {
            Signal.none(CheckId.REPUTATION)
        }
    }

    companion object {
        const val SCORE = 0.9
    }
}
