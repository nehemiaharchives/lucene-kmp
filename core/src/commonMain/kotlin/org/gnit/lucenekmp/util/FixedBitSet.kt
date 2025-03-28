package org.gnit.lucenekmp.util

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math.toIntExact
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlin.math.max
import kotlin.math.min


/**
 * BitSet of fixed length (numBits), backed by accessible ([.getBits]) long[], accessed with
 * an int index, implementing [Bits] and [DocIdSet]. If you need to manage more than
 * 2.1B bits, use [LongBitSet].
 *
 * @lucene.internal
 */
class FixedBitSet : BitSet {
    /** Expert.  */
    val bits: LongArray // Array of longs holding the bits
    private val numBits: Int // The number of bits in use
    private val numWords: Int // The exact number of longs needed to hold numBits (<= bits.length)

    /**
     * Creates a new FixedBitSet. The internally allocated long array will be exactly the size needed
     * to accommodate the numBits specified.
     *
     * @param numBits the number of bits needed
     */
    constructor(numBits: Int) {
        this.numBits = numBits
        bits = LongArray(bits2words(numBits))
        numWords = bits.size
    }

    /**
     * Creates a new FixedBitSet using the provided long[] array as backing store. The storedBits
     * array must be large enough to accommodate the numBits specified, but may be larger. In that
     * case the 'extra' or 'ghost' bits must be clear (or they may provoke spurious side-effects)
     *
     * @param storedBits the array to use as backing store
     * @param numBits the number of bits actually needed
     */
    constructor(storedBits: LongArray, numBits: Int) {
        this.numWords = bits2words(numBits)
        require(numWords <= storedBits.size) { "The given long array is too small  to hold $numBits bits" }
        this.numBits = numBits
        this.bits = storedBits

        require(verifyGhostBitsClear())
    }

    override fun clear() {
        bits.fill(0L)
    }

    /**
     * Checks if the bits past numBits are clear. Some methods rely on this implicit assumption:
     * search for "Depends on the ghost bits being clear!"
     *
     * @return true if the bits past numBits are clear.
     */
    private fun verifyGhostBitsClear(): Boolean {
        for (i in numWords..<bits.size) {
            if (bits[i] != 0L) return false
        }

        if ((numBits and 0x3f) == 0) return true

        val mask = -1L shl numBits

        return (bits[numWords - 1] and mask) == 0L
    }

    override fun length(): Int {
        return numBits
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(bits)
    }

    /**
     * Returns number of set bits. NOTE: this visits every long in the backing bits array, and the
     * result is not internally cached!
     */
    override fun cardinality(): Int {
        // Depends on the ghost bits being clear!
        var tot: Long = 0
        for (i in 0..<numWords) {
            tot += Long.bitCount(bits[i]).toLong()
        }
        return toIntExact(tot)
    }

    override fun approximateCardinality(): Int {
        // Naive sampling: compute the number of bits that are set on the first 16 longs every 1024
        // longs and scale the result by 1024/16.
        // This computes the pop count on ranges instead of single longs in order to take advantage of
        // vectorization.

        val rangeLength = 16
        val interval = 1024

        if (numWords <= interval) {
            return cardinality()
        }

        var popCount: Long = 0
        var maxWord = 0
        while (maxWord + interval < numWords) {
            for (i in 0..<rangeLength) {
                popCount += Long.bitCount(bits[maxWord + i]).toLong()
            }
            maxWord += interval
        }

        popCount *= ((interval / rangeLength) * numWords / maxWord).toLong()
        return popCount.toInt()
    }

    override fun get(index: Int): Boolean {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val i = index shr 6 // div 64
        // signed shift will keep a negative index and force an
        // array-index-out-of-bounds-exception, removing the need for an explicit check.
        val bitmask = 1L shl index
        return (bits[i] and bitmask) != 0L
    }

    override fun set(index: Int) {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val wordNum = index shr 6 // div 64
        val bitmask = 1L shl index
        bits[wordNum] = bits[wordNum] or bitmask
    }

    override fun getAndSet(index: Int): Boolean {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val wordNum = index shr 6 // div 64
        val bitmask = 1L shl index
        val `val` = (bits[wordNum] and bitmask) != 0L
        bits[wordNum] = bits[wordNum] or bitmask
        return `val`
    }

    override fun clear(index: Int) {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val wordNum = index shr 6
        val bitmask = 1L shl index
        bits[wordNum] = bits[wordNum] and bitmask.inv()
    }

    fun getAndClear(index: Int): Boolean {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val wordNum = index shr 6 // div 64
        val bitmask = 1L shl index
        val `val` = (bits[wordNum] and bitmask) != 0L
        bits[wordNum] = bits[wordNum] and bitmask.inv()
        return `val`
    }

    override fun nextSetBit(index: Int): Int {
        // Override with a version that skips the bound check on the result since we know it will not
        // go OOB:
        return nextSetBitInRange(index, numBits)
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
        // Depends on the ghost bits being clear!
        require(start >= 0 && start < numBits) { "index=$start, numBits=$numBits" }
        require(start < upperBound) { "index=$start, upperBound=$upperBound" }
        require(upperBound <= numBits) { "upperBound=$upperBound, numBits=$numBits" }
        var i = start shr 6
        var word = bits[i] shr start // skip all the bits to the right of index

        if (word != 0L) {
            return start + Long.numberOfTrailingZeros(word)
        }

        val limit = if (upperBound == numBits) numWords else bits2words(upperBound)
        while (++i < limit) {
            word = bits[i]
            if (word != 0L) {
                return (i shl 6) + Long.numberOfTrailingZeros(word)
            }
        }

        return DocIdSetIterator.NO_MORE_DOCS
    }

    override fun prevSetBit(index: Int): Int {
        require(index >= 0 && index < numBits) { "index=$index numBits=$numBits" }
        var i = index shr 6
        val subIndex = index and 0x3f // index within the word
        var word = (bits[i] shl (63 - subIndex)) // skip all the bits to the left of index

        if (word != 0L) {
            return (i shl 6) + subIndex - Long.numberOfLeadingZeros(word) // See LUCENE-3197
        }

        while (--i >= 0) {
            word = bits[i]
            if (word != 0L) {
                return (i shl 6) + 63 - Long.numberOfLeadingZeros(word)
            }
        }

        return -1
    }

    @Throws(IOException::class)
    public override fun or(iter: DocIdSetIterator) {
        checkUnpositioned(iter)
        iter.nextDoc()
        iter.intoBitSet(DocIdSetIterator.NO_MORE_DOCS, this, 0)
    }

    /** this = this OR other  */
    fun or(other: FixedBitSet) {
        orRange(other, 0, this, 0, other.length())
    }

    /** this = this XOR other  */
    fun xor(other: FixedBitSet) {
        xor(other.bits, other.numWords)
    }

    /** Does in-place XOR of the bits provided by the iterator.  */
    @Throws(IOException::class)
    fun xor(iter: DocIdSetIterator) {
        checkUnpositioned(iter)
        val fixedBitSet = BitSetIterator.getFixedBitSetOrNull(iter)
        if (fixedBitSet != null) {
            val bits: FixedBitSet = fixedBitSet
            xor(bits)
        } else {
            var doc: Int
            while ((iter.nextDoc().also { doc = it }) < numBits) {
                flip(doc)
            }
        }
    }

    private fun xor(otherBits: LongArray, otherNumWords: Int) {
        require(otherNumWords <= numWords) { "numWords=$numWords, other.numWords=$otherNumWords" }
        val thisBits = this.bits
        var pos = min(numWords, otherNumWords)
        while (--pos >= 0) {
            thisBits[pos] = thisBits[pos] xor otherBits[pos]
        }
    }

    /** returns true if the sets have any elements in common  */
    fun intersects(other: FixedBitSet): Boolean {
        // Depends on the ghost bits being clear!
        var pos = min(numWords, other.numWords)
        while (--pos >= 0) {
            if ((bits[pos] and other.bits[pos]) != 0L) return true
        }
        return false
    }

    /** this = this AND other  */
    fun and(other: FixedBitSet) {
        and(other.bits, other.numWords)
    }

    private fun and(otherArr: LongArray, otherNumWords: Int) {
        val thisArr = this.bits
        var pos = min(this.numWords, otherNumWords)
        while (--pos >= 0) {
            thisArr[pos] = thisArr[pos] and otherArr[pos]
        }
        if (this.numWords > otherNumWords) {
            /*java.util.Arrays.fill(thisArr, otherNumWords, this.numWords, 0L)*/
            thisArr.fill(0L, otherNumWords, this.numWords)
        }
    }

    @Throws(IOException::class)
    fun andNot(iter: DocIdSetIterator) {
        if (BitSetIterator.getFixedBitSetOrNull(iter) != null) {
            checkUnpositioned(iter)
            val bits: FixedBitSet = checkNotNull(BitSetIterator.getFixedBitSetOrNull(iter))
            andNot(bits)
        } else if (iter is DocBaseBitSetIterator) {
            checkUnpositioned(iter)
            val baseIter: DocBaseBitSetIterator = iter
            andNot(baseIter.docBase shr 6, baseIter.bitSet)
        } else {
            checkUnpositioned(iter)
            var doc: Int = iter.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                clear(doc)
                doc = iter.nextDoc()
            }
        }
    }

    /** this = this AND NOT other  */
    fun andNot(other: FixedBitSet) {
        andNot(0, other.bits, other.numWords)
    }

    private fun andNot(otherOffsetWords: Int, other: FixedBitSet) {
        andNot(otherOffsetWords, other.bits, other.numWords)
    }

    private fun andNot(otherOffsetWords: Int, otherArr: LongArray, otherNumWords: Int) {
        var pos = min(numWords - otherOffsetWords, otherNumWords)
        val thisArr = this.bits
        while (--pos >= 0) {
            thisArr[pos + otherOffsetWords] = thisArr[pos + otherOffsetWords] and otherArr[pos].inv()
        }
    }

    /**
     * Scans the backing store to check if all bits are clear. The method is deliberately not called
     * "isEmpty" to emphasize it is not low cost (as isEmpty usually is).
     *
     * @return true if all bits are clear.
     */
    fun scanIsEmpty(): Boolean {
        // This 'slow' implementation is still faster than any external one could be
        // (e.g.: (bitSet.length() == 0 || bitSet.nextSetBit(0) == -1))
        // especially for small BitSets
        // Depends on the ghost bits being clear!
        val count = numWords

        var i = 0
        while (i < count) {
            val cmpLen = min(ZEROES.size, bits.size - i)
            if (Arrays.equals(bits, i, i + cmpLen, ZEROES, 0, cmpLen) == false) {
                return false
            }
            i += ZEROES.size
        }

        return true
    }

    /**
     * Flips a range of bits
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to flip
     */
    fun flip(startIndex: Int, endIndex: Int) {
        require(startIndex >= 0 && startIndex < numBits)
        require(endIndex >= 0 && endIndex <= numBits)
        if (endIndex <= startIndex) {
            return
        }

        val startWord = startIndex shr 6
        val endWord = (endIndex - 1) shr 6

        /* Grrr, java shifting uses only the lower 6 bits of the count so -1L>>>64 == -1
     * for that reason, make sure not to use endmask if the bits to flip will
     * be zero in the last word (redefine endWord to be the last changed...)
     * long startmask = -1L << (startIndex & 0x3f);     // example: 11111...111000
     * long endmask = -1L >>> (64-(endIndex & 0x3f));   // example: 00111...111111
     */
        val startmask = -1L shl startIndex
        val endmask = -1L ushr -endIndex

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] xor (startmask and endmask)
            return
        }

        bits[startWord] = bits[startWord] xor startmask

        for (i in startWord + 1..<endWord) {
            bits[i] = bits[i].inv()
        }

        bits[endWord] = bits[endWord] xor endmask
    }

    /** Flip the bit at the provided index.  */
    fun flip(index: Int) {
        require(index >= 0 && index < numBits) { "index=$index numBits=$numBits" }
        val wordNum = index shr 6 // div 64
        val bitmask = 1L shl index // mod 64 is implicit
        bits[wordNum] = bits[wordNum] xor bitmask
    }

    /**
     * Sets a range of bits
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to set
     */
    fun set(startIndex: Int, endIndex: Int) {
        require(
            startIndex >= 0 && startIndex < numBits
        ) { "startIndex=$startIndex, numBits=$numBits" }
        require(endIndex >= 0 && endIndex <= numBits) { "endIndex=$endIndex, numBits=$numBits" }
        if (endIndex <= startIndex) {
            return
        }

        val startWord = startIndex shr 6
        val endWord = (endIndex - 1) shr 6

        val startmask = -1L shl startIndex
        val endmask = -1L ushr -endIndex

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] or (startmask and endmask)
            return
        }

        bits[startWord] = bits[startWord] or startmask
        /*java.util.Arrays.fill(bits, startWord + 1, endWord, -1L)*/
        bits.fill(element = -1L,
            fromIndex = startWord + 1,
            toIndex = endWord
        )
        bits[endWord] = bits[endWord] or endmask
    }

    override fun clear(startIndex: Int, endIndex: Int) {
        require(
            startIndex >= 0 && startIndex < numBits
        ) { "startIndex=$startIndex, numBits=$numBits" }
        require(endIndex >= 0 && endIndex <= numBits) { "endIndex=$endIndex, numBits=$numBits" }
        if (endIndex <= startIndex) {
            return
        }

        val startWord = startIndex shr 6
        val endWord = (endIndex - 1) shr 6

        var startmask = -1L shl startIndex
        var endmask = -1L ushr -endIndex

        // invert masks since we are clearing
        startmask = startmask.inv()
        endmask = endmask.inv()

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] and (startmask or endmask)
            return
        }

        bits[startWord] = bits[startWord] and startmask
        /*java.util.Arrays.fill(bits, startWord + 1, endWord, 0L)*/
        bits.fill(element = 0L,
            fromIndex = startWord + 1,
            toIndex = endWord
        )
        bits[endWord] = bits[endWord] and endmask
    }

    fun clone(): FixedBitSet {
        val bits = LongArray(this.bits.size)
        /*java.lang.System.arraycopy(this.bits, 0, bits, 0, numWords)*/
        this.bits.copyInto(
            destination = bits,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = numWords
        )
        return FixedBitSet(bits, numBits)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is FixedBitSet) {
            return false
        }
        val other = o
        if (numBits != other.numBits) {
            return false
        }
        // Depends on the ghost bits being clear!
        return bits.contentEquals(other.bits)
    }

    override fun hashCode(): Int {
        // Depends on the ghost bits being clear!
        var h: Long = 0
        var i = numWords
        while (--i >= 0) {
            h = h xor bits[i]
            h = (h shl 1) or (h ushr 63) // rotate left
        }
        // fold leftmost bits into right and add a constant to prevent
        // empty sets from returning 0, which is too common.
        return ((h shr 32) xor h).toInt() + -0x6789edcc
    }

    /**
     * Convert this instance to read-only [Bits]. This is useful in the case that this [ ] is returned as a [Bits] instance, to make sure that consumers may not get
     * write access back by casting to a [FixedBitSet]. NOTE: Changes to this [ ] will be reflected on the returned [Bits].
     */
    fun asReadOnlyBits(): Bits {
        return FixedBits(bits, numBits)
    }

    public override fun applyMask(bitSet: FixedBitSet, offset: Int) {
        // Note: Some scorers don't track maxDoc and may thus call this method with an offset that is
        // beyond bitSet.length()
        val length = min(bitSet.length(), length() - offset)
        if (length >= 0) {
            andRange(this, offset, bitSet, 0, length)
        }
        require(
            !(length < bitSet.length()
                    && bitSet.nextSetBit(max(0, length)) != DocIdSetIterator.NO_MORE_DOCS)
        ) { "Some bits are set beyond the end of live docs" }
    }

    companion object {
        private val BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(FixedBitSet::class)

        // An array that is small enough to use reasonable amounts of RAM and large enough to allow
        // Arrays#mismatch to use SIMD instructions and multiple registers under the hood.
        private val ZEROES = LongArray(32)

        /**
         * If the given [FixedBitSet] is large enough to hold `numBits+1`, returns the given
         * bits, otherwise returns a new [FixedBitSet] which can hold the requested number of bits.
         *
         *
         * **NOTE:** the returned bitset reuses the underlying `long[]` of the given `bits` if possible. Also, calling [.length] on the returned bits may return a value
         * greater than `numBits`.
         */
        fun ensureCapacity(bits: FixedBitSet, numBits: Int): FixedBitSet {
            if (numBits < bits.numBits) {
                return bits
            } else {
                // Depends on the ghost bits being clear!
                // (Otherwise, they may become visible in the new instance)
                val numWords = bits2words(numBits)
                var arr = bits.bits
                if (numWords >= arr.size) {
                    arr = ArrayUtil.grow(arr, numWords + 1)
                }
                return FixedBitSet(arr, arr.size shl 6)
            }
        }

        /** returns the number of 64 bit words it would take to hold numBits  */
        fun bits2words(numBits: Int): Int {
            // I.e.: get the word-offset of the last bit and add one (make sure to use >> so 0
            // returns 0!)
            return ((numBits - 1) shr 6) + 1
        }

        /**
         * Returns the popcount or cardinality of the intersection of the two sets. Neither set is
         * modified.
         */
        fun intersectionCount(a: FixedBitSet, b: FixedBitSet): Long {
            // Depends on the ghost bits being clear!
            var tot: Long = 0
            val numCommonWords = min(a.numWords, b.numWords)
            for (i in 0..<numCommonWords) {
                tot += Long.bitCount(a.bits[i] and b.bits[i]).toLong()
            }
            return tot
        }

        /** Returns the popcount or cardinality of the union of the two sets. Neither set is modified.  */
        fun unionCount(a: FixedBitSet, b: FixedBitSet): Long {
            // Depends on the ghost bits being clear!
            var tot: Long = 0
            val numCommonWords = min(a.numWords, b.numWords)
            for (i in 0..<numCommonWords) {
                tot += Long.bitCount(a.bits[i] or b.bits[i]).toLong()
            }
            for (i in numCommonWords..<a.numWords) {
                tot += Long.bitCount(a.bits[i]).toLong()
            }
            for (i in numCommonWords..<b.numWords) {
                tot += Long.bitCount(b.bits[i]).toLong()
            }
            return tot
        }

        /**
         * Returns the popcount or cardinality of "a and not b" or "intersection(a, not(b))". Neither set
         * is modified.
         */
        fun andNotCount(a: FixedBitSet, b: FixedBitSet): Long {
            // Depends on the ghost bits being clear!
            var tot: Long = 0
            val numCommonWords = min(a.numWords, b.numWords)
            for (i in 0..<numCommonWords) {
                tot += Long.bitCount(a.bits[i] and b.bits[i].inv()).toLong()
            }
            for (i in numCommonWords..<a.numWords) {
                tot += Long.bitCount(a.bits[i]).toLong()
            }
            return tot
        }

        /** Read `numBits` (between 1 and 63) bits from `bitSet` at `from`.  */
        private fun readNBits(bitSet: LongArray, from: Int, numBits: Int): Long {
            require(numBits > 0 && numBits < Long.SIZE_BITS)
            var bits = bitSet[from shr 6] ushr from
            val numBitsSoFar: Int = Long.SIZE_BITS - (from and 0x3F)
            if (numBitsSoFar < numBits) {
                bits = bits or (bitSet[(from shr 6) + 1] shl -from)
            }
            return bits and ((1L shl numBits) - 1)
        }

        /**
         * Or `length` bits starting at `sourceFrom` from `source` into `dest`
         * starting at `destFrom`.
         */
        fun orRange(
            source: FixedBitSet, sourceFrom: Int, dest: FixedBitSet, destFrom: Int, length: Int
        ) {
            var sourceFrom = sourceFrom
            var destFrom = destFrom
            var length = length
            require(length >= 0)
            Objects.checkFromIndexSize(sourceFrom, length, source.length())
            Objects.checkFromIndexSize(destFrom, length, dest.length())

            if (length == 0) {
                return
            }

            val sourceBits = source.bits
            val destBits = dest.bits

            // First, align `destFrom` with a word start, ie. a multiple of Long.SIZE (64)
            if ((destFrom and 0x3F) != 0) {
                val numBitsNeeded = min(-destFrom and 0x3F, length)
                val bits = readNBits(sourceBits, sourceFrom, numBitsNeeded) shl destFrom
                destBits[destFrom shr 6] = destBits[destFrom shr 6] or bits

                sourceFrom += numBitsNeeded
                destFrom += numBitsNeeded
                length -= numBitsNeeded
            }

            if (length == 0) {
                return
            }

            require((destFrom and 0x3F) == 0)

            // Now OR at the word level
            val numFullWords = length shr 6
            val sourceWordFrom = sourceFrom shr 6
            val destWordFrom = destFrom shr 6

            // Note: these two for loops auto-vectorize
            if ((sourceFrom and 0x3F) == 0) {
                // sourceFrom and destFrom are both aligned with a long[]
                for (i in 0..<numFullWords) {
                    destBits[destWordFrom + i] = destBits[destWordFrom + i] or sourceBits[sourceWordFrom + i]
                }
            } else {
                for (i in 0..<numFullWords) {
                    destBits[destWordFrom + i] = destBits[destWordFrom + i] or
                            ((sourceBits[sourceWordFrom + i] ushr sourceFrom)
                                    or (sourceBits[sourceWordFrom + i + 1] shl -sourceFrom))
                }
            }

            sourceFrom += numFullWords shl 6
            destFrom += numFullWords shl 6
            length -= numFullWords shl 6

            // Finally handle tail bits
            if (length > 0) {
                val bits = readNBits(sourceBits, sourceFrom, length)
                destBits[destFrom shr 6] = destBits[destFrom shr 6] or bits
            }
        }

        /**
         * And `length` bits starting at `sourceFrom` from `source` into `dest`
         * starting at `destFrom`.
         */
        fun andRange(
            source: FixedBitSet, sourceFrom: Int, dest: FixedBitSet, destFrom: Int, length: Int
        ) {
            var sourceFrom = sourceFrom
            var destFrom = destFrom
            var length = length
            require(length >= 0) { length }
            Objects.checkFromIndexSize(sourceFrom, length, source.length())
            Objects.checkFromIndexSize(destFrom, length, dest.length())

            if (length == 0) {
                return
            }

            val sourceBits = source.bits
            val destBits = dest.bits

            // First, align `destFrom` with a word start, ie. a multiple of Long.SIZE (64)
            if ((destFrom and 0x3F) != 0) {
                val numBitsNeeded = min(-destFrom and 0x3F, length)
                var bits = readNBits(sourceBits, sourceFrom, numBitsNeeded) shl destFrom
                bits = bits or (((1L shl numBitsNeeded) - 1) shl destFrom).inv()
                destBits[destFrom shr 6] = destBits[destFrom shr 6] and bits

                sourceFrom += numBitsNeeded
                destFrom += numBitsNeeded
                length -= numBitsNeeded
            }

            if (length == 0) {
                return
            }

            require((destFrom and 0x3F) == 0)

            // Now AND at the word level
            val numFullWords = length shr 6
            val sourceWordFrom = sourceFrom shr 6
            val destWordFrom = destFrom shr 6

            // Note: these two for loops auto-vectorize
            if ((sourceFrom and 0x3F) == 0) {
                // sourceFrom and destFrom are both aligned with a long[]
                for (i in 0..<numFullWords) {
                    destBits[destWordFrom + i] = destBits[destWordFrom + i] and sourceBits[sourceWordFrom + i]
                }
            } else {
                for (i in 0..<numFullWords) {
                    destBits[destWordFrom + i] = destBits[destWordFrom + i] and
                            ((sourceBits[sourceWordFrom + i] ushr sourceFrom)
                                    or (sourceBits[sourceWordFrom + i + 1] shl -sourceFrom))
                }
            }

            sourceFrom += numFullWords shl 6
            destFrom += numFullWords shl 6
            length -= numFullWords shl 6

            // Finally handle tail bits
            if (length > 0) {
                var bits = readNBits(sourceBits, sourceFrom, length)
                bits = bits or (0L.inv() shl length)
                destBits[destFrom shr 6] = destBits[destFrom shr 6] and bits
            }
        }

        /** Make a copy of the given bits.  */
        fun copyOf(bits: Bits): FixedBitSet {
            var bits = bits
            if (bits is FixedBits) {
                // restore the original FixedBitSet
                bits = bits.bitSet
            }

            if (bits is FixedBitSet) {
                return bits.clone()
            } else {
                val length = bits.length()
                val bitSet = FixedBitSet(length)
                bitSet.set(0, length)
                for (i in 0..<length) {
                    if (bits.get(i) == false) {
                        bitSet.clear(i)
                    }
                }
                return bitSet
            }
        }
    }
}
