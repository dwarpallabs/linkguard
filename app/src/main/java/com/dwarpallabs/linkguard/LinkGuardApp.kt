package com.dwarpallabs.linkguard

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.HashedAllowlist
import com.dwarpallabs.linkguard.core.HttpReputationLookup
import com.dwarpallabs.linkguard.core.LinkEngine
import com.dwarpallabs.linkguard.core.MemoryCachedReputation
import com.dwarpallabs.linkguard.core.RdapClient
import com.dwarpallabs.linkguard.core.UrlConnectionHttp
import com.dwarpallabs.linkguard.core.checks.MemoryCachedRegistrationDates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Starts loading the bundled lists as soon as the process starts, so the first intercepted link
 * does not pay for parsing them on its own budget. Also loads the last verified allowlist download
 * and refreshes it in the background at most once a day.
 */
class LinkGuardApp : Application() {

    private lateinit var dataFuture: Future<EngineData>

    /** Downloaded, signature-checked allowlist hashes. Updated in place; the engine sees changes at once. */
    val allowlist = HashedAllowlist()

    val http = UrlConnectionHttp()

    /**
     * Completes once the start-up allowlist refresh has finished, whether or not it got a bundle.
     * On a fresh install a warned link waits briefly for this, so a popular site is not warned
     * about just because the allowlist has not arrived yet.
     */
    val allowlistWarmUp = CompletableFuture<Unit>()

    /** True once a signature-checked allowlist (cached or downloaded) is in memory. */
    fun hasAllowlist(): Boolean = allowlist.version != null

    /** Outlives any screen, so a report the user sent keeps going after they tap Go back. */
    val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What LinkGuard learned on this phone: trusted websites and the user's brands. Never sent. */
    val memory: LocalMemory by lazy { LocalMemory.of(this) }

    @Volatile
    private var myBrandsCache: Set<String>? = null

    /** The user's brands, cached in memory so a link check never recomputes them. Off the main thread. */
    fun myBrands(): Set<String> =
        myBrandsCache ?: memory.myBrands(data.brands.map { it.name }).also { myBrandsCache = it }

    /** Call after the user's brands changed (one more learned, or cleared in Settings). */
    fun forgetMyBrandsCache() {
        myBrandsCache = null
    }


    override fun onCreate() {
        super.onCreate()
        val worker = Executors.newSingleThreadExecutor { Thread(it, "engine-data") }
        dataFuture = worker.submit(Callable {
            val start = SystemClock.elapsedRealtime()
            val updater = AllowlistUpdater(this, http, allowlist)
            updater.loadCached()
            EngineData.loadBundled(downloadedAllowlist = allowlist).also {
                // Timing only; never log anything about links.
                if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.i(TAG, "engine data loaded in ${SystemClock.elapsedRealtime() - start} ms, allowlist ${allowlist.size}")
                }
            }
        })
        worker.shutdown()
        // In parallel with loading: open the TLS connection to the LinkGuard server, so the first
        // link's lookup can reuse it (a lookup for a fixed prefix, carrying nothing about the user
        // or any link), then refresh the allowlist if it is a day old.
        Thread({
            try {
                http.get("${ServerApi.BASE_URL}/v1/lookup?p=00000000", "text/plain", WARM_UP_TIMEOUT_MS)
                AllowlistUpdater(this, http, allowlist).refreshIfStale()
            } finally {
                allowlistWarmUp.complete(Unit)
            }
        }, "server-warm-up").start()
    }

    /** Bundled lists. Blocks until loading finishes: call off the main thread. */
    val data: EngineData get() = dataFuture.get()

    /** Full engine: shortener redirects, server blocklist by hash prefix and RDAP, within the caller's budget. */
    val engine: LinkEngine by lazy {
        LinkEngine(
            data = data,
            http = http,
            registrationDates = MemoryCachedRegistrationDates(RdapClient(http, data.rdapServers)),
            reputation = MemoryCachedReputation(HttpReputationLookup(http, ServerApi.BASE_URL)),
        )
    }

    /** Offline engine: local checks only, used as the fallback when the network is slow. */
    val localEngine: LinkEngine by lazy { LinkEngine(data, http = null, registrationDates = null) }

    private companion object {
        const val TAG = "LinkGuard"
        const val WARM_UP_TIMEOUT_MS = 5_000
    }
}
