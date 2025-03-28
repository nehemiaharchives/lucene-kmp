package org.gnit.lucenekmp.util.automaton

internal class FrozenIntSet(override val array: IntArray, val hashCode: Long, val state: Int) : IntSet() {
    val values: IntArray = array

    override fun size(): Int {
        return array.size
    }

    override fun longHashCode(): Long {
        return hashCode
    }

    override fun toString(): String {
        return array.contentToString()
    }
}
