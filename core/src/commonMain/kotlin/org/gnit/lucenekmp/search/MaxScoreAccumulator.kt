package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi


/** Maintains the maximum score and its corresponding document id concurrently  */
internal class MaxScoreAccumulator {
    // scores are always positive
    @OptIn(ExperimentalAtomicApi::class)
    val acc: AtomicLong = AtomicLong(Long.MAX_VALUE) /*LongAccumulator =
        LongAccumulator(LongBinaryOperator { v1: Long, v2: Long -> maxEncode(v1, v2) }, Long.MIN_VALUE)*/

    // non-final and visible for tests
    var modInterval: Long = DEFAULT_INTERVAL.toLong()

    @OptIn(ExperimentalAtomicApi::class)
    fun accumulate(docId: Int, score: Float) {
        assert(docId >= 0 && score >= 0)
        val encode = ((Float.floatToIntBits(score).toLong()) shl 32) or docId.toLong()
        //acc.accumulate(encode)

        // CAS-loop using the standard AtomicLong.compareAndSet / load
        do {
            val prev = acc.load()
            val next = maxEncode(prev, encode)
        } while (!acc.compareAndSet(prev, next))
    }

    @OptIn(ExperimentalAtomicApi::class)
    val raw: Long
        get() = acc.load()

    companion object {
        // we use 2^10-1 to check the remainder with a bitwise operation
        const val DEFAULT_INTERVAL: Int = 0x3ff

        /**
         * Return the max encoded docId and score found in the two longs, following the encoding in [ ][.accumulate].
         */
        private fun maxEncode(v1: Long, v2: Long): Long {
            val score1: Float = Float.intBitsToFloat((v1 shr 32).toInt())
            val score2: Float = Float.intBitsToFloat((v2 shr 32).toInt())
            val cmp: Int = Float.compare(score1, score2)
            if (cmp == 0) {
                // tie-break on the minimum doc base
                return if (v1.toInt() < v2.toInt()) v1 else v2
            } else if (cmp > 0) {
                return v1
            }
            return v2
        }

        fun toScore(value: Long): Float {
            return Float.intBitsToFloat((value shr 32).toInt())
        }

        fun docId(value: Long): Int {
            return value.toInt()
        }
    }
}
