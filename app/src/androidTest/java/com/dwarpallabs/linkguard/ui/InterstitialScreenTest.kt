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
import org.junit.Assert.assertTrue
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
    fun upiCardSaysWhatTheLinkAsksForAndShowsTheUpiId() {
        show(InterstitialModel(false, null, null, Reason.Upi("Electricity Board", "collect.now@ybl", "4,999")))
        compose.onNodeWithTag("upi_heading").performScrollTo().assertTextContains(res.getString(R.string.upi_asks_to_pay))
        compose.onNodeWithTag("upi_id").assertTextContains("collect.now@ybl")
        compose.onNodeWithTag("reason").assertTextContains(res.getString(R.string.warn_upi_intent))
    }

    @Test
    fun upiMandateAndFakeBrandHaveTheirOwnWords() {
        show(InterstitialModel(true, null, null, Reason.Upi("SBI Refund", "refund@axl", "1", mandate = true, brand = "SBI")))
        compose.onNodeWithTag("upi_heading").performScrollTo().assertTextContains(res.getString(R.string.upi_asks_mandate))
        compose.onNodeWithTag("reason").assertTextContains(res.getString(R.string.warn_upi_brand, "SBI"))
    }

    private class FakeSpeaker(override var speaking: Boolean = false) : WarningSpeaker {
        val said = mutableListOf<String>()
        var stops = 0
        override fun speak(text: String) { said += text }
        override fun stop() { stops++ }
    }

    @Test
    fun listenReadsTheTitleAndReason() {
        val speaker = FakeSpeaker()
        compose.setContent { LinkguardTheme { InterstitialScreen(brand, {}, {}, speaker = speaker) } }
        compose.onNodeWithTag("listen").assertTextContains(res.getString(R.string.listen)).performClick()
        assertEquals(1, speaker.said.size)
        assertTrue(speaker.said[0].startsWith(res.getString(R.string.interstitial_title_strong)))
        assertTrue(speaker.said[0].contains(res.getString(R.string.warn_brand_lookalike, "SBI")))
    }

    @Test
    fun whileSpeakingTheButtonStops() {
        val speaker = FakeSpeaker(speaking = true)
        compose.setContent { LinkguardTheme { InterstitialScreen(brand, {}, {}, speaker = speaker) } }
        compose.onNodeWithTag("listen").assertTextContains(res.getString(R.string.listen_stop)).performClick()
        assertEquals(1, speaker.stops)
    }

    @Test
    fun noVoiceNoButton() {
        show(brand)
        compose.onNodeWithTag("listen").assertDoesNotExist()
    }

    @Test
    fun lookOnlyOnSoftWebWarnings() {
        var looks = 0
        val soft = InterstitialModel(false, "lg-fresh-store.com", "lg-fresh-store.com", Reason.DomainAge(3))
        compose.setContent { LinkguardTheme { InterstitialScreen(soft, {}, {}, onLook = { looks++ }) } }
        compose.onNodeWithTag("look").performScrollTo().assertTextContains(res.getString(R.string.look_button)).performClick()
        compose.onNodeWithText(res.getString(R.string.look_hint)).assertIsDisplayed()
        assertEquals(1, looks)
    }

    @Test
    fun noLookOnStrongWarnings() {
        compose.setContent { LinkguardTheme { InterstitialScreen(brand, {}, {}, onLook = {}) } }
        compose.onNodeWithTag("look").assertDoesNotExist()
    }

    @Test
    fun noLookOnUpiRequests() {
        val upi = InterstitialModel(false, null, null, Reason.Upi("Shop", "shop@ybl", "10"))
        compose.setContent { LinkguardTheme { InterstitialScreen(upi, {}, {}, onLook = {}) } }
        compose.onNodeWithTag("look").assertDoesNotExist()
    }

    @Test
    fun partnerConfirmationIsNamed() {
        show(InterstitialModel(true, "lg-partner-scam.example", "lg-partner-scam.example", Reason.Reputation("HDFC Bank")))
        compose.onNodeWithTag("confirmed_by").assertTextContains(res.getString(R.string.confirmed_by, "HDFC Bank"))
        compose.onNodeWithTag("reason").assertTextContains(res.getString(R.string.warn_reputation))
    }

    @Test
    fun noPartnerNoConfirmedLine() {
        show(InterstitialModel(true, "lg-test-phish.com", "lg-test-phish.com", Reason.Reputation()))
        compose.onNodeWithTag("confirmed_by").assertDoesNotExist()
    }

    @Test
    fun organisationBlockSaysWhoAndKeepsOpenAnyway() {
        show(InterstitialModel(true, "lg-org-blocked.example", "lg-org-blocked.example", Reason.Managed))
        compose.onNodeWithTag("title").assertTextContains(res.getString(R.string.interstitial_title_strong))
        compose.onNodeWithTag("reason").assertTextContains(res.getString(R.string.warn_managed_block))
        compose.onNodeWithTag("open_anyway").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun smsLineOnlyWhenTheLinkCameBySms() {
        compose.setContent { LinkguardTheme { InterstitialScreen(brand, {}, {}, fromSms = true) } }
        compose.onNodeWithTag("context_sms").assertTextContains(res.getString(R.string.warn_context_sms))
    }

    @Test
    fun noSmsLineByDefault() {
        show(brand)
        compose.onNodeWithTag("context_sms").assertDoesNotExist()
    }

    @Test
    fun campaignShowsCountWithStrongTitle() {
        show(InterstitialModel(true, "lg-campaign-one.example", "lg-campaign-one.example", Reason.Campaign(7)))
        compose.onNodeWithTag("title").assertTextContains(res.getString(R.string.interstitial_title_strong))
        compose.onNodeWithTag("reason").assertTextContains(res.getQuantityString(R.plurals.warn_campaign, 7, 7))
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
        show(InterstitialModel(true, null, null, Reason.Upi("KBC Lottery", "lucky.winner@paytm", "25,000")))
        compose.onNodeWithTag("upi_details").assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.interstitial_upi_payee, "KBC Lottery")).assertIsDisplayed()
        compose.onNodeWithTag("upi_id").assertTextContains("lucky.winner@paytm")
        compose.onNodeWithText(res.getString(R.string.interstitial_upi_amount, "25,000")).assertIsDisplayed()

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
    fun reportButtonAsksFirstThenFiresItsCallback() {
        var reports = 0
        show(brand, onReport = { reports++ })
        compose.onNodeWithTag("report").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.interstitial_report_hint)).assertIsDisplayed()
        compose.onNodeWithTag("report").performClick()
        assertEquals(0, reports)
        compose.onNodeWithTag("report_confirm").performClick()
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
        compose.onNodeWithTag("report_result").performScrollTo().assertTextContains(res.getString(R.string.report_thanks))

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
