package com.dwarpallabs.linkguard

import com.dwarpallabs.linkguard.ApkInspector.Capability
import com.dwarpallabs.linkguard.core.EngineData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Roadmap B3: what an app could do, in plain words, and the banking-trojan pattern. */
class ApkInspectorTest {

    private val p = "android.permission."

    @Test
    fun permissionsBecomeCapabilitiesInReadingOrder() {
        val caps = ApkInspector.capabilities(
            requested = listOf("${p}INTERNET", "${p}READ_CONTACTS", "${p}RECEIVE_SMS", "${p}SYSTEM_ALERT_WINDOW", "${p}CALL_PHONE"),
            servicePermissions = listOf("${p}BIND_ACCESSIBILITY_SERVICE"),
            receiverPermissions = listOf("${p}BIND_DEVICE_ADMIN", "${p}BROADCAST_SMS"),
        )
        assertEquals(
            listOf(Capability.READ_SMS, Capability.SMS_APP, Capability.ACCESSIBILITY, Capability.OVERLAY, Capability.DEVICE_ADMIN, Capability.CONTACTS, Capability.CALLS),
            caps,
        )
    }

    @Test
    fun anOrdinaryAppHasNothingUnusual() {
        assertEquals(emptyList<Capability>(), ApkInspector.capabilities(listOf("${p}INTERNET", "${p}CAMERA"), emptyList(), emptyList()))
    }

    @Test
    fun aBrandNamedAppWantingSmsOrAccessibilityIsTheTrap() {
        val brands = EngineData.loadBundled().brands
        assertEquals("SBI", ApkInspector.brandTrap("SBI YONO KYC Update", "app.apk", listOf(Capability.READ_SMS), brands))
        assertEquals("HDFC Bank", ApkInspector.brandTrap(null, "HDFC-Reward-Points.apk", listOf(Capability.ACCESSIBILITY), brands))
        // Same name, but it asks for nothing dangerous: no trap.
        assertNull(ApkInspector.brandTrap("SBI YONO", "yono.apk", listOf(Capability.CONTACTS), brands))
        // Dangerous, but no brand name.
        assertNull(ApkInspector.brandTrap("Free Wallpapers", "wall.apk", listOf(Capability.READ_SMS), brands))
    }
}
