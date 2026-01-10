package org.gnit.lucenekmp.codecs.bloom

import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.SegmentWriteState

/**
 * Class used to create index-time [FuzzySet] appropriately configured for each field. Also
 * called to right-size bitsets for serialization.
 *
 * @lucene.experimental
 */
abstract class BloomFilterFactory {
    /**
     * @param state The content to be indexed
     * @param info the field requiring a BloomFilter
     * @return An appropriately sized set or null if no BloomFiltering required
     */
    abstract fun getSetForField(
        state: SegmentWriteState,
        info: FieldInfo?
    ): FuzzySet

    /**
     * Called when downsizing bitsets for serialization
     *
     * @param fieldInfo The field with sparse set bits
     * @param initialSet The bits accumulated
     * @return null or a hopefully more densely packed, smaller bitset
     */
    fun downsize(fieldInfo: FieldInfo, initialSet: FuzzySet): FuzzySet? {
        val targetMaxSaturation: Float = initialSet.targetMaxSaturation
        return initialSet.downsize(targetMaxSaturation)
    }

    /**
     * Used to determine if the given filter has reached saturation and should be retired i.e. not
     * saved any more
     *
     * @param bloomFilter The bloomFilter being tested
     * @param fieldInfo The field with which this filter is associated
     * @return true if the set has reached saturation and should be retired
     */
    abstract fun isSaturated(
        bloomFilter: FuzzySet,
        fieldInfo: FieldInfo
    ): Boolean
}
