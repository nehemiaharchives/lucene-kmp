package org.gnit.lucenekmp.util.packed

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.LongsRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.jvm.JvmRecord
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


/**
 * Simplistic compression for array of unsigned long values. Each value is `>= 0` and `<=` a specified maximum value. The values are stored as packed ints, with each value consuming a
 * fixed number of bits.
 *
 * @lucene.internal
 */
object PackedInts {
    /** At most 700% memory overhead, always select a direct implementation.  */
    const val FASTEST: Float = 7f

    /** At most 50% memory overhead, always select a reasonably fast implementation.  */
    const val FAST: Float = 0.5f

    /** At most 25% memory overhead.  */
    const val DEFAULT: Float = 0.25f

    /** No memory overhead at all, but the returned implementation may be slow.  */
    const val COMPACT: Float = 0f

    /** Default amount of memory to use for bulk operations.  */
    const val DEFAULT_BUFFER_SIZE: Int = 1024 // 1K

    const val CODEC_NAME: String = "PackedInts"
    const val VERSION_MONOTONIC_WITHOUT_ZIGZAG: Int = 2
    val VERSION_START: Int = VERSION_MONOTONIC_WITHOUT_ZIGZAG
    val VERSION_CURRENT: Int = VERSION_MONOTONIC_WITHOUT_ZIGZAG

    /** Check the validity of a version number.  */
    fun checkVersion(version: Int) {
        require(version >= VERSION_START) { "Version is too old, should be at least $VERSION_START (got $version)" }
        require(version <= VERSION_CURRENT) { "Version is too new, should be at most $VERSION_CURRENT (got $version)" }
    }

    /**
     * Try to find the [Format] and number of bits per value that would restore from disk the
     * fastest reader whose overhead is less than `acceptableOverheadRatio`.
     *
     *
     * The `acceptableOverheadRatio` parameter makes sense for random-access [ ]s. In case you only plan to perform sequential access on this stream later on, you
     * should probably use [PackedInts.COMPACT].
     *
     *
     * If you don't know how many values you are going to write, use `valueCount = -1`.
     */
    fun fastestFormatAndBits(
        valueCount: Int, bitsPerValue: Int, acceptableOverheadRatio: Float
    ): FormatAndBits {
        var valueCount = valueCount
        var acceptableOverheadRatio = acceptableOverheadRatio
        if (valueCount == -1) {
            valueCount = Int.Companion.MAX_VALUE
        }

        acceptableOverheadRatio = max(COMPACT, acceptableOverheadRatio)
        acceptableOverheadRatio = min(FASTEST, acceptableOverheadRatio)
        val acceptableOverheadPerValue = acceptableOverheadRatio * bitsPerValue // in bits

        val maxBitsPerValue = bitsPerValue + acceptableOverheadPerValue.toInt()

        var actualBitsPerValue = -1

        // rounded number of bits per value are usually the fastest
        if (bitsPerValue <= 8 && maxBitsPerValue >= 8) {
            actualBitsPerValue = 8
        } else if (bitsPerValue <= 16 && maxBitsPerValue >= 16) {
            actualBitsPerValue = 16
        } else if (bitsPerValue <= 32 && maxBitsPerValue >= 32) {
            actualBitsPerValue = 32
        } else if (bitsPerValue <= 64 && maxBitsPerValue >= 64) {
            actualBitsPerValue = 64
        } else {
            actualBitsPerValue = bitsPerValue
        }

        return FormatAndBits(Format.PACKED, actualBitsPerValue)
    }

    /**
     * Get a [Decoder].
     *
     * @param format the format used to store packed ints
     * @param version the compatibility version
     * @param bitsPerValue the number of bits per value
     * @return a decoder
     */
    fun getDecoder(format: Format, version: Int, bitsPerValue: Int): Decoder {
        checkVersion(version)
        return BulkOperation.of(format, bitsPerValue)
    }

    /**
     * Get an [Encoder].
     *
     * @param format the format used to store packed ints
     * @param version the compatibility version
     * @param bitsPerValue the number of bits per value
     * @return an encoder
     */
    fun getEncoder(format: Format, version: Int, bitsPerValue: Int): Encoder {
        checkVersion(version)
        return BulkOperation.of(format, bitsPerValue)
    }

    /**
     * Expert: Restore a [ReaderIterator] from a stream without reading metadata at the
     * beginning of the stream. This method is useful to restore data from streams which have been
     * created using [PackedInts.getWriterNoHeader].
     *
     * @param in the stream to read data from, positioned at the beginning of the packed values
     * @param format the format used to serialize
     * @param version the version used to serialize the data
     * @param valueCount how many values the stream holds
     * @param bitsPerValue the number of bits per value
     * @param mem how much memory the iterator is allowed to use to read-ahead (likely to speed up
     * iteration)
     * @return a ReaderIterator
     * @see PackedInts.getWriterNoHeader
     * @lucene.internal
     */
    fun getReaderIteratorNoHeader(
        `in`: DataInput, format: Format, version: Int, valueCount: Int, bitsPerValue: Int, mem: Int
    ): ReaderIterator {
        checkVersion(version)
        return PackedReaderIterator(format, version, valueCount, bitsPerValue, `in`, mem)
    }

    /**
     * Create a packed integer array with the given amount of values initialized to 0. the valueCount
     * and the bitsPerValue cannot be changed after creation. All Mutables known by this factory are
     * kept fully in RAM.
     *
     *
     * Positive values of `acceptableOverheadRatio` will trade space for speed by
     * selecting a faster but potentially less memory-efficient implementation. An `
     * acceptableOverheadRatio` of [PackedInts.COMPACT] will make sure that the most
     * memory-efficient implementation is selected whereas [PackedInts.FASTEST] will make sure
     * that the fastest implementation is selected.
     *
     * @param valueCount the number of elements
     * @param bitsPerValue the number of bits available for any given value
     * @param acceptableOverheadRatio an acceptable overhead ratio per value
     * @return a mutable packed integer array
     * @lucene.internal
     */
    fun getMutable(
        valueCount: Int, bitsPerValue: Int, acceptableOverheadRatio: Float
    ): Mutable {
        val formatAndBits =
            fastestFormatAndBits(valueCount, bitsPerValue, acceptableOverheadRatio)
        return PackedInts.getMutable(valueCount, formatAndBits.bitsPerValue, formatAndBits.format!!)
    }

    /**
     * Same as [.getMutable] with a pre-computed number of bits per value and
     * format.
     *
     * @lucene.internal
     */
    fun getMutable(valueCount: Int, bitsPerValue: Int, format: Format): Mutable {
        require(valueCount >= 0)
        when (format) {
            // PACKED_SINGLE_BLOCK is deprecated
            // Format.PACKED_SINGLE_BLOCK -> return Packed64SingleBlock.create(valueCount, bitsPerValue)
            Format.PACKED -> return Packed64(valueCount, bitsPerValue)
            else -> throw AssertionError()
        }
    }

    /**
     * Expert: Create a packed integer array writer for the given output, format, value count, and
     * number of bits per value.
     *
     *
     * The resulting stream will be long-aligned. This means that depending on the format which is
     * used, up to 63 bits will be wasted. An easy way to make sure that no space is lost is to always
     * use a `valueCount` that is a multiple of 64.
     *
     *
     * This method does not write any metadata to the stream, meaning that it is your
     * responsibility to store it somewhere else in order to be able to recover data from the stream
     * later on:
     *
     *
     *  * `format` (using [Format.getId]),
     *  * `valueCount`,
     *  * `bitsPerValue`,
     *  * [.VERSION_CURRENT].
     *
     *
     *
     * It is possible to start writing values without knowing how many of them you are actually
     * going to write. To do this, just pass `-1` as `valueCount`. On the other
     * hand, for any positive value of `valueCount`, the returned writer will make sure
     * that you don't write more values than expected and pad the end of stream with zeros in case you
     * have written less than `valueCount` when calling [Writer.finish].
     *
     *
     * The `mem` parameter lets you control how much memory can be used to buffer
     * changes in memory before flushing to disk. High values of `mem` are likely to
     * improve throughput. On the other hand, if speed is not that important to you, a value of `
     * 0` will use as little memory as possible and should already offer reasonable throughput.
     *
     * @param out the data output
     * @param format the format to use to serialize the values
     * @param valueCount the number of values
     * @param bitsPerValue the number of bits per value
     * @param mem how much memory (in bytes) can be used to speed up serialization
     * @return a Writer
     * @see PackedInts.getReaderIteratorNoHeader
     * @lucene.internal
     */
    fun getWriterNoHeader(
        out: DataOutput, format: Format, valueCount: Int, bitsPerValue: Int, mem: Int
    ): Writer {
        return PackedWriter(format, out, valueCount, bitsPerValue, mem)
    }

    /**
     * Returns how many bits are required to hold values up to and including maxValue NOTE: This
     * method returns at least 1.
     *
     * @param maxValue the maximum value that should be representable.
     * @return the amount of bits needed to represent values from 0 to maxValue.
     * @lucene.internal
     */
    fun bitsRequired(maxValue: Long): Int {
        require(maxValue >= 0) { "maxValue must be non-negative (got: $maxValue)" }
        return unsignedBitsRequired(maxValue)
    }

    /**
     * Returns how many bits are required to store `bits`, interpreted as an unsigned
     * value. NOTE: This method returns at least 1.
     *
     * @lucene.internal
     */
    fun unsignedBitsRequired(bits: Long): Int {
        return max(1, 64 - Long.numberOfLeadingZeros(bits))
    }

    /**
     * Calculates the maximum unsigned long that can be expressed with the given number of bits.
     *
     * @param bitsPerValue the number of bits available for any given value.
     * @return the maximum value for the given bits.
     * @lucene.internal
     */
    fun maxValue(bitsPerValue: Int): Long {
        return if (bitsPerValue == 64) Long.Companion.MAX_VALUE else (0L.inv() shl bitsPerValue).inv()
    }

    /**
     * Copy `src[srcPos:srcPos+len]` into `dest[destPos:destPos+len]` using at
     * most `mem` bytes.
     */
    fun copy(src: Reader, srcPos: Int, dest: Mutable, destPos: Int, len: Int, mem: Int) {
        var srcPos = srcPos
        var destPos = destPos
        require(srcPos + len <= src.size())
        require(destPos + len <= dest.size())
        val capacity = mem ushr 3
        if (capacity == 0) {
            for (i in 0..<len) {
                dest.set(destPos++, src.get(srcPos++))
            }
        } else if (len > 0) {
            // use bulk operations
            val buf = LongArray(min(capacity, len))
            copy(src, srcPos, dest, destPos, len, buf)
        }
    }

    /**
     * Same as [.copy] but using a pre-allocated buffer.
     */
    fun copy(src: Reader, srcPos: Int, dest: Mutable, destPos: Int, len: Int, buf: LongArray) {
        var srcPos = srcPos
        var destPos = destPos
        var len = len
        require(buf.size > 0)
        var remaining = 0
        while (len > 0) {
            val read = src.get(srcPos, buf, remaining, min(len, buf.size - remaining))
            require(read > 0)
            srcPos += read
            len -= read
            remaining += read
            val written = dest.set(destPos, buf, 0, remaining)
            require(written > 0)
            destPos += written
            if (written < remaining) {
                /*java.lang.System.arraycopy(buf, written, buf, 0, remaining - written)*/
                buf.copyInto(
                    destination = buf,
                    destinationOffset = 0,
                    startIndex = written,
                    endIndex = remaining
                )
            }
            remaining -= written
        }
        while (remaining > 0) {
            val written = dest.set(destPos, buf, 0, remaining)
            destPos += written
            remaining -= written
            /*java.lang.System.arraycopy(buf, written, buf, 0, remaining)*/
            buf.copyInto(
                destination = buf,
                destinationOffset = 0,
                startIndex = written,
                endIndex = remaining
            )
        }
    }

    /**
     * Check that the block size is a power of 2, in the right bounds, and return its log in base 2.
     */
    fun checkBlockSize(blockSize: Int, minBlockSize: Int, maxBlockSize: Int): Int {
        require(!(blockSize < minBlockSize || blockSize > maxBlockSize)) {
            ("blockSize must be >= "
                    + minBlockSize
                    + " and <= "
                    + maxBlockSize
                    + ", got "
                    + blockSize)
        }
        require((blockSize and (blockSize - 1)) == 0) { "blockSize must be a power of two, got $blockSize" }
        return Int.numberOfTrailingZeros(blockSize)
    }

    /**
     * Return the number of blocks required to store `size` values on `blockSize
    ` * .
     */
    fun numBlocks(size: Long, blockSize: Int): Int {
        val numBlocks = (size / blockSize).toInt() + (if (size % blockSize == 0L) 0 else 1)
        require(numBlocks.toLong() * blockSize >= size) { "size is too large for this block size" }
        return numBlocks
    }

    /**
     * A format to write packed ints.
     *
     * @lucene.internal
     */
    enum class Format(
        /** Returns the ID of the format.  */
        val id: Int
    ) {
        /** Compact format, all bits are written contiguously.  */
        PACKED(0) {
            override fun byteCount(packedIntsVersion: Int, valueCount: Int, bitsPerValue: Int): Long {
                return ceil(valueCount.toDouble() * bitsPerValue / 8).toLong()
            }
        };

        /**
         * A format that may insert padding bits to improve encoding and decoding speed. Since this
         * format doesn't support all possible bits per value, you should never use it directly, but
         * rather use [PackedInts.fastestFormatAndBits] to find the format that
         * best suits your needs.
         *
         */
        /*@Deprecated("Use {@link #PACKED} instead.")
        PACKED_SINGLE_BLOCK(1) {
            override fun longCount(packedIntsVersion: Int, valueCount: Int, bitsPerValue: Int): Int {
                val valuesPerBlock = 64 / bitsPerValue
                return ceil(valueCount.toDouble() / valuesPerBlock).toInt()
            }

            override fun isSupported(bitsPerValue: Int): Boolean {
                return Packed64SingleBlock.isSupported(bitsPerValue)
            }

            override fun overheadPerValue(bitsPerValue: Int): Float {
                require(isSupported(bitsPerValue))
                val valuesPerBlock = 64 / bitsPerValue
                val overhead = 64 % bitsPerValue
                return overhead.toFloat() / valuesPerBlock
            }
        };*/

        /**
         * Computes how many byte blocks are needed to store `values` values of size `
         * bitsPerValue`.
         */
        open fun byteCount(packedIntsVersion: Int, valueCount: Int, bitsPerValue: Int): Long {
            require(bitsPerValue >= 0 && bitsPerValue <= 64) { bitsPerValue }
            // assume long-aligned
            return 8L * longCount(packedIntsVersion, valueCount, bitsPerValue)
        }

        /**
         * Computes how many long blocks are needed to store `values` values of size `
         * bitsPerValue`.
         */
        open fun longCount(packedIntsVersion: Int, valueCount: Int, bitsPerValue: Int): Int {
            require(bitsPerValue >= 0 && bitsPerValue <= 64) { bitsPerValue }
            val byteCount = byteCount(packedIntsVersion, valueCount, bitsPerValue)
            require(byteCount < 8L * Int.Companion.MAX_VALUE)
            return ((byteCount + 7) ushr 3).toInt()
        }

        /** Tests whether the provided number of bits per value is supported by the format.  */
        open fun isSupported(bitsPerValue: Int): Boolean {
            return bitsPerValue >= 1 && bitsPerValue <= 64
        }

        /** Returns the overhead per value, in bits.  */
        open fun overheadPerValue(bitsPerValue: Int): Float {
            require(isSupported(bitsPerValue))
            return 0f
        }

        /** Returns the overhead ratio (`overhead per value / bits per value`).  */
        fun overheadRatio(bitsPerValue: Int): Float {
            require(isSupported(bitsPerValue))
            return overheadPerValue(bitsPerValue) / bitsPerValue
        }

        companion object {
            /** Get a format according to its ID.  */
            fun byId(id: Int): Format {
                for (format in entries) {
                    if (format.id == id) {
                        return format
                    }
                }
                throw IllegalArgumentException("Unknown format id: $id")
            }
        }
    }

    /** Simple class that holds a format and a number of bits per value.  */
    @JvmRecord
    data class FormatAndBits(val format: Format, val bitsPerValue: Int)

    /** A decoder for packed integers.  */
    interface Decoder {
        /**
         * The minimum number of long blocks to encode in a single iteration, when using long encoding.
         */
        fun longBlockCount(): Int

        /** The number of values that can be stored in [.longBlockCount] long blocks.  */
        fun longValueCount(): Int

        /**
         * The minimum number of byte blocks to encode in a single iteration, when using byte encoding.
         */
        fun byteBlockCount(): Int

        /** The number of values that can be stored in [.byteBlockCount] byte blocks.  */
        fun byteValueCount(): Int

        /**
         * Read `iterations * blockCount()` blocks from `blocks`, decode them and
         * write `iterations * valueCount()` values into `values`.
         *
         * @param blocks the long blocks that hold packed integer values
         * @param blocksOffset the offset where to start reading blocks
         * @param values the values buffer
         * @param valuesOffset the offset where to start writing values
         * @param iterations controls how much data to decode
         */
        fun decode(blocks: LongArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int)

        /**
         * Read `8 * iterations * blockCount()` blocks from `blocks`, decode them
         * and write `iterations * valueCount()` values into `values`.
         *
         * @param blocks the long blocks that hold packed integer values
         * @param blocksOffset the offset where to start reading blocks
         * @param values the values buffer
         * @param valuesOffset the offset where to start writing values
         * @param iterations controls how much data to decode
         */
        fun decode(blocks: ByteArray, blocksOffset: Int, values: LongArray, valuesOffset: Int, iterations: Int)

        /**
         * Read `iterations * blockCount()` blocks from `blocks`, decode them and
         * write `iterations * valueCount()` values into `values`.
         *
         * @param blocks the long blocks that hold packed integer values
         * @param blocksOffset the offset where to start reading blocks
         * @param values the values buffer
         * @param valuesOffset the offset where to start writing values
         * @param iterations controls how much data to decode
         */
        fun decode(blocks: LongArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int)

        /**
         * Read `8 * iterations * blockCount()` blocks from `blocks`, decode them
         * and write `iterations * valueCount()` values into `values`.
         *
         * @param blocks the long blocks that hold packed integer values
         * @param blocksOffset the offset where to start reading blocks
         * @param values the values buffer
         * @param valuesOffset the offset where to start writing values
         * @param iterations controls how much data to decode
         */
        fun decode(blocks: ByteArray, blocksOffset: Int, values: IntArray, valuesOffset: Int, iterations: Int)
    }

    /** An encoder for packed integers.  */
    interface Encoder {
        /**
         * The minimum number of long blocks to encode in a single iteration, when using long encoding.
         */
        fun longBlockCount(): Int

        /** The number of values that can be stored in [.longBlockCount] long blocks.  */
        fun longValueCount(): Int

        /**
         * The minimum number of byte blocks to encode in a single iteration, when using byte encoding.
         */
        fun byteBlockCount(): Int

        /** The number of values that can be stored in [.byteBlockCount] byte blocks.  */
        fun byteValueCount(): Int

        /**
         * Read `iterations * valueCount()` values from `values`, encode them and
         * write `iterations * blockCount()` blocks into `blocks`.
         *
         * @param blocks the long blocks that hold packed integer values
         * @param blocksOffset the offset where to start writing blocks
         * @param values the values buffer
         * @param valuesOffset the offset where to start reading values
         * @param iterations controls how much data to encode
         */
        fun encode(values: LongArray, valuesOffset: Int, blocks: LongArray, blocksOffset: Int, iterations: Int)

        /**
         * Read `iterations * valueCount()` values from `values`, encode them and
         * write `8 * iterations * blockCount()` blocks into `blocks`.
         *
         * @param blocks the long blocks that hold packed integer values
         * @param blocksOffset the offset where to start writing blocks
         * @param values the values buffer
         * @param valuesOffset the offset where to start reading values
         * @param iterations controls how much data to encode
         */
        fun encode(values: LongArray, valuesOffset: Int, blocks: ByteArray, blocksOffset: Int, iterations: Int)

        /**
         * Read `iterations * valueCount()` values from `values`, encode them and
         * write `iterations * blockCount()` blocks into `blocks`.
         *
         * @param blocks the long blocks that hold packed integer values
         * @param blocksOffset the offset where to start writing blocks
         * @param values the values buffer
         * @param valuesOffset the offset where to start reading values
         * @param iterations controls how much data to encode
         */
        fun encode(values: IntArray, valuesOffset: Int, blocks: LongArray, blocksOffset: Int, iterations: Int)

        /**
         * Read `iterations * valueCount()` values from `values`, encode them and
         * write `8 * iterations * blockCount()` blocks into `blocks`.
         *
         * @param blocks the long blocks that hold packed integer values
         * @param blocksOffset the offset where to start writing blocks
         * @param values the values buffer
         * @param valuesOffset the offset where to start reading values
         * @param iterations controls how much data to encode
         */
        fun encode(values: IntArray, valuesOffset: Int, blocks: ByteArray, blocksOffset: Int, iterations: Int)
    }

    /**
     * A read-only random access array of positive integers.
     *
     * @lucene.internal
     */
    abstract class Reader : Accountable {
        /** Get the long at the given index. Behavior is undefined for out-of-range indices.  */
        abstract fun get(index: Int): Long

        /**
         * Bulk get: read at least one and at most `len` longs starting from `index
        ` *  into `arr[off:off+len]` and return the actual number of values that have
         * been read.
         */
        open fun get(index: Int, arr: LongArray, off: Int, len: Int): Int {
            require(len > 0) { "len must be > 0 (got $len)" }
            require(index >= 0 && index < size())
            require(off + len <= arr.size)

            val gets = min(size() - index, len)
            var i = index
            var o = off
            val end = index + gets
            while (i < end) {
                arr[o] = get(i)
                ++i
                ++o
            }
            return gets
        }

        /**
         * @return the number of values.
         */
        abstract fun size(): Int
    }

    /** Run-once iterator interface, to decode previously saved PackedInts.  */
    interface ReaderIterator {
        /** Returns next value  */
        @Throws(IOException::class)
        fun next(): Long

        /**
         * Returns at least 1 and at most `count` next values, the returned ref MUST NOT be
         * modified
         */
        @Throws(IOException::class)
        fun next(count: Int): LongsRef

        /** Returns number of bits per value  */
        val bitsPerValue: Int

        /** Returns number of values  */
        fun size(): Int

        /** Returns the current position  */
        fun ord(): Int
    }

    internal abstract class ReaderIteratorImpl protected constructor(
        val valueCount: Int,
        override val bitsPerValue: Int,
        val `in`: DataInput
    ) : ReaderIterator {

        @Throws(IOException::class)
        override fun next(): Long {
            val nextValues: LongsRef = next(1)
            require(nextValues.length > 0)
            val result: Long = nextValues.longs[nextValues.offset]
            ++nextValues.offset
            --nextValues.length
            return result
        }

        override fun size(): Int {
            return valueCount
        }
    }

    /**
     * A packed integer array that can be modified.
     *
     * @lucene.internal
     */
    abstract class Mutable : Reader() {
        /**
         * @return the number of bits used to store any given value. Note: This does not imply that
         * memory usage is `bitsPerValue * #values` as implementations are free to use
         * non-space-optimal packing of bits.
         */
        abstract val bitsPerValue: Int

        /**
         * Set the value at the given index in the array.
         *
         * @param index where the value should be positioned.
         * @param value a value conforming to the constraints set by the array.
         */
        abstract fun set(index: Int, value: Long)

        /**
         * Bulk set: set at least one and at most `len` longs starting at `off` in
         * `arr` into this mutable, starting at `index`. Returns the actual number
         * of values that have been set.
         */
        open fun set(index: Int, arr: LongArray, off: Int, len: Int): Int {
            var len = len
            require(len > 0) { "len must be > 0 (got $len)" }
            require(index >= 0 && index < size())
            len = min(len, size() - index)
            require(off + len <= arr.size)

            var i = index
            var o = off
            val end = index + len
            while (i < end) {
                set(i, arr[o])
                ++i
                ++o
            }
            return len
        }

        /**
         * Fill the mutable from `fromIndex` (inclusive) to `toIndex` (exclusive)
         * with `val`.
         */
        open fun fill(fromIndex: Int, toIndex: Int, `val`: Long) {
            require(`val` <= maxValue(this.bitsPerValue))
            require(fromIndex <= toIndex)
            for (i in fromIndex..<toIndex) {
                set(i, `val`)
            }
        }

        /** Sets all values to 0.  */
        open fun clear() {
            fill(0, size(), 0)
        }
    }

    /**
     * A simple base for Readers that keeps track of valueCount and bitsPerValue.
     *
     * @lucene.internal
     */
    internal abstract class ReaderImpl protected constructor(protected val valueCount: Int) : Reader() {
        abstract override fun get(index: Int): Long

        override fun size(): Int {
            return valueCount
        }
    }

    internal abstract class MutableImpl protected constructor(protected val valueCount: Int, override val bitsPerValue: Int) :
        Mutable() {

        init {
            require(bitsPerValue > 0 && bitsPerValue <= 64) { "bitsPerValue=$bitsPerValue" }
        }

        override fun size(): Int {
            return valueCount
        }

        override fun toString(): String {
            return (this::class.simpleName
                    + "(valueCount="
                    + valueCount
                    + ",bitsPerValue="
                    + bitsPerValue
                    + ")")
        }
    }

    /** A [Reader] which has all its values equal to 0 (bitsPerValue = 0).  */
    class NullReader
    /** Sole constructor.  */ private constructor(private val valueCount: Int) : Reader() {
        override fun get(index: Int): Long {
            return 0
        }

        override fun get(index: Int, arr: LongArray, off: Int, len: Int): Int {
            var len = len
            require(len > 0) { "len must be > 0 (got $len)" }
            require(index >= 0 && index < valueCount)
            len = min(len, valueCount - index)
            /*java.util.Arrays.fill(arr, off, off + len, 0)*/
            arr.fill(0, off, off + len)
            return len
        }

        override fun size(): Int {
            return valueCount
        }

        public override fun ramBytesUsed(): Long {
            return if (valueCount == PackedLongValues.DEFAULT_PAGE_SIZE)
                0
            else
                RamUsageEstimator.alignObjectSize(
                    RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + Int.SIZE_BYTES.toLong()
                )
        }

        companion object {
            private val DEFAULT_PACKED_LONG_VALUES_PAGE_SIZE = NullReader(PackedLongValues.DEFAULT_PAGE_SIZE)

            fun forCount(valueCount: Int): NullReader {
                if (valueCount == PackedLongValues.DEFAULT_PAGE_SIZE) {
                    return DEFAULT_PACKED_LONG_VALUES_PAGE_SIZE
                }
                return NullReader(valueCount)
            }
        }
    }

    /**
     * A write-once Writer.
     *
     * @lucene.internal
     */
    abstract class Writer protected constructor(out: DataOutput, valueCount: Int, bitsPerValue: Int) {
        protected val out: DataOutput
        protected val valueCount: Int
        protected val bitsPerValue: Int

        init {
            require(bitsPerValue <= 64)
            require(valueCount >= 0 || valueCount == -1)
            this.out = out
            this.valueCount = valueCount
            this.bitsPerValue = bitsPerValue
        }

        /** The format used to serialize values.  */
        protected abstract val format: Format

        /** Add a value to the stream.  */
        @Throws(IOException::class)
        abstract fun add(v: Long)

        /** The number of bits per value.  */
        fun bitsPerValue(): Int {
            return bitsPerValue
        }

        /** Perform end-of-stream operations.  */
        @Throws(IOException::class)
        abstract fun finish()

        /**
         * Returns the current ord in the stream (number of values that have been written so far minus
         * one).
         */
        abstract fun ord(): Int
    }
}
