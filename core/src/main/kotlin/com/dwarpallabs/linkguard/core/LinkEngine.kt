package com.dwarpallabs.linkguard.core

import com.dwarpallabs.linkguard.core.checks.AllowlistCheck
import com.dwarpallabs.linkguard.core.checks.ApkDownloadCheck
import com.dwarpallabs.linkguard.core.checks.BrandLookalikeCheck
import com.dwarpallabs.linkguard.core.checks.BrandMatcher
import com.dwarpallabs.linkguard.core.checks.DomainAgeCheck
import com.dwarpallabs.linkguard.core.checks.RegistrationDates
import com.dwarpallabs.linkguard.core.checks.ReputationCheck
import com.dwarpallabs.linkguard.core.checks.UpiIntentCheck
import com.dwarpallabs.linkguard.core.checks.WeirdHostCheck

/**
 * Runs every check on a link and returns a [Verdict]. Blocking: call it off the main thread.
 *
 * Order: normalise → (upi: only UpiIntent) → unwrap → ApkDownload → Allowlist (stops on a hit)
 * → BrandLookalike, WeirdHost, Reputation, DomainAge. Verdict = max signal.
 *
 * ApkDownload runs before the allowlist on purpose: an APK on a trusted file host is still dangerous.
 *
 * Pass `http = null` / `registrationDates = null` to run offline (no unwrap, no domain age).
 */
class LinkEngine(
    private val data: EngineData,
    http: Http?,
    registrationDates: RegistrationDates?,
) {
    private val unwrapper = http?.let { Unwrapper(it, data) }
    private val matcher = BrandMatcher(data.brands)

    private val upi = UpiIntentCheck()
    private val apk = ApkDownloadCheck(data)
    private val allowlist = AllowlistCheck(data)
    private val checksAfterAllowlist: List<Check> = listOfNotNull(
        BrandLookalikeCheck(matcher),
        WeirdHostCheck(data, matcher),
        ReputationCheck(data),
        registrationDates?.let { DomainAgeCheck(it) },
    )

    fun evaluate(raw: String): Verdict {
        val parsed = NormalisedUrl.parse(raw, data.psl) ?: return Verdict.of(emptyList(), null)
        if (parsed.scheme == "upi") return Verdict.of(listOf(upi.run(parsed)), parsed)

        val unwrapped = unwrapper?.unwrap(parsed)
        val url = unwrapped?.url ?: parsed
        val unwrapSignal = Signal(CheckId.UNWRAP, 0.0, "unwrap.hops_${unwrapped?.hops ?: 0}", null)
        if (url.scheme == "upi") return Verdict.of(listOf(unwrapSignal, upi.run(url)), url)

        val signals = mutableListOf(unwrapSignal, apk.run(url))
        if (allowlist.isAllowed(url)) {
            signals += allowlist.run(url)
            return Verdict.of(signals, url)
        }
        checksAfterAllowlist.mapTo(signals) { it.run(url) }
        return Verdict.of(signals, url)
    }
}
