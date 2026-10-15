package com.dwarpallabs.linkguard

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.dwarpallabs.linkguard.browser.Browsers

/**
 * Receives every http/https link once LinkGuard is the default browser.
 *
 * Milestone 1: forwards straight to the chosen browser and finishes, with a translucent theme and
 * no animations so the user never sees it. Milestone 3 adds checks and the interstitial here.
 *
 * Never log, store or transmit the URL.
 */
class LinkInterceptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme?.lowercase() in WEB_SCHEMES) {
            Browsers.forward(this, uri)
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
