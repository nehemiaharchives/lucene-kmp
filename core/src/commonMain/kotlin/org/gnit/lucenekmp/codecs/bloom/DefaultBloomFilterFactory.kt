package org.gnit.lucenekmp.codecs.bloom

import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.SegmentWriteState

/**
 * Default policy is to allocate a bitset with 10% saturation given a unique term per document. Bits
 * are set via MurmurHash2 hashing function.
 *
 * @lucene.experimental
 */
class DefaultBloomFilterFactory : BloomFilterFactory() {
    override fun getSetForField(
        state: SegmentWriteState,
        info: FieldInfo?
    ): FuzzySet {
        // Assume all of the docs have a unique term (e.g. a primary key) and we hope to maintain a set
        // with 10% of bits set
        return FuzzySet.createOptimalSet(
            state.segmentInfo.maxDoc(),
            0.1023f
        )
    }

    override fun isSaturated(
        bloomFilter: FuzzySet,
        fieldInfo: FieldInfo
    ): Boolean {
        // Don't bother saving bitsets if >90% of bits are set - we don't want to
        // throw any more memory at this problem.
        return bloomFilter.saturation > 0.9f
    }
}
