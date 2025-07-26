package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsFormat
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IntBlockPool

internal class SortingTermVectorsConsumer(
    intBlockAllocator: IntBlockPool.Allocator,
    byteBlockAllocator: ByteBlockPool.Allocator,
    directory: Directory,
    info: SegmentInfo,
    codec: Codec
) : TermVectorsConsumer(intBlockAllocator, byteBlockAllocator, directory, info, codec) {
    var tmpDirectory: TrackingTmpOutputDirectoryWrapper? = null

    @Throws(IOException::class)
    override fun flush(
        fieldsToFlush: MutableMap<String, TermsHashPerField>,
        state: SegmentWriteState,
        sortMap: Sorter.DocMap,
        norms: NormsProducer
    ) {
        super.flush(fieldsToFlush, state, sortMap, norms)
        if (tmpDirectory != null) {
            val reader: TermVectorsReader =
                TEMP_TERM_VECTORS_FORMAT.vectorsReader(
                    tmpDirectory!!, state.segmentInfo, state.fieldInfos, IOContext.DEFAULT
                )
            // Don't pull a merge instance, since merge instances optimize for
            // sequential access while term vectors will likely be accessed in random
            // order here.
            val writer: TermVectorsWriter =
                codec
                    .termVectorsFormat()
                    .vectorsWriter(state.directory, state.segmentInfo, state.context)
            try {
                reader.checkIntegrity()
                for (docID in 0..<state.segmentInfo.maxDoc()) {
                    val vectors: Fields? =
                        reader.get(if (sortMap == null) docID else sortMap.newToOld(docID))
                    writeTermVectors(writer, vectors, state.fieldInfos)
                }
                writer.finish(state.segmentInfo.maxDoc())
            } finally {
                IOUtils.close(reader, writer)
                IOUtils.deleteFiles(tmpDirectory!!, tmpDirectory!!.getTemporaryFiles().values)
            }
        }
    }

    @Throws(IOException::class)
    override fun initTermVectorsWriter() {
        if (writer == null) {
            val context: IOContext =
                IOContext(FlushInfo(lastDocID, bytesUsed.get()))
            tmpDirectory = TrackingTmpOutputDirectoryWrapper(directory)
            writer = TEMP_TERM_VECTORS_FORMAT.vectorsWriter(tmpDirectory!!, info, context)
            lastDocID = 0
        }
    }

    override fun abort() {
        try {
            super.abort()
        } finally {
            if (tmpDirectory != null) {
                IOUtils.deleteFilesIgnoringExceptions(
                    tmpDirectory!!, tmpDirectory!!.getTemporaryFiles().values
                )
            }
        }
    }

    companion object {
        private val TEMP_TERM_VECTORS_FORMAT: TermVectorsFormat =
            Lucene90CompressingTermVectorsFormat(
                "TempTermVectors", "", SortingStoredFieldsConsumer.NO_COMPRESSION, 8 * 1024, 128, 10
            )

        /**
         * Safe (but, slowish) default method to copy every vector field in the provided [ ].
         */
        @Throws(IOException::class)
        private fun writeTermVectors(
            writer: TermVectorsWriter,
            vectors: Fields?,
            fieldInfos: FieldInfos?
        ) {
            if (vectors == null) {
                writer.startDocument(0)
                writer.finishDocument()
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
            writer.startDocument(numFields)

            var lastFieldName: String? = null

            var termsEnum: TermsEnum? = null
            var docsAndPositionsEnum: PostingsEnum? = null

            var fieldCount = 0
            for (fieldName in vectors) {
                fieldCount++
                val fieldInfo: FieldInfo? = fieldInfos!!.fieldInfo(fieldName)

                assert(
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
                assert(!hasPayloads || hasPositions)

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

                writer.startField(fieldInfo, numTerms, hasPositions, hasOffsets, hasPayloads)
                termsEnum = terms.iterator()

                var termCount = 0
                while (termsEnum.next() != null) {
                    termCount++

                    val freq = termsEnum.totalTermFreq().toInt()

                    writer.startTerm(termsEnum.term(), freq)

                    if (hasPositions || hasOffsets) {
                        docsAndPositionsEnum =
                            termsEnum.postings(
                                docsAndPositionsEnum,
                                PostingsEnum.OFFSETS.toInt() or PostingsEnum.PAYLOADS.toInt()
                            )
                        checkNotNull(docsAndPositionsEnum)

                        val docID: Int = docsAndPositionsEnum.nextDoc()
                        assert(docID != DocIdSetIterator.NO_MORE_DOCS)
                        assert(docsAndPositionsEnum.freq() == freq)

                        for (posUpto in 0..<freq) {
                            val pos: Int = docsAndPositionsEnum.nextPosition()
                            val startOffset: Int = docsAndPositionsEnum.startOffset()
                            val endOffset: Int = docsAndPositionsEnum.endOffset()

                            val payload: BytesRef? = docsAndPositionsEnum.payload

                            assert(!hasPositions || pos >= 0)
                            writer.addPosition(pos, startOffset, endOffset, payload)
                        }
                    }
                    writer.finishTerm()
                }
                assert(termCount == numTerms)
                writer.finishField()
            }
            assert(fieldCount == numFields)
            writer.finishDocument()
        }
    }
}
