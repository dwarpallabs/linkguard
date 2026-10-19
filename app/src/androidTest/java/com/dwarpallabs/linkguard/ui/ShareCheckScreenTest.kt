package com.dwarpallabs.linkguard.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ShareCheckActivity
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.components.Status
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/** The "Check with LinkGuard" results screen, in English and Tamil. */
@RunWith(Parameterized::class)
class ShareCheckScreenTest(private val languageTag: String) {

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
    private lateinit var original: Configuration

    /** The report sheet draws in its own window, which reads the activity's resources. */
    @Before
    fun useLanguage() {
        compose.runOnUiThread {
            original = Configuration(compose.activity.resources.configuration)
            @Suppress("DEPRECATION")
            compose.activity.resources.updateConfiguration(res.configuration, compose.activity.resources.displayMetrics)
        }
    }

    @After
    fun restoreLanguage() {
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            compose.activity.resources.updateConfiguration(original, compose.activity.resources.displayMetrics)
        }
    }

    private val danger = ShareResult(Status.DANGER, InterstitialModel(true, "login.sbi-kyc.top", "sbi-kyc.top", Reason.BrandLookalike("SBI")), "sbi-kyc.top", true)
    private val safe = ShareResult(Status.SAFE, InterstitialModel(false, "www.irctc.co.in", "irctc.co.in", Reason.Generic), "irctc.co.in", true)
    private val upi = ShareResult(Status.CAUTION, InterstitialModel(false, null, null, Reason.Upi("Electricity Board", "collect.now@ybl", "4,999")), null, false)

    private val copied = mutableListOf<String>()
    private val reported = mutableListOf<Int>()
    private var closed = 0

    private fun show(ui: ShareCheckUi, reports: Map<Int, ReportStatus> = emptyMap()) {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides res.configuration) {
                LinkguardTheme {
                    ShareCheckScreen(ui, reports, { copied += it }, { reported += it }, { closed++ })
                }
            }
        }
    }

    @Test
    fun oneCardPerLinkWithItsBand() {
        show(ShareCheckUi.Results(listOf(danger, safe, upi), more = false))
        compose.onNodeWithTag("share_title").assertTextContains(res.getQuantityString(R.plurals.share_title, 3, 3))
        compose.onNodeWithTag("share_result_title_0").assertTextContains(res.getString(R.string.interstitial_title_strong))
        compose.onNodeWithTag("share_result_host_0").assertTextContains("login.sbi-kyc.top")
        compose.onNodeWithTag("share_result_title_1").performScrollTo().assertTextContains(res.getString(R.string.share_safe_title))
        compose.onNodeWithTag("share_result_title_2").performScrollTo().assertTextContains(res.getString(R.string.interstitial_title_soft))
        compose.onNodeWithTag("upi_details").performScrollTo().assertIsDisplayed()
        // A UPI request has no website to copy or report.
        compose.onNodeWithTag("share_copy_2").assertDoesNotExist()
        compose.onNodeWithTag("share_more").assertDoesNotExist()
        // A verdict, not a gate: there is no way to open a link from here.
        compose.onNodeWithText(res.getString(R.string.interstitial_open_anyway)).assertDoesNotExist()
    }

    @Test
    fun copyGivesTheWebsiteNameAndSaysSo() {
        show(ShareCheckUi.Results(listOf(danger), more = false))
        compose.onNodeWithTag("share_copy_0").performScrollTo().performClick()
        assertEquals(listOf("sbi-kyc.top"), copied)
        compose.onNodeWithText(res.getString(R.string.share_copied)).assertIsDisplayed()
    }

    @Test
    fun reportAsksFirstThenSendsThatLink() {
        show(ShareCheckUi.Results(listOf(safe, danger), more = false))
        // Each card has its own Report button; the second card's must report the second link.
        compose.onAllNodesWithTag("report")[1].performScrollTo().performClick()
        compose.onNodeWithTag("report_confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1), reported)
    }

    @Test
    fun moreThanTenLinksSaysOnlyTheFirstWereChecked() {
        show(ShareCheckUi.Results(List(10) { safe }, more = true))
        compose.onNodeWithTag("share_more").performScrollTo()
            .assertTextContains(res.getQuantityString(R.plurals.share_more, 10, 10))
    }

    @Test
    fun noLinksExplainsAndCloses() {
        show(ShareCheckUi.NoLinks)
        compose.onNodeWithTag("share_no_links").assertTextContains(res.getString(R.string.share_no_links))
        compose.onNodeWithTag("share_close").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun checkingShowsProgress() {
        show(ShareCheckUi.Checking)
        compose.onNodeWithText(res.getString(R.string.share_checking)).assertIsDisplayed()
    }

    @Test
    fun sharedTextReadsTextAndSubjectOfASendIntent() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Update KYC sbi-kyc.top/x")
            .putExtra(Intent.EXTRA_SUBJECT, "SBI")
        assertEquals("SBI\nUpdate KYC sbi-kyc.top/x", ShareCheckActivity.sharedText(send))
        assertEquals("", ShareCheckActivity.sharedText(Intent(Intent.ACTION_VIEW)))
        assertEquals("", ShareCheckActivity.sharedText(null))
    }
}
