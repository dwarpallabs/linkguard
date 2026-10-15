package com.dwarpallabs.linkguard.core

/**
 * Tiny JSON reader for RDAP responses and the RDAP bootstrap file, so `core` needs no JSON library.
 * Produces Map<String, Any?>, List<Any?>, String, Double, Boolean or null.
 */
internal class MiniJson private constructor(private val text: String) {

    private var pos = 0

    private fun value(): Any? {
        skipSpace()
        return when (val c = peek()) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c.isDigit()) number() else fail("unexpected '$c'")
        }
    }

    private fun obj(): Map<String, Any?> {
        expect('{')
        val result = LinkedHashMap<String, Any?>()
        skipSpace()
        if (peek() == '}') { pos++; return result }
        while (true) {
            skipSpace()
            val key = string()
            skipSpace()
            expect(':')
            result[key] = value()
            skipSpace()
            when (next()) {
                ',' -> continue
                '}' -> return result
                else -> fail("expected , or }")
            }
        }
    }

    private fun array(): List<Any?> {
        expect('[')
        val result = ArrayList<Any?>()
        skipSpace()
        if (peek() == ']') { pos++; return result }
        while (true) {
            result += value()
            skipSpace()
            when (next()) {
                ',' -> continue
                ']' -> return result
                else -> fail("expected , or ]")
            }
        }
    }

    private fun string(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            when (val c = next()) {
                '"' -> return sb.toString()
                '\\' -> when (val e = next()) {
                    'u' -> {
                        if (pos + 4 > text.length) fail("bad escape")
                        sb.append(text.substring(pos, pos + 4).toInt(16).toChar())
                        pos += 4
                    }
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    else -> sb.append(e)
                }
                else -> sb.append(c)
            }
        }
    }

    private fun number(): Double {
        val start = pos
        while (pos < text.length && text[pos] in "+-0123456789.eE") pos++
        return text.substring(start, pos).toDoubleOrNull() ?: fail("bad number")
    }

    private fun literal(word: String, result: Any?): Any? {
        if (!text.startsWith(word, pos)) fail("expected $word")
        pos += word.length
        return result
    }

    private fun skipSpace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun peek(): Char = if (pos < text.length) text[pos] else fail("unexpected end")
    private fun next(): Char = peek().also { pos++ }
    private fun expect(c: Char) { if (next() != c) fail("expected '$c'") }
    private fun fail(message: String): Nothing = throw IllegalArgumentException("JSON: $message at $pos")

    companion object {
        /** Parses [text], or returns null if it is not valid JSON. */
        fun parse(text: String): Any? = try {
            MiniJson(text).value()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
