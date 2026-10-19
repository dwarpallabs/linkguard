package com.dwarpallabs.linkguard

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.dwarpallabs.linkguard.browser.BrowserApp
import com.dwarpallabs.linkguard.browser.Browsers
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.ui.AboutScreen
import com.dwarpallabs.linkguard.ui.FeedbackScreen
import com.dwarpallabs.linkguard.ui.HomeScreen
import com.dwarpallabs.linkguard.ui.HowItWorksScreen
import com.dwarpallabs.linkguard.ui.ReportScreen
import com.dwarpallabs.linkguard.ui.PrivacyPolicyUrl
import com.dwarpallabs.linkguard.ui.ReportStatus
import com.dwarpallabs.linkguard.ui.ScamCheckCard

import com.dwarpallabs.linkguard.ui.SourceCodeUrl
import com.dwarpallabs.linkguard.ui.SupportEmail
import com.dwarpallabs.linkguard.ui.TranslationIssueUrl
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import kotlinx.coroutines.launch

/**
 * First-launch setup and Settings: the "Was that link a scam?" card, default browser, forward-to
 * browser, check toggles, how it works, reporting, translation feedback, about.
 */

class MainActivity : ComponentActivity() {

    private enum class Screen { HOME, HOW, REPORT, FEEDBACK, ABOUT }

    private var isDefault by mutableStateOf(false)
    private var browsers by mutableStateOf(emptyList<BrowserApp>())
    private var selected by mutableStateOf<String?>(null)
    private var disabledChecks by mutableStateOf(emptySet<CheckId>())
    private var reportStatus by mutableStateOf(ReportStatus.IDLE)

    /** The "Was that link a scam?" question to ask, if the user tapped Open anyway on a warning. */
    private var question by mutableStateOf<PendingQuestion?>(null)
    private var questionStatus by mutableStateOf(ReportStatus.IDLE)

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
                        onOpenHowItWorks = { screen = Screen.HOW },
                        onOpenReport = { screen = Screen.REPORT },
                        onOpenFeedback = { screen = Screen.FEEDBACK },
                        onOpenAbout = { screen = Screen.ABOUT },
                        topCard = {
                            question?.let { q ->
                                ScamCheckCard(
                                    domain = q.domain,
                                    openedAt = q.openedAt,
                                    status = questionStatus,
                                    onScam = { reportQuestion(q) },
                                    onFine = { answerQuestion(q) },
                                    onNotSure = { answerQuestion(q) },
                                    onClose = { question = null; questionStatus = ReportStatus.IDLE },
                                )
                            }
                        },
                    )
                    Screen.ABOUT -> AboutScreen(
                        version = appVersion(),
                        onPrivacy = { Browsers.forward(this, PrivacyPolicyUrl.toUri()) },
                        onSource = { Browsers.forward(this, SourceCodeUrl.toUri()) },
                        onContact = { email("LinkGuard ${appVersion()}") },
                        onBack = { screen = Screen.HOME },
                    )
                    Screen.FEEDBACK -> FeedbackScreen(
                        onEmail = { email("LinkGuard translation (${resources.configuration.locales[0].displayName})") },
                        onGitHub = { Browsers.forward(this, TranslationIssueUrl.toUri()) },
                        onBack = { screen = Screen.HOME },
                    )
                    Screen.HOW -> HowItWorksScreen(
                        browserName = browsers.firstOrNull { it.packageName == selected }?.label
                            ?: stringResource(R.string.your_browser),
                        onBack = { screen = Screen.HOME },
                    )
                    Screen.REPORT -> ReportScreen(
                        status = reportStatus,
                        onBack = { reportStatus = ReportStatus.IDLE; screen = Screen.HOME },
                        onSubmit = ::sendReport,
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
        // Keep a card that is sending or showing its thank-you; otherwise ask what is pending now.
        if (questionStatus == ReportStatus.IDLE || questionStatus == ReportStatus.FAILED) {
            question = AfterOpenStore.of(this).pending()
            if (question == null) questionStatus = ReportStatus.IDLE
        }
    }

    /** "Yes, it was a scam": sends the website and the checks that fired, never a link. */
    private fun reportQuestion(q: PendingQuestion) {
        if (questionStatus == ReportStatus.SENDING) return
        questionStatus = ReportStatus.SENDING
        lifecycleScope.launch {
            val ok = Reporter(application as LinkGuardApp).send(
                Reporter.websiteLink(q.domain),
                InterceptViewModel.reportNote(q.reasons),
            )
            if (ok) AfterOpenStore.of(this@MainActivity).answer(q)
            questionStatus = if (ok) ReportStatus.SENT else ReportStatus.FAILED
        }
    }

    /** "No, it was fine" or "Not sure": never ask about this website again. Nothing is sent. */
    private fun answerQuestion(q: PendingQuestion) {
        AfterOpenStore.of(this).answer(q)
        question = null
        questionStatus = ReportStatus.IDLE
    }


    /** Sends the report only now, because the user tapped Send report. */
    private fun sendReport(link: String, note: String) {
        reportStatus = ReportStatus.SENDING
        lifecycleScope.launch {
            val ok = Reporter(application as LinkGuardApp).send(link, note)
            reportStatus = if (ok) ReportStatus.SENT else ReportStatus.FAILED
        }
    }

    /** Opens the user's email app with the address and subject filled in; the user writes and sends it. */
    private fun email(subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportEmail))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Browsers.forward(this, SourceCodeUrl.toUri())
        }
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
