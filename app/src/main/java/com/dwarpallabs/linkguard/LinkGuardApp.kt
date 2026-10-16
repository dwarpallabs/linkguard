package com.dwarpallabs.linkguard

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.LinkEngine
import com.dwarpallabs.linkguard.core.RdapClient
import com.dwarpallabs.linkguard.core.UrlConnectionHttp
import com.dwarpallabs.linkguard.core.checks.MemoryCachedRegistrationDates
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Starts loading the bundled lists as soon as the process starts, so the first intercepted link
 * does not pay for parsing them on its own budget.
 */
class LinkGuardApp : Application() {

    private lateinit var dataFuture: Future<EngineData>

    override fun onCreate() {
        super.onCreate()
        val loader = Executors.newSingleThreadExecutor { Thread(it, "engine-data") }
        dataFuture = loader.submit(Callable {
            val start = SystemClock.elapsedRealtime()
            EngineData.loadBundled().also {
                // Timing only; never log anything about links.
                if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.i(TAG, "engine data loaded in ${SystemClock.elapsedRealtime() - start} ms")
                }
            }
        })
        loader.shutdown()
    }

    /** Bundled lists. Blocks until loading finishes: call off the main thread. */
    val data: EngineData get() = dataFuture.get()

    /** Full engine: follows shortener redirects and asks RDAP registries, within the caller's budget. */
    val engine: LinkEngine by lazy {
        val http = UrlConnectionHttp()
        LinkEngine(data, http, MemoryCachedRegistrationDates(RdapClient(http, data.rdapServers)))
    }

    /** Offline engine: local checks only, used as the fallback when the network is slow. */
    val localEngine: LinkEngine by lazy { LinkEngine(data, http = null, registrationDates = null) }

    private companion object {
        const val TAG = "LinkGuard"
    }
}
