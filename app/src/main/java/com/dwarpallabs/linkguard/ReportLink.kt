package com.dwarpallabs.linkguard

import java.net.URLEncoder

/** Builds the prefilled GitHub "new issue" URL for a user's scam report. Pure, so it is unit-testable. */
object ReportLink {

    private const val NEW_ISSUE = "https://github.com/dwarpallabs/linkguard/issues/new"
    private const val MAX_LINK = 2_000
    private const val MAX_NOTE = 1_000

    fun issueUrl(link: String, note: String, appVersion: String): String {
        val title = "Scam link report"
        val body = buildString {
            append("**Link:** `").append(link.take(MAX_LINK).replace("`", "%60")).append("`\n\n")
            append("**Why it looks like a scam:** ").append(note.take(MAX_NOTE).ifBlank { "_(not given)_" }).append("\n\n")
            append("_Reported from LinkGuard ").append(appVersion).append("_\n")
        }
        return "$NEW_ISSUE?title=${encode(title)}&body=${encode(body)}"
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
