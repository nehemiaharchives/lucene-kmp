package org.gnit.lucenekmp.tests.codex.bloom

import okio.IOException
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.bloom.BloomFilterFactory
import org.gnit.lucenekmp.codecs.bloom.BloomFilteringPostingsFormat
import org.gnit.lucenekmp.codecs.bloom.FuzzySet
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.tests.util.TestUtil


/**
 * A class used for testing [BloomFilteringPostingsFormat] with a concrete delegate
 * (Lucene41). Creates a Bloom filter on ALL fields and with tiny amounts of memory reserved for the
 * filter. DO NOT USE IN A PRODUCTION APPLICATION This is not a realistic application of Bloom
 * Filters as they ordinarily are larger and operate on only primary key type fields.
 */
class TestBloomFilteredLucenePostings :
    PostingsFormat("TestBloomFilteredLucenePostings") {
    private val delegate: BloomFilteringPostingsFormat

    // Special class used to avoid OOM exceptions where Junit tests create many
    // fields.
    internal class LowMemoryBloomFactory : BloomFilterFactory() {
        override fun getSetForField(
            state: SegmentWriteState,
            info: FieldInfo?
        ): FuzzySet {
            return FuzzySet.createSetBasedOnMaxMemory(1024)
        }

        override fun isSaturated(
            bloomFilter: FuzzySet,
            fieldInfo: FieldInfo
        ): Boolean {
            // For test purposes always maintain the BloomFilter - even past the point
            // of usefulness when all bits are set
            return false
        }
    }

    init {
        delegate =
            BloomFilteringPostingsFormat(
                TestUtil.getDefaultPostingsFormat(),
                LowMemoryBloomFactory()
            )
    }

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        return delegate.fieldsConsumer(state)
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        return delegate.fieldsProducer(state)
    }

    override fun toString(): String {
        return "TestBloomFilteredLucenePostings($delegate)"
    }
}
