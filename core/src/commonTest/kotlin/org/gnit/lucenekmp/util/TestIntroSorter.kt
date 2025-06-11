package org.gnit.lucenekmp.util

import kotlin.test.Test

class TestIntroSorter : BaseSortTestCase(false) {
    override fun newSorter(arr: Array<Entry>): Sorter {
        return ArrayIntroSorter(arr, Comparator { a, b -> a.compareTo(b) })
    }

    // Kotlin requires explicit test annotations on inherited tests
    @Test fun testEmptyIntroSorter() = testEmpty()
    @Test fun testOneIntroSorter() = testOne()
    @Test fun testTwoIntroSorter() = testTwo()
    @Test fun testRandomIntroSorter() = testRandom()
    @Test fun testRandomLowCardinalityIntroSorter() = testRandomLowCardinality()
    @Test fun testAscendingIntroSorter() = testAscending()
    @Test fun testAscendingSequencesIntroSorter() = testAscendingSequences()
    @Test fun testDescendingIntroSorter() = testDescending()
    @Test fun testStrictlyDescendingIntroSorter() = testStrictlyDescending()
}
