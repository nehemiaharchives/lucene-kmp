package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.FieldInfosFormat
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.IOUtils

internal class PendingSoftDeletes : PendingDeletes {
    private val field: String
    private var dvGeneration: Long = -2
    private val hardDeletes: PendingDeletes

    constructor(field: String, info: SegmentCommitInfo) : super(
        info,
        null,
        info.getDelCount(true) == 0
    ) {
        this.field = field
        hardDeletes = PendingDeletes(info)
    }

    constructor(
        field: String,
        reader: SegmentReader,
        info: SegmentCommitInfo
    ) : super(reader, info) {
        this.field = field
        hardDeletes = PendingDeletes(reader, info)
    }

    @Throws(IOException::class)
    override fun delete(docID: Int): Boolean {
        // we need to fetch this first it might be a shared instance with
        val mutableBits: FixedBitSet = mutableBits
        // hardDeletes
        if (hardDeletes.delete(docID)) {
            if (mutableBits.getAndClear(docID)) { // delete it here too!
                assert(!hardDeletes.delete(docID))
            } else {
                // if it was deleted subtract the delCount
                pendingDeleteCount--
                assert(assertPendingDeletes())
            }
            return true
        }
        return false
    }

    override fun numPendingDeletes(): Int {
        return super.numPendingDeletes() + hardDeletes.numPendingDeletes()
    }

    @Throws(IOException::class)
    override fun onNewReader(
        reader: CodecReader,
        info: SegmentCommitInfo
    ) {
        super.onNewReader(reader, info)
        hardDeletes.onNewReader(reader, info)
        // only re-calculate this if we haven't seen this generation
        if (dvGeneration < info.docValuesGen) {
            val newDelCount: Int
            var iterator: DocIdSetIterator? =
                FieldExistsQuery.getDocValuesDocIdSetIterator(field, reader)
            if (iterator != null && iterator.nextDoc() != NO_MORE_DOCS) {
                iterator = FieldExistsQuery.getDocValuesDocIdSetIterator(field, reader)
                newDelCount = applySoftDeletes(iterator!!, mutableBits)
                assert(newDelCount >= 0) { " illegal pending delete count: $newDelCount" }
            } else {
                // nothing is deleted we don't have a soft deletes field in this segment
                newDelCount = 0
            }
            assert(
                info.getSoftDelCount() == newDelCount
            ) { "softDeleteCount doesn't match " + info.getSoftDelCount() + " != " + newDelCount }
            dvGeneration = info.docValuesGen
        }
        assert(delCount <= info.info.maxDoc()) { delCount.toString() + " > " + info.info.maxDoc() }
    }

    @Throws(IOException::class)
    override fun writeLiveDocs(dir: Directory): Boolean {
        // we need to set this here to make sure our stats in SCI are up-to-date otherwise we might hit
        // an assertion
        // when the hard deletes are set since we need to account for docs that used to be only
        // soft-delete but now hard-deleted
        this.info.setSoftDelCount(this.info.getSoftDelCount() + pendingDeleteCount)
        super.dropChanges()
        // delegate the write to the hard deletes - it will only write if somebody used it.
        if (hardDeletes.writeLiveDocs(dir)) {
            return true
        }
        return false
    }

    override fun dropChanges() {
        // don't reset anything here - this is called after a merge (successful or not) to prevent
        // rewriting the deleted docs to disk. we only pass it on and reset the number of pending
        // deletes
        hardDeletes.dropChanges()
    }

    @Throws(IOException::class)
    override fun onDocValuesUpdate(
        info: FieldInfo,
        iterator: DocValuesFieldUpdates.Iterator
    ) {
        if (this.field == info.name) {
            pendingDeleteCount += applySoftDeletes(iterator, mutableBits)
            assert(assertPendingDeletes())
            this.info.setSoftDelCount(this.info.getSoftDelCount() + pendingDeleteCount)
            super.dropChanges()
        }
        assert(
            dvGeneration < info.docValuesGen
        ) {
            ("we have seen this generation update already: "
                    + dvGeneration
                    + " vs. "
                    + info.docValuesGen)
        }
        assert(dvGeneration != -2L) { "docValues generation is still uninitialized" }
        dvGeneration = info.docValuesGen
    }

    private fun assertPendingDeletes(): Boolean {
        assert(
            pendingDeleteCount + info.getSoftDelCount() >= 0
        ) { "illegal pending delete count: " + (pendingDeleteCount + info.getSoftDelCount()) }
        assert(info.info.maxDoc() >= delCount)
        return true
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("PendingSoftDeletes(seg=").append(info)
        sb.append(" numPendingDeletes=").append(pendingDeleteCount)
        sb.append(" field=").append(field)
        sb.append(" dvGeneration=").append(dvGeneration)
        sb.append(" hardDeletes=").append(hardDeletes)
        return sb.toString()
    }

    @Throws(IOException::class)
    override fun numDeletesToMerge(
        policy: MergePolicy,
        readerIOSupplier: IOSupplier<CodecReader>
    ): Int {
        ensureInitialized(readerIOSupplier) // initialize to ensure we have accurate counts
        return super.numDeletesToMerge(policy, readerIOSupplier)
    }

    @Throws(IOException::class)
    private fun ensureInitialized(readerIOSupplier: IOSupplier<CodecReader>) {
        if (dvGeneration == -2L) {
            val fieldInfos: FieldInfos = readFieldInfos()
            val fieldInfo: FieldInfo? = fieldInfos.fieldInfo(field)
            // we try to only open a reader if it's really necessary i.e. indices that are mainly append
            // only might have
            // big segments that don't even have any docs in the soft deletes field. In such a case it's
            // simply
            // enough to look at the FieldInfo for the field and check if the field has DocValues
            if (fieldInfo != null && fieldInfo.docValuesType != DocValuesType.NONE) {
                // in order to get accurate numbers we need to have at least one reader see here.
                onNewReader(readerIOSupplier.get(), info)
            } else {
                // we are safe here since we don't have any doc values for the soft-delete field on disk
                // no need to open a new reader
                dvGeneration = fieldInfo?.docValuesGen ?: -1
            }
        }
    }

    @Throws(IOException::class)
    override fun isFullyDeleted(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
        ensureInitialized(
            readerIOSupplier
        ) // initialize to ensure we have accurate counts - only needed in the
        // soft-delete case
        return super.isFullyDeleted(readerIOSupplier)
    }

    @Throws(IOException::class)
    private fun readFieldInfos(): FieldInfos {
        val segInfo: SegmentInfo = info.info
        var dir: Directory = segInfo.dir
        if (!info.hasFieldUpdates()) {
            // updates always outside of CFS
            val toClose: AutoCloseable?
            if (segInfo.useCompoundFile) {
                dir = segInfo.codec.compoundFormat().getCompoundReader(segInfo.dir, segInfo)
                toClose = dir
            } else {
                toClose = null
                dir = segInfo.dir
            }
            try {
                return segInfo.codec.fieldInfosFormat()
                    .read(dir, segInfo, "", IOContext.READONCE)
            } finally {
                IOUtils.close(toClose)
            }
        } else {
            val fisFormat: FieldInfosFormat = segInfo.codec.fieldInfosFormat()
            val segmentSuffix = info.fieldInfosGen.toString(Character.MAX_RADIX.coerceIn(2, 36))
            return fisFormat.read(dir, segInfo, segmentSuffix, IOContext.READONCE)
        }
    }

    override val hardLiveDocs: Bits?
        get() = hardDeletes.liveDocs

    override fun mustInitOnDelete(): Boolean {
        return !liveDocsInitialized
    }

    companion object {
        /**
         * Clears all bits in the given bitset that are set and are also in the given DocIdSetIterator.
         *
         * @param iterator the doc ID set iterator for apply
         * @param bits the bit set to apply the deletes to
         * @return the number of bits changed by this function
         */
        @Throws(IOException::class)
        fun applySoftDeletes(
            iterator: DocIdSetIterator,
            bits: FixedBitSet
        ): Int {
            checkNotNull(iterator)
            var newDeletes = 0
            var docID: Int
            val hasValue: DocValuesFieldUpdates.Iterator? = iterator as? DocValuesFieldUpdates.Iterator
            while ((iterator.nextDoc().also { docID = it }) != NO_MORE_DOCS) {
                if (hasValue == null || hasValue.hasValue()) {
                    if (bits.getAndClear(docID)) { // doc is live - clear it
                        newDeletes++
                        // now that we know we deleted it and we fully control the hard deletes we can do correct
                        // accounting
                        // below.
                    }
                } else {
                    if (!bits.getAndSet(docID)) {
                        newDeletes--
                    }
                }
            }
            return newDeletes
        }

        @Throws(IOException::class)
        fun countSoftDeletes(
            softDeletedDocs: DocIdSetIterator?,
            hardDeletes: Bits?
        ): Int {
            var count = 0
            if (softDeletedDocs != null) {
                var doc: Int
                while ((softDeletedDocs.nextDoc()
                        .also { doc = it }) != NO_MORE_DOCS
                ) {
                    if (hardDeletes == null || hardDeletes.get(doc)) {
                        count++
                    }
                }
            }
            return count
        }
    }
}
