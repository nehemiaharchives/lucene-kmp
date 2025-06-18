package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefHash
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.IntBlockPool
import kotlin.math.min

/**
 * This class stores streams of information per term without knowing the size of the stream ahead of
 * time. Each stream typically encodes one level of information like term frequency per document or
 * term proximity. Internally this class allocates a linked list of slices that can be read by a
 * [ByteSliceReader] for each term. Terms are first deduplicated in a [BytesRefHash]
 * once this is done internal data-structures point to the current offset of each stream that can be
 * written to.
 */
abstract class TermsHashPerField(
    private val streamCount: Int,
    private val intPool: IntBlockPool,
    val bytePool: ByteBlockPool,
    termBytePool: ByteBlockPool,
    bytesUsed: Counter,
    val nextPerField: TermsHashPerField?,
    val fieldName: String,
    indexOptions: IndexOptions
) : Comparable<TermsHashPerField> {
    private val slicePool: ByteSlicePool = ByteSlicePool(bytePool)

    // for each term we store an integer per stream that points into the bytePool above
    // the address is updated once data is written to the stream to point to the next free offset
    // in the terms stream. The start address for the stream is stored in
    // postingsArray.byteStarts[termId]
    // This is initialized in the #addTerm method, either to a brand new per term stream if the term
    // is new or
    // to the addresses where the term stream was written to when we saw it the last time.
    private lateinit var termStreamAddressBuffer: IntArray
    private var streamAddressOffset = 0
    val indexOptions: IndexOptions

    /* This stores the actual term bytes for postings and offsets into the parent hash in the case that this
   * TermsHashPerField is hashing term vectors.*/
    private val bytesHash: BytesRefHash

    var postingsArray: ParallelPostingsArray? = null
    private var lastDocID = 0 // only with assert

    fun reset() {
        bytesHash.clear(false)
        sortedTermIDs = null
        nextPerField?.reset()
    }

    fun initReader(reader: ByteSliceReader, termID: Int, stream: Int) {
        assert(stream < streamCount)
        val streamStartOffset: Int = postingsArray!!.addressOffset[termID]
        val streamAddressBuffer: IntArray =
            intPool.buffers[streamStartOffset shr IntBlockPool.INT_BLOCK_SHIFT]
        val offsetInAddressBuffer = streamStartOffset and IntBlockPool.INT_BLOCK_MASK
        reader.init(
            bytePool,
            postingsArray!!.byteStarts[termID] + stream * ByteSlicePool.FIRST_LEVEL_SIZE,
            streamAddressBuffer[offsetInAddressBuffer + stream]
        )
    }

    private var sortedTermIDs: IntArray? = null

    /**
     * Collapse the hash table and sort in-place; also sets this.sortedTermIDs to the results This
     * method must not be called twice unless [.reset] or [.reinitHash] was called.
     */
    fun sortTerms() {
        assert(sortedTermIDs == null)
        sortedTermIDs = bytesHash.sort()
    }

    /** Returns the sorted term IDs. [.sortTerms] must be called before  */
    fun getSortedTermIDs(): IntArray {
        checkNotNull(sortedTermIDs)
        return sortedTermIDs!!
    }

    fun reinitHash() {
        sortedTermIDs = null
        bytesHash.reinit()
    }

    private var doNextCall = false

    /**
     * streamCount: how many streams this field stores per term. E.g. doc(+freq) is 1 stream,
     * prox+offset is a second.
     */
    init {
        assert(indexOptions != IndexOptions.NONE)
        this.indexOptions = indexOptions
        val byteStarts = PostingsBytesStartArray(this, bytesUsed)
        bytesHash = BytesRefHash(termBytePool, HASH_INIT_SIZE, byteStarts)
    }

    // Secondary entry point (for 2nd & subsequent TermsHash),
    // because token text has already been "interned" into
    // textStart, so we hash by textStart.  term vectors use
    // this API.
    @Throws(IOException::class)
    private fun add(textStart: Int, docID: Int) {
        val termID: Int = bytesHash.addByPoolOffset(textStart)
        if (termID >= 0) { // New posting
            // First time we are seeing this token since we last
            // flushed the hash.
            initStreamSlices(termID, docID)
        } else {
            positionStreamSlice(termID, docID)
        }
    }

    /**
     * Called when we first encounter a new term. We must allocate slies to store the postings (vInt
     * compressed doc/freq/prox), and also the int pointers to where (in our [ByteBlockPool]
     * storage) the postings for this term begin.
     */
    @Throws(IOException::class)
    private fun initStreamSlices(termID: Int, docID: Int) {
        // Init stream slices
        if (streamCount + intPool.intUpto > IntBlockPool.INT_BLOCK_SIZE) {
            // not enough space remaining in this buffer -- jump to next buffer and lose this remaining
            // piece
            intPool.nextBuffer()
        }

        if (ByteBlockPool.BYTE_BLOCK_SIZE - bytePool.byteUpto
            < (2 * streamCount) * ByteSlicePool.FIRST_LEVEL_SIZE
        ) {
            // can we fit at least one byte per stream in the current buffer, if not allocate a new one
            bytePool.nextBuffer()
        }

        termStreamAddressBuffer = intPool.buffer
        streamAddressOffset = intPool.intUpto
        intPool.intUpto += streamCount // advance the pool to reserve the N streams for this term

        postingsArray!!.addressOffset[termID] = streamAddressOffset + intPool.intOffset

        for (i in 0..<streamCount) {
            // initialize each stream with a slice we start with ByteBlockPool.FIRST_LEVEL_SIZE)
            // and grow as we need more space. see ByteBlockPool.LEVEL_SIZE_ARRAY
            val upto: Int = slicePool.newSlice(ByteSlicePool.FIRST_LEVEL_SIZE)
            termStreamAddressBuffer[streamAddressOffset + i] = upto + bytePool.byteOffset
        }
        postingsArray!!.byteStarts[termID] = termStreamAddressBuffer[streamAddressOffset]
        newTerm(termID, docID)
    }

    private fun assertDocId(docId: Int): Boolean {
        assert(docId >= lastDocID) { "docID must be >= $lastDocID but was: $docId" }
        lastDocID = docId
        return true
    }

    /**
     * Called once per inverted token. This is the primary entry point (for first TermsHash); postings
     * use this API.
     */
    @Throws(IOException::class)
    fun add(termBytes: BytesRef, docID: Int) {
        assert(assertDocId(docID))
        // We are first in the chain so we must "intern" the
        // term text into textStart address
        // Get the text & hash of this term.
        var termID: Int = bytesHash.add(termBytes)
        // System.out.println("add term=" + termBytesRef.utf8ToString() + " doc=" + docState.docID + "
        // termID=" + termID);
        if (termID >= 0) { // New posting
            // Init stream slices
            initStreamSlices(termID, docID)
        } else {
            termID = positionStreamSlice(termID, docID)
        }
        if (doNextCall) {
            nextPerField!!.add(postingsArray!!.textStarts[termID], docID)
        }
    }

    @Throws(IOException::class)
    private fun positionStreamSlice(termID: Int, docID: Int): Int {
        var termID = termID
        termID = (-termID) - 1
        val intStart: Int = postingsArray!!.addressOffset[termID]
        termStreamAddressBuffer = intPool.buffers[intStart shr IntBlockPool.INT_BLOCK_SHIFT]
        streamAddressOffset = intStart and IntBlockPool.INT_BLOCK_MASK
        addTerm(termID, docID)
        return termID
    }

    fun writeByte(stream: Int, b: Byte) {
        val streamAddress = streamAddressOffset + stream
        val upto = termStreamAddressBuffer[streamAddress]
        var bytes: ByteArray =
            checkNotNull(bytePool.getBuffer(upto shr ByteBlockPool.BYTE_BLOCK_SHIFT))
        var offset = upto and ByteBlockPool.BYTE_BLOCK_MASK
        if (bytes[offset].toInt() != 0) {
            // End of slice; allocate a new one
            offset = slicePool.allocSlice(bytes, offset)
            bytes = bytePool.buffer!!
            termStreamAddressBuffer[streamAddress] = offset + bytePool.byteOffset
        }
        bytes[offset] = b
        termStreamAddressBuffer[streamAddress]++
    }

    fun writeBytes(stream: Int, b: ByteArray, offset: Int, len: Int) {
        var offset = offset
        val end = offset + len
        val streamAddress = streamAddressOffset + stream
        val upto = termStreamAddressBuffer[streamAddress]
        var slice: ByteArray =
            checkNotNull(bytePool.getBuffer(upto shr ByteBlockPool.BYTE_BLOCK_SHIFT))
        var sliceOffset = upto and ByteBlockPool.BYTE_BLOCK_MASK

        while (slice[sliceOffset].toInt() == 0 && offset < end) {
            slice[sliceOffset++] = b[offset++]
            termStreamAddressBuffer[streamAddress]++
        }

        while (offset < end) {
            val offsetAndLength: Int = slicePool.allocKnownSizeSlice(slice, sliceOffset)
            sliceOffset = offsetAndLength shr 8
            val sliceLength = offsetAndLength and 0xff
            slice = bytePool.buffer!!
            val writeLength = min(sliceLength - 1, end - offset)
            System.arraycopy(b, offset, slice, sliceOffset, writeLength)
            sliceOffset += writeLength
            offset += writeLength
            termStreamAddressBuffer[streamAddress] = sliceOffset + bytePool.byteOffset
        }
    }

    fun writeVInt(stream: Int, i: Int) {
        var i = i
        assert(stream < streamCount)
        while ((i and 0x7F.inv()) != 0) {
            writeByte(stream, ((i and 0x7f) or 0x80).toByte())
            i = i ushr 7
        }
        writeByte(stream, i.toByte())
    }

    private class PostingsBytesStartArray(
        private val perField: TermsHashPerField,
        private val bytesUsed: Counter
    ) : BytesRefHash.BytesStartArray() {

        override fun init(): IntArray {
            if (perField.postingsArray == null) {
                perField.postingsArray = perField.createPostingsArray(2)
                perField.newPostingsArray()
                bytesUsed.addAndGet(
                    perField.postingsArray!!.size * perField.postingsArray!!.bytesPerPosting().toLong()
                )
            }
            return perField.postingsArray!!.textStarts
        }

        override fun grow(): IntArray {
            var postingsArray: ParallelPostingsArray = perField.postingsArray!!
            val oldSize: Int = perField.postingsArray!!.size
            perField.postingsArray = postingsArray.grow()
            postingsArray = perField.postingsArray!!
            perField.newPostingsArray()
            bytesUsed.addAndGet(postingsArray.bytesPerPosting() * (postingsArray.size - oldSize).toLong())
            return postingsArray.textStarts
        }

        override fun clear(): IntArray? {
            if (perField.postingsArray != null) {
                bytesUsed.addAndGet(
                    -(perField.postingsArray!!.size * perField.postingsArray!!.bytesPerPosting()).toLong()
                )
                perField.postingsArray = null
                perField.newPostingsArray()
            }
            return null
        }

        override fun bytesUsed(): Counter {
            return bytesUsed
        }
    }

    override fun compareTo(other: TermsHashPerField): Int {
        return fieldName.compareTo(other.fieldName)
    }

    /** Finish adding all instances of this field to the current document.  */
    @Throws(IOException::class)
    open fun finish() {
        nextPerField?.finish()
    }

    val numTerms: Int
        get() = bytesHash.size()

    /**
     * Start adding a new field instance; first is true if this is the first time this field name was
     * seen in the document.
     */
    open fun start(field: IndexableField, first: Boolean): Boolean {
        if (nextPerField != null) {
            doNextCall = nextPerField.start(field, first)
        }
        return true
    }

    /** Called when a term is seen for the first time.  */
    @Throws(IOException::class)
    abstract fun newTerm(termID: Int, docID: Int)

    /** Called when a previously seen term is seen again.  */
    @Throws(IOException::class)
    abstract fun addTerm(termID: Int, docID: Int)

    /** Called when the postings array is initialized or resized.  */
    abstract fun newPostingsArray()

    /** Creates a new postings array of the specified size.  */
    abstract fun createPostingsArray(size: Int): ParallelPostingsArray

    companion object {
        private const val HASH_INIT_SIZE = 4
    }
}
