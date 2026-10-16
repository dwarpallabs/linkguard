package com.dwarpallabs.linkguard

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Anonymous per-domain reporter token: HMAC-SHA256(secret, registrable domain).
 *
 * The secret is random, created once per install and never leaves the phone. The server can tell
 * that the same phone reported the same website twice (so it counts once), but tokens for
 * different websites are unrelated, so one phone's reports cannot be linked together.
 */
class ReporterToken(private val secret: ByteArray) {

    init {
        require(secret.size == SECRET_BYTES) { "secret must be $SECRET_BYTES bytes" }
    }

    fun forDomain(registrableDomain: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(registrableDomain.lowercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SECRET_BYTES = 32

        fun newSecret(): ByteArray = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }

        /** Loads the install's secret from [Prefs], creating it on first use. */
        fun of(prefs: Prefs): ReporterToken {
            val stored = prefs.reporterSecret?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            val secret = stored?.takeIf { it.size == SECRET_BYTES } ?: newSecret().also {
                prefs.reporterSecret = Base64.getEncoder().encodeToString(it)
            }
            return ReporterToken(secret)
        }
    }
}
