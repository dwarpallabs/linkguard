package com.dwarpallabs.linkguard.browser

/** Picks which installed browser receives a link. Pure Kotlin so it is unit-testable. */
object ForwardTargetResolver {

    const val CHROME = "com.android.chrome"

    /**
     * Returns the browser package to forward to, or null if no other browser is installed
     * (the caller then falls back to the system chooser).
     *
     * Order: the user's saved choice if still installed, then Chrome, then the first other browser.
     * [self] is always excluded so a link can never loop back into LinkGuard.
     */
    fun resolve(saved: String?, installed: List<String>, self: String): String? {
        val candidates = installed.filter { it != self }
        return when {
            saved != null && saved in candidates -> saved
            CHROME in candidates -> CHROME
            else -> candidates.firstOrNull()
        }
    }
}
