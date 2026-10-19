package com.dwarpallabs.linkguard.browser

import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken

/**
 * Tells apps that LinkGuard, as the default browser, supports Custom Tabs.
 *
 * Apps like Google Messages and Gmail open links in a Custom Tab of the default browser only if it
 * offers this service; otherwise they pick Chrome directly and LinkGuard never sees the link. With it,
 * the Custom Tab launch comes to LinkInterceptActivity like any other link, is checked, and is then
 * forwarded to the user's real browser as a Custom Tab.
 *
 * Deliberately does nothing else: it never prefetches or even looks at URLs passed to [mayLaunchUrl]
 * (that would contact websites the user has not opened), keeps no session state, and refuses
 * messaging, file and origin-verification features.
 */
class LinkGuardCustomTabsService : CustomTabsService() {

    override fun warmup(flags: Long): Boolean = true

    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean = true

    override fun mayLaunchUrl(sessionToken: CustomTabsSessionToken, url: Uri?, extras: Bundle?, otherLikelyBundles: MutableList<Bundle>?): Boolean = true

    override fun extraCommand(commandName: String, args: Bundle?): Bundle? = null

    override fun updateVisuals(sessionToken: CustomTabsSessionToken, bundle: Bundle?): Boolean = false

    override fun requestPostMessageChannel(sessionToken: CustomTabsSessionToken, postMessageOrigin: Uri): Boolean = false

    override fun postMessage(sessionToken: CustomTabsSessionToken, message: String, extras: Bundle?): Int = RESULT_FAILURE_DISALLOWED

    override fun validateRelationship(sessionToken: CustomTabsSessionToken, relation: Int, origin: Uri, extras: Bundle?): Boolean = false

    override fun receiveFile(sessionToken: CustomTabsSessionToken, uri: Uri, purpose: Int, extras: Bundle?): Boolean = false
}
