package org.gnit.lucenekmp.codecs.bloom

import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.StringHelper
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

/**
 * A class used to represent a set of many, potentially large, values (e.g. many long strings such
 * as URLs), using a significantly smaller amount of memory.
 *
 *
 * The set is "lossy" in that it cannot definitively state that is does contain a value but it
 * *can* definitively say if a value is *not* in the set. It can therefore be used as
 * a Bloom Filter. Another application of the set is that it can be used to perform fuzzy counting
 * because it can estimate reasonably accurately how many unique values are contained in the set.
 *
 *
 * This class is NOT threadsafe.
 *
 *
 * Internally a Bitset is used to record values and once a client has finished recording a stream
 * of values the [.downsize] method can be used to create a suitably smaller set that
 * is sized appropriately for the number of values recorded and desired saturation levels.
 *
 * @lucene.experimental
 */
class FuzzySet private constructor(
    filter: FixedBitSet,
    bloomSize: Int,
    hashCount: Int
) : Accountable {
    /**
     * Result from [FuzzySet.contains]: can never return definitively YES (always
     * MAYBE), but can sometimes definitely return NO.
     */
    enum class ContainsResult {
        MAYBE,
        NO
    }

    private val filter: FixedBitSet
    private val bloomSize: Int
    private val hashCount: Int

    init {
        this.filter = filter
        this.bloomSize = bloomSize
        this.hashCount = hashCount
    }

    /**
     * The main method required for a Bloom filter which, given a value determines set membership.
     * Unlike a conventional set, the fuzzy set returns NO or MAYBE rather than true or false. Hash
     * generation follows the same principles as [.addValue]
     *
     * @return NO or MAYBE
     */
    fun contains(value: BytesRef): ContainsResult {
        val hash: LongArray = StringHelper.murmurhash3_x64_128(value)

        val msb = hash[0]
        val lsb = hash[1]
        for (i in 0..<hashCount) {
            val bloomPos = ((lsb + i * msb).toInt()) and bloomSize
            if (!mayContainValue(bloomPos)) {
                return ContainsResult.NO
            }
        }
        return ContainsResult.MAYBE
    }

    /**
     * Serializes the data set to file using the following format:
     *
     *
     *  * FuzzySet --&gt;hashCount,BloomSize, NumBitSetWords,BitSetWord<sup>NumBitSetWords</sup>
     *  * hashCount --&gt; [Uint32][org.gnit.lucenekmp.store.DataOutput.writeVInt] The number of hash functions (k).
     *  * BloomSize --&gt; [Uint32][org.gnit.lucenekmp.store.DataOutput.writeInt] The modulo value used to project
     * hashes into the field's Bitset
     *  * NumBitSetWords --&gt; [Uint32][org.gnit.lucenekmp.store.DataOutput.writeInt] The number of longs (as returned
     * from [FixedBitSet.getBits])
     *  * BitSetWord --&gt; [Long][org.gnit.lucenekmp.store.DataOutput.writeLong] A long from the array returned by
     * [FixedBitSet.getBits]
     *
     *
     * @param out Data output stream
     * @throws okio.IOException If there is a low-level I/O error
     */
    @Throws(IOException::class)
    fun serialize(out: DataOutput) {
        out.writeVInt(hashCount)
        out.writeInt(bloomSize)
        val bits: LongArray = filter.bits
        out.writeInt(bits.size)
        for (i in bits.indices) {
            // Can't used VLong encoding because cant cope with negative numbers
            // output by FixedBitSet
            out.writeLong(bits[i])
        }
    }

    private fun mayContainValue(aHash: Int): Boolean {
        // Bloom sizes are always base 2 and so can be ANDed for a fast modulo
        val pos = aHash and bloomSize
        return filter.get(pos)
    }

    /**
     * Records a value in the set. The referenced bytes are hashed. From the 64-bit generated hash,
     * two 32-bit hashes are derived from the msb and lsb which can be used to derive more hashes (see
     * https://www.eecs.harvard.edu/~michaelm/postscripts/rsa2008.pdf). Finally, each generated hash
     * is modulo n'd where n is the chosen size of the internal bitset.
     *
     * @param value the key value to be hashed
     */
    fun addValue(value: BytesRef) {
        val hash: LongArray = StringHelper.murmurhash3_x64_128(value)
        val msb = hash[0]
        val lsb = hash[1]
        for (i in 0..<hashCount) {
            // Bitmasking using bloomSize is effectively a modulo operation.
            val bloomPos = ((lsb + i * msb).toInt()) and bloomSize
            filter.set(bloomPos)
        }
    }

    /**
     * @param targetMaxSaturation A number between 0 and 1 describing the % of bits that would ideally
     * be set in the result. Lower values have better accuracy but require more space.
     * @return a smaller FuzzySet or null if the current set is already over-saturated
     */
    fun downsize(targetMaxSaturation: Float): FuzzySet? {
        val numBitsSet: Int = filter.cardinality()
        var rightSizedBitSet: FixedBitSet = filter
        var rightSizedBitSetSize = bloomSize
        // Hopefully find a smaller size bitset into which we can project accumulated values while
        // maintaining desired saturation level
        for (i in usableBitSetSizes.indices) {
            val candidateBitsetSize = usableBitSetSizes[i]
            val candidateSaturation = numBitsSet.toFloat() / candidateBitsetSize.toFloat()
            if (candidateSaturation <= targetMaxSaturation) {
                rightSizedBitSetSize = candidateBitsetSize
                break
            }
        }
        // Re-project the numbers to a smaller space if necessary
        if (rightSizedBitSetSize < bloomSize) {
            // Reset the choice of bitset to the smaller version
            rightSizedBitSet = FixedBitSet(rightSizedBitSetSize + 1)
            // Map across the bits from the large set to the smaller one
            var bitIndex = 0
            do {
                bitIndex = filter.nextSetBit(bitIndex)
                if (bitIndex != DocIdSetIterator.Companion.NO_MORE_DOCS) {
                    // Project the larger number into a smaller one effectively
                    // modulo-ing by using the target bitset size as a mask
                    val downSizedBitIndex = bitIndex and rightSizedBitSetSize
                    rightSizedBitSet.set(downSizedBitIndex)
                    bitIndex++
                }
            } while ((bitIndex >= 0) && (bitIndex <= bloomSize))
        } else {
            return null
        }
        return FuzzySet(rightSizedBitSet, rightSizedBitSetSize, hashCount)
    }

    val estimatedUniqueValues: Int
        get() = getEstimatedNumberUniqueValuesAllowingForCollisions(bloomSize, filter.cardinality())

    val targetMaxSaturation: Float
        get() = 0.5f

    val saturation: Float
        get() {
            val numBitsSet: Int = filter.cardinality()
            return numBitsSet.toFloat() / bloomSize.toFloat()
        }

    override fun ramBytesUsed(): Long {
        return RamUsageEstimator.Companion.sizeOf(filter.bits)
    }

    override fun toString(): String {
        return (this::class.simpleName
                + "(k="
                + hashCount
                + ", bits="
                + filter.cardinality()
                + "/"
                + filter.length()
                + ")")
    }

    companion object {
        // The sizes of BitSet used are all numbers that, when expressed in binary form,
        // are all ones. This is to enable fast downsizing from one bitset to another
        // by simply ANDing each set index in one bitset with the size of the target bitset
        // - this provides a fast modulo of the number. Values previously accumulated in
        // a large bitset and then mapped to a smaller set can be looked up using a single
        // AND operation of the query term's hash rather than needing to perform a 2-step
        // translation of the query term that mirrors the stored content's reprojections.
        val usableBitSetSizes: IntArray

        init {
            usableBitSetSizes = IntArray(26)
            for (i in usableBitSetSizes.indices) {
                usableBitSetSizes[i] = (1 shl (i + 6)) - 1
            }
        }

        /**
         * Rounds down required maxNumberOfBits to the nearest number that is made up of all ones as a
         * binary number. Use this method where controlling memory use is paramount.
         */
        fun getNearestSetSize(maxNumberOfBits: Int): Int {
            var result = usableBitSetSizes[0]
            for (i in usableBitSetSizes.indices) {
                if (usableBitSetSizes[i] <= maxNumberOfBits) {
                    result = usableBitSetSizes[i]
                }
            }
            return result
        }

        /**
         * Use this method to choose a set size where accuracy (low content saturation) is more important
         * than deciding how much memory to throw at the problem.
         *
         * @param desiredSaturation A number between 0 and 1 expressing the % of bits set once all values
         * have been recorded
         * @return The size of the set nearest to the required size
         */
        fun getNearestSetSize(maxNumberOfValuesExpected: Int, desiredSaturation: Float): Int {
            // Iterate around the various scales of bitset from smallest to largest looking for the first
            // that
            // satisfies value volumes at the chosen saturation level
            for (i in usableBitSetSizes.indices) {
                val numSetBitsAtDesiredSaturation =
                    (usableBitSetSizes[i] * desiredSaturation).toInt()
                val estimatedNumUniqueValues =
                    getEstimatedNumberUniqueValuesAllowingForCollisions(
                        usableBitSetSizes[i], numSetBitsAtDesiredSaturation
                    )
                if (estimatedNumUniqueValues > maxNumberOfValuesExpected) {
                    return usableBitSetSizes[i]
                }
            }
            return -1
        }

        fun createSetBasedOnMaxMemory(maxNumBytes: Int): FuzzySet {
            val setSize = getNearestSetSize(maxNumBytes)
            return FuzzySet(FixedBitSet(setSize + 1), setSize, 1)
        }

        fun createSetBasedOnQuality(
            maxNumUniqueValues: Int, desiredMaxSaturation: Float, version: Int
        ): FuzzySet {
            val setSize = getNearestSetSize(maxNumUniqueValues, desiredMaxSaturation)
            return FuzzySet(FixedBitSet(setSize + 1), setSize, 1)
        }

        fun createOptimalSet(maxNumUniqueValues: Int, targetMaxFpp: Float): FuzzySet {
            var setSize = ceil(
                (maxNumUniqueValues * ln(targetMaxFpp.toDouble()))
                        / ln(1 / 2.0.pow(ln(2.0)))
            ).toInt()
            setSize = getNearestSetSize(2 * setSize)
            val optimalK =
                Math.round((setSize.toDouble() / maxNumUniqueValues.toDouble()) * ln(2.0)).toInt()
            return FuzzySet(FixedBitSet(setSize + 1), setSize, optimalK)
        }

        @Throws(IOException::class)
        fun deserialize(`in`: DataInput): FuzzySet {
            val hashCount: Int = `in`.readVInt()
            val bloomSize: Int = `in`.readInt()
            val numLongs: Int = `in`.readInt()
            val longs = LongArray(numLongs)
            `in`.readLongs(longs, 0, numLongs)
            val bits: FixedBitSet =
                FixedBitSet(longs, bloomSize + 1)
            return FuzzySet(bits, bloomSize, hashCount)
        }

        // Given a set size and a the number of set bits, produces an estimate of the number of unique
        // values recorded
        fun getEstimatedNumberUniqueValuesAllowingForCollisions(
            setSize: Int, numRecordedBits: Int
        ): Int {
            val setSizeAsDouble = setSize.toDouble()
            val numRecordedBitsAsDouble = numRecordedBits.toDouble()
            val saturation = numRecordedBitsAsDouble / setSizeAsDouble
            val logInverseSaturation = ln(1 - saturation) * -1
            return (setSizeAsDouble * logInverseSaturation).toInt()
        }
    }
}