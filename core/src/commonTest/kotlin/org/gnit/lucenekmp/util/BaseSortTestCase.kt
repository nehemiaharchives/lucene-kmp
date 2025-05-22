package org.gnit.lucenekmp.util

import kotlin.random.Random
import kotlin.test.assertEquals
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil

abstract class BaseSortTestCase(private val stable: Boolean) {
    data class Entry(val value: Int, val ord: Int) : Comparable<Entry> {
        override fun compareTo(other: Entry): Int = value.compareTo(other.value)
    }

    protected abstract fun newSorter(arr: Array<Entry>): Sorter

    fun assertSorted(original: Array<Entry>, sorted: Array<Entry>) {
        assertEquals(original.size, sorted.size)
        val actuallySorted = ArrayUtil.copyArray(original)
        actuallySorted.sort()
        for (i in original.indices) {
            assertEquals(actuallySorted[i].value, sorted[i].value)
            if (stable) {
                assertEquals(actuallySorted[i].ord, sorted[i].ord)
            }
        }
    }

    fun test(arr: Array<Entry>) {
        val o = Random.nextInt(1000)
        val toSort = Array(o + arr.size + Random.nextInt(3)) { Entry(0, 0) }

        require(toSort is Array<Entry>){"the type of toSort is not Array<Entry>"}

        for (i in arr.indices) {
            toSort[o + i] = arr[i]
        }
        val sorter = newSorter(toSort)
        sorter.sort(o, o + arr.size)
        val sorted = ArrayUtil.copyOfSubArray(toSort, o, o + arr.size)

        require(sorted is Array<Entry>){"the type of sorted is not Array<Entry>"}

        assertSorted(arr, sorted)
    }

    enum class Strategy {
        RANDOM {
            override fun set(arr: Array<Entry>, i: Int, random: Random) {
                arr[i] = Entry(random.nextInt(), i)
            }
        },
        RANDOM_LOW_CARDINALITY {
            override fun set(arr: Array<Entry>, i: Int, random: Random) {
                arr[i] = Entry(random.nextInt(6), i)
            }
        },
        RANDOM_MEDIUM_CARDINALITY {
            override fun set(arr: Array<Entry>, i: Int, random: Random) {
                arr[i] = Entry(random.nextInt(arr.size / 2), i)
            }
        },
        ASCENDING {
            override fun set(arr: Array<Entry>, i: Int, random: Random) {
                arr[i] = if (i == 0) Entry(random.nextInt(6), 0)
                else Entry(arr[i - 1].value + random.nextInt(6), i)
            }
        },
        DESCENDING {
            override fun set(arr: Array<Entry>, i: Int, random: Random) {
                arr[i] = if (i == 0) Entry(random.nextInt(6), 0)
                else Entry(arr[i - 1].value - random.nextInt(6), i)
            }
        },
        STRICTLY_DESCENDING {
            override fun set(arr: Array<Entry>, i: Int, random: Random) {
                arr[i] = if (i == 0) Entry(random.nextInt(6), 0)
                else Entry(arr[i - 1].value - TestUtil.nextInt(random, 1, 5), i)
            }
        },
        ASCENDING_SEQUENCES {
            override fun set(arr: Array<Entry>, i: Int, random: Random) {
                arr[i] = if (i == 0) Entry(random.nextInt(6), 0)
                else Entry(if (TestUtil.rarely(random)) random.nextInt(1000) else arr[i - 1].value + random.nextInt(6), i)
            }
        },
        MOSTLY_ASCENDING {
            override fun set(arr: Array<Entry>, i: Int, random: Random) {
                arr[i] = if (i == 0) Entry(random.nextInt(6), 0)
                else Entry(arr[i - 1].value + TestUtil.nextInt(random, -8, 10), i)
            }
        };
        abstract fun set(arr: Array<Entry>, i: Int, random: Random)
    }

    fun test(strategy: Strategy, length: Int) {
        val random = Random.Default
        val arr = Array(length) { Entry(0, 0) }
        for (i in arr.indices) {
            strategy.set(arr, i, random)
        }
        test(arr)
    }

    fun test(strategy: Strategy) {
        test(strategy, Random.nextInt(20000))
    }

    fun testEmpty() {
        test(emptyArray())
    }

    fun testOne() {
        test(Strategy.RANDOM, 1)
    }

    fun testTwo() {
        test(Strategy.RANDOM_LOW_CARDINALITY, 2)
    }

    fun testRandom() {
        test(Strategy.RANDOM)
    }

    fun testRandomLowCardinality() {
        test(Strategy.RANDOM_LOW_CARDINALITY)
    }

    fun testAscending() {
        test(Strategy.ASCENDING)
    }

    fun testAscendingSequences() {
        test(Strategy.ASCENDING_SEQUENCES)
    }

    fun testDescending() {
        test(Strategy.DESCENDING)
    }

    fun testStrictlyDescending() {
        test(Strategy.STRICTLY_DESCENDING)
    }
}
