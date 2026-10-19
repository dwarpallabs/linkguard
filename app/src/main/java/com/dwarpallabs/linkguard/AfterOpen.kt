package com.dwarpallabs.linkguard

import android.content.Context
import androidx.core.content.edit

/**
 * One pending "Was that link a scam?" question, asked on the next app open after the user tapped
 * Open anyway on a warning. Holds the website's registrable domain, when it was opened and which
 * checks fired. Never the link, its path, query or fragment.
 */
data class PendingQuestion(val domain: String, val openedAt: Long, val reasons: List<String>) {

    fun encode(): String = "$domain\n$openedAt\n${reasons.joinToString(",")}"

    companion object {
        private val DOMAIN = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
        private val REASON = Regex("[a-z0-9._-]{1,64}")

        fun isValidDomain(domain: String): Boolean = domain.length <= 253 && DOMAIN.matches(domain)

        fun isValidReason(reason: String): Boolean = REASON.matches(reason)

        /** Reads back what [encode] wrote; anything malformed is treated as no question. */
        fun decode(stored: String?): PendingQuestion? {
            val parts = stored?.split('\n') ?: return null
            if (parts.size != 3) return null
            val domain = parts[0].takeIf(::isValidDomain) ?: return null
            val openedAt = parts[1].toLongOrNull() ?: return null
            val reasons = parts[2].split(',').filter { it.isNotEmpty() }
            if (!reasons.all(::isValidReason)) return null
            return PendingQuestion(domain, openedAt, reasons)
        }
    }
}

/** The two values [AfterOpenStore] keeps. SharedPreferences in the app, a map in tests. */
interface KeyValue {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/**
 * Remembers at most one website the user opened anyway, so LinkGuard can ask about it once.
 *
 * Stored in its own file, excluded from backup and device transfer (res/xml rules). The question
 * is deleted when answered and expires after [MAX_AGE_MS]. Websites already asked about are kept
 * only as HMACs (with the phone-only reporter secret), so the file never lists them by name.
 */
class AfterOpenStore(
    private val kv: KeyValue,
    private val token: ReporterToken,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Replaces any earlier question. Does nothing for a website already asked about. */
    fun remember(domain: String, reasons: List<String>) {
        val d = domain.lowercase()
        if (!PendingQuestion.isValidDomain(d)) return
        if (token.forDomain(d) in asked()) return
        kv.put(KEY_PENDING, PendingQuestion(d, now(), reasons.filter(PendingQuestion::isValidReason)).encode())
    }

    /** The question to ask now, or null. A question older than [MAX_AGE_MS] is deleted instead. */
    fun pending(): PendingQuestion? {
        val q = PendingQuestion.decode(kv.get(KEY_PENDING))
        val age = q?.let { now() - it.openedAt }
        if (q == null || age == null || age !in 0..MAX_AGE_MS) {
            if (kv.get(KEY_PENDING) != null) kv.put(KEY_PENDING, null)
            return null
        }
        return q
    }

    /** The user answered (any answer): delete the question and never ask about that website again. */
    fun answer(question: PendingQuestion) {
        if (pending()?.domain == question.domain) kv.put(KEY_PENDING, null)
        val hash = token.forDomain(question.domain)
        val asked = asked().filter { it != hash } + hash
        kv.put(KEY_ASKED, asked.takeLast(MAX_ASKED).joinToString("\n"))
    }

    private fun asked(): List<String> = kv.get(KEY_ASKED)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    companion object {
        const val FILE = "after_open"
        const val KEY_PENDING = "pending"
        const val KEY_ASKED = "asked"
        const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
        const val MAX_ASKED = 200

        fun of(context: Context): AfterOpenStore {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val kv = object : KeyValue {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun put(key: String, value: String?) = prefs.edit { if (value == null) remove(key) else putString(key, value) }
            }
            return AfterOpenStore(kv, ReporterToken.of(Prefs(context)))
        }
    }
}
