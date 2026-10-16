package com.dwarpallabs.linkguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme

private val SoftAccent = Color(0xFFB45309)

/** The one warning screen. "Go back" is primary; "Open anyway" is always present (never block). */
@Composable
fun InterstitialScreen(model: InterstitialModel, onGoBack: () -> Unit, onOpenAnyway: () -> Unit) {
    val accent = if (model.strong) MaterialTheme.colorScheme.error else SoftAccent
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = accent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            Text(
                stringResource(if (model.strong) R.string.interstitial_title_strong else R.string.interstitial_title_soft),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.testTag("title"),
            )

            if (model.host != null) {
                Text(
                    hostText(model.host, model.domain),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("host"),
                )
            }

            Text(
                reasonText(model.reason),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("reason"),
            )

            if (model.reason is Reason.Upi) UpiDetails(model.reason)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onGoBack,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("go_back"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.interstitial_go_back), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = onOpenAnyway,
                modifier = Modifier.fillMaxWidth().testTag("open_anyway"),
            ) {
                Text(stringResource(R.string.interstitial_open_anyway), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Shown only for short links still being resolved after 300 ms. */
@Composable
fun CheckingCard() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
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

private fun hostText(host: String, domain: String?) = buildAnnotatedString {
    val start = if (domain != null && host.endsWith(domain)) host.length - domain.length else host.length
    append(host.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(host.substring(start)) }
}

@Composable
private fun reasonText(reason: Reason): String = when (reason) {
    Reason.ApkDownload -> stringResource(R.string.warn_apk_download)
    is Reason.BrandLookalike -> stringResource(R.string.warn_brand_lookalike, reason.brand)
    is Reason.Upi -> stringResource(R.string.warn_upi_intent)
    is Reason.WeirdHost -> reason.brand?.let { stringResource(R.string.warn_weird_host_brand, it) }
        ?: stringResource(R.string.warn_weird_host)
    Reason.Reputation -> stringResource(R.string.warn_reputation)
    is Reason.Community -> pluralStringResource(R.plurals.warn_community, reason.count, reason.count)
    is Reason.DomainAge -> pluralStringResource(R.plurals.warn_domain_age, reason.days, reason.days)
    Reason.Generic -> stringResource(R.string.warn_generic)
}

@Composable
private fun UpiDetails(upi: Reason.Upi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().testTag("upi_details"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.interstitial_upi_heading), style = MaterialTheme.typography.titleMedium)
            val payee = listOfNotNull(upi.payeeName, upi.payeeAddress?.let { "($it)" }).joinToString(" ")
            if (payee.isNotEmpty()) {
                Text(stringResource(R.string.interstitial_upi_payee, payee), style = MaterialTheme.typography.bodyLarge)
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
