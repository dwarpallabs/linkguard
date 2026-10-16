package com.dwarpallabs.linkguard.ui

import androidx.compose.ui.test.assertIsDisplayed
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

    private fun show(model: InterstitialModel, onGoBack: () -> Unit = {}, onOpenAnyway: () -> Unit = {}) {
        compose.setContent { LinkguardTheme { InterstitialScreen(model, onGoBack, onOpenAnyway) } }
    }

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
}
