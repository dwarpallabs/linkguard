package com.dwarpallabs.linkguard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterstitialScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun show(
        model: InterstitialModel,
        onGoBack: () -> Unit = {},
        onOpenAnyway: () -> Unit = {},
        report: ReportStatus = ReportStatus.IDLE,
        onReport: () -> Unit = {},
    ) {
        compose.setContent { LinkguardTheme { InterstitialScreen(model, onGoBack, onOpenAnyway, report, onReport) } }
    }

    private val brand = InterstitialModel(true, "sbi-rewards-redeem.xyz", "sbi-rewards-redeem.xyz", Reason.BrandLookalike("SBI"))

    @Test
    fun strongBrandWarningShowsHostReasonAndBothButtons() {
        show(InterstitialModel(true, "sbi-rewards-redeem.xyz", "sbi-rewards-redeem.xyz", Reason.BrandLookalike("SBI")))
        compose.onNodeWithTag("title").assertTextContains(res.getString(R.string.interstitial_title_strong))
        compose.onNodeWithTag("host").assertTextContains("sbi-rewards-redeem.xyz")
        compose.onNodeWithTag("reason").assertTextContains(res.getString(R.string.warn_brand_lookalike, "SBI"))
        compose.onNodeWithTag("go_back").assertIsDisplayed()
        compose.onNodeWithTag("open_anyway").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun softWarningUsesSoftTitleAndPluralDays() {
        show(InterstitialModel(false, "lg-fresh-store.com", "lg-fresh-store.com", Reason.DomainAge(3)))
        compose.onNodeWithTag("title").assertTextContains(res.getString(R.string.interstitial_title_soft))
        compose.onNodeWithTag("reason").assertTextContains(res.getQuantityString(R.plurals.warn_domain_age, 3, 3))
    }

    @Test
    fun communityReportShowsCountWithSoftTitle() {
        show(InterstitialModel(false, "lg-reported-three.example", "lg-reported-three.example", Reason.Community(12)))
        compose.onNodeWithTag("title").assertTextContains(res.getString(R.string.interstitial_title_soft))
        compose.onNodeWithTag("reason").assertTextContains(res.getQuantityString(R.plurals.warn_community, 12, 12))
    }

    @Test
    fun homographHostIsDisplayedAsPunycode() {
        show(InterstitialModel(true, "xn--irtel-3ve.com", "xn--irtel-3ve.com", Reason.BrandLookalike("Airtel")))
        compose.onNodeWithTag("host").assertTextContains("xn--irtel-3ve.com")
    }

    @Test
    fun upiShowsPayeeAndAmount() {
        show(InterstitialModel(true, null, null, Reason.Upi("KBC Lottery", "lucky.winner@paytm", "25000")))
        compose.onNodeWithTag("upi_details").assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.interstitial_upi_payee, "KBC Lottery (lucky.winner@paytm)")).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.interstitial_upi_amount, "25000")).assertIsDisplayed()
    }

    @Test
    fun buttonsFireTheirCallbacks() {
        var back = 0
        var open = 0
        show(
            InterstitialModel(true, "x.top", "x.top", Reason.ApkDownload),
            onGoBack = { back++ },
            onOpenAnyway = { open++ },
        )
        compose.onNodeWithTag("go_back").performClick()
        compose.onNodeWithTag("open_anyway").performScrollTo().performClick()
        assertEquals(1, back)
        assertEquals(1, open)
    }

    @Test
    fun reportButtonIsShownAndFiresItsCallback() {
        var reports = 0
        show(brand, onReport = { reports++ })
        compose.onNodeWithTag("report").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.interstitial_report_hint)).assertIsDisplayed()
        compose.onNodeWithTag("report").performClick()
        assertEquals(1, reports)
    }

    @Test
    fun sendingDisablesTheReportButton() {
        show(brand, report = ReportStatus.SENDING)
        compose.onNodeWithTag("report").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(res.getString(R.string.report_sending)).assertIsDisplayed()
    }

    @Test
    fun sentReportReplacesTheButtonWithThanks() {
        show(brand, report = ReportStatus.SENT)
        compose.onNodeWithTag("report_result").performScrollTo().assertTextContains(res.getString(R.string.report_sent))
        compose.onNodeWithTag("report").assertDoesNotExist()
        compose.onNodeWithTag("open_anyway").assertIsDisplayed()
    }

    @Test
    fun failedReportShowsTheErrorAndKeepsTheButton() {
        show(brand, report = ReportStatus.FAILED)
        compose.onNodeWithTag("report_result").performScrollTo().assertTextContains(res.getString(R.string.report_failed))
        compose.onNodeWithTag("report").assertIsDisplayed()
    }
}
