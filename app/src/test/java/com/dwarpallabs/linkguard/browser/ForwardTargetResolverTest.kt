package com.dwarpallabs.linkguard.browser

import com.dwarpallabs.linkguard.browser.ForwardTargetResolver.CHROME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForwardTargetResolverTest {

    private val self = "com.dwarpallabs.linkguard"
    private val firefox = "org.mozilla.firefox"
    private val brave = "com.brave.browser"

    @Test
    fun savedChoiceWinsWhenInstalled() {
        assertEquals(firefox, ForwardTargetResolver.resolve(firefox, listOf(CHROME, firefox), self))
    }

    @Test
    fun uninstalledSavedChoiceFallsBackToChrome() {
        assertEquals(CHROME, ForwardTargetResolver.resolve(brave, listOf(firefox, CHROME), self))
    }

    @Test
    fun noChromeFallsBackToFirstBrowser() {
        assertEquals(firefox, ForwardTargetResolver.resolve(null, listOf(firefox, brave), self))
    }

    @Test
    fun neverForwardsToItself() {
        assertEquals(CHROME, ForwardTargetResolver.resolve(self, listOf(self, CHROME), self))
        assertNull(ForwardTargetResolver.resolve(null, listOf(self), self))
    }

    @Test
    fun noBrowsersMeansChooser() {
        assertNull(ForwardTargetResolver.resolve(null, emptyList(), self))
    }
}
