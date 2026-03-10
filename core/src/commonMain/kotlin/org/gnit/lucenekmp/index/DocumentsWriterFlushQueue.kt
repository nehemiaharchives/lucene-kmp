package org.gnit.lucenekmp.index

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.index.DocumentsWriterPerThread.FlushedSegment
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.IOConsumer
import okio.IOException
import org.gnit.lucenekmp.jdkport.peek
import org.gnit.lucenekmp.jdkport.poll
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

private val dwfqLogger = KotlinLogging.logger {}

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
    private val queueLock: ReentrantLock = ReentrantLock()

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
                queueLock.lock()
                try {
                    // no need to publish anything if we don't have any frozen updates
                    queue.add(ticket)
                    success = true
                } finally {
                    queueLock.unlock()
                }
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
    fun addSegment(ticket: FlushTicket, segment: FlushedSegment) {
        assert(ticket.hasSegment)
        // the actual flush is done asynchronously and once done the FlushedSegment
        // is passed to the flush ticket
        queueLock.lock()
        try {
            ticket.setSegment(segment)
        } finally {
            queueLock.unlock()
        }
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun markTicketFailed(ticket: FlushTicket) {
        assert(ticket.hasSegment)
        // to free the queue we mark tickets as failed just to clean up the queue.
        queueLock.lock()
        try {
            ticket.setFailed()
        } finally {
            queueLock.unlock()
        }
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
            val head: FlushTicket?
            val canPublish: Boolean
            queueLock.lock()
            try {
                head = queue.peek()
                canPublish = head?.canPublish() == true // do this synced
            } finally {
                queueLock.unlock()
            }
            if (canPublish) {
                try {
                    /*
                   * if we block on publish -> lock IW -> lock BufferedDeletes we don't block
                   * concurrent segment flushes just because they want to append to the queue.
                   * the downside is that we need to force a purge on fullFlush since there could
                   * be a ticket still in the queue.
                   */
                    consumer.accept(head!!) // head is non-null here
                } finally {
                    // finally remove the published ticket from the queue
                    queueLock.lock()
                    try {
                        val poll: FlushTicket? = queue.poll()
                        decTickets()
                        // we hold the purgeLock so no other thread should have polled:
                        assert(poll == head)
                    } finally {
                        queueLock.unlock()
                    }
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

    class FlushTicket(private val frozenUpdates: FrozenBufferedUpdates?, val hasSegment: Boolean) {
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
            if (segment != null) {
                dwfqLogger.debug {
                    "dwfq setFailed after segment assigned hasSegment=$hasSegment failed=$failed published=$published"
                }
            }
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
        fun getFrozenUpdates(): FrozenBufferedUpdates? {
            return frozenUpdates
        }
    }
}
