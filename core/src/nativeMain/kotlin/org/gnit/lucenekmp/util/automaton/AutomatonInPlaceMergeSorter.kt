package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.util.InPlaceMergeSorter

actual class AutomatonInPlaceMergeSorter actual constructor(
    actual var transitions: IntArray,
) : InPlaceMergeSorter() {
    actual override fun swap(i: Int, j: Int) {
        val transitions = transitions
        val iStart = 4 * i
        val jStart = 4 * j
        var x = transitions[iStart]
        transitions[iStart] = transitions[jStart]
        transitions[jStart] = x
        x = transitions[iStart + 1]
        transitions[iStart + 1] = transitions[jStart + 1]
        transitions[jStart + 1] = x
        x = transitions[iStart + 2]
        transitions[iStart + 2] = transitions[jStart + 2]
        transitions[jStart + 2] = x
        x = transitions[iStart + 3]
        transitions[iStart + 3] = transitions[jStart + 3]
        transitions[jStart + 3] = x
    }

    actual override fun compare(i: Int, j: Int): Int {
        val transitions = transitions
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
