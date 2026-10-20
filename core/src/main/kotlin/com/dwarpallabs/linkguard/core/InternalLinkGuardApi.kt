package com.dwarpallabs.linkguard.core

/**
 * Marks declarations LinkGuard's own tools (the feeds pipeline) use but that are not part of the
 * library's stable API: they may change or disappear in any release.
 */
@RequiresOptIn(
    message = "LinkGuard-internal API: it may change without notice.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class InternalLinkGuardApi
