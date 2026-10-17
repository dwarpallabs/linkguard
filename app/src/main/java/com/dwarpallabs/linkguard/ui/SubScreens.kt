package com.dwarpallabs.linkguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.R

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("back")) { Text((if (isRtl()) "› " else "‹ ") + stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            content(Modifier.padding(top = 12.dp))
        }
    }
}

/** Sections of the "How LinkGuard works" screen: icon, title, body. */
private val HowSections = listOf(
    Triple("⚠️", R.string.how_why_title, R.string.how_why_body),
    Triple("🛡", R.string.how_where_title, R.string.how_where_body),
    Triple("✅", R.string.how_safe_title, R.string.how_safe_body),
    Triple("🔍", R.string.how_scam_title, R.string.how_scam_body),
    Triple("👥", R.string.how_community_title, R.string.how_community_body),
    Triple("👍", R.string.how_choice_title, R.string.how_choice_body),
)

/**
 * Plain-language overview of what LinkGuard does. Deliberately high level: it doesn't list brands,
 * sources or thresholds, which would mislead users or help scammers.
 */
@Composable
fun HowItWorksScreen(browserName: String, onBack: () -> Unit) {
    SubScreen(stringResource(R.string.how_title), onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).testTag("how_it_works"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HowSections.forEach { (icon, title, body) ->
                Row(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(icon, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(body, browserName), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/**
 * Where to send translation corrections. Every language except English is machine-translated, so
 * the community fixes mistakes. Nothing is sent from here: each row hands off to email, the
 * browser or the Play Store.
 */
@Composable
fun FeedbackScreen(onEmail: () -> Unit, onGitHub: () -> Unit, onPlay: (() -> Unit)?, onBack: () -> Unit) {
    SubScreen(stringResource(R.string.feedback_title), onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).testTag("feedback"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.feedback_intro), style = MaterialTheme.typography.bodyLarge)
            LinkRow(stringResource(R.string.feedback_email), SupportEmail, onEmail, "feedback_email")
            LinkRow(stringResource(R.string.feedback_github), stringResource(R.string.feedback_github_desc), onGitHub, "feedback_github")
            if (onPlay != null) {
                LinkRow(stringResource(R.string.feedback_play), "Google Play", onPlay, "feedback_play")
            }
        }
    }
}

/** Version, who makes LinkGuard, privacy policy, source code, contact and data credits. */
@Composable
fun AboutScreen(version: String, onPrivacy: () -> Unit, onSource: () -> Unit, onContact: () -> Unit, onBack: () -> Unit) {
    SubScreen(stringResource(R.string.about_title), onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).testTag("about"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("about_version"),
            )
            Text(stringResource(R.string.about_by), style = MaterialTheme.typography.bodyMedium)
            LinkRow(stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_desc), onPrivacy, "about_privacy")
            LinkRow(stringResource(R.string.about_source), stringResource(R.string.about_source_desc), onSource, "about_source")
            LinkRow(stringResource(R.string.about_contact), SupportEmail, onContact, "about_contact")
            SectionHeading(stringResource(R.string.about_credits))
            Text(
                stringResource(R.string.about_credits_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

const val SupportEmail = "support@dwarpallabs.com"
const val PrivacyPolicyUrl = "https://dwarpallabs.com/linkguard/privacy"
const val SourceCodeUrl = "https://github.com/dwarpallabs/linkguard"
const val TranslationIssueUrl = "https://github.com/dwarpallabs/linkguard/issues/new?template=translation.yml"

/** Progress of a report submission. */
enum class ReportStatus { IDLE, SENDING, SENT, FAILED }

/**
 * Lets the user report a link to Dwarpal Labs' private review queue. Nothing is sent until the
 * user taps Submit; the report is never published.
 */
@Composable
fun ReportScreen(status: ReportStatus, onBack: () -> Unit, onSubmit: (link: String, note: String) -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(status) {
        if (status == ReportStatus.SENT) { link = ""; note = "" }
    }
    SubScreen(stringResource(R.string.settings_report), onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    stringResource(R.string.report_private_info),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(stringResource(R.string.report_link_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("report_link"),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.report_note_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("report_note"),
            )
            Button(
                onClick = { onSubmit(link.trim(), note.trim()) },
                enabled = link.isNotBlank() && status != ReportStatus.SENDING,
                modifier = Modifier.fillMaxWidth().testTag("report_submit"),
            ) {
                Text(stringResource(if (status == ReportStatus.SENDING) R.string.report_sending else R.string.report_submit))
            }
            when (status) {
                ReportStatus.SENT -> Text(
                    stringResource(R.string.report_sent),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("report_result"),
                )
                ReportStatus.FAILED -> Text(
                    stringResource(R.string.report_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("report_result"),
                )
                else -> Unit
            }
        }
    }
}
