package com.dwarpallabs.linkguard.core

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Asks the LinkGuard server which blocklisted domains share a hash prefix.
 *
 * Privacy: only 4-byte prefixes (8 hex characters) of SHA-256 hashes ever leave the phone, so the
 * server cannot tell which domain was opened. The full blocklist never leaves the server.
 */
fun interface ReputationLookup {
    /** Full hashes on the blocklist that start with any of [prefixes], or null if unavailable. */
    fun blockedHashes(prefixes: List<String>, timeoutMs: Int): Set<String>?
}

class HttpReputationLookup(private val http: Http, private val baseUrl: String) : ReputationLookup {

    override fun blockedHashes(prefixes: List<String>, timeoutMs: Int): Set<String>? {
        require(prefixes.all { it.length == PREFIX_LENGTH && it.all(Char::isLetterOrDigit) }) { "prefixes must be 8 hex chars" }
        val response = http.get("$baseUrl/v1/lookup?p=${prefixes.joinToString(",")}", "text/plain", timeoutMs, 64 * 1024)
            ?: return null
        if (response.status != 200) return null
        return response.body.orEmpty().lineSequence().map { it.trim() }.filter { it.length == 64 }.toSet()
    }

    companion object {
        const val PREFIX_LENGTH = 8
    }
}

/**
 * Process-lifetime cache in front of [ReputationLookup], keyed by prefix. Entries expire after
 * [ttlMs] so newly blocked domains are picked up. Never written to disk.
 */
class MemoryCachedReputation(
    private val delegate: ReputationLookup,
    private val ttlMs: Long = 10 * 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 512,
) : ReputationLookup {

    private class Entry(val hashes: Set<String>, val at: Long)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    override fun blockedHashes(prefixes: List<String>, timeoutMs: Int): Set<String>? {
        val now = clock()
        val cached = synchronized(cache) { prefixes.associateWith { p -> cache[p]?.takeIf { now - it.at < ttlMs } } }
        val missing = prefixes.filter { cached[it] == null }
        if (missing.isEmpty()) return cached.values.flatMap { it!!.hashes }.toSet()
        val fetched = delegate.blockedHashes(missing, timeoutMs) ?: return null
        synchronized(cache) {
            missing.forEach { p -> cache[p] = Entry(fetched.filter { it.startsWith(p) }.toSet(), now) }
        }
        return fetched + cached.values.filterNotNull().flatMap { it.hashes }
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
