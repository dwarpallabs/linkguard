package com.dwarpallabs.linkguard

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.dwarpallabs.linkguard.core.Brand
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * "Check what this app can do" (roadmap B3), only after the user taps it on an app-download warning.
 *
 * Downloads the app file from the link into LinkGuard's cache (the one time LinkGuard contacts a
 * link's destination, and only on that tap), reads what the app would be allowed to do with
 * Android's own package parser, and deletes the file. LinkGuard never installs it.
 */
object ApkInspector {

    /** What an app could do, in the order the user should read about it. */
    enum class Capability { READ_SMS, SEND_SMS, SMS_APP, ACCESSIBILITY, NOTIFICATIONS, OVERLAY, INSTALL, DEVICE_ADMIN, CONTACTS, CALLS }

    data class Report(val label: String?, val capabilities: List<Capability>, val brand: String?, val sha256: String)

    sealed interface Result {
        data class Ok(val report: Report) : Result
        data object TooBig : Result
        data object NotAnApp : Result
        data object Failed : Result
    }

    const val MAX_BYTES = 200L * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MS = 15_000

    /**
     * Pure: what an app could do, from its requested permissions and the permissions that protect
     * its services and receivers (an accessibility service, an SMS receiver, a device admin, …).
     */
    fun capabilities(requested: Collection<String>, servicePermissions: Collection<String>, receiverPermissions: Collection<String>): List<Capability> {
        val p = "android.permission."
        val has = { name: String -> "$p$name" in requested }
        val set = linkedSetOf<Capability>()
        if (has("READ_SMS") || has("RECEIVE_SMS")) set += Capability.READ_SMS
        if (has("SEND_SMS")) set += Capability.SEND_SMS
        if ("${p}BROADCAST_SMS" in receiverPermissions || "${p}BROADCAST_WAP_PUSH" in receiverPermissions) set += Capability.SMS_APP
        if ("${p}BIND_ACCESSIBILITY_SERVICE" in servicePermissions) set += Capability.ACCESSIBILITY
        if ("${p}BIND_NOTIFICATION_LISTENER_SERVICE" in servicePermissions) set += Capability.NOTIFICATIONS
        if (has("SYSTEM_ALERT_WINDOW")) set += Capability.OVERLAY
        if (has("REQUEST_INSTALL_PACKAGES")) set += Capability.INSTALL
        if ("${p}BIND_DEVICE_ADMIN" in receiverPermissions) set += Capability.DEVICE_ADMIN
        if (has("READ_CONTACTS")) set += Capability.CONTACTS
        if (has("READ_CALL_LOG") || has("CALL_PHONE") || has("PROCESS_OUTGOING_CALLS") || has("ANSWER_PHONE_CALLS")) set += Capability.CALLS
        return Capability.entries.filter { it in set }
    }

    /**
     * Pure: the brand an app pretends to be, when its name or file name uses a brand (whole words)
     * and it wants SMS or accessibility access: the banking-trojan pattern. Otherwise null.
     */
    fun brandTrap(label: String?, fileName: String?, capabilities: List<Capability>, brands: List<Brand>): String? {
        if (Capability.READ_SMS !in capabilities && Capability.ACCESSIBILITY !in capabilities) return null
        val words = listOfNotNull(label, fileName).joinToString(" ").lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        return brands.firstOrNull { b -> b.keywords.any { it.length >= 3 && it.lowercase() in words } }?.name
    }

    /**
     * Downloads [url] into [dir] (following up to 5 redirects), hashing as it goes, and analyses it.
     * [onProgress] gets (bytes so far, total or null). Cancellation: [isCancelled] is polled while
     * reading. The file is always deleted before this returns.
     */
    fun check(
        url: String,
        dir: File,
        pm: PackageManager,
        brands: List<Brand>,
        onProgress: (Long, Long?) -> Unit,
        isCancelled: () -> Boolean,
    ): Result {
        val file = File(dir, "check-${System.nanoTime()}.apk")
        try {
            val (sha, fileName) = download(url, file, onProgress, isCancelled) ?: return Result.Failed
            if (sha == TOO_BIG) return Result.TooBig
            return analyse(file, fileName, sha, pm, brands)
        } catch (_: IOException) {

            return Result.Failed
        } finally {
            file.delete()
        }
    }

    /** Reads an app file already on the phone. [fileName] and [sha] come from the download. */
    fun analyse(file: File, fileName: String?, sha: String, pm: PackageManager, brands: List<Brand>): Result {
        if (!isZip(file)) return Result.NotAnApp
        val info = archiveInfo(pm, file) ?: return Result.NotAnApp
        val caps = capabilities(
            requested = info.requestedPermissions?.toList().orEmpty(),
            servicePermissions = info.services?.mapNotNull { it.permission }.orEmpty(),
            receiverPermissions = info.receivers?.mapNotNull { it.permission }.orEmpty(),
        )
        val label = info.applicationInfo?.let { app ->
            app.sourceDir = file.path
            app.publicSourceDir = file.path
            runCatching { app.loadLabel(pm).toString() }.getOrNull()
        }
        return Result.Ok(Report(label, caps, brandTrap(label, fileName, caps, brands), sha))
    }

    private const val TOO_BIG = "too-big"


    /** Returns (sha256 hex, file name from the last URL) or null; ("too-big", _) when over the limit. */
    private fun download(start: String, file: File, onProgress: (Long, Long?) -> Unit, isCancelled: () -> Boolean): Pair<String, String?>? {
        var current = URL(start)
        repeat(MAX_REDIRECTS + 1) {
            val conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: return null
                    current = URL(current, next)
                    if (current.protocol != "https" && current.protocol != "http") return null
                    return@repeat
                }
                if (code !in 200..299) return null
                val total = conn.contentLengthLong.takeIf { it > 0 }
                if (total != null && total > MAX_BYTES) return TOO_BIG to null
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (isCancelled()) return null
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > MAX_BYTES) return TOO_BIG to null
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                            onProgress(done, total)
                        }
                    }
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                return sha to current.path.substringAfterLast('/').takeIf { it.isNotBlank() }
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun isZip(file: File): Boolean = file.length() > 4 && file.inputStream().use { s ->
        val b = ByteArray(4)
        s.read(b) == 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2] == 3.toByte() && b[3] == 4.toByte()
    }

    private fun archiveInfo(pm: PackageManager, file: File): PackageInfo? {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.path, flags)
        }
    }
}
