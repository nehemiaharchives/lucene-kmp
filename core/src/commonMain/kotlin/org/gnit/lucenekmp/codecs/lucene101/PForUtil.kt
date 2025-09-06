package org.gnit.lucenekmp.codecs.lucene101


import org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.LongHeap
import org.gnit.lucenekmp.util.packed.PackedInts
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.jdkport.toUnsignedLong
import org.gnit.lucenekmp.store.IndexInput
import kotlin.math.max

/** Utility class to encode sequences of 128 small positive integers.  */
internal class PForUtil {
    private val forUtil = ForUtil()

    /** Encode 128 integers from `ints` into `out`.  */
    @Throws(IOException::class)
    fun encode(ints: IntArray, out: DataOutput) {
        // Determine the top MAX_EXCEPTIONS + 1 values
        val top = LongHeap(MAX_EXCEPTIONS + 1)
        for (i in 0..MAX_EXCEPTIONS) {
            top.push(ints[i].toLong())
        }
        var topValue: Long = top.top()
        for (i in MAX_EXCEPTIONS + 1..<ForUtil.BLOCK_SIZE) {
            if (ints[i] > topValue) {
                topValue = top.updateTop(ints[i].toLong())
            }
        }

        var max = 0L
        for (i in 1..top.size()) {
            max = max(max, top.get(i))
        }

        val maxBitsRequired: Int = PackedInts.bitsRequired(max)
        // We store the patch on a byte, so we can't decrease the number of bits required by more than 8
        val patchedBitsRequired: Int =
            max(PackedInts.bitsRequired(topValue), maxBitsRequired - 8)
        var numExceptions = 0
        val maxUnpatchedValue = (1L shl patchedBitsRequired) - 1
        for (i in 2..top.size()) {
            if (top.get(i) > maxUnpatchedValue) {
                numExceptions++
            }
        }
        val exceptions = ByteArray(numExceptions * 2)
        if (numExceptions > 0) {
            var exceptionCount = 0
            for (i in 0..<ForUtil.BLOCK_SIZE) {
                if (ints[i] > maxUnpatchedValue) {
                    exceptions[exceptionCount * 2] = i.toByte()
                    exceptions[exceptionCount * 2 + 1] = (ints[i] ushr patchedBitsRequired).toByte()
                    ints[i] = ints[i] and maxUnpatchedValue.toInt()
                    exceptionCount++
                }
            }
            require(exceptionCount == numExceptions) { "$exceptionCount $numExceptions" }
        }

        if (allEqual(ints) && maxBitsRequired <= 8) {
            for (i in 0..<numExceptions) {
                exceptions[2 * i + 1] =
                    (Byte.toUnsignedLong(exceptions[2 * i + 1]) shl patchedBitsRequired).toByte()
            }
            out.writeByte((numExceptions shl 5).toByte())
            out.writeVInt(ints[0])
        } else {
            val token = (numExceptions shl 5) or patchedBitsRequired
            out.writeByte(token.toByte())
            forUtil.encode(ints, patchedBitsRequired, out)
        }
        out.writeBytes(exceptions, exceptions.size)
    }

    /** Decode 128 integers into `ints`.  */
    @Throws(IOException::class)
    fun decode(pdu: PostingDecodingUtil, ints: IntArray) {
        val `in`: IndexInput /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */? = pdu.`in`
        val token: Int = Byte.toUnsignedInt(`in`!!.readByte())
        val bitsPerValue = token and 0x1f
        if (bitsPerValue == 0) {
            Arrays.fill(ints, 0, ForUtil.BLOCK_SIZE, `in`.readVInt())
        } else {
            forUtil.decode(bitsPerValue, pdu, ints)
        }
        val numExceptions = token ushr 5
        for (i in 0 until numExceptions) {
            val index = Byte.toUnsignedInt(`in`.readByte())
            // apply stored exception value at the given index
            ints[index] = ints[index] or (Byte.toUnsignedLong(`in`.readByte()) shl bitsPerValue).toInt()
        }
    }

    companion object {
        private const val MAX_EXCEPTIONS = 7

        fun allEqual(l: IntArray): Boolean {
            for (i in 1..<ForUtil.BLOCK_SIZE) {
                if (l[i] != l[0]) {
                    return false
                }
            }
            return true
        }

        init {
            require(ForUtil.BLOCK_SIZE <= 256) { "blocksize must fit in one byte. got " + ForUtil.BLOCK_SIZE }
        }

        /** Skip 128 integers.  */
        @Throws(IOException::class)
        fun skip(`in`: DataInput) {
            val token: Int = Byte.toUnsignedInt(`in`.readByte())
            val bitsPerValue = token and 0x1f
            val numExceptions = token ushr 5
            if (bitsPerValue == 0) {
                `in`.readVLong()
                `in`.skipBytes((numExceptions shl 1).toLong())
            } else {
                `in`.skipBytes((ForUtil.numBytes(bitsPerValue) + (numExceptions shl 1)).toLong())
            }
        }
    }
}
