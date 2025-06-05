package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.internal.hppc.LongHashSet
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOConsumer
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.jdkport.assert
import okio.IOException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max

/**
 * Tracks the stream of [FrozenBufferedUpdates]. When DocumentsWriterPerThread flushes, its
 * buffered deletes and updates are appended to this stream and immediately resolved (to actual
 * docIDs, per segment) using the indexing thread that triggered the flush for concurrency. When a
 * merge kicks off, we sync to ensure all resolving packets complete. We also apply to all segments
 * when NRT reader is pulled, commit/close is called, or when too many deletes or updates are
 * buffered and must be flushed (by RAM usage or by count).
 *
 *
 * Each packet is assigned a generation, and each flushed or merged segment is also assigned a
 * generation, so we can track which BufferedDeletes packets to apply to any given segment.
 */
class BufferedUpdatesStream(infoStream: InfoStream) :
    Accountable {
    private val updates: MutableSet<FrozenBufferedUpdates> = HashSet<FrozenBufferedUpdates>()

    // Starts at 1 so that SegmentInfos that have never had
    // deletes applied (whose bufferedDelGen defaults to 0)
    // will be correct:
    private var nextGen: Long = 1
    private val finishedSegments: FinishedSegments
    private val infoStream: InfoStream
    @OptIn(ExperimentalAtomicApi::class)
    private val bytesUsed: AtomicLong = AtomicLong(0)

    init {
        this.infoStream = infoStream
        this.finishedSegments = FinishedSegments(infoStream)
    }

    // Appends a new packet of buffered deletes to the stream,
    // setting its generation:
    /*@Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    fun push(packet: FrozenBufferedUpdates): Long {
        /*
     * The insert operation must be atomic. If we let threads increment the gen
     * and push the packet afterwards we risk that packets are out of order.
     * With DWPT this is possible if two or more flushes are racing for pushing
     * updates. If the pushed packets get our of order would loose documents
     * since deletes are applied to the wrong segments.
     */
        packet.setDelGen(nextGen++)
        assert(packet.any())
        assert(checkDeleteStats())

        updates.add(packet)
        bytesUsed.addAndFetch(packet.bytesUsed.toLong())
        if (infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                "push new packet ($packet), packetCount=${updates.size}, bytesUsed=${bytesUsed.load() / 1024.0 / 1024.0} MB"
            )
        }
        assert(checkDeleteStats())

        return packet.delGen()
    }

    /*@get:Synchronized*/
    val pendingUpdatesCount: Int
        get() = updates.size

    /** Only used by IW.rollback  */
    /*@Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    fun clear() {
        updates.clear()
        nextGen = 1
        finishedSegments.clear()
        bytesUsed.store(0)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun any(): Boolean {
        return bytesUsed.load() != 0L
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun ramBytesUsed(): Long {
        return bytesUsed.load()
    }

    /**
     * @param anyDeletes True if any actual deletes took place:
     * @param allDeleted If non-null, contains segments that are 100% deleted
     */
    internal class ApplyDeletesResult(
        val anyDeletes: Boolean,
        allDeleted: MutableList<SegmentCommitInfo>
    ) {
        val allDeleted: MutableList<SegmentCommitInfo>

        init {
            this.allDeleted = allDeleted
        }
    }

    /**
     * Waits for all in-flight packets, which are already being resolved concurrently by indexing
     * threads, to finish. Returns true if there were any new deletes or updates. This is called for
     * refresh, commit.
     */
    @Throws(IOException::class)
    fun waitApplyAll(writer: IndexWriter) {
        /*assert(java.lang.Thread.holdsLock(writer) == false)*/ // jvm specific operation, need to do something for kotlin common
        val waitFor: MutableSet<FrozenBufferedUpdates>


        // TODO implement synchronized in kotlin common
        //synchronized(this) {
            waitFor = HashSet<FrozenBufferedUpdates>(updates)
        //}

        waitApply(waitFor, writer)
    }

    /** Returns true if this delGen is still running.  */
    fun stillRunning(delGen: Long): Boolean {
        return finishedSegments.stillRunning(delGen)
    }

    fun finishedSegment(delGen: Long) {
        finishedSegments.finishedSegment(delGen)
    }

    /**
     * Called by indexing threads once they are fully done resolving all deletes for the provided
     * delGen. We track the completed delGens and record the maximum delGen for which all prior
     * delGens, inclusive, are completed, so that it's safe for doc values updates to apply and write.
     */
    /*@Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    fun finished(packet: FrozenBufferedUpdates) {
        // TODO: would be a bit more memory efficient to track this per-segment, so when each segment
        // writes it writes all packets finished for
        // it, rather than only recording here, across all segments.  But, more complex code, and more
        // CPU, and maybe not so much impact in
        // practice
        assert(packet.applied.getCount() == 1L) { "packet=$packet" }

        packet.applied.countDown()

        updates.remove(packet)

        bytesUsed.addAndFetch(-packet.bytesUsed.toLong())

        finishedSegment(packet.delGen())
    }

    val completedDelGen: Long
        /** All frozen packets up to and including this del gen are guaranteed to be finished.  */
        get() = finishedSegments.completedDelGen

    /**
     * Waits only for those in-flight packets that apply to these merge segments. This is called when
     * a merge needs to finish and must ensure all deletes to the merging segments are resolved.
     */
    @Throws(IOException::class)
    fun waitApplyForMerge(
        mergeInfos: MutableList<SegmentCommitInfo>,
        writer: IndexWriter
    ) {
        var maxDelGen = Long.Companion.MIN_VALUE
        for (info in mergeInfos) {
            maxDelGen = max(maxDelGen, info.bufferedDeletesGen)
        }

        val waitFor: MutableSet<FrozenBufferedUpdates> = HashSet()

        // TODO implement synchronized in kotlin common
        //synchronized(this) {
            for (packet in updates) {
                if (packet.delGen() <= maxDelGen) {
                    // We must wait for this packet before finishing the merge because its
                    // deletes apply to a subset of the segments being merged:
                    waitFor.add(packet)
                }
            }
        //}

        if (infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                ("waitApplyForMerge: "
                        + waitFor.size
                        + " packets, "
                        + mergeInfos.size
                        + " merging segments")
            )
        }

        waitApply(waitFor, writer)
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    private fun waitApply(waitFor: MutableSet<FrozenBufferedUpdates>, writer: IndexWriter) {
        val startNS: Long = System.nanoTime()

        val packetCount = waitFor.size

        if (waitFor.isEmpty()) {
            if (infoStream.isEnabled("BD")) {
                infoStream.message("BD", "waitApply: no deletes to apply")
            }
            return
        }

        if (infoStream.isEnabled("BD")) {
            infoStream.message("BD", "waitApply: " + waitFor.size + " packets: " + waitFor)
        }

        val pendingPackets: ArrayList<FrozenBufferedUpdates> = ArrayList()
        var totalDelCount: Long = 0
        for (packet in waitFor) {
            // Frozen packets are now resolved, concurrently, by the indexing threads that
            // create them, by adding a DocumentsWriter.ResolveUpdatesEvent to the events queue,
            // but if we get here and the packet is not yet resolved, we resolve it now ourselves:
            if (writer.tryApply(packet) == false) {
                // if somebody else is currently applying it - move on to the next one and force apply below
                pendingPackets.add(packet)
            }
            totalDelCount += packet.totalDelCount
        }
        for (packet in pendingPackets) {
            // now block on all the packets that were concurrently applied to ensure they are due before
            // we continue.
            writer.forceApply(packet)
        }

        if (infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                "waitApply: done $packetCount packets; totalDelCount=$totalDelCount; totBytesUsed=${bytesUsed.load()}; took ${(System.nanoTime() - startNS) / TimeUnit.MILLISECONDS.toNanos(1).toDouble()} msec"
            )
        }
    }

    /*@Synchronized*/
    fun getNextGen(): Long {
        return nextGen++
    }

    /** Holds all per-segment internal state used while resolving deletions.  */
    class SegmentState(
        rld: ReadersAndUpdates,
        onClose: IOConsumer<ReadersAndUpdates>,
        info: SegmentCommitInfo
    ) : AutoCloseable {
        val delGen: Long
        val rld: ReadersAndUpdates
        val reader: SegmentReader
        val startDelCount: Int
        private val onClose: IOConsumer<ReadersAndUpdates>

        var termsEnum: TermsEnum? = null
        var postingsEnum: PostingsEnum? = null
        var term: BytesRef? = null

        init {
            this.rld = rld
            reader = rld.getReader(IOContext.DEFAULT)
            startDelCount = rld.delCount
            delGen = info.bufferedDeletesGen
            this.onClose = onClose
        }

        override fun toString(): String {
            return "SegmentState(" + rld.info + ")"
        }

        override fun close() {
            IOUtils.close(
                AutoCloseable { runBlocking{ rld.release(reader) } },
                AutoCloseable { onClose.accept(rld) })
        }
    }

    // only for assert
    @OptIn(ExperimentalAtomicApi::class)
    private fun checkDeleteStats(): Boolean {
        var bytesUsed2: Long = 0
        for (packet in updates) {
            bytesUsed2 += packet.bytesUsed.toLong()
        }
        assert(bytesUsed2 == bytesUsed.load()) { "bytesUsed2=$bytesUsed2 vs $bytesUsed" }
        return true
    }

    /**
     * Tracks the contiguous range of packets that have finished resolving. We need this because the
     * packets are concurrently resolved, and we can only write to disk the contiguous completed
     * packets.
     */
    private class FinishedSegments(infoStream: InfoStream) {
        /** Largest del gen, inclusive, for which all prior packets have finished applying.  */
        /*@get:Synchronized*/
        var completedDelGen: Long = 0
            private set

        /**
         * This lets us track the "holes" in the current frontier of applying del gens; once the holes
         * are filled in we can advance completedDelGen.
         */
        private val finishedDelGens: LongHashSet =
            LongHashSet()

        private val infoStream: InfoStream

        init {
            this.infoStream = infoStream
        }

        /*@Synchronized*/
        fun clear() {
            finishedDelGens.clear()
            completedDelGen = 0
        }

        /*@Synchronized*/
        fun stillRunning(delGen: Long): Boolean {
            return delGen > completedDelGen && finishedDelGens.contains(delGen) == false
        }

        /*@Synchronized*/
        fun finishedSegment(delGen: Long) {
            finishedDelGens.add(delGen)
            while (true) {
                if (finishedDelGens.contains(completedDelGen + 1)) {
                    finishedDelGens.remove(completedDelGen + 1)
                    completedDelGen++
                } else {
                    break
                }
            }

            if (infoStream.isEnabled("BD")) {
                infoStream.message(
                    "BD", "finished packet delGen=$delGen now completedDelGen=$completedDelGen"
                )
            }
        }
    }
}
