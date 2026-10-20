package com.dwarpallabs.linkguard.core

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** What the server knows about domains sharing a hash prefix, keyed by full SHA-256 hash. */
data class ServerVerdicts(
    /** On the blocklist (feeds or confirmed): strong warning. */
    val blocked: Set<String> = emptySet(),
    /** Distinct LinkGuard users who reported the domain in the last 7 days (only sent when 3 or more). */
    val reported: Map<String, Int> = emptyMap(),
    /**
     * Member of a confirmed scam campaign (websites sharing infrastructure with a blocklisted one and
     * reported by several users): distinct reporters across the whole campaign this week.
     */
    val campaign: Map<String, Int> = emptyMap(),
    /** Who confirmed a blocked domain, when a partner did ("HDFC Bank"); shown as "Confirmed by …". */
    val sources: Map<String, String> = emptyMap(),
) {
    fun forPrefix(prefix: String) = ServerVerdicts(
        blocked.filter { it.startsWith(prefix) }.toSet(),
        reported.filterKeys { it.startsWith(prefix) },
        campaign.filterKeys { it.startsWith(prefix) },
        sources.filterKeys { it.startsWith(prefix) },
    )

    operator fun plus(other: ServerVerdicts) =
        ServerVerdicts(blocked + other.blocked, reported + other.reported, campaign + other.campaign, sources + other.sources)

    companion object {
        /**
         * Parses `/v1/lookup` lines: `<hash>\tblock`, `<hash>\treported\t<N>`, `<hash>\tcampaign\t<N>`,
         * or a bare `<hash>` (treated as blocked). Unknown line types are ignored, so the server can
         * add new ones without breaking older apps.
         */
        fun parse(body: String): ServerVerdicts {
            val blocked = HashSet<String>()
            val reported = HashMap<String, Int>()
            val campaign = HashMap<String, Int>()
            val sources = HashMap<String, String>()
            for (line in body.lineSequence()) {
                val fields = line.trim().split('\t')
                val hash = fields[0]
                if (hash.length != 64) continue
                when (fields.getOrNull(1)) {
                    null, "block" -> blocked += hash
                    "reported" -> fields.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }?.let { reported[hash] = it }
                    "campaign" -> fields.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }?.let { campaign[hash] = it }
                    "source" -> fields.getOrNull(2)?.let(::cleanSource)?.let { sources[hash] = it }
                }
            }
            return ServerVerdicts(blocked, reported, campaign, sources)
        }

        /** A partner's name as the phone shows it: printable, one line, at most 60 characters. */
        private fun cleanSource(raw: String): String? =
            raw.filter { !it.isISOControl() }.trim().take(60).takeIf { it.isNotEmpty() }
    }
}

/**
 * Asks the LinkGuard server about domains sharing a hash prefix.
 *
 * Privacy: only 4-byte prefixes (8 hex characters) of SHA-256 hashes ever leave the phone, so the
 * server cannot tell which domain was opened. The full lists never leave the server.
 */
fun interface ReputationLookup {
    /** Server verdicts for domains starting with any of [prefixes], or null if unavailable. */
    fun lookup(prefixes: List<String>, timeoutMs: Int): ServerVerdicts?
}

/**
 * [ReputationLookup] against a LinkGuard server's `/v1/lookup`. Without [apiKey] it uses the anonymous
 * endpoint, which is for LinkGuard and non-commercial apps; commercial apps pass the key Dwarpal Labs
 * issued them, sent as `Authorization: Bearer <key>`.
 */
class HttpReputationLookup(
    private val http: Http,
    private val baseUrl: String,
    private val apiKey: String? = null,
) : ReputationLookup {

    override fun lookup(prefixes: List<String>, timeoutMs: Int): ServerVerdicts? {
        require(prefixes.all { it.length == PREFIX_LENGTH && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }) {
            "prefixes must be 8 hex chars"
        }
        val headers = apiKey?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
        val response = http.get("$baseUrl/v1/lookup?p=${prefixes.joinToString(",")}", "text/plain", timeoutMs, 64 * 1024, headers)
            ?: return null
        if (response.status != 200) return null
        return ServerVerdicts.parse(response.body.orEmpty())
    }

    companion object {
        const val PREFIX_LENGTH = 8
    }
}

/**
 * Process-lifetime cache in front of [ReputationLookup], keyed by prefix. Entries expire after
 * [ttlMs] so newly blocked or reported domains are picked up. Never written to disk.
 */
class MemoryCachedReputation(
    private val delegate: ReputationLookup,
    private val ttlMs: Long = 10 * 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 512,
) : ReputationLookup {

    private class Entry(val verdicts: ServerVerdicts, val at: Long)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    override fun lookup(prefixes: List<String>, timeoutMs: Int): ServerVerdicts? {
        val now = clock()
        val cached = synchronized(cache) { prefixes.associateWith { p -> cache[p]?.takeIf { now - it.at < ttlMs } } }
        val missing = prefixes.filter { cached[it] == null }
        val known = cached.values.filterNotNull().fold(ServerVerdicts()) { acc, e -> acc + e.verdicts }
        if (missing.isEmpty()) return known
        val fetched = delegate.lookup(missing, timeoutMs) ?: return null
        synchronized(cache) { missing.forEach { p -> cache[p] = Entry(fetched.forPrefix(p), now) } }
        return fetched + known
    }
}

/**
 * The downloaded allowlist: SHA-256 hashes of allowed domains. Swapped in place when a newer
 * signed bundle arrives, so the engine never needs rebuilding.
 */
class HashedAllowlist : ListSource {

    @Volatile
    private var hashes: Set<String> = emptySet()

    @Volatile
    var version: String? = null
        private set

    val size: Int get() = hashes.size

    fun replace(newVersion: String, newHashes: Set<String>) {
        hashes = newHashes
        version = newVersion
    }

    override fun opinion(host: String, registrableDomain: String?): Opinion? {
        val current = hashes
        if (current.isEmpty()) return null
        if (sha256(host) in current) return Opinion.ALLOW
        if (registrableDomain != null && registrableDomain != host && sha256(registrableDomain) in current) return Opinion.ALLOW
        return null
    }
}

/**
 * Signed allowlist bundle served by `/v1/allowlist`:
 * first line `linkguard-allowlist 1 <version>`, then one lowercase hex SHA-256 per line.
 * The `X-Signature` header is base64 of an ECDSA P-256 / SHA-256 signature over the exact body,
 * in raw r||s form (WebCrypto's format).
 */
object AllowlistBundle {

    data class Parsed(val version: String, val hashes: Set<String>)

    /** Returns the bundle if [signatureB64] is a valid signature by [publicKey] over [body], else null. */
    fun verifyAndParse(body: String, signatureB64: String?, publicKey: PublicKey): Parsed? {
        if (signatureB64.isNullOrBlank()) return null
        val raw = try {
            Base64.getDecoder().decode(signatureB64.trim())
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (raw.size != 64) return null
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(body.toByteArray(Charsets.UTF_8))
        if (!verifier.verify(p1363ToDer(raw))) return null
        return parse(body)
    }

    fun parse(body: String): Parsed? {
        val lines = body.lineSequence().iterator()
        if (!lines.hasNext()) return null
        val header = lines.next().split(' ')
        if (header.size != 3 || header[0] != "linkguard-allowlist" || header[1] != "1") return null
        val hashes = HashSet<String>()
        for (line in lines) {
            val h = line.trim()
            if (h.length == 64 && h.all { it in '0'..'9' || it in 'a'..'f' }) hashes += h
        }
        return Parsed(header[2], hashes)
    }

    /** Decodes a base64 X.509 SubjectPublicKeyInfo EC public key. */
    fun publicKey(base64Spki: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64Spki)))

    /** Converts a raw r||s ECDSA signature (64 bytes for P-256) to the ASN.1 DER form Java expects. */
    internal fun p1363ToDer(raw: ByteArray): ByteArray {
        fun integer(bytes: ByteArray): ByteArray {
            var start = 0
            while (start < bytes.size - 1 && bytes[start] == 0.toByte()) start++
            val trimmed = bytes.copyOfRange(start, bytes.size)
            val body = if (trimmed[0].toInt() and 0x80 != 0) byteArrayOf(0) + trimmed else trimmed
            return byteArrayOf(0x02, body.size.toByte()) + body
        }
        val half = raw.size / 2
        val seq = integer(raw.copyOfRange(0, half)) + integer(raw.copyOfRange(half, raw.size))
        return byteArrayOf(0x30, seq.size.toByte()) + seq
    }

    /** Converts a DER ECDSA signature to raw r||s; used by tests to emulate the server. */
    internal fun derToP1363(der: ByteArray, size: Int = 32): ByteArray {
        var i = 2
        fun next(): ByteArray {
            check(der[i] == 0x02.toByte()); val len = der[i + 1].toInt(); val v = der.copyOfRange(i + 2, i + 2 + len); i += 2 + len
            val trimmed = if (v.size > size) v.copyOfRange(v.size - size, v.size) else v
            return ByteArray(size - trimmed.size) + trimmed
        }
        return next() + next()
    }
}
