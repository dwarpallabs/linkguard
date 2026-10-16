package com.dwarpallabs.linkguard.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(
    val status: Int,
    val location: String? = null,
    val contentType: String? = null,
    val body: String? = null,
    /** Response headers, lowercase names. */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Minimal blocking HTTP used by Unwrap, DomainAge, reputation lookups, the allowlist download and
 * reports. Returns null on any network failure or timeout.
 * Implementations must not follow redirects, send cookies or send a Referer.
 */
interface Http {
    fun head(url: String, timeoutMs: Int): HttpResponse?

    fun get(
        url: String,
        accept: String,
        timeoutMs: Int,
        maxBytes: Int = 256 * 1024,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse?

    fun post(url: String, contentType: String, body: String, timeoutMs: Int): HttpResponse? = null
}

/** [Http] on `HttpURLConnection`: available on both the JVM and Android, no dependencies. */
class UrlConnectionHttp(private val userAgent: String = "Mozilla/5.0 (Linux; Android) LinkGuard") : Http {

    override fun head(url: String, timeoutMs: Int): HttpResponse? =
        request(url, "HEAD", "*/*", timeoutMs) { conn -> response(conn, body = null) }

    override fun get(
        url: String,
        accept: String,
        timeoutMs: Int,
        maxBytes: Int,
        headers: Map<String, String>,
    ): HttpResponse? =
        request(url, "GET", accept, timeoutMs, headers) { conn -> response(conn, readBody(conn, maxBytes)) }

    override fun post(url: String, contentType: String, body: String, timeoutMs: Int): HttpResponse? =
        request(url, "POST", "*/*", timeoutMs) { conn ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", contentType)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            response(conn, readBody(conn, 4 * 1024))
        }

    private fun request(
        url: String,
        method: String,
        accept: String,
        timeoutMs: Int,
        headers: Map<String, String> = emptyMap(),
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
            headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
            read(conn)
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection, maxBytes: Int): String? {
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
        return bytes?.toString(Charsets.UTF_8)
    }

    private fun response(conn: HttpURLConnection, body: String?) = HttpResponse(
        status = conn.responseCode,
        location = conn.getHeaderField("Location"),
        contentType = conn.contentType,
        body = body,
        headers = conn.headerFields
            .filterKeys { it != null }
            .mapKeys { it.key.lowercase() }
            .mapValues { it.value.firstOrNull().orEmpty() },
    )
}
