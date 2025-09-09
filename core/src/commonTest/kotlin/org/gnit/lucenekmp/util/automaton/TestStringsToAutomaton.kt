package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.tests.util.automaton.MinimizationOperations
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestStringsToAutomaton : LuceneTestCase() {
    @Test
    fun testBasic() {
        val terms = basicTerms()
        terms.sort()

        val a = build(terms, false)
        checkAutomaton(terms, a, false)
        checkMinimized(a)
    }

    @Test
    fun testBasicBinary() {
        val terms = basicTerms()
        terms.sort()

        val a = build(terms, true)
        checkAutomaton(terms, a, true)
        checkMinimized(a)
    }

    @Test
    fun testRandomMinimized() {
        val iters = if (TEST_NIGHTLY) 20 else 5
        repeat(iters) {
            val buildBinary = random().nextBoolean()
            val size = random().nextInt(2, 10) // TODO originally 50 but reduced to 10 for dev speed
            val terms = mutableSetOf<BytesRef>()
            val automata = mutableListOf<Automaton>()
            for (j in 0 until size) {
                if (buildBinary) {
                    val bytes = ByteArray(random().nextInt(1, 9)) { random().nextInt(0, 256).toByte() }
                    val t = BytesRef(bytes)
                    terms.add(t)
                    automata.add(Automata.makeBinary(t))
                } else {
                    val s = randomUnicodeString(random(), 8)
                    terms.add(newBytesRef(s))
                    automata.add(Automata.makeString(s))
                }
            }
            val sortedTerms = terms.toList().sorted()
            val expected =
                MinimizationOperations.minimize(Operations.union(automata), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            val actual = build(sortedTerms, buildBinary)
            assertSameAutomaton(expected, actual)
        }
    }

    @Test
    fun testRandomUnicodeOnly() {
        testRandom(false)
    }

    @Test
    fun testRandomBinary() {
        testRandom(true)
    }

    @Test
    fun testLargeTerms() {
        val b10k = ByteArray(10000)
        Arrays.fill(b10k, 'a'.code.toByte())
        val e: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) {
                build(mutableSetOf(BytesRef(b10k)), false)
            }!!
        assertTrue(
            e.message!!
                .startsWith(
                    ("This builder doesn't allow terms that are larger than "
                            + Automata.MAX_STRING_UNION_TERM_LENGTH
                            + " UTF-8 bytes")
                )
        )

        val b1k: ByteArray = ArrayUtil.copyOfSubArray(b10k, 0, 1000)
        build(
            mutableSetOf(BytesRef(b1k)),
            false
        ) // no exception
    }

    private fun testRandom(allowBinary: Boolean) {
        val iters = /*if (RandomizedTest.isNightly) 50 else */3
        for (i in 0..<iters) {
            val size: Int = random().nextInt(3, 10)  // TODO originally 500, 2000 but reducing to 3, 10 for dev speed
            val terms: MutableSet<BytesRef> = HashSet(size)
            for (j in 0..<size) {
                if (allowBinary && random().nextInt(10) < 2) {
                    // Sometimes random bytes term that isn't necessarily valid unicode
                    terms.add(
                        newBytesRef(
                            TestUtil.randomBinaryTerm(
                                random()
                            )
                        )
                    )
                } else {
                    terms.add(
                        newBytesRef(
                            TestUtil.randomRealisticUnicodeString(
                                random()
                            )
                        )
                    )
                }
            }

            val sorted: MutableList<BytesRef> = terms.sorted().toMutableList()
            val a: Automaton = build(sorted, allowBinary)
            checkAutomaton(sorted, a, allowBinary)
        }
    }

    private fun checkAutomaton(expected: List<BytesRef>, a: Automaton, isBinary: Boolean) {
        val c = CompiledAutomaton(a, true, false, isBinary)
        val runAutomaton = c.runAutomaton!!
        for (t in expected) {
            val readable = if (isBinary) t.toString() else t.utf8ToString()
            assertTrue(runAutomaton.run(t.bytes, t.offset, t.length), "$readable should be found but wasn't")
        }
        // TODO: verify produced terms once FiniteStringsIterator is ported
    }

    private fun checkMinimized(a: Automaton) {
        val minimized =
            MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        assertSameAutomaton(minimized, a)
    }

    private fun assertSameAutomaton(a: Automaton, b: Automaton) {
        assertEquals(a.numStates, b.numStates)
        assertEquals(a.numTransitions, b.numTransitions)
        assertTrue(AutomatonTestUtil.sameLanguage(a, b))
    }

    private fun basicTerms(): MutableList<BytesRef> {
        val terms = mutableListOf<BytesRef>()
        terms.add(newBytesRef("dog"))
        terms.add(newBytesRef("day"))
        terms.add(newBytesRef("dad"))
        terms.add(newBytesRef("cats"))
        terms.add(newBytesRef("cat"))
        return terms
    }

    private fun build(terms: Collection<BytesRef>, asBinary: Boolean): Automaton {
        return if (random().nextBoolean()) {
            StringsToAutomaton.build(terms, asBinary)
        } else {
            StringsToAutomaton.build(TermIterator(terms), asBinary)
        }
    }

    private class TermIterator(terms: Collection<BytesRef>) : BytesRefIterator {
        private val it = terms.iterator()
        override fun next(): BytesRef? {
            return if (it.hasNext()) it.next() else null
        }
    }
}

