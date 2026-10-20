package com.dwarpallabs.linkguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.components.PrimaryButton
import com.dwarpallabs.linkguard.ui.components.SecondaryButton
import com.dwarpallabs.linkguard.ui.components.Status
import com.dwarpallabs.linkguard.ui.components.StatusBadge
import com.dwarpallabs.linkguard.ui.components.TextButton
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme

/**
 * The one warning screen. "Go back" is primary; "Open anyway" is always present (never block).
 * "Report this link" asks once in a sheet, then sends the website's name to Dwarpal Labs;
 * [report] shows how that went.
 */
@Composable
fun InterstitialScreen(
    model: InterstitialModel,
    onGoBack: () -> Unit,
    onOpenAnyway: () -> Unit,
    report: ReportStatus = ReportStatus.IDLE,
    onReport: () -> Unit = {},
    /** The link came from the phone's SMS app: one more line under the reason (roadmap B1). */
    fromSms: Boolean = false,
    /** "Check what this app can do" (roadmap B3), only offered on an app-download warning. */
    apkCheck: ApkCheckUi = ApkCheckUi.Idle,
    onCheckApk: () -> Unit = {},
    onStopApk: () -> Unit = {},
    /** "Listen" (roadmap B4): null hides it (TalkBack on, or no on-device voice for the language). */
    speaker: WarningSpeaker? = null,
    /** "Look without opening" (roadmap B5); offered on soft warnings about a web page only. */
    onLook: (() -> Unit)? = null,
) {
    val status = LinkguardTheme.status
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBadge(if (model.strong) Status.DANGER else Status.CAUTION)
            Text(
                stringResource(if (model.strong) R.string.interstitial_title_strong else R.string.interstitial_title_soft),
                style = MaterialTheme.typography.headlineSmall,
                color = if (model.strong) status.onDangerTint else status.onCautionTint,
                modifier = Modifier.testTag("title"),
            )

            if (model.host != null) {
                // Hosts are always left-to-right, even inside an Urdu or Arabic sentence.
                Text(
                    hostText(model.host, model.domain, MaterialTheme.colorScheme.onSurfaceVariant),
                    style = MaterialTheme.typography.displaySmall.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.testTag("host"),
                )
            }

            Text(
                reasonText(model.reason),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("reason"),
            )

            (model.reason as? Reason.Reputation)?.source?.let {
                Text(
                    stringResource(R.string.confirmed_by, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("confirmed_by"),
                )
            }

            if (fromSms) {

                Text(
                    stringResource(R.string.warn_context_sms),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("context_sms"),
                )
            }

            speaker?.let { s ->
                val spoken = listOfNotNull(
                    stringResource(if (model.strong) R.string.interstitial_title_strong else R.string.interstitial_title_soft),
                    reasonText(model.reason),
                    if (fromSms) stringResource(R.string.warn_context_sms) else null,
                ).joinToString(" ")
                ListenButton(s.speaking) { if (s.speaking) s.stop() else s.speak(spoken) }
            }


            if (model.reason is Reason.Upi) UpiDetails(model.reason)

            if (model.reason == Reason.ApkDownload) ApkCheckSection(apkCheck, onCheckApk, onStopApk)

            Spacer(Modifier.height(8.dp))


            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(stringResource(R.string.interstitial_go_back), onGoBack, Modifier.testTag("go_back"))
                SecondaryButton(stringResource(R.string.interstitial_open_anyway), onOpenAnyway, Modifier.testTag("open_anyway"))
                if (onLook != null && !model.strong && model.host != null && model.reason !is Reason.Upi && model.reason != Reason.ApkDownload) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(stringResource(R.string.look_button), onLook, Modifier.testTag("look"))
                        Text(
                            stringResource(R.string.look_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                ReportBlock(report, onReport)

            }
        }
    }
}

/**
 * A text button under "Open anyway". It opens a sheet that says what is sent; only Report there
 * sends. The outcome stays inline, never a toast, which slow readers would miss. After a failure
 * the button comes back so the user can retry. The result lines are plain ink: safe/caution/danger
 * colours are reserved for what the link itself is.
 */
@Composable
internal fun ReportBlock(status: ReportStatus, onReport: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        ReportConfirmSheet(
            onReport = { confirming = false; onReport() },
            onClose = { confirming = false },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (status) {
            ReportStatus.SENT -> Text(
                stringResource(R.string.report_thanks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("report_result"),
            )
            else -> {
                if (status == ReportStatus.FAILED) {
                    Text(
                        stringResource(R.string.report_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("report_result"),
                    )
                }
                TextButton(
                    stringResource(if (status == ReportStatus.SENDING) R.string.report_sending else R.string.interstitial_report),
                    onClick = { confirming = true },

                    enabled = status != ReportStatus.SENDING,
                    modifier = Modifier.testTag("report"),
                )
                Text(
                    stringResource(R.string.interstitial_report_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Asks once before a report is sent and says exactly what leaves the phone. Report sends; Close or
 * the back gesture closes the sheet and sends nothing. Bottom-sheet radius from the design system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportConfirmSheet(onReport: () -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("report_sheet"),
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.report_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("report_sheet_title"),
            )
            Text(
                stringResource(R.string.report_confirm_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(stringResource(R.string.report_confirm_send), onReport, Modifier.testTag("report_confirm"))
                TextButton(stringResource(R.string.action_close), onClose, Modifier.testTag("report_close"))
            }
        }
    }
}

/** Shown only for short links still being resolved after 300 ms. */
@Composable
fun CheckingCard() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.interstitial_checking), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Registrable domain in bold; anything to its left (`www.`, `login.`) muted, since that is where scammers hide. */
internal fun hostText(host: String, domain: String?, muted: Color) = buildAnnotatedString {
    // No registrable domain (e.g. a raw IP address): the whole host is what the site really is.
    val start = if (domain != null && host.endsWith(domain)) host.length - domain.length else 0
    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = muted)) { append(host.substring(0, start)) }
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(host.substring(start)) }
}

@Composable
internal fun reasonText(reason: Reason): String = when (reason) {
    Reason.ApkDownload -> stringResource(R.string.warn_apk_download)
    is Reason.BrandLookalike -> stringResource(if (reason.mine) R.string.warn_brand_lookalike_mine else R.string.warn_brand_lookalike, reason.brand)

    is Reason.Upi -> when {
        reason.brand != null -> stringResource(R.string.warn_upi_brand, reason.brand)
        reason.mandate -> stringResource(R.string.warn_upi_mandate)
        else -> stringResource(R.string.warn_upi_intent)
    }
    is Reason.WeirdHost -> reason.brand?.let { stringResource(R.string.warn_weird_host_brand, it) }
        ?: stringResource(R.string.warn_weird_host)
    is Reason.Reputation -> stringResource(R.string.warn_reputation)
    is Reason.Community -> pluralStringResource(R.plurals.warn_community, reason.count, reason.count)
    is Reason.Campaign -> pluralStringResource(R.plurals.warn_campaign, reason.count, reason.count)

    is Reason.DomainAge -> pluralStringResource(R.plurals.warn_domain_age, reason.days, reason.days)
    Reason.Managed -> stringResource(R.string.warn_managed_block)
    Reason.Generic -> stringResource(R.string.warn_generic)
}

@Composable
internal fun UpiDetails(upi: Reason.Upi) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth().testTag("upi_details"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (upi.mandate) R.string.upi_asks_mandate else R.string.upi_asks_to_pay),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("upi_heading"),
            )
            upi.payeeName?.let {
                Text(stringResource(R.string.interstitial_upi_payee, it), style = MaterialTheme.typography.bodyLarge)
            }
            upi.payeeAddress?.let {
                // The UPI ID in monospace, always left to right, so look-alike characters stand out.
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        textDirection = TextDirection.Ltr,
                    ),
                    modifier = Modifier.testTag("upi_id"),
                )
            }
            upi.amount?.let {
                Text(
                    stringResource(R.string.interstitial_upi_amount, it),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StrongPreview() {
    LinkguardTheme {
        InterstitialScreen(
            InterstitialModel(true, "sbi-rewards-redeem.xyz", "sbi-rewards-redeem.xyz", Reason.BrandLookalike("SBI")),
            onGoBack = {},
            onOpenAnyway = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UpiPreview() {
    LinkguardTheme {
        InterstitialScreen(
            InterstitialModel(false, null, null, Reason.Upi("Electricity Board", "collect.now@ybl", "4999")),
            onGoBack = {},
            onOpenAnyway = {},
        )
    }
}

/**
 * "Check what this app can do": a text button and what it does; then progress with Stop; then, in
 * plain words, what the app could do on the phone. Never a verdict on its own: the warning above
 * stays, and Go back stays the main action.
 */
@Composable
internal fun ApkCheckSection(state: ApkCheckUi, onCheck: () -> Unit, onStop: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun size(bytes: Long) = android.text.format.Formatter.formatShortFileSize(context, bytes)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("apk_check")) {
        when (state) {
            ApkCheckUi.Idle, ApkCheckUi.Failed, ApkCheckUi.TooBig, ApkCheckUi.NotAnApp -> {
                val problem = when (state) {
                    ApkCheckUi.Failed -> R.string.apk_check_failed
                    ApkCheckUi.TooBig -> R.string.apk_check_too_big
                    ApkCheckUi.NotAnApp -> R.string.apk_check_not_an_app
                    else -> null
                }
                problem?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("apk_check_problem"))
                }
                if (state != ApkCheckUi.TooBig && state != ApkCheckUi.NotAnApp) {
                    TextButton(stringResource(R.string.apk_check_button), onCheck, Modifier.testTag("apk_check_button"))
                    Text(
                        stringResource(R.string.apk_check_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            is ApkCheckUi.Running -> {
                if (state.total != null && state.total > 0) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (state.done.toFloat() / state.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.apk_check_progress, size(state.done), size(state.total)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("apk_check_progress"))
                } else {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.apk_check_progress_unknown, size(state.done)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("apk_check_progress"))
                }
                TextButton(stringResource(R.string.apk_check_stop), onStop, Modifier.testTag("apk_check_stop"))
            }
            is ApkCheckUi.Done -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().testTag("apk_check_result"),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.report.brand?.let {
                        Text(stringResource(R.string.apk_check_brand, it), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("apk_check_brand"))
                    }
                    if (state.report.capabilities.isEmpty()) {
                        Text(stringResource(R.string.apk_check_nothing), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(stringResource(R.string.apk_check_result_title), style = MaterialTheme.typography.titleMedium)
                        state.report.capabilities.forEach { cap ->
                            Text("• " + stringResource(capabilityText(cap)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(stringResource(R.string.apk_check_deleted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun capabilityText(cap: com.dwarpallabs.linkguard.ApkInspector.Capability): Int = when (cap) {
    com.dwarpallabs.linkguard.ApkInspector.Capability.READ_SMS -> R.string.cap_read_sms
    com.dwarpallabs.linkguard.ApkInspector.Capability.SEND_SMS -> R.string.cap_send_sms
    com.dwarpallabs.linkguard.ApkInspector.Capability.SMS_APP -> R.string.cap_sms_app
    com.dwarpallabs.linkguard.ApkInspector.Capability.ACCESSIBILITY -> R.string.cap_accessibility
    com.dwarpallabs.linkguard.ApkInspector.Capability.NOTIFICATIONS -> R.string.cap_notifications
    com.dwarpallabs.linkguard.ApkInspector.Capability.OVERLAY -> R.string.cap_overlay
    com.dwarpallabs.linkguard.ApkInspector.Capability.INSTALL -> R.string.cap_install
    com.dwarpallabs.linkguard.ApkInspector.Capability.DEVICE_ADMIN -> R.string.cap_device_admin
    com.dwarpallabs.linkguard.ApkInspector.Capability.CONTACTS -> R.string.cap_contacts
    com.dwarpallabs.linkguard.ApkInspector.Capability.CALLS -> R.string.cap_calls
}

/** "Listen" with a speaker icon (never an icon alone); "Stop" while it is speaking. */
@Composable
internal fun ListenButton(speaking: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).testTag("listen"),
    ) {
        androidx.compose.material3.Icon(com.dwarpallabs.linkguard.ui.components.LineIcons.Speaker, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (speaking) R.string.listen_stop else R.string.listen), style = MaterialTheme.typography.labelLarge)
    }
}
