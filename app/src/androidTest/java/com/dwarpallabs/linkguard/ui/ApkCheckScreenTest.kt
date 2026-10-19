package com.dwarpallabs.linkguard.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.ApkInspector
import com.dwarpallabs.linkguard.ApkInspector.Capability
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/** Roadmap B3 in English and Tamil: "Check what this app can do" on an app-download warning. */
@RunWith(Parameterized::class)
class ApkCheckScreenTest(private val languageTag: String) {

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
    private var checks = 0
    private var stops = 0

    private fun show(state: ApkCheckUi, reason: Reason = Reason.ApkDownload) {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides res.configuration) {
                LinkguardTheme {
                    InterstitialScreen(
                        InterstitialModel(true, "lg-files.example", "lg-files.example", reason),
                        onGoBack = {}, onOpenAnyway = {},
                        apkCheck = state, onCheckApk = { checks++ }, onStopApk = { stops++ },
                    )
                }
            }
        }
    }

    @Test
    fun offeredOnlyOnAppDownloadWarnings() {
        show(ApkCheckUi.Idle)
        compose.onNodeWithTag("apk_check_button").performScrollTo().assertTextContains(res.getString(R.string.apk_check_button)).performClick()
        compose.onNodeWithText(res.getString(R.string.apk_check_hint)).assertIsDisplayed()
        assertEquals(1, checks)
    }

    @Test
    fun notOfferedForOtherWarnings() {
        show(ApkCheckUi.Idle, reason = Reason.BrandLookalike("SBI"))
        compose.onNodeWithTag("apk_check").assertDoesNotExist()
    }

    @Test
    fun runningShowsProgressAndStop() {
        show(ApkCheckUi.Running(3L * 1024 * 1024, 12L * 1024 * 1024))
        compose.onNodeWithTag("apk_check_progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("apk_check_stop").performClick()
        assertEquals(1, stops)
    }

    @Test
    fun resultListsWhatTheAppCouldDoAndTheBrandTrap() {
        show(ApkCheckUi.Done(ApkInspector.Report("SBI YONO KYC", listOf(Capability.READ_SMS, Capability.ACCESSIBILITY), "SBI", "abc")))
        compose.onNodeWithTag("apk_check_brand").performScrollTo().assertTextContains(res.getString(R.string.apk_check_brand, "SBI"))
        compose.onNodeWithText("• " + res.getString(R.string.cap_read_sms)).assertIsDisplayed()
        compose.onNodeWithText("• " + res.getString(R.string.cap_accessibility)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.apk_check_deleted)).assertIsDisplayed()
    }

    @Test
    fun anOrdinaryAppSaysSo() {
        show(ApkCheckUi.Done(ApkInspector.Report("Notes", emptyList(), null, "abc")))
        compose.onNodeWithText(res.getString(R.string.apk_check_nothing)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("apk_check_brand").assertDoesNotExist()
    }

    @Test
    fun problemsExplainAndRetryWhereItHelps() {
        show(ApkCheckUi.Failed)
        compose.onNodeWithTag("apk_check_problem").performScrollTo().assertTextContains(res.getString(R.string.apk_check_failed))
        compose.onNodeWithTag("apk_check_button").assertIsDisplayed()
    }

    @Test
    fun notAnAppHasNoRetry() {
        show(ApkCheckUi.NotAnApp)
        compose.onNodeWithTag("apk_check_problem").performScrollTo().assertTextContains(res.getString(R.string.apk_check_not_an_app))
        compose.onNodeWithTag("apk_check_button").assertDoesNotExist()
    }
}
