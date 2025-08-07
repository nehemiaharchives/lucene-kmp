package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.NumericDocValuesFieldUpdates.SingleValueNumericDocValuesFieldUpdates
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.RamUsageEstimator
import okio.IOException
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Holds buffered deletes and updates by term or query, once pushed. Pushed deletes/updates are
 * write-once, so we shift to more memory efficient data structure to hold them. We don't hold
 * docIDs because these are applied on flush.
 */
@OptIn(ExperimentalAtomicApi::class)
class FrozenBufferedUpdates(
    // a segment private deletes. in that case is should
    // only have Queries and doc values updates
    private val infoStream: InfoStream,
    updates: BufferedUpdates,
    // non-null iff this frozen packet represents
    val privateSegment: SegmentCommitInfo?
) {
    // Terms, in sorted order:
    val deleteTerms: PrefixCodedTerms

    // Parallel array of deleted query, and the docIDUpto for each
    val deleteQueries: Array<Query>
    val deleteQueryLimits: IntArray

    /** Counts down once all deletes/updates have been applied  */
    val applied: CountDownLatch = CountDownLatch(1)

    private val applyLock: ReentrantLock = ReentrantLock()
    private val fieldUpdates: MutableMap<String, FieldUpdatesBuffer>

    /** How many total documents were deleted/updated.  */
    var totalDelCount: Long = 0

    private val fieldUpdatesCount: Int

    val bytesUsed: Int

    private var delGen: Long = -1 // assigned by BufferedUpdatesStream once pushed

    init {
        assert(
            privateSegment == null || updates.deleteTerms.isEmpty
        ) { "segment private packet should only have del queries" }

        val builder: PrefixCodedTerms.Builder = PrefixCodedTerms.Builder()
        updates.deleteTerms.forEachOrdered<RuntimeException> { term, docId ->
            builder.add(
                term
            )
        }
        deleteTerms = builder.finish()

        deleteQueries = kotlin.arrayOfNulls<Query>(updates.deleteQueries.size) as Array<Query>
        deleteQueryLimits = IntArray(updates.deleteQueries.size)
        var upto = 0
        for (ent in updates.deleteQueries.entries) {
            deleteQueries[upto] = ent.key
            deleteQueryLimits[upto] = ent.value
            upto++
        }
        // TODO if a Term affects multiple fields, we could keep the updates key'd by Term
        // so that it maps to all fields it affects, sorted by their docUpto, and traverse
        // that Term only once, applying the update to all fields that still need to be
        // updated.
        updates.fieldUpdates.values.forEach { obj: FieldUpdatesBuffer -> obj.finish() }
        this.fieldUpdates = updates.fieldUpdates.toMutableMap()
        this.fieldUpdatesCount = updates.numFieldUpdates.load()

        bytesUsed = ((deleteTerms.ramBytesUsed() + deleteQueries.size * BYTES_PER_DEL_QUERY.toLong())
                + updates.fieldUpdatesBytesUsed.get()).toInt()

        if (infoStream != null && infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                "compressed ${updates.ramBytesUsed()} to $bytesUsed bytes (${100.0 * bytesUsed / updates.ramBytesUsed()}%) for deletes/updates; private segment $privateSegment"
            )
        }
    }

    /**
     * Tries to lock this buffered update instance
     *
     * @return true if the lock was successfully acquired. otherwise false.
     */
    fun tryLock(): Boolean {
        return applyLock.tryLock()
    }

    /** locks this buffered update instance  */
    fun lock() {
        applyLock.lock()
    }

    /** Releases the lock of this buffered update instance  */
    fun unlock() {
        applyLock.unlock()
    }

    /** Returns true iff this buffered updates instance was already applied  */
    fun isApplied(): Boolean {
        assert(applyLock.isHeldByCurrentThread())
        return applied.getCount() == 0L
    }

    /**
     * Applies pending delete-by-term, delete-by-query and doc values updates to all segments in the
     * index, returning the number of new deleted or updated documents.
     */
    suspend fun apply(segStates: Array<BufferedUpdatesStream.SegmentState>): Long {
        assert(applyLock.isHeldByCurrentThread())
        require(delGen != -1L) { "gen is not yet set; call BufferedUpdatesStream.push first" }

        assert(applied.getCount() != 0L)

        if (privateSegment != null) {
            assert(segStates.size == 1)
            assert(privateSegment === segStates[0].reader.originalSegmentInfo)
        }

        totalDelCount += applyTermDeletes(segStates)
        totalDelCount += applyQueryDeletes(segStates)
        totalDelCount += applyDocValuesUpdates(segStates)

        return totalDelCount
    }

    @Throws(IOException::class)
    private fun applyDocValuesUpdates(segStates: Array<BufferedUpdatesStream.SegmentState>): Long {
        if (fieldUpdates.isEmpty()) {
            return 0
        }

        val startNS: Long = System.nanoTime()

        var updateCount: Long = 0

        for (segState in segStates) {
            if (delGen < segState.delGen) {
                // segment is newer than this deletes packet
                continue
            }

            if (segState.rld.refCount() == 1) {
                // This means we are the only remaining reference to this segment, meaning
                // it was merged away while we were running, so we can safely skip running
                // because we will run on the newly merged segment next:
                continue
            }
            val isSegmentPrivateDeletes = privateSegment != null
            if (!fieldUpdates.isEmpty()) {
                updateCount +=
                    applyDocValuesUpdates(segState, fieldUpdates, delGen, isSegmentPrivateDeletes)
            }
        }

        if (infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                "applyDocValuesUpdates ${
                    (System.nanoTime() - startNS) / TimeUnit.MILLISECONDS.toNanos(1).toDouble()
                } msec for ${segStates.size} segments, $fieldUpdatesCount field updates; $updateCount new updates"
            )
        }

        return updateCount
    }

    // Delete by query
    private suspend fun applyQueryDeletes(segStates: Array<BufferedUpdatesStream.SegmentState>): Long {
        if (deleteQueries.isEmpty()) {
            return 0
        }

        val startNS: Long = System.nanoTime()

        var delCount: Long = 0
        for (segState in segStates) {
            if (delGen < segState.delGen) {
                // segment is newer than this deletes packet
                continue
            }

            if (segState.rld.refCount() == 1) {
                // This means we are the only remaining reference to this segment, meaning
                // it was merged away while we were running, so we can safely skip running
                // because we will run on the newly merged segment next:
                continue
            }

            val readerContext: LeafReaderContext = segState.reader.context
            for (i in deleteQueries.indices) {
                var query: Query = deleteQueries[i]
                val limit: Int
                if (delGen == segState.delGen) {
                    checkNotNull(privateSegment)
                    limit = deleteQueryLimits[i]
                } else {
                    limit = Int.MAX_VALUE
                }
                val searcher = IndexSearcher(readerContext.reader())
                searcher.queryCache = null
                query = searcher.rewrite(query)
                val weight: Weight =
                    searcher.createWeight(query, ScoreMode.COMPLETE_NO_SCORES, 1f)
                val scorer: Scorer? = weight.scorer(readerContext)
                if (scorer != null) {
                    val it: DocIdSetIterator = scorer.iterator()
                    if (segState.rld.sortMap != null && limit != Int.MAX_VALUE) {
                        checkNotNull(privateSegment)
                        // This segment was sorted on flush; we must apply seg-private deletes carefully in this
                        // case:
                        var docID: Int
                        while ((it.nextDoc()
                                .also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS
                        ) {
                            // The limit is in the pre-sorted doc space:
                            if (segState.rld.sortMap!!.newToOld(docID) < limit) {
                                if (segState.rld.delete(docID)) {
                                    delCount++
                                }
                            }
                        }
                    } else {
                        var docID: Int
                        while ((it.nextDoc().also { docID = it }) < limit) {
                            if (segState.rld.delete(docID)) {
                                delCount++
                            }
                        }
                    }
                }
            }
        }

        if (infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                "applyQueryDeletes took ${
                    (System.nanoTime() - startNS) / TimeUnit.MILLISECONDS.toNanos(1).toDouble()
                } msec for ${segStates.size} segments and ${deleteQueries.size} queries; $delCount new deletions"
            )
        }

        return delCount
    }

    private suspend fun applyTermDeletes(segStates: Array<BufferedUpdatesStream.SegmentState>): Long {
        if (deleteTerms.size() == 0L) {
            return 0
        }

        // We apply segment-private deletes on flush:
        assert(privateSegment == null)

        val startNS: Long = System.nanoTime()

        var delCount: Long = 0

        for (segState in segStates) {
            assert(
                segState.delGen != delGen
            ) { "segState.delGen=" + segState.delGen + " vs this.gen=" + delGen }
            if (segState.delGen > delGen) {
                // our deletes don't apply to this segment
                continue
            }
            if (segState.rld.refCount() == 1) {
                // This means we are the only remaining reference to this segment, meaning
                // it was merged away while we were running, so we can safely skip running
                // because we will run on the newly merged segment next:
                continue
            }

            val iter: FieldTermIterator = deleteTerms.iterator()
            var delTerm: BytesRef?
            val termDocsIterator = TermDocsIterator(segState.reader, true)
            while ((iter.next().also { delTerm = it }) != null) {
                val iterator: DocIdSetIterator? =
                    termDocsIterator.nextTerm(iter.field()!!, delTerm!!)
                if (iterator != null) {
                    var docID: Int
                    while ((iterator.nextDoc()
                            .also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS
                    ) {
                        // NOTE: there is no limit check on the docID
                        // when deleting by Term (unlike by Query)
                        // because on flush we apply all Term deletes to
                        // each segment.  So all Term deleting here is
                        // against prior segments:
                        if (segState.rld.delete(docID)) {
                            delCount++
                        }
                    }
                }
            }
        }

        if (infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                "applyTermDeletes took ${
                    (System.nanoTime() - startNS) / TimeUnit.MILLISECONDS.toNanos(1).toDouble()
                } msec for ${segStates.size} segments and ${deleteTerms.size()} del terms; $delCount new deletions"
            )
        }

        return delCount
    }

    fun setDelGen(delGen: Long) {
        assert(this.delGen == -1L) { "delGen was already previously set to " + this.delGen }
        this.delGen = delGen
        deleteTerms.setDelGen(delGen)
    }

    fun delGen(): Long {
        assert(delGen != -1L)
        return delGen
    }

    override fun toString(): String {
        var s = "delGen=$delGen"
        if (deleteTerms.size() != 0L) {
            s += " unique deleteTerms=" + deleteTerms.size()
        }
        if (deleteQueries.isNotEmpty()) {
            s += " numDeleteQueries=" + deleteQueries.size
        }
        if (fieldUpdates.isNotEmpty()) {
            s += " fieldUpdates=$fieldUpdatesCount"
        }
        if (bytesUsed != 0) {
            s += " bytesUsed=$bytesUsed"
        }
        if (privateSegment != null) {
            s += " privateSegment=$privateSegment"
        }

        return s
    }

    fun any(): Boolean {
        return deleteTerms.size() > 0 || deleteQueries.isNotEmpty() || fieldUpdatesCount > 0
    }

    /**
     * This class helps iterating a term dictionary and consuming all the docs for each terms. It
     * accepts a field, value tuple and returns a [DocIdSetIterator] if the field has an entry
     * for the given value. It has an optimized way of iterating the term dictionary if the terms are
     * passed in sorted order and makes sure terms and postings are reused as much as possible.
     */
    internal class TermDocsIterator(
        private val provider: TermsProvider,
        private val sortedTerms: Boolean
    ) {
        private var field: String? = null
        private var termsEnum: TermsEnum? = null
        private var postingsEnum: PostingsEnum? = null
        private var readerTerm: BytesRef? = null
        private var lastTerm: BytesRef? = null // only set with asserts

        internal fun interface TermsProvider {
            @Throws(IOException::class)
            fun terms(field: String): Terms
        }

        constructor(
            fields: Fields,
            sortedTerms: Boolean
        ) : this(TermsProvider { field: String -> fields.terms(field)!! }, sortedTerms)

        constructor(
            reader: LeafReader,
            sortedTerms: Boolean
        ) : this(TermsProvider { field: String -> reader.terms(field)!! }, sortedTerms)

        @Throws(IOException::class)
        private fun setField(field: String) {
            if (this.field == null || this.field != field) {
                this.field = field

                val terms: Terms = provider.terms(field)
                if (terms != null) {
                    termsEnum = terms.iterator()
                    if (sortedTerms) {
                        // need to reset otherwise we fail the assertSorted below since we sort per field
                        assert((null.also { lastTerm = it }) == null)
                        readerTerm = termsEnum!!.next()
                    }
                } else {
                    termsEnum = null
                }
            }
        }

        @Throws(IOException::class)
        fun nextTerm(
            field: String,
            term: BytesRef
        ): DocIdSetIterator? {
            setField(field)
            if (termsEnum != null) {
                if (sortedTerms) {
                    assert(assertSorted(term))
                    // in the sorted case we can take advantage of the "seeking forward" property
                    // this allows us depending on the term dict impl to reuse data-structures internally
                    // which speed up iteration over terms and docs significantly.
                    val cmp = term.compareTo(readerTerm!!)
                    if (cmp < 0) {
                        return null // requested term does not exist in this segment
                    } else if (cmp == 0) {
                        return this.docs
                    } else {
                        val status: TermsEnum.SeekStatus = termsEnum!!.seekCeil(term)
                        when (status) {
                            TermsEnum.SeekStatus.FOUND -> return this.docs
                            TermsEnum.SeekStatus.NOT_FOUND -> {
                                readerTerm = termsEnum!!.term()
                                return null
                            }

                            TermsEnum.SeekStatus.END -> {
                                // no more terms in this segment
                                termsEnum = null
                                return null
                            }

                            else -> throw AssertionError("unknown status")
                        }
                    }
                } else if (termsEnum!!.seekExact(term)) {
                    return this.docs
                }
            }
            return null
        }

        private fun assertSorted(term: BytesRef): Boolean {
            assert(sortedTerms)
            assert(
                lastTerm == null || term >= lastTerm!!
            ) { "boom: " + term.utf8ToString() + " last: " + lastTerm!!.utf8ToString() }
            lastTerm = BytesRef.deepCopyOf(term)
            return true
        }

        private val docs: DocIdSetIterator
            get() {
                checkNotNull(termsEnum)
                return termsEnum!!.postings(postingsEnum, PostingsEnum.NONE.toInt())
                    .also { postingsEnum = it }
            }
    }

    companion object {
        /* NOTE: we now apply this frozen packet immediately on creation, yet this process is heavy, and runs
   * in multiple threads, and this compression is sizable (~8.3% of the original size), so it's important
   * we run this before applying the deletes/updates. */
        /* Query we often undercount (say 24 bytes), plus int. */
        const val BYTES_PER_DEL_QUERY: Int =
            RamUsageEstimator.NUM_BYTES_OBJECT_REF + Int.SIZE_BYTES + 24

        @Throws(IOException::class)
        private fun applyDocValuesUpdates(
            segState: BufferedUpdatesStream.SegmentState,
            updates: MutableMap<String, FieldUpdatesBuffer>,
            delGen: Long,
            segmentPrivateDeletes: Boolean
        ): Long {
            // TODO: we can process the updates per DV field, from last to first so that
            // if multiple terms affect same document for the same field, we add an update
            // only once (that of the last term). To do that, we can keep a bitset which
            // marks which documents have already been updated. So e.g. if term T1
            // updates doc 7, and then we process term T2 and it updates doc 7 as well,
            // we don't apply the update since we know T1 came last and therefore wins
            // the update.
            // We can also use that bitset as 'liveDocs' to pass to TermEnum.docs(), so
            // that these documents aren't even returned.

            var updateCount: Long = 0

            // We first write all our updates private, and only in the end publish to the ReadersAndUpdates
            // */
            val resolvedUpdates: MutableList<DocValuesFieldUpdates> =
                ArrayList()
            for (fieldUpdate in updates.entries) {
                val updateField = fieldUpdate.key
                var dvUpdates: DocValuesFieldUpdates? = null
                val value: FieldUpdatesBuffer = fieldUpdate.value
                val isNumeric: Boolean = value.isNumeric()
                val iterator: FieldUpdatesBuffer.BufferedUpdateIterator = value.iterator()
                var bufferedUpdate: FieldUpdatesBuffer.BufferedUpdate?
                val termDocsIterator = TermDocsIterator(segState.reader, iterator.isSortedTerms)
                while ((iterator.next().also { bufferedUpdate = it }) != null) {
                    // TODO: we traverse the terms in update order (not term order) so that we
                    // apply the updates in the correct order, i.e. if two terms update the
                    // same document, the last one that came in wins, irrespective of the
                    // terms lexical order.
                    // we can apply the updates in terms order if we keep an updatesGen (and
                    // increment it with every update) and attach it to each NumericUpdate. Note
                    // that we cannot rely only on docIDUpto because an app may send two updates
                    // which will get same docIDUpto, yet will still need to respect the order
                    // those updates arrived.
                    // TODO: we could at least *collate* by field
                    val docIdSetIterator: DocIdSetIterator? =
                        termDocsIterator.nextTerm(bufferedUpdate!!.termField!!, bufferedUpdate.termValue!!)
                    if (docIdSetIterator != null) {
                        val limit: Int
                        if (delGen == segState.delGen) {
                            assert(segmentPrivateDeletes)
                            limit = bufferedUpdate.docUpTo
                        } else {
                            limit = Int.MAX_VALUE
                        }
                        val binaryValue: BytesRef?
                        val longValue: Long
                        if (!bufferedUpdate.hasValue) {
                            longValue = -1
                            binaryValue = null
                        } else {
                            longValue = bufferedUpdate.numericValue
                            binaryValue = bufferedUpdate.binaryValue
                        }
                        if (dvUpdates == null) {
                            if (isNumeric) {
                                dvUpdates = if (value.hasSingleValue()) {
                                    SingleValueNumericDocValuesFieldUpdates(
                                        delGen, updateField, segState.reader.maxDoc(), value.getNumericValue(0)
                                    )
                                } else {
                                    NumericDocValuesFieldUpdates(
                                        delGen,
                                        updateField,
                                        value.getMinNumeric(),
                                        value.getMaxNumeric(),
                                        segState.reader.maxDoc()
                                    )
                                }
                            } else {
                                dvUpdates =
                                    BinaryDocValuesFieldUpdates(
                                        delGen,
                                        updateField,
                                        segState.reader.maxDoc()
                                    )
                            }
                            resolvedUpdates.add(dvUpdates)
                        }
                        val docIdConsumer: (Int) -> Unit
                        val update: DocValuesFieldUpdates = dvUpdates
                        docIdConsumer = if (!bufferedUpdate.hasValue) {
                            { doc: Int -> update.reset(doc) }
                        } else if (isNumeric) {
                            { doc: Int -> update.add(doc, longValue) }
                        } else {
                            { doc: Int -> update.add(doc, binaryValue!!) }
                        }
                        val acceptDocs: Bits? = segState.rld.liveDocs
                        if (segState.rld.sortMap != null && segmentPrivateDeletes) {
                            // This segment was sorted on flush; we must apply seg-private deletes carefully in this
                            // case:
                            var doc: Int
                            while ((docIdSetIterator.nextDoc()
                                    .also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS
                            ) {
                                if (acceptDocs == null || acceptDocs.get(doc)) {
                                    // The limit is in the pre-sorted doc space:
                                    if (segState.rld.sortMap!!.newToOld(doc) < limit) {
                                        docIdConsumer(doc)
                                        updateCount++
                                    }
                                }
                            }
                        } else {
                            var doc: Int
                            while ((docIdSetIterator.nextDoc()
                                    .also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS
                            ) {
                                if (doc >= limit) {
                                    break // no more docs that can be updated for this term
                                }
                                if (acceptDocs == null || acceptDocs.get(doc)) {
                                    docIdConsumer(doc)
                                    updateCount++
                                }
                            }
                        }
                    }
                }
            }

            // now freeze & publish:
            for (update in resolvedUpdates) {
                if (update.any()) {
                    update.finish()
                    segState.rld.addDVUpdate(update)
                }
            }

            return updateCount
        }
    }
}
