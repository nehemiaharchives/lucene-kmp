package org.gnit.lucenekmp.util

import kotlin.test.Test

class TestInPlaceMergeSorter : BaseSortTestCase(true) {
    override fun newSorter(arr: Array<Entry>): Sorter {
        return ArrayInPlaceMergeSorter(arr, Comparator { a, b -> a.compareTo(b) })
    }

    // In Java lucene, tests are inherited from BaseSortTestCase. Kotlin requires
    // explicit @Test annotations, so we delegate.
    @Test fun testEmptyInPlaceMergeSorter() = testEmpty()
    @Test fun testOneInPlaceMergeSorter() = testOne()
    @Test fun testTwoInPlaceMergeSorter() = testTwo()
    @Test fun testRandomInPlaceMergeSorter() = testRandom()
    @Test fun testRandomLowCardinalityInPlaceMergeSorter() = testRandomLowCardinality()
    @Test fun testAscendingInPlaceMergeSorter() = testAscending()
    @Test fun testAscendingSequencesInPlaceMergeSorter() = testAscendingSequences()
    @Test fun testDescendingInPlaceMergeSorter() = testDescending()
    @Test fun testStrictlyDescendingInPlaceMergeSorter() = testStrictlyDescending()
}
