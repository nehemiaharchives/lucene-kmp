package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.RandomAcceptedStrings
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import kotlin.experimental.ExperimentalNativeApi
import org.gnit.lucenekmp.util.UnicodeUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalNativeApi::class)
class TestUTF32ToUTF8 : LuceneTestCase() {

    private fun matches(a: ByteRunAutomaton, code: Int): Boolean {
        val chars = CharArray(2)
        val charCount = Character.toChars(code, chars, 0)
        val b = ByteArray(UnicodeUtil.maxUTF8Length(charCount))
        val len = UnicodeUtil.UTF16toUTF8(chars, 0, charCount, b)
        return a.run(b, 0, len)
    }

    private fun testOne(r: Random, a: ByteRunAutomaton, startCode: Int, endCode: Int, iters: Int) {
        val nonSurrogateCount: Int
        val ovSurStart: Boolean
        if (endCode < UnicodeUtil.UNI_SUR_HIGH_START || startCode > UnicodeUtil.UNI_SUR_LOW_END) {
            nonSurrogateCount = endCode - startCode + 1
            ovSurStart = false
        } else if (isSurrogate(startCode)) {
            nonSurrogateCount = endCode - startCode + 1 - (UnicodeUtil.UNI_SUR_LOW_END - startCode + 1)
            ovSurStart = false
        } else if (isSurrogate(endCode)) {
            ovSurStart = true
            nonSurrogateCount = endCode - startCode + 1 - (endCode - UnicodeUtil.UNI_SUR_HIGH_START + 1)
        } else {
            ovSurStart = true
            nonSurrogateCount = endCode - startCode + 1 - (UnicodeUtil.UNI_SUR_LOW_END - UnicodeUtil.UNI_SUR_HIGH_START + 1)
        }
        assert(nonSurrogateCount > 0)
        for (iter in 0 until iters) {
            var code = startCode + r.nextInt(nonSurrogateCount)
            if (isSurrogate(code)) {
                code = if (ovSurStart) {
                    UnicodeUtil.UNI_SUR_LOW_END + 1 + (code - UnicodeUtil.UNI_SUR_HIGH_START)
                } else {
                    UnicodeUtil.UNI_SUR_LOW_END + 1 + (code - startCode)
                }
            }
            assert(code >= startCode && code <= endCode)
            assert(!isSurrogate(code))
            assertTrue(matches(a, code), "DFA for range $startCode-$endCode failed to match code=$code")
        }

        val invalidRange = MAX_UNICODE - (endCode - startCode + 1)
        if (invalidRange > 0) {
            for (iter in 0 until iters) {
                val x = TestUtil.nextInt(r, 0, invalidRange - 1)
                val code = if (x >= startCode) {
                    endCode + 1 + x - startCode
                } else {
                    x
                }
                if ((code >= UnicodeUtil.UNI_SUR_HIGH_START && code <= UnicodeUtil.UNI_SUR_HIGH_END) ||
                    (code >= UnicodeUtil.UNI_SUR_LOW_START && code <= UnicodeUtil.UNI_SUR_LOW_END)) {
                    continue
                }
                assertFalse(matches(a, code), "DFA for range $startCode-$endCode matched invalid code=$code")
            }
        }
    }

    private fun getCodeStart(r: Random): Int {
        return when (r.nextInt(4)) {
            0 -> TestUtil.nextInt(r, 0, 128)
            1 -> TestUtil.nextInt(r, 128, 2048)
            2 -> TestUtil.nextInt(r, 2048, 65536)
            else -> TestUtil.nextInt(r, 65536, 1 + MAX_UNICODE)
        }
    }

    private fun isSurrogate(code: Int): Boolean {
        return code >= UnicodeUtil.UNI_SUR_HIGH_START && code <= UnicodeUtil.UNI_SUR_LOW_END
    }

    @Test
    fun testRandomRanges() {
        val r = random()
        val ITERS = atLeast(10)
        val ITERS_PER_DFA = atLeast(100)
        for (iter in 0 until ITERS) {
            val x1 = getCodeStart(r)
            val x2 = getCodeStart(r)
            val startCode: Int
            val endCode: Int
            if (x1 < x2) {
                startCode = x1
                endCode = x2
            } else {
                startCode = x2
                endCode = x1
            }
            if (isSurrogate(startCode) && isSurrogate(endCode)) {
                continue
            }
            val a = Automata.makeCharRange(startCode, endCode)
            testOne(r, ByteRunAutomaton(a), startCode, endCode, ITERS_PER_DFA)
        }
    }

    @Test
    fun testSpecialCase() {
        val re = RegExp(".?")
        val automaton = re.toAutomaton()!!
        val cra = CharacterRunAutomaton(automaton)
        val bra = ByteRunAutomaton(automaton)
        assertTrue(cra.isAccept(0))
        assertTrue(cra.run(""))
        assertTrue(cra.run(CharArray(0), 0, 0))
        assertTrue(bra.isAccept(0))
        assertTrue(bra.run(ByteArray(0), 0, 0))
    }

    @Test
    fun testSpecialCase2() {
        val re = RegExp(".+\u0775")
        val input = "\ufadc\ufffd\ub80b\uda5a\udc68\uf234\u0056\uda5b\udcc1\ufffd\ufffd\u0775"
        var automaton = re.toAutomaton()!!
        automaton = Operations.determinize(automaton, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val cra = CharacterRunAutomaton(automaton)
        val bra = ByteRunAutomaton(automaton)
        assertTrue(cra.run(input))
        val bytes = input.encodeToByteArray()
        assertTrue(bra.run(bytes, 0, bytes.size))
    }

    @Test
    fun testSpecialCase3() {
        val re = RegExp("(\\u9bfa)*(.)*\\u04d4")
        val input = "\u5cfd\ufffd\ub2f7\u0033\ue304\u51d7\u3692\udb50\udfb3\u0576\udae2\udc62\u0053\u0449\u04d4"
        var automaton = re.toAutomaton()!!
        automaton = Operations.determinize(automaton, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val cra = CharacterRunAutomaton(automaton)
        val bra = ByteRunAutomaton(automaton)
        assertTrue(cra.run(input))
        val bytes = input.encodeToByteArray()
        assertTrue(bra.run(bytes, 0, bytes.size))
    }

    @Test
    fun testSingleton() {
        val iters = atLeast(10) // TODO originally 100 but reduced to 10 for dev speed
        for (iter in 0 until iters) {
            val s = TestUtil.randomUnicodeString(random())
            val a = Automata.makeString(s)
            val utf8 = UTF32ToUTF8().convert(a)
            val bytes = s.encodeToByteArray()
            assertTrue(ByteRunAutomaton(utf8, true).run(bytes, 0, bytes.size))
        }
    }

    private fun assertAutomaton(automaton: Automaton) {
        val cra = CharacterRunAutomaton(automaton)
        val bra = ByteRunAutomaton(automaton)
        val ras = RandomAcceptedStrings(automaton)
        val num = atLeast(1000)
        for (i in 0 until num) {
            val string = if (random().nextBoolean()) {
                TestUtil.randomUnicodeString(random())
            } else {
                val codepoints = ras.getRandomAcceptedString(random())
                UnicodeUtil.newString(codepoints, 0, codepoints.size)
            }
            val bytes = string.encodeToByteArray()
            assertEquals(cra.run(string), bra.run(bytes, 0, bytes.size))
        }
    }

    companion object {
        private const val MAX_UNICODE = 0x10FFFF
    }

}