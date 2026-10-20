package com.dwarpallabs.linkguard.core

/** Identifies a check. [code] matches the `expected_check` column of the corpus files. */
enum class CheckId(val code: String) {
    UNWRAP("unwrap"),
    ALLOWLIST("allowlist"),
    APK_DOWNLOAD("apk_download"),
    BRAND_LOOKALIKE("brand_lookalike"),
    UPI_INTENT("upi_intent"),
    WEIRD_HOST("weird_host"),
    DOMAIN_AGE("domain_age"),
    REPUTATION("reputation"),

    /** The organisation's own lists, from managed configuration ([ManagedPolicy]). Not a user toggle. */
    MANAGED("managed"),
}

/**
 * The result of one check.
 *
 * @property score 0.0 (nothing found) to 1.0 (certain scam).
 * @property reason machine-readable reason, `<check>.<detail>`, e.g. `brand_lookalike.typo`.
 * @property messageKey Android string resource name for the user-facing sentence; null when [score] is 0.
 * @property details values shown on the interstitial (brand name, UPI payee, amount). In memory only.
 */
data class Signal(
    val check: CheckId,
    val score: Double,
    val reason: String,
    val messageKey: String?,
    val details: Map<String, String> = emptyMap(),
) {
    init {
        require(score in 0.0..1.0) { "score out of range: $score" }
    }

    val fired: Boolean get() = score > 0.0

    companion object {
        fun none(check: CheckId) = Signal(check, 0.0, "${check.code}.none", null)
    }
}

/** A single URL check. Implementations must be pure apart from explicitly injected network clients. */
fun interface Check {
    fun run(url: NormalisedUrl): Signal
}

/** The engine's decision for one link. [finalUrl] is null when the input could not be parsed. */
data class Verdict(
    val score: Double,
    val level: Level,
    val signals: List<Signal>,
    val finalUrl: NormalisedUrl?,
) {
    enum class Level { SILENT, SOFT, STRONG }

    /** The strongest signal, which the interstitial explains. */
    val top: Signal? get() = signals.filter { it.fired }.maxByOrNull { it.score }

    companion object {
        const val SOFT_THRESHOLD = 0.3
        const val STRONG_THRESHOLD = 0.7

        /** Verdict thresholds: < 0.3 silent, 0.3–0.7 soft, > 0.7 strong. */
        fun levelFor(score: Double) = when {
            score < SOFT_THRESHOLD -> Level.SILENT
            score <= STRONG_THRESHOLD -> Level.SOFT
            else -> Level.STRONG
        }

        fun of(signals: List<Signal>, finalUrl: NormalisedUrl?): Verdict {
            val score = signals.maxOfOrNull { it.score } ?: 0.0
            return Verdict(score, levelFor(score), signals, finalUrl)
        }
    }
}
