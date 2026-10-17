package com.dwarpallabs.linkguard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.browser.BrowserApp
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme

/** Checks the user can switch off, in display order. The allowlist is always on. */
val ToggleableChecks: List<Pair<CheckId, Int>> = listOf(
    CheckId.UNWRAP to R.string.check_unwrap,
    CheckId.APK_DOWNLOAD to R.string.check_apk_download,
    CheckId.BRAND_LOOKALIKE to R.string.check_brand_lookalike,
    CheckId.WEIRD_HOST to R.string.check_weird_host,
    CheckId.REPUTATION to R.string.check_reputation,
    CheckId.DOMAIN_AGE to R.string.check_domain_age,
    CheckId.UPI_INTENT to R.string.check_upi_intent,
)

/** Extra line for checks that use the network, so the user knows what leaves the phone. */
private val CheckDescriptions = mapOf(
    CheckId.UNWRAP to R.string.check_unwrap_desc,
    CheckId.DOMAIN_AGE to R.string.check_domain_age_desc,
)

/** First-launch setup and Settings in one screen. */
@Composable
fun HomeScreen(
    isDefault: Boolean,
    browsers: List<BrowserApp>,
    selected: String?,
    disabledChecks: Set<CheckId>,
    onSetDefault: () -> Unit,
    onSelectBrowser: (String) -> Unit,
    onToggleCheck: (CheckId, Boolean) -> Unit,
    onOpenReport: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!isDefault) {
                Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge)
            }
            val browserName = browsers.firstOrNull { it.packageName == selected }?.label
                ?: stringResource(R.string.your_browser)
            NotABrowserCard(browserName)
            Text(
                stringResource(R.string.onboarding_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeading(stringResource(R.string.default_heading))
            if (isDefault) {
                Text(
                    "✓ " + stringResource(R.string.default_done),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    stringResource(R.string.default_explain, browserName),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("default_explain"),
                )
                Button(onClick = onSetDefault, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.default_button))
                }
                Text(
                    stringResource(R.string.default_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeading(stringResource(R.string.browser_heading))
            if (browsers.isEmpty()) {
                Text(stringResource(R.string.browser_none), style = MaterialTheme.typography.bodyLarge)
            } else {
                Column(Modifier.selectableGroup()) {
                    browsers.forEach { browser ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = browser.packageName == selected,
                                    onClick = { onSelectBrowser(browser.packageName) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = browser.packageName == selected, onClick = null)
                            Text(browser.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }

            SectionHeading(stringResource(R.string.settings_checks_heading))
            Column {
                ToggleableChecks.forEach { (check, label) ->
                    CheckRow(
                        check = check,
                        label = label,
                        description = CheckDescriptions[check],
                        enabled = check !in disabledChecks,
                        onToggle = { onToggleCheck(check, it) },
                    )
                }
            }

            HorizontalDivider()
            LinkRow(stringResource(R.string.settings_report), stringResource(R.string.settings_report_desc), onOpenReport, "open_report")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    stringResource(R.string.telegram_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Makes clear LinkGuard is a check between the link and the user's own browser, not a browser:
 * Android's "default browser" wording otherwise suggests it replaces Chrome.
 */
@Composable
private fun NotABrowserCard(browserName: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().testTag("not_a_browser"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                FlowStep("🔗", stringResource(R.string.flow_link))
                Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowStep("🛡", stringResource(R.string.flow_check))
                Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowStep("🌐", browserName)
            }
            Text(
                stringResource(R.string.not_a_browser_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.not_a_browser_body, browserName), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RowScope.FlowStep(icon: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun CheckRow(check: CheckId, @StringRes label: Int, @StringRes description: Int?, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, onValueChange = onToggle, role = Role.Switch)
            .padding(vertical = 10.dp)
            .testTag("toggle_${check.code}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit, tag: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(tag),
    ) {
        Text(title + "  ›", style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun HomePreview() {
    LinkguardTheme {
        HomeScreen(
            isDefault = true,
            browsers = listOf(BrowserApp("com.android.chrome", "Chrome"), BrowserApp("org.mozilla.firefox", "Firefox")),
            selected = "com.android.chrome",
            disabledChecks = setOf(CheckId.DOMAIN_AGE),
            onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> }, onOpenReport = {},
        )
    }
}
