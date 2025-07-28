package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.DocValuesUpdate.BinaryDocValuesUpdate
import org.gnit.lucenekmp.index.DocValuesUpdate.NumericDocValuesUpdate
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.jdkport.set
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.ByteBlockPool.DirectTrackingAllocator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefHash
import org.gnit.lucenekmp.util.BytesRefHash.DirectBytesStartArray
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Holds buffered deletes and updates, by docID, term or query for a single segment. This is used to
 * hold buffered pending deletes and updates against the to-be-flushed segment. Once the deletes and
 * updates are pushed (on flush in DocumentsWriter), they are converted to a [ ] instance and pushed to the [BufferedUpdatesStream].
 */
// NOTE: instances of this class are accessed either via a private
// instance on DocumentWriterPerThread, or via sync'd code by
// DocumentsWriterDeleteQueue
class BufferedUpdates(val segmentName: String) : Accountable {
    @OptIn(ExperimentalAtomicApi::class)
    val numFieldUpdates: AtomicInt = AtomicInt(0)

    val deleteTerms: DeletedTerms = DeletedTerms()
    val deleteQueries: MutableMap<Query, Int> = mutableMapOf()

    val fieldUpdates: MutableMap<String, FieldUpdatesBuffer> = mutableMapOf()

    private val bytesUsed: Counter = Counter.newCounter(true)
    val fieldUpdatesBytesUsed: Counter = Counter.newCounter(true)

    var gen: Long = 0

    @OptIn(ExperimentalAtomicApi::class)
    override fun toString(): String {
        if (VERBOSE_DELETES) {
            return (("gen=$gen")
                    + (", deleteTerms=$deleteTerms")
                    + (", deleteQueries=$deleteQueries")
                    + (", fieldUpdates=$fieldUpdates")
                    + (", bytesUsed=$bytesUsed"))
        } else {
            var s = "gen=$gen"
            if (!deleteTerms.isEmpty) {
                s += " " + deleteTerms.size() + " unique deleted terms "
            }
            if (deleteQueries.isNotEmpty()) {
                s += " " + deleteQueries.size + " deleted queries"
            }
            if (numFieldUpdates.get() != 0) {
                s += " " + numFieldUpdates.get() + " field updates"
            }
            if (bytesUsed.get() != 0L) {
                s += " bytesUsed=" + bytesUsed.get()
            }

            return s
        }
    }

    fun addQuery(query: Query, docIDUpto: Int) {
        val current = deleteQueries.put(query, docIDUpto)
        // increment bytes used only if the query wasn't added so far.
        if (current == null) {
            bytesUsed.addAndGet(BYTES_PER_DEL_QUERY.toLong())
        }
    }

    fun addTerm(term: Term, docIDUpto: Int) {
        val current = deleteTerms.get(term)
        if (current != -1 && docIDUpto < current) {
            // Only record the new number if it's greater than the
            // current one.  This is important because if multiple
            // threads are replacing the same doc at nearly the
            // same time, it's possible that one thread that got a
            // higher docID is scheduled before the other
            // threads.  If we blindly replace than we can
            // incorrectly get both docs indexed.
            return
        }

        deleteTerms.put(term, docIDUpto)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun addNumericUpdate(update: NumericDocValuesUpdate, docIDUpto: Int) {
        val buffer: FieldUpdatesBuffer =
            fieldUpdates.getOrPut(
                update.field
            ) { FieldUpdatesBuffer(fieldUpdatesBytesUsed, update, docIDUpto) }
        if (update.hasValue) {
            buffer.addUpdate(update.term, update.getValue(), docIDUpto)
        } else {
            buffer.addNoValue(update.term, docIDUpto)
        }
        numFieldUpdates.incrementAndFetch()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun addBinaryUpdate(update: BinaryDocValuesUpdate, docIDUpto: Int) {
        val buffer: FieldUpdatesBuffer =
            fieldUpdates.getOrPut(
                update.field
            ) { FieldUpdatesBuffer(fieldUpdatesBytesUsed, update, docIDUpto) }
        if (update.hasValue) {
            buffer.addUpdate(update.term, update.getValue(), docIDUpto)
        } else {
            buffer.addNoValue(update.term, docIDUpto)
        }
        numFieldUpdates.incrementAndFetch()
    }

    fun clearDeleteTerms() {
        deleteTerms.clear()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun clear() {
        deleteTerms.clear()
        deleteQueries.clear()
        numFieldUpdates.set(0)
        fieldUpdates.clear()
        bytesUsed.addAndGet(-bytesUsed.get())
        fieldUpdatesBytesUsed.addAndGet(-fieldUpdatesBytesUsed.get())
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun any(): Boolean {
        return deleteTerms.size() > 0 || deleteQueries.isNotEmpty() || numFieldUpdates.get() > 0
    }

    override fun ramBytesUsed(): Long {
        return bytesUsed.get() + fieldUpdatesBytesUsed.get() + deleteTerms.ramBytesUsed()
    }

    class DeletedTerms : Accountable {
        private val bytesUsed: Counter = Counter.newCounter()
        private val pool: ByteBlockPool = ByteBlockPool(DirectTrackingAllocator(bytesUsed))
        private val deleteTerms: MutableMap<String, BytesRefIntMap> = HashMap()
        private var termsSize = 0

        /**
         * Get the newest doc id of the deleted term.
         *
         * @param term The deleted term.
         * @return The newest doc id of this deleted term.
         */
        fun get(term: Term): Int {
            val hash = deleteTerms[term.field]
            if (hash == null) {
                return -1
            }
            return hash.get(term.bytes)
        }

        /**
         * Put the newest doc id of the deleted term.
         *
         * @param term The deleted term.
         * @param value The newest doc id of the deleted term.
         */
        fun put(term: Term, value: Int) {
            val hash: BytesRefIntMap = deleteTerms[term.field] ?: run {
                bytesUsed.addAndGet(RamUsageEstimator.sizeOf(term.field))
                BytesRefIntMap(pool, bytesUsed).also {
                    deleteTerms[term.field] = it
                }
            }
            if (hash.put(term.bytes, value)) {
                termsSize++
            }
        }

        fun clear() {
            pool.reset(false, reuseFirst = false)
            bytesUsed.addAndGet(-bytesUsed.get())
            deleteTerms.clear()
            termsSize = 0
        }

        fun size(): Int {
            return termsSize
        }

        val isEmpty: Boolean
            get() = termsSize == 0

        /** Just for test, not efficient.  */
        fun keySet(): MutableSet<Term> {
            return deleteTerms.entries.flatMap { entry ->
                entry.value.keySet().map { bytesRef ->
                    Term(entry.key, bytesRef)
                }
            }.toMutableSet()
        }

        fun interface DeletedTermConsumer<E : Exception> {
            @Throws(Exception::class)
            fun accept(term: Term, docId: Int)
        }

        /**
         * Consume all terms in a sorted order.
         *
         *
         * Note: This is a destructive operation as it calls [BytesRefHash.sort].
         *
         * @see BytesRefHash.sort
         */
        @Throws(Exception::class)
        fun <E : Exception> forEachOrdered(consumer: DeletedTermConsumer<E>) {
            val deleteFields: MutableList<MutableMap.MutableEntry<String, BytesRefIntMap>> =
                deleteTerms.entries.toMutableList()
            deleteFields.sortBy { it.key }
            val scratch = Term("", BytesRef())
            for (deleteFieldEntry in deleteFields) {
                scratch.field = deleteFieldEntry.key
                val terms: BytesRefIntMap = deleteFieldEntry.value
                val indices: IntArray = terms.bytesRefHash.sort()
                for (i in 0..<terms.bytesRefHash.size()) {
                    val index = indices[i]
                    terms.bytesRefHash.get(index, scratch.bytes)
                    consumer.accept(scratch, terms.values[index])
                }
            }
        }

        /** Visible for testing.  */
        fun getPool(): ByteBlockPool {
            return pool
        }

        override fun ramBytesUsed(): Long {
            return bytesUsed.get()
        }

        /** Used for [BufferedUpdates.VERBOSE_DELETES].  */
        override fun toString(): String {
            return keySet().joinToString(", ", "{", "}") { t: Term -> t.toString() + "=" + get(t) }
        }
    }

    private class BytesRefIntMap(pool: ByteBlockPool, private val counter: Counter) {
        val bytesRefHash: BytesRefHash = BytesRefHash(
            pool,
            BytesRefHash.DEFAULT_CAPACITY,
            DirectBytesStartArray(BytesRefHash.DEFAULT_CAPACITY, counter)
        )
        var values: IntArray

        init {
            this.values = IntArray(BytesRefHash.DEFAULT_CAPACITY)
            counter.addAndGet(INIT_RAM_BYTES)
        }

        fun keySet(): MutableSet<BytesRef> {
            val scratch = BytesRef()
            val set: MutableSet<BytesRef> = HashSet()
            for (i in 0..<bytesRefHash.size()) {
                bytesRefHash.get(i, scratch)
                set.add(BytesRef.deepCopyOf(scratch))
            }
            return set
        }

        fun put(key: BytesRef, value: Int): Boolean {
            require(value >= 0)
            val e: Int = bytesRefHash.add(key)
            if (e < 0) {
                values[-e - 1] = value
                return false
            } else {
                if (e >= values.size) {
                    val originLength = values.size
                    values = ArrayUtil.grow(values, e + 1)
                    counter.addAndGet((values.size - originLength).toLong() * Int.SIZE_BYTES)
                }
                values[e] = value
                return true
            }
        }

        fun get(key: BytesRef): Int {
            val e: Int = bytesRefHash.find(key)
            if (e == -1) {
                return -1
            }
            return values[e]
        }

        companion object {
            private val INIT_RAM_BYTES: Long = (RamUsageEstimator.shallowSizeOf(BytesRefIntMap::class)
                    + RamUsageEstimator.shallowSizeOf(BytesRefHash::class)
                    + RamUsageEstimator.sizeOf(IntArray(BytesRefHash.DEFAULT_CAPACITY)))
        }
    }

    companion object {
        /* Rough logic: HashMap has an array[Entry] w/ varying
  load factor (say 2 * POINTER).  Entry is object w/
  Query key, Integer val, int hash, Entry next
  (OBJ_HEADER + 3*POINTER + INT).  Query we often
  undercount (say 24 bytes).  Integer is OBJ_HEADER + INT. */
        const val BYTES_PER_DEL_QUERY: Int =
            (5 * RamUsageEstimator.NUM_BYTES_OBJECT_REF + 2 * RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + 2 * Int.SIZE_BYTES + 24)
        const val MAX_INT: Int = Int.MAX_VALUE

        private const val VERBOSE_DELETES = false
    }
}
