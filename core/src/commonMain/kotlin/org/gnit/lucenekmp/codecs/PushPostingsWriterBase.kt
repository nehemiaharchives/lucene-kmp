package org.gnit.lucenekmp.codecs


import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import kotlin.experimental.or

/**
 * Extension of [PostingsWriterBase], adding a push API for writing each element of the
 * postings. This API is somewhat analogous to an XML SAX API, while [PostingsWriterBase] is
 * more like an XML DOM API.
 *
 * @see PostingsReaderBase
 *
 * @lucene.experimental
 */
// TODO: find a better name; this defines the API that the
// terms dict impls use to talk to a postings impl.
// TermsDict + PostingsReader/WriterBase == PostingsConsumer/Producer
abstract class PushPostingsWriterBase
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : PostingsWriterBase() {
    // Reused in writeTerm
    private var postingsEnum: PostingsEnum? = null
    private var enumFlags = 0

    /** [FieldInfo] of current field being written.  */
    protected var fieldInfo: FieldInfo? = null

    /** [IndexOptions] of current field being written  */
    protected var indexOptions: IndexOptions? = null

    /** True if the current field writes freqs.  */
    protected var writeFreqs: Boolean = false

    /** True if the current field writes positions.  */
    protected var writePositions: Boolean = false

    /** True if the current field writes payloads.  */
    protected var writePayloads: Boolean = false

    /** True if the current field writes offsets.  */
    protected var writeOffsets: Boolean = false

    /** Return a newly created empty TermState  */
    @Throws(IOException::class)
    abstract fun newTermState(): BlockTermState

    /**
     * Start a new term. Note that a matching call to [.finishTerm] is done,
     * only if the term has at least one document.
     */
    @Throws(IOException::class)
    abstract fun startTerm(norms: NumericDocValues?)

    /**
     * Finishes the current term. The provided [BlockTermState] contains the term's summary
     * statistics, and will holds metadata from PBF when returned
     */
    @Throws(IOException::class)
    abstract fun finishTerm(state: BlockTermState)

    /**
     * Sets the current field for writing, and returns the fixed length of long[] metadata (which is
     * fixed per field), called when the writing switches to another field.
     */
    override fun setField(fieldInfo: FieldInfo) {
        this.fieldInfo = fieldInfo
        indexOptions = fieldInfo.indexOptions

        writeFreqs = indexOptions!! >= IndexOptions.DOCS_AND_FREQS
        writePositions = indexOptions!! >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        writeOffsets =
            indexOptions!! >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
        writePayloads = fieldInfo.hasPayloads()

        if (!writeFreqs) {
            enumFlags = 0
        } else if (!writePositions) {
            enumFlags = PostingsEnum.FREQS.toInt()
        } else if (!writeOffsets) {
            if (writePayloads) {
                enumFlags = PostingsEnum.PAYLOADS.toInt()
            } else {
                enumFlags = PostingsEnum.POSITIONS.toInt()
            }
        } else {
            if (writePayloads) {
                enumFlags = (PostingsEnum.PAYLOADS or PostingsEnum.OFFSETS).toInt()
            } else {
                enumFlags = PostingsEnum.OFFSETS.toInt()
            }
        }
    }

    @Throws(IOException::class)
    override fun writeTerm(
        term: BytesRef, termsEnum: TermsEnum, docsSeen: FixedBitSet, norms: NormsProducer?
    ): BlockTermState? {
        val normValues: NumericDocValues? = if (!fieldInfo!!.hasNorms()) {
            null
        } else {
            checkNotNull(norms) { "norms is null but field has norms: ${fieldInfo!!.name}" }
                .getNorms(fieldInfo!!)
        }
        startTerm(normValues)
        postingsEnum = termsEnum.postings(postingsEnum, enumFlags)
        checkNotNull(postingsEnum)

        var docFreq = 0
        var totalTermFreq: Long = 0
        while (true) {
            val docID: Int = postingsEnum!!.nextDoc()
            if (docID == NO_MORE_DOCS) {
                break
            }
            docFreq++
            docsSeen.set(docID)
            val freq: Int
            if (writeFreqs) {
                freq = postingsEnum!!.freq()
                totalTermFreq += freq.toLong()
            } else {
                freq = -1
            }
            startDoc(docID, freq)

            if (writePositions) {
                for (i in 0..<freq) {
                    val pos: Int = postingsEnum!!.nextPosition()
                    val payload: BytesRef? = if (writePayloads) postingsEnum!!.payload else null
                    val startOffset: Int
                    val endOffset: Int
                    if (writeOffsets) {
                        startOffset = postingsEnum!!.startOffset()
                        endOffset = postingsEnum!!.endOffset()
                    } else {
                        startOffset = -1
                        endOffset = -1
                    }
                    addPosition(pos, payload, startOffset, endOffset)
                }
            }

            finishDoc()
        }

        if (docFreq == 0) {
            return null
        } else {
            val state = newTermState()
            state.docFreq = docFreq
            state.totalTermFreq = if (writeFreqs) totalTermFreq else -1
            finishTerm(state)
            return state
        }
    }

    /**
     * Adds a new doc in this term. `freq` will be -1 when term frequencies are omitted for
     * the field.
     */
    @Throws(IOException::class)
    abstract fun startDoc(docID: Int, freq: Int)

    /**
     * Add a new position and payload, and start/end offset. A null payload means no payload; a
     * non-null payload with zero length also means no payload. Caller may reuse the [BytesRef]
     * for the payload between calls (method must fully consume the payload). `startOffset`
     * and `endOffset` will be -1 when offsets are not indexed.
     */
    @Throws(IOException::class)
    abstract fun addPosition(position: Int, payload: BytesRef?, startOffset: Int, endOffset: Int)

    /** Called when we are done adding positions and payloads for each doc.  */
    @Throws(IOException::class)
    abstract fun finishDoc()
}
