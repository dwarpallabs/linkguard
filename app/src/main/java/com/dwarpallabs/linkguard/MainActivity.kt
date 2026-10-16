package com.dwarpallabs.linkguard

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.dwarpallabs.linkguard.browser.BrowserApp
import com.dwarpallabs.linkguard.browser.Browsers
import com.dwarpallabs.linkguard.core.Brand
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.ui.BrandsScreen
import com.dwarpallabs.linkguard.ui.HomeScreen
import com.dwarpallabs.linkguard.ui.ReportScreen
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** First-launch setup and Settings: default browser, forward-to browser, check toggles, brands, reporting. */
class MainActivity : ComponentActivity() {

    private enum class Screen { HOME, BRANDS, REPORT }

    private var isDefault by mutableStateOf(false)
    private var browsers by mutableStateOf(emptyList<BrowserApp>())
    private var selected by mutableStateOf<String?>(null)
    private var disabledChecks by mutableStateOf(emptySet<CheckId>())

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
        val prefs = Prefs(this)
        setContent {
            LinkguardTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        isDefault = isDefault,
                        browsers = browsers,
                        selected = selected,
                        disabledChecks = disabledChecks,
                        onSetDefault = ::requestDefaultBrowser,
                        onSelectBrowser = { packageName ->
                            prefs.forwardTo = packageName
                            selected = packageName
                        },
                        onToggleCheck = { check, enabled ->
                            prefs.setCheckEnabled(check, enabled)
                            disabledChecks = prefs.disabledChecks
                        },
                        onOpenBrands = { screen = Screen.BRANDS },
                        onOpenReport = { screen = Screen.REPORT },
                    )
                    Screen.BRANDS -> {
                        val brands by produceState(emptyList<Brand>()) {
                            value = withContext(Dispatchers.IO) { (application as LinkGuardApp).data.brands }
                        }
                        BrandsScreen(brands, onBack = { screen = Screen.HOME })
                    }
                    Screen.REPORT -> ReportScreen(
                        onBack = { screen = Screen.HOME },
                        onSubmit = { link, note ->
                            Browsers.forward(this@MainActivity, ReportLink.issueUrl(link, note, appVersion()).toUri())
                            screen = Screen.HOME
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val prefs = Prefs(this)
        isDefault = Browsers.isDefaultBrowser(this)
        browsers = Browsers.installed(this)
        selected = Browsers.target(this)
        disabledChecks = prefs.disabledChecks
    }

    private fun appVersion(): String =
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"

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
