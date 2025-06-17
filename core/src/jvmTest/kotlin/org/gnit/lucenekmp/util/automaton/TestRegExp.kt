package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.util.Locale
import kotlin.random.Random
import org.gnit.lucenekmp.jdkport.appendCodePoint
import org.gnit.lucenekmp.tests.util.LuceneTestCase

@Ignore
class TestRegExp : LuceneTestCase() {

    /** Simple smoke test for regular expression. */
    @Test
    fun testSmoke() {
        val r = RegExp("a(b+|c+)d")
        val a = r.toAutomaton()!!
        assertTrue(a.isDeterministic)
        val run = ByteRunAutomaton(a)
        assertTrue(run.run("abbbbbd".encodeToByteArray(), 0, "abbbbbd".length))
        assertTrue(run.run("acd".encodeToByteArray(), 0, "acd".length))
        assertFalse(run.run("ad".encodeToByteArray(), 0, "ad".length))
    }

    @Test
    fun testUnicodeAsciiInsensitiveFlags() {
        var r: RegExp

        // ASCII behaves appropriately with different flags
        r = RegExp("A")
        assertFalse(ByteRunAutomaton(r.toAutomaton()!!).run("a".encodeToByteArray(), 0, 1))

        r = RegExp("A", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("a".encodeToByteArray(), 0, 1))

        r = RegExp("A", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("a".encodeToByteArray(), 0, 1))

        r = RegExp("A", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("a".encodeToByteArray(), 0, 1))

        // class 1 Unicode characters behaves appropriately with different flags
        r = RegExp("Σ")
        assertFalse(ByteRunAutomaton(r.toAutomaton()!!).run("σ".encodeToByteArray(), 0, 1))
        assertFalse(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("σ")
        assertFalse(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("Σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("σ".encodeToByteArray(), 0, 1))
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("Σ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("σ".encodeToByteArray(), 0, 1))
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("σ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("Σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("σ".encodeToByteArray(), 0, 1))
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        r = RegExp("σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ς".encodeToByteArray(), 0, 1))

        // class 2 Unicode characters behaves appropriately with different flags
        r = RegExp("ῼ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(ByteRunAutomaton(r.toAutomaton()!!).run("ῳ".encodeToByteArray(), 0, 1))

        r = RegExp("ῼ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertFalse(
            ByteRunAutomaton(r.toAutomaton()!!).run("ῼ".uppercase(Locale.ROOT).encodeToByteArray(), 0, "ῼ".uppercase(Locale.ROOT).length)
        )

        // class 3 Unicode characters behaves appropriately with different flags
        r = RegExp("ﬗ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertFalse(
            ByteRunAutomaton(r.toAutomaton()!!).run("ﬗ".uppercase(Locale.ROOT).encodeToByteArray(), 0, "ﬗ".uppercase(Locale.ROOT).length)
        )

        r = RegExp("\uD802\uDC8A", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(
            ByteRunAutomaton(r.toAutomaton()!!).run(
                "\uD802\uDC89".encodeToByteArray(),
                0,
                "\uD802\uDC89".length
            )
        )
    }

    @Test
    fun testRandomUnicodeInsensitiveMatchPatternParity() {
        val reserved = setOf(
            '.', '^', '$', '*', '+', '?', '(', ')', '[', '{', '\\', '|', '-', '"', '<', '>', '#', '@', '&', '~'
        ).map { it.code }.toSet()
        val maxIters = 1000
        for (i in 0 until maxIters) {
            val code1 = Random.nextInt(0, org.gnit.lucenekmp.jdkport.Character.MAX_CODE_POINT + 1)
            val code2 = Random.nextInt(0, org.gnit.lucenekmp.jdkport.Character.MAX_CODE_POINT + 1)
            if (reserved.contains(code1)) {
                continue
            }

            val pattern = StringBuilder().appendCodePoint(code1).toString()
            val altString = StringBuilder().appendCodePoint(code2).toString()

            val javaRegex = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE)
            val r = RegExp(pattern, RegExp.ALL, RegExp.CASE_INSENSITIVE)
            val cra = CharacterRunAutomaton(r.toAutomaton()!!)

            if (javaRegex.matcher(altString).matches()) {
                val msg = "Pattern and RegExp disagree on pattern: " +
                    code1.toString(16).uppercase().padStart(4, '0') +
                    " :text: " + code2.toString(16).uppercase().padStart(4, '0')
                assertTrue(cra.run(altString), msg)
            }
        }
    }

    @Test
    fun testUnicodeInsensitiveMatchPatternParity() {
        for (codepoint in 0..org.gnit.lucenekmp.jdkport.Character.MAX_CODE_POINT) {
            val alts = CaseFolding.lookupAlternates(codepoint)
            if (alts != null) {
                val pattern = StringBuilder().appendCodePoint(codepoint).toString()
                val javaRegex = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE)
                val r = RegExp(pattern, RegExp.ALL, RegExp.CASE_INSENSITIVE)
                val cra = CharacterRunAutomaton(r.toAutomaton()!!)
                for (alt in alts) {
                    val altString = StringBuilder().appendCodePoint(alt).toString()
                    if (javaRegex.matcher(altString).matches()) {
                        assertTrue(cra.run(altString))
                    }
                }
            }
        }
    }

    @Test
    fun testRepeatWithEmptyString() {
        val a = RegExp("[^y]*{1,2}").toAutomaton()
        assertTrue(a.toString().isNotEmpty())
    }
}
