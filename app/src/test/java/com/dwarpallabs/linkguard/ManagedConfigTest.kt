package com.dwarpallabs.linkguard

import com.dwarpallabs.linkguard.core.EngineData
import com.dwarpallabs.linkguard.core.ManagedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Managed configuration as device management delivers it (roadmap C5), run on the JVM. */
class ManagedConfigTest {

    private val psl = EngineData.loadBundled().psl

    @Test
    fun nothingSetIsNoPolicy() {
        assertSame(ManagedPolicy.NONE, ManagedConfig.from(null, null, false, psl))
        assertSame(ManagedPolicy.NONE, ManagedConfig.from("", "  \n", false, psl))
        // Only entries that are not websites: still nothing to manage.
        assertSame(ManagedPolicy.NONE, ManagedConfig.from("co.in\nlocalhost", null, false, psl))
    }

    @Test
    fun listsAndKeepChecksOnAreRead() {
        val policy = ManagedConfig.from("bad.example.com\nhttps://worse.example.net/x", "intranet.example.org", true, psl)
        assertTrue(policy.isActive)
        assertTrue(policy.keepChecksOn)
        assertEquals(2, policy.blockedCount)
        assertEquals(1, policy.allowedCount)
    }

    @Test
    fun keepChecksOnAloneIsAPolicy() {
        val policy = ManagedConfig.from(null, null, true, psl)
        assertTrue(policy.isActive)
        assertEquals(0, policy.blockedCount)
        assertFalse(policy.disabled(setOf(com.dwarpallabs.linkguard.core.CheckId.REPUTATION)).isNotEmpty())
    }

    @Test
    fun restrictionKeysMatchTheXml() {
        val xml = java.io.File("src/main/res/xml/app_restrictions.xml").readText()
        listOf(ManagedConfig.KEY_BLOCKED, ManagedConfig.KEY_ALLOWED, ManagedConfig.KEY_KEEP_CHECKS_ON).forEach {
            assertTrue("$it missing from app_restrictions.xml", xml.contains("android:key=\"$it\""))
        }
    }
}
