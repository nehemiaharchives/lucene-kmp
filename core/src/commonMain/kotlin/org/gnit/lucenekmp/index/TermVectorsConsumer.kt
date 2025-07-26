package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IntBlockPool
import org.gnit.lucenekmp.util.RamUsageEstimator
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert

open class TermVectorsConsumer(
    intBlockAllocator: IntBlockPool.Allocator,
    byteBlockAllocator: ByteBlockPool.Allocator,
    protected val directory: Directory,
    protected val info: SegmentInfo,
    protected val codec: Codec
) : TermsHash(
    intBlockAllocator,
    byteBlockAllocator,
    Counter.newCounter(),
    null
) {
    var writer: TermVectorsWriter? = null

    /** Scratch term used by TermVectorsConsumerPerField.finishDocument.  */
    val flushTerm: BytesRef = BytesRef()

    /** Used by TermVectorsConsumerPerField when serializing the term vectors.  */
    val vectorSliceReaderPos: ByteSliceReader = ByteSliceReader()

    val vectorSliceReaderOff: ByteSliceReader = ByteSliceReader()

    private var hasVectors = false
    private var numVectorFields = 0
    var lastDocID: Int = 0
    private var perFields: Array<TermVectorsConsumerPerField?> = kotlin.arrayOfNulls(1)

    // this accountable either holds the writer or one that returns null.
    // it's cleaner than checking if the writer is null all over the place
    var accountable: Accountable = Accountable.NULL_ACCOUNTABLE

    @Throws(IOException::class)
    override fun flush(
        fieldsToFlush: MutableMap<String, TermsHashPerField>,
        state: SegmentWriteState,
        sortMap: Sorter.DocMap,
        norms: NormsProducer
    ) {
        if (writer != null) {
            val numDocs: Int = state.segmentInfo.maxDoc()
            assert(numDocs > 0)
            // At least one doc in this run had term vectors enabled
            try {
                fill(numDocs)
                checkNotNull(state.segmentInfo)
                writer!!.finish(numDocs)
            } finally {
                IOUtils.close(writer)
            }
        }
    }

    /**
     * Fills in no-term-vectors for all docs we haven't seen since the last doc that had term vectors.
     */
    @Throws(IOException::class)
    fun fill(docID: Int) {
        while (lastDocID < docID) {
            writer!!.startDocument(0)
            writer!!.finishDocument()
            lastDocID++
        }
    }

    @Throws(IOException::class)
    open fun initTermVectorsWriter() {
        if (writer == null) {
            val context = IOContext(FlushInfo(lastDocID, bytesUsed.get()))
            writer = codec.termVectorsFormat().vectorsWriter(directory, info, context)
            lastDocID = 0
            accountable = writer!!
        }
    }

    fun setHasVectors() {
        hasVectors = true
    }

    @Throws(IOException::class)
    override fun finishDocument(docID: Int) {
        if (!hasVectors) {
            return
        }

        // Fields in term vectors are UTF16 sorted:
        ArrayUtil.introSort(
            perFields as Array<TermVectorsConsumerPerField>,
            0,
            numVectorFields
        )

        initTermVectorsWriter()

        fill(docID)

        // Append term vectors to the real outputs:
        writer!!.startDocument(numVectorFields)
        for (i in 0..<numVectorFields) {
            perFields[i]!!.finishDocument()
        }
        writer!!.finishDocument()

        assert(lastDocID == docID) { "lastDocID=$lastDocID docID=$docID" }

        lastDocID++

        super.reset()
        resetFields()
    }

    override fun abort() {
        try {
            super.abort()
        } finally {
            IOUtils.closeWhileHandlingException(writer)
            reset()
        }
    }

    fun resetFields() {
        Arrays.fill(perFields, null) // don't hang onto stuff from previous doc
        numVectorFields = 0
    }

    override fun addField(
        invertState: FieldInvertState,
        fieldInfo: FieldInfo
    ): TermsHashPerField {
        return TermVectorsConsumerPerField(invertState, this, fieldInfo)
    }

    fun addFieldToFlush(fieldToFlush: TermVectorsConsumerPerField?) {
        if (numVectorFields == perFields.size) {
            val newSize: Int = ArrayUtil.oversize(
                numVectorFields + 1,
                RamUsageEstimator.NUM_BYTES_OBJECT_REF
            )
            val newArray: Array<TermVectorsConsumerPerField?> =
                kotlin.arrayOfNulls(newSize)
            System.arraycopy(perFields, 0, newArray, 0, numVectorFields)
            perFields = newArray
        }

        perFields[numVectorFields++] = fieldToFlush
    }

    override fun startDocument() {
        resetFields()
        numVectorFields = 0
    }
}
