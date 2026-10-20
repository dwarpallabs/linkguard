package com.dwarpallabs.linkguard.core

import com.dwarpallabs.linkguard.core.checks.RegistrationDates
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Parses the IANA RDAP bootstrap file (https://data.iana.org/rdap/dns.json). */
internal object RdapBootstrap {

    /** Parses the compact `tld<TAB>https-base-url` file the data module generates from the JSON at build time. */
    fun parseTsv(lines: Sequence<String>): Map<String, String> = lines
        .map { it.split('\t') }
        .filter { it.size == 2 && it[1].startsWith("https://") }
        .associate { (tld, base) -> tld to base }

    /** TLD → base URL. Only HTTPS servers are kept, so the domain name never travels unencrypted. */
    fun parse(json: String): Map<String, String> {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return emptyMap()
        val services = root["services"] as? List<*> ?: return emptyMap()
        val result = HashMap<String, String>()
        for (service in services) {
            val pair = service as? List<*> ?: continue
            val tlds = pair.getOrNull(0) as? List<*> ?: continue
            val urls = pair.getOrNull(1) as? List<*> ?: continue
            val https = urls.filterIsInstance<String>().firstOrNull { it.startsWith("https://") } ?: continue
            val base = if (https.endsWith("/")) https else "$https/"
            tlds.filterIsInstance<String>().forEach { result[it.lowercase()] = base }
        }
        return result
    }
}

/**
 * Asks the TLD's registry when a domain was registered, over RDAP.
 *
 * Privacy: sends only the registrable domain (`example.com`), never subdomains, paths or queries.
 */
class RdapClient(
    private val http: Http,
    private val servers: Map<String, String>,
    private val timeoutMs: Int = 1_500,
) : RegistrationDates {

    override fun creationDate(registrableDomain: String, timeoutMs: Int): Instant? {
        val tld = registrableDomain.substringAfterLast('.')
        val base = servers[tld] ?: return null
        val timeout = minOf(timeoutMs, this.timeoutMs)
        val response = http.get(base + "domain/" + registrableDomain, "application/rdap+json", timeout)
            ?: return null
        if (response.status != 200) return null
        return registrationEvent(response.body ?: return null)
    }

    companion object {
        /** The `registration` event date from an RDAP domain response. */
        fun registrationEvent(json: String): Instant? {
            val root = MiniJson.parse(json) as? Map<*, *> ?: return null
            val events = root["events"] as? List<*> ?: return null
            val date = events.filterIsInstance<Map<*, *>>()
                .firstOrNull { it["eventAction"] == "registration" }
                ?.get("eventDate") as? String ?: return null
            return try {
                OffsetDateTime.parse(date).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
