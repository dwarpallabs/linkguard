package com.dwarpallabs.linkguard.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(
    val status: Int,
    val location: String? = null,
    val contentType: String? = null,
    val body: String? = null,
)

/**
 * Minimal blocking HTTP used by Unwrap and DomainAge. Returns null on any network failure or timeout.
 * Implementations must not follow redirects, send cookies or send a Referer.
 */
interface Http {
    fun head(url: String, timeoutMs: Int): HttpResponse?
    fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int = 256 * 1024): HttpResponse?
}

/** [Http] on `HttpURLConnection`: available on both the JVM and Android, no dependencies. */
class UrlConnectionHttp(private val userAgent: String = "Mozilla/5.0 (Linux; Android) LinkGuard") : Http {

    override fun head(url: String, timeoutMs: Int): HttpResponse? =
        request(url, "HEAD", "*/*", timeoutMs) { conn -> response(conn, body = null) }

    override fun get(url: String, accept: String, timeoutMs: Int, maxBytes: Int): HttpResponse? =
        request(url, "GET", accept, timeoutMs) { conn ->
            val stream = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
            val bytes = stream?.use { input ->
                val buffer = ByteArray(maxBytes)
                var total = 0
                while (total < maxBytes) {
                    val read = input.read(buffer, total, maxBytes - total)
                    if (read == -1) break
                    total += read
                }
                buffer.copyOf(total)
            }
            response(conn, bytes?.toString(Charsets.UTF_8))
        }

    private fun request(
        url: String,
        method: String,
        accept: String,
        timeoutMs: Int,
        read: (HttpURLConnection) -> HttpResponse,
    ): HttpResponse? {
        if (timeoutMs <= 0) return null
        val conn = try {
            URL(url).openConnection() as? HttpURLConnection ?: return null
        } catch (_: IOException) {
            return null
        }
        return try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", accept)
            read(conn)
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun response(conn: HttpURLConnection, body: String?) = HttpResponse(
        status = conn.responseCode,
        location = conn.getHeaderField("Location"),
        contentType = conn.contentType,
        body = body,
    )
}
