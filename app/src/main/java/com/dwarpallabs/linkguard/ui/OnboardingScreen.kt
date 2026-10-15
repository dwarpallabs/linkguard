package com.dwarpallabs.linkguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.browser.BrowserApp
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme

@Composable
fun OnboardingScreen(
    isDefault: Boolean,
    browsers: List<BrowserApp>,
    selected: String?,
    onSetDefault: () -> Unit,
    onSelectBrowser: (String) -> Unit,
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
            Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.onboarding_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            SectionHeading(stringResource(R.string.default_heading))
            if (isDefault) {
                Text(
                    "✓ " + stringResource(R.string.default_done),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Button(onClick = onSetDefault, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.default_button))
                }
                Text(
                    stringResource(R.string.default_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
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
                            Text(
                                browser.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    stringResource(R.string.telegram_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    LinkguardTheme {
        OnboardingScreen(
            isDefault = false,
            browsers = listOf(
                BrowserApp("com.android.chrome", "Chrome"),
                BrowserApp("org.mozilla.firefox", "Firefox"),
            ),
            selected = "com.android.chrome",
            onSetDefault = {},
            onSelectBrowser = {},
        )
    }
}
