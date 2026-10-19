package com.dwarpallabs.linkguard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.dwarpallabs.linkguard.InterceptViewModel.State
import com.dwarpallabs.linkguard.browser.Browsers
import com.dwarpallabs.linkguard.browser.cameBySms
import com.dwarpallabs.linkguard.browser.isCustomTabLaunch
import com.dwarpallabs.linkguard.browser.sourcePackage


import com.dwarpallabs.linkguard.core.Verdict
import com.dwarpallabs.linkguard.ui.CheckingCard
import com.dwarpallabs.linkguard.ui.InterstitialModel
import com.dwarpallabs.linkguard.ui.InterstitialScreen
import com.dwarpallabs.linkguard.ui.rememberWarningSpeaker

import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import kotlinx.coroutines.launch

/**
 * Receives every http/https link once LinkGuard is the default browser.
 *
 * Runs the checks within the budget, then either forwards silently (nothing is ever drawn for a
 * safe link) or shows the interstitial. Translucent and animation-free so silent forwards are
 * invisible.
 *
 * Never log, store or transmit the URL.
 */
class LinkInterceptActivity : ComponentActivity() {

    private val viewModel: InterceptViewModel by viewModels()

    /** Compose is only started once there is something to show, so silent forwards skip its startup cost. */
    private var composed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge from the first frame on every Android version (it is forced from Android 15):
        // transparent system bars on the invisible translucent window, and the warning screen pads
        // for the bars itself. This replaces the theme's deprecated statusBarColor/navigationBarColor.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!start(intent)) return


        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is State.Forward -> {
                        Browsers.forward(this@LinkInterceptActivity, state.url.toUri(), isCustomTabLaunch(intent))
                        finishWithoutAnimation()
                    }
                    State.CheckingSlow, is State.Warn -> showUi()
                    State.Checking -> Unit
                }
            }
        }
    }

    private fun showUi() {
        if (composed) return
        composed = true
        setContent {

            val state by viewModel.state.collectAsState()
            when (val s = state) {
                State.Checking, is State.Forward -> Unit
                State.CheckingSlow -> LinkguardTheme { CheckingCard() }
                is State.Warn -> LinkguardTheme {
                    val report by viewModel.reportStatus.collectAsState()
                    InterstitialScreen(
                        model = InterstitialModel.from(s.verdict),
                        onGoBack = ::finishWithoutAnimation,
                        onOpenAnyway = { openAnyway(s.verdict) },
                        report = report,
                        onReport = { viewModel.report(s.verdict) },
                        fromSms = cameBySms(this, sourcePackage(intent, referrer)),
                        apkCheck = viewModel.apkCheck.collectAsState().value,
                        onCheckApk = { viewModel.checkApk(s.verdict) },
                        onStopApk = viewModel::stopApkCheck,
                        speaker = rememberWarningSpeaker(),

                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        start(intent)
    }

    /** Starts checking the intent's link; finishes and returns false if there is nothing to check. */
    private fun start(intent: Intent?): Boolean {
        val uri = intent?.data
        if (uri == null || uri.scheme?.lowercase() !in WEB_SCHEMES) {
            finishWithoutAnimation()
            return false
        }
        viewModel.check(uri.toString())
        return true
    }

    private fun openAnyway(verdict: Verdict) {
        // The website's name only, so the next app open can ask whether it was a scam.
        viewModel.rememberOpenedAnyway(verdict)
        val url = verdict.finalUrl

        if (url != null && url.isUpi) {
            // A web link that redirected to a UPI request: hand it to the user's UPI app.
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUrlString().toUri()))
            } catch (_: ActivityNotFoundException) {
                // No UPI app installed; nothing to open.
            }
        } else {
            val raw = intent?.data?.toString() ?: return finishWithoutAnimation()
            Browsers.forward(this, InterceptViewModel.forwardUrl(raw, verdict).toUri(), isCustomTabLaunch(intent))
        }
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        val WEB_SCHEMES = setOf("http", "https")
    }
}
