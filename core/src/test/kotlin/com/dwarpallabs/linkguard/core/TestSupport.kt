package com.dwarpallabs.linkguard.core

import com.dwarpallabs.linkguard.core.checks.RegistrationDates
import java.io.File
import java.time.Duration
import java.time.Instant

/** Shared, offline test fixtures. Loading the bundled data once keeps the suite fast. */
object TestSupport {

    val data: EngineData by lazy { EngineData.loadBundled(listOf(fixtureAllowlist())) }

    val http: FakeHttp by lazy { FakeHttp.fromResource("fake_redirects.tsv") }

    val dates: FakeRegistrationDates by lazy { FakeRegistrationDates.fromResource("fake_rdap.tsv") }

    val reputation: FakeReputation by lazy {
        FakeReputation.fromResources("fixture_blocklist.txt", "fixture_reported.tsv", "fixture_campaign.tsv")
    }


    /** Engine with a fake network: shorteners, RDAP and the blocklist server answer from fixtures. */
    val engine: LinkEngine by lazy { LinkEngine(data, http, dates, reputation) }

    fun url(raw: String): NormalisedUrl = requireNotNull(NormalisedUrl.parse(raw, data.psl)) { "unparseable: $raw" }

    fun resourceLines(name: String): List<String> =
        requireNotNull(TestSupport::class.java.classLoader.getResource(name)) { "missing $name" }
            .readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }

    data class CorpusEntry(val url: String, val check: String, val note: String)

    fun corpus(name: String): List<CorpusEntry> =
        File(System.getProperty("corpus.dir"), name).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.split('\t').let { CorpusEntry(it[0], it[1], it[2]) } }

    private fun fixtureAllowlist(): ListSource =
        DomainListSource(resourceLines("fixture_allowlist.txt").map { it.trim() }, emptyList())
}

/** Plays the LinkGuard server: answers hash-prefix queries from plain-text fixtures. */
class FakeReputation(
    private val blocked: Set<String>,
    private val reported: Map<String, Int> = emptyMap(),
    private val campaign: Map<String, Int> = emptyMap(),
) : ReputationLookup {

    val prefixesSeen = mutableListOf<String>()

    override fun lookup(prefixes: List<String>, timeoutMs: Int): ServerVerdicts {
        prefixesSeen += prefixes
        fun match(h: String) = prefixes.any { h.startsWith(it) }
        return ServerVerdicts(blocked.filter(::match).toSet(), reported.filterKeys(::match), campaign.filterKeys(::match))
    }

    companion object {
        fun fromResource(name: String) = FakeReputation(TestSupport.resourceLines(name).map { sha256(it.trim()) }.toSet())

        fun fromResources(blockedName: String, reportedName: String, campaignName: String? = null) = FakeReputation(
            TestSupport.resourceLines(blockedName).map { sha256(it.trim()) }.toSet(),
            counts(reportedName),
            campaignName?.let(::counts).orEmpty(),
        )

        private fun counts(name: String) = TestSupport.resourceLines(name).associate { line ->
            val (domain, count) = line.split('\t')
            sha256(domain.trim()) to count.trim().toInt()
        }
    }
}

/** Answers HEAD requests from a table keyed by host + path; anything else is "offline". */
class FakeHttp(private val responses: Map<String, HttpResponse>) : Http {

    val requested = mutableListOf<String>()

    override fun head(url: String, timeoutMs: Int): HttpResponse? {
        requested += url
        return responses[url.substringAfter("://")]
    }

    override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int, headers: Map<String, String>): HttpResponse? {
        requested += url
        return null
    }

    companion object {
        fun fromResource(name: String) = FakeHttp(
            TestSupport.resourceLines(name).associate { line ->
                val fields = line.split('\t')
                fields[0] to if (fields[1] == "-") {
                    HttpResponse(200, contentType = fields.getOrNull(2))
                } else {
                    HttpResponse(301, location = fields[1])
                }
            },
        )
    }
}

open class FakeRegistrationDates(private val created: Map<String, Instant>) : RegistrationDates {

    val asked = mutableListOf<String>()

    override fun creationDate(registrableDomain: String, timeoutMs: Int): Instant? {
        asked += registrableDomain
        return created[registrableDomain]
    }

    companion object {
        fun fromResource(name: String): FakeRegistrationDates {
            val now = Instant.now()
            return FakeRegistrationDates(
                TestSupport.resourceLines(name).associate { line ->
                    val (domain, days) = line.split('\t')
                    domain to now.minus(Duration.ofDays(days.trim().toLong()))
                },
            )
        }
    }
}
