package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IntBlockPool
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.LSBRadixSorter
import org.gnit.lucenekmp.util.LongsRef
import org.gnit.lucenekmp.util.TimSorter
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlin.math.max


class FreqProxTermsWriter(
    intBlockAllocator: IntBlockPool.Allocator,
    byteBlockAllocator: ByteBlockPool.Allocator,
    bytesUsed: Counter,
    termVectors: TermsHash
) : TermsHash(intBlockAllocator, byteBlockAllocator, bytesUsed, termVectors) {
    @Throws(IOException::class)
    private fun applyDeletes(state: SegmentWriteState, fields: Fields) {
        // Process any pending Term deletes for this newly
        // flushed segment:
        if (state.segUpdates != null && state.segUpdates.deleteTerms.size() > 0) {
            val segDeletes: BufferedUpdates.DeletedTerms = state.segUpdates.deleteTerms
            val iterator: FrozenBufferedUpdates.TermDocsIterator =
                FrozenBufferedUpdates.TermDocsIterator(fields, true)

            segDeletes.forEachOrdered<IOException> { term: Term, docId: Int ->
                val postings: DocIdSetIterator? =
                    iterator.nextTerm(term.field(), term.bytes())
                if (postings != null) {
                    assert(docId < DocIdSetIterator.NO_MORE_DOCS)
                    var doc: Int
                    while ((postings.nextDoc().also { doc = it }) < docId) {
                        if (state.liveDocs == null) {
                            state.liveDocs = FixedBitSet(state.segmentInfo.maxDoc())
                            state.liveDocs!!.set(0, state.segmentInfo.maxDoc())
                        }
                        if (state.liveDocs!!.getAndClear(doc)) {
                            state.delCountOnFlush++
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun flush(
        fieldsToFlush: MutableMap<String, TermsHashPerField>,
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?,
        norms: NormsProducer?
    ) {
        super.flush(fieldsToFlush, state, sortMap, norms)

        // Gather all fields that saw any postings:
        val allFields: MutableList<FreqProxTermsWriterPerField> = mutableListOf()

        for (f in fieldsToFlush.values) {
            val perField: FreqProxTermsWriterPerField =
                f as FreqProxTermsWriterPerField
            if (perField.numTerms > 0) {
                perField.sortTerms()
                assert(perField.indexOptions != IndexOptions.NONE)
                allFields.add(perField)
            }
        }

        if (!state.fieldInfos!!.hasPostings()) {
            assert(allFields.isEmpty())
            return
        }

        // Sort by field name
        CollectionUtil.introSort(allFields)

        var fields: Fields = FreqProxFields(allFields)
        applyDeletes(state, fields)
        if (sortMap != null) {
            val docMap: Sorter.DocMap = sortMap
            val infos: FieldInfos = state.fieldInfos
            fields =
                object : FilterLeafReader.FilterFields(fields) {
                    @Throws(IOException::class)
                    override fun terms(field: String?): Terms? {
                        val terms: Terms? = `in`.terms(field)
                        return if (terms == null) {
                            null
                        } else {
                            SortingTerms(terms, infos.fieldInfo(field!!)!!.indexOptions, docMap)
                        }
                    }
                }
        }

        state.segmentInfo.codec.postingsFormat().fieldsConsumer(state).use { consumer ->
            consumer.write(fields, norms)
        }
    }

    override fun addField(
        invertState: FieldInvertState,
        fieldInfo: FieldInfo
    ): TermsHashPerField {
        return FreqProxTermsWriterPerField(
            invertState, this, fieldInfo, nextTermsHash!!.addField(invertState, fieldInfo)
        )
    }

    internal class SortingTerms(
        `in`: Terms,
        private val indexOptions: IndexOptions,
        private val docMap: Sorter.DocMap
    ) : FilterLeafReader.FilterTerms(`in`) {

        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            return SortingTermsEnum(`in`.iterator(), docMap, indexOptions)
        }

        @Throws(IOException::class)
        override fun intersect(
            compiled: CompiledAutomaton,
            startTerm: BytesRef?
        ): TermsEnum {
            return SortingTermsEnum(`in`.intersect(compiled, startTerm), docMap, indexOptions)
        }

        override val sumTotalTermFreq: Long
            get() = throw UnsupportedOperationException(
                "sumTotalTermFreq is not supported by SortingTerms"
            )
    }

    private class SortingTermsEnum(
        `in`: TermsEnum,
        // pkg-protected to avoid synthetic accessor methods
        val docMap: Sorter.DocMap,
        private val indexOptions: IndexOptions
    ) : FilterLeafReader.FilterTermsEnum(`in`) {

        @Throws(IOException::class)
        override fun postings(
            reuse: PostingsEnum?,
            flags: Int
        ): PostingsEnum {
            if (indexOptions >= IndexOptions.DOCS_AND_FREQS
                && PostingsEnum.featureRequested(
                    flags,
                    PostingsEnum.FREQS
                )
            ) {
                val inReuse: PostingsEnum?
                val wrapReuse: SortingPostingsEnum
                if (reuse != null && reuse is SortingPostingsEnum) {
                    // if we're asked to reuse the given DocsEnum and it is Sorting, return
                    // the wrapped one, since some Codecs expect it.
                    wrapReuse = reuse
                    inReuse = wrapReuse.wrapped
                } else {
                    wrapReuse = SortingPostingsEnum()
                    inReuse = reuse
                }

                val inDocsAndPositions: PostingsEnum? = `in`.postings(inReuse, flags)
                // we ignore the fact that positions/offsets may be stored but not asked for,
                // since this code is expected to be used during addIndexes which will
                // ask for everything. if that assumption changes in the future, we can
                // factor in whether 'flags' says offsets are not required.
                val storePositions =
                    indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                val storeOffsets =
                    indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
                wrapReuse.reset(docMap, inDocsAndPositions, storePositions, storeOffsets)
                return wrapReuse
            }

            val inReuse: PostingsEnum?
            val wrapReuse: SortingDocsEnum
            if (reuse != null && reuse is SortingDocsEnum) {
                // if we're asked to reuse the given DocsEnum and it is Sorting, return
                // the wrapped one, since some Codecs expect it.
                wrapReuse = reuse
                inReuse = wrapReuse.wrapped
            } else {
                wrapReuse = SortingDocsEnum()
                inReuse = reuse
            }

            val inDocs: PostingsEnum = `in`.postings(inReuse, flags)!!
            wrapReuse.reset(docMap, inDocs)
            return wrapReuse
        }
    }

    internal class SortingDocsEnum : PostingsEnum() {
        private val sorter: LSBRadixSorter = LSBRadixSorter()
        private var `in`: PostingsEnum? = null
        private var docs: IntArray = IntsRef.EMPTY_INTS
        private var docIt = 0
        private var upTo = 0

        @Throws(IOException::class)
        fun reset(docMap: Sorter.DocMap, `in`: PostingsEnum) {
            this.`in` = `in`
            var i = 0
            var doc: Int = `in`.nextDoc()
            while (doc != NO_MORE_DOCS) {
                if (docs.size <= i) {
                    docs = ArrayUtil.grow(docs)
                }
                docs[i++] = docMap.oldToNew(doc)
                doc = `in`.nextDoc()
            }
            upTo = i
            if (docs.size == upTo) {
                docs = ArrayUtil.grow(docs)
            }
            docs[upTo] = NO_MORE_DOCS
            val maxDoc: Int = docMap.size()
            val numBits: Int = PackedInts.bitsRequired(max(0, maxDoc - 1).toLong())
            // Even though LSBRadixSorter cannot take advantage of partial ordering like TimSorter it is
            // often still faster for nearly-sorted inputs.
            sorter.sort(numBits, docs, upTo)
            docIt = -1
        }

        val wrapped: PostingsEnum?
            get() = `in`

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            // need to support it for checkIndex, but in practice it won't be called, so
            // don't bother to implement efficiently for now.
            return slowAdvance(target)
        }

        override fun docID(): Int {
            return if (docIt < 0) -1 else docs[docIt]
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return docs[++docIt]
        }

        override fun cost(): Long {
            return upTo.toLong()
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            return 1
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
    }

    internal class SortingPostingsEnum : PostingsEnum() {
        /**
         * A [TimSorter] which sorts two parallel arrays of doc IDs and offsets in one go. Everyti
         * me a doc ID is 'swapped', its corresponding offset is swapped too.
         */
        private class DocOffsetSorter(numTempSlots: Int) : TimSorter(numTempSlots) {
            private lateinit var docs: IntArray
            private lateinit var offsets: LongArray
            private var tmpDocs: IntArray
            private var tmpOffsets: LongArray

            init {
                this.tmpDocs = IntsRef.EMPTY_INTS
                this.tmpOffsets = LongsRef.EMPTY_LONGS
            }

            fun reset(docs: IntArray, offsets: LongArray) {
                this.docs = docs
                this.offsets = offsets
            }

            override fun compare(i: Int, j: Int): Int {
                return docs[i] - docs[j]
            }

            override fun swap(i: Int, j: Int) {
                val tmpDoc = docs[i]
                docs[i] = docs[j]
                docs[j] = tmpDoc

                val tmpOffset = offsets[i]
                offsets[i] = offsets[j]
                offsets[j] = tmpOffset
            }

            override fun copy(src: Int, dest: Int) {
                docs[dest] = docs[src]
                offsets[dest] = offsets[src]
            }

            override fun save(i: Int, len: Int) {
                if (tmpDocs.size < len) {
                    tmpDocs = IntArray(ArrayUtil.oversize(len, Int.SIZE_BYTES))
                    tmpOffsets = LongArray(tmpDocs.size)
                }
                System.arraycopy(docs, i, tmpDocs, 0, len)
                System.arraycopy(offsets, i, tmpOffsets, 0, len)
            }

            override fun restore(i: Int, j: Int) {
                docs[j] = tmpDocs[i]
                offsets[j] = tmpOffsets[i]
            }

            override fun compareSaved(i: Int, j: Int): Int {
                return tmpDocs[i] - docs[j]
            }
        }

        private var sorter: DocOffsetSorter? = null
        private var docs: IntArray = IntsRef.EMPTY_INTS
        private var offsets: LongArray = LongsRef.EMPTY_LONGS
        private var upto = 0

        private var postingInput: ByteBuffersDataInput? = null
        private var `in`: PostingsEnum? = null
        private var storePositions = false
        private var storeOffsets = false

        private var docIt = 0
        private var pos = 0
        private var startOffset = 0
        private var endOffset = 0
        private val payloadRef = BytesRef()
        override val payload: BytesRef?
            get(): BytesRef? {
                return if (payloadRef.length == 0) null else payloadRef
            }

        private var currFreq = 0

        private val buffer: ByteBuffersDataOutput =
            ByteBuffersDataOutput.newResettableInstance()

        @Throws(IOException::class)
        fun reset(
            docMap: Sorter.DocMap,
            `in`: PostingsEnum?,
            storePositions: Boolean,
            storeOffsets: Boolean
        ) {
            this.`in` = `in`
            this.storePositions = storePositions
            this.storeOffsets = storeOffsets
            if (sorter == null) {
                val numTempSlots: Int = docMap.size() / 8
                sorter = DocOffsetSorter(numTempSlots)
            }
            docIt = -1
            startOffset = -1
            endOffset = -1

            buffer.reset()
            var doc: Int
            var i = 0
            while ((`in`!!.nextDoc().also { doc = it }) != NO_MORE_DOCS) {
                if (i == docs.size) {
                    val newLength: Int = ArrayUtil.oversize(i + 1, 4)
                    docs = ArrayUtil.growExact(docs, newLength)
                    offsets = ArrayUtil.growExact(offsets, newLength)
                }
                docs[i] = docMap.oldToNew(doc)
                offsets[i] = buffer.size()
                addPositions(`in`, buffer)
                i++
            }
            upto = i
            sorter!!.reset(docs, offsets)
            sorter!!.sort(0, upto)

            this.postingInput = buffer.toDataInput()
        }

        @Throws(IOException::class)
        private fun addPositions(`in`: PostingsEnum, out: DataOutput) {
            val freq: Int = `in`.freq()
            out.writeVInt(freq)
            if (storePositions) {
                var previousPosition = 0
                var previousEndOffset = 0
                for (i in 0..<freq) {
                    val pos: Int = `in`.nextPosition()
                    val payload: BytesRef? = `in`.payload
                    // The low-order bit of token is set only if there is a payload, the
                    // previous bits are the delta-encoded position.
                    val token = (pos - previousPosition) shl 1 or (if (payload == null) 0 else 1)
                    out.writeVInt(token)
                    previousPosition = pos
                    if (storeOffsets) { // don't encode offsets if they are not stored
                        val startOffset: Int = `in`.startOffset()
                        val endOffset: Int = `in`.endOffset()
                        out.writeVInt(startOffset - previousEndOffset)
                        out.writeVInt(endOffset - startOffset)
                        previousEndOffset = endOffset
                    }
                    if (payload != null) {
                        out.writeVInt(payload.length)
                        out.writeBytes(payload.bytes, payload.offset, payload.length)
                    }
                }
            }
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            // need to support it for checkIndex, but in practice it won't be called, so
            // don't bother to implement efficiently for now.
            return slowAdvance(target)
        }

        override fun docID(): Int {
            return if (docIt < 0) -1 else if (docIt >= upto) NO_MORE_DOCS else docs[docIt]
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return endOffset
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            return currFreq
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            if (++docIt >= upto) return NO_MORE_DOCS
            postingInput!!.seek(offsets[docIt])
            currFreq = postingInput!!.readVInt()
            // reset variables used in nextPosition
            pos = 0
            endOffset = 0
            return docs[docIt]
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            if (!storePositions) {
                return -1
            }
            val token: Int = postingInput!!.readVInt()
            pos += token ushr 1
            if (storeOffsets) {
                startOffset = endOffset + postingInput!!.readVInt()
                endOffset = startOffset + postingInput!!.readVInt()
            }
            if ((token and 1) != 0) {
                payloadRef.offset = 0
                payloadRef.length = postingInput!!.readVInt()
                if (payloadRef.length > payloadRef.bytes.size) {
                    payloadRef.bytes = ByteArray(ArrayUtil.oversize(payloadRef.length, 1))
                }
                postingInput!!.readBytes(payloadRef.bytes, 0, payloadRef.length)
            } else {
                payloadRef.length = 0
            }
            return pos
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return startOffset
        }

        val wrapped: PostingsEnum?
            /** Returns the wrapped [PostingsEnum].  */
            get() = `in`

        override fun cost(): Long {
            return `in`!!.cost()
        }
    }
}
