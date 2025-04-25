package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.index.DocIDMerger
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import kotlin.experimental.or

/**
 * Codec API for writing term vectors:
 *
 *
 *  1. For every document, [.startDocument] is called, informing the Codec how many
 * fields will be written.
 *  1. [.startField] is called for each field in
 * the document, informing the codec how many terms will be written for that field, and
 * whether or not positions, offsets, or payloads are enabled.
 *  1. Within each field, [.startTerm] is called for each term.
 *  1. If offsets and/or positions are enabled, then [.addPosition]
 * will be called for each term occurrence.
 *  1. After all documents have been written, [.finish] is called for
 * verification/sanity-checks.
 *  1. Finally the writer is closed ([.close])
 *
 *
 * @lucene.experimental
 */
abstract class TermVectorsWriter
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable, Accountable {
    /**
     * Called before writing the term vectors of the document. [.startField] will be called `numVectorFields` times. Note that if
     * term vectors are enabled, this is called even if the document has no vector fields, in this
     * case `numVectorFields` will be zero.
     */
    @Throws(IOException::class)
    abstract fun startDocument(numVectorFields: Int)

    /** Called after a doc and all its fields have been added.  */
    @Throws(IOException::class)
    open fun finishDocument() {
    }

    /**
     * Called before writing the terms of the field. [.startTerm] will be called
     * `numTerms` times.
     */
    @Throws(IOException::class)
    abstract fun startField(
        info: FieldInfo?, numTerms: Int, positions: Boolean, offsets: Boolean, payloads: Boolean
    )

    /** Called after a field and all its terms have been added.  */
    @Throws(IOException::class)
    open fun finishField() {
    }

    /**
     * Adds a term and its term frequency `freq`. If this field has positions and/or
     * offsets enabled, then [.addPosition] will be called `freq
    ` *  times respectively.
     */
    @Throws(IOException::class)
    abstract fun startTerm(term: BytesRef?, freq: Int)

    /** Called after a term and all its positions have been added.  */
    @Throws(IOException::class)
    fun finishTerm() {
    }

    /** Adds a term position and offsets  */
    @Throws(IOException::class)
    abstract fun addPosition(position: Int, startOffset: Int, endOffset: Int, payload: BytesRef?)

    /**
     * Called before [.close], passing in the number of documents that were written. Note that
     * this is intentionally redundant (equivalent to the number of calls to [ ][.startDocument], but a Codec should check that this is the case to detect the JRE bug
     * described in LUCENE-1282.
     */
    @Throws(IOException::class)
    abstract fun finish(numDocs: Int)

    /**
     * Called by IndexWriter when writing new segments.
     *
     *
     * This is an expert API that allows the codec to consume positions and offsets directly from
     * the indexer.
     *
     *
     * The default implementation calls [.addPosition], but
     * subclasses can override this if they want to efficiently write all the positions, then all the
     * offsets, for example.
     *
     *
     * NOTE: This API is extremely expert and subject to change or removal!!!
     *
     * @lucene.internal
     */
    // TODO: we should probably nuke this and make a more efficient 4.x format
    // PreFlex-RW could then be slow and buffer (it's only used in tests...)
    @Throws(IOException::class)
    open fun addProx(numProx: Int, positions: DataInput?, offsets: DataInput?) {
        var position = 0
        var lastOffset = 0
        var payload: BytesRefBuilder? = null

        for (i in 0..<numProx) {
            val startOffset: Int
            val endOffset: Int
            val thisPayload: BytesRef?

            if (positions == null) {
                position = -1
                thisPayload = null
            } else {
                val code: Int = positions.readVInt()
                position += code ushr 1
                if ((code and 1) != 0) {
                    // This position has a payload
                    val payloadLength: Int = positions.readVInt()

                    if (payload == null) {
                        payload = BytesRefBuilder()
                    }
                    payload.growNoCopy(payloadLength)

                    positions.readBytes(payload.bytes(), 0, payloadLength)
                    payload.setLength(payloadLength)
                    thisPayload = payload.get()
                } else {
                    thisPayload = null
                }
            }

            if (offsets == null) {
                endOffset = -1
                startOffset = endOffset
            } else {
                startOffset = lastOffset + offsets.readVInt()
                endOffset = startOffset + offsets.readVInt()
                lastOffset = endOffset
            }
            addPosition(position, startOffset, endOffset, thisPayload)
        }
    }

    private class TermVectorsMergeSub(
        docMap: MergeState.DocMap,
        val reader: TermVectorsReader?,
        private val maxDoc: Int
    ) : DocIDMerger.Sub(docMap) {
        var docID: Int = -1

        override fun nextDoc(): Int {
            docID++
            return if (docID == maxDoc) {
                NO_MORE_DOCS
            } else {
                docID
            }
        }
    }

    /**
     * Merges in the term vectors from the readers in `mergeState`. The default
     * implementation skips over deleted documents, and uses [.startDocument], [ ][.startField], [.startTerm],
     * [.addPosition], and [.finish], returning the number
     * of documents that were written. Implementations can override this method for more sophisticated
     * merging (bulk-byte copying, etc).
     */
    @Throws(IOException::class)
    open fun merge(mergeState: MergeState): Int {
        val subs: MutableList<TermVectorsMergeSub> = ArrayList()
        for (i in 0..<mergeState.termVectorsReaders.size) {
            val reader: TermVectorsReader? = mergeState.termVectorsReaders[i]
            reader?.checkIntegrity()
            subs.add(TermVectorsMergeSub(mergeState.docMaps!![i], reader, mergeState.maxDocs[i]))
        }

        val docIDMerger: DocIDMerger<TermVectorsMergeSub> =
            DocIDMerger.of(subs, mergeState.needsIndexSort)

        var docCount = 0
        while (true) {
            val sub: TermVectorsMergeSub? = docIDMerger.next()
            if (sub == null) {
                break
            }

            // NOTE: it's very important to first assign to vectors then pass it to
            // termVectorsWriter.addAllDocVectors; see LUCENE-1282
            val vectors: Fields? = sub.reader?.get(sub.docID)
            addAllDocVectors(vectors, mergeState)
            docCount++
        }
        finish(docCount)
        return docCount
    }

    /** Safe (but, slowish) default method to write every vector field in the document.  */
    @Throws(IOException::class)
    protected fun addAllDocVectors(vectors: Fields?, mergeState: MergeState) {
        if (vectors == null) {
            startDocument(0)
            finishDocument()
            return
        }

        var numFields: Int = vectors.size()
        if (numFields == -1) {
            // count manually! TODO: Maybe enforce that Fields.size() returns something valid
            numFields = 0
            val it: MutableIterator<String> = vectors.iterator()
            while (it.hasNext()) {
                it.next()
                numFields++
            }
        }
        startDocument(numFields)

        var lastFieldName: String? = null

        var termsEnum: TermsEnum? = null
        var docsAndPositionsEnum: PostingsEnum? = null

        var fieldCount = 0
        for (fieldName in vectors) {
            fieldCount++
            val fieldInfo: FieldInfo? = mergeState.mergeFieldInfos!!.fieldInfo(fieldName)

            require(
                lastFieldName == null || fieldName > lastFieldName
            ) { "lastFieldName=$lastFieldName fieldName=$fieldName" }
            lastFieldName = fieldName

            val terms: Terms? = vectors.terms(fieldName)
            if (terms == null) {
                // FieldsEnum shouldn't lie...
                continue
            }

            val hasPositions: Boolean = terms.hasPositions()
            val hasOffsets: Boolean = terms.hasOffsets()
            val hasPayloads: Boolean = terms.hasPayloads()
            require(!hasPayloads || hasPositions)

            var numTerms = terms.size().toInt()
            if (numTerms == -1) {
                // count manually. It is stupid, but needed, as Terms.size() is not a mandatory statistics
                // function
                numTerms = 0
                termsEnum = terms.iterator()
                while (termsEnum.next() != null) {
                    numTerms++
                }
            }

            startField(fieldInfo, numTerms, hasPositions, hasOffsets, hasPayloads)
            termsEnum = terms.iterator()

            var termCount = 0
            while (termsEnum.next() != null) {
                termCount++

                val freq = termsEnum.totalTermFreq().toInt()

                startTerm(termsEnum.term(), freq)

                if (hasPositions || hasOffsets) {
                    docsAndPositionsEnum =
                        termsEnum.postings(
                            docsAndPositionsEnum, (PostingsEnum.OFFSETS or PostingsEnum.PAYLOADS).toInt()
                        )
                    checkNotNull(docsAndPositionsEnum)

                    val docID: Int = docsAndPositionsEnum.nextDoc()
                    require(docID != NO_MORE_DOCS)
                    require(docsAndPositionsEnum.freq() == freq)

                    for (posUpto in 0..<freq) {
                        val pos: Int = docsAndPositionsEnum.nextPosition()
                        val startOffset: Int = docsAndPositionsEnum.startOffset()
                        val endOffset: Int = docsAndPositionsEnum.endOffset()

                        val payload: BytesRef? = docsAndPositionsEnum.payload

                        require(!hasPositions || pos >= 0)
                        addPosition(pos, startOffset, endOffset, payload)
                    }
                }
                finishTerm()
            }
            require(termCount == numTerms)
            finishField()
        }
        require(fieldCount == numFields)
        finishDocument()
    }

    @Throws(IOException::class)
    abstract override fun close()
}
