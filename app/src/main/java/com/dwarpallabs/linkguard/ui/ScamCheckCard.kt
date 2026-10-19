package com.dwarpallabs.linkguard.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat

import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.components.Banner
import com.dwarpallabs.linkguard.ui.components.SecondaryButton
import com.dwarpallabs.linkguard.ui.components.TextButton
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Was that link a scam?" on Home, after the user tapped Open anyway on a warning. Three equal,
 * stacked answers so none is nudged. "Yes, it was a scam" reports the website (with [status]
 * shown inline); the other two only make the card go away for good. After a sent report the card
 * becomes a thank-you banner that stays until Close.
 */
@Composable
fun ScamCheckCard(
    domain: String,
    openedAt: Long,
    status: ReportStatus,
    onScam: () -> Unit,
    onFine: () -> Unit,
    onNotSure: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == ReportStatus.SENT) {
        Column(modifier.testTag("scam_check_thanks"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Banner(stringResource(R.string.report_thanks))
            TextButton(stringResource(R.string.action_close), onClose, Modifier.testTag("scam_check_close"))
        }
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(24.dp)
            .testTag("scam_check"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.scam_check_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("scam_check_title"),
        )
        Text(
            bodyText(stringResource(R.string.scam_check_body, ltr(domain), writtenDate(openedAt)), domain),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("scam_check_body"),
        )
        if (status == ReportStatus.FAILED) {
            Text(
                stringResource(R.string.report_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("scam_check_failed").semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val sending = status == ReportStatus.SENDING
            SecondaryButton(
                stringResource(if (sending) R.string.report_sending else R.string.scam_check_yes),
                { if (!sending) onScam() },
                Modifier.testTag("scam_check_yes"),
            )
            SecondaryButton(stringResource(R.string.scam_check_no), { if (!sending) onFine() }, Modifier.testTag("scam_check_no"))
            SecondaryButton(stringResource(R.string.scam_check_unsure), { if (!sending) onNotSure() }, Modifier.testTag("scam_check_unsure"))
        }
    }
}

/** Keeps the website left-to-right inside an Urdu or Arabic sentence. */
@Composable
private fun ltr(domain: String): String =
    BidiFormatter.getInstance(LocalConfiguration.current.locales[0]).unicodeWrap(domain, TextDirectionHeuristicsCompat.LTR)

/** The sentence with the website in bold. */
private fun bodyText(sentence: String, website: String): AnnotatedString = buildAnnotatedString {
    val at = sentence.indexOf(website)
    if (at < 0) {
        append(sentence)
        return@buildAnnotatedString
    }
    append(sentence.substring(0, at))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(website) }
    append(sentence.substring(at + website.length))
}

/** "19 October" in the app's language, with Western digits as everywhere else in LinkGuard. */
@Composable
private fun writtenDate(millis: Long): String {
    val base = LocalConfiguration.current.locales[0]
    val locale = Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build()
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale).format(Date(millis))
}

@Preview(showBackground = true)
@Composable
private fun ScamCheckPreview() {
    LinkguardTheme {
        ScamCheckCard("sbi-rewards-redeem.xyz", 1_792_000_000_000, ReportStatus.IDLE, {}, {}, {}, {})
    }
}
