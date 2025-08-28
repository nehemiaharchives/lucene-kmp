package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.FreqProxTermsWriterPerField.FreqProxPostingsArray
import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBlockPool
import org.gnit.lucenekmp.util.BytesRefBuilder


/**
 * Implements limited (iterators only, no stats) [Fields] interface over the in-RAM buffered
 * fields/terms/postings, to flush postings through the PostingsFormat.
 */
internal class FreqProxFields(fieldList: MutableList<FreqProxTermsWriterPerField>) : Fields() {
    val fields: MutableMap<String, FreqProxTermsWriterPerField> =
        LinkedHashMap()

    init {
        // NOTE: fields are already sorted by field name
        for (field in fieldList) {
            fields.put(field.fieldName, field)
        }
    }

    override fun iterator(): MutableIterator<String> {
        return fields.keys.iterator()
    }

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        val perField: FreqProxTermsWriterPerField? = fields[field]
        return if (perField == null) null else FreqProxTerms(perField)
    }

    override fun size(): Int {
        throw UnsupportedOperationException()
    }

    private class FreqProxTerms(val terms: FreqProxTermsWriterPerField) : Terms() {

        override fun iterator(): TermsEnum {
            val termsEnum = FreqProxTermsEnum(terms)
            termsEnum.reset()
            return termsEnum
        }

        override fun size(): Long {
            throw UnsupportedOperationException()
        }

        override val sumTotalTermFreq: Long
            get() {
                throw UnsupportedOperationException()
            }

        override val sumDocFreq: Long
            get() {
                throw UnsupportedOperationException()
            }

        override val docCount: Int
            get() {
                throw UnsupportedOperationException()
            }

        override fun hasFreqs(): Boolean {
            return terms.indexOptions >= IndexOptions.DOCS_AND_FREQS
        }

        override fun hasOffsets(): Boolean {
            // NOTE: the in-memory buffer may have indexed offsets
            // because that's what FieldInfo said when we started,
            // but during indexing this may have been downgraded:
            return (terms.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        }

        override fun hasPositions(): Boolean {
            // NOTE: the in-memory buffer may have indexed positions
            // because that's what FieldInfo said when we started,
            // but during indexing this may have been downgraded:
            return terms.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        }

        override fun hasPayloads(): Boolean {
            return terms.sawPayloads
        }
    }

    private class FreqProxTermsEnum(val terms: FreqProxTermsWriterPerField) : BaseTermsEnum() {
        val termsPool: BytesRefBlockPool = BytesRefBlockPool(terms.bytePool)
        val sortedTermIDs: IntArray = terms.getSortedTermIDs()
        val postingsArray: FreqProxPostingsArray = terms.postingsArray as FreqProxPostingsArray
        val scratch: BytesRef = BytesRef()
        val numTerms: Int = terms.numTerms
        var ord: Int = 0

        fun reset() {
            ord = -1
        }

        override fun seekCeil(text: BytesRef): SeekStatus {
            // TODO: we could instead keep the BytesRefHash
            // intact so this is a hash lookup

            // binary search:

            var lo = 0
            var hi = numTerms - 1
            while (hi >= lo) {
                val mid = (lo + hi) ushr 1
                val textStart: Int = postingsArray.textStarts[sortedTermIDs[mid]]
                termsPool.fillBytesRef(scratch, textStart)
                val cmp = scratch.compareTo(text)
                if (cmp < 0) {
                    lo = mid + 1
                } else if (cmp > 0) {
                    hi = mid - 1
                } else {
                    // found:
                    ord = mid
                    assert(term().compareTo(text) == 0)
                    return SeekStatus.FOUND
                }
            }

            // not found:
            ord = lo
            if (ord >= numTerms) {
                return SeekStatus.END
            } else {
                val textStart: Int = postingsArray.textStarts[sortedTermIDs[ord]]
                termsPool.fillBytesRef(scratch, textStart)
                assert(term() > text)
                return SeekStatus.NOT_FOUND
            }
        }

        override fun seekExact(ord: Long) {
            this.ord = ord.toInt()
            val textStart: Int = postingsArray.textStarts[sortedTermIDs[this.ord]]
            termsPool.fillBytesRef(scratch, textStart)
        }

        override fun next(): BytesRef? {
            ord++
            if (ord >= numTerms) {
                return null
            } else {
                val textStart: Int = postingsArray.textStarts[sortedTermIDs[ord]]
                termsPool.fillBytesRef(scratch, textStart)
                return scratch
            }
        }

        override fun term(): BytesRef {
            return scratch
        }

        override fun ord(): Long {
            return ord.toLong()
        }

        override fun docFreq(): Int {
            // We do not store this per-term, and we cannot
            // implement this at merge time w/o an added pass
            // through the postings:
            throw UnsupportedOperationException()
        }

        override fun totalTermFreq(): Long {
            // We do not store this per-term, and we cannot
            // implement this at merge time w/o an added pass
            // through the postings:
            throw UnsupportedOperationException()
        }

        override fun postings(
            reuse: PostingsEnum?,
            flags: Int
        ): PostingsEnum {
            if (PostingsEnum.featureRequested(
                    flags,
                    PostingsEnum.POSITIONS
                )
            ) {
                var posEnum: FreqProxPostingsEnum

                require(terms.hasProx) { "did not index positions" }

                require(
                    !(!terms.hasOffsets && PostingsEnum.featureRequested(
                        flags,
                        PostingsEnum.OFFSETS
                    ))
                ) { "did not index offsets" }

                if (reuse is FreqProxPostingsEnum) {
                    posEnum = reuse
                    if (posEnum.postingsArray != postingsArray) {
                        posEnum = FreqProxPostingsEnum(terms, postingsArray)
                    }
                } else {
                    posEnum = FreqProxPostingsEnum(terms, postingsArray)
                }
                posEnum.reset(sortedTermIDs[ord])
                return posEnum
            }

            var docsEnum: FreqProxDocsEnum

            require(
                !(!terms.hasFreq && PostingsEnum.featureRequested(
                    flags,
                    PostingsEnum.FREQS
                ))
            ) { "did not index freq" }

            if (reuse is FreqProxDocsEnum) {
                docsEnum = reuse
                if (docsEnum.postingsArray != postingsArray) {
                    docsEnum = FreqProxDocsEnum(terms, postingsArray)
                }
            } else {
                docsEnum = FreqProxDocsEnum(terms, postingsArray)
            }
            docsEnum.reset(sortedTermIDs[ord])
            return docsEnum
        }

        @Throws(IOException::class)
        override fun impacts(flags: Int): ImpactsEnum {
            throw UnsupportedOperationException()
        }

        /**
         * Expert: Returns the TermsEnums internal state to position the TermsEnum without re-seeking
         * the term dictionary.
         *
         *
         * NOTE: A seek by [TermState] might not capture the [AttributeSource]'s state.
         * Callers must maintain the [AttributeSource] states separately
         *
         * @see TermState
         *
         * @see .seekExact
         */
        @Throws(IOException::class)
        override fun termState(): TermState {
            return object : TermState() {
                override fun copyFrom(other: TermState) {
                    throw UnsupportedOperationException()
                }
            }
        }
    }

    private class FreqProxDocsEnum(val terms: FreqProxTermsWriterPerField, val postingsArray: FreqProxPostingsArray) :
        PostingsEnum() {
        val reader: ByteSliceReader = ByteSliceReader()
        val readTermFreq: Boolean = terms.hasFreq
        var docID: Int = -1
        var freq: Int = 0
        var ended: Boolean = false
        var termID: Int = 0

        fun reset(termID: Int) {
            this.termID = termID
            terms.initReader(reader, termID, 0)
            ended = false
            docID = -1
        }

        override fun docID(): Int {
            return docID
        }

        override fun freq(): Int {
            // Don't lie here ... don't want codecs writings lots
            // of wasted 1s into the index:
            check(readTermFreq) { "freq was not indexed" }
            return freq
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            return -1
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return -1
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return -1
        }

        override val payload: BytesRef?
            get() = null

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            if (docID == -1) {
                docID = 0
            }
            if (reader.eof()) {
                if (ended) {
                    return NO_MORE_DOCS
                } else {
                    ended = true
                    docID = postingsArray.lastDocIDs[termID]
                    if (readTermFreq) {
                        freq = postingsArray.termFreqs!![termID]
                    }
                }
            } else {
                val code: Int = reader.readVInt()
                if (!readTermFreq) {
                    docID += code
                } else {
                    docID += code ushr 1
                    freq = if ((code and 1) != 0) {
                        1
                    } else {
                        reader.readVInt()
                    }
                }

                assert(docID != postingsArray.lastDocIDs[termID])
            }

            return docID
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun cost(): Long {
            throw UnsupportedOperationException()
        }
    }

    private class FreqProxPostingsEnum(val terms: FreqProxTermsWriterPerField, val postingsArray: FreqProxPostingsArray) :
        PostingsEnum() {
        val reader: ByteSliceReader = ByteSliceReader()
        val posReader: ByteSliceReader = ByteSliceReader()
        val readOffsets: Boolean = terms.hasOffsets
        var docID: Int = -1
        var freq: Int = 0
        var pos: Int = 0
        var startOffset: Int = 0
        var endOffset: Int = 0
        var posLeft: Int = 0
        var termID: Int = 0
        var ended: Boolean = false
        var hasPayload: Boolean = false
        var payloadBuilder: BytesRefBuilder = BytesRefBuilder()

        init {
            assert(terms.hasProx)
            assert(terms.hasFreq)
        }

        fun reset(termID: Int) {
            this.termID = termID
            terms.initReader(reader, termID, 0)
            terms.initReader(posReader, termID, 1)
            ended = false
            docID = -1
            posLeft = 0
        }

        override fun docID(): Int {
            return docID
        }

        override fun freq(): Int {
            return freq
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            if (docID == -1) {
                docID = 0
            }
            while (posLeft != 0) {
                nextPosition()
            }

            if (reader.eof()) {
                if (ended) {
                    return NO_MORE_DOCS
                } else {
                    ended = true
                    docID = postingsArray.lastDocIDs[termID]
                    freq = postingsArray.termFreqs!![termID]
                }
            } else {
                val code: Int = reader.readVInt()
                docID += code ushr 1
                freq = if ((code and 1) != 0) {
                    1
                } else {
                    reader.readVInt()
                }

                assert(docID != postingsArray.lastDocIDs[termID])
            }

            posLeft = freq
            pos = 0
            startOffset = 0
            return docID
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun cost(): Long {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            assert(posLeft > 0)
            posLeft--
            val code: Int = posReader.readVInt()
            pos += code ushr 1
            if ((code and 1) != 0) {
                hasPayload = true
                // has a payload
                payloadBuilder.setLength(posReader.readVInt())
                payloadBuilder.growNoCopy(payloadBuilder.length())
                posReader.readBytes(payloadBuilder.bytes(), 0, payloadBuilder.length())
            } else {
                hasPayload = false
            }

            if (readOffsets) {
                startOffset += posReader.readVInt()
                endOffset = startOffset + posReader.readVInt()
            }

            return pos
        }

        override fun startOffset(): Int {
            check(readOffsets) { "offsets were not indexed" }
            return startOffset
        }

        override fun endOffset(): Int {
            check(readOffsets) { "offsets were not indexed" }
            return endOffset
        }

        override val payload
            get(): BytesRef? {
                return if (hasPayload) {
                    payloadBuilder.get()
                } else {
                    null
                }
        }
    }
}
