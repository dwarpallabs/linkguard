package com.dwarpallabs.linkguard.ui

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/** Roadmap G1: the warning screen and Home in Arabic (right-to-left) and Simplified Chinese, also at 200 % text. */
@RunWith(Parameterized::class)
class GlobalLanguagesTest(private val languageTag: String, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} x{1}")
        fun cases() = listOf(arrayOf<Any>("ar", 1f), arrayOf<Any>("zh-Hans", 1f), arrayOf<Any>("ar", 2f), arrayOf<Any>("zh-Hans", 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.let { base ->
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
            fontScale = this@GlobalLanguagesTest.fontScale
        }
        base.createConfigurationContext(config)
    }
    private val res = context.resources
    private val rtl = res.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides res.configuration,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) { LinkguardTheme { content() } }
        }
    }

    @Test
    fun theLanguageIsReallyTranslatedAndArabicIsRightToLeft() {
        assertTrue(res.getString(R.string.interstitial_go_back) != "Go back")
        assertEquals(languageTag == "ar", rtl)
    }

    @Test
    fun warningScreenShowsEverythingAndKeepsTheWebsiteLeftToRight() {
        show {
            InterstitialScreen(
                InterstitialModel(true, "login.sbi-kyc.top", "sbi-kyc.top", Reason.BrandLookalike("SBI")),
                onGoBack = {}, onOpenAnyway = {},
            )
        }
        compose.onNodeWithTag("title").assertTextContains(res.getString(R.string.interstitial_title_strong))
        compose.onNodeWithTag("host").assertTextContains("login.sbi-kyc.top")
        compose.onNodeWithTag("reason").assertTextContains(res.getString(R.string.warn_brand_lookalike, "SBI"))
        compose.onNodeWithTag("go_back").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("open_anyway").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("report").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun upiWarningShowsPayeeAndAmount() {
        show {
            InterstitialScreen(
                InterstitialModel(false, null, null, Reason.Upi("Electricity Board", "collect.now@ybl", "4,999")),
                onGoBack = {}, onOpenAnyway = {},
            )
        }
        compose.onNodeWithTag("upi_details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.interstitial_upi_amount, "4,999")).assertIsDisplayed()
    }

    @Test
    fun homeScreenRendersToTheBottom() {
        show {
            HomeScreen(
                isDefault = false, browsers = emptyList(), selected = null, disabledChecks = emptySet(),
                onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> },
                onOpenHowItWorks = {}, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
                memory = MemoryUi(trustedCount = 2, myBrands = listOf("SBI")),
            )
        }
        compose.onNodeWithTag("status_card").assertIsDisplayed()
        compose.onNodeWithTag("memory_trusted").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("open_about").performScrollTo().assertIsDisplayed()
    }
}
