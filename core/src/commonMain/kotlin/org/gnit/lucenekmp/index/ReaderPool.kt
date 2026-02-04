package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Holds shared SegmentReader instances. IndexWriter uses SegmentReaders for 1) applying deletes/DV
 * updates, 2) doing merges, 3) handing out a real-time reader. This pool reuses instances of the
 * SegmentReaders in all these places if it is in "near real-time mode" (getReader() has been called
 * on this instance).
 */
internal class ReaderPool(
    private val directory: Directory,
    private val originalDirectory: Directory,
    private val segmentInfos: SegmentInfos,
    private val fieldNumbers: FieldInfos.FieldNumbers,
    private val completedDelGenSupplier: () -> Long,
    private val infoStream: InfoStream,
    private val softDeletesField: String?,
    reader: StandardDirectoryReader?
) : AutoCloseable {
    private val readerMap: MutableMap<SegmentCommitInfo, ReadersAndUpdates> = mutableMapOf()
    private val readerMapMutex = Mutex()

    // This is a "write once" variable (like the organic dye
    // on a DVD-R that may or may not be heated by a laser and
    // then cooled to permanently record the event): it's
    // false, by default until {@link #enableReaderPooling()}
    // is called for the first time,
    // at which point it's switched to true and never changes
    // back to false.  Once this is true, we hold open and
    // reuse SegmentReader instances internally for applying
    // deletes, doing merges, and reopening near real-time
    // readers.
    // in practice this should be called once the readers are likely
    // to be needed and reused ie if IndexWriter#getReader is called.
    @Volatile
    var isReaderPoolingEnabled: Boolean = false
        private set
    @OptIn(ExperimentalAtomicApi::class)
    private val closed: AtomicBoolean = AtomicBoolean(false)

    init {
        if (reader != null) {
            // Pre-enroll all segment readers into the reader pool; this is necessary so
            // any in-memory NRT live docs are correctly carried over, and so NRT readers
            // pulled from this IW share the same segment reader:
            val leaves: MutableList<LeafReaderContext> = reader.leaves()
            assert(segmentInfos.size() == leaves.size)
            for (i in leaves.indices) {
                val leaf: LeafReaderContext = leaves[i]
                val segReader: SegmentReader =
                    leaf.reader() as SegmentReader
                val newReader =
                    SegmentReader(
                        segmentInfos.info(i),
                        segReader,
                        segReader.liveDocs,
                        segReader.hardLiveDocs,
                        segReader.numDocs(),
                        true
                    )
                readerMap[newReader.originalSegmentInfo] = ReadersAndUpdates(
                    segmentInfos.indexCreatedVersionMajor,
                    newReader,
                    newPendingDeletes(newReader, newReader.originalSegmentInfo)
                )
            }
        }
    }

    /** Asserts this
     * // TODO Synchronized is not possible in kmp, need to think what to doinfo still exists in IW's segment infos  */
    /*@Synchronized*/
    fun assertInfoIsLive(info: SegmentCommitInfo): Boolean {
        return withReaderMapLock { assertInfoIsLiveUnsafe(info) }
    }

    /**
     * Drops reader for the given [SegmentCommitInfo] if it's pooled
     *
     * @return `true` if a reader is pooled
     */
    // TODO Synchronized is not possible in kmp, need to think what to do
    /*@Synchronized*/
    suspend fun drop(info: SegmentCommitInfo): Boolean {
        return withReaderMapLockSuspend {
            val rld: ReadersAndUpdates? = readerMap[info]
            if (rld != null) {
                assert(info === rld.info)
                readerMap.remove(info)
                rld.dropReaders()
                return@withReaderMapLockSuspend true
            }
            false
        }
    }

    /** Returns the s
     * // TODO Synchronized is not possible in kmp, need to think what to doum of the ram used by all the buffered readers and updates in MB  */
    /*@Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    fun ramBytesUsed(): Long {
        return withReaderMapLock {
            var bytes: Long = 0
            for (rld in readerMap.values) {
                bytes += rld.ramBytesUsed.load()
            }
            bytes
        }
    }

    /**
     * Returns `true` iff any of the buffered readers and updates has at least one pending
     * delete
     */
    // TODO Synchronized is not possible in kmp, need to think what to do
    /*@Synchronized*/
    fun anyDeletions(): Boolean {
        return withReaderMapLock {
            for (rld in readerMap.values) {
                if (rld.delCount > 0) {
                    return@withReaderMapLock true
                }
            }
            false
        }
    }

    /**
     * Enables reader pooling for this pool. This should be called once the readers in this pool are
     * shared with an outside resource like an NRT reader. Once reader pooling is enabled a [ ] will be kept around in the reader pool on calling [ ][.release] until the segment get dropped via calls to [ ][.drop] or [.dropAll] or [.close]. Reader pooling is disabled
     * upon construction but can't be disabled again once it's enabled.
     */
    fun enableReaderPooling() {
        this.isReaderPoolingEnabled = true
    }

    /**
     * Releases the [ReadersAndUpdates]. This should only be called if the [ ][.get] is called with the 'create' parameter set to true.
     *
     * @return `true` if any files were written by this release call.
     */
    // TODO Synchronized is not possible in kmp, need to think what to do
    /*@Synchronized*/
    suspend fun release(rld: ReadersAndUpdates, assertInfoLive: Boolean): Boolean {
        return withReaderMapLockSuspend {
            var changed = false
            // Matches incRef in get:
            rld.decRef()

            if (rld.refCount() == 0) {
                // This happens if the segment was just merged away,
                // while a buffered deletes packet was still applying deletes/updates to it.
                assert(
                    !readerMap.containsKey(rld.info)
                ) { "seg=" + rld.info + " has refCount 0 but still unexpectedly exists in the reader pool" }
            } else {
                // Pool still holds a ref:

                assert(rld.refCount() > 0) { "refCount=" + rld.refCount() + " reader=" + rld.info }

                if (!this.isReaderPoolingEnabled && rld.refCount() == 1 && readerMap.containsKey(rld.info)) {
                    // This is the last ref to this RLD, and we're not
                    // pooling, so remove it:
                    if (rld.writeLiveDocs(directory)) {
                        // Make sure we only write del docs for a live segment:
                        assert(!assertInfoLive || assertInfoIsLiveUnsafe(rld.info))
                        // Must checkpoint because we just
                        // created new _X_N.del and field updates files;
                        // don't call IW.checkpoint because that also
                        // increments SIS.version, which we do not want to
                        // do here: it was done previously (after we
                        // invoked BDS.applyDeletes), whereas here all we
                        // did was move the state to disk:
                        changed = true
                    }
                    if (rld.writeFieldUpdates(
                            directory, fieldNumbers, completedDelGenSupplier(), infoStream
                        )
                    ) {
                        changed = true
                    }
                    if (rld.numDVUpdates == 0L) {
                        rld.dropReaders()
                        readerMap.remove(rld.info)
                    } else {
                        // We are forced to pool this segment until its deletes fully apply (no delGen gaps)
                    }
                }
            }
            changed
        }
    }

    // TODO Synchronized is not possible in kmp, need to think what to do
    /*@Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    override fun close() {
        if (closed.compareAndSet(false, newValue = true)) {
            // TODO not sure if runBlocking is the right way to do this in kmp, need to think what to do
            runBlocking{ dropAll() }
        }
    }

    /**
     * Writes all doc values updates to disk if there are any.
     *
     * @return `true` iff any files where written
     */
    suspend fun writeAllDocValuesUpdates(): Boolean {
        // this needs to be protected by the reader pool lock otherwise we hit ConcurrentModificationException
        val copy = withReaderMapLock { HashSet(readerMap.values) }
        var any = false
        for (rld in copy) {
            any = any or
                    rld.writeFieldUpdates(
                        directory, fieldNumbers, completedDelGenSupplier(), infoStream
                    )
        }
        return any
    }

    /**
     * Writes all doc values updates to disk if there are any.
     *
     * @return `true` iff any files where written
     */
    suspend fun writeDocValuesUpdatesForMerge(infos: MutableList<SegmentCommitInfo>): Boolean {
        var any = false
        for (info in infos) {
            val rld: ReadersAndUpdates? = get(info, false)
            if (rld != null) {
                any = any or
                        rld.writeFieldUpdates(
                            directory, fieldNumbers, completedDelGenSupplier(), infoStream
                        )
                rld.setIsMerging()
            }
        }
        return any
    }

    // TODO Synchronized is not possible in kmp, need to think what to do
    /*@get:Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    val readersByRam: MutableList<ReadersAndUpdates>
        /**
         * Returns a list of all currently maintained ReadersAndUpdates sorted by their ram consumption
         * largest to smallest. This list can also contain readers that don't consume any ram at this
         * point i.e. don't have any updates buffered.
         */
        get() {
            class RamRecordingHolder(val updates: ReadersAndUpdates) {
                val ramBytesUsed: Long = updates.ramBytesUsed.load()
            }

            val readersByRam: ArrayList<RamRecordingHolder> = withReaderMapLock {
                if (readerMap.isEmpty()) {
                    return@withReaderMapLock ArrayList(0)
                }
                val snapshot = ArrayList<RamRecordingHolder>(readerMap.size)
                for (rld in readerMap.values) {
                    // we have to record the RAM usage once and then sort
                    // since the RAM usage can change concurrently and that will confuse the sort or hit an
                    // assertion
                    // the we can acquire here is not enough we would need to lock all ReadersAndUpdates to make
                    // sure it doesn't change
                    snapshot.add(RamRecordingHolder(rld))
                }
                snapshot
            }
            // Sort this outside of the lock by largest ramBytesUsed:
            CollectionUtil.introSort<RamRecordingHolder>(
                readersByRam
            ) { a: RamRecordingHolder, b: RamRecordingHolder ->
                Long.compare(
                    b.ramBytesUsed, a.ramBytesUsed
                )
            }
            return readersByRam.map {it.updates }.toMutableList()
        }

    /** Remove all ou
     * // TODO Synchronized is not possible in kmp, need to think what to dor references to readers, and commits any pending changes.  */
    /*@Synchronized*/
    suspend fun dropAll() {
        withReaderMapLockSuspend {
            var priorE: Throwable? = null
            val it: MutableIterator<MutableMap.MutableEntry<SegmentCommitInfo, ReadersAndUpdates>> =
                readerMap.entries.iterator()
            while (it.hasNext()) {
                val rld: ReadersAndUpdates = it.next().value

                // Important to remove as-we-go, not with .clear()
                // in the end, in case we hit an exception;
                // otherwise we could over-decref if close() is
                // called again:
                it.remove()

                // NOTE: it is allowed that these decRefs do not
                // actually close the SRs; this happens when a
                // near real-time reader is kept open after the
                // IndexWriter instance is closed:
                try {
                    rld.dropReaders()
                } catch (t: Throwable) {
                    priorE = IOUtils.useOrSuppress(priorE, t)
                }
            }
            assert(readerMap.isEmpty())
            if (priorE != null) {
                throw IOUtils.rethrowAlways(priorE)
            }
        }
    }

    /**
     * Commit live docs changes for the segment readers for the provided infos.
     *
     * @throws IOException If there is a low-level I/O error
     */
    // TODO Synchronized is not possible in kmp, need to think what to do
    /*@Synchronized*/
    suspend fun commit(infos: SegmentInfos): Boolean {
        return withReaderMapLockSuspend {
            var atLeastOneChange = false
            for (info in infos) {
                val rld: ReadersAndUpdates? = readerMap[info]
                if (rld != null) {
                    assert(rld.info === info)
                    var changed: Boolean = rld.writeLiveDocs(directory)
                    changed = changed or
                            rld.writeFieldUpdates(
                                directory, fieldNumbers, completedDelGenSupplier(), infoStream
                            )

                    if (changed) {
                        // Make sure we only write del docs for a live segment:
                        assert(assertInfoIsLiveUnsafe(info))

                        // Must checkpoint because we just
                        // created new _X_N.del and field updates files;
                        // don't call IW.checkpoint because that also
                        // increments SIS.version, which we do not want to
                        // do here: it was done previously (after we
                        // invoked BDS.applyDeletes), whereas here all we
                        // did was move the state to disk:
                        atLeastOneChange = true
                    }
                }
            }
            atLeastOneChange
        }
    }

    /**
     * Returns `true` iff there are any buffered doc values updates. Otherwise `false
    ` * .
     */
    // TODO Synchronized is not possible in kmp, need to think what to do
    /*@Synchronized*/
    fun anyDocValuesChanges(): Boolean {
        return withReaderMapLock {
            for (rld in readerMap.values) {
                // NOTE: we don't check for pending deletes because deletes carry over in RAM to NRT readers
                if (rld.numDVUpdates != 0L) {
                    return@withReaderMapLock true
                }
            }
            false
        }
    }

    /**
     * Obtain a ReadersAndLiveDocs instance from the readerPool. If create is true, you must later
     * call [.release].
     */
    // TODO Synchronized is not possible in kmp, need to think what to do
    /*@Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    fun get(
        info: SegmentCommitInfo,
        create: Boolean
    ): ReadersAndUpdates? {
        return withReaderMapLock {
            assert(
                info.info.dir === originalDirectory
            ) { "info.dir=" + info.info.dir + " vs " + originalDirectory }
            if (closed.load()) {
                assert(readerMap.isEmpty()) { "Reader map is not empty: $readerMap" }
                throw AlreadyClosedException("ReaderPool is already closed")
            }

            var rld: ReadersAndUpdates? = readerMap[info]
            if (rld == null) {
                if (!create) {
                    return@withReaderMapLock null
                }
                rld =
                    ReadersAndUpdates(
                        segmentInfos.indexCreatedVersionMajor, info, newPendingDeletes(info)
                    )
                // Steal initial reference:
                readerMap[info] = rld
            } else {
                assert(
                    rld.info === info
                ) {
                    ("rld.info="
                            + rld.info
                            + " info="
                            + info
                            + " isLive="
                            + assertInfoIsLiveUnsafe(rld.info)
                            + " vs "
                            + assertInfoIsLiveUnsafe(info))
                }
            }

            if (create) {
                // Return ref to caller:
                rld.incRef()
            }

            assert(noDupsUnsafe())

            rld
        }
    }

    private fun newPendingDeletes(info: SegmentCommitInfo): PendingDeletes {
        return if (softDeletesField == null)
            PendingDeletes(info)
        else
            PendingSoftDeletes(softDeletesField, info)
    }

    private fun newPendingDeletes(
        reader: SegmentReader,
        info: SegmentCommitInfo
    ): PendingDeletes {
        return if (softDeletesField == null)
            PendingDeletes(reader, info)
        else
            PendingSoftDeletes(softDeletesField, reader, info)
    }

    // Make sure that every segment appears only once in the
    // pool:
    private fun noDupsUnsafe(): Boolean {
        val seen: MutableSet<String> = HashSet()
        for (info in readerMap.keys) {
            assert(!seen.contains(info.info.name)) { "seen twice: " + info.info.name }
            seen.add(info.info.name)
        }
        return true
    }

    private fun assertInfoIsLiveUnsafe(info: SegmentCommitInfo): Boolean {
        val idx: Int = segmentInfos.indexOf(info)
        assert(idx != -1) { "info=$info isn't live" }
        assert(
            segmentInfos.info(idx) === info
        ) { "info=$info doesn't match live info in segmentInfos" }
        return true
    }

    private fun <T> withReaderMapLock(action: () -> T): T =
        runBlocking { readerMapMutex.withLock { action() } }

    private suspend fun <T> withReaderMapLockSuspend(action: suspend () -> T): T =
        readerMapMutex.withLock { action() }
}
