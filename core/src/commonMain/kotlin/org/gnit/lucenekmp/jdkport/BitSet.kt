package org.gnit.lucenekmp.jdkport

import kotlin.math.max
import kotlin.math.min


/**
 * port of java.util.BitSet
 *
 * This class implements a vector of bits that grows as needed. Each
 * component of the bit set has a `boolean` value. The
 * bits of a `BitSet` are indexed by nonnegative integers.
 * Individual indexed bits can be examined, set, or cleared. One
 * `BitSet` may be used to modify the contents of another
 * `BitSet` through logical AND, logical inclusive OR, and
 * logical exclusive OR operations.
 *
 *
 * By default, all bits in the set initially have the value
 * `false`.
 *
 *
 * Every bit set has a current size, which is the number of bits
 * of space currently in use by the bit set. Note that the size is
 * related to the implementation of a bit set, so it may change with
 * implementation. The length of a bit set relates to logical length
 * of a bit set and is defined independently of implementation.
 *
 *
 * Unless otherwise noted, passing a null parameter to any of the
 * methods in a `BitSet` will result in a
 * `NullPointerException`.
 *
 *
 * A `BitSet` is not safe for multithreaded use without
 * external synchronization.
 *
 * @author  Arthur van Hoff
 * @author  Michael McCloskey
 * @author  Martin Buchholz
 * @since   1.0
 */
class BitSet : Cloneable<BitSet> {
    /**
     * The internal field corresponding to the serialField "bits".
     */
    private lateinit var words: LongArray

    /**
     * The number of words in the logical size of this BitSet.
     */
    private var wordsInUse = 0

    /**
     * Whether the size of "words" is user-specified.  If so, we assume
     * the user knows what he's doing and try harder to preserve it.
     */
    private var sizeIsSticky = false

    /**
     * Every public method must preserve these invariants.
     */
    private fun checkInvariants() {
        assert(wordsInUse == 0 || words[wordsInUse - 1] != 0L)
        assert(wordsInUse >= 0 && wordsInUse <= words.size)
        assert(wordsInUse == words.size || words[wordsInUse] == 0L)
    }

    /**
     * Sets the field wordsInUse to the logical size in words of the bit set.
     * WARNING:This method assumes that the number of words actually in use is
     * less than or equal to the current value of wordsInUse!
     */
    private fun recalculateWordsInUse() {
        // Traverse the bitset until a used word is found
        var i: Int
        i = wordsInUse - 1
        while (i >= 0) {
            if (words[i] != 0L) break
            i--
        }

        wordsInUse = i + 1 // The new logical size
    }

    /**
     * Creates a new bit set. All bits are initially `false`.
     */
    constructor() {
        initWords(BITS_PER_WORD)
        sizeIsSticky = false
    }

    /**
     * Creates a bit set whose initial size is large enough to explicitly
     * represent bits with indices in the range `0` through
     * `nbits-1`. All bits are initially `false`.
     *
     * @param  nbits the initial size of the bit set
     * @throws NegativeArraySizeException if the specified initial size
     * is negative
     */
    constructor(nbits: Int) {
        // nbits can't be negative; size 0 is OK
        if (nbits < 0) throw Exception("nbits < 0: " + nbits)

        initWords(nbits)
        sizeIsSticky = true
    }

    private fun initWords(nbits: Int) {
        words = LongArray(wordIndex(nbits - 1) + 1)
    }

    /**
     * Creates a bit set using words as the internal representation.
     * The last word (if there is one) must be non-zero.
     */
    private constructor(words: LongArray) {
        this.words = words
        // Correctly initialize wordsInUse by finding the last non-zero word
        var i = words.size - 1
        while (i >= 0) {
            if (words[i] != 0L) break
            i--
        }
        this.wordsInUse = i + 1
        checkInvariants()
    }

    /**
     * Returns a new byte array containing all the bits in this bit set.
     *
     *
     * More precisely, if
     * <br></br>`byte[] bytes = s.toByteArray();`
     * <br></br>then `bytes.length == (s.length()+7)/8` and
     * <br></br>`s.get(n) == ((bytes[n/8] & (1<<(n%8))) != 0)`
     * <br></br>for all `n < 8 * bytes.length`.
     *
     * @return a byte array containing a little-endian representation
     * of all the bits in this bit set
     * @since 1.7
     */
    fun toByteArray(): ByteArray {
        // Find actual highest set bit
        var highest = wordsInUse - 1
        if (highest < 0) return ByteArray(0)

        // Find position of the highest non-zero bit
        var lastWord = words[highest]
        var lastByte = 8
        while (lastByte > 0 && (lastWord ushr ((lastByte - 1) * 8) and 0xFFL) == 0L) {
            lastByte--
        }
        val byteLen = highest * 8 + lastByte
        val bytes = ByteArray(byteLen)

        var k = 0
        for (i in 0..highest) {
            var w = words[i]
            for (j in 0 until 8) {
                if (k == bytes.size) break
                bytes[k++] = (w and 0xFF).toByte()
                w = w ushr 8
            }
        }
        return bytes
    }

    /**
     * Returns a new long array containing all the bits in this bit set.
     *
     *
     * More precisely, if
     * <br></br>`long[] longs = s.toLongArray();`
     * <br></br>then `longs.length == (s.length()+63)/64` and
     * <br></br>`s.get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)`
     * <br></br>for all `n < 64 * longs.length`.
     *
     * @return a long array containing a little-endian representation
     * of all the bits in this bit set
     * @since 1.7
     */
    fun toLongArray(): LongArray {
        return words.copyOf(wordsInUse)
    }

    /**
     * Ensures that the BitSet can hold enough words.
     * @param wordsRequired the minimum acceptable number of words.
     */
    private fun ensureCapacity(wordsRequired: Int) {
        if (words.size < wordsRequired) {
            // Allocate larger of doubled size or required size
            val request = max(2 * words.size, wordsRequired)
            words = words.copyOf(request)
            sizeIsSticky = false
        }
    }

    /**
     * Ensures that the BitSet can accommodate a given wordIndex,
     * temporarily violating the invariants.  The caller must
     * restore the invariants before returning to the user,
     * possibly using recalculateWordsInUse().
     * @param wordIndex the index to be accommodated.
     */
    private fun expandTo(wordIndex: Int) {
        val wordsRequired = wordIndex + 1
        if (wordsInUse < wordsRequired) {
            ensureCapacity(wordsRequired)
            wordsInUse = wordsRequired
        }
    }

    /**
     * Sets the bit at the specified index to the complement of its
     * current value.
     *
     * @param  bitIndex the index of the bit to flip
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.4
     */
    fun flip(bitIndex: Int) {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: " + bitIndex)

        val wordIndex = wordIndex(bitIndex)
        expandTo(wordIndex)

        words[wordIndex] = words[wordIndex] xor (1L shl bitIndex)

        recalculateWordsInUse()
        checkInvariants()
    }

    /**
     * Sets each bit from the specified `fromIndex` (inclusive) to the
     * specified `toIndex` (exclusive) to the complement of its current
     * value.
     *
     * @param  fromIndex index of the first bit to flip
     * @param  toIndex index after the last bit to flip
     * @throws IndexOutOfBoundsException if `fromIndex` is negative,
     * or `toIndex` is negative, or `fromIndex` is
     * larger than `toIndex`
     * @since  1.4
     */
    fun flip(fromIndex: Int, toIndex: Int) {
        checkRange(fromIndex, toIndex)

        if (fromIndex == toIndex) return

        val startWordIndex = wordIndex(fromIndex)
        val endWordIndex = wordIndex(toIndex - 1)
        expandTo(endWordIndex)

        val firstWordMask = WORD_MASK shl fromIndex
        val lastWordMask = WORD_MASK ushr -toIndex
        if (startWordIndex == endWordIndex) {
            // Case 1: One word
            words[startWordIndex] = words[startWordIndex] xor (firstWordMask and lastWordMask)
        } else {
            // Case 2: Multiple words
            // Handle first word
            words[startWordIndex] = words[startWordIndex] xor firstWordMask

            // Handle intermediate words, if any
            for (i in startWordIndex + 1..<endWordIndex) words[i] = words[i] xor WORD_MASK

            // Handle last word
            words[endWordIndex] = words[endWordIndex] xor lastWordMask
        }

        recalculateWordsInUse()
        checkInvariants()
    }

    /**
     * Sets the bit at the specified index to `true`.
     *
     * @param  bitIndex a bit index
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.0
     */
    fun set(bitIndex: Int) {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: " + bitIndex)

        val wordIndex = wordIndex(bitIndex)
        expandTo(wordIndex)

        words[wordIndex] = words[wordIndex] or (1L shl bitIndex) // Restores invariants

        checkInvariants()
    }

    /**
     * Sets the bit at the specified index to the specified value.
     *
     * @param  bitIndex a bit index
     * @param  value a boolean value to set
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.4
     */
    fun set(bitIndex: Int, value: Boolean) {
        if (value) set(bitIndex)
        else clear(bitIndex)
    }

    /**
     * Sets the bits from the specified `fromIndex` (inclusive) to the
     * specified `toIndex` (exclusive) to `true`.
     *
     * @param  fromIndex index of the first bit to be set
     * @param  toIndex index after the last bit to be set
     * @throws IndexOutOfBoundsException if `fromIndex` is negative,
     * or `toIndex` is negative, or `fromIndex` is
     * larger than `toIndex`
     * @since  1.4
     */
    fun set(fromIndex: Int, toIndex: Int) {
        checkRange(fromIndex, toIndex)

        if (fromIndex == toIndex) return

        // Increase capacity if necessary
        val startWordIndex = wordIndex(fromIndex)
        val endWordIndex = wordIndex(toIndex - 1)
        expandTo(endWordIndex)

        val firstWordMask = WORD_MASK shl fromIndex
        val lastWordMask = WORD_MASK ushr -toIndex
        if (startWordIndex == endWordIndex) {
            // Case 1: One word
            words[startWordIndex] = words[startWordIndex] or (firstWordMask and lastWordMask)
        } else {
            // Case 2: Multiple words
            // Handle first word
            words[startWordIndex] = words[startWordIndex] or firstWordMask

            // Handle intermediate words, if any
            for (i in startWordIndex + 1..<endWordIndex) words[i] = WORD_MASK

            // Handle last word (restores invariants)
            words[endWordIndex] = words[endWordIndex] or lastWordMask
        }

        checkInvariants()
    }

    /**
     * Sets the bits from the specified `fromIndex` (inclusive) to the
     * specified `toIndex` (exclusive) to the specified value.
     *
     * @param  fromIndex index of the first bit to be set
     * @param  toIndex index after the last bit to be set
     * @param  value value to set the selected bits to
     * @throws IndexOutOfBoundsException if `fromIndex` is negative,
     * or `toIndex` is negative, or `fromIndex` is
     * larger than `toIndex`
     * @since  1.4
     */
    fun set(fromIndex: Int, toIndex: Int, value: Boolean) {
        if (value) set(fromIndex, toIndex)
        else clear(fromIndex, toIndex)
    }

    /**
     * Sets the bit specified by the index to `false`.
     *
     * @param  bitIndex the index of the bit to be cleared
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.0
     */
    fun clear(bitIndex: Int) {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: " + bitIndex)

        val wordIndex = wordIndex(bitIndex)
        if (wordIndex >= wordsInUse) return

        words[wordIndex] = words[wordIndex] and (1L shl bitIndex).inv()

        recalculateWordsInUse()
        checkInvariants()
    }

    /**
     * Sets the bits from the specified `fromIndex` (inclusive) to the
     * specified `toIndex` (exclusive) to `false`.
     *
     * @param  fromIndex index of the first bit to be cleared
     * @param  toIndex index after the last bit to be cleared
     * @throws IndexOutOfBoundsException if `fromIndex` is negative,
     * or `toIndex` is negative, or `fromIndex` is
     * larger than `toIndex`
     * @since  1.4
     */
    fun clear(fromIndex: Int, toIndex: Int) {
        var toIndex = toIndex
        checkRange(fromIndex, toIndex)

        if (fromIndex == toIndex) return

        val startWordIndex = wordIndex(fromIndex)
        if (startWordIndex >= wordsInUse) return

        var endWordIndex = wordIndex(toIndex - 1)
        if (endWordIndex >= wordsInUse) {
            toIndex = length()
            endWordIndex = wordsInUse - 1
        }

        val firstWordMask = WORD_MASK shl fromIndex
        val lastWordMask = WORD_MASK ushr -toIndex
        if (startWordIndex == endWordIndex) {
            // Case 1: One word
            words[startWordIndex] = words[startWordIndex] and (firstWordMask and lastWordMask).inv()
        } else {
            // Case 2: Multiple words
            // Handle first word
            words[startWordIndex] = words[startWordIndex] and firstWordMask.inv()

            // Handle intermediate words, if any
            for (i in startWordIndex + 1..<endWordIndex) words[i] = 0

            // Handle last word
            words[endWordIndex] = words[endWordIndex] and lastWordMask.inv()
        }

        recalculateWordsInUse()
        checkInvariants()
    }

    /**
     * Sets all of the bits in this BitSet to `false`.
     *
     * @since 1.4
     */
    fun clear() {
        while (wordsInUse > 0) words[--wordsInUse] = 0
    }

    /**
     * Returns the value of the bit with the specified index. The value
     * is `true` if the bit with the index `bitIndex`
     * is currently set in this `BitSet`; otherwise, the result
     * is `false`.
     *
     * @param  bitIndex   the bit index
     * @return the value of the bit with the specified index
     * @throws IndexOutOfBoundsException if the specified index is negative
     */
    fun get(bitIndex: Int): Boolean {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: " + bitIndex)

        checkInvariants()

        val wordIndex = wordIndex(bitIndex)
        return (wordIndex < wordsInUse)
                && ((words[wordIndex] and (1L shl bitIndex)) != 0L)
    }

    /**
     * Returns a new `BitSet` composed of bits from this `BitSet`
     * from `fromIndex` (inclusive) to `toIndex` (exclusive).
     *
     * @param  fromIndex index of the first bit to include
     * @param  toIndex index after the last bit to include
     * @return a new `BitSet` from a range of this `BitSet`
     * @throws IndexOutOfBoundsException if `fromIndex` is negative,
     * or `toIndex` is negative, or `fromIndex` is
     * larger than `toIndex`
     * @since  1.4
     */
    fun get(fromIndex: Int, toIndex: Int): BitSet {
        var toIndex = toIndex
        checkRange(fromIndex, toIndex)

        checkInvariants()

        val len = length()

        // If no set bits in range return empty bitset
        if (len <= fromIndex || fromIndex == toIndex) return BitSet(0)

        // An optimization
        if (toIndex > len) toIndex = len

        val result = BitSet(toIndex - fromIndex)
        val targetWords = wordIndex(toIndex - fromIndex - 1) + 1
        var sourceIndex = wordIndex(fromIndex)
        val wordAligned = ((fromIndex and BIT_INDEX_MASK) == 0)

        // Process all words but the last word
        var i = 0
        while (i < targetWords - 1) {
            result.words[i] = if (wordAligned) words[sourceIndex] else (words[sourceIndex] ushr fromIndex) or
                    (words[sourceIndex + 1] shl -fromIndex)
            i++
            sourceIndex++
        }

        // Process the last word
        val lastWordMask = WORD_MASK ushr -toIndex
        result.words[targetWords - 1] =
            if (((toIndex - 1) and BIT_INDEX_MASK) < (fromIndex and BIT_INDEX_MASK))  /* straddles source words */
                ((words[sourceIndex] ushr fromIndex) or
                        ((words[sourceIndex + 1] and lastWordMask) shl -fromIndex))
            else
                ((words[sourceIndex] and lastWordMask) ushr fromIndex)

        // Set wordsInUse correctly
        result.wordsInUse = targetWords
        result.recalculateWordsInUse()
        result.checkInvariants()

        return result
    }

    /**
     * Returns the index of the first bit that is set to `true`
     * that occurs on or after the specified starting index. If no such
     * bit exists then `-1` is returned.
     *
     *
     * To iterate over the `true` bits in a `BitSet`,
     * use the following loop:
     *
     * <pre> `for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
     * // operate on index i here
     * if (i == Integer.MAX_VALUE) {
     * break; // or (i+1) would overflow
     * }
     * }`</pre>
     *
     * @param  fromIndex the index to start checking from (inclusive)
     * @return the index of the next set bit, or `-1` if there
     * is no such bit
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.4
     */
    fun nextSetBit(fromIndex: Int): Int {
        if (fromIndex < 0) throw IndexOutOfBoundsException("fromIndex < 0: " + fromIndex)

        checkInvariants()

        var u = wordIndex(fromIndex)
        if (u >= wordsInUse) return -1

        var word = words[u] and (WORD_MASK shl fromIndex)

        while (true) {
            if (word != 0L) return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word)
            if (++u == wordsInUse) return -1
            word = words[u]
        }
    }

    /**
     * Returns the index of the first bit that is set to `false`
     * that occurs on or after the specified starting index.
     *
     * @param  fromIndex the index to start checking from (inclusive)
     * @return the index of the next clear bit
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.4
     */
    fun nextClearBit(fromIndex: Int): Int {
        // Neither spec nor implementation handle bitsets of maximal length.
        // See 4816253.
        if (fromIndex < 0) throw IndexOutOfBoundsException("fromIndex < 0: " + fromIndex)

        checkInvariants()

        var u = wordIndex(fromIndex)
        if (u >= wordsInUse) return fromIndex

        var word = words[u].inv() and (WORD_MASK shl fromIndex)

        while (true) {
            if (word != 0L) return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word)
            if (++u == wordsInUse) return wordsInUse * BITS_PER_WORD
            word = words[u].inv()
        }
    }

    /**
     * Returns the index of the nearest bit that is set to `true`
     * that occurs on or before the specified starting index.
     * If no such bit exists, or if `-1` is given as the
     * starting index, then `-1` is returned.
     *
     *
     * To iterate over the `true` bits in a `BitSet`,
     * use the following loop:
     *
     * <pre> `for (int i = bs.length(); (i = bs.previousSetBit(i-1)) >= 0; ) {
     * // operate on index i here
     * }`</pre>
     *
     * @param  fromIndex the index to start checking from (inclusive)
     * @return the index of the previous set bit, or `-1` if there
     * is no such bit
     * @throws IndexOutOfBoundsException if the specified index is less
     * than `-1`
     * @since  1.7
     */
    fun previousSetBit(fromIndex: Int): Int {
        if (fromIndex < 0) {
            if (fromIndex == -1) return -1
            throw IndexOutOfBoundsException(
                "fromIndex < -1: " + fromIndex
            )
        }

        checkInvariants()

        var u = wordIndex(fromIndex)
        if (u >= wordsInUse) return length() - 1

        var word = words[u] and (WORD_MASK ushr -(fromIndex + 1))

        while (true) {
            if (word != 0L) return (u + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word)
            if (u-- == 0) return -1
            word = words[u]
        }
    }

    /**
     * Returns the index of the nearest bit that is set to `false`
     * that occurs on or before the specified starting index.
     * If no such bit exists, or if `-1` is given as the
     * starting index, then `-1` is returned.
     *
     * @param  fromIndex the index to start checking from (inclusive)
     * @return the index of the previous clear bit, or `-1` if there
     * is no such bit
     * @throws IndexOutOfBoundsException if the specified index is less
     * than `-1`
     * @since  1.7
     */
    fun previousClearBit(fromIndex: Int): Int {
        if (fromIndex < 0) {
            if (fromIndex == -1) return -1
            throw IndexOutOfBoundsException(
                "fromIndex < -1: " + fromIndex
            )
        }

        checkInvariants()

        var u = wordIndex(fromIndex)
        if (u >= wordsInUse) return fromIndex

        var word = words[u].inv() and (WORD_MASK ushr -(fromIndex + 1))

        while (true) {
            if (word != 0L) return (u + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word)
            if (u-- == 0) return -1
            word = words[u].inv()
        }
    }

    /**
     * Returns the "logical size" of this `BitSet`: the index of
     * the highest set bit in the `BitSet` plus one. Returns zero
     * if the `BitSet` contains no set bits.
     *
     * @return the logical size of this `BitSet`
     * @since  1.2
     */
    fun length(): Int {
        if (wordsInUse == 0) return 0

        return BITS_PER_WORD * (wordsInUse - 1) +
                (BITS_PER_WORD - Long.numberOfLeadingZeros(words[wordsInUse - 1]))
    }

    val isEmpty: Boolean
        /**
         * Returns true if this `BitSet` contains no bits that are set
         * to `true`.
         *
         * @return boolean indicating whether this `BitSet` is empty
         * @since  1.4
         */
        get() = wordsInUse == 0

    /**
     * Returns true if the specified `BitSet` has any bits set to
     * `true` that are also set to `true` in this `BitSet`.
     *
     * @param  set `BitSet` to intersect with
     * @return boolean indicating whether this `BitSet` intersects
     * the specified `BitSet`
     * @since  1.4
     */
    fun intersects(set: BitSet): Boolean {
        for (i in min(wordsInUse, set.wordsInUse) - 1 downTo 0) if ((words[i] and set.words[i]) != 0L) return true
        return false
    }

    /**
     * Returns the number of bits set to `true` in this `BitSet`.
     *
     * @return the number of bits set to `true` in this `BitSet`
     * @since  1.4
     */
    fun cardinality(): Int {
        var sum = 0
        for (i in 0..<wordsInUse) sum += Long.bitCount(words[i])
        return sum
    }

    /**
     * Performs a logical **AND** of this target bit set with the
     * argument bit set. This bit set is modified so that each bit in it
     * has the value `true` if and only if it both initially
     * had the value `true` and the corresponding bit in the
     * bit set argument also had the value `true`.
     *
     * @param set a bit set
     */
    fun and(set: BitSet) {
        if (this === set) return

        while (wordsInUse > set.wordsInUse) words[--wordsInUse] = 0

        // Perform logical AND on words in common
        for (i in 0..<wordsInUse) words[i] = words[i] and set.words[i]

        recalculateWordsInUse()
        checkInvariants()
    }

    /**
     * Performs a logical **OR** of this bit set with the bit set
     * argument. This bit set is modified so that a bit in it has the
     * value `true` if and only if it either already had the
     * value `true` or the corresponding bit in the bit set
     * argument has the value `true`.
     *
     * @param set a bit set
     */
    fun or(set: BitSet) {
        if (this === set) return

        val wordsInCommon = min(wordsInUse, set.wordsInUse)

        if (wordsInUse < set.wordsInUse) {
            ensureCapacity(set.wordsInUse)
            wordsInUse = set.wordsInUse
        }

        // Perform logical OR on words in common
        for (i in 0..<wordsInCommon) words[i] = words[i] or set.words[i]

        // Copy any remaining words
        if (wordsInCommon < set.wordsInUse) {
            /*java.lang.System.arraycopy(
                set.words, wordsInCommon,
                words, wordsInCommon,
                wordsInUse - wordsInCommon
            )*/
            set.words.copyInto(
                destination = words,
                destinationOffset = wordsInCommon,
                startIndex = wordsInCommon,
                endIndex = set.wordsInUse
            )
        }

        // recalculateWordsInUse() is unnecessary
        checkInvariants()
    }

    /**
     * Performs a logical **XOR** of this bit set with the bit set
     * argument. This bit set is modified so that a bit in it has the
     * value `true` if and only if one of the following
     * statements holds:
     *
     *  * The bit initially has the value `true`, and the
     * corresponding bit in the argument has the value `false`.
     *  * The bit initially has the value `false`, and the
     * corresponding bit in the argument has the value `true`.
     *
     *
     * @param  set a bit set
     */
    fun xor(set: BitSet) {
        val wordsInCommon = min(wordsInUse, set.wordsInUse)

        if (wordsInUse < set.wordsInUse) {
            ensureCapacity(set.wordsInUse)
            wordsInUse = set.wordsInUse
        }

        // Perform logical XOR on words in common
        for (i in 0..<wordsInCommon) words[i] = words[i] xor set.words[i]

        // Copy any remaining words
        if (wordsInCommon < set.wordsInUse) {
            /*java.lang.System.arraycopy(
                set.words, wordsInCommon,
                words, wordsInCommon,
                set.wordsInUse - wordsInCommon
            )*/
            set.words.copyInto(
                destination = words,
                destinationOffset = wordsInCommon,
                startIndex = wordsInCommon,
                endIndex = set.wordsInUse
            )
        }

        recalculateWordsInUse()
        checkInvariants()
    }

    /**
     * Clears all of the bits in this `BitSet` whose corresponding
     * bit is set in the specified `BitSet`.
     *
     * @param  set the `BitSet` with which to mask this
     * `BitSet`
     * @since  1.2
     */
    fun andNot(set: BitSet) {
        // Perform logical (a & !b) on words in common
        for (i in min(wordsInUse, set.wordsInUse) - 1 downTo 0) words[i] = words[i] and set.words[i].inv()

        recalculateWordsInUse()
        checkInvariants()
    }

    /**
     * {@return the hash code value for this bit set}
     *
     * The hash code depends only on which bits are set within this
     * `BitSet`.
     *
     *
     * The hash code is defined to be the result of the following
     * calculation:
     * <pre> `public int hashCode() {
     * long h = 1234;
     * long[] words = toLongArray();
     * for (int i = words.length; --i >= 0; )
     * h ^= words[i] * (i + 1);
     * return (int)((h >> 32) ^ h);
     * }`</pre>
     * Note that the hash code changes if the set of bits is altered.
     */
    override fun hashCode(): Int {
        var h: Long = 1234
        var i = wordsInUse
        while (--i >= 0) {
            h = h xor words[i] * (i + 1)
        }

        return ((h shr 32) xor h).toInt()
    }

    /**
     * Returns the number of bits of space actually in use by this
     * `BitSet` to represent bit values.
     * The maximum element in the set is the size - 1st element.
     *
     * @return the number of bits currently in this bit set
     */
    fun size(): Int {
        return words.size * BITS_PER_WORD
    }

    /**
     * Compares this bit set against the specified object.
     * The result is `true` if and only if the argument is
     * not `null` and is a `BitSet` object that has
     * exactly the same set of bits set to `true` as this bit
     * set. That is, for every nonnegative `int` index `k`,
     * <pre>((BitSet)obj).get(k) == this.get(k)</pre>
     * must be true. The current sizes of the two bit sets are not compared.
     *
     * @param  obj the object to compare with
     * @return `true` if the objects are the same;
     * `false` otherwise
     * @see .size
     */
    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj !is BitSet) return false

        checkInvariants()
        obj.checkInvariants()

        if (wordsInUse != obj.wordsInUse) return false

        // Check words in use by both BitSets
        return ArraysSupport.mismatch(words, 0, obj.words, 0, wordsInUse) == -1
    }

    /**
     * Returns a new BitSet that is a clone of this BitSet.
     * The clone is a new BitSet with the same internal state.
     */
    override fun clone(): BitSet {
        // Make a copy of the entire words array (preserving capacity).
        val clonedWords = words.copyOf()
        // Create a new BitSet using the cloned words.
        val clone = BitSet(clonedWords)
        // Copy the state that is not derived from the words array.
        clone.wordsInUse = this.wordsInUse
        clone.sizeIsSticky = this.sizeIsSticky
        // Ensure invariants hold in the clone.
        clone.checkInvariants()
        return clone
    }

    /**
     * Attempts to reduce internal storage used for the bits in this bit set.
     * Calling this method may, but is not required to, affect the value
     * returned by a subsequent call to the [.size] method.
     */
    fun trimToSize() {
        if (wordsInUse != words.size) {
            words = words.copyOf(wordsInUse)
            checkInvariants()
        }
    }

    /**
     * TODO implement if needed
     * Save the state of the `BitSet` instance to a stream (i.e.,
     * serialize it).
     */
    /*@Throws(IOException::class)
    private fun writeObject(s: java.io.ObjectOutputStream) {
        checkInvariants()

        if (!sizeIsSticky) trimToSize()

        val fields: java.io.ObjectOutputStream.PutField = s.putFields()
        fields.put("bits", words)
        s.writeFields()
    }*/

    /**
     * TODO implement if needed
     * Reconstitute the `BitSet` instance from a stream (i.e.,
     * deserialize it).
     */
    /*@Throws(IOException::class, Exception::class)
    private fun readObject(s: java.io.ObjectInputStream) {
        val fields: java.io.ObjectInputStream.GetField = s.readFields()
        words = fields.get("bits", null) as LongArray

        // Assume maximum length then find real length
        // because recalculateWordsInUse assumes maintenance
        // or reduction in logical size
        wordsInUse = words.size
        recalculateWordsInUse()
        sizeIsSticky = (words.size > 0 && words[words.size - 1] == 0L) // heuristic
        checkInvariants()
    }*/

    /**
     * Returns a string representation of this bit set. For every index
     * for which this `BitSet` contains a bit in the set
     * state, the decimal representation of that index is included in
     * the result. Such indices are listed in order from lowest to
     * highest, separated by ",&nbsp;" (a comma and a space) and
     * surrounded by braces, resulting in the usual mathematical
     * notation for a set of integers.
     *
     *
     * Example:
     * <pre>
     * BitSet drPepper = new BitSet();</pre>
     * Now `drPepper.toString()` returns "`{}`".
     * <pre>
     * drPepper.set(2);</pre>
     * Now `drPepper.toString()` returns "`{2}`".
     * <pre>
     * drPepper.set(4);
     * drPepper.set(10);</pre>
     * Now `drPepper.toString()` returns "`{2, 4, 10}`".
     *
     * @return a string representation of this bit set
     */
    override fun toString(): String {
        checkInvariants()

        val MAX_INITIAL_CAPACITY = Int.Companion.MAX_VALUE - 8
        val numBits = if (wordsInUse > 128) cardinality() else wordsInUse * BITS_PER_WORD
        // Avoid overflow in the case of a humongous numBits
        val initialCapacity = if (numBits <= (MAX_INITIAL_CAPACITY - 2) / 6) 6 * numBits + 2 else MAX_INITIAL_CAPACITY
        val b = StringBuilder(initialCapacity)
        b.append('{')

        var i = nextSetBit(0)
        if (i != -1) {
            b.append(i)
            while (true) {
                if (++i < 0) break
                if ((nextSetBit(i).also { i = it }) < 0) break
                val endOfRun = nextClearBit(i)
                do {
                    b.append(", ").append(i)
                } while (++i != endOfRun)
            }
        }

        b.append('}')
        return b.toString()
    }

    /**
     * TODO implement if needed
     * Returns a stream of indices for which this `BitSet`
     * contains a bit in the set state. The indices are returned
     * in order, from lowest to highest. The size of the stream
     * is the number of bits in the set state, equal to the value
     * returned by the [.cardinality] method.
     *
     *
     * The stream binds to this bit set when the terminal stream operation
     * commences (specifically, the spliterator for the stream is
     * [*late-binding*](Spliterator.html#binding)).  If the
     * bit set is modified during that operation then the result is undefined.
     *
     * @return a stream of integers representing set indices
     * @since 1.8
     */
    /*fun stream(): java.util.stream.IntStream {
        class BitSetSpliterator internal constructor(origin: Int, fence: Int, est: Int, root: Boolean) :
            java.util.Spliterator.OfInt {
            private var index: Int // current bit index for a set bit
            private var fence: Int // -1 until used; then one past last bit index
            private var est: Int // size estimate
            private var root: Boolean // true if root and not split

            // root == true then size estimate is accurate
            // index == -1 or index >= fence if fully traversed
            // Special case when the max bit set is Integer.MAX_VALUE
            init {
                this.index = origin
                this.fence = fence
                this.est = est
                this.root = root
            }

            fun getFence(): Int {
                var hi: Int
                if ((fence.also { hi = it }) < 0) {
                    // Round up fence to maximum cardinality for allocated words
                    // This is sufficient and cheap for sequential access
                    // When splitting this value is lowered
                    fence = if (wordsInUse >= wordIndex(Int.Companion.MAX_VALUE)) Int.Companion.MAX_VALUE else
                        wordsInUse shl ADDRESS_BITS_PER_WORD
                    hi = fence
                    est = cardinality()
                    index = nextSetBit(0)
                }
                return hi
            }

            override fun tryAdvance(action: java.util.function.IntConsumer?): Boolean {
                java.util.Objects.requireNonNull<java.util.function.IntConsumer?>(action)

                val hi = getFence()
                val i = index
                if (i < 0 || i >= hi) {
                    // Check if there is a final bit set for Integer.MAX_VALUE
                    if (i == Int.Companion.MAX_VALUE && hi == Int.Companion.MAX_VALUE) {
                        index = -1
                        action.accept(Int.Companion.MAX_VALUE)
                        return true
                    }
                    return false
                }

                index = nextSetBit(i + 1, wordIndex(hi - 1))
                action.accept(i)
                return true
            }

            override fun forEachRemaining(action: java.util.function.IntConsumer?) {
                java.util.Objects.requireNonNull<java.util.function.IntConsumer?>(action)

                val hi = getFence()
                var i = index
                index = -1

                if (i >= 0 && i < hi) {
                    action.accept(i++)

                    var u = wordIndex(i) // next lower word bound
                    val v = wordIndex(hi - 1) // upper word bound

                    words_loop@ while (u <= v && i <= hi) {
                        var word = words[u] and (WORD_MASK shl i)
                        while (word != 0L) {
                            i = (u shl ADDRESS_BITS_PER_WORD) + Long.numberOfTrailingZeros(word)
                            if (i >= hi) {
                                // Break out of outer loop to ensure check of
                                // Integer.MAX_VALUE bit set
                                break@words_loop
                            }

                            // Flip the set bit
                            word = word and (1L shl i).inv()

                            action.accept(i)
                        }
                        u++
                        i = u shl ADDRESS_BITS_PER_WORD
                    }
                }

                // Check if there is a final bit set for Integer.MAX_VALUE
                if (i == Int.Companion.MAX_VALUE && hi == Int.Companion.MAX_VALUE) {
                    action.accept(Int.Companion.MAX_VALUE)
                }
            }

            override fun trySplit(): java.util.Spliterator.OfInt? {
                var hi = getFence()
                val lo = index
                if (lo < 0) {
                    return null
                }

                // Lower the fence to be the upper bound of last bit set
                // The index is the first bit set, thus this spliterator
                // covers one bit and cannot be split, or two or more
                // bits
                fence = if (hi < Int.Companion.MAX_VALUE || !get(Int.Companion.MAX_VALUE))
                    previousSetBit(hi - 1) + 1
                else Int.Companion.MAX_VALUE
                hi = fence

                // Find the mid point
                val mid = (lo + hi) ushr 1
                if (lo >= mid) {
                    return null
                }

                // Raise the index of this spliterator to be the next set bit
                // from the mid point
                index = nextSetBit(mid, wordIndex(hi - 1))
                root = false

                // Don't lower the fence (mid point) of the returned spliterator,
                // traversal or further splitting will do that work
                return BitSetSpliterator(lo, mid, 1.let { est = est ushr it; est }, false)
            }

            override fun estimateSize(): Long {
                getFence() // force init
                return est.toLong()
            }

            override fun characteristics(): Int {
                // Only sized when root and not split
                return (if (root) java.util.Spliterator.SIZED else 0) or
                        java.util.Spliterator.ORDERED or java.util.Spliterator.DISTINCT or java.util.Spliterator.SORTED
            }

            val comparator: java.util.Comparator<in Int?>?
                get() = null
        }
        return java.util.stream.StreamSupport.intStream(BitSetSpliterator(0, -1, 0, true), false)
    }*/

    /**
     * Returns the index of the first bit that is set to `true`
     * that occurs on or after the specified starting index and up to and
     * including the specified word index
     * If no such bit exists then `-1` is returned.
     *
     * @param  fromIndex the index to start checking from (inclusive)
     * @param  toWordIndex the last word index to check (inclusive)
     * @return the index of the next set bit, or `-1` if there
     * is no such bit
     */
    private fun nextSetBit(fromIndex: Int, toWordIndex: Int): Int {
        var u = wordIndex(fromIndex)
        // Check if out of bounds
        if (u > toWordIndex) return -1

        var word = words[u] and (WORD_MASK shl fromIndex)

        while (true) {
            if (word != 0L) return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word)
            // Check if out of bounds
            if (++u > toWordIndex) return -1
            word = words[u]
        }
    }

    companion object {
        /*
     * BitSets are packed into arrays of "words."  Currently a word is
     * a long, which consists of 64 bits, requiring 6 address bits.
     * The choice of word size is determined purely by performance concerns.
     */
        private const val ADDRESS_BITS_PER_WORD = 6
        private val BITS_PER_WORD = 1 shl ADDRESS_BITS_PER_WORD
        private val BIT_INDEX_MASK = BITS_PER_WORD - 1

        /* Used to shift left or right for a partial word mask */
        private const val WORD_MASK = -0x1L

        /**
         * TODO implement if needed
         * @serialField bits long[]
         *
         * The bits in this BitSet.  The ith bit is stored in bits[i/64] at
         * bit position i % 64 (where bit position 0 refers to the least
         * significant bit and 63 refers to the most significant bit).
         */
        /*private val serialPersistentFields: Array<java.io.ObjectStreamField?> = arrayOf<java.io.ObjectStreamField?>(
            java.io.ObjectStreamField("bits", LongArray::class.java),
        )*/

        /* use serialVersionUID from JDK 1.0.2 for interoperability
        @java.io.Serial
        private const val serialVersionUID = 7997698588986878753L
        */


        /**
         * Given a bit index, return word index containing it.
         */
        private fun wordIndex(bitIndex: Int): Int {
            return bitIndex shr ADDRESS_BITS_PER_WORD
        }

        /**
         * Returns a new bit set containing all the bits in the given long array.
         *
         *
         * More precisely,
         * <br></br>`BitSet.valueOf(longs).get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)`
         * <br></br>for all `n < 64 * longs.length`.
         *
         *
         * This method is equivalent to
         * `BitSet.valueOf(LongBuffer.wrap(longs))`.
         *
         * @param longs a long array containing a little-endian representation
         * of a sequence of bits to be used as the initial bits of the
         * new bit set
         * @return a `BitSet` containing all the bits in the long array
         * @since 1.7
         */
        fun valueOf(longs: LongArray): BitSet {
            var n: Int = longs.size
            while (n > 0 && longs[n - 1] == 0L) {
                n--
            }
            return BitSet(longs.copyOf(n))
        }

        /**
         * Returns a new bit set containing all the bits in the given long
         * buffer between its position and limit.
         *
         *
         * More precisely,
         * <br></br>`BitSet.valueOf(lb).get(n) == ((lb.get(lb.position()+n/64) & (1L<<(n%64))) != 0)`
         * <br></br>for all `n < 64 * lb.remaining()`.
         *
         *
         * The long buffer is not modified by this method, and no
         * reference to the buffer is retained by the bit set.
         *
         * @param lb a long buffer containing a little-endian representation
         * of a sequence of bits between its position and limit, to be
         * used as the initial bits of the new bit set
         * @return a `BitSet` containing all the bits in the buffer in the
         * specified range
         * @since 1.7
         */
        fun valueOf(lb: LongBuffer): BitSet {
            var lb: LongBuffer = lb
            lb = lb.slice()
            var n: Int = lb.remaining()
            while (n > 0 && lb.get(n - 1) == 0L) {
                n--
            }
            val words = LongArray(n)
            lb.get(words)
            return BitSet(words)
        }

        /**
         * Returns a new bit set containing all the bits in the given byte array.
         *
         *
         * More precisely,
         * <br></br>`BitSet.valueOf(bytes).get(n) == ((bytes[n/8] & (1<<(n%8))) != 0)`
         * <br></br>for all `n <  8 * bytes.length`.
         *
         *
         * This method is equivalent to
         * `BitSet.valueOf(ByteBuffer.wrap(bytes))`.
         *
         * @param bytes a byte array containing a little-endian
         * representation of a sequence of bits to be used as the
         * initial bits of the new bit set
         * @return a `BitSet` containing all the bits in the byte array
         * @since 1.7
         */
        fun valueOf(bytes: ByteArray): BitSet {
            // Trim trailing zeros for compatibility with Java BitSet
            var n = bytes.size
            while (n > 0 && bytes[n - 1] == 0.toByte()) n--

            val words = LongArray((n + 7) / 8)

            // Direct conversion from byteArray to long[] words
            for (byteIndex in 0 until n) {
                val byteValue = bytes[byteIndex].toInt() and 0xFF
                val wordIndex = byteIndex / 8
                val byteOffsetInWord = byteIndex % 8

                // Convert the byte value to a long and shift it to the appropriate position in the word
                words[wordIndex] = words[wordIndex] or ((byteValue.toLong() and 0xFFL) shl (byteOffsetInWord * 8))
            }

            return BitSet(words)
        }
        /**
         * Returns a new bit set containing all the bits in the given byte
         * buffer between its position and limit.
         *
         *
         * More precisely,
         * <br></br>`BitSet.valueOf(bb).get(n) == ((bb.get(bb.position()+n/8) & (1<<(n%8))) != 0)`
         * <br></br>for all `n < 8 * bb.remaining()`.
         *
         *
         * The byte buffer is not modified by this method, and no
         * reference to the buffer is retained by the bit set.
         *
         * @param bb a byte buffer containing a little-endian representation
         * of a sequence of bits between its position and limit, to be
         * used as the initial bits of the new bit set
         * @return a `BitSet` containing all the bits in the buffer in the
         * specified range
         * @since 1.7
         */
        fun valueOf(bb: ByteBuffer): BitSet {
            val originalPos = bb.position
            val originalLimit = bb.limit

            // We'll manually scan between position and limit, find up-to what index the content is nonzero
            var start = originalPos
            var end = originalLimit - 1

            // Trim trailing zeros
            while (end >= start && (bb.get(end).toInt() and 0xFF) == 0) {
                end--
            }

            val n = if (end >= start) (end - start + 1) else 0

            // Now, read bytes from bb between start and start + n
            val words = LongArray((n + 7) / 8)
            var i = 0
            var byteIdx = start

            // Fill words with full 8-byte groups
            while (byteIdx + 8 <= start + n) {
                // Little-endian: least significant byte first
                var word = 0L
                for (b in 0 until 8) {
                    word = word or ((bb.get(byteIdx + b).toLong() and 0xFF) shl (8 * b))
                }
                words[i++] = word
                byteIdx += 8
            }

            // Fill possibly remaining bytes
            if (byteIdx < start + n) {
                var word = 0L
                var offset = 0
                while (byteIdx < start + n) {
                    word = word or ((bb.get(byteIdx).toLong() and 0xFF) shl (8 * offset))
                    byteIdx++
                    offset++
                }
                words[i] = word
            }

            return BitSet(words)
        }

        /**
         * Checks that fromIndex ... toIndex is a valid range of bit indices.
         */
        private fun checkRange(fromIndex: Int, toIndex: Int) {
            if (fromIndex < 0) throw IndexOutOfBoundsException("fromIndex < 0: $fromIndex")
            if (toIndex < 0) throw IndexOutOfBoundsException("toIndex < 0: $toIndex")
            if (fromIndex > toIndex) throw IndexOutOfBoundsException(
                "fromIndex: " + fromIndex +
                        " > toIndex: " + toIndex
            )
        }
    }
}
