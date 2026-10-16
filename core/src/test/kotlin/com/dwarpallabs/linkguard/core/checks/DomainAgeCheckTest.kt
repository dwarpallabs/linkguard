package com.dwarpallabs.linkguard.core.checks

import com.dwarpallabs.linkguard.core.HttpResponse
import com.dwarpallabs.linkguard.core.RdapBootstrap
import com.dwarpallabs.linkguard.core.RdapClient
import com.dwarpallabs.linkguard.core.TestSupport.url
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DomainAgeCheckTest {

    private val now = Instant.parse("2026-10-15T00:00:00Z")
    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))

    @Test
    fun youngDomainIsSoft() {
        val check = DomainAgeCheck({ _, _ -> daysAgo(3) }, now = { now })
        val signal = check.run(url("https://shop.fresh.example/"))
        assertEquals(0.5, signal.score)
        assertEquals("3", signal.details["age_days"])
    }

    @Test
    fun oldOrUnknownDomainIsSilent() {
        assertFalse(DomainAgeCheck({ _, _ -> daysAgo(30) }, now = { now }).run(url("https://old.example/")).fired)
        assertFalse(DomainAgeCheck({ _, _ -> null }, now = { now }).run(url("https://offline.example/")).fired)
    }

    @Test
    fun asksOnlyForTheRegistrableDomain() {
        val asked = mutableListOf<String>()
        DomainAgeCheck({ d, _ -> asked += d; null }, now = { now }).run(url("https://a.b.shop.example.co.in/secret/path?q=1"))
        assertEquals(listOf("example.co.in"), asked)
    }

    @Test
    fun skipsIpsAndUpi() {
        val asked = mutableListOf<String>()
        val check = DomainAgeCheck({ d, _ -> asked += d; null }, now = { now })
        check.run(url("http://192.0.2.1/"))
        check.run(url("upi://pay?pa=a@b"))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun memoryCacheAsksOncePerDomain() {
        var calls = 0
        val cached = MemoryCachedRegistrationDates({ _, _ -> calls++; now })
        repeat(3) { cached.creationDate("example.com", 100) }
        assertEquals(1, calls)
    }

    @Test
    fun memoryCacheRetriesMisses() {
        var calls = 0
        val cached = MemoryCachedRegistrationDates({ _, _ -> calls++; null })
        repeat(3) { cached.creationDate("example.com", 100) }
        assertEquals(3, calls)
    }

    @Test
    fun rdapClientQueriesRegistryAndParsesRegistrationEvent() {
        val body = """{"objectClassName":"domain","events":[
            {"eventAction":"last changed","eventDate":"2026-10-01T00:00:00Z"},
            {"eventAction":"registration","eventDate":"2026-10-12T08:30:00+05:30"}]}"""
        val http = object : FakeHttpBase() {
            override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int): HttpResponse? {
                requested += url
                return HttpResponse(200, body = body)
            }
        }
        val client = RdapClient(http, mapOf("com" to "https://rdap.verisign.com/com/v1/"))
        assertEquals(Instant.parse("2026-10-12T03:00:00Z"), client.creationDate("example.com", 1_000))
        assertEquals(listOf("https://rdap.verisign.com/com/v1/domain/example.com"), http.requested)
        assertNull(client.creationDate("example.unknowntld", 1_000))
    }

    @Test
    fun bootstrapKeepsOnlyHttpsServers() {
        val servers = RdapBootstrap.parse(
            """{"services":[[["com","net"],["https://rdap.verisign.com/com/v1/"]],
               [["kg"],["http://rdap.cctld.kg/"]],[["in"],["http://x/","https://rdap.nixiregistry.in/rdap"]]]}""",
        )
        assertEquals("https://rdap.verisign.com/com/v1/", servers["net"])
        assertEquals("https://rdap.nixiregistry.in/rdap/", servers["in"])
        assertNull(servers["kg"])
    }

    private open class FakeHttpBase : com.dwarpallabs.linkguard.core.Http {
        val requested = mutableListOf<String>()
        override fun head(url: String, timeoutMs: Int): HttpResponse? = null
        override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int): HttpResponse? = null
    }
}
