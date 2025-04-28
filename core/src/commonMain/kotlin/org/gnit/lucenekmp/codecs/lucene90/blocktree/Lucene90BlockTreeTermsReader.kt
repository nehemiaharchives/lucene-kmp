package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsReaderBase
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.fst.ByteSequenceOutputs
import org.gnit.lucenekmp.util.fst.Outputs
import kotlinx.io.IOException

/**
 * A block-based terms index and dictionary that assigns terms to variable length blocks according
 * to how they share prefixes. The terms index is a prefix trie whose leaves are term blocks. The
 * advantage of this approach is that seekExact is often able to determine a term cannot exist
 * without doing any IO, and intersection with Automata is very fast. Note that this terms
 * dictionary has its own fixed terms index (ie, it does not support a pluggable terms index
 * implementation).
 *
 *
 * **NOTE**: this terms dictionary supports min/maxItemsPerBlock during indexing to control
 * how much memory the terms index uses.
 *
 *
 * The data structure used by this implementation is very similar to a burst trie
 * (http://citeseer.ist.psu.edu/viewdoc/summarydoi=10.1.1.18.3499), but with added logic to break
 * up too-large blocks of all terms sharing a given prefix into smaller ones.
 *
 *
 * Use [org.apache.lucene.index.CheckIndex] with the `-verbose` option to see
 * summary statistics on the blocks in the dictionary.
 *
 *
 * See [Lucene90BlockTreeTermsWriter].
 *
 * @lucene.experimental
 */
class Lucene90BlockTreeTermsReader(postingsReader: PostingsReaderBase, state: SegmentReadState) : FieldsProducer() {
    // Open input to the main terms dict file (_X.tib)
    val termsIn: IndexInput

    // Open input to the terms index file (_X.tip)
    val indexIn: IndexInput

    // private static final boolean DEBUG = BlockTreeTermsWriter.DEBUG;
    // Reads the terms dict entries, to gather state to
    // produce DocsEnum on demand
    val postingsReader: PostingsReaderBase

    private val fieldInfos: FieldInfos
    private val fieldMap: IntObjectHashMap<FieldReader>
    private val fieldList: MutableList<String>

    val segment: String

    val version: Int

    /** Sole constructor.  */
    init {
        var success = false

        this.postingsReader = postingsReader
        this.segment = state.segmentInfo.name

        try {
            val termsName: String =
                IndexFileNames.segmentFileName(segment, state.segmentSuffix, TERMS_EXTENSION)
            termsIn = state.directory.openInput(termsName, state.context)
            version =
                CodecUtil.checkIndexHeader(
                    termsIn,
                    TERMS_CODEC_NAME,
                    VERSION_START,
                    VERSION_CURRENT,
                    state.segmentInfo.getId(),
                    state.segmentSuffix
                )

            val indexName: String =
                IndexFileNames.segmentFileName(segment, state.segmentSuffix, TERMS_INDEX_EXTENSION)
            indexIn =
                state.directory.openInput(
                    indexName, state.context.withReadAdvice(ReadAdvice.RANDOM_PRELOAD)
                )
            CodecUtil.checkIndexHeader(
                indexIn,
                TERMS_INDEX_CODEC_NAME,
                version,
                version,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )

            // Read per-field details
            val metaName: String =
                IndexFileNames.segmentFileName(segment, state.segmentSuffix, TERMS_META_EXTENSION)
            var fieldMap: IntObjectHashMap<FieldReader>? = null
            var priorE: Throwable? = null
            var indexLength: Long = -1
            var termsLength: Long = -1
            state.directory.openChecksumInput(metaName).use { metaIn ->
                try {
                    CodecUtil.checkIndexHeader(
                        metaIn,
                        TERMS_META_CODEC_NAME,
                        version,
                        version,
                        state.segmentInfo.getId(),
                        state.segmentSuffix
                    )
                    postingsReader.init(metaIn, state)

                    val numFields: Int = metaIn.readVInt()
                    if (numFields < 0) {
                        throw CorruptIndexException("invalid numFields: $numFields", metaIn)
                    }
                    fieldMap = IntObjectHashMap(numFields)
                    for (i in 0..<numFields) {
                        val field: Int = metaIn.readVInt()
                        val numTerms: Long = metaIn.readVLong()
                        if (numTerms <= 0) {
                            throw CorruptIndexException(
                                "Illegal numTerms for field number: $field", metaIn
                            )
                        }
                        val rootCode: BytesRef = readBytesRef(metaIn)
                        val fieldInfo: FieldInfo? = state.fieldInfos.fieldInfo(field)
                        if (fieldInfo == null) {
                            throw CorruptIndexException("invalid field number: $field", metaIn)
                        }
                        val sumTotalTermFreq: Long = metaIn.readVLong()
                        // when frequencies are omitted, sumDocFreq=sumTotalTermFreq and only one value is
                        // written.
                        val sumDocFreq =
                            if (fieldInfo.indexOptions === IndexOptions.DOCS)
                                sumTotalTermFreq
                            else
                                metaIn.readVLong()
                        val docCount: Int = metaIn.readVInt()
                        val minTerm: BytesRef = readBytesRef(metaIn)
                        var maxTerm: BytesRef = readBytesRef(metaIn)
                        if (numTerms == 1L) {
                            require(maxTerm == minTerm)
                            // save heap for edge case of a single term only so min == max
                            maxTerm = minTerm
                        }
                        if (docCount < 0
                            || docCount > state.segmentInfo.maxDoc()
                        ) { // #docs with field must be <= #docs
                            throw CorruptIndexException(
                                "invalid docCount: " + docCount + " maxDoc: " + state.segmentInfo.maxDoc(),
                                metaIn
                            )
                        }
                        if (sumDocFreq < docCount) { // #postings must be >= #docs with field
                            throw CorruptIndexException(
                                "invalid sumDocFreq: $sumDocFreq docCount: $docCount", metaIn
                            )
                        }
                        if (sumTotalTermFreq < sumDocFreq) { // #positions must be >= #postings
                            throw CorruptIndexException(
                                "invalid sumTotalTermFreq: $sumTotalTermFreq sumDocFreq: $sumDocFreq",
                                metaIn
                            )
                        }
                        val indexStartFP: Long = metaIn.readVLong()
                        val previous: FieldReader? =
                            fieldMap.put(
                                fieldInfo.number,
                                FieldReader(
                                    this,
                                    fieldInfo,
                                    numTerms,
                                    rootCode,
                                    sumTotalTermFreq,
                                    sumDocFreq,
                                    docCount,
                                    indexStartFP,
                                    metaIn,
                                    indexIn,
                                    minTerm,
                                    maxTerm
                                )
                            )
                        if (previous != null) {
                            throw CorruptIndexException("duplicate field: " + fieldInfo.name, metaIn)
                        }
                    }
                    indexLength = metaIn.readLong()
                    termsLength = metaIn.readLong()
                } catch (exception: Throwable) {
                    priorE = exception
                } finally {
                    if (metaIn != null) {
                        CodecUtil.checkFooter(metaIn, priorE)
                    } else if (priorE != null) {
                        IOUtils.rethrowAlways(priorE)
                    }
                }
            }
            // At this point the checksum of the meta file has been verified so the lengths are likely
            // correct
            CodecUtil.retrieveChecksum(indexIn, indexLength)
            CodecUtil.retrieveChecksum(termsIn, termsLength)
            fieldInfos = state.fieldInfos
            this.fieldMap = fieldMap!!
            this.fieldList = sortFieldNames(fieldMap, state.fieldInfos)
            success = true
        } finally {
            if (!success) {
                // this.close() will close in:
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    // for debugging
    // private static String toHex(int v) {
    //   return "0x" + Integer.toHexString(v);
    // }
    @Throws(IOException::class)
    override fun close() {
        try {
            IOUtils.close(indexIn, termsIn, postingsReader)
        } finally {
            // Clear so refs to terms index is GCable even if
            // app hangs onto us:
            fieldMap.clear()
        }
    }

    override fun iterator(): MutableIterator<String> {
        return fieldList.iterator()
    }

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        checkNotNull(field)
        val fieldInfo: FieldInfo? = fieldInfos.fieldInfo(field)
        return if (fieldInfo == null) null else fieldMap[fieldInfo.number]
    }

    override fun size(): Int {
        return fieldMap.size()
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        // terms index
        CodecUtil.checksumEntireFile(indexIn)

        // term dictionary
        CodecUtil.checksumEntireFile(termsIn)

        // postings
        postingsReader.checkIntegrity()
    }

    override fun toString(): String {
        return (this::class.simpleName
                + "(fields="
                + fieldMap.size()
                + ",delegate="
                + postingsReader
                + ")")
    }

    companion object {
        val FST_OUTPUTS: Outputs<BytesRef> = ByteSequenceOutputs.getSingleton()

        val NO_OUTPUT: BytesRef = FST_OUTPUTS.getNoOutput()

        const val OUTPUT_FLAGS_NUM_BITS: Int = 2
        const val OUTPUT_FLAGS_MASK: Int = 0x3
        const val OUTPUT_FLAG_IS_FLOOR: Int = 0x1
        const val OUTPUT_FLAG_HAS_TERMS: Int = 0x2

        /** Extension of terms file  */
        const val TERMS_EXTENSION: String = "tim"

        const val TERMS_CODEC_NAME: String = "BlockTreeTermsDict"

        /** Initial terms format.  */
        const val VERSION_START: Int = 0

        /**
         * Version that encode output as MSB VLong for better outputs sharing in FST, see GITHUB#12620.
         */
        const val VERSION_MSB_VLONG_OUTPUT: Int = 1

        /** The version that specialize arc store for continuous label in FST.  */
        const val VERSION_FST_CONTINUOUS_ARCS: Int = 2

        /** Current terms format.  */
        const val VERSION_CURRENT: Int = VERSION_FST_CONTINUOUS_ARCS

        /** Extension of terms index file  */
        const val TERMS_INDEX_EXTENSION: String = "tip"

        const val TERMS_INDEX_CODEC_NAME: String = "BlockTreeTermsIndex"

        /** Extension of terms meta file  */
        const val TERMS_META_EXTENSION: String = "tmd"

        const val TERMS_META_CODEC_NAME: String = "BlockTreeTermsMeta"

        @Throws(IOException::class)
        private fun readBytesRef(`in`: IndexInput): BytesRef {
            val numBytes: Int = `in`.readVInt()
            if (numBytes < 0) {
                throw CorruptIndexException("invalid bytes length: $numBytes", `in`)
            }

            val bytes = BytesRef(numBytes)
            bytes.length = numBytes
            `in`.readBytes(bytes.bytes, 0, numBytes)

            return bytes
        }

        private fun sortFieldNames(
            fieldMap: IntObjectHashMap<FieldReader>, fieldInfos: FieldInfos
        ): MutableList<String> {
            val fieldNames: MutableList<String> = ArrayList(fieldMap.size())
            for (fieldNumber in fieldMap.keys()) {
                fieldNames.add(fieldInfos.fieldInfo(fieldNumber.value)!!.name)
            }
            fieldNames.sort()
            return fieldNames
        }
    }
}
