package com.dwarpallabs.linkguard

import android.content.Context
import androidx.core.content.edit
import com.dwarpallabs.linkguard.core.CheckId

/**
 * App settings. Holds only user choices, never URLs or any history of opened links.
 */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

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

    fun setCheckEnabled(check: CheckId, enabled: Boolean) {
        disabledChecks = if (enabled) disabledChecks - check else disabledChecks + check
    }

    private companion object {
        const val KEY_FORWARD_TO = "forward_to"
        const val KEY_DISABLED_CHECKS = "disabled_checks"
        const val KEY_ALLOWLIST_CHECKED_AT = "allowlist_checked_at"
    }
}
