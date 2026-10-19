package com.dwarpallabs.linkguard

import android.content.Context
import androidx.core.content.edit

/**
 * What LinkGuard learns about the user on the phone, and never sends anywhere (roadmap A6):
 *
 * - **Trusted by me**: websites the user answered "No, it was fine" about. They no longer get the
 *   soft "Be careful" warning, but always the strong one, and always app-download and UPI warnings.
 * - **My brands**: brands whose real website the user opened [MY_BRAND_OPENS] times. A fake of one
 *   of them gets a stronger warning: "…pretending to be SBI, which you use".
 *
 * Both are kept only as HMACs with the phone-only reporter secret (see [ReporterToken]), in their
 * own file excluded from backup and device transfer. Brands are shown again by hashing the known
 * brand names; trusted websites can only be counted and cleared, never listed. No links, no dates.
 */
class LocalMemory(private val kv: KeyValue, private val token: ReporterToken) {

    fun trust(domain: String) {
        val hash = token.forDomain(domain.lowercase())
        save(KEY_TRUSTED, (trusted() - hash + hash).takeLast(MAX_TRUSTED))
    }

    fun untrust(domain: String) {
        val hash = token.forDomain(domain.lowercase())
        save(KEY_TRUSTED, trusted() - hash)
    }

    fun isTrusted(domain: String): Boolean = token.forDomain(domain.lowercase()) in trusted()

    fun trustedCount(): Int = trusted().size

    fun clearTrusted() = kv.put(KEY_TRUSTED, null)

    /** One more open of [brand]'s real website. Returns true when this made it one of "my brands". */
    fun countOpen(brand: String): Boolean {
        val key = brandKey(brand)
        val counts = brandCounts()
        val before = counts[key] ?: 0
        if (before >= MY_BRAND_OPENS) return false
        counts[key] = before + 1
        kv.put(KEY_BRANDS, counts.entries.joinToString("\n") { "${it.key}=${it.value}" })
        return before + 1 == MY_BRAND_OPENS
    }

    /** The user's brands among [allBrands] (names from the bundled brand list). */
    fun myBrands(allBrands: List<String>): Set<String> {
        val counts = brandCounts()
        return allBrands.filter { (counts[brandKey(it)] ?: 0) >= MY_BRAND_OPENS }.toSet()
    }

    fun clearBrands() = kv.put(KEY_BRANDS, null)

    /** The brand tip after Go back (roadmap B6) is shown at most once per brand per [TIP_INTERVAL_S]. */
    fun shouldShowBrandTip(brand: String, nowSeconds: Long): Boolean {
        val last = tips()[tipKey(brand)] ?: return true
        return nowSeconds - last >= TIP_INTERVAL_S || nowSeconds < last
    }

    fun markBrandTipShown(brand: String, nowSeconds: Long) {
        val tips = tips()
        tips.remove(tipKey(brand))
        tips[tipKey(brand)] = nowSeconds
        kv.put(KEY_TIPS, tips.entries.toList().takeLast(MAX_TIPS).joinToString("\n") { "${it.key}=${it.value}" })
    }

    private fun tipKey(brand: String) = token.forDomain("tip:$brand")

    private fun tips(): MutableMap<String, Long> = kv.get(KEY_TIPS)?.split('\n').orEmpty()
        .mapNotNull { line ->
            val (k, v) = line.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            if (!HASH.matches(k)) return@mapNotNull null
            v.toLongOrNull()?.let { k to it }
        }.toMap(LinkedHashMap())

    private fun brandKey(brand: String) = token.forDomain("brand:$brand")

    private fun trusted(): List<String> = kv.get(KEY_TRUSTED)?.split('\n')?.filter { HASH.matches(it) }.orEmpty()

    private fun brandCounts(): MutableMap<String, Int> = kv.get(KEY_BRANDS)?.split('\n').orEmpty()
        .mapNotNull { line ->
            val (k, v) = line.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            if (!HASH.matches(k)) return@mapNotNull null
            v.toIntOrNull()?.let { k to it.coerceIn(0, MY_BRAND_OPENS) }
        }.toMap(LinkedHashMap())

    private fun save(key: String, values: List<String>) = kv.put(key, values.joinToString("\n").ifEmpty { null })

    companion object {
        const val FILE = "local_memory"
        const val KEY_TRUSTED = "trusted"
        const val KEY_BRANDS = "brands"
        const val KEY_TIPS = "tips"
        const val TIP_INTERVAL_S = 30 * 24 * 60 * 60L
        const val MAX_TIPS = 100

        const val MY_BRAND_OPENS = 3
        const val MAX_TRUSTED = 500
        private val HASH = Regex("^[0-9a-f]{64}$")

        fun of(context: Context): LocalMemory {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val kv = object : KeyValue {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun put(key: String, value: String?) = prefs.edit { if (value == null) remove(key) else putString(key, value) }
            }
            return LocalMemory(kv, ReporterToken.of(Prefs(context)))
        }
    }
}
