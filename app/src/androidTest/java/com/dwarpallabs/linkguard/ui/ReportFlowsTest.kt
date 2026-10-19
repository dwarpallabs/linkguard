package com.dwarpallabs.linkguard.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before


import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/**
 * The report confirmation sheet and the "Was that link a scam?" card, in English and Tamil.
 * Tamil labels are long, so this also catches text that does not fit or a button that is cut off.
 */
@RunWith(Parameterized::class)
class ReportFlowsTest(private val languageTag: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun languages() = listOf("en", "ta")
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.let { base ->
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
        base.createConfigurationContext(config)
    }
    private val res = context.resources

    /**
     * The bottom sheet draws in its own window, which takes its strings from the activity rather
     * than from LocalContext, so the activity's own resources are switched to the test language too.
     */
    private lateinit var original: Configuration

    @Before
    fun useLanguage() {
        compose.runOnUiThread {
            original = Configuration(compose.activity.resources.configuration)
            @Suppress("DEPRECATION")
            compose.activity.resources.updateConfiguration(res.configuration, compose.activity.resources.displayMetrics)
        }
    }

    /** Resources are shared across the process: put the device language back for the next test class. */
    @After
    fun restoreLanguage() {
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            compose.activity.resources.updateConfiguration(original, compose.activity.resources.displayMetrics)
        }
    }

    @Composable
    private fun Localised(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides res.configuration) {
            LinkguardTheme { content() }
        }
    }

    private val brand = InterstitialModel(true, "sbi-rewards-redeem.xyz", "sbi-rewards-redeem.xyz", Reason.BrandLookalike("SBI"))

    private fun showWarning(onReport: () -> Unit) {
        compose.setContent { Localised { InterstitialScreen(brand, {}, {}, ReportStatus.IDLE, onReport) } }
    }

    @Test
    fun sheetSaysWhatIsSentAndReportSends() {
        var reports = 0
        showWarning { reports++ }
        compose.onNodeWithTag("report").performScrollTo().performClick()
        compose.onNodeWithTag("report_sheet_title").assertTextContains(res.getString(R.string.report_confirm_title))
        compose.onNodeWithTag("report_confirm").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, reports)
        compose.onNodeWithTag("report_sheet").assertDoesNotExist()
    }

    @Test
    fun closingTheSheetSendsNothing() {
        var reports = 0
        showWarning { reports++ }
        compose.onNodeWithTag("report").performScrollTo().performClick()
        compose.onNodeWithTag("report_close").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(0, reports)
        compose.onNodeWithTag("report_sheet").assertDoesNotExist()
    }

    private val answers = mutableListOf<String>()

    private fun showCard(status: ReportStatus) {
        compose.setContent {
            Localised {
                ScamCheckCard(
                    domain = "sbi-rewards-redeem.xyz",
                    openedAt = 1_792_000_000_000,
                    status = status,
                    onScam = { answers += "scam" },
                    onFine = { answers += "fine" },
                    onNotSure = { answers += "unsure" },
                    onClose = { answers += "close" },
                )
            }
        }
    }

    @Test
    fun cardAsksAboutTheWebsiteAndEachAnswerFires() {
        showCard(ReportStatus.IDLE)
        compose.onNodeWithTag("scam_check_title").assertTextContains(res.getString(R.string.scam_check_title))
        compose.onNodeWithTag("scam_check_body").assertTextContains("sbi-rewards-redeem.xyz", substring = true)
        compose.onNodeWithTag("scam_check_yes").assertTextContains(res.getString(R.string.scam_check_yes)).performClick()
        compose.onNodeWithTag("scam_check_no").assertTextContains(res.getString(R.string.scam_check_no)).performClick()
        compose.onNodeWithTag("scam_check_unsure").assertTextContains(res.getString(R.string.scam_check_unsure)).performClick()
        assertEquals(listOf("scam", "fine", "unsure"), answers)
    }

    @Test
    fun whileSendingAnswersAreIgnored() {
        showCard(ReportStatus.SENDING)
        compose.onNodeWithTag("scam_check_yes").assertTextContains(res.getString(R.string.report_sending)).performClick()
        compose.onNodeWithTag("scam_check_no").performClick()
        assertEquals(emptyList<String>(), answers)
    }

    @Test
    fun failedReportShowsTheErrorAndKeepsTheAnswers() {
        showCard(ReportStatus.FAILED)
        compose.onNodeWithTag("scam_check_failed").assertTextContains(res.getString(R.string.report_failed))
        compose.onNodeWithTag("scam_check_yes").performClick()
        assertEquals(listOf("scam"), answers)
    }

    @Test
    fun sentReportBecomesAThankYouUntilClosed() {
        showCard(ReportStatus.SENT)
        compose.onNodeWithTag("scam_check").assertDoesNotExist()
        compose.onNodeWithTag("scam_check_close").assertTextContains(res.getString(R.string.action_close)).performClick()
        assertEquals(listOf("close"), answers)
    }
}
