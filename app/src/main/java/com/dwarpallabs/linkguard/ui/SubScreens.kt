package com.dwarpallabs.linkguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
            TextButton(onClick = onBack, modifier = Modifier.testTag("back")) { Text("‹ " + stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            content(Modifier.padding(top = 12.dp))
        }
    }
}

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
