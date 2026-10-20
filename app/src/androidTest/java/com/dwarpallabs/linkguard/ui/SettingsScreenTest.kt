package com.dwarpallabs.linkguard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
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
                    onOpenHowItWorks = {}, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
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
                    onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> }, onOpenHowItWorks = {}, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
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
                    onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> }, onOpenHowItWorks = {}, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
                )
            }
        }
        ToggleableChecks.forEach { (check, _) -> compose.onNodeWithTag("toggle_${check.code}").performScrollTo().assertIsOn() }
    }

    @Test
    fun notManagedShowsNoNotice() {
        compose.setContent {
            LinkguardTheme {
                HomeScreen(
                    isDefault = true, browsers = emptyList(), selected = null, disabledChecks = emptySet(),
                    onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> }, onOpenHowItWorks = {}, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
                )
            }
        }
        compose.onNodeWithTag("managed_notice").assertDoesNotExist()
        compose.onNodeWithTag("managed_checks_locked").assertDoesNotExist()
    }

    @Test
    fun organisationKeepsChecksOnAndLockedSwitchesIgnoreTaps() {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var toggles = 0
        compose.setContent {
            LinkguardTheme {
                HomeScreen(
                    isDefault = true, browsers = emptyList(), selected = null, disabledChecks = setOf(CheckId.DOMAIN_AGE),
                    onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> toggles++ }, onOpenHowItWorks = {}, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
                    managed = true, checksLocked = true,
                )
            }
        }
        compose.onNodeWithTag("managed_notice").performScrollTo()
            .assertTextContains(res.getString(com.dwarpallabs.linkguard.R.string.managed_notice))
        val toggle = compose.onNodeWithTag("toggle_domain_age").performScrollTo()
        toggle.assertIsOn().assertIsNotEnabled()
        toggle.performClick()
        assertEquals(0, toggles)
        compose.onNodeWithTag("managed_checks_locked").performScrollTo()
            .assertTextContains(res.getString(com.dwarpallabs.linkguard.R.string.managed_checks_locked))
    }

    @Test
    fun howItWorksRowOpensTheScreen() {
        var opened = false
        compose.setContent {
            LinkguardTheme {
                HomeScreen(
                    isDefault = true, browsers = emptyList(), selected = null, disabledChecks = emptySet(),
                    onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> }, onOpenHowItWorks = { opened = true }, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
                )
            }
        }
        compose.onNodeWithTag("open_how").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun howItWorksNamesTheChosenBrowser() {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        compose.setContent { LinkguardTheme { HowItWorksScreen(browserName = "Firefox", onBack = {}) } }
        compose.onNodeWithText(res.getString(com.dwarpallabs.linkguard.R.string.how_where_body, "Firefox")).assertExists()
        compose.onNodeWithText(res.getString(com.dwarpallabs.linkguard.R.string.how_community_title)).performScrollTo()
    }

    @Test
    fun feedbackScreenOffersEmailAndGitHub() {
        compose.setContent { LinkguardTheme { FeedbackScreen(onEmail = {}, onGitHub = {}, onBack = {}) } }
        compose.onNodeWithTag("feedback_email").assertExists()
        compose.onNodeWithTag("feedback_github").assertExists()
    }

    @Test
    fun aboutShowsVersionAndPrivacyPolicy() {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var privacyOpened = false
        compose.setContent {
            LinkguardTheme { AboutScreen(version = "9.9.9", onPrivacy = { privacyOpened = true }, onSource = {}, onContact = {}, onBack = {}) }
        }
        compose.onNodeWithTag("about_version").assertTextContains(res.getString(com.dwarpallabs.linkguard.R.string.about_version, "9.9.9"))
        compose.onNodeWithTag("about_privacy").performClick()
        assertTrue(privacyOpened)
    }
}
