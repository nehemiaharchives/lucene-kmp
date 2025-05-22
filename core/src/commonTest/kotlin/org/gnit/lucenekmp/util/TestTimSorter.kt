package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestTimSorter : BaseSortTestCase(true) {
    override fun newSorter(arr: Array<Entry>): Sorter {
        return ArrayTimSorter(
            arr,
            Comparator { a, b -> a.compareTo(b) },
            TestUtil.nextInt(TestUtil.random(), 0, arr.size)
        )
    }

    // in Java lucene all test method overrides are omitted and it relies on inherited tests from BaseSortTestCase
    // however in Kotlin we need @Test annotation to run the tests so we will add them here

    @Test fun testEmptyTimSorter() = testEmpty()
    @Test fun testOneTimSorter() = testOne()
    @Test fun testTwoTimSorter() = testTwo()
    @Test fun testRandomTimSorter() = testRandom()
    @Test fun testRandomLowCardinalityTimSorter() = testRandomLowCardinality()
    @Test fun testAscendingTimSorter() = testAscending()
    @Test fun testAscendingSequencesTimSorter() = testAscendingSequences()
    @Test fun testDescendingTimSorter() = testDescending()
    @Test fun testStrictlyDescendingTimSorter() = testStrictlyDescending()
}
