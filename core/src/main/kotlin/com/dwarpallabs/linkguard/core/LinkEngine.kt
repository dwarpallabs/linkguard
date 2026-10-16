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
 * Time allowed for one evaluation, including network calls. Links on known shorteners get longer
 * because their real destination is only known after a round-trip.
 */
data class Budget(val normalMs: Long, val shortenerMs: Long) {
    companion object {
        /** Production budget: decide normal links within 300 ms, short links within 1.5 s. */
        val INTERACTIVE = Budget(normalMs = 300, shortenerMs = 1_500)

        /** No practical limit; for tests and tools. */
        val UNLIMITED = Budget(normalMs = 60_000, shortenerMs = 60_000)

        /** DomainAge is skipped when less than this is left. */
        const val MIN_LOOKUP_MS = 100L
    }
}

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
    private val registrationDates: RegistrationDates?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val unwrapper = http?.let { Unwrapper(it, data, clock = clock) }
    private val matcher = BrandMatcher(data.brands)

    private val upi = UpiIntentCheck()
    private val apk = ApkDownloadCheck(data)
    private val allowlist = AllowlistCheck(data)
    private val localChecks: List<Pair<CheckId, Check>> = listOf(
        CheckId.BRAND_LOOKALIKE to BrandLookalikeCheck(matcher),
        CheckId.WEIRD_HOST to WeirdHostCheck(data, matcher),
        CheckId.REPUTATION to ReputationCheck(data),
    )

    /** True if [raw] points at a known link shortener, so evaluation may take [Budget.shortenerMs]. */
    fun isShortener(raw: String): Boolean =
        NormalisedUrl.parse(raw, data.psl)?.let(::isListedShortener) ?: false

    fun evaluate(
        raw: String,
        budget: Budget = Budget.UNLIMITED,
        disabled: Set<CheckId> = emptySet(),
    ): Verdict {
        val start = clock()
        val parsed = NormalisedUrl.parse(raw, data.psl) ?: return Verdict.of(emptyList(), null)
        if (parsed.scheme == "upi") return Verdict.of(listOfNotNull(upiSignal(parsed, disabled)), parsed)

        val deadline = start + if (isListedShortener(parsed)) budget.shortenerMs else budget.normalMs
        val unwrapped = if (CheckId.UNWRAP in disabled) null else unwrapper?.unwrap(parsed, deadline - clock())
        val url = unwrapped?.url ?: parsed
        val unwrapSignal = Signal(CheckId.UNWRAP, 0.0, "unwrap.hops_${unwrapped?.hops ?: 0}", null)
        if (url.scheme == "upi") return Verdict.of(listOfNotNull(unwrapSignal, upiSignal(url, disabled)), url)

        val signals = mutableListOf(unwrapSignal)
        if (CheckId.APK_DOWNLOAD !in disabled) signals += apk.run(url)
        if (allowlist.isAllowed(url)) {
            signals += allowlist.run(url)
            return Verdict.of(signals, url)
        }
        localChecks.filter { (id, _) -> id !in disabled }.mapTo(signals) { (_, check) -> check.run(url) }

        val remaining = deadline - clock()
        if (registrationDates != null && CheckId.DOMAIN_AGE !in disabled && remaining >= Budget.MIN_LOOKUP_MS) {
            signals += DomainAgeCheck(registrationDates, timeoutMs = remaining.toInt()).run(url)
        }
        return Verdict.of(signals, url)
    }

    private fun upiSignal(url: NormalisedUrl, disabled: Set<CheckId>) =
        if (CheckId.UPI_INTENT in disabled) null else upi.run(url)

    private fun isListedShortener(url: NormalisedUrl) =
        url.host in data.shorteners || url.registrableDomain in data.shorteners
}
