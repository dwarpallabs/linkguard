package com.dwarpallabs.linkguard

import com.dwarpallabs.linkguard.core.NormalisedUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends one user report, from the Settings form, the warning screen or the "Was that link a scam?"
 * card, and only because the user tapped to send it. What leaves the phone: the link (from the
 * warning screen and the card, only the website, see [websiteLink]), the note, the app version,
 * the website's registrable domain and an anonymous per-website token. Nothing is stored.
 */
class Reporter(private val app: LinkGuardApp) {

    suspend fun send(link: String, note: String): Boolean = withContext(Dispatchers.IO) {
        val domain = reportDomain(NormalisedUrl.parse(link, app.data.psl))
        // The token lets the server count distinct reporters per website without being able to
        // link one phone's reports together.
        val token = domain?.let { ReporterToken.of(Prefs(app)).forDomain(it) }
        ReportClient(app.http).send(link, note, appVersion(), domain, token)
    }

    private fun appVersion(): String =
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"

    companion object {
        /** The link sent for a website the user did not type: scheme and website only, nothing else from the link. */
        fun websiteLink(domain: String): String = "https://$domain/"


        /** The website a report counts against: its registrable domain, else the host; never an IP address. */
        fun reportDomain(parsed: NormalisedUrl?): String? =
            parsed?.registrableDomain ?: parsed?.host?.takeIf { parsed.isWeb && !parsed.isIpLiteral }
    }
}
