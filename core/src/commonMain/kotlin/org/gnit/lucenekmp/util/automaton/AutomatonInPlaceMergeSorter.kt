package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.util.InPlaceMergeSorter

expect class AutomatonInPlaceMergeSorter(
    transitions: IntArray,
) : InPlaceMergeSorter {
    var transitions: IntArray
    override fun compare(i: Int, j: Int): Int
    override fun swap(i: Int, j: Int)
}
