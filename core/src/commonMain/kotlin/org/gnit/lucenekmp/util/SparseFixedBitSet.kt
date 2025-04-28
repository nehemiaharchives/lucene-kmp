package org.gnit.lucenekmp.util

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.round


/**
 * A bit set that only stores longs that have at least one bit which is set. The way it works is
 * that the space of bits is divided into blocks of 4096 bits, which is 64 longs. Then for each
 * block, we have:
 *
 *
 *  * a long[] which stores the non-zero longs for that block
 *  * a long so that bit `i` being set means that the `i-th` long of the
 * block is non-null, and its offset in the array of longs is the number of one bits on the
 * right of the `i-th` bit.
 *
 *
 * @lucene.internal
 */
class SparseFixedBitSet(length: Int) : BitSet() {
    val indices: LongArray
    val bits: Array<LongArray?>
    val length: Int
    var nonZeroLongCount: Int = 0
    var ramBytesUsed: Long

    /**
     * Create a [SparseFixedBitSet] that can contain bits between `0` included and
     * `length` excluded.
     */
    init {
        require(length >= 1) { "length needs to be >= 1" }
        this.length = length
        val blockCount = blockCount(length)
        indices = LongArray(blockCount)
        bits = kotlin.arrayOfNulls<LongArray>(blockCount)
        ramBytesUsed =
            (BASE_RAM_BYTES_USED
                    + RamUsageEstimator.sizeOf(indices)
                    + RamUsageEstimator.shallowSizeOf(bits))
    }

    override fun clear() {
        bits.fill(null)
        indices.fill(0L)
        nonZeroLongCount = 0
        ramBytesUsed =
            (BASE_RAM_BYTES_USED
                    + RamUsageEstimator.sizeOf(indices)
                    + RamUsageEstimator.shallowSizeOf(bits))
    }

    override fun length(): Int {
        return length
    }

    private fun consistent(index: Int): Boolean {
        require(index >= 0 && index < length) { "index=$index,length=$length" }
        return true
    }

    override fun cardinality(): Int {
        var cardinality = 0
        for (bitArray in bits) {
            if (bitArray != null) {
                for (bits in bitArray) {
                    cardinality += Long.bitCount(bits)
                }
            }
        }
        return cardinality
    }

    override fun approximateCardinality(): Int {
        // we are assuming that bits are uniformly set and use the linear counting
        // algorithm to estimate the number of bits that are set based on the number
        // of longs that are different from zero
        val totalLongs = (length + 63) ushr 6 // total number of longs in the space
        require(totalLongs >= nonZeroLongCount)
        val zeroLongs = totalLongs - nonZeroLongCount // number of longs that are zeros
        // No need to guard against division by zero, it will return +Infinity and things will work as
        // expected
        val estimate: Long = round(totalLongs.toDouble() * ln(totalLongs.toDouble() / zeroLongs.toDouble())).toLong()
        return min(length.toLong(), estimate).toInt()
    }

    override fun get(i: Int): Boolean {
        require(consistent(i))
        val i4096 = i ushr 12
        val index = indices[i4096]
        val i64 = i ushr 6
        val i64bit = 1L shl i64
        // first check the index, if the i64-th bit is not set, then i is not set
        // note: this relies on the fact that shifts are mod 64 in java
        if ((index and i64bit) == 0L) {
            return false
        }

        // if it is set, then we count the number of bits that are set on the right
        // of i64, and that gives us the index of the long that stores the bits we
        // are interested in
        val bits = this.bits[i4096]!![Long.bitCount(index and (i64bit - 1))]
        return (bits and (1L shl i)) != 0L
    }

    override fun getAndSet(i: Int): Boolean {
        require(consistent(i))
        val i4096 = i ushr 12
        val index = indices[i4096]
        val i64 = i ushr 6
        val i64bit = 1L shl i64
        if ((index and i64bit) != 0L) {
            // in that case the sub 64-bits block we are interested in already exists,
            // we just need to set a bit in an existing long: the number of ones on
            // the right of i64 gives us the index of the long we need to update
            val location = Long.bitCount(index and (i64bit - 1))
            val bit = 1L shl i // shifts are mod 64 in java
            val v = (bits[i4096]!![location] and bit) != 0L
            bits[i4096]!![location] = bits[i4096]!![location] or bit
            return v
        } else if (index == 0L) {
            // if the index is 0, it means that we just found a block of 4096 bits
            // that has no bit that is set yet. So let's initialize a new block:
            insertBlock(i4096, i64bit, i)
            return false
        } else {
            // in that case we found a block of 4096 bits that has some values, but
            // the sub-block of 64 bits that we are interested in has no value yet,
            // so we need to insert a new long
            insertLong(i4096, i64bit, i, index)
            return false
        }
    }

    /** Set the bit at index `i`.  */
    override fun set(i: Int) {
        require(consistent(i))
        val i4096 = i ushr 12
        val index = indices[i4096]
        val i64 = i ushr 6
        val i64bit = 1L shl i64
        if ((index and i64bit) != 0L) {
            // in that case the sub 64-bits block we are interested in already exists,
            // we just need to set a bit in an existing long: the number of ones on
            // the right of i64 gives us the index of the long we need to update
            bits[i4096]!![Long.bitCount(index and (i64bit - 1))] =
                bits[i4096]!![Long.bitCount(index and (i64bit - 1))] or (1L shl i) // shifts are mod 64 in java
        } else if (index == 0L) {
            // if the index is 0, it means that we just found a block of 4096 bits
            // that has no bit that is set yet. So let's initialize a new block:
            insertBlock(i4096, i64bit, i)
        } else {
            // in that case we found a block of 4096 bits that has some values, but
            // the sub-block of 64 bits that we are interested in has no value yet,
            // so we need to insert a new long
            insertLong(i4096, i64bit, i, index)
        }
    }

    private fun insertBlock(i4096: Int, i64bit: Long, i: Int) {
        indices[i4096] = i64bit
        require(bits[i4096] == null)
        bits[i4096] = longArrayOf(1L shl i) // shifts are mod 64 in java
        ++nonZeroLongCount
        ramBytesUsed += SINGLE_ELEMENT_ARRAY_BYTES_USED
    }

    private fun insertLong(i4096: Int, i64bit: Long, i: Int, index: Long) {
        indices[i4096] = indices[i4096] or i64bit
        // we count the number of bits that are set on the right of i64
        // this gives us the index at which to perform the insertion
        val o = Long.bitCount(index and (i64bit - 1))
        val bitArray = bits[i4096]
        if (bitArray!![bitArray.size - 1] == 0L) {
            // since we only store non-zero longs, if the last value is 0, it means
            // that we already have extra space, make use of it
            /*java.lang.System.arraycopy(bitArray, o, bitArray, o + 1, bitArray.size - o - 1)*/
            bitArray.copyInto(
                destination = bitArray,
                destinationOffset = o + 1,
                startIndex = o,
                endIndex = bitArray.size - 1
            )
            bitArray[o] = 1L shl i
        } else {
            // we don't have extra space so we need to resize to insert the new long
            val newSize = oversize(bitArray.size + 1)
            val newBitArray = LongArray(newSize)
            /*java.lang.System.arraycopy(bitArray, 0, newBitArray, 0, o)*/
            bitArray.copyInto(
                destination = newBitArray,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = o
            )
            newBitArray[o] = 1L shl i
            /*java.lang.System.arraycopy(bitArray, o, newBitArray, o + 1, bitArray.size - o)*/
            bitArray.copyInto(
                destination = newBitArray,
                destinationOffset = o + 1,
                startIndex = o,
                endIndex = bitArray.size
            )
            bits[i4096] = newBitArray
            // we may slightly overestimate size here, but keep it cheap
            ramBytesUsed += ((newBitArray.size - bitArray.size) shl 3).toLong()
        }
        ++nonZeroLongCount
    }

    /** Clear the bit at index `i`.  */
    override fun clear(i: Int) {
        require(consistent(i))
        val i4096 = i ushr 12
        val i64 = i ushr 6
        and(i4096, i64, (1L shl i).inv())
    }

    private fun and(i4096: Int, i64: Int, mask: Long) {
        val index = indices[i4096]
        if ((index and (1L shl i64)) != 0L) {
            // offset of the long bits we are interested in in the array
            val o = Long.bitCount(index and ((1L shl i64) - 1))
            val bits = this.bits[i4096]!![o] and mask
            if (bits == 0L) {
                removeLong(i4096, i64, index, o)
            } else {
                this.bits[i4096]!![o] = bits
            }
        }
    }

    private fun removeLong(i4096: Int, i64: Int, index: Long, o: Int) {
        var index = index
        index = index and (1L shl i64).inv()
        indices[i4096] = index
        if (index == 0L) {
            // release memory, there is nothing in this block anymore
            this.bits[i4096] = null
        } else {
            val length = Long.bitCount(index)
            val bitArray = bits[i4096]
            /*java.lang.System.arraycopy(bitArray, o + 1, bitArray, o, length - o)*/
            bitArray!!.copyInto(
                destination = bitArray,
                destinationOffset = o,
                startIndex = o + 1,
                endIndex = length
            )
            bitArray[length] = 0L
        }
        nonZeroLongCount -= 1
    }

    override fun clear(from: Int, to: Int) {
        require(from >= 0)
        require(to <= length)
        if (from >= to) {
            return
        }
        val firstBlock = from ushr 12
        val lastBlock = (to - 1) ushr 12
        if (firstBlock == lastBlock) {
            clearWithinBlock(firstBlock, from and MASK_4096, (to - 1) and MASK_4096)
        } else {
            clearWithinBlock(firstBlock, from and MASK_4096, MASK_4096)
            for (i in firstBlock + 1..<lastBlock) {
                nonZeroLongCount -= Long.bitCount(indices[i])
                indices[i] = 0
                bits[i] = null
            }
            clearWithinBlock(lastBlock, 0, (to - 1) and MASK_4096)
        }
    }

    private fun clearWithinBlock(i4096: Int, from: Int, to: Int) {
        val firstLong = from ushr 6
        val lastLong = to ushr 6

        if (firstLong == lastLong) {
            and(i4096, firstLong, mask(from, to).inv())
        } else {
            require(firstLong < lastLong)
            and(i4096, lastLong, mask(0, to).inv())
            for (i in lastLong - 1 downTo firstLong + 1) {
                and(i4096, i, 0L)
            }
            and(i4096, firstLong, mask(from, 63).inv())
        }
    }

    /** Return the first document that occurs on or after the provided block index.  */
    private fun firstDoc(i4096: Int, i4096upper: Int): Int {
        var i4096 = i4096
        require(
            i4096upper <= indices.size
        ) { "i4096upper=" + i4096 + ", indices.length=" + indices.size }
        var index: Long
        while (i4096 < i4096upper) {
            index = indices[i4096]
            if (index != 0L) {
                val i64: Int = Long.numberOfTrailingZeros(index)
                return (i4096 shl 12) or (i64 shl 6) or Long.numberOfTrailingZeros(bits[i4096]!![0])
            }
            i4096 += 1
        }
        return DocIdSetIterator.NO_MORE_DOCS
    }

    override fun nextSetBit(i: Int): Int {
        // Override with a version that skips the bound check on the result since we know it will not
        // go OOB:
        return nextSetBitInRange(i, length)
    }

    override fun nextSetBit(start: Int, upperBound: Int): Int {
        val res = nextSetBitInRange(start, upperBound)
        return if (res < upperBound) res else DocIdSetIterator.NO_MORE_DOCS
    }

    /**
     * Returns the next set bit in the specified range, but treats `upperBound` as a best-effort hint
     * rather than a hard requirement. Note that this may return a result that is >= upperBound in
     * some cases, so callers must add their own check if `upperBound` is a hard requirement.
     */
    private fun nextSetBitInRange(start: Int, upperBound: Int): Int {
        require(start < length)
        require(
            upperBound > start && upperBound <= length
        ) { "upperBound=$upperBound, start=$start, length=$length" }
        val i4096 = start ushr 12
        val index = indices[i4096]
        val bitArray = this.bits[i4096]
        var i64 = start ushr 6
        val i64bit = 1L shl i64
        var o = Long.bitCount(index and (i64bit - 1))
        if ((index and i64bit) != 0L) {
            // There is at least one bit that is set in the current long, check if
            // one of them is after i
            val bits = bitArray!![o] ushr start // shifts are mod 64
            if (bits != 0L) {
                return start + Long.numberOfTrailingZeros(bits)
            }
            o += 1
        }
        val indexBits = index ushr i64 ushr 1
        if (indexBits == 0L) {
            // no more bits are set in the current block of 4096 bits, go to the next one
            val i4096upper = if (upperBound == length) indices.size else blockCount(upperBound)
            return firstDoc(i4096 + 1, i4096upper)
        }
        // there are still set bits
        i64 += 1 + Long.numberOfTrailingZeros(indexBits)
        val bits = bitArray!![o]
        return (i64 shl 6) or Long.numberOfTrailingZeros(bits)
    }

    /** Return the last document that occurs on or before the provided block index.  */
    private fun lastDoc(i4096: Int): Int {
        var i4096 = i4096
        var index: Long
        while (i4096 >= 0) {
            index = indices[i4096]
            if (index != 0L) {
                val i64 = 63 - Long.numberOfLeadingZeros(index)
                val bits = this.bits[i4096]!![Long.bitCount(index) - 1]
                return (i4096 shl 12) or (i64 shl 6) or (63 - Long.numberOfLeadingZeros(bits))
            }
            i4096 -= 1
        }
        return -1
    }

    override fun prevSetBit(i: Int): Int {
        require(i >= 0)
        val i4096 = i ushr 12
        val index = indices[i4096]
        val bitArray = this.bits[i4096]
        var i64 = i ushr 6
        val indexBits = index and ((1L shl i64) - 1)
        val o = Long.bitCount(indexBits)
        if ((index and (1L shl i64)) != 0L) {
            // There is at least one bit that is set in the same long, check if there
            // is one bit that is set that is lower than i
            val bits = bitArray!![o] and ((1L shl i shl 1) - 1)
            if (bits != 0L) {
                return (i64 shl 6) or (63 - Long.numberOfLeadingZeros(bits))
            }
        }
        if (indexBits == 0L) {
            // no more bits are set in this block, go find the last bit in the
            // previous block
            return lastDoc(i4096 - 1)
        }
        // go to the previous long
        i64 = 63 - Long.numberOfLeadingZeros(indexBits)
        val bits = bitArray!![o - 1]
        return (i4096 shl 12) or (i64 shl 6) or (63 - Long.numberOfLeadingZeros(bits))
    }

    /** Return the long bits at the given `i64` index.  */
    private fun longBits(index: Long, bits: LongArray, i64: Int): Long {
        return if ((index and (1L shl i64)) == 0L) {
            0L
        } else {
            bits[Long.bitCount(index and ((1L shl i64) - 1))]
        }
    }

    private fun or(i4096: Int, index: Long, bits: LongArray, nonZeroLongCount: Int) {
        require(Long.bitCount(index) == nonZeroLongCount)
        val currentIndex = indices[i4096]
        if (currentIndex == 0L) {
            // fast path: if we currently have nothing in the block, just copy the data
            // this especially happens all the time if you call OR on an empty set
            indices[i4096] = index

            val newBits = ArrayUtil.copyOfSubArray(bits, 0, nonZeroLongCount)
            this.bits[i4096] = newBits
            // we may slightly overestimate size here, but keep it cheap
            this.ramBytesUsed += SINGLE_ELEMENT_ARRAY_BYTES_USED + (newBits.size.toLong() - 1 shl 3)
            this.nonZeroLongCount += nonZeroLongCount
            return
        }
        val currentBits = this.bits[i4096]
        val newBits: LongArray?
        val newIndex = currentIndex or index
        val requiredCapacity = Long.bitCount(newIndex)
        if (currentBits!!.size >= requiredCapacity) {
            newBits = currentBits
        } else {
            newBits = LongArray(oversize(requiredCapacity))
            // we may slightly overestimate size here, but keep it cheap
            this.ramBytesUsed += (newBits.size - currentBits.size).toLong() shl 3
        }
        // we iterate backwards in order to not override data we might need on the next iteration if the
        // array is reused
        var i = Long.numberOfLeadingZeros(newIndex)
        var newO = Long.bitCount(newIndex) - 1
        while (i < 64
        ) {
            // bitIndex is the index of a bit which is set in newIndex and newO is the number of 1 bits on
            // its right
            val bitIndex = 63 - i
            require(newO == Long.bitCount(newIndex and ((1L shl bitIndex) - 1)))
            newBits[newO] =
                longBits(currentIndex, currentBits, bitIndex) or longBits(index, bits, bitIndex)
            i += 1 + Long.numberOfLeadingZeros(newIndex shl (i + 1))
            newO -= 1
        }
        indices[i4096] = newIndex
        this.bits[i4096] = newBits
        this.nonZeroLongCount += nonZeroLongCount - Long.bitCount(currentIndex and index)
    }

    private fun or(other: SparseFixedBitSet) {
        for (i in other.indices.indices) {
            val index = other.indices[i]
            if (index != 0L) {
                or(i, index, other.bits[i]!!, Long.bitCount(index))
            }
        }
    }

    /** [.or] impl that works best when `it` is dense  */
    @Throws(IOException::class)
    private fun orDense(it: DocIdSetIterator) {
        checkUnpositioned(it)
        // The goal here is to try to take advantage of the ordering of documents
        // to build the data-structure more efficiently
        // NOTE: this heavily relies on the fact that shifts are mod 64
        val firstDoc: Int = it.nextDoc()
        if (firstDoc == DocIdSetIterator.NO_MORE_DOCS) {
            return
        }
        var i4096 = firstDoc ushr 12
        var i64 = firstDoc ushr 6
        var index = 1L shl i64
        var currentLong = 1L shl firstDoc
        // we store at most 64 longs per block so preallocate in order never to have to resize
        val longs = LongArray(64)
        var numLongs = 0

        var doc: Int = it.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            val doc64 = doc ushr 6
            if (doc64 == i64) {
                // still in the same long, just set the bit
                currentLong = currentLong or (1L shl doc)
            } else {
                longs[numLongs++] = currentLong

                val doc4096 = doc ushr 12
                if (doc4096 == i4096) {
                    index = index or (1L shl doc64)
                } else {
                    // we are on a new block, flush what we buffered
                    or(i4096, index, longs, numLongs)
                    // and reset state for the new block
                    i4096 = doc4096
                    index = 1L shl doc64
                    numLongs = 0
                }

                // we are on a new long, reset state
                i64 = doc64
                currentLong = 1L shl doc
            }
            doc = it.nextDoc()
        }

        // flush
        longs[numLongs++] = currentLong
        or(i4096, index, longs, numLongs)
    }

    @Throws(IOException::class)
    override fun or(it: DocIdSetIterator) {
        run {
            // specialize union with another SparseFixedBitSet
            val other: SparseFixedBitSet? = BitSetIterator.getSparseFixedBitSetOrNull(it)
            if (other != null) {
                checkUnpositioned(it)
                or(other)
                return
            }
        }

        // We do not specialize the union with a FixedBitSet since FixedBitSets are
        // supposed to be used for dense data and sparse fixed bit sets for sparse
        // data, so a sparse set would likely get upgraded by DocIdSetBuilder before
        // being or'ed with a FixedBitSet
        if (it.cost() < indices.size) {
            // the default impl is good for sparse iterators
            super.or(it)
        } else {
            orDense(it)
        }
    }

    override fun ramBytesUsed(): Long {
        return ramBytesUsed
    }

    override fun toString(): String {
        return "SparseFixedBitSet(size=" + length + ",cardinality=~" + approximateCardinality()
    }

    companion object {
        private val BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(SparseFixedBitSet::class)
        private val SINGLE_ELEMENT_ARRAY_BYTES_USED = RamUsageEstimator.sizeOf(LongArray(1))
        private const val MASK_4096 = (1 shl 12) - 1

        private fun blockCount(length: Int): Int {
            var blockCount = length ushr 12
            if ((blockCount shl 12) < length) {
                ++blockCount
            }
            require((blockCount shl 12) >= length)
            return blockCount
        }

        private fun oversize(s: Int): Int {
            var newSize = s + (s ushr 1)
            if (newSize > 50) {
                newSize = 64
            }
            return newSize
        }

        // create a long that has bits set to one between from and to
        private fun mask(from: Int, to: Int): Long {
            return ((1L shl (to - from) shl 1) - 1) shl from
        }
    }
}
