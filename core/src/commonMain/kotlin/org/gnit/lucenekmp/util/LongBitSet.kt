package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import kotlin.math.min


/**
 * BitSet of fixed length (numBits), backed by accessible ([.getBits]) long[], accessed with a
 * long index. Use it only if you intend to store more than 2.1B bits, otherwise you should use
 * [FixedBitSet].
 *
 * @lucene.internal
 */
class LongBitSet : Accountable {
    /** Expert.  */
    val bits: LongArray // Array of longs holding the bits
    private val numBits: Long // The number of bits in use
    private val numWords: Int // The exact number of longs needed to hold numBits (<= bits.length)

    /**
     * Creates a new LongBitSet. The internally allocated long array will be exactly the size needed
     * to accommodate the numBits specified.
     *
     * @param numBits the number of bits needed
     */
    constructor(numBits: Long) {
        this.numBits = numBits
        bits = LongArray(bits2words(numBits))
        numWords = bits.size
    }

    /**
     * Creates a new LongBitSet using the provided long[] array as backing store. The storedBits array
     * must be large enough to accommodate the numBits specified, but may be larger. In that case the
     * 'extra' or 'ghost' bits must be clear (or they may provoke spurious side-effects)
     *
     * @param storedBits the array to use as backing store
     * @param numBits the number of bits actually needed
     */
    constructor(storedBits: LongArray, numBits: Long) {
        this.numWords = bits2words(numBits)
        require(numWords <= storedBits.size) { "The given long array is too small  to hold $numBits bits" }
        this.numBits = numBits
        this.bits = storedBits

        require(verifyGhostBitsClear())
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

        if ((numBits and 0x3fL) == 0L) return true

        val mask = -1L shl numBits.toInt()

        return (bits[numWords - 1] and mask) == 0L
    }

    /** Returns the number of bits stored in this bitset.  */
    fun length(): Long {
        return numBits
    }

    /**
     * Returns number of set bits. NOTE: this visits every long in the backing bits array, and the
     * result is not internally cached!
     */
    fun cardinality(): Long {
        // Depends on the ghost bits being clear!
        var tot: Long = 0
        for (i in 0..<numWords) {
            tot += Long.bitCount(bits[i]).toLong()
        }
        return tot
    }

    fun get(index: Long): Boolean {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val i = (index shr 6).toInt() // div 64
        // signed shift will keep a negative index and force an
        // array-index-out-of-bounds-exception, removing the need for an explicit check.
        val bitmask = 1L shl index.toInt()
        return (bits[i] and bitmask) != 0L
    }

    fun set(index: Long) {
        require(index >= 0 && index < numBits) { "index=$index numBits=$numBits" }
        val wordNum = (index shr 6).toInt() // div 64
        val bitmask = 1L shl index.toInt()
        bits[wordNum] = bits[wordNum] or bitmask
    }

    fun getAndSet(index: Long): Boolean {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val wordNum = (index shr 6).toInt() // div 64
        val bitmask = 1L shl index.toInt()
        val `val` = (bits[wordNum] and bitmask) != 0L
        bits[wordNum] = bits[wordNum] or bitmask
        return `val`
    }

    fun clear(index: Long) {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val wordNum = (index shr 6).toInt()
        val bitmask = 1L shl index.toInt()
        bits[wordNum] = bits[wordNum] and bitmask.inv()
    }

    fun getAndClear(index: Long): Boolean {
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        val wordNum = (index shr 6).toInt() // div 64
        val bitmask = 1L shl index.toInt()
        val `val` = (bits[wordNum] and bitmask) != 0L
        bits[wordNum] = bits[wordNum] and bitmask.inv()
        return `val`
    }

    /**
     * Returns the index of the first set bit starting at the index specified. -1 is returned if there
     * are no more set bits.
     */
    fun nextSetBit(index: Long): Long {
        // Depends on the ghost bits being clear!
        require(index >= 0 && index < numBits) { "index=$index, numBits=$numBits" }
        var i = (index shr 6).toInt()
        var word = bits[i] shr index.toInt() // skip all the bits to the right of index

        if (word != 0L) {
            return index + Long.numberOfTrailingZeros(word)
        }

        while (++i < numWords) {
            word = bits[i]
            if (word != 0L) {
                return ((i shl 6) + Long.numberOfTrailingZeros(word)).toLong()
            }
        }

        return -1
    }

    /**
     * Returns the index of the last set bit before or on the index specified. -1 is returned if there
     * are no more set bits.
     */
    fun prevSetBit(index: Long): Long {
        require(index >= 0 && index < numBits) { "index=$index numBits=$numBits" }
        var i = (index shr 6).toInt()
        val subIndex = (index and 0x3fL).toInt() // index within the word
        var word = (bits[i] shl (63 - subIndex)) // skip all the bits to the left of index

        if (word != 0L) {
            return ((i shl 6) + subIndex - Long.numberOfLeadingZeros(word) // See LUCENE-3197
                    ).toLong()
        }

        while (--i >= 0) {
            word = bits[i]
            if (word != 0L) {
                return ((i shl 6) + 63 - Long.numberOfLeadingZeros(word)).toLong()
            }
        }

        return -1
    }

    /** this = this OR other  */
    fun or(other: LongBitSet) {
        require(
            other.numWords <= numWords
        ) { "numWords=" + numWords + ", other.numWords=" + other.numWords }
        var pos = min(numWords, other.numWords)
        while (--pos >= 0) {
            bits[pos] = bits[pos] or other.bits[pos]
        }
    }

    /** this = this XOR other  */
    fun xor(other: LongBitSet) {
        require(
            other.numWords <= numWords
        ) { "numWords=" + numWords + ", other.numWords=" + other.numWords }
        var pos = min(numWords, other.numWords)
        while (--pos >= 0) {
            bits[pos] = bits[pos] xor other.bits[pos]
        }
    }

    /** returns true if the sets have any elements in common  */
    fun intersects(other: LongBitSet): Boolean {
        // Depends on the ghost bits being clear!
        var pos = min(numWords, other.numWords)
        while (--pos >= 0) {
            if ((bits[pos] and other.bits[pos]) != 0L) return true
        }
        return false
    }

    /** this = this AND other  */
    fun and(other: LongBitSet) {
        var pos = min(numWords, other.numWords)
        while (--pos >= 0) {
            bits[pos] = bits[pos] and other.bits[pos]
        }
        if (numWords > other.numWords) {
            Arrays.fill(bits, other.numWords, numWords, 0L)
        }
    }

    /** this = this AND NOT other  */
    fun andNot(other: LongBitSet) {
        var pos = min(numWords, other.numWords)
        while (--pos >= 0) {
            bits[pos] = bits[pos] and other.bits[pos].inv()
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

        for (i in 0..<count) {
            if (bits[i] != 0L) return false
        }

        return true
    }

    /**
     * Flips a range of bits
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to flip
     */
    fun flip(startIndex: Long, endIndex: Long) {
        require(startIndex >= 0 && startIndex < numBits)
        require(endIndex >= 0 && endIndex <= numBits)
        if (endIndex <= startIndex) {
            return
        }

        val startWord = (startIndex shr 6).toInt()
        val endWord = ((endIndex - 1) shr 6).toInt()

        /* Grrr, java shifting uses only the lower 6 bits of the count so -1L>>>64 == -1
     * for that reason, make sure not to use endmask if the bits to flip will
     * be zero in the last word (redefine endWord to be the last changed...)
     * long startmask = -1L << (startIndex & 0x3f);     // example: 11111...111000
     * long endmask = -1L >>> (64-(endIndex & 0x3f));   // example: 00111...111111
     */
        val startmask = -1L shl startIndex.toInt()

        // 64-(endIndex&0x3f) is the same as -endIndex since only the lowest 6 bits are used
        val endmask = -1L ushr -endIndex.toInt()

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
    fun flip(index: Long) {
        require(index >= 0 && index < numBits) { "index=$index numBits=$numBits" }
        val wordNum = (index shr 6).toInt() // div 64
        val bitmask = 1L shl index.toInt() // mod 64 is implicit
        bits[wordNum] = bits[wordNum] xor bitmask
    }

    /**
     * Sets a range of bits
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to set
     */
    fun set(startIndex: Long, endIndex: Long) {
        require(
            startIndex >= 0 && startIndex < numBits
        ) { "startIndex=$startIndex, numBits=$numBits" }
        require(endIndex >= 0 && endIndex <= numBits) { "endIndex=$endIndex, numBits=$numBits" }
        if (endIndex <= startIndex) {
            return
        }

        val startWord = (startIndex shr 6).toInt()
        val endWord = ((endIndex - 1) shr 6).toInt()

        val startmask = -1L shl startIndex.toInt()

        // 64-(endIndex&0x3f) is the same as -endIndex since only the lowest 6 bits are used
        val endmask = -1L ushr -endIndex.toInt()

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] or (startmask and endmask)
            return
        }

        bits[startWord] = bits[startWord] or startmask
        Arrays.fill(bits, startWord + 1, endWord, -1L)
        bits[endWord] = bits[endWord] or endmask
    }

    /**
     * Clears a range of bits.
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to clear
     */
    fun clear(startIndex: Long, endIndex: Long) {
        require(
            startIndex >= 0 && startIndex < numBits
        ) { "startIndex=$startIndex, numBits=$numBits" }
        require(endIndex >= 0 && endIndex <= numBits) { "endIndex=$endIndex, numBits=$numBits" }
        if (endIndex <= startIndex) {
            return
        }

        val startWord = (startIndex shr 6).toInt()
        val endWord = ((endIndex - 1) shr 6).toInt()

        var startmask = -1L shl startIndex.toInt()
        var endmask = -1L ushr -endIndex.toInt()

        // invert masks since we are clearing
        startmask = startmask.inv()
        endmask = endmask.inv()

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] and (startmask or endmask)
            return
        }

        bits[startWord] = bits[startWord] and startmask
        Arrays.fill(bits, startWord + 1, endWord, 0L)
        bits[endWord] = bits[endWord] and endmask
    }

    fun clone(): LongBitSet {
        val bits = LongArray(this.bits.size)
        /*java.lang.System.arraycopy(this.bits, 0, bits, 0, numWords)*/
        this.bits.copyInto(
            destination = bits,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = numWords,
        )

        return LongBitSet(bits, numBits)
    }

    /** returns true if both sets have the same bits set  */
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is LongBitSet) {
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

    public override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES + RamUsageEstimator.sizeOfObject(bits)
    }

    companion object {
        private val BASE_RAM_BYTES = RamUsageEstimator.shallowSizeOfInstance(LongBitSet::class)

        /**
         * If the given [LongBitSet] is large enough to hold `numBits+1`, returns the given
         * bits, otherwise returns a new [LongBitSet] which can hold the requested number of bits.
         *
         *
         * **NOTE:** the returned bitset reuses the underlying `long[]` of the given `bits` if possible. Also, calling [.length] on the returned bits may return a value
         * greater than `numBits`.
         */
        fun ensureCapacity(bits: LongBitSet, numBits: Long): LongBitSet {
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
                return LongBitSet(arr, arr.size.toLong() shl 6)
            }
        }

        /** The maximum `numBits` supported.  */
        val MAX_NUM_BITS: Long = 64 * ArrayUtil.MAX_ARRAY_LENGTH as Long

        /** Returns the number of 64 bit words it would take to hold numBits  */
        fun bits2words(numBits: Long): Int {
            require(!(numBits < 0 || numBits > MAX_NUM_BITS)) { "numBits must be 0 .. $MAX_NUM_BITS; got: $numBits" }
            // I.e.: get the word-offset of the last bit and add one (make sure to use >> so 0
            // returns 0!)
            return ((numBits - 1) shr 6).toInt() + 1
        }
    }
}
