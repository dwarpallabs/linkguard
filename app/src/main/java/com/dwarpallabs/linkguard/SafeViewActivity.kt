package com.dwarpallabs.linkguard

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.dwarpallabs.linkguard.ui.components.Banner
import com.dwarpallabs.linkguard.ui.components.TextButton
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/**
 * "Look without opening" (roadmap B5): the only place LinkGuard ever shows a page, and only when the
 * user taps it on a soft "Be careful" warning.
 *
 * Runs in its own process (`:safeview`) with its own WebView data folder, so it shares nothing with
 * the rest of LinkGuard. The page gets no JavaScript, no cookies, no storage, no file access, no
 * autofill, no downloads, no new windows and no POST requests (nothing typed can be sent), and it
 * cannot navigate anywhere else. WebView Safe Browsing is off (it would send URLs to Google). The
 * view closes itself after [LIFETIME_MS]; cache and storage are wiped when it does.
 */
class SafeViewActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var blocked by mutableStateOf(false)
    private var failed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Unencrypted pages are not allowed in LinkGuard (Android's default), so ask for the HTTPS one.
        val start = intent.getStringExtra(EXTRA_URL)?.takeIf(::isWebUrl)?.let(::preferHttps) ?: return finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { WebView.setDataDirectorySuffix(DATA_SUFFIX) }
        }
        CookieManager.getInstance().setAcceptCookie(false)
        setContent {
            LinkguardTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Banner(stringResource(R.string.look_banner), Modifier.testTag("look_banner"))
                            if (blocked) Text(stringResource(R.string.look_blocked), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp).testTag("look_blocked"))
                            if (failed) Text(stringResource(R.string.look_failed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp).testTag("look_failed"))
                            TextButton(stringResource(R.string.action_close), ::returnToWarning, Modifier.testTag("look_close"))
                        }
                        AndroidView(
                            factory = { context -> lockedWebView(context, start).also { webView = it } },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this) { returnToWarning() }
        lifecycleScope.launch {
            delay(LIFETIME_MS)
            returnToWarning()
        }
    }

    /**
     * Back to the warning for the same link (Close, Back, or the one-minute limit). The warning screen
     * does not stay in the background (it must never linger in recents), so it is opened again; the
     * re-check is fast because the main process still holds its in-memory caches.
     */
    private fun returnToWarning() {
        if (isFinishing) return
        intent.getStringExtra(EXTRA_WARNED_LINK)?.takeIf(::isWebUrl)?.let { link ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setClass(this, LinkInterceptActivity::class.java))
        }
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun lockedWebView(context: Context, start: String): WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = false
            @Suppress("DEPRECATION")
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            safeBrowsingEnabled = false
        }
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        setDownloadListener { _, _, _, _, _ -> blocked = true }
        webViewClient = object : WebViewClient() {
            /** The page may follow its own redirects while loading; anything the user taps is stopped. */
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.toString() == "about:blank") return false
                if (request.isForMainFrame && request.isRedirect && isWebUrl(request.url.toString())) return false
                blocked = true
                return true
            }

            /** Nothing typed can leave: only GET requests are allowed, forms and beacons get an empty answer. */
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (request.method.equals("GET", ignoreCase = true)) null else empty()

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                failed = true
                // Our own message above replaces WebView's error page.
                view.loadUrl("about:blank")
            }
        }
        loadUrl(start)
    }

    private fun empty() = WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            destroy()
        }
        webView = null
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "com.dwarpallabs.linkguard.extra.SAFE_VIEW_URL"
        const val EXTRA_WARNED_LINK = "com.dwarpallabs.linkguard.extra.WARNED_LINK"
        const val LIFETIME_MS = 60_000L
        private const val DATA_SUFFIX = "safeview"

        fun isWebUrl(url: String): Boolean = Uri.parse(url).scheme?.lowercase().let { it == "https" || it == "http" }

        /** "http://x/y" → "https://x/y"; anything else unchanged. */
        fun preferHttps(url: String): String = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url


        fun intent(context: Context, url: String, warnedLink: String): Intent =
            Intent(context, SafeViewActivity::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_WARNED_LINK, warnedLink)
    }
}
