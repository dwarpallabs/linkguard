package com.dwarpallabs.linkguard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dwarpallabs.linkguard.core.EngineData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Roadmap B3 on a real device: Android's own parser reads an app file LinkGuard did not install. */
@RunWith(AndroidJUnit4::class)
class ApkInspectorDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val brands = EngineData.loadBundled().brands

    @Test
    fun readsLinkGuardsOwnAppFile() {
        val copy = File(context.cacheDir, "test-copy.apk")
        File(context.packageCodePath).copyTo(copy, overwrite = true)
        try {
            val result = ApkInspector.analyse(copy, "linkguard.apk", "abc", context.packageManager, brands)
            assertTrue(result.toString(), result is ApkInspector.Result.Ok)
            val report = (result as ApkInspector.Result.Ok).report
            assertEquals("LinkGuard", report.label)
            // LinkGuard asks for nothing dangerous.
            assertEquals(emptyList<ApkInspector.Capability>(), report.capabilities)
        } finally {
            copy.delete()
        }
    }

    @Test
    fun aFileThatIsNotAnAppIsSaidSo() {
        val f = File(context.cacheDir, "not-an-app.apk").apply { writeText("<html>not an app</html>") }
        try {
            assertEquals(ApkInspector.Result.NotAnApp, ApkInspector.analyse(f, null, "x", context.packageManager, brands))
        } finally {
            f.delete()
        }
    }
}
