package com.dwarpallabs.linkguard

import android.content.Context
import androidx.core.content.edit

/**
 * App settings. Holds only user choices, never URLs or any history of opened links.
 */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Package name of the browser that receives forwarded links, or null for the default pick. */
    var forwardTo: String?
        get() = prefs.getString(KEY_FORWARD_TO, null)
        set(value) = prefs.edit { putString(KEY_FORWARD_TO, value) }

    private companion object {
        const val KEY_FORWARD_TO = "forward_to"
    }
}
