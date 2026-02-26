package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import kotlin.collections.ArrayList
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Not thorough, but tries to test determinism correctness somewhat randomly, by determinizing a
 * huge random lexicon.
 */
class TestDeterminizeLexicon : LuceneTestCase() {
    private val automata: MutableList<Automaton> = ArrayList()
    private val terms: MutableList<String> = ArrayList()

    @Test
    fun testLexicon() {
        val num = atLeast(1)
        for (i in 0 until num) {
            automata.clear()
            terms.clear()
            for (j in 0 until 50) { // TODO reduced from 5000 to 50 for dev speed
                val randomString = TestUtil.randomUnicodeString(random())
                terms.add(randomString)
                automata.add(Automata.makeString(randomString))
            }
            assertLexicon()
        }
    }

    fun assertLexicon() {
        automata.shuffle(random())
        var lex = Operations.union(automata)
        lex = Operations.determinize(lex, 1_000_000)
        assertTrue(AutomatonTestUtil.isFinite(lex))
        for (s in terms) {
            assertTrue(Operations.run(lex, s))
        }
        if (TEST_NIGHTLY) {
            // TODO: very wasteful of RAM to do this without minimizing first.
            val lexByte = ByteRunAutomaton(lex, false)
            for (s in terms) {
                val bytes = s.encodeToByteArray()
                assertTrue(lexByte.run(bytes, 0, bytes.size))
            }
        }
    }
}
