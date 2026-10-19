package com.dwarpallabs.linkguard

import android.content.Intent
import android.content.pm.PackageManager
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.browser.isCustomTabLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Roadmap CT1: apps that open links in Custom Tabs must find LinkGuard and send links to it. */
@RunWith(AndroidJUnit4::class)
class CustomTabsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun linkGuardOffersACustomTabsService() {
        val services = context.packageManager.queryIntentServices(Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION), PackageManager.MATCH_ALL)
        assertTrue(services.any { it.serviceInfo.packageName == context.packageName })
        assertEquals(context.packageName, CustomTabsClient.getPackageName(context, listOf(context.packageName), true))
    }

    @Test
    fun aCustomTabLaunchOfALinkResolvesToTheInterceptScreen() {
        val launch = CustomTabsIntent.Builder().build().intent.setData("https://lg-test.example/".toUri()).setPackage(context.packageName)
        val target = context.packageManager.resolveActivity(launch, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(LinkInterceptActivity::class.java.name, target?.activityInfo?.name)
        assertTrue(isCustomTabLaunch(launch))
        assertFalse(isCustomTabLaunch(Intent(Intent.ACTION_VIEW, "https://lg-test.example/".toUri())))
        assertFalse(isCustomTabLaunch(null))
    }
}
