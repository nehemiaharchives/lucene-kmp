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

    @Test
    fun testRepeatWithEmptyLanguage() {
        var a = RegExp("#*").toAutomaton()
        assertTrue(a.toString().isNotEmpty())
        a = RegExp("#+").toAutomaton()
        assertTrue(a.toString().isNotEmpty())
        a = RegExp("#{2,10}").toAutomaton()
        assertTrue(a.toString().isNotEmpty())
        a = RegExp("#?").toAutomaton()
        assertTrue(a.toString().isNotEmpty())
    }

    var caseSensitiveQuery = true
    var unicodeCaseQuery = true

    @Test
    fun testCoreJavaParity() {
        for (i in 0 until 1000) {
            caseSensitiveQuery = true
            checkRandomExpression(randomDocValue(1 + Random.nextInt(30), false))
        }

        for (i in 0 until 1000) {
            caseSensitiveQuery = true
            unicodeCaseQuery = true
            checkRandomExpression(randomDocValue(1 + Random.nextInt(30), true))
        }
    }

    @Test
    fun testIllegalBackslashChars() {
        val illegalChars = "abcefghijklmnopqrtuvxyzABCEFGHIJKLMNOPQRTUVXYZ"
        for (ch in illegalChars) {
            val illegalExpression = "\\" + ch
            val expected = expectThrows(IllegalArgumentException::class) {
                RegExp(illegalExpression)
            }
            assertTrue(expected!!.message!!.contains("invalid character class"))
        }
    }

    @Test
    fun testLegalBackslashChars() {
        val legalChars = "dDsSWw0123456789[]*&^$@!{}\\/"
        for (ch in legalChars) {
            val legalExpression = "\\" + ch
            RegExp(legalExpression)
        }
    }

    private fun randomDocValue(minLength: Int, includeUnicode: Boolean): String {
        var charPalette = "AAAaaaBbbCccc123456 \t"
        if (includeUnicode) {
            charPalette += "Σσςῼῳ"
        }
        val sb = StringBuilder()
        for (i in 0 until minLength) {
            sb.append(charPalette[randomInt(charPalette.length - 1)])
        }
        return sb.toString()
    }

    private fun randomInt(bound: Int): Int {
        return if (bound == 0) 0 else Random.nextInt(bound)
    }

    private fun checkRandomExpression(docValue: String): String {
        val result = StringBuilder()
        val substitutionPoint = randomInt(docValue.length - 1)
        val substitutionLength = 1 + randomInt(kotlin.math.min(10, docValue.length - substitutionPoint))

        if (substitutionPoint > 0) {
            result.append(docValue, 0, substitutionPoint)
        }

        val replacementPart = docValue.substring(substitutionPoint, substitutionPoint + substitutionLength)
        when (Random.nextInt(15)) {
            0 -> result.append("(" + replacementPart + "|d" + randomDocValue(replacementPart.length, false) + ")")
            1 -> result.append("(" + replacementPart + "|doesnotexist)")
            2 -> result.append("(" + checkRandomExpression(replacementPart) + "|doesnotexist)")
            3 -> result.append(replacementPart.replace("ab", ".*"))
            4 -> result.append(replacementPart.replace("b", "."))
            5 -> result.append(".{1," + replacementPart.length + "}")
            6 -> result.append(".".repeat(replacementPart.length))
            7 -> {
                val chars = replacementPart.toCharArray()
                for (c in chars) {
                    result.append("[" + c + org.gnit.lucenekmp.jdkport.Character.toUpperCase(c.code).toChar() + "]")
                }
            }
            8 -> result.append(replacementPart.replace("b", "[^a]"))
            9 -> result.append("(" + replacementPart + ")+")
            10 -> result.append("(" + replacementPart + ")?")
            11 -> result.append(replacementPart.replace(Regex("\\d"), "\\\\d"))
            12 -> result.append(replacementPart.replace(Regex("\\s"), "\\\\W"))
            13 -> result.append(replacementPart.replace(Regex("\\s"), "\\\\s"))
            14 -> {
                val switchedCase = StringBuilder()
                var i = 0
                while (i < replacementPart.length) {
                    val cp = org.gnit.lucenekmp.jdkport.Character.codePointAt(replacementPart, i)
                    var switchedP = cp
                    switchedP = if (org.gnit.lucenekmp.jdkport.Character.isLowerCase(cp)) {
                        org.gnit.lucenekmp.jdkport.Character.toUpperCase(cp)
                    } else {
                        org.gnit.lucenekmp.jdkport.Character.toLowerCase(cp)
                    }
                    switchedCase.appendCodePoint(switchedP)
                    if (cp != switchedP) {
                        caseSensitiveQuery = false
                    }
                    i += org.gnit.lucenekmp.jdkport.Character.charCount(cp)
                }
                result.append(switchedCase.toString())
            }
        }

        if (substitutionPoint + substitutionLength <= docValue.length - 1) {
            result.append(docValue.substring(substitutionPoint + substitutionLength))
        }

        val regexPattern = result.toString()
        val pattern = if (caseSensitiveQuery) {
            java.util.regex.Pattern.compile(regexPattern)
        } else {
            java.util.regex.Pattern.compile(
                regexPattern,
                java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
            )
        }
        val matcher = pattern.matcher(docValue)
        assertTrue(matcher.matches(), "Java regex $regexPattern did not match doc value $docValue")

        val matchFlags = if (caseSensitiveQuery) 0 else RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE
        val regex = RegExp(regexPattern, RegExp.ALL, matchFlags)
        val automaton = Operations.determinize(regex.toAutomaton()!!, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val bytesMatcher = ByteRunAutomaton(automaton)
        val br = newBytesRef(docValue)
        assertTrue(
            bytesMatcher.run(br.bytes, br.offset, br.length),
            "[" + regexPattern + "]should match [" + docValue + "]" + substitutionPoint + "-" + substitutionLength + "/" + docValue.length
        )
        if (!caseSensitiveQuery) {
            var csAutomaton = RegExp(regexPattern).toAutomaton()!!
            csAutomaton = Operations.determinize(csAutomaton, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            val csBytesMatcher = ByteRunAutomaton(csAutomaton)
            assertFalse(
                csBytesMatcher.run(br.bytes, br.offset, br.length),
                "[" + regexPattern + "] with case sensitive setting should not match [" + docValue + "]"
            )
        }
        return regexPattern
    }
}
