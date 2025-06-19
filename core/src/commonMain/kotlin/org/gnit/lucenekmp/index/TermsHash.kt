package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.IntBlockPool

/**
 * This class is passed each token produced by the analyzer on each field during indexing, and it
 * stores these tokens in a hash table, and allocates separate byte streams per token. Consumers of
 * this class, eg [FreqProxTermsWriter] and [TermVectorsConsumer], write their own byte
 * streams under each term.
 */
abstract class TermsHash(
    intBlockAllocator: IntBlockPool.Allocator,
    byteBlockAllocator: ByteBlockPool.Allocator,
    val bytesUsed: Counter,
    val nextTermsHash: TermsHash?
) {
    val intPool: IntBlockPool = IntBlockPool(intBlockAllocator)
    val bytePool: ByteBlockPool = ByteBlockPool(byteBlockAllocator)
    var termBytePool: ByteBlockPool? = null

    init {

        if (nextTermsHash != null) {
            // We are primary
            termBytePool = bytePool
            nextTermsHash.termBytePool = bytePool
        }
    }

    open fun abort() {
        try {
            reset()
        } finally {
            nextTermsHash?.abort()
        }
    }

    // Clear all state
    fun reset() {
        // we don't reuse so we drop everything and don't fill with 0
        intPool.reset(zeroFillBuffers = false, reuseFirst = false)
        bytePool.reset(zeroFillBuffers = false, reuseFirst = false)
    }

    @Throws(IOException::class)
    open fun flush(
        fieldsToFlush: MutableMap<String, TermsHashPerField>,
        state: SegmentWriteState,
        sortMap: Sorter.DocMap,
        norms: NormsProducer
    ) {
        if (nextTermsHash != null) {
            val nextChildFields: MutableMap<String, TermsHashPerField> = HashMap()
            for (entry in fieldsToFlush.entries) {
                nextChildFields.put(entry.key, entry.value.nextPerField!!)
            }
            nextTermsHash.flush(nextChildFields, state, sortMap, norms)
        }
    }

    abstract fun addField(
        fieldInvertState: FieldInvertState,
        fieldInfo: FieldInfo
    ): TermsHashPerField

    @Throws(IOException::class)
    open fun finishDocument(docID: Int) {
        nextTermsHash?.finishDocument(docID)
    }

    @Throws(IOException::class)
    open fun startDocument() {
        nextTermsHash?.startDocument()
    }
}
