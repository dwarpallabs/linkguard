package com.dwarpallabs.linkguard

import android.content.Context
import androidx.core.content.edit
import com.dwarpallabs.linkguard.core.CheckId

/**
 * App settings. Holds only user choices, never URLs or any history of opened links.
 */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Kept apart from settings so backup and device transfer can exclude it (res/xml rules). */
    private val secrets = context.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)

    /** Package name of the browser that receives forwarded links, or null for the default pick. */
    var forwardTo: String?
        get() = prefs.getString(KEY_FORWARD_TO, null)
        set(value) = prefs.edit { putString(KEY_FORWARD_TO, value) }

    /** Checks the user switched off in Settings. Everything is on by default. */
    var disabledChecks: Set<CheckId>
        get() = prefs.getStringSet(KEY_DISABLED_CHECKS, emptySet()).orEmpty()
            .mapNotNull { name -> CheckId.entries.firstOrNull { it.name == name } }
            .toSet()
        set(value) = prefs.edit { putStringSet(KEY_DISABLED_CHECKS, value.map { it.name }.toSet()) }

    /** When the allowlist download was last checked (epoch millis). List housekeeping only. */
    var allowlistCheckedAt: Long
        get() = prefs.getLong(KEY_ALLOWLIST_CHECKED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_ALLOWLIST_CHECKED_AT, value) }

    /**
     * Random per-install secret for anonymous reporter tokens (see [ReporterToken]). Never sent
     * anywhere; only HMACs derived from it leave the phone, one per reported website. Lives in
     * [SECRETS_FILE], which is never backed up or copied to a new phone.
     */
    var reporterSecret: String?
        get() {
            // Versions up to 0.3.0 kept it in settings, which is backed up: move it out once.
            prefs.getString(KEY_REPORTER_SECRET, null)?.let { old ->
                if (!secrets.contains(KEY_REPORTER_SECRET)) secrets.edit(commit = true) { putString(KEY_REPORTER_SECRET, old) }
                prefs.edit(commit = true) { remove(KEY_REPORTER_SECRET) }
            }
            return secrets.getString(KEY_REPORTER_SECRET, null)
        }
        set(value) = secrets.edit { putString(KEY_REPORTER_SECRET, value) }

    fun setCheckEnabled(check: CheckId, enabled: Boolean) {
        disabledChecks = if (enabled) disabledChecks - check else disabledChecks + check
    }

    private companion object {
        const val SECRETS_FILE = "reporter"
        const val KEY_FORWARD_TO = "forward_to"
        const val KEY_DISABLED_CHECKS = "disabled_checks"
        const val KEY_ALLOWLIST_CHECKED_AT = "allowlist_checked_at"
        const val KEY_REPORTER_SECRET = "reporter_secret"
    }
}
