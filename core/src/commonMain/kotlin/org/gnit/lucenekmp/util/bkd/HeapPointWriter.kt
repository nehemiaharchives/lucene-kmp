package org.gnit.lucenekmp.util.bkd

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef

/**
 * Utility class to write new points into in-heap arrays.
 *
 * @lucene.internal
 */
class HeapPointWriter(private val config: BKDConfig, val size: Int) : PointWriter {
    private val block: ByteArray = ByteArray(config.bytesPerDoc() * size)
    private val scratch: ByteArray = ByteArray(config.bytesPerDoc())
    private val dimComparator: ByteArrayComparator = ArrayUtil.getUnsignedComparator(config.bytesPerDim)

    // length is composed by the data dimensions plus the docID
    private val dataDimsAndDocLength: Int = config.bytesPerDoc() - config.packedIndexBytesLength()
    private var nextWrite = 0
    private var closed = false
    private var pointValue: HeapPointValue? = null

    init {
        pointValue = if (size > 0) {
            HeapPointValue(config, block)
        } else {
            // no values
            null
        }
    }

    /** Returns a reference, in `result`, to the byte[] slice holding this value  */
    fun getPackedValueSlice(index: Int): PointValue? {
        require(index < nextWrite) { "nextWrite=$nextWrite vs index=$index" }
        pointValue!!.setOffset(index * config.bytesPerDoc())
        return pointValue
    }

    override fun append(packedValue: ByteArray, docID: Int) {
        require(closed == false) { "point writer is already closed" }
        require(
            packedValue.size == config.packedBytesLength()
        ) {
            ("[packedValue] must have length ["
                    + config.packedBytesLength()
                    + "] but was ["
                    + packedValue.size
                    + "]")
        }
        require(nextWrite < size) { "nextWrite=" + (nextWrite + 1) + " vs size=" + size }
        val position = nextWrite * config.bytesPerDoc()
        System.arraycopy(packedValue, 0, block, position, config.packedBytesLength())
        BitUtil.VH_BE_INT.set(block, position + config.packedBytesLength(), docID)
        nextWrite++
    }

    override fun append(pointValue: PointValue) {
        require(closed == false) { "point writer is already closed" }
        require(nextWrite < size) { "nextWrite=" + (nextWrite + 1) + " vs size=" + size }
        val packedValueDocID: BytesRef = pointValue.packedValueDocIDBytes()
        require(
            packedValueDocID.length == config.bytesPerDoc()
        ) {
            ("[packedValue] must have length ["
                    + (config.bytesPerDoc())
                    + "] but was ["
                    + packedValueDocID.length
                    + "]")
        }
        val position = nextWrite * config.bytesPerDoc()
        System.arraycopy(
            packedValueDocID.bytes, packedValueDocID.offset, block, position, config.bytesPerDoc()
        )
        nextWrite++
    }

    /** Swaps the point at point `i` with the point at position `j`  */
    fun swap(i: Int, j: Int) {
        val indexI = i * config.bytesPerDoc()
        val indexJ = j * config.bytesPerDoc()
        // scratch1 = values[i]
        System.arraycopy(block, indexI, scratch, 0, config.bytesPerDoc())
        // values[i] = values[j]
        System.arraycopy(block, indexJ, block, indexI, config.bytesPerDoc())
        // values[j] = scratch1
        System.arraycopy(scratch, 0, block, indexJ, config.bytesPerDoc())
    }

    /** Return the byte at position `k` of the point at position `i`  */
    fun byteAt(i: Int, k: Int): Int {
        return block[i * config.bytesPerDoc() + k].toInt() and 0xff
    }

    /**
     * Copy the dimension `dim` of the point at position `i` in the provided `bytes`
     * at the given offset
     */
    fun copyDim(i: Int, dim: Int, bytes: ByteArray, offset: Int) {
        System.arraycopy(block, i * config.bytesPerDoc() + dim, bytes, offset, config.bytesPerDim)
    }

    /**
     * Copy the data dimensions and doc value of the point at position `i` in the provided
     * `bytes` at the given offset
     */
    fun copyDataDimsAndDoc(i: Int, bytes: ByteArray, offset: Int) {
        System.arraycopy(
            block,
            i * config.bytesPerDoc() + config.packedIndexBytesLength(),
            bytes,
            offset,
            dataDimsAndDocLength
        )
    }

    /**
     * Compares the dimension `dim` value of the point at position `i` with the point at
     * position `j`
     */
    fun compareDim(i: Int, j: Int, dim: Int): Int {
        val iOffset = i * config.bytesPerDoc() + dim
        val jOffset = j * config.bytesPerDoc() + dim
        return compareDim(block, iOffset, block, jOffset)
    }

    /**
     * Compares the dimension `dim` value of the point at position `j` with the provided
     * value
     */
    fun compareDim(j: Int, dimValue: ByteArray, offset: Int, dim: Int): Int {
        val jOffset = j * config.bytesPerDoc() + dim
        return compareDim(dimValue, offset, block, jOffset)
    }

    private fun compareDim(blockI: ByteArray, offsetI: Int, blockJ: ByteArray, offsetJ: Int): Int {
        return dimComparator.compare(blockI, offsetI, blockJ, offsetJ)
    }

    /**
     * Compares the data dimensions and doc values of the point at position `i` with the point
     * at position `j`
     */
    fun compareDataDimsAndDoc(i: Int, j: Int): Int {
        val iOffset = i * config.bytesPerDoc() + config.packedIndexBytesLength()
        val jOffset = j * config.bytesPerDoc() + config.packedIndexBytesLength()
        return compareDataDimsAndDoc(block, iOffset, block, jOffset)
    }

    /**
     * Compares the data dimensions and doc values of the point at position `j` with the
     * provided value
     */
    fun compareDataDimsAndDoc(j: Int, dataDimsAndDocs: ByteArray, offset: Int): Int {
        val jOffset = j * config.bytesPerDoc() + config.packedIndexBytesLength()
        return compareDataDimsAndDoc(dataDimsAndDocs, offset, block, jOffset)
    }

    private fun compareDataDimsAndDoc(blockI: ByteArray, offsetI: Int, blockJ: ByteArray, offsetJ: Int): Int {
        return Arrays.compareUnsigned(
            blockI,
            offsetI,
            offsetI + dataDimsAndDocLength,
            blockJ,
            offsetJ,
            offsetJ + dataDimsAndDocLength
        )
    }

    /** Computes the cardinality of the points between `from` tp `to`  */
    fun computeCardinality(from: Int, to: Int, commonPrefixLengths: IntArray): Int {
        var leafCardinality = 1
        for (i in from + 1..<to) {
            val pointOffset = (i - 1) * config.bytesPerDoc()
            val nextPointOffset = pointOffset + config.bytesPerDoc()
            for (dim in 0..<config.numDims) {
                val start: Int = dim * config.bytesPerDim + commonPrefixLengths[dim]
                val end: Int = dim * config.bytesPerDim + config.bytesPerDim
                if (Arrays.mismatch(
                        block,
                        nextPointOffset + start,
                        nextPointOffset + end,
                        block,
                        pointOffset + start,
                        pointOffset + end
                    )
                    != -1
                ) {
                    leafCardinality++
                    break
                }
            }
        }
        return leafCardinality
    }

    override fun count(): Long {
        return nextWrite.toLong()
    }

    override fun getReader(start: Long, length: Long): PointReader {
        require(closed) { "point writer is still open and trying to get a reader" }
        require(
            start + length <= size
        ) { "start=$start length=$length docIDs.length=$size" }
        require(
            start + length <= nextWrite
        ) { "start=$start length=$length nextWrite=$nextWrite" }
        return HeapPointReader(
            { index: Int -> this.getPackedValueSlice(index) }, start.toInt(), Math.toIntExact(start + length)
        )
    }

    override fun close() {
        closed = true
    }

    override fun destroy() {}

    override fun toString(): String {
        return "HeapPointWriter(count=$nextWrite size=$size)"
    }

    /** Reusable implementation for a point value on-heap  */
    private class HeapPointValue(config: BKDConfig, value: ByteArray) : PointValue {
        private val packedValue: BytesRef
        private val packedValueDocID: BytesRef = BytesRef(value, 0, config.bytesPerDoc())
        private val packedValueLength: Int = config.packedBytesLength()

        init {
            this.packedValue = BytesRef(value, 0, packedValueLength)
        }

        /** Sets a new value by changing the offset.  */
        fun setOffset(offset: Int) {
            packedValue.offset = offset
            packedValueDocID.offset = offset
        }

        override fun packedValue(): BytesRef {
            return packedValue
        }

        override fun docID(): Int {
            val position: Int = packedValueDocID.offset + packedValueLength
            return BitUtil.VH_BE_INT.get(packedValueDocID.bytes, position)
        }

        override fun packedValueDocIDBytes(): BytesRef {
            return packedValueDocID
        }
    }
}
