package com.dwarpallabs.linkguard.core.checks

import com.dwarpallabs.linkguard.core.Check
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.core.NormalisedUrl
import com.dwarpallabs.linkguard.core.Signal
import java.time.Duration
import java.time.Instant

/** Looks up when a registrable domain was first registered. */
fun interface RegistrationDates {
    /** Creation date of [registrableDomain], or null if unknown, unsupported, offline or not answered within [timeoutMs]. */
    fun creationDate(registrableDomain: String, timeoutMs: Int): Instant?
}

/**
 * Process-lifetime, in-memory cache in front of [RegistrationDates]. Never written to disk:
 * a persisted cache would be a history of the sites the user opened. Only answers are cached, so a
 * timeout or offline miss is retried next time.
 */
class MemoryCachedRegistrationDates(
    private val delegate: RegistrationDates,
    private val maxEntries: Int = 256,
) : RegistrationDates {

    private val cache = object : LinkedHashMap<String, Instant>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Instant>?) = size > maxEntries
    }

    override fun creationDate(registrableDomain: String, timeoutMs: Int): Instant? {
        synchronized(cache) {
            cache[registrableDomain]?.let { return it }
        }
        val result = delegate.creationDate(registrableDomain, timeoutMs) ?: return null
        synchronized(cache) { cache[registrableDomain] = result }
        return result
    }
}

/** Domain registered less than [maxAge] ago: scam sites are usually days old. */
class DomainAgeCheck(
    private val dates: RegistrationDates,
    private val timeoutMs: Int = 1_500,
    private val now: () -> Instant = Instant::now,
    private val maxAge: Duration = Duration.ofDays(30),
) : Check {

    override fun run(url: NormalisedUrl): Signal {
        val domain = url.registrableDomain
        if (!url.isWeb || domain == null) return Signal.none(CheckId.DOMAIN_AGE)
        val created = dates.creationDate(domain, timeoutMs) ?: return Signal.none(CheckId.DOMAIN_AGE)
        val age = Duration.between(created, now())
        if (age.isNegative || age >= maxAge) return Signal.none(CheckId.DOMAIN_AGE)
        return Signal(
            CheckId.DOMAIN_AGE, SCORE, "domain_age.young", "warn_domain_age",
            mapOf("age_days" to age.toDays().toString()),
        )
    }

    companion object {
        const val SCORE = 0.5
    }
}
