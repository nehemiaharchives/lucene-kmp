package org.gnit.lucenekmp.util.packed


import kotlinx.io.EOFException
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BitUtil

/**
 * Class for writing packed integers to be directly read from Directory. Integers can be read
 * on-the-fly via [DirectReader].
 *
 *
 * Unlike PackedInts, it optimizes for read i/o operations and supports &gt; 2B values. Example
 * usage:
 *
 * <pre class="prettyprint">
 * int bitsPerValue = DirectWriter.bitsRequired(100); // values up to and including 100
 * IndexOutput output = dir.createOutput("packed", IOContext.DEFAULT);
 * DirectWriter writer = DirectWriter.getInstance(output, numberOfValues, bitsPerValue);
 * for (int i = 0; i &lt; numberOfValues; i++) {
 * writer.add(value);
 * }
 * writer.finish();
 * output.close();
</pre> *
 *
 * @see DirectReader
 */
class DirectWriter internal constructor(val output: DataOutput, val numValues: Long, val bitsPerValue: Int) {

    var count: Long = 0
    var finished: Boolean = false

    // for now, just use the existing writer under the hood
    var off: Int = 0
    val nextBlocks: ByteArray
    val nextValues: LongArray

    /** Adds a value to this writer  */
    @Throws(IOException::class)
    fun add(l: Long) {
        require(bitsPerValue == 64 || (l >= 0 && l <= PackedInts.maxValue(bitsPerValue))) { bitsPerValue }
        require(!finished)
        if (count >= numValues) {
            throw EOFException("Writing past end of stream")
        }
        nextValues[off++] = l
        if (off == nextValues.size) {
            flush()
        }
        count++
    }

    @Throws(IOException::class)
    private fun flush() {
        if (off == 0) {
            return
        }
        // Avoid writing bits from values that are outside of the range we need to encode
        Arrays.fill(nextValues, off, nextValues.size, 0L)
        encode(nextValues, off, nextBlocks, bitsPerValue)
        val blockCount =
            PackedInts.Format.PACKED.byteCount(PackedInts.VERSION_CURRENT, off, bitsPerValue).toInt()
        output.writeBytes(nextBlocks, blockCount)
        off = 0
    }

    /** finishes writing  */
    @Throws(IOException::class)
    fun finish() {
        check(count == numValues) { "Wrong number of values added, expected: $numValues, got: $count" }
        require(!finished)
        flush()
        // add padding bytes for fast io
        // for every number of bits per value, we want to be able to read the entire value in a single
        // read e.g. for 20 bits per value, we want to be able to read values using ints so we need
        // 32 - 20 = 12 bits of padding
        val paddingBitsNeeded: Int = if (bitsPerValue > Int.SIZE_BITS) {
            Long.SIZE_BITS - bitsPerValue
        } else if (bitsPerValue > Short.SIZE_BITS) {
            Int.SIZE_BITS - bitsPerValue
        } else if (bitsPerValue > Byte.SIZE_BITS) {
            Short.SIZE_BITS - bitsPerValue
        } else {
            0
        }

        require(paddingBitsNeeded >= 0)
        val paddingBytesNeeded: Int = (paddingBitsNeeded + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS
        require(paddingBytesNeeded <= 3)

        for (i in 0..<paddingBytesNeeded) {
            output.writeByte(0.toByte())
        }
        finished = true
    }

    init {
        val memoryBudgetInBits: Int = Math.multiplyExact(Byte.SIZE_BITS, PackedInts.DEFAULT_BUFFER_SIZE)
        // For every value we need 64 bits for the value and bitsPerValue for the encoded value
        var bufferSize: Int = memoryBudgetInBits / (Long.SIZE_BITS + bitsPerValue)
        require(bufferSize > 0)
        // Round to the next multiple of 64
        bufferSize = Math.toIntExact((bufferSize + 63).toLong()) and -0x40
        nextValues = LongArray(bufferSize)
        // add 7 bytes in the end so that any value could be written as a long
        nextBlocks = ByteArray(bufferSize * bitsPerValue / Byte.SIZE_BITS + Long.SIZE_BYTES - 1)
    }

    companion object {
        private fun encode(nextValues: LongArray, upTo: Int, nextBlocks: ByteArray, bitsPerValue: Int) {
            if ((bitsPerValue and 7) == 0) {
                // bitsPerValue is a multiple of 8: 8, 16, 24, 32, 30, 48, 56, 64
                val bytesPerValue: Int = bitsPerValue / Byte.SIZE_BITS
                var i = 0
                var o = 0
                while (i < upTo) {
                    val l = nextValues[i]
                    if (bitsPerValue > Int.SIZE_BITS) {
                        BitUtil.VH_LE_LONG.set(nextBlocks, o, l)
                    } else if (bitsPerValue > Short.SIZE_BITS) {
                        BitUtil.VH_LE_INT.set(nextBlocks, o, l.toInt())
                    } else if (bitsPerValue > Byte.SIZE_BITS) {
                        BitUtil.VH_LE_SHORT.set(nextBlocks, o, l.toShort())
                    } else {
                        nextBlocks[o] = l.toByte()
                    }
                    ++i
                    o += bytesPerValue
                }
            } else if (bitsPerValue < 8) {
                // bitsPerValue is 1, 2 or 4
                val valuesPerLong: Int = Long.SIZE_BITS / bitsPerValue
                var i = 0
                var o = 0
                while (i < upTo) {
                    var v: Long = 0
                    for (j in 0..<valuesPerLong) {
                        v = v or (nextValues[i + j] shl (bitsPerValue * j))
                    }
                    BitUtil.VH_LE_LONG.set(nextBlocks, o, v)
                    i += valuesPerLong
                    o += Long.SIZE_BYTES
                }
            } else {
                // bitsPerValue is 12, 20 or 28
                // Write values 2 by 2
                val numBytesFor2Values: Int = bitsPerValue * 2 / Byte.SIZE_BITS
                var i = 0
                var o = 0
                while (i < upTo) {
                    val l1 = nextValues[i]
                    val l2 = nextValues[i + 1]
                    val merged = l1 or (l2 shl bitsPerValue)
                    if (bitsPerValue <= Int.SIZE_BITS / 2) {
                        BitUtil.VH_LE_INT.set(nextBlocks, o, merged.toInt())
                    } else {
                        BitUtil.VH_LE_LONG.set(nextBlocks, o, merged)
                    }
                    i += 2
                    o += numBytesFor2Values
                }
            }
        }

        /** Returns an instance suitable for encoding `numValues` using `bitsPerValue`  */
        fun getInstance(output: DataOutput, numValues: Long, bitsPerValue: Int): DirectWriter {
            require(
                Arrays.binarySearch(
                    SUPPORTED_BITS_PER_VALUE,
                    bitsPerValue
                ) >= 0
            ) { "Unsupported bitsPerValue $bitsPerValue. Did you use bitsRequired?" }
            return DirectWriter(output, numValues, bitsPerValue)
        }

        /**
         * Round a number of bits per value to the next amount of bits per value that is supported by this
         * writer.
         *
         * @param bitsRequired the amount of bits required
         * @return the next number of bits per value that is gte the provided value and supported by this
         * writer
         */
        private fun roundBits(bitsRequired: Int): Int {
            val index: Int = Arrays.binarySearch(SUPPORTED_BITS_PER_VALUE, bitsRequired)
            return if (index < 0) {
                SUPPORTED_BITS_PER_VALUE[-index - 1]
            } else {
                bitsRequired
            }
        }

        /**
         * Returns how many bits are required to hold values up to and including maxValue
         *
         * @param maxValue the maximum value that should be representable.
         * @return the amount of bits needed to represent values from 0 to maxValue.
         * @see PackedInts.bitsRequired
         */
        fun bitsRequired(maxValue: Long): Int {
            return roundBits(PackedInts.bitsRequired(maxValue))
        }

        /**
         * Returns how many bits are required to hold values up to and including maxValue, interpreted as
         * an unsigned value.
         *
         * @param maxValue the maximum value that should be representable.
         * @return the amount of bits needed to represent values from 0 to maxValue.
         * @see PackedInts.unsignedBitsRequired
         */
        fun unsignedBitsRequired(maxValue: Long): Int {
            return roundBits(PackedInts.unsignedBitsRequired(maxValue))
        }

        val SUPPORTED_BITS_PER_VALUE: IntArray = intArrayOf(1, 2, 4, 8, 12, 16, 20, 24, 28, 32, 40, 48, 56, 64)
    }
}
