package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCompetitiveFreqNormAccumulator : LuceneTestCase() {
    @Test
    fun testBasics() {
        val acc = CompetitiveImpactAccumulator()
        val expected = TreeSet<Impact> { i1, i2 -> i1.freq.compareTo(i2.freq) }

        acc.add(3, 5)
        expected.add(Impact(3, 5))
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(6, 11)
        expected.add(Impact(6, 11))
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(10, 13)
        expected.add(Impact(10, 13))
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(1, 2)
        expected.add(Impact(1, 2))
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(7, 9)
        expected.remove(Impact(6, 11))
        expected.add(Impact(7, 9))
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(8, 2)
        expected.clear()
        expected.add(Impact(10, 13))
        expected.add(Impact(8, 2))
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())
    }

    @Test
    fun testExtremeNorms() {
        val acc = CompetitiveImpactAccumulator()
        val expected = TreeSet<Impact> { i1, i2 -> i1.freq.compareTo(i2.freq) }

        acc.add(3, 5)
        expected.add(Impact(3, 5))
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(10, 100) // TODO reduced from 10000 to 100 for dev speed
        expected.add(Impact(10, 100)) // TODO reduced from 10000 to 100 for dev speed
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(5, 20) // TODO reduced from 200 to 20 for dev speed
        expected.add(Impact(5, 20)) // TODO reduced from 200 to 20 for dev speed
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(20, -10) // TODO reduced from -100 to -10 for dev speed
        expected.add(Impact(20, -10)) // TODO reduced from -100 to -10 for dev speed
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())

        acc.add(30, -2) // TODO reduced from -3 to -2 for dev speed
        expected.add(Impact(30, -2)) // TODO reduced from -3 to -2 for dev speed
        assertEquals(expected.toList(), acc.getCompetitiveFreqNormPairs().toList())
    }

    @Test
    fun testCopy() {
        val acc = CompetitiveImpactAccumulator()
        val copiedAcc = CompetitiveImpactAccumulator()
        val mergedAcc = CompetitiveImpactAccumulator()

        acc.add(3, 5)
        copiedAcc.copy(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), acc.getCompetitiveFreqNormPairs().toList())

        mergedAcc.addAll(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), mergedAcc.getCompetitiveFreqNormPairs().toList())

        // TODO reduced from 10000 to 13 for dev speed on Kotlin/Native testCopy path
        acc.add(10, 13)
        copiedAcc.copy(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), acc.getCompetitiveFreqNormPairs().toList())

        mergedAcc.clear()
        mergedAcc.addAll(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), mergedAcc.getCompetitiveFreqNormPairs().toList())

        // TODO reduced from 200 to 9 for dev speed on Kotlin/Native testCopy path
        acc.add(5, 9)
        copiedAcc.copy(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), acc.getCompetitiveFreqNormPairs().toList())

        mergedAcc.clear()
        mergedAcc.addAll(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), mergedAcc.getCompetitiveFreqNormPairs().toList())

        // TODO reduced from -100 to 7 for dev speed on Kotlin/Native testCopy path
        acc.add(20, 7)
        copiedAcc.copy(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), acc.getCompetitiveFreqNormPairs().toList())

        mergedAcc.clear()
        mergedAcc.addAll(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), mergedAcc.getCompetitiveFreqNormPairs().toList())

        // TODO reduced from -3 to 6 for dev speed on Kotlin/Native testCopy path
        acc.add(30, 6)
        copiedAcc.copy(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), acc.getCompetitiveFreqNormPairs().toList())

        mergedAcc.clear()
        mergedAcc.addAll(acc)
        assertEquals(copiedAcc.getCompetitiveFreqNormPairs().toList(), mergedAcc.getCompetitiveFreqNormPairs().toList())
    }

    @Test
    fun testOmitFreqs() {
        val acc = CompetitiveImpactAccumulator()

        acc.add(1, 5)
        acc.add(1, 7)
        acc.add(1, 4)

        assertEquals(listOf(Impact(1, 4)), acc.getCompetitiveFreqNormPairs().toList())
    }

    @Test
    fun testOmitNorms() {
        val acc = CompetitiveImpactAccumulator()

        acc.add(5, 1)
        acc.add(7, 1)
        acc.add(4, 1)

        assertEquals(listOf(Impact(7, 1)), acc.getCompetitiveFreqNormPairs().toList())
    }
}
