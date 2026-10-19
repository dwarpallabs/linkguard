package com.dwarpallabs.linkguard.browser

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

import com.dwarpallabs.linkguard.LinkInterceptActivity
import com.dwarpallabs.linkguard.Prefs

data class BrowserApp(val packageName: String, val label: String)

/** True if the launch [intent] came from an app opening a Custom Tab (it always carries a session extra). */
fun isCustomTabLaunch(intent: Intent?): Boolean = intent?.hasExtra(CustomTabsIntent.EXTRA_SESSION) == true

/**
 * Everything that touches the system's browser list and default-browser role.
 *
 * Never logs or stores the URLs passed through [forward].
 */
object Browsers {

    // Any generic web URL works here: verified app links only claim specific hosts.
    private val PROBE = "https://example.com/".toUri()

    private fun browserIntent(uri: Uri) =
        Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun query(context: Context, flags: Int): List<ResolveInfo> {
        val pm = context.packageManager
        val intent = browserIntent(PROBE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags)
        }
    }

    /** Package names of installed browsers, excluding LinkGuard. Cheap: no labels loaded. */
    fun installedPackages(context: Context): List<String> =
        query(context, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()

    /** Installed browsers with display labels, for the picker. */
    fun installed(context: Context): List<BrowserApp> {
        val pm = context.packageManager
        return query(context, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { BrowserApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** The browser links will be forwarded to, given the saved choice and what is installed. */
    fun target(context: Context): String? =
        ForwardTargetResolver.resolve(
            saved = Prefs(context).forwardTo,
            installed = installedPackages(context),
            self = context.packageName,
        )

    /** True if [packageName] offers a Custom Tabs service. */
    fun supportsCustomTabs(context: Context, packageName: String): Boolean =
        CustomTabsClient.getPackageName(context, listOf(packageName), true) == packageName

    fun isDefaultBrowser(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                return roles.isRoleHeld(RoleManager.ROLE_BROWSER)
            }
        }
        val resolved = context.packageManager.resolveActivity(
            browserIntent(PROBE), PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Opens [uri] in the chosen browser with an explicit, package-targeted intent so it can never
     * loop back to LinkGuard. Falls back to the system chooser (with LinkGuard excluded) if no
     * other browser is installed or the chosen one cannot open it.
     *
     * Deliberately forwards only the URL: extras from the source app (for example Custom Tabs
     * session binders, which belong to LinkGuard's own no-op service) are dropped. When the app asked
     * for a Custom Tab ([customTab]) and the chosen browser supports them, the link opens in a plain
     * Custom Tab there, so the user still lands back in their app afterwards.
     */
    fun forward(context: Context, uri: Uri, customTab: Boolean = false) {
        val target = target(context)
        if (target != null) {
            try {
                val intent = if (customTab && supportsCustomTabs(context, target)) {
                    CustomTabsIntent.Builder().build().intent.setData(uri).setPackage(target)
                } else {
                    browserIntent(uri).setPackage(target)
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Fall through to the chooser.
            }
        }
        val chooser = Intent.createChooser(browserIntent(uri), null)
            .putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(context, LinkInterceptActivity::class.java)),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
