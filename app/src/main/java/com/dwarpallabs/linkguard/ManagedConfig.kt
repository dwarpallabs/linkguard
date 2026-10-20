package com.dwarpallabs.linkguard

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import com.dwarpallabs.linkguard.core.ManagedPolicy
import com.dwarpallabs.linkguard.core.PublicSuffixList

/**
 * Managed configuration (roadmap C5): what an organisation's device management sets for LinkGuard,
 * declared in `res/xml/app_restrictions.xml`. Android delivers it; LinkGuard only reads it, keeps
 * it in memory and never sends it anywhere.
 */
object ManagedConfig {

    const val KEY_BLOCKED = "blocked_websites"
    const val KEY_ALLOWED = "allowed_websites"
    const val KEY_KEEP_CHECKS_ON = "keep_checks_on"

    /** Reads the current configuration. A binder call: off the main thread when it can be. */
    fun read(context: Context, psl: PublicSuffixList): ManagedPolicy {
        val restrictions = context.getSystemService(RestrictionsManager::class.java)
            ?.applicationRestrictions ?: return ManagedPolicy.NONE
        return from(restrictions, psl)
    }

    fun from(bundle: Bundle, psl: PublicSuffixList): ManagedPolicy {
        if (bundle.isEmpty) return ManagedPolicy.NONE
        return from(bundle.getString(KEY_BLOCKED), bundle.getString(KEY_ALLOWED), bundle.getBoolean(KEY_KEEP_CHECKS_ON), psl)
    }

    fun from(blocked: String?, allowed: String?, keepChecksOn: Boolean, psl: PublicSuffixList): ManagedPolicy {
        val policy = ManagedPolicy(ManagedPolicy.parseList(blocked, psl), ManagedPolicy.parseList(allowed, psl), keepChecksOn)
        return if (policy.isActive) policy else ManagedPolicy.NONE
    }
}
