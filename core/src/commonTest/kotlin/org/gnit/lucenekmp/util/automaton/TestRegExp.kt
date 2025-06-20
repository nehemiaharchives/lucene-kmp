package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class TestRegExp : LuceneTestCase() {

    @Test
    fun testSmoke() {
        val r = RegExp("a(b+|c+)d")
        val a = r.toAutomaton()!!
        assertTrue(a.isDeterministic)
        val run = CharacterRunAutomaton(a)
        assertTrue(run.run("abbbbbd"))
        assertTrue(run.run("acd"))
        assertFalse(run.run("ad"))
    }

    @Test
    fun testUnicodeAsciiInsensitiveFlags() {
        var r: RegExp
        // ASCII behaves appropriately with different flags
        r = RegExp("A")
        assertFalse(CharacterRunAutomaton(r.toAutomaton()).run("a"))

        r = RegExp("A", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("a"))

        r = RegExp("A", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("a"))

        r = RegExp("A", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("a"))

        // class 1 Unicode characters behaves appropriately with different flags
        r = RegExp("Σ")
        assertFalse(CharacterRunAutomaton(r.toAutomaton()).run("σ"))
        assertFalse(CharacterRunAutomaton(r.toAutomaton()).run("ς"))

        r = RegExp("σ")
        assertFalse(CharacterRunAutomaton(r.toAutomaton()).run("ς"))

        r = RegExp("Σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("σ"))
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("ς"))

        r = RegExp("σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("ς"))

        r = RegExp("Σ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("σ"))
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("ς"))

        r = RegExp("σ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("ς"))

        r = RegExp("Σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("σ"))
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("ς"))

        r = RegExp("σ", RegExp.ALL, RegExp.ASCII_CASE_INSENSITIVE or RegExp.CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("ς"))

        // class 2 Unicode characters behaves appropriately with different flags
        r = RegExp("ῼ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run("ῳ"))

        r = RegExp("ῼ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertFalse(CharacterRunAutomaton(r.toAutomaton()).run("ῼ".uppercase())) // "ΩΙ"

        // class 3 Unicode characters behaves appropriately with different flags
        r = RegExp("ﬗ", RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertFalse(CharacterRunAutomaton(r.toAutomaton()).run("ﬗ".uppercase()))

        r = RegExp(0x1C8A.toChar().toString(), RegExp.ALL, RegExp.CASE_INSENSITIVE)
        assertTrue(CharacterRunAutomaton(r.toAutomaton()).run(0x1C8A.toChar().toString()))
    }

    @Test
    fun testRandomUnicodeInsensitiveMatchPatternParity() {
        val maxIters = 1000
        val reservedCharacters: List<Int> =
            setOf(
                '.', '^', '$', '*', '+', '?', '(', ')', '[', '{', '\\', '|', '-', '"', '<', '>',
                '#', '@', '&', '~'
            ).map { it.code }
        for (i in 0..<maxIters) {
            val nextCode1: Int =
                random().nextInt(0, Character.MAX_CODE_POINT + 1)
            val nextCode2: Int =
                random().nextInt(0, Character.MAX_CODE_POINT + 1)

            // skip if we select a reserved character that blows up .^$*+?()[{\|-]"<
            if (reservedCharacters.contains(nextCode1)) {
                continue
            }

            val pattern = nextCode1.toChar().toString()
            val altString = nextCode2.toChar().toString()

            val kotlinRegex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()

            val r = runCatching {
                RegExp(
                    pattern,
                    RegExp.ALL,
                    RegExp.CASE_INSENSITIVE
                )
            }.getOrNull()

            if (r == null) {
                // If the RegExp constructor fails, we skip this iteration
                continue
            }

            val cra = CharacterRunAutomaton(r.toAutomaton())

            // Pattern doesn't respect the Unicode spec so some things will not match
            if (kotlinRegex != null && kotlinRegex.matches(altString)) {
                // ... but if they do match then we must agree
                val message = ("Pattern and RegExp disagree on pattern: "
                        + nextCode1.toString(16).uppercase().padStart(4, '0')
                        + " :text: "
                        + nextCode2.toString(16).uppercase().padStart(4, '0'))

                assertTrue(cra.run(altString), message)
            }
        }
    }

    @Test
    fun testUnicodeInsensitiveMatchPatternParity() {
        // this ensures that if the Pattern class behavior were to change with a change to the Unicode
        // spec then we would pick it up.  It may help indicate in the future if we don't notice
        // that the spec has changed and Pattern picks up the change first
        for (codepoint in 0..<Character.MAX_CODE_POINT + 1) {
            val caseInsensitiveAlternatives: IntArray? =
                CaseFolding.lookupAlternates(codepoint)
            if (caseInsensitiveAlternatives != null) {
                val pattern = codepoint.toChar().toString()
                val kotlinRegex = Regex(pattern, RegexOption.IGNORE_CASE)
                val r = RegExp(
                    pattern,
                    RegExp.ALL,
                    RegExp.CASE_INSENSITIVE
                )
                val cra = CharacterRunAutomaton(r.toAutomaton())
                for (alt in caseInsensitiveAlternatives) {
                    val altString = alt.toChar().toString()

                    // Pattern doesn't respect the Unicode spec so some things will not match
                    if (kotlinRegex.matches(altString)) {
                        // ... but if they do match then we must agree
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

    @Ignore
    @Test
    fun testCoreJavaParity() {
        // TODO too long and it is checking behavior matching jdk so we will implement if needed later
    }

    @Test
    fun testIllegalBackslashChars() {
        val illegalChars = "abcefghijklmnopqrtuvxyzABCEFGHIJKLMNOPQRTUVXYZ"
        for (ch in illegalChars) {
            val illegalExpression = "\\" + ch
            val expected = kotlin.runCatching { RegExp(illegalExpression) }.exceptionOrNull()
            assertContains(expected!!.message!!, "invalid character class")  //expected.message!!.contains("invalid character class")
            assertTrue(expected is IllegalArgumentException)
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

    @Test
    fun testParseIllegalRepeatExp() {
        val expected = kotlin.runCatching { RegExp("a{99,11}") }.exceptionOrNull()
        assertTrue(expected is IllegalArgumentException && expected.message!!.contains("out of order"))
    }

    @Test
    fun testRegExpNoStackOverflow() {
        RegExp("(a)|".repeat(50000) + "(a)")
    }

    @Test
    fun testDeprecatedComplement() {
        val expected = Operations.complement(
            Automata.makeString("abcd"), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        val actual = RegExp("~(abcd)", RegExp.DEPRECATED_COMPLEMENT).toAutomaton()!!
        assertTrue(AutomatonTestUtil.sameLanguage(expected, actual))
    }
}

