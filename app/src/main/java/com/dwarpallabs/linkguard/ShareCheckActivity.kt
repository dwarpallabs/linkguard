package com.dwarpallabs.linkguard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dwarpallabs.linkguard.ShareCheckViewModel.State
import com.dwarpallabs.linkguard.core.PublicSuffixList
import com.dwarpallabs.linkguard.core.Verdict
import com.dwarpallabs.linkguard.ui.InterstitialModel
import com.dwarpallabs.linkguard.ui.ShareCheckScreen
import com.dwarpallabs.linkguard.ui.ShareCheckUi
import com.dwarpallabs.linkguard.ui.ShareResult
import com.dwarpallabs.linkguard.ui.components.Status
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme

/**
 * The "Check with LinkGuard" share target: receives text shared from WhatsApp, SMS, Telegram or
 * any app, and shows a verdict for every link in it.
 *
 * Separate from the link-intercept flow: this is an ACTION_SEND filter on its own activity and
 * does not touch the VIEW intent filter or the default-browser role. The shared text stays in
 * memory and is never logged, stored or sent; only its links are checked, as if tapped.
 */
class ShareCheckActivity : ComponentActivity() {

    private val viewModel: ShareCheckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.check(sharedText(intent))
        val app = application as LinkGuardApp
        setContent {
            val state by viewModel.state.collectAsState()
            val reports by viewModel.reports.collectAsState()
            LinkguardTheme {
                ShareCheckScreen(
                    ui = when (val s = state) {
                        State.Checking -> ShareCheckUi.Checking
                        State.NoLinks -> ShareCheckUi.NoLinks
                        is State.Results -> ShareCheckUi.Results(s.items.map { toResult(it.link, it.verdict, app.data.psl) }, s.more)
                    },
                    reports = reports,
                    onCopy = ::copy,
                    onReport = viewModel::report,
                    onClose = ::finish,
                )
            }
        }
    }

    /** Shared again while this screen is open: Android hands the new message here, so check that one. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.check(sharedText(intent))
    }

    /** Copies a website name the user asked for. Never a link. */

    private fun copy(name: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.share_copy), name))
    }

    companion object {
        /** The shared text: the message, plus its subject if the app sent one. Empty if there is none. */
        fun sharedText(intent: Intent?): String {
            if (intent?.action != Intent.ACTION_SEND) return ""
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
            val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
            return listOfNotNull(subject, text).joinToString("\n")
        }

        /** How one checked link is shown: its band, the warning screen's model and what can be copied or reported. */
        fun toResult(link: String, verdict: Verdict, psl: PublicSuffixList): ShareResult {
            val status = when (verdict.level) {
                Verdict.Level.STRONG -> Status.DANGER
                Verdict.Level.SOFT -> Status.CAUTION
                Verdict.Level.SILENT -> Status.SAFE
            }
            val model = InterstitialModel.from(verdict)
            return ShareResult(
                status = status,
                model = model,
                copyName = model.domain ?: model.host,
                canReport = InterceptViewModel.reportWebsite(link, verdict, psl) != null && verdict.finalUrl?.scheme != "upi",
            )
        }
    }
}
