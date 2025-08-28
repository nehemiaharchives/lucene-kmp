package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.setCharAt
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.InfoStream
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * [DocumentsWriterDeleteQueue] is a non-blocking linked pending deletes queue. In contrast to
 * other queue implementation we only maintain the tail of the queue. A delete queue is always used
 * in a context of a set of DWPTs and a global delete pool. Each of the DWPT and the global pool
 * need to maintain their 'own' head of the queue (as a DeleteSlice instance per [ ]). The difference between the DWPT and the global pool is that the DWPT
 * starts maintaining a head once it has added its first document since for its segments private
 * deletes only the deletes after that document are relevant. The global pool instead starts
 * maintaining the head once this instance is created by taking the sentinel instance as its initial
 * head.
 *
 *
 * Since each [DeleteSlice] maintains its own head and the list is only single linked the
 * garbage collector takes care of pruning the list for us. All nodes in the list that are still
 * relevant should be either directly or indirectly referenced by one of the DWPT's private [ ] or by the global [BufferedUpdates] slice.
 *
 *
 * Each DWPT as well as the global delete pool maintain their private DeleteSlice instance. In
 * the DWPT case updating a slice is equivalent to atomically finishing the document. The slice
 * update guarantees a "happens before" relationship to all other updates in the same indexing
 * session. When a DWPT updates a document it:
 *
 *
 *  1. consumes a document and finishes its processing
 *  1. updates its private [DeleteSlice] either by calling [.updateSlice]
 * or [.add] (if the document has a delTerm)
 *  1. applies all deletes in the slice to its private [BufferedUpdates] and resets it
 *  1. increments its internal document id
 *
 *
 * The DWPT also doesn't apply its current documents delete term until it has updated its delete
 * slice which ensures the consistency of the update. If the update fails before the DeleteSlice
 * could have been updated the deleteTerm will also not be added to its private deletes neither to
 * the global deletes.
 */
@OptIn(ExperimentalAtomicApi::class)
class DocumentsWriterDeleteQueue private constructor(
    private val infoStream: InfoStream,
    val generation: Long,
    private val startSeqNo: Long,
    /*LongSupplier*/private val previousMaxSeqId: () -> Long
) : Accountable, AutoCloseable {
    // the current end (latest delete operation) in the delete queue:
    @Volatile
    private var tail: Node<*>

    @Volatile
    private var closed = false

    /**
     * Used to record deletes against all prior (already written to disk) segments. Whenever any
     * segment flushes, we bundle up this set of deletes and insert into the buffered updates stream
     * before the newly flushed segment(s).
     */
    private val globalSlice: DeleteSlice

    private val globalBufferedUpdates: BufferedUpdates = BufferedUpdates("global")

    // only acquired to update the global deletes, pkg-private for access by tests:
    val globalBufferLock: ReentrantLock = ReentrantLock()

    /**
     * Generates the sequence number that IW returns to callers changing the index, showing the
     * effective serialization of all operations.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private val nextSeqNo: AtomicLong = AtomicLong(startSeqNo)

    /**
     * Returns the maximum sequence number for this queue. This value will change once this queue is
     * advanced.
     */
    @Volatile
    var maxSeqNo: Long = Long.Companion.MAX_VALUE
        private set

    /** Returns `true` if it was advanced.  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@get:Synchronized*/
    var isAdvanced: Boolean = false
        private set

    constructor(infoStream: InfoStream) : this(infoStream, 0, 1, { 0 })

    init {
        val value: Long = previousMaxSeqId()
        assert(value <= startSeqNo) { "illegal max sequence ID: $value start was: $startSeqNo" }
        /*
         * we use a sentinel instance as our initial tail. No slice will ever try to
         * apply this tail since the head is always omitted.
         */
        tail = Node<Any>(/*null*/ Any()) // sentinel
        globalSlice = DeleteSlice(tail)
    }

    fun addDelete(vararg queries: Query): Long {
        val seqNo = add(QueryArrayNode(queries))
        tryApplyGlobalSlice()
        return seqNo
    }

    fun addDelete(vararg terms: Term): Long {
        val seqNo = add(TermArrayNode(terms))
        tryApplyGlobalSlice()
        return seqNo
    }

    fun addDocValuesUpdates(vararg updates: DocValuesUpdate): Long {
        val seqNo = add(DocValuesUpdatesNode(*updates))
        tryApplyGlobalSlice()
        return seqNo
    }

    /** invariant for document update  */
    fun add(deleteNode: Node<*>, slice: DeleteSlice): Long {
        val seqNo = add(deleteNode)
        /*
     * this is an update request where the term is the updated documents
     * delTerm. in that case we need to guarantee that this insert is atomic
     * with regards to the given delete slice. This means if two threads try to
     * update the same document with in turn the same delTerm one of them must
     * win. By taking the node we have created for our del term as the new tail
     * it is guaranteed that if another thread adds the same right after us we
     * will apply this delete next time we update our slice and one of the two
     * competing updates wins!
     */
        slice.sliceTail = deleteNode
        assert(slice.sliceHead !== slice.sliceTail) { "slice head and tail must differ after add" }
        tryApplyGlobalSlice() // TODO doing this each time is not necessary maybe

        // we can do it just every n times or so
        return seqNo
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun add(newNode: Node<*>): Long {
        ensureOpen()
        tail.next = newNode
        this.tail = newNode
        return this.nextSequenceNumber
    }

    fun anyChanges(): Boolean {
        globalBufferLock.lock()
        try {
            /*
       * check if all items in the global slice were applied
       * and if the global slice is up-to-date
       * and if globalBufferedUpdates has changes
       */
            return globalBufferedUpdates.any()
                    || !globalSlice.isEmpty || globalSlice.sliceTail !== tail || tail.next != null
        } finally {
            globalBufferLock.unlock()
        }
    }

    fun tryApplyGlobalSlice() {
        if (globalBufferLock.tryLock()) {
            ensureOpen()
            /*
       * The global buffer must be locked but we don't need to update them if
       * there is an update going on right now. It is sufficient to apply the
       * deletes that have been added after the current in-flight global slices
       * tail the next time we can get the lock!
       */
            try {
                if (updateSliceNoSeqNo(globalSlice)) {
                    globalSlice.apply(globalBufferedUpdates, BufferedUpdates.MAX_INT)
                }
            } finally {
                globalBufferLock.unlock()
            }
        }
    }

    fun freezeGlobalBuffer(callerSlice: DeleteSlice): FrozenBufferedUpdates? {
        globalBufferLock.lock()
        try {
            ensureOpen()
            /*
           * Here we freeze the global buffer so we need to lock it, apply all
           * deletes in the queue and reset the global slice to let the GC prune the
           * queue.
           */
            val currentTail = tail // take the current tail make this local any
            // Changes after this call are applied later
            // and not relevant here
            if (callerSlice != null) {
                // Update the callers slices so we are on the same page
                callerSlice.sliceTail = currentTail
            }
            return freezeGlobalBufferInternal(currentTail)
        } finally {
            globalBufferLock.unlock()
        }
    }

    /**
     * This may freeze the global buffer unless the delete queue has already been closed. If the queue
     * has been closed this method will return `null`
     */
    fun maybeFreezeGlobalBuffer(): FrozenBufferedUpdates? {
        globalBufferLock.lock()
        try {
            if (!closed) {
                /*
                 * Here we freeze the global buffer so we need to lock it, apply all
                 * deletes in the queue and reset the global slice to let the GC prune the
                 * queue.
                 */
                return freezeGlobalBufferInternal(tail) // take the current tail make this local any
            } else {
                assert(!anyChanges()) { "we are closed but have changes" }
                return null
            }
        } finally {
            globalBufferLock.unlock()
        }
    }

    private fun freezeGlobalBufferInternal(currentTail: Node<*>): FrozenBufferedUpdates? {
        assert(globalBufferLock.isHeldByCurrentThread())
        if (globalSlice.sliceTail !== currentTail) {
            globalSlice.sliceTail = currentTail
            globalSlice.apply(globalBufferedUpdates, BufferedUpdates.MAX_INT)
        }

        if (globalBufferedUpdates.any()) {
            val packet = FrozenBufferedUpdates(infoStream, globalBufferedUpdates, null)
            globalBufferedUpdates.clear()
            return packet
        } else {
            return null
        }
    }

    fun newSlice(): DeleteSlice {
        return DeleteSlice(tail)
    }


    /** Negative result means there were new deletes since we last applied  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun updateSlice(slice: DeleteSlice): Long {
        ensureOpen()
        var seqNo = this.nextSequenceNumber
        if (slice.sliceTail !== tail) {
            // new deletes arrived since we last checked
            slice.sliceTail = tail
            seqNo = -seqNo
        }
        return seqNo
    }

    /** Just like updateSlice, but does not assign a sequence number  */
    fun updateSliceNoSeqNo(slice: DeleteSlice): Boolean {
        if (slice.sliceTail !== tail) {
            // new deletes arrived since we last checked
            slice.sliceTail = tail
            return true
        }
        return false
    }

    private fun ensureOpen() {
        if (closed) {
            throw AlreadyClosedException(
                "This " + DocumentsWriterDeleteQueue::class.simpleName + " is already closed"
            )
        }
    }

    val isOpen: Boolean
        get() = !closed

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    override fun close() {
        globalBufferLock.lock()
        try {
            // Avoid nested locking: inline the anyChanges() logic under the held lock
            val hasChanges =
                globalBufferedUpdates.any() ||
                    !globalSlice.isEmpty ||
                    globalSlice.sliceTail !== tail ||
                    tail.next != null
            check(!hasChanges) { "Can't close queue unless all changes are applied" }
            this.closed = true
            val seqNo: Long = nextSeqNo.load()
            assert(
                seqNo <= maxSeqNo
            ) { "maxSeqNo must be greater or equal to $seqNo but was $maxSeqNo" }
            nextSeqNo.store(maxSeqNo + 1)
        } finally {
            globalBufferLock.unlock()
        }
    }

    class DeleteSlice(currentTail: Node<*>) {
        // No need to be volatile, slices are thread captive (only accessed by one thread)!
        var sliceHead: Node<*> // we don't apply this one
        var sliceTail: Node<*>

        init {
            checkNotNull(currentTail)
            /*
       * Initially this is a 0 length slice pointing to the 'current' tail of
       * the queue. Once we update the slice we only need to assign the tail and
       * have a new slice
       */
            sliceTail = currentTail
            sliceHead = sliceTail
        }

        fun apply(del: BufferedUpdates, docIDUpto: Int) {
            if (sliceHead === sliceTail) {
                // 0 length slice
                return
            }
            /*
       * When we apply a slice we take the head and get its next as our first
       * item to apply and continue until we applied the tail. If the head and
       * tail in this slice are not equal then there will be at least one more
       * non-null node in the slice!
       */
            var current: Node<*>? = sliceHead
            do {
                current = current!!.next
                checkNotNull(current) { "slice property violated between the head on the tail must not be a null node" }
                current.apply(del, docIDUpto)
            } while (current !== sliceTail)
            reset()
        }

        fun reset() {
            // Reset to a 0 length slice
            sliceHead = sliceTail
        }

        /**
         * Returns `true` iff the given node is identical to the slices tail, otherwise
         * `false`.
         */
        fun isTail(node: Node<*>): Boolean {
            return sliceTail === node
        }

        /**
         * Returns `true` iff the given item is identical to the item hold by the slices
         * tail, otherwise `false`.
         */
        fun isTailItem(`object`: Any): Boolean {
            return sliceTail.item === `object`
        }

        val isEmpty: Boolean
            get() = sliceHead === sliceTail
    }

    /** For test purposes.  */
    fun numGlobalTermDeletes(): Int {
        return globalBufferedUpdates.deleteTerms.size()
    }

    fun clear() {
        globalBufferLock.lock()
        try {
            val currentTail = tail
            globalSlice.sliceTail = currentTail
            globalSlice.sliceHead = globalSlice.sliceTail
            globalBufferedUpdates.clear()
        } finally {
            globalBufferLock.unlock()
        }
    }

    open class Node<T>(val item: T) {
        @Volatile
        var next: Node<*>? = null

        open fun apply(bufferedDeletes: BufferedUpdates, docIDUpto: Int) {
            throw IllegalStateException("sentinel item must never be applied")
        }

        val isDelete: Boolean
            get() = true
    }

    private class TermNode(term: Term) : Node<Term>(term) {
        override fun apply(bufferedDeletes: BufferedUpdates, docIDUpto: Int) {
            bufferedDeletes.addTerm(item, docIDUpto)
        }

        override fun toString(): String {
            return "del=$item"
        }
    }

    private class QueryNode(query: Query) : Node<Query>(query) {
        override fun apply(bufferedDeletes: BufferedUpdates, docIDUpto: Int) {
            bufferedDeletes.addQuery(item, docIDUpto)
        }

        override fun toString(): String {
            return "del=$item"
        }
    }

    private class QueryArrayNode(query: Array<out Query>) :
        Node<Array<out Query>>(query) {
        override fun apply(bufferedUpdates: BufferedUpdates, docIDUpto: Int) {
            for (query in item) {
                bufferedUpdates.addQuery(query, docIDUpto)
            }
        }
    }

    private class TermArrayNode(term: Array<out Term>) :
        Node<Array<out Term>>(term) {
        override fun apply(bufferedUpdates: BufferedUpdates, docIDUpto: Int) {
            for (term in item) {
                bufferedUpdates.addTerm(term, docIDUpto)
            }
        }

        override fun toString(): String {
            return "dels=" + item.contentToString()
        }
    }

    private class DocValuesUpdatesNode(vararg updates: DocValuesUpdate) :
        Node<Array<out DocValuesUpdate>>(updates) {
        override fun apply(bufferedUpdates: BufferedUpdates, docIDUpto: Int) {
            for (update in item) {
                when (update.type) {
                    DocValuesType.NUMERIC -> bufferedUpdates.addNumericUpdate(
                        update as DocValuesUpdate.NumericDocValuesUpdate,
                        docIDUpto
                    )

                    DocValuesType.BINARY -> bufferedUpdates.addBinaryUpdate(
                        update as DocValuesUpdate.BinaryDocValuesUpdate,
                        docIDUpto
                    )

                    DocValuesType.NONE, DocValuesType.SORTED, DocValuesType.SORTED_SET, DocValuesType.SORTED_NUMERIC -> throw IllegalArgumentException(
                        update.type.toString() + " DocValues updates not supported yet!"
                    )

                    else -> throw IllegalArgumentException(
                        update.type.toString() + " DocValues updates not supported yet!"
                    )
                }
            }
        }

        /*override fun isDelete(): Boolean {
            return false
        }*/

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("docValuesUpdates: ")
            if (item.isNotEmpty()) {
                sb.append("term=").append(item[0].term).append("; updates: [")
                for (update in item) {
                    sb.append(update.field).append(':').append(update.valueToString()).append(',')
                }
                sb.setCharAt(sb.length - 1, ']')
            }
            return sb.toString()
        }
    }

    val bufferedUpdatesTermsSize: Int
        get() {
            val lock: ReentrantLock = globalBufferLock // Trusted final
            lock.lock()
            try {
                val currentTail = tail
                if (globalSlice.sliceTail !== currentTail) {
                    globalSlice.sliceTail = currentTail
                    globalSlice.apply(globalBufferedUpdates, BufferedUpdates.MAX_INT)
                }
                return globalBufferedUpdates.deleteTerms.size()
            } finally {
                lock.unlock()
            }
        }

    override fun ramBytesUsed(): Long {
        return globalBufferedUpdates.ramBytesUsed()
    }

    override fun toString(): String {
        return "DWDQ: [ generation: $generation ]"
    }

    @OptIn(ExperimentalAtomicApi::class)
    val nextSequenceNumber: Long
        get() {
            val seqNo: Long = nextSeqNo.fetchAndIncrement()
            assert(seqNo <= maxSeqNo) { "seqNo=$seqNo vs maxSeqNo=$maxSeqNo" }
            return seqNo
        }

    @OptIn(ExperimentalAtomicApi::class)
    val lastSequenceNumber: Long
        get() = nextSeqNo.load() - 1

    /**
     * Inserts a gap in the sequence numbers. This is used by IW during flush or commit to ensure any
     * in-flight threads get sequence numbers inside the gap
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun skipSequenceNumbers(jump: Long) {
        nextSeqNo.addAndFetch(jump)
    }

    val maxCompletedSeqNo: Long
        /** Returns the maximum completed seq no for this queue.  */
        get() {
            if (startSeqNo < nextSeqNo.load()) {
                return this.lastSequenceNumber
            } else {
                // if we haven't advanced the seqNo make sure we fall back to the previous queue
                val value: Long = previousMaxSeqId()
                assert(value < startSeqNo) { "illegal max sequence ID: $value start was: $startSeqNo" }
                return value
            }
        }

    /**
     * Advances the queue to the next queue on flush. This carries over the generation to the next
     * queue and set the [.getMaxSeqNo] based on the given maxNumPendingOps. This method can
     * only be called once, subsequently the returned queue should be used.
     *
     * @param maxNumPendingOps the max number of possible concurrent operations that will execute on
     * this queue after it was advanced. This corresponds to the number of DWPTs that own the
     * current queue at the moment when this queue is advanced since each these DWPTs can
     * increment the seqId after we advanced it.
     * @return a new queue as a successor of this queue.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun advanceQueue(maxNumPendingOps: Int): DocumentsWriterDeleteQueue {
        check(!this.isAdvanced) { "queue was already advanced" }
        this.isAdvanced = true
        val seqNo = this.lastSequenceNumber + maxNumPendingOps + 1
        maxSeqNo = seqNo
        return DocumentsWriterDeleteQueue(
            infoStream,
            generation + 1,
            seqNo + 1,  // don't pass ::getMaxCompletedSeqNo here b/c otherwise we keep an reference to this queue
            // and this will be a memory leak since the queues can't be GCed
            getPrevMaxSeqIdSupplier(nextSeqNo)
        )
    }

    companion object {
        fun newNode(term: Term): Node<Term> {
            return TermNode(term)
        }

        fun newNode(query: Query): Node<Query> {
            return QueryNode(query)
        }

        fun newNode(vararg updates: DocValuesUpdate): Node<Array<out DocValuesUpdate>> {
            return DocValuesUpdatesNode(*updates)
        }

        // we use a static method to get this lambda since we previously introduced a memory leak since it
        // would
        // implicitly reference this.nextSeqNo which holds on to this del queue. see LUCENE-9478 for
        // reference
        private fun getPrevMaxSeqIdSupplier(nextSeqNo: AtomicLong): () -> Long {
            return { nextSeqNo.load() - 1 }
        }
    }
}
