package org.gnit.lucenekmp.tests.util

import kotlin.random.Random

/**
 * port of com.carrotsearch.randomizedtesting.generators.RandomPicks
 */
object RandomPicks {

    fun randomFrom(r: Random, array: ByteArray): Byte {
        checkZeroLength(array.size)
        return array[r.nextInt(array.size)]
    }

    fun randomFrom(r: Random, array: ShortArray): Short {
        checkZeroLength(array.size)
        return array[r.nextInt(array.size)]
    }

    fun randomFrom(r: Random, array: IntArray): Int {
        checkZeroLength(array.size)
        return array[r.nextInt(array.size)]
    }

    fun randomFrom(r: Random, array: CharArray): Char {
        checkZeroLength(array.size)
        return array[r.nextInt(array.size)]
    }

    fun randomFrom(r: Random, array: FloatArray): Float {
        checkZeroLength(array.size)
        return array[r.nextInt(array.size)]
    }

    fun randomFrom(r: Random, array: LongArray): Long {
        checkZeroLength(array.size)
        return array[r.nextInt(array.size)]
    }

    fun randomFrom(r: Random, array: DoubleArray): Double {
        checkZeroLength(array.size)
        return array[r.nextInt(array.size)]
    }

    private fun checkZeroLength(length: Int) {
        require(length != 0) { "Can't pick a random object from an empty array." }
    }

    /**
     * Pick a random object from the given array.
     */
    fun <T> randomFrom(r: Random, array: Array<T>): T {
        checkZeroLength(array.size)
        return array[r.nextInt(array.size)]
    }

    /**
     * Pick a random object from the given list.
     */
    fun <T> randomFrom(r: Random, list: MutableList<T>): T {
        require(list.isNotEmpty()) { "Can't pick a random object from an empty list." }
        return list[r.nextInt(list.size)]
    }

    /**
     * Pick a random object from the collection. Requires linear scanning.
     */
    fun <T> randomFrom(r: Random, collection: MutableCollection<T>): T {
        val size = collection.size
        require(size != 0) { "Can't pick a random object from an empty collection." }
        var pick: Int = r.nextInt(size)
        var value: T
        val i = collection.iterator()
        while (true) {
            value = i.next()
            if (pick == 0) {
                break
            }
            pick--
        }
        return value
    }
}
