package org.gnit.lucenekmp.codecs.bloom

import okio.IOException
import org.gnit.lucenekmp.codecs.bloom.DefaultBloomFilterFactory
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.bloom.FuzzySet.ContainsResult
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import kotlin.jvm.JvmOverloads


/**
 * A [PostingsFormat] useful for low doc-frequency fields such as primary keys. Bloom filters
 * are maintained in a ".blm" file which offers "fast-fail" for reads in segments known to have no
 * record of the key. A choice of delegate PostingsFormat is used to record all other Postings data.
 *
 *
 * A choice of [BloomFilterFactory] can be passed to tailor Bloom Filter settings on a
 * per-field basis. The default configuration is [DefaultBloomFilterFactory] which allocates a
 * ~8mb bitset and hashes values using [ ][org.apache.lucene.util.StringHelper.murmurhash3_x64_128]. This should be suitable for
 * most purposes.
 *
 *
 * The format of the blm file is as follows:
 *
 *
 *  * BloomFilter (.blm) --&gt; Header, DelegatePostingsFormatName, NumFilteredFields,
 * Filter<sup>NumFilteredFields</sup>, Footer
 *  * Filter --&gt; FieldNumber, FuzzySet
 *  * FuzzySet --&gt;See [FuzzySet.serialize]
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * DelegatePostingsFormatName --&gt; [String][DataOutput.writeString] The name of
 * a ServiceProvider registered [PostingsFormat]
 *  * NumFilteredFields --&gt; [Uint32][DataOutput.writeInt]
 *  * FieldNumber --&gt; [Uint32][DataOutput.writeInt] The number of the field in this
 * segment
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * @lucene.experimental
 */
class BloomFilteringPostingsFormat(
    delegatePostingsFormat: PostingsFormat? = null,
    bloomFilterFactory: BloomFilterFactory = DefaultBloomFilterFactory()
) : PostingsFormat(
    BLOOM_CODEC_NAME
) {
    private val bloomFilterFactory: BloomFilterFactory
    private val delegatePostingsFormat: PostingsFormat?

    /**
     * Creates Bloom filters for a selection of fields created in the index. This is recorded as a set
     * of Bitsets held as a segment summary in an additional "blm" file. This PostingsFormat delegates
     * to a choice of delegate PostingsFormat for encoding all other postings data.
     *
     * @param delegatePostingsFormat The PostingsFormat that records all the non-bloom filter data
     * i.e. postings info.
     * @param bloomFilterFactory The [BloomFilterFactory] responsible for sizing BloomFilters
     * appropriately
     */
    // Used only by core Lucene at read-time via Service Provider instantiation -
    // do not use at Write-time in application code.
    /**
     * Creates Bloom filters for a selection of fields created in the index. This is recorded as a set
     * of Bitsets held as a segment summary in an additional "blm" file. This PostingsFormat delegates
     * to a choice of delegate PostingsFormat for encoding all other postings data. This choice of
     * constructor defaults to the [DefaultBloomFilterFactory] for configuring per-field
     * BloomFilters.
     *
     * @param delegatePostingsFormat The PostingsFormat that records all the non-bloom filter data
     * i.e. postings info.
     */
    init {
        this.delegatePostingsFormat = delegatePostingsFormat
        this.bloomFilterFactory = bloomFilterFactory
    }

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        if (delegatePostingsFormat == null) {
            throw UnsupportedOperationException(
                ("Error - "
                        + this::class.simpleName
                        + " has been constructed without a choice of PostingsFormat")
            )
        }
        val fieldsConsumer: FieldsConsumer =
            delegatePostingsFormat.fieldsConsumer(state)
        return BloomFilteredFieldsConsumer(fieldsConsumer, state)
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        return BloomFilteredFieldsProducer(state)
    }

    internal class BloomFilteredFieldsProducer(state: SegmentReadState) :
        FieldsProducer() {
        private var delegateFieldsProducer: FieldsProducer? = null
        var bloomsByFieldName: MutableMap<String, FuzzySet> = mutableMapOf()
        init {
            val bloomFileName: String =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name, state.segmentSuffix, BLOOM_EXTENSION
                )
            var bloomIn: ChecksumIndexInput? = null
            var success = false
            try {
                bloomIn = state.directory.openChecksumInput(bloomFileName)
                CodecUtil.checkIndexHeader(
                    bloomIn,
                    BLOOM_CODEC_NAME,
                    VERSION_START,
                    VERSION_CURRENT,
                    state.segmentInfo.getId(),
                    state.segmentSuffix
                )
                // // Load the hash function used in the BloomFilter
                // hashFunction = HashFunction.forName(bloomIn.readString());
                // Load the delegate postings format
                val delegatePostingsFormat: PostingsFormat = forName(bloomIn.readString())

                this.delegateFieldsProducer = delegatePostingsFormat.fieldsProducer(state)
                val numBlooms: Int = bloomIn.readInt()
                for (i in 0..<numBlooms) {
                    val fieldNum: Int = bloomIn.readInt()
                    val bloom: FuzzySet =
                        FuzzySet.deserialize(bloomIn)
                    val fieldInfo: FieldInfo? = state.fieldInfos.fieldInfo(fieldNum)
                    bloomsByFieldName[fieldInfo!!.name] = bloom
                }
                CodecUtil.checkFooter(bloomIn)
                IOUtils.close(bloomIn)
                success = true
            } finally {
                if (!success) {
                    IOUtils.closeWhileHandlingException(
                        bloomIn,
                        delegateFieldsProducer
                    )
                }
            }
        }

        override fun iterator(): MutableIterator<String> {
            return asUnmodifiableIterator(delegateFieldsProducer!!.iterator())
        }

        override fun close() {
            delegateFieldsProducer!!.close()
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            val filter: FuzzySet? = bloomsByFieldName[field]
            if (filter == null) {
                return delegateFieldsProducer!!.terms(field)
            } else {
                val result: Terms? = delegateFieldsProducer!!.terms(field)
                if (result == null) {
                    return null
                }
                return BloomFilteredTerms(result, filter)
            }
        }

        override fun size(): Int {
            return delegateFieldsProducer!!.size()
        }

        internal class BloomFilteredTerms(
            terms: Terms,
            filter: FuzzySet
        ) : Terms() {
            private val delegateTerms: Terms
            private val filter: FuzzySet

            init {
                this.delegateTerms = terms
                this.filter = filter
            }

            @Throws(IOException::class)
            override fun intersect(
                compiled: CompiledAutomaton,
                startTerm: BytesRef?
            ): TermsEnum {
                return delegateTerms.intersect(compiled, startTerm)
            }

            @Throws(IOException::class)
            override fun iterator(): TermsEnum {
                return BloomFilteredTermsEnum(delegateTerms, filter)
            }

            @Throws(IOException::class)
            override fun size(): Long {
                return delegateTerms.size()
            }

            override val sumTotalTermFreq: Long
                get() = delegateTerms.sumTotalTermFreq

            override val sumDocFreq: Long
                get() = delegateTerms.sumDocFreq

            override val docCount: Int
                get() = delegateTerms.docCount

            override fun hasFreqs(): Boolean {
                return delegateTerms.hasFreqs()
            }

            override fun hasOffsets(): Boolean {
                return delegateTerms.hasOffsets()
            }

            override fun hasPositions(): Boolean {
                return delegateTerms.hasPositions()
            }

            override fun hasPayloads(): Boolean {
                return delegateTerms.hasPayloads()
            }

            override val min: BytesRef?
                get() = delegateTerms.min

            override val max: BytesRef?
                get() = delegateTerms.max
        }

        internal class BloomFilteredTermsEnum(
            delegateTerms: Terms,
            filter: FuzzySet
        ) : BaseTermsEnum() {
            private var delegateTerms: Terms
            private var delegateTermsEnum: TermsEnum? = null
            private val filter: FuzzySet

            init {
                this.delegateTerms = delegateTerms
                this.filter = filter
            }

            @Throws(IOException::class)
            fun reset(delegateTerms: Terms) {
                this.delegateTerms = delegateTerms
                this.delegateTermsEnum = null
            }

            @Throws(IOException::class)
            private fun delegate(): TermsEnum? {
                if (delegateTermsEnum == null) {
                    /* pull the iterator only if we really need it -
           * this can be a relativly heavy operation depending on the
           * delegate postings format and they underlying directory
           * (clone IndexInput) */
                    delegateTermsEnum = delegateTerms.iterator()
                }
                return delegateTermsEnum
            }

            @Throws(IOException::class)
            override fun next(): BytesRef? {
                return delegate()!!.next()
            }

            @Throws(IOException::class)
            override fun prepareSeekExact(text: BytesRef): IOBooleanSupplier? {
                // The magical fail-fast speed up that is the entire point of all of
                // this code - save a disk seek if there is a match on an in-memory
                // structure
                // that may occasionally give a false positive but guaranteed no false
                // negatives
                if (filter.contains(text) == ContainsResult.NO) {
                    return null
                }
                return delegate()!!.prepareSeekExact(text)
            }

            @Throws(IOException::class)
            override fun seekExact(text: BytesRef): Boolean {
                // See #prepareSeekExact
                if (filter.contains(text) == ContainsResult.NO) {
                    return false
                }
                return delegate()!!.seekExact(text)
            }

            @Throws(IOException::class)
            override fun seekCeil(text: BytesRef): SeekStatus {
                return delegate()!!.seekCeil(text)
            }

            @Throws(IOException::class)
            override fun seekExact(ord: Long) {
                delegate()!!.seekExact(ord)
            }

            @Throws(IOException::class)
            override fun term(): BytesRef? {
                return delegate()!!.term()
            }

            @Throws(IOException::class)
            override fun ord(): Long {
                return delegate()!!.ord()
            }

            @Throws(IOException::class)
            override fun docFreq(): Int {
                return delegate()!!.docFreq()
            }

            @Throws(IOException::class)
            override fun totalTermFreq(): Long {
                return delegate()!!.totalTermFreq()
            }

            @Throws(IOException::class)
            override fun postings(
                reuse: PostingsEnum?,
                flags: Int
            ): PostingsEnum? {
                return delegate()!!.postings(reuse, flags)
            }

            @Throws(IOException::class)
            override fun impacts(flags: Int): ImpactsEnum {
                return delegate()!!.impacts(flags)
            }

            override fun toString(): String {
                return this::class.simpleName + "(filter=" + filter.toString() + ")"
            }
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            delegateFieldsProducer!!.checkIntegrity()
        }

        override fun toString(): String {
            return (this::class.simpleName
                    + "(fields="
                    + bloomsByFieldName.size
                    + ",delegate="
                    + delegateFieldsProducer
                    + ")")
        }
    }

    internal inner class BloomFilteredFieldsConsumer(
        fieldsConsumer: FieldsConsumer,
        state: SegmentWriteState
    ) : FieldsConsumer() {
        private val delegateFieldsConsumer: FieldsConsumer
        private val bloomFilters: MutableMap<FieldInfo, FuzzySet> = mutableMapOf()
        private val state: SegmentWriteState

        @Throws(IOException::class)
        override fun write(
            fields: Fields,
            norms: NormsProducer?
        ) {
            // Delegate must write first: it may have opened files
            // on creating the class
            // (e.g. Lucene41PostingsConsumer), and write() will
            // close them; alternatively, if we delayed pulling
            // the fields consumer until here, we could do it
            // afterwards:

            delegateFieldsConsumer.write(fields, norms)

            for (field in fields) {
                val terms: Terms? = fields.terms(field)
                if (terms == null) {
                    continue
                }
                val fieldInfo: FieldInfo? =
                    state.fieldInfos!!.fieldInfo(field)
                val termsEnum: TermsEnum = terms.iterator()

                var bloomFilter: FuzzySet? = null

                var postingsEnum: PostingsEnum? = null
                while (true) {
                    val term: BytesRef? = termsEnum.next()
                    if (term == null) {
                        break
                    }
                    if (bloomFilter == null) {
                        bloomFilter = bloomFilterFactory.getSetForField(state, fieldInfo)
                        if (bloomFilter == null) {
                            // Field not bloom'd
                            break
                        }
                        assert(bloomFilters.containsKey(fieldInfo) == false)
                        bloomFilters[fieldInfo!!] = bloomFilter
                    }
                    // Make sure there's at least one doc for this term:
                    postingsEnum = termsEnum.postings(postingsEnum, 0)
                    if (postingsEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        bloomFilter.addValue(term)
                    }
                }
            }
        }

        private var closed = false

        init {
            this.delegateFieldsConsumer = fieldsConsumer
            this.state = state
        }

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            delegateFieldsConsumer.close()

            // Now we are done accumulating values for these fields
            val nonSaturatedBlooms: MutableList<MutableMap.MutableEntry<FieldInfo, FuzzySet>> = mutableListOf()

            for (entry in bloomFilters.entries) {
                val bloomFilter: FuzzySet = entry.value
                if (!bloomFilterFactory.isSaturated(bloomFilter, entry.key)) {
                    nonSaturatedBlooms.add(entry)
                }
            }
            val bloomFileName: String =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name, state.segmentSuffix, BLOOM_EXTENSION
                )
            state.directory.createOutput(bloomFileName, state.context).use { bloomOutput ->
                CodecUtil.writeIndexHeader(
                    bloomOutput,
                    BLOOM_CODEC_NAME,
                    VERSION_CURRENT,
                    state.segmentInfo.getId(),
                    state.segmentSuffix
                )
                // remember the name of the postings format we will delegate to
                bloomOutput.writeString(delegatePostingsFormat!!.name)

                // First field in the output file is the number of fields+blooms saved
                bloomOutput.writeInt(nonSaturatedBlooms.size)
                for (entry in nonSaturatedBlooms) {
                    val fieldInfo: FieldInfo = entry.key
                    val bloomFilter: FuzzySet = entry.value
                    bloomOutput.writeInt(fieldInfo.number)
                    saveAppropriatelySizedBloomFilter(bloomOutput, bloomFilter, fieldInfo)
                }
                CodecUtil.writeFooter(bloomOutput)
            }
            // We are done with large bitsets so no need to keep them hanging around
            bloomFilters.clear()
        }

        @Throws(IOException::class)
        private fun saveAppropriatelySizedBloomFilter(
            bloomOutput: IndexOutput,
            bloomFilter: FuzzySet,
            fieldInfo: FieldInfo
        ) {
            var rightSizedSet: FuzzySet? =
                bloomFilterFactory.downsize(fieldInfo, bloomFilter)
            if (rightSizedSet == null) {
                rightSizedSet = bloomFilter
            }
            rightSizedSet.serialize(bloomOutput)
        }
    }

    override fun toString(): String {
        return "BloomFilteringPostingsFormat($delegatePostingsFormat)"
    }

    companion object {
        const val BLOOM_CODEC_NAME: String = "BloomFilter"
        const val VERSION_START: Int = 3
        const val VERSION_CURRENT: Int = VERSION_START

        /** Extension of Bloom Filters file  */
        const val BLOOM_EXTENSION: String = "blm"
    }
}
