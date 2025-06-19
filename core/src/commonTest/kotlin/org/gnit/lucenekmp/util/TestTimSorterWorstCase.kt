package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlin.test.Test

/**
 * Port of Lucene's TestTimSorterWorstCase from commit ec75fca.
 */
class TestTimSorterWorstCase : LuceneTestCase() {

    @Test
    @LuceneTestCase.Companion.Nightly
    fun testWorstCaseStackSize() {
        // we need large arrays to be able to reproduce this bug
        // but not so big we blow up available heap.
        val length: Int = if (TEST_NIGHTLY) {
            TestUtil.nextInt(random(), 140_000_000, 400_000_000)
        } else {
            TestUtil.nextInt(random(), 140, 200) // TODO originally TestUtil.nextInt(random(), 140_000_000, 200_000_000), but reduced to 140, 200 for dev speed
        }
        val arr = generateWorstCaseArray(length)
        object : TimSorter(0) {
            override fun swap(i: Int, j: Int) {
                val tmp = arr.get(i)
                arr.set(i, arr.get(j))
                arr.set(j, tmp)
            }

            override fun compare(i: Int, j: Int): Int {
                return arr.get(i).compareTo(arr.get(j))
            }

            override fun save(i: Int, len: Int) {
                throw UnsupportedOperationException()
            }

            override fun restore(i: Int, j: Int) {
                throw UnsupportedOperationException()
            }

            override fun copy(src: Int, dest: Int) {
                arr.set(dest, arr.get(src))
            }

            override fun compareSaved(i: Int, j: Int): Int {
                throw UnsupportedOperationException()
            }
        }.sort(0, length)
    }

    /** Create an array for the given list of runs. */
    private fun createArray(length: Int, runs: List<Int>): PackedInts.Mutable {
        val array = PackedInts.getMutable(length, 1, 0f)
        var endRun = -1
        for (len in runs) {
            array.set(endRun + len, 1)
            endRun += len
        }
        array.set(length - 1, 0)
        return array
    }

    /** Create an array that triggers a worst-case sequence of run lens. */
    private fun generateWorstCaseArray(length: Int): PackedInts.Mutable {
        val minRun = TimSorter.minRun(length)
        val runs = runsWorstCase(length, minRun)
        return createArray(length, runs)
    }

    // Code below is borrowed from
    // https://github.com/abstools/java-timsort-bug/blob/master/TestTimSort.java

    private fun runsWorstCase(length: Int, minRun: Int): MutableList<Int> {
        val runs = mutableListOf<Int>()

        var runningTotal = 0
        var Y = minRun + 4
        var X = minRun

        while (runningTotal.toLong() + Y + X <= length) {
            runningTotal += X + Y
            generateWrongElem(X, minRun, runs)
            runs.add(0, Y)

            // X_{i+1} = Y_i + x_{i,1} + 1, since runs[1] = x_{i,1}
            X = Y + runs[1] + 1

            // Y_{i+1} = X_{i+1} + Y_i + 1
            Y += X + 1
        }

        if (runningTotal.toLong() + X <= length) {
            runningTotal += X
            generateWrongElem(X, minRun, runs)
        }

        runs.add(length - runningTotal)
        return runs
    }

    private fun generateWrongElem(X: Int, minRun: Int, runs: MutableList<Int>) {
        var Xvar = X
        while (Xvar >= 2 * minRun + 1) {
            var newTotal = Xvar / 2 + 1

            if (3 * minRun + 3 <= Xvar && Xvar <= 4 * minRun + 1) {
                newTotal = 2 * minRun + 1
            } else if (5 * minRun + 5 <= Xvar && Xvar <= 6 * minRun + 5) {
                newTotal = 3 * minRun + 3
            } else if (8 * minRun + 9 <= Xvar && Xvar <= 10 * minRun + 9) {
                newTotal = 5 * minRun + 5
            } else if (13 * minRun + 15 <= Xvar && Xvar <= 16 * minRun + 17) {
                newTotal = 8 * minRun + 9
            }
            runs.add(0, Xvar - newTotal)
            Xvar = newTotal
        }
        runs.add(0, Xvar)
    }
}

