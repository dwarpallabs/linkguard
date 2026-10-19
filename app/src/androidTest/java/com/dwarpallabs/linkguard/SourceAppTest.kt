package com.dwarpallabs.linkguard

import android.content.Intent
import android.provider.Browser
import android.provider.Telephony
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.browser.cameBySms
import com.dwarpallabs.linkguard.browser.sourcePackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Roadmap B1: which app sent a link, from the sender's app-id extra or the referrer. */
@RunWith(AndroidJUnit4::class)
class SourceAppTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val link = "https://lg-test.example/".toUri()

    @Test
    fun appIdExtraWinsThenTheReferrer() {
        val withId = Intent(Intent.ACTION_VIEW, link).putExtra(Browser.EXTRA_APPLICATION_ID, "com.example.chat")
        assertEquals("com.example.chat", sourcePackage(withId, "android-app://com.example.other".toUri()))
        assertEquals("com.example.other", sourcePackage(Intent(Intent.ACTION_VIEW, link), "android-app://com.example.other".toUri()))
        assertNull(sourcePackage(Intent(Intent.ACTION_VIEW, link), "https://web.example/".toUri()))
        assertNull(sourcePackage(null, null))
    }

    @Test
    fun onlyTheDefaultSmsAppCountsAsSms() {
        val sms = Telephony.Sms.getDefaultSmsPackage(context)
        if (sms != null) assertTrue(cameBySms(context, sms))
        assertFalse(cameBySms(context, "com.example.not.sms"))
        assertFalse(cameBySms(context, null))
    }
}
