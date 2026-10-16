package com.dwarpallabs.linkguard

import android.content.Context
import com.dwarpallabs.linkguard.core.AllowlistBundle
import com.dwarpallabs.linkguard.core.HashedAllowlist
import com.dwarpallabs.linkguard.core.Http
import java.io.File

/** The LinkGuard server. See dwarpallabs/linkguard-server. */
object ServerApi {
    const val BASE_URL = "https://api.dwarpallabs.com"

    /** Public half of the allowlist signing key (ECDSA P-256, X.509 SPKI). Bundles not signed by it are ignored. */
    const val ALLOWLIST_PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAExIq/JlE6GlXetaIftLTB7KdSJX4G8TtU9dOUA/Zz3QmJ/s/QZuCguFRNjK3WEkrSsi0J7X3tO2lElOE2j+EIOg=="
}

/**
 * Sends a user's report to the private review queue. Only called when the user taps Submit;
 * the link and note go to Dwarpal Labs and nowhere else.
 */
class ReportClient(private val http: Http, private val baseUrl: String = ServerApi.BASE_URL) {

    fun send(link: String, note: String, appVersion: String): Boolean {
        val response = http.post("$baseUrl/v1/report", "application/json", json(link, note, appVersion), TIMEOUT_MS)
        return response != null && response.status in 200..299
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /** Minimal JSON writer for three strings: no JSON library needed. */
        fun json(link: String, note: String, appVersion: String) =
            """{"url":${quote(link.take(2_000))},"note":${quote(note.take(1_000))},"app_version":${quote(appVersion)}}"""

        private fun quote(s: String) = buildString {
            append('"')
            for (c in s) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
    }
}

/**
 * Keeps the hashed allowlist fresh: loads the last verified bundle from app storage at start-up and
 * downloads a new one at most once a day. Every bundle's signature is checked, both on download
 * and when read back from disk. The file holds list data only, nothing about the user.
 */
class AllowlistUpdater(
    private val context: Context,
    private val http: Http,
    private val target: HashedAllowlist,
    private val baseUrl: String = ServerApi.BASE_URL,
    private val publicKeyBase64: String = ServerApi.ALLOWLIST_PUBLIC_KEY,
) {
    private val bodyFile get() = File(context.filesDir, "allowlist.txt")
    private val sigFile get() = File(context.filesDir, "allowlist.sig")

    /** Loads the stored bundle, if present and validly signed. */
    fun loadCached() {
        val body = bodyFile.takeIf { it.exists() }?.readText() ?: return
        val sig = sigFile.takeIf { it.exists() }?.readText() ?: return
        AllowlistBundle.verifyAndParse(body, sig, publicKey())?.let { target.replace(it.version, it.hashes) }
    }

    /** Downloads a newer bundle if a day has passed (or [force]). Blocking: call off the main thread. */
    fun refreshIfStale(force: Boolean = false) {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        if (!force && target.version != null && now - prefs.allowlistCheckedAt < DAY_MS) return
        val headers = target.version?.let { mapOf("If-None-Match" to "\"$it\"") }.orEmpty()
        val response = http.get("$baseUrl/v1/allowlist", "text/plain", TIMEOUT_MS, MAX_BYTES, headers) ?: return
        when (response.status) {
            304 -> prefs.allowlistCheckedAt = now
            200 -> {
                val body = response.body ?: return
                val sig = response.headers["x-signature"]
                val parsed = AllowlistBundle.verifyAndParse(body, sig, publicKey()) ?: return
                bodyFile.writeText(body)
                sigFile.writeText(sig.orEmpty())
                target.replace(parsed.version, parsed.hashes)
                prefs.allowlistCheckedAt = now
            }
        }
    }

    private fun publicKey() = AllowlistBundle.publicKey(publicKeyBase64)

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val TIMEOUT_MS = 15_000
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
