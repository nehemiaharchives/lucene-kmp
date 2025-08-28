package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.FileDeleter
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * This class keeps track of each SegmentInfos instance that is still "live", either because it
 * corresponds to a segments_N file in the Directory (a "commit", i.e. a committed SegmentInfos) or
 * because it's an in-memory SegmentInfos that a writer is actively updating but has not yet
 * committed. This class uses simple reference counting to map the live SegmentInfos instances to
 * individual files in the Directory.
 *
 *
 * The same directory file may be referenced by more than one IndexCommit, i.e. more than one
 * SegmentInfos. Therefore we count how many commits reference each file. When all the commits
 * referencing a certain file have been deleted, the refcount for that file becomes zero, and the
 * file is deleted.
 *
 *
 * A separate deletion policy interface (IndexDeletionPolicy) is consulted on creation (onInit)
 * and once per commit (onCommit), to decide when a commit should be removed.
 *
 *
 * It is the business of the IndexDeletionPolicy to choose when to delete commit points. The
 * actual mechanics of file deletion, retrying, etc, derived from the deletion of commit points is
 * the business of the IndexFileDeleter.
 *
 *
 * The current default deletion policy is [KeepOnlyLastCommitDeletionPolicy], which removes
 * all prior commits when a new commit has completed. This matches the behavior before 2.2.
 *
 *
 * Note that you must hold the write.lock before instantiating this class. It opens segments_N
 * file(s) directly with no retry logic.
 */
internal class IndexFileDeleter(
    files: Array<String>,
    directoryOrig: Directory,
    directory: Directory,
    policy: IndexDeletionPolicy,
    segmentInfos: SegmentInfos,
    private val infoStream: InfoStream,
    private val writer: IndexWriter,
    initialIndexExists: Boolean,
    isReaderInit: Boolean
) : AutoCloseable {
    /* Holds all commits (segments_N) currently in the index.
      * This will have just 1 commit if you are using the
      * default delete policy (KeepOnlyLastCommitDeletionPolicy).
      * Other policies may leave commit points live for longer
      * in which case this list would be longer than 1: */
    private val commits: MutableList<CommitPoint> = mutableListOf()

    /* Holds files we had incref'd from the previous
   * non-commit checkpoint: */
    private val lastFiles: MutableList<String> = mutableListOf()

    /* Commits that the IndexDeletionPolicy have decided to delete: */
    private val commitsToDelete: MutableList<CommitPoint> = mutableListOf()

    private val directoryOrig: Directory // for commit point metadata
    private val directory: Directory
    private val policy: IndexDeletionPolicy

    val startingCommitDeleted: Boolean
    private var lastSegmentInfos: SegmentInfos? = null

    private val fileDeleter: FileDeleter

    // called only from assert
    private fun locked(): Boolean {
        return true /* writer == null || java.lang.Thread.holdsLock(writer)*/ // TODO Thread is not available in Kotlin Multiplatform, need to think what to do here
    }

    /**
     * Initialize the deleter: find all previous commits in the Directory, incref the files they
     * reference, call the policy to let it delete commits. This will remove any files not referenced
     * by any of the commits.
     *
     * @throws IOException if there is a low-level IO error
     */
    init {

        val currentSegmentsFile: String? = segmentInfos.segmentsFileName

        if (infoStream.isEnabled("IFD")) {
            infoStream.message(
                "IFD",
                ("init: current segments file is \""
                        + currentSegmentsFile
                        + "\"; deletionPolicy="
                        + policy)
            )
        }

        this.policy = policy
        this.directoryOrig = directoryOrig
        this.directory = directory

        this.fileDeleter = FileDeleter(
            directory
        ) { msgType: FileDeleter.MsgType, msg: String ->
            this.logInfo(
                msgType,
                msg
            )
        }

        // First pass: walk the files and initialize our ref
        // counts:
        var currentCommitPoint: CommitPoint? = null

        if (currentSegmentsFile != null) {
            for (fileName in files) {
                if (!fileName.endsWith("write.lock")
                    && (IndexFileNames.CODEC_FILE_PATTERN.matches(fileName)
                        || fileName.startsWith(IndexFileNames.SEGMENTS)
                        || fileName.startsWith(IndexFileNames.PENDING_SEGMENTS))
                ) {
                    // Add this file to refCounts with initial count 0:

                    fileDeleter.initRefCount(fileName)

                    if (fileName.startsWith(IndexFileNames.SEGMENTS)) {
                        // This is a commit (segments or segments_N), and
                        // it's valid (<= the max gen).  Load it, then
                        // incref all files it refers to:

                        if (infoStream.isEnabled("IFD")) {
                            infoStream.message("IFD", "init: load commit \"$fileName\"")
                        }
                        val sis: SegmentInfos =
                            SegmentInfos.readCommit(directoryOrig, fileName)

                        val commitPoint = CommitPoint(commitsToDelete, directoryOrig, sis)
                        if (sis.generation == segmentInfos.generation) {
                            currentCommitPoint = commitPoint
                        }
                        commits.add(commitPoint)
                        incRef(sis, true)

                        if (lastSegmentInfos == null
                            || sis.generation > lastSegmentInfos!!.generation
                        ) {
                            lastSegmentInfos = sis
                        }
                    }
                }
            }
        }

        if (currentCommitPoint == null && currentSegmentsFile != null && initialIndexExists) {
            // We did not in fact see the segments_N file
            // corresponding to the segmentInfos that was passed
            // in.  Yet, it must exist, because our caller holds
            // the write lock.  This can happen when the directory
            // listing was stale (eg when index accessed via NFS
            // client with stale directory listing cache).  So we
            // try now to explicitly open this commit point:
            var sis: SegmentInfos? = null
            try {
                sis = SegmentInfos.readCommit(directoryOrig, currentSegmentsFile)
            } catch (e: IOException) {
                throw CorruptIndexException(
                    "unable to read current segments_N file", currentSegmentsFile, e
                )
            }
            if (infoStream.isEnabled("IFD")) {
                infoStream.message(
                    "IFD", "forced open of current segments file " + segmentInfos.segmentsFileName
                )
            }
            currentCommitPoint = CommitPoint(commitsToDelete, directoryOrig, sis)
            commits.add(currentCommitPoint)
            incRef(sis, true)
        }

        if (isReaderInit) {
            // Incoming SegmentInfos may have NRT changes not yet visible in the latest commit, so we have
            // to protect its files from deletion too:
            checkpoint(segmentInfos, false)
        }

        // We keep commits list in sorted order (oldest to newest):
        CollectionUtil.timSort<CommitPoint>(commits)
        val relevantFiles: MutableCollection<String> = HashSet(fileDeleter.allFiles)
        val pendingDeletions: MutableSet<String> = directoryOrig.pendingDeletions
        if (!pendingDeletions.isEmpty()) {
            relevantFiles.addAll(pendingDeletions)
        }
        // refCounts only includes "normal" filenames (does not include write.lock)
        inflateGens(segmentInfos, relevantFiles, infoStream)

        // Now delete anything with ref count at 0.  These are
        // presumably abandoned files eg due to crash of
        // IndexWriter.
        val toDelete: MutableSet<String> = fileDeleter.unrefedFiles
        for (fileName in toDelete) {
            check(!fileName.startsWith(IndexFileNames.SEGMENTS)) { "file \"$fileName\" has refCount=0, which should never happen on init" }
            if (infoStream.isEnabled("IFD")) {
                infoStream.message("IFD", "init: removing unreferenced file \"$fileName\"")
            }
        }

        fileDeleter.deleteFilesIfNoRef(toDelete)

        // Finally, give policy a chance to remove things on
        // startup:
        policy.onInit(commits)

        // Always protect the incoming segmentInfos since
        // sometime it may not be the most recent commit
        checkpoint(segmentInfos, false)

        startingCommitDeleted = currentCommitPoint?.isDeleted ?: false

        deleteCommits()
    }

    @Throws(AlreadyClosedException::class)
    fun ensureOpen() {
        writer.ensureOpen(false)
        // since we allow 'closing' state, we must still check this, we could be closing because we hit
        // e.g. OOM
        if (writer.getTragicException() != null) {
            throw AlreadyClosedException(
                "refusing to delete any files: this IndexWriter hit an unrecoverable exception",
                writer.getTragicException()
            )
        }
    }

    val isClosed: Boolean
        // for testing
        get() {
            try {
                ensureOpen()
                return false
            } catch (ace: AlreadyClosedException) {
                return true
            }
        }

    /**
     * Remove the CommitPoints in the commitsToDelete List by DecRef'ing all files from each
     * SegmentInfos.
     */
    @Throws(IOException::class)
    private fun deleteCommits() {
        var size = commitsToDelete.size

        if (size > 0) {
            // First decref all files that had been referred to by
            // the now-deleted commits:

            var firstThrowable: Throwable? = null
            for (i in 0..<size) {
                val commit = commitsToDelete[i]
                if (infoStream.isEnabled("IFD")) {
                    infoStream.message(
                        "IFD", "deleteCommits: now decRef commit \"" + commit.segmentsFileName + "\""
                    )
                }
                try {
                    decRef(commit.fileNames)
                } catch (t: Throwable) {
                    firstThrowable = IOUtils.useOrSuppress(firstThrowable, t)
                }
            }
            commitsToDelete.clear()

            // Now compact commits to remove deleted ones (preserving the sort):
            size = commits.size
            var readFrom = 0
            var writeTo = 0
            while (readFrom < size) {
                val commit = commits[readFrom]
                if (!commit.isDeleted) {
                    if (writeTo != readFrom) {
                        commits[writeTo] = commits[readFrom]
                    }
                    writeTo++
                }
                readFrom++
            }

            while (size > writeTo) {
                commits.removeAt(size - 1)
                size--
            }

            if (firstThrowable != null) {
                throw IOUtils.rethrowAlways(firstThrowable)
            }
        }
    }

    /**
     * Writer calls this when it has hit an error and had to roll back, to tell us that there may now
     * be unreferenced files in the filesystem. So we re-list the filesystem and delete such files. If
     * segmentName is non-null, we will only delete files corresponding to that segment.
     */
    @Throws(IOException::class)
    fun refresh() {
        assert(locked())
        val toDelete: MutableSet<String> = HashSet()

        val files: Array<String> = directory.listAll()

        for (fileName in files) {
            if (!fileName.endsWith("write.lock") &&
                !fileDeleter.exists(fileName) &&
                (IndexFileNames.CODEC_FILE_PATTERN.matches(fileName)
                    || fileName.startsWith(IndexFileNames.SEGMENTS)
                    || fileName.startsWith(IndexFileNames.PENDING_SEGMENTS))
            ) {
                // Unreferenced file, so remove it
                if (infoStream.isEnabled("IFD")) {
                    infoStream.message(
                        "IFD", "refresh: removing newly created unreferenced file \"$fileName\""
                    )
                }
                toDelete.add(fileName)
            }
        }

        fileDeleter.deleteFilesIfNoRef(toDelete)
    }

    override fun close() {
        // DecRef old files from the last checkpoint, if any:
        assert(locked())

        if (!lastFiles.isEmpty()) {
            try {
                decRef(lastFiles)
            } finally {
                lastFiles.clear()
            }
        }
    }

    fun assertCommitsAreNotDeleted(commits: MutableList<CommitPoint>): Boolean {
        for (commit in commits) {
            assert(!commit.isDeleted) { "Commit [$commit] was deleted already" }
        }
        return true
    }

    /**
     * Revisits the [IndexDeletionPolicy] by calling its [ ][IndexDeletionPolicy.onCommit] again with the known commits. This is useful in cases where
     * a deletion policy which holds onto index commits is used. The application may know that some
     * commits are not held by the deletion policy anymore and call [ ][IndexWriter.deleteUnusedFiles], which will attempt to delete the unused commits again.
     */
    @Throws(IOException::class)
    fun revisitPolicy() {
        assert(locked())
        if (infoStream.isEnabled("IFD")) {
            infoStream.message("IFD", "now revisitPolicy")
        }

        if (commits.isNotEmpty()) {
            assert(assertCommitsAreNotDeleted(commits))
            policy.onCommit(commits)
            deleteCommits()
        }
    }

    /**
     * For definition of "check point" see IndexWriter comments: "Clarification: Check Points (and
     * commits)".
     *
     *
     * Writer calls this when it has made a "consistent change" to the index, meaning new files are
     * written to the index and the in-memory SegmentInfos have been modified to point to those files.
     *
     *
     * This may or may not be a commit (segments_N may or may not have been written).
     *
     *
     * We simply incref the files referenced by the new SegmentInfos and decref the files we had
     * previously seen (if any).
     *
     *
     * If this is a commit, we also call the policy to give it a chance to remove other commits. If
     * any commits are removed, we decref their files as well.
     */
    @OptIn(ExperimentalTime::class)
    @Throws(IOException::class)
    fun checkpoint(segmentInfos: SegmentInfos, isCommit: Boolean) {
        assert(locked())

        /*assert(java.lang.Thread.holdsLock(writer))*/ // TODO Thread is not available in Kotlin Multiplatform, need to think what to do here
        val t0: Instant = Clock.System.now() /*nanoTime()*/
        if (infoStream.isEnabled("IFD")) {
            infoStream.message(
                "IFD",
                ("now checkpoint \""
                        + writer.segString(writer.toLiveInfos(segmentInfos))
                        + "\" ["
                        + segmentInfos.size()
                        + " segments "
                        + "; isCommit = "
                        + isCommit
                        + "]")
            )
        }

        // Incref the files:
        incRef(segmentInfos, isCommit)

        if (isCommit) {
            // Append to our commits list:
            commits.add(CommitPoint(commitsToDelete, directoryOrig, segmentInfos))

            // Tell policy so it can remove commits:
            assert(assertCommitsAreNotDeleted(commits))
            policy.onCommit(commits)

            // Decref files for commits that were deleted by the policy:
            deleteCommits()
        } else {
            // DecRef old files from the last checkpoint, if any:
            try {
                decRef(lastFiles)
            } finally {
                lastFiles.clear()
            }

            // Save files so we can decr on next checkpoint/commit:
            lastFiles.addAll(segmentInfos.files(false))
        }

        if (infoStream.isEnabled("IFD")) {
            val t1: Instant = Clock.System.now()
            infoStream.message(
                "IFD",
                (t1 - t0).toString() + " ms to checkpoint"
            )
        }
    }

    private fun logInfo(msgType: FileDeleter.MsgType, msg: String) {
        if (msgType == FileDeleter.MsgType.REF) {
            // do not log anything
        } else {
            if (infoStream.isEnabled("IFD")) {
                infoStream.message("IFD", msg)
            }
        }
    }

    @Throws(IOException::class)
    fun incRef(segmentInfos: SegmentInfos, isCommit: Boolean) {
        assert(locked())
        // If this is a commit point, also incRef the
        // segments_N file:
        for (fileName in segmentInfos.files(isCommit)) {
            fileDeleter.incRef(fileName)
        }
    }

    fun incRef(files: MutableCollection<String>) {
        assert(locked())
        fileDeleter.incRef(files)
    }

    /** Decrefs all provided files, even on exception; throws first exception hit, if any.  */
    @Throws(IOException::class)
    fun decRef(files: MutableCollection<String>) {
        assert(locked())
        fileDeleter.decRef(files)
    }

    @Throws(IOException::class)
    fun decRef(segmentInfos: SegmentInfos) {
        assert(locked())
        decRef(segmentInfos.files(false))
    }

    fun exists(fileName: String): Boolean {
        assert(locked())
        return fileDeleter.exists(fileName)
    }

    /** Deletes the specified files, but only if they are new (have not yet been incref'd).  */
    @Throws(IOException::class)
    fun deleteNewFiles(files: MutableCollection<String>) {
        assert(locked())
        fileDeleter.deleteFilesIfNoRef(files)
    }

    /**
     * Holds details for each commit point. This class is also passed to the deletion policy. Note:
     * this class has a natural ordering that is inconsistent with equals.
     */
    class CommitPoint(
        var commitsToDelete: MutableCollection<CommitPoint>,
        var directoryOrig: Directory,
        segmentInfos: SegmentInfos
    ) : IndexCommit() {
        override var fileNames: MutableCollection<String> = segmentInfos.files(true).toMutableList()
        override var segmentsFileName: String = segmentInfos.segmentsFileName!!
        override var isDeleted: Boolean = false
        override var generation: Long = segmentInfos.generation
        override val userData: MutableMap<String, String> = segmentInfos.userData
        override val segmentCount: Int = segmentInfos.size()

        override fun toString(): String {
            return "IndexFileDeleter.CommitPoint($segmentsFileName)"
        }

        override val directory: Directory
            get() = directoryOrig

        /** Called only be the deletion policy, to remove this commit point from the index.  */
        override fun delete() {
            if (!this.isDeleted) {
                this.isDeleted = true
                commitsToDelete.add(this)
            }
        }
    }

    companion object {
        /**
         * Set all gens beyond what we currently see in the directory, to avoid double-write in cases
         * where the previous IndexWriter did not gracefully close/rollback (e.g. os/machine crashed or
         * lost power).
         */
        fun inflateGens(
            infos: SegmentInfos,
            files: MutableCollection<String>,
            infoStream: InfoStream
        ) {
            var maxSegmentGen = Long.Companion.MIN_VALUE
            var maxSegmentName = Long.Companion.MIN_VALUE

            // Confusingly, this is the union of liveDocs, field infos, doc values
            // (and maybe others, in the future) gens.  This is somewhat messy,
            // since it means DV updates will suddenly write to the next gen after
            // live docs' gen, for example, but we don't have the APIs to ask the
            // codec which file is which:
            val maxPerSegmentGen: MutableMap<String, Long> = HashMap()

            for (fileName in files) {
                if (fileName == IndexWriter.WRITE_LOCK_NAME) {
                    // do nothing
                } else if (fileName.startsWith(IndexFileNames.SEGMENTS)) {
                    try {
                        maxSegmentGen = max(
                            SegmentInfos.generationFromSegmentsFileName(fileName),
                            maxSegmentGen
                        )
                    } catch (ignore: NumberFormatException) {
                        // trash file: we have to handle this since we allow anything starting with 'segments'
                        // here
                    }
                } else if (fileName.startsWith(IndexFileNames.PENDING_SEGMENTS)) {
                    try {
                        maxSegmentGen = max(
                            SegmentInfos.generationFromSegmentsFileName(fileName.substring(8)),
                            maxSegmentGen
                        )
                    } catch (ignore: NumberFormatException) {
                        // trash file: we have to handle this since we allow anything starting with
                        // 'pending_segments' here
                    }
                } else {
                    val segmentName: String = IndexFileNames.parseSegmentName(fileName)
                    assert(segmentName.startsWith("_")) { "wtf file=$fileName" }

                    if (fileName.lowercase().endsWith(".tmp")) {
                        // A temp file: don't try to look at its gen
                        continue
                    }

                    maxSegmentName = max(maxSegmentName, segmentName.substring(1).toLong(Character.MAX_RADIX))

                    var curGen = maxPerSegmentGen[segmentName]
                    if (curGen == null) {
                        curGen = 0L
                    }

                    try {
                        curGen = max(curGen, IndexFileNames.parseGeneration(fileName))
                    } catch (ignore: NumberFormatException) {
                        // trash file: we have to handle this since codec regex is only so good
                    }
                    maxPerSegmentGen.put(segmentName, curGen)
                }
            }

            // Generation is advanced before write:
            infos.setNextWriteGeneration(max(infos.generation, maxSegmentGen))
            if (infos.counter < 1 + maxSegmentName) {
                if (infoStream.isEnabled("IFD")) {
                    infoStream.message(
                        "IFD",
                        ("init: inflate infos.counter to "
                                + (1 + maxSegmentName)
                                + " vs current="
                                + infos.counter)
                    )
                }
                infos.counter = 1 + maxSegmentName
            }

            for (info in infos) {
                val gen = checkNotNull(maxPerSegmentGen[info.info.name])
                val genLong = gen
                if (info.getNextWriteDelGen() < genLong + 1) {
                    if (infoStream.isEnabled("IFD")) {
                        infoStream.message(
                            "IFD",
                            ("init: seg="
                                    + info.info.name
                                    + " set nextWriteDelGen="
                                    + (genLong + 1)
                                    + " vs current="
                                    + info.getNextWriteDelGen())
                        )
                    }
                    info.setNextWriteDelGen(genLong + 1)
                }
                if (info.getNextWriteFieldInfosGen() < genLong + 1) {
                    if (infoStream.isEnabled("IFD")) {
                        infoStream.message(
                            "IFD",
                            ("init: seg="
                                    + info.info.name
                                    + " set nextWriteFieldInfosGen="
                                    + (genLong + 1)
                                    + " vs current="
                                    + info.getNextWriteFieldInfosGen())
                        )
                    }
                    info.setNextWriteFieldInfosGen(genLong + 1)
                }
                if (info.getNextWriteDocValuesGen() < genLong + 1) {
                    if (infoStream.isEnabled("IFD")) {
                        infoStream.message(
                            "IFD",
                            ("init: seg="
                                    + info.info.name
                                    + " set nextWriteDocValuesGen="
                                    + (genLong + 1)
                                    + " vs current="
                                    + info.getNextWriteDocValuesGen())
                        )
                    }
                    info.setNextWriteDocValuesGen(genLong + 1)
                }
            }
        }
    }
}
