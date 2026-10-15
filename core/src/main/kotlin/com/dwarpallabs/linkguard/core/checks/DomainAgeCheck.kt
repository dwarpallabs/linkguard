package com.dwarpallabs.linkguard.core.checks

import com.dwarpallabs.linkguard.core.Check
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.core.NormalisedUrl
import com.dwarpallabs.linkguard.core.Signal
import java.time.Duration
import java.time.Instant

/** Looks up when a registrable domain was first registered. */
fun interface RegistrationDates {
    /** Creation date of [registrableDomain], or null if unknown, unsupported, offline or timed out. */
    fun creationDate(registrableDomain: String): Instant?
}

/**
 * Process-lifetime, in-memory cache in front of [RegistrationDates]. Never written to disk:
 * a persisted cache would be a history of the sites the user opened.
 */
class MemoryCachedRegistrationDates(
    private val delegate: RegistrationDates,
    private val maxEntries: Int = 256,
) : RegistrationDates {

    private object Unknown

    private val cache = object : LinkedHashMap<String, Any>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?) = size > maxEntries
    }

    override fun creationDate(registrableDomain: String): Instant? {
        synchronized(cache) {
            cache[registrableDomain]?.let { return it as? Instant }
        }
        val result = delegate.creationDate(registrableDomain)
        synchronized(cache) { cache[registrableDomain] = result ?: Unknown }
        return result
    }
}

/** Domain registered less than [maxAge] ago: scam sites are usually days old. */
class DomainAgeCheck(
    private val dates: RegistrationDates,
    private val now: () -> Instant = Instant::now,
    private val maxAge: Duration = Duration.ofDays(30),
) : Check {

    override fun run(url: NormalisedUrl): Signal {
        val domain = url.registrableDomain
        if (!url.isWeb || domain == null) return Signal.none(CheckId.DOMAIN_AGE)
        val created = dates.creationDate(domain) ?: return Signal.none(CheckId.DOMAIN_AGE)
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
