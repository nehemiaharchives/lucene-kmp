package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.util.InPlaceMergeSorter

actual class AutomatonInPlaceMergeSorter actual constructor(
    actual var transitions: IntArray,
) : InPlaceMergeSorter() {
    private fun swapOne(i: Int, j: Int) {
        val x = transitions[i]
        transitions[i] = transitions[j]
        transitions[j] = x
    }

    actual override fun swap(i: Int, j: Int) {
        val iStart = 4 * i
        val jStart = 4 * j
        swapOne(iStart, jStart)
        swapOne(iStart + 1, jStart + 1)
        swapOne(iStart + 2, jStart + 2)
        swapOne(iStart + 3, jStart + 3)
    }

    actual override fun compare(i: Int, j: Int): Int {
        val iStart = 4 * i
        val jStart = 4 * j

        val iSrc = transitions[iStart]
        val jSrc = transitions[jStart]
        if (iSrc < jSrc) {
            return -1
        } else if (iSrc > jSrc) {
            return 1
        }

        val iMin = transitions[iStart + 2]
        val jMin = transitions[jStart + 2]
        if (iMin < jMin) {
            return -1
        } else if (iMin > jMin) {
            return 1
        }

        val iMax = transitions[iStart + 3]
        val jMax = transitions[jStart + 3]
        if (iMax < jMax) {
            return -1
        } else if (iMax > jMax) {
            return 1
        }

        val iDest = transitions[iStart + 1]
        val jDest = transitions[jStart + 1]
        if (iDest < jDest) {
            return -1
        } else if (iDest > jDest) {
            return 1
        }

        return 0
    }
}

