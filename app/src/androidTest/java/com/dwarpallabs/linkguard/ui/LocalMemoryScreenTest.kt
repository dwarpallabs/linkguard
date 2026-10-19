package com.dwarpallabs.linkguard.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/** Roadmap A6 in English and Tamil: "Learned on this phone" in Settings and the my-brand warning. */
@RunWith(Parameterized::class)
class LocalMemoryScreenTest(private val languageTag: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun languages() = listOf("en", "ta")
    }

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.let { base ->
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
        base.createConfigurationContext(config)
    }
    private val res = context.resources
    private val forgotten = mutableListOf<String>()

    private fun showHome(memory: MemoryUi) {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides res.configuration) {
                LinkguardTheme {
                    HomeScreen(
                        isDefault = true, browsers = emptyList(), selected = null, disabledChecks = emptySet(),
                        onSetDefault = {}, onSelectBrowser = {}, onToggleCheck = { _, _ -> },
                        onOpenHowItWorks = {}, onOpenReport = {}, onOpenFeedback = {}, onOpenAbout = {},
                        memory = memory,
                        onForgetBrands = { forgotten += "brands" },
                        onForgetTrusted = { forgotten += "trusted" },
                    )
                }
            }
        }
    }

    @Test
    fun learnedSectionShowsBrandsAndTrustedCountWithForgetButtons() {
        showHome(MemoryUi(trustedCount = 2, myBrands = listOf("HDFC Bank", "SBI")))
        compose.onNodeWithText(res.getString(R.string.memory_brands, "HDFC Bank, SBI")).performScrollTo()
        compose.onNodeWithText(res.getQuantityString(R.plurals.memory_trusted, 2, 2)).performScrollTo()
        compose.onNodeWithTag("forget_brands").performScrollTo().performClick()
        compose.onNodeWithTag("forget_trusted").performScrollTo().performClick()
        assertEquals(listOf("brands", "trusted"), forgotten)
    }

    @Test
    fun learnedSectionIsHiddenWhileEmpty() {
        showHome(MemoryUi())
        compose.onNodeWithText(res.getString(R.string.memory_heading)).assertDoesNotExist()
        compose.onNodeWithTag("memory_brands").assertDoesNotExist()
    }

    @Test
    fun onlyTrustedShowsOnlyThatPart() {
        showHome(MemoryUi(trustedCount = 1))
        compose.onNodeWithTag("memory_trusted").performScrollTo()
        compose.onNodeWithTag("memory_brands").assertDoesNotExist()
    }

    @Test
    fun aFakeOfMyBrandSaysSoOnTheWarningScreen() {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides res.configuration) {
                LinkguardTheme {
                    InterstitialScreen(
                        InterstitialModel(true, "sbi-kyc.top", "sbi-kyc.top", Reason.BrandLookalike("SBI", mine = true)),
                        onGoBack = {}, onOpenAnyway = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("reason").assertTextContains(res.getString(R.string.warn_brand_lookalike_mine, "SBI"))
    }
}
