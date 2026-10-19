package com.dwarpallabs.linkguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.components.PrimaryButton
import com.dwarpallabs.linkguard.ui.components.Status
import com.dwarpallabs.linkguard.ui.components.StatusBadge
import com.dwarpallabs.linkguard.ui.components.TextButton
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme

/** One checked link, as the results screen shows it. */
data class ShareResult(
    val status: Status,
    /** Host, website and reason, as on the warning screen. The reason is not shown for [Status.SAFE]. */
    val model: InterstitialModel,
    /** The website name the copy button copies; null when there is none (a UPI request). */
    val copyName: String?,
    /** False when there is no website to report (a UPI request on its own). */
    val canReport: Boolean,
)

sealed interface ShareCheckUi {
    data object Checking : ShareCheckUi
    data object NoLinks : ShareCheckUi
    data class Results(val results: List<ShareResult>, val more: Boolean) : ShareCheckUi
}

/**
 * "Check with LinkGuard": the verdict for every link in a shared message. A verdict, not a gate,
 * so there is no Open button: the user goes back to the message to open a link. One card per link,
 * built from the warning screen's parts; Close is the one main action.
 */
@Composable
fun ShareCheckScreen(
    ui: ShareCheckUi,
    reports: Map<Int, ReportStatus>,
    onCopy: (String) -> Unit,
    onReport: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (ui) {
            ShareCheckUi.Checking -> Box(Modifier.fillMaxSize().testTag("share_checking"), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.share_checking), style = MaterialTheme.typography.bodyLarge)
                }
            }
            ShareCheckUi.NoLinks -> Page {
                Text(
                    stringResource(R.string.share_no_links),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("share_no_links"),
                )
                PrimaryButton(stringResource(R.string.action_close), onClose, Modifier.testTag("share_close"))
            }
            is ShareCheckUi.Results -> Page {
                Text(
                    pluralStringResource(R.plurals.share_title, ui.results.size, ui.results.size),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("share_title"),
                )
                Text(
                    stringResource(R.string.share_private),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ui.results.forEachIndexed { index, result ->
                    ResultCard(index, result, reports[index] ?: ReportStatus.IDLE, onCopy, { onReport(index) })
                }
                if (ui.more) {
                    Text(
                        pluralStringResource(R.plurals.share_more, ui.results.size, ui.results.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("share_more"),
                    )
                }
                PrimaryButton(stringResource(R.string.action_close), onClose, Modifier.testTag("share_close"))
            }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun ResultCard(index: Int, result: ShareResult, report: ReportStatus, onCopy: (String) -> Unit, onReport: () -> Unit) {
    val model = result.model
    var copied by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .testTag("share_result_$index"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusBadge(result.status, size = 40.dp, container = MaterialTheme.colorScheme.background)
            Text(
                stringResource(
                    when (result.status) {
                        Status.DANGER -> R.string.interstitial_title_strong
                        Status.CAUTION -> R.string.interstitial_title_soft
                        Status.SAFE -> R.string.share_safe_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("share_result_title_$index"),
            )
        }
        if (model.host != null) {
            // Hosts are always left-to-right, even inside an Urdu or Arabic sentence.
            Text(
                hostText(model.host, model.domain, MaterialTheme.colorScheme.onSurfaceVariant),
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.testTag("share_result_host_$index"),
            )
        }
        if (result.status != Status.SAFE) {
            Text(reasonText(model.reason), style = MaterialTheme.typography.bodyLarge)
        }
        if (model.reason is Reason.Upi) UpiDetails(model.reason)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            result.copyName?.let { name ->
                TextButton(
                    stringResource(R.string.share_copy),
                    { onCopy(name); copied = true },
                    Modifier.testTag("share_copy_$index"),
                )
                if (copied) {
                    Text(
                        stringResource(R.string.share_copied),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            if (result.canReport) ReportBlock(report, onReport)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShareCheckPreview() {
    LinkguardTheme {
        ShareCheckScreen(
            ShareCheckUi.Results(
                listOf(
                    ShareResult(Status.DANGER, InterstitialModel(true, "sbi-kyc.top", "sbi-kyc.top", Reason.BrandLookalike("SBI")), "sbi-kyc.top", true),
                    ShareResult(Status.SAFE, InterstitialModel(false, "www.irctc.co.in", "irctc.co.in", Reason.Generic), "irctc.co.in", true),
                ),
                more = false,
            ),
            emptyMap(), {}, {}, {},
        )
    }
}
