package com.dwarpallabs.linkguard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.Prefs
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val prefs = Prefs(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before
    @After
    fun reset() {
        prefs.disabledChecks = emptySet()
    }

    @Test
    fun togglingACheckPersistsInPrefs() {
        compose.setContent {
            var disabled by remember { mutableStateOf(prefs.disabledChecks) }
            LinkguardTheme {
                HomeScreen(
                    isDefault = true, browsers = emptyList(), selected = null, disabledChecks = disabled,
                    onSetDefault = {}, onSelectBrowser = {},
                    onToggleCheck = { check, enabled -> prefs.setCheckEnabled(check, enabled); disabled = prefs.disabledChecks },
                    onOpenReport = {},
                )
            }
        }
        val toggle = compose.onNodeWithTag("toggle_domain_age").performScrollTo()
        toggle.assertIsOn()
        toggle.performClick()
        toggle.assertIsOff()
        assertEquals(setOf(CheckId.DOMAIN_AGE), prefs.disabledChecks)
        toggle.performClick()
        assertTrue(prefs.disabledChecks.isEmpty())
    }

    @Test
    fun explainsLinkGuardIsNotABrowserUsingTheChosenBrowserName() {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        compose.setContent {
            LinkguardTheme {
                HomeScreen(
                    isDefault = false,
                    browsers = listOf(com.dwarpallabs.linkguard.browser.BrowserApp("org.mozilla.firefox", "Firefox")),
                    selected = "org.mozilla.firefox", disabledChecks = emptySet(),
                    onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> }, onOpenReport = {},
                )
            }
        }
        compose.onNodeWithTag("not_a_browser").assertExists()
        compose.onNodeWithText(res.getString(com.dwarpallabs.linkguard.R.string.not_a_browser_title)).assertExists()
        compose.onNodeWithText(res.getString(com.dwarpallabs.linkguard.R.string.not_a_browser_body, "Firefox")).assertExists()
        compose.onNodeWithTag("default_explain").assertTextContains(
            res.getString(com.dwarpallabs.linkguard.R.string.default_explain, "Firefox"),
        )
    }

    @Test
    fun everyToggleableCheckHasARow() {
        compose.setContent {
            LinkguardTheme {
                HomeScreen(
                    isDefault = true, browsers = emptyList(), selected = null, disabledChecks = emptySet(),
                    onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> }, onOpenReport = {},
                )
            }
        }
        ToggleableChecks.forEach { (check, _) -> compose.onNodeWithTag("toggle_${check.code}").performScrollTo().assertIsOn() }
    }
}
