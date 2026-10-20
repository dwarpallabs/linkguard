package com.dwarpallabs.linkguard.ui

import com.dwarpallabs.linkguard.core.Verdict

/** What the interstitial shows, decoupled from `core` types so the screen is easy to test. */
data class InterstitialModel(
    val strong: Boolean,
    /** ASCII host (punycode for homographs, so the trick is visible), or null for UPI. */
    val host: String?,
    /** The registrable domain inside [host], shown in bold. */
    val domain: String?,
    val reason: Reason,
) {
    sealed interface Reason {
        data object ApkDownload : Reason
        /** [mine]: one of the brands the user opens often, so the sentence says so. */
        data class BrandLookalike(val brand: String, val mine: Boolean = false) : Reason
        /**
         * A UPI request. [amount] is already grouped the Indian way (₹1,23,456). [mandate]: automatic,
         * repeated payments. [brand]: the payee's name uses this brand but the UPI ID isn't the brand's.
         */
        data class Upi(
            val payeeName: String?,
            val payeeAddress: String?,
            val amount: String?,
            val mandate: Boolean = false,
            val brand: String? = null,
        ) : Reason
        data class WeirdHost(val brand: String?) : Reason
        /** A known scam website. [source]: the partner that confirmed it, if one did. */
        data class Reputation(val source: String? = null) : Reason
        /** Reported as a scam by [count] distinct LinkGuard users this week. Always a soft warning. */
        data class Community(val count: Int) : Reason
        /** Part of a confirmed group of scam websites that [count] people reported this week. Strong. */
        data class Campaign(val count: Int) : Reason
        data class DomainAge(val days: Int) : Reason
        /** The user's organisation marked the website as unsafe (managed configuration). Strong. */
        data object Managed : Reason
        data object Generic : Reason
    }

    companion object {
        /** Builds the model from a non-silent [verdict]. The top signal supplies the one reason shown. */
        fun from(verdict: Verdict): InterstitialModel {
            val top = verdict.top
            val details = top?.details.orEmpty()
            val reason = when (top?.messageKey) {
                "warn_apk_download" -> Reason.ApkDownload
                "warn_brand_lookalike" -> details["brand"]?.let { Reason.BrandLookalike(it) } ?: Reason.Generic
                "warn_brand_lookalike_mine" -> details["brand"]?.let { Reason.BrandLookalike(it, mine = true) } ?: Reason.Generic

                "warn_upi_intent", "warn_upi_mandate", "warn_upi_brand" -> Reason.Upi(
                    payeeName = details["payee_name"],
                    payeeAddress = details["payee_address"],
                    amount = details["amount"]?.let(::indianAmount),
                    mandate = details["kind"] == "mandate",
                    brand = details["brand"],
                )

                "warn_weird_host" -> Reason.WeirdHost(details["brand"])
                "warn_reputation" -> Reason.Reputation(details["source"])
                "warn_community" -> details["count"]?.toIntOrNull()?.let { Reason.Community(it) } ?: Reason.Generic
                "warn_campaign" -> details["count"]?.toIntOrNull()?.let { Reason.Campaign(it) } ?: Reason.Reputation()


                "warn_managed_block" -> Reason.Managed
                "warn_domain_age" -> details["age_days"]?.toIntOrNull()?.let { Reason.DomainAge(it) } ?: Reason.Generic
                else -> Reason.Generic
            }
            val url = verdict.finalUrl
            val isWeb = url != null && url.isWeb
            return InterstitialModel(
                strong = verdict.level == Verdict.Level.STRONG,
                host = if (isWeb) url?.host else null,
                domain = if (isWeb) url?.registrableDomain else null,
                reason = reason,
            )
        }
    }
}

/**
 * Groups a rupee amount the Indian way with Western digits: "123456.5" → "1,23,456.5". Anything that
 * isn't a plain number is returned unchanged, so a strange amount is still shown as it was sent.
 */
fun indianAmount(raw: String): String {
    val m = Regex("""^(\d+)(\.\d{1,2})?$""").matchEntire(raw.trim()) ?: return raw
    val whole = m.groupValues[1].trimStart('0').ifEmpty { "0" }
    val grouped = if (whole.length <= 3) {
        whole
    } else {
        val last3 = whole.takeLast(3)
        val rest = whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$rest,$last3"
    }
    return grouped + m.groupValues[2]
}
