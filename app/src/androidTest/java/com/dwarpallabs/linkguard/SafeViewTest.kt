package com.dwarpallabs.linkguard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Roadmap B5: the locked view only takes web links, prefers HTTPS, and runs in its own process. */
@RunWith(AndroidJUnit4::class)
class SafeViewTest {

    @Test
    fun onlyWebLinksAndHttpsFirst() {
        assertTrue(SafeViewActivity.isWebUrl("https://x.example/"))
        assertTrue(SafeViewActivity.isWebUrl("HTTP://x.example/"))
        assertFalse(SafeViewActivity.isWebUrl("upi://pay?pa=a@ybl"))
        assertFalse(SafeViewActivity.isWebUrl("file:///sdcard/x.html"))
        assertFalse(SafeViewActivity.isWebUrl("javascript:alert(1)"))
        assertEquals("https://x.example/a?b", SafeViewActivity.preferHttps("http://x.example/a?b"))
        assertEquals("https://x.example/", SafeViewActivity.preferHttps("https://x.example/"))
    }

    @Test
    fun runsInItsOwnProcessAndIsNotExported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(android.content.ComponentName(context, SafeViewActivity::class.java), 0)
        assertEquals("${context.packageName}:safeview", info.processName)
        assertFalse(info.exported)
    }
}
