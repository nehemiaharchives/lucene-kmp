package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.DocumentsWriterPerThread.FlushedSegment
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.IOConsumer
import okio.IOException
import org.gnit.lucenekmp.jdkport.decrementAndGet
import org.gnit.lucenekmp.jdkport.incrementAndGet
import org.gnit.lucenekmp.jdkport.peek
import org.gnit.lucenekmp.jdkport.poll
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * @lucene.internal
 */
class DocumentsWriterFlushQueue {
    private val queue: ArrayDeque<FlushTicket> = ArrayDeque<FlushTicket>()

    // we track tickets separately since count must be present even before the ticket is
    // constructed ie. queue.size would not reflect it.
    @OptIn(ExperimentalAtomicApi::class)
    private val ticketCount: AtomicInteger = AtomicInteger(0)
    private val purgeLock: ReentrantLock = ReentrantLock()

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    fun addTicket(ticketSupplier: () -> FlushTicket): FlushTicket {
        // first inc the ticket count - freeze opens a window for #anyChanges to fail
        incTickets()
        var success = false
        try {
            val ticket: FlushTicket = ticketSupplier()
            if (ticket != null) {
                // no need to publish anything if we don't have any frozen updates
                queue.add(ticket)
                success = true
            }
            return ticket
        } finally {
            if (!success) {
                decTickets()
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun incTickets() {
        val numTickets: Int = ticketCount.incrementAndFetch()
        assert(numTickets > 0)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun decTickets() {
        val numTickets: Int = ticketCount.decrementAndFetch()
        assert(numTickets >= 0)
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun addSegment(ticket: FlushTicket, segment: DocumentsWriterPerThread.FlushedSegment) {
        assert(ticket.hasSegment)
        // the actual flush is done asynchronously and once done the FlushedSegment
        // is passed to the flush ticket
        ticket.setSegment(segment)
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun markTicketFailed(ticket: FlushTicket) {
        assert(ticket.hasSegment)
        // to free the queue we mark tickets as failed just to clean up the queue.
        ticket.setFailed()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun hasTickets(): Boolean {
        assert(ticketCount.load() >= 0) { "ticketCount should be >= 0 but was: " + ticketCount.load() }
        return ticketCount.load() != 0
    }

    @Throws(IOException::class)
    private fun innerPurge(consumer: IOConsumer<FlushTicket>) {
        assert(purgeLock.isHeldByCurrentThread())
        while (true) {
            val canPublish: Boolean

            // TODO Synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            val head = queue.peek()
                canPublish = head != null && head.canPublish() // do this synced
            //}
            if (canPublish) {
                try {
                    /*
                   * if we block on publish -> lock IW -> lock BufferedDeletes we don't block
                   * concurrent segment flushes just because they want to append to the queue.
                   * the downside is that we need to force a purge on fullFlush since there could
                   * be a ticket still in the queue.
                   */
                    consumer.accept(head)
                } finally {
                    // TODO Synchronized is not supported in KMP, need to think what to do here
                    //synchronized(this) {
                        // finally remove the published ticket from the queue
                        val poll: FlushTicket? = queue.poll()
                        decTickets()
                        // we hold the purgeLock so no other thread should have polled:
                        assert(poll == head)
                    //}
                }
            } else {
                break
            }
        }
    }

    @Throws(IOException::class)
    fun forcePurge(consumer: IOConsumer<FlushTicket>) {
        // TODO Thread is not supported in KMP, need to think what to do here
        /*assert(!java.lang.Thread.holdsLock(this))*/
        purgeLock.lock()
        try {
            innerPurge(consumer)
        } finally {
            purgeLock.unlock()
        }
    }

    @Throws(IOException::class)
    fun tryPurge(consumer: IOConsumer<FlushTicket>) {
        // TODO Thread is not supported in KMP, need to think what to do here
        /*assert(!java.lang.Thread.holdsLock(this))*/
        if (purgeLock.tryLock()) {
            try {
                innerPurge(consumer)
            } finally {
                purgeLock.unlock()
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun getTicketCount(): Int {
        return ticketCount.load()
    }

    class FlushTicket(private val frozenUpdates: FrozenBufferedUpdates, val hasSegment: Boolean) {
        private var segment: FlushedSegment? = null
        private var failed = false
        private var published = false

        fun canPublish(): Boolean {
            return hasSegment == false || segment != null || failed
        }

        // TODO Synchronized is not supported in KMP, need to think what to do here
        /*@Synchronized*/
        fun markPublished() {
            assert(!published) { "ticket was already published - can not publish twice" }
            published = true
        }

        fun setSegment(segment: FlushedSegment) {
            assert(!failed)
            this.segment = segment
        }

        fun setFailed() {
            assert(segment == null)
            failed = true
        }

        val flushedSegment: FlushedSegment?
            /**
             * Returns the flushed segment or `null` if this flush ticket doesn't have a segment.
             * This can be the case if this ticket represents a flushed global frozen updates package.
             */
            get() = segment

        /** Returns a frozen global deletes package.  */
        fun getFrozenUpdates(): FrozenBufferedUpdates {
            return frozenUpdates
        }
    }
}
