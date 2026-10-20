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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.browser.BrowserApp
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.ui.components.LineIcons
import com.dwarpallabs.linkguard.ui.components.PrimaryButton
import com.dwarpallabs.linkguard.ui.components.StatusCard
import com.dwarpallabs.linkguard.ui.components.TextButton
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
    onOpenHowItWorks: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenAbout: () -> Unit,
    /** Shown first, above everything else: the "Was that link a scam?" card, when there is a question. */
    topCard: @Composable () -> Unit = {},
    /** What LinkGuard learned on this phone; the section is hidden while it is empty. */
    memory: MemoryUi = MemoryUi(),
    onForgetBrands: () -> Unit = {},
    onForgetTrusted: () -> Unit = {},
    /** The organisation set LinkGuard through device management (roadmap C5): say so above the checks. */
    managed: Boolean = false,
    /** The organisation keeps every check on: the switches show on and cannot be changed. */
    checksLocked: Boolean = false,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            topCard()
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

            if (isDefault) {
                SectionHeading(stringResource(R.string.default_heading))
                StatusCard(on = true, title = stringResource(R.string.default_done), modifier = Modifier.testTag("status_card"))
            } else {
                // Not protected yet: the step heading becomes the card's title, so the one action sits inside it.
                StatusCard(on = false, title = stringResource(R.string.default_heading), modifier = Modifier.testTag("status_card")) {
                    Text(
                        stringResource(R.string.default_explain, browserName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("default_explain"),
                    )
                    PrimaryButton(stringResource(R.string.default_button), onSetDefault)
                    Text(
                        stringResource(R.string.default_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                                .heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = browser.packageName == selected, onClick = null)
                            Text(browser.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }

            SectionHeading(stringResource(R.string.settings_checks_heading))
            if (managed) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        stringResource(R.string.managed_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp).testTag("managed_notice"),
                    )
                }
            }
            Column {
                ToggleableChecks.forEach { (check, label) ->
                    CheckRow(
                        check = check,
                        label = label,
                        description = CheckDescriptions[check],
                        enabled = checksLocked || check !in disabledChecks,
                        locked = checksLocked,
                        onToggle = { onToggleCheck(check, it) },
                    )
                }
                if (checksLocked) {
                    Text(
                        stringResource(R.string.managed_checks_locked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("managed_checks_locked"),
                    )
                }
            }

            if (memory.myBrands.isNotEmpty() || memory.trustedCount > 0) {
                MemorySection(memory, onForgetBrands, onForgetTrusted)
            }

            HorizontalDivider()
            LinkRow(stringResource(R.string.how_title), stringResource(R.string.how_desc), onOpenHowItWorks, "open_how")

            LinkRow(stringResource(R.string.settings_report), stringResource(R.string.settings_report_desc), onOpenReport, "open_report")

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    stringResource(R.string.home_share_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp).testTag("share_tip"),
                )
            }


            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    stringResource(R.string.telegram_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LinkRow(stringResource(R.string.feedback_title), stringResource(R.string.feedback_desc), onOpenFeedback, "open_feedback")
            LinkRow(stringResource(R.string.about_title), stringResource(R.string.about_desc), onOpenAbout, "open_about")
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth().testTag("not_a_browser"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val steps = listOf(
                LineIcons.Link to stringResource(R.string.flow_link),
                LineIcons.Shield to stringResource(R.string.flow_check),
                LineIcons.Globe to browserName,
            )
            // Side by side only while every word fits its column; long words (Tamil, large text)
            // would otherwise break mid-word, so stack the steps instead.
            if (flowNeedsStacking(steps.map { it.second })) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    steps.forEachIndexed { i, (icon, label) ->
                        if (i > 0) FlowArrow(Modifier.padding(start = 6.dp).rotate(downRotation()))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    steps.forEachIndexed { i, (icon, label) ->
                        if (i > 0) FlowArrow()
                        FlowStep(icon, label)
                    }
                }
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
private fun RowScope.FlowStep(icon: ImageVector, label: String) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FlowArrow(modifier: Modifier = Modifier) {
    Icon(
        LineIcons.Arrow,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(20.dp),
    )
}

@Composable
private fun flowNeedsStacking(labels: List<String>): Boolean {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    // Card padding 16 dp each side, two 20 dp arrows, four 6 dp gaps (all inside the 24 dp gutters).
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val column = with(density) { ((screenWidth - 48.dp - 32.dp - 40.dp - 24.dp) / 3).toPx() }
    return labels.any { label ->
        label.split(' ').any { word -> measurer.measure(word, style, maxLines = 1).size.width > column }
    }
}

/** True for right-to-left languages (Urdu, Kashmiri, Sindhi). */
@Composable
internal fun isRtl(): Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl

/** Turns the forward arrow to point down. It is already mirrored in RTL, so it turns the other way there. */
@Composable
private fun downRotation() = if (isRtl()) -90f else 90f

@Composable
private fun CheckRow(
    check: CheckId,
    @StringRes label: Int,
    @StringRes description: Int?,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    /** Set by the organisation: shown, read out, but not changeable. */
    locked: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, enabled = !locked, onValueChange = onToggle, role = Role.Switch)
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp)
            .testTag("toggle_${check.code}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = enabled, onCheckedChange = null, enabled = !locked)
    }
}

@Composable
internal fun LinkRow(title: String, subtitle: String, onClick: () -> Unit, tag: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp)
            .testTag(tag),
    ) {
        Text(title + if (isRtl()) "  ‹" else "  ›", style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> }, onOpenHowItWorks = {}, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
        )
    }
}

/** What LinkGuard learned on this phone (roadmap A6), for Settings. */
data class MemoryUi(val trustedCount: Int = 0, val myBrands: List<String> = emptyList())

/** "Learned on this phone": the user's brands and trusted websites, each with its own Forget button. */
@Composable
private fun MemorySection(memory: MemoryUi, onForgetBrands: () -> Unit, onForgetTrusted: () -> Unit) {
    SectionHeading(stringResource(R.string.memory_heading))
    Text(
        stringResource(R.string.memory_private),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (memory.myBrands.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("memory_brands")) {
            Text(
                stringResource(R.string.memory_brands, memory.myBrands.joinToString(", ")),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.memory_brands_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(stringResource(R.string.memory_forget_brands), onForgetBrands, Modifier.testTag("forget_brands"))
        }
    }
    if (memory.trustedCount > 0) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("memory_trusted")) {
            Text(
                pluralStringResource(R.plurals.memory_trusted, memory.trustedCount, memory.trustedCount),
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(stringResource(R.string.memory_forget_trusted), onForgetTrusted, Modifier.testTag("forget_trusted"))
        }
    }
}
