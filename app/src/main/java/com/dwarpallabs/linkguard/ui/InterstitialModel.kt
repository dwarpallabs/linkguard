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
        data class BrandLookalike(val brand: String) : Reason
        data class Upi(val payeeName: String?, val payeeAddress: String?, val amount: String?) : Reason
        data class WeirdHost(val brand: String?) : Reason
        data object Reputation : Reason
        /** Reported as a scam by [count] distinct LinkGuard users this week. Always a soft warning. */
        data class Community(val count: Int) : Reason
        data class DomainAge(val days: Int) : Reason
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
                "warn_upi_intent" -> Reason.Upi(details["payee_name"], details["payee_address"], details["amount"])
                "warn_weird_host" -> Reason.WeirdHost(details["brand"])
                "warn_reputation" -> Reason.Reputation
                "warn_community" -> details["count"]?.toIntOrNull()?.let { Reason.Community(it) } ?: Reason.Generic
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
