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
data class Budget(val normalMs: Long, val shortenerMs: Long, val lookupMs: Long = normalMs) {
    companion object {
        /**
         * Production budget: local checks and RDAP within 300 ms; the LinkGuard server lookup for
         * links not on the allowlist may take up to 1 s (the app shows "Checking link…" after
         * 300 ms); short links up to 1.5 s.
         */
        val INTERACTIVE = Budget(normalMs = 300, shortenerMs = 1_500, lookupMs = 1_000)

        /** No practical limit; for tests and tools. */
        val UNLIMITED = Budget(normalMs = 60_000, shortenerMs = 60_000, lookupMs = 60_000)

        /** DomainAge is skipped when less than this is left. */
        const val MIN_LOOKUP_MS = 100L
    }
}

/**
 * Runs every check on a link and returns a [Verdict]. Blocking: call it off the main thread.
 *
 * Order: normalise → (upi: only UpiIntent) → unwrap → organisation's blocklist (stops on a hit)
 * → ApkDownload → organisation's and LinkGuard's allowlists (stop on a hit)
 * → BrandLookalike, WeirdHost → Reputation (server, by hash prefix) → DomainAge (RDAP).
 * Verdict = max signal.
 *
 * ApkDownload runs before the allowlist on purpose: an APK on a trusted file host is still dangerous.
 * The two network checks are skipped when a local check already gave a strong warning (the answer
 * cannot change the outcome, so nothing is sent) or when the budget is spent.
 *
 * Pass `http = null` / `reputation = null` / `registrationDates = null` to run offline.
 */
class LinkEngine(
    private val data: EngineData,
    http: Http?,
    private val registrationDates: RegistrationDates?,
    private val reputation: ReputationLookup? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val unwrapper = http?.let { Unwrapper(it, data, clock = clock) }
    private val matcher = BrandMatcher(data.brands)

    private val upi = UpiIntentCheck(matcher)

    private val apk = ApkDownloadCheck(data)
    private val allowlist = AllowlistCheck(data)
    private val localChecks: List<Pair<CheckId, Check>> = listOf(
        CheckId.BRAND_LOOKALIKE to BrandLookalikeCheck(matcher),
        CheckId.WEIRD_HOST to WeirdHostCheck(data, matcher),
    )

    /** True if [raw] points at a known link shortener, so evaluation may take [Budget.shortenerMs]. */
    fun isShortener(raw: String): Boolean =
        NormalisedUrl.parse(raw, data.psl)?.let(::isListedShortener) ?: false

    /**
     * Runs every check on [raw]. [myBrands] are the names of brands the user opens often (learned
     * on the phone, never sent): a lookalike of one of them is warned about more strongly.
     * [managed] is the organisation's policy, if the phone is managed: its blocked websites get a
     * strong warning before any other check, its trusted ones are forwarded like allowlisted ones,
     * and it can keep every check on whatever [disabled] says.
     */
    fun evaluate(
        raw: String,
        budget: Budget = Budget.UNLIMITED,
        disabled: Set<CheckId> = emptySet(),
        myBrands: Set<String> = emptySet(),
        managed: ManagedPolicy = ManagedPolicy.NONE,
    ): Verdict {
        val start = clock()
        @Suppress("NAME_SHADOWING") val disabled = managed.disabled(disabled)
        val parsed = NormalisedUrl.parse(raw, data.psl) ?: return Verdict.of(emptyList(), null)
        if (parsed.isUpi) return Verdict.of(listOfNotNull(upiSignal(parsed, disabled)), parsed)

        val deadline = start + if (isListedShortener(parsed)) budget.shortenerMs else budget.normalMs
        val unwrapped = if (CheckId.UNWRAP in disabled) null else unwrapper?.unwrap(parsed, deadline - clock())
        val url = unwrapped?.url ?: parsed
        val unwrapSignal = Signal(CheckId.UNWRAP, 0.0, "unwrap.hops_${unwrapped?.hops ?: 0}", null)
        if (url.isUpi) return Verdict.of(listOfNotNull(unwrapSignal, upiSignal(url, disabled)), url)

        val signals = mutableListOf(unwrapSignal)
        if (managed.blocks(url) || managed.blocks(parsed)) {
            signals += Signal(CheckId.MANAGED, MANAGED_BLOCK_SCORE, "managed.block", MANAGED_BLOCK_MESSAGE)
            return Verdict.of(signals, url)
        }
        if (CheckId.APK_DOWNLOAD !in disabled) signals += apk.run(url)
        if (managed.allows(url)) {
            signals += Signal.none(CheckId.MANAGED).copy(reason = "managed.allow")
            return Verdict.of(signals, url)
        }
        if (allowlist.isAllowed(url)) {
            signals += allowlist.run(url)
            return Verdict.of(signals, url)
        }
        localChecks.filter { (id, _) -> id !in disabled }.mapTo(signals) { (_, check) -> withMyBrands(check.run(url), myBrands) }

        val alreadyStrong = signals.any { it.score > Verdict.STRONG_THRESHOLD }
        if (CheckId.REPUTATION !in disabled && !alreadyStrong) {
            // The server lookup may use the longer lookup budget; DomainAge below keeps the normal one.
            val remaining = maxOf(deadline, start + budget.lookupMs) - clock()
            val remote = reputation.takeIf { remaining >= Budget.MIN_LOOKUP_MS }
            signals += ReputationCheck(data, remote, timeoutMs = remaining.coerceAtLeast(0).toInt()).run(url)
        }
        val remaining = deadline - clock()
        val decided = signals.any { it.score >= DomainAgeCheck.SCORE }
        if (registrationDates != null && CheckId.DOMAIN_AGE !in disabled && !decided && remaining >= Budget.MIN_LOOKUP_MS) {
            signals += DomainAgeCheck(registrationDates, timeoutMs = remaining.toInt()).run(url)
        }
        return Verdict.of(signals, url)
    }

    /** The brand whose official website [url] is, if any; used on the phone to learn the user's brands. */
    fun ownerBrand(url: NormalisedUrl): Brand? =
        if (!url.isWeb || url.isIpLiteral) null else data.brands.firstOrNull { it.owns(url.host, url.registrableDomain) }

    private fun withMyBrands(signal: Signal, myBrands: Set<String>): Signal {
        if (signal.check != CheckId.BRAND_LOOKALIKE || !signal.fired || signal.details["brand"] !in myBrands) return signal
        return signal.copy(score = minOf(1.0, signal.score + MY_BRAND_BOOST), messageKey = MY_BRAND_MESSAGE)
    }

    private fun upiSignal(url: NormalisedUrl, disabled: Set<CheckId>) =

        if (CheckId.UPI_INTENT in disabled) null else upi.run(url)

    private fun isListedShortener(url: NormalisedUrl) =
        url.host in data.shorteners || url.registrableDomain in data.shorteners

    companion object {
        /** Added to a lookalike of one of the user's own brands (capped at 1.0). */
        const val MY_BRAND_BOOST = 0.2

        /** "This website is pretending to be SBI, which you use…" */
        const val MY_BRAND_MESSAGE = "warn_brand_lookalike_mine"

        /** A website the organisation marked as unsafe: a strong warning, still with Open anyway. */
        const val MANAGED_BLOCK_SCORE = 0.9

        /** "Your organisation has marked this website as unsafe." */
        const val MANAGED_BLOCK_MESSAGE = "warn_managed_block"
    }
}
