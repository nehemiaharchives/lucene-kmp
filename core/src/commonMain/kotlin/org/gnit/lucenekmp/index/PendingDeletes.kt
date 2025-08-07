package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.IOUtils

/** This class handles accounting and applying pending deletes for live segment readers  */
open class PendingDeletes(
    val info: SegmentCommitInfo,
    liveDocs: Bits? = null,
    liveDocsInitialized: Boolean = !info.hasDeletions()
) {

    // Read-only live docs, null until live docs are initialized or if all docs are alive
    var liveDocs: Bits?
        /** Returns a snapshot of the current live docs.  */
        get(): Bits? {
            // Prevent modifications to the returned live docs
            writeableLiveDocs = null
            return liveDocs
        }

    // Writeable live docs, null if this instance is not ready to accept writes, in which
    // case getMutableBits needs to be called
    private var writeableLiveDocs: FixedBitSet? = null
    protected var pendingDeleteCount: Int
    var liveDocsInitialized: Boolean

    constructor(reader: SegmentReader, info: SegmentCommitInfo) : this(
        info,
        reader.liveDocs,
        true
    ) {
        pendingDeleteCount = reader.numDeletedDocs() - info.delCount
    }

    init {
        this.liveDocs = liveDocs
        pendingDeleteCount = 0
        this.liveDocsInitialized = liveDocsInitialized
    }

    protected val mutableBits: FixedBitSet
        get() {
            // if we pull mutable bits but we haven't been initialized something is completely off.
            // this means we receive deletes without having the bitset that is on-disk ready to be cloned
            assert(liveDocsInitialized) { "can't delete if liveDocs are not initialized" }
            if (writeableLiveDocs == null) {
                // Copy on write: this means we've cloned a
                // SegmentReader sharing the current liveDocs
                // instance; must now make a private clone so we can
                // change it:
                if (liveDocs != null) {
                    writeableLiveDocs = FixedBitSet.copyOf(liveDocs!!)
                } else {
                    writeableLiveDocs = FixedBitSet(info.info.maxDoc())
                    writeableLiveDocs!!.set(0, info.info.maxDoc())
                }
                liveDocs = writeableLiveDocs!!.asReadOnlyBits()
            }
            return writeableLiveDocs!!
        }

    /**
     * Marks a document as deleted in this segment and return true if a document got actually deleted
     * or if the document was already deleted.
     */
    @Throws(IOException::class)
    open fun delete(docID: Int): Boolean {
        assert(info.info.maxDoc() > 0)
        val mutableBits: FixedBitSet = checkNotNull(this.mutableBits)
        assert(
            docID >= 0 && docID < mutableBits.length()
        ) {
            ("out of bounds: docid="
                    + docID
                    + " liveDocsLength="
                    + mutableBits.length()
                    + " seg="
                    + info.info.name
                    + " maxDoc="
                    + info.info.maxDoc())
        }
        val didDelete: Boolean = mutableBits.getAndClear(docID)
        if (didDelete) {
            pendingDeleteCount++
        }
        return didDelete
    }

    open val hardLiveDocs: Bits?
        /** Returns a snapshot of the hard live docs.  */
        get() = liveDocs

    /** Returns the number of pending deletes that are not written to disk.  */
    open fun numPendingDeletes(): Int {
        return pendingDeleteCount
    }

    /**
     * Called once a new reader is opened for this segment ie. when deletes or updates are applied.
     */
    @Throws(IOException::class)
    open fun onNewReader(reader: CodecReader, info: SegmentCommitInfo) {
        if (!liveDocsInitialized) {
            assert(writeableLiveDocs == null)
            if (reader.hasDeletions()) {
                // we only initialize this once either in the ctor or here
                // if we use the live docs from a reader it has to be in a situation where we don't
                // have any existing live docs
                assert(pendingDeleteCount == 0) { "pendingDeleteCount: $pendingDeleteCount" }
                liveDocs = reader.liveDocs
                assert(
                    liveDocs == null
                            || assertCheckLiveDocs(liveDocs!!, info.info.maxDoc(), info.delCount)
                )
            }
            liveDocsInitialized = true
        }
    }

    private fun assertCheckLiveDocs(
        bits: Bits,
        expectedLength: Int,
        expectedDeleteCount: Int
    ): Boolean {
        assert(bits.length() == expectedLength)
        var deletedCount = 0
        for (i in 0..<bits.length()) {
            if (!bits.get(i)) {
                deletedCount++
            }
        }
        assert(
            deletedCount == expectedDeleteCount
        ) { "deleted: $deletedCount != expected: $expectedDeleteCount" }
        return true
    }

    /** Resets the pending docs  */
    open fun dropChanges() {
        pendingDeleteCount = 0
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("PendingDeletes(seg=").append(info)
        sb.append(" numPendingDeletes=").append(pendingDeleteCount)
        sb.append(" writeable=").append(writeableLiveDocs != null)
        return sb.toString()
    }

    /** Writes the live docs to disk and returns `true` if any new docs were written.  */
    @Throws(IOException::class)
    open fun writeLiveDocs(dir: Directory): Boolean {
        if (pendingDeleteCount == 0) {
            return false
        }

        val liveDocs: Bits = checkNotNull(this.liveDocs)
        // We have new deletes
        assert(liveDocs.length() == info.info.maxDoc())

        // Do this so we can delete any created files on
        // exception; this saves all codecs from having to do
        // it:
        val trackingDir = TrackingDirectoryWrapper(dir)

        // We can write directly to the actual name (vs to a
        // .tmp & renaming it) because the file is not live
        // until segments file is written:
        var success = false
        try {
            val codec: Codec = info.info.codec
            codec
                .liveDocsFormat()
                .writeLiveDocs(
                    liveDocs,
                    trackingDir,
                    info,
                    pendingDeleteCount,
                    IOContext.DEFAULT
                )
            success = true
        } finally {
            if (!success) {
                // Advance only the nextWriteDelGen so that a 2nd
                // attempt to write will write to a new file
                info.advanceNextWriteDelGen()

                // Delete any partially created file(s):
                for (fileName in trackingDir.createdFiles) {
                    IOUtils.deleteFilesIgnoringExceptions(dir, fileName)
                }
            }
        }

        // If we hit an exc in the line above (eg disk full)
        // then info's delGen remains pointing to the previous
        // (successfully written) del docs:
        info.advanceDelGen()
        info.delCount += pendingDeleteCount
        dropChanges()
        return true
    }

    /**
     * Returns `true` iff the segment represented by this [PendingDeletes] is fully
     * deleted
     */
    @Throws(IOException::class)
    open fun isFullyDeleted(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
        return this.delCount == info.info.maxDoc()
    }

    /**
     * Called for every field update for the given field at flush time
     *
     * @param info the field info of the field that's updated
     * @param iterator the values to apply
     */
    @Throws(IOException::class)
    open fun onDocValuesUpdate(
        info: FieldInfo,
        iterator: DocValuesFieldUpdates.Iterator
    ) {
    }

    @Throws(IOException::class)
    open fun numDeletesToMerge(
        policy: MergePolicy,
        readerIOSupplier: IOSupplier<CodecReader>
    ): Int {
        return policy.numDeletesToMerge(info, this.delCount, readerIOSupplier)
    }

    /** Returns true if the given reader needs to be refreshed in order to see the latest deletes  */
    fun needsRefresh(reader: CodecReader): Boolean {
        return reader.liveDocs !== liveDocs || reader.numDeletedDocs() != this.delCount
    }

    val delCount: Int
        /** Returns the number of deleted docs in the segment.  */
        get() {
            val delCount: Int = info.delCount + info.getSoftDelCount() + numPendingDeletes()
            return delCount
        }

    /** Returns the number of live documents in this segment  */
    fun numDocs(): Int {
        return info.info.maxDoc() - this.delCount
    }

    // Call only from assert!
    fun verifyDocCounts(reader: CodecReader): Boolean {
        var count = 0
        val liveDocs: Bits? = liveDocs
        if (liveDocs != null) {
            for (docID in 0..<info.info.maxDoc()) {
                if (liveDocs.get(docID)) {
                    count++
                }
            }
        } else {
            count = info.info.maxDoc()
        }
        assert(
            numDocs() == count
        ) {
            ("info.maxDoc="
                    + info.info.maxDoc()
                    + " info.delCount="
                    + info.delCount
                    + " info.getSoftDelCount()="
                    + info.getSoftDelCount()
                    + " pendingDeletes="
                    + toString()
                    + " count="
                    + count
                    + " numDocs: "
                    + numDocs())
        }
        assert(
            reader.numDocs() == numDocs()
        ) { "reader.numDocs() = " + reader.numDocs() + " numDocs() " + numDocs() }
        assert(
            reader.numDeletedDocs() <= info.info.maxDoc()
        ) {
            ("delCount="
                    + reader.numDeletedDocs()
                    + " info.maxDoc="
                    + info.info.maxDoc()
                    + " rld.pendingDeleteCount="
                    + numPendingDeletes()
                    + " info.delCount="
                    + info.delCount)
        }
        return true
    }

    /**
     * Returns `true` if we have to initialize this PendingDeletes before [.delete];
     * otherwise this PendingDeletes is ready to accept deletes. A PendingDeletes can be initialized
     * by providing it a reader via [.onNewReader].
     */
    open fun mustInitOnDelete(): Boolean {
        return false
    }
}
