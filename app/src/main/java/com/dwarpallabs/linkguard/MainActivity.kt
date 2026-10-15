package com.dwarpallabs.linkguard

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dwarpallabs.linkguard.browser.BrowserApp
import com.dwarpallabs.linkguard.browser.Browsers
import com.dwarpallabs.linkguard.ui.OnboardingScreen
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme

/** First-launch screen: become the default browser and pick where links are forwarded. */
class MainActivity : ComponentActivity() {

    private var isDefault by mutableStateOf(false)
    private var browsers by mutableStateOf(emptyList<BrowserApp>())
    private var selected by mutableStateOf<String?>(null)

    // After one refusal Android may silently suppress the role dialog, so switch to Settings.
    private var roleDeclined = false

    private val requestRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refresh()
            if (!isDefault) roleDeclined = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkguardTheme {
                OnboardingScreen(
                    isDefault = isDefault,
                    browsers = browsers,
                    selected = selected,
                    onSetDefault = ::requestDefaultBrowser,
                    onSelectBrowser = { packageName ->
                        Prefs(this).forwardTo = packageName
                        selected = packageName
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        isDefault = Browsers.isDefaultBrowser(this)
        browsers = Browsers.installed(this)
        selected = Browsers.target(this)
    }

    private fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !roleDeclined) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                requestRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                return
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
