package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.MergePolicy.OneMerge
import okio.EOFException
import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.jvm.JvmOverloads

/**
 * A collection of segmentInfo objects with methods for operating on those segments in relation to
 * the file system.
 *
 *
 * The active segments in the index are stored in the segment info file, `segments_N`.
 * There may be one or more `segments_N` files in the index; however, the one with the
 * largest generation is the active one (when older segments_N files are present it's because they
 * temporarily cannot be deleted, or a custom [IndexDeletionPolicy] is in use). This file
 * lists each segment by name and has details about the codec and generation of deletes.
 *
 *
 * Files:
 *
 *
 *  * `segments_N`: Header, LuceneVersion, Version, NameCounter, SegCount,
 * MinSegmentLuceneVersion, &lt;SegName, SegID, SegCodec, DelGen, DeletionCount,
 * FieldInfosGen, DocValuesGen, UpdatesFiles&gt;<sup>SegCount</sup>, CommitUserData, Footer
 *
 *
 * Data types:
 *
 *
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * LuceneVersion --&gt; Which Lucene code [Version] was used for this commit, written as
 * three [vInt][DataOutput.writeVInt]: major, minor, bugfix
 *  * MinSegmentLuceneVersion --&gt; Lucene code [Version] of the oldest segment, written
 * as three [vInt][DataOutput.writeVInt]: major, minor, bugfix; this is only written only
 * if there's at least one segment
 *  * NameCounter, SegCount, DeletionCount --&gt; [Int32][DataOutput.writeInt]
 *  * Generation, Version, DelGen, Checksum, FieldInfosGen, DocValuesGen --&gt; [       ][DataOutput.writeLong]
 *  * SegID --&gt; [Int8&lt;sup&gt;ID_LENGTH&lt;/sup&gt;][DataOutput.writeByte]
 *  * SegName, SegCodec --&gt; [String][DataOutput.writeString]
 *  * CommitUserData --&gt; [Map&amp;lt;String,String&amp;gt;][DataOutput.writeMapOfStrings]
 *  * UpdatesFiles --&gt; Map&lt;[Int32][DataOutput.writeInt], [       ][DataOutput.writeSetOfStrings]&gt;
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Field Descriptions:
 *
 *
 *  * Version counts how often the index has been changed by adding or deleting documents.
 *  * NameCounter is used to generate names for new segment files.
 *  * SegName is the name of the segment, and is used as the file name prefix for all of the
 * files that compose the segment's index.
 *  * DelGen is the generation count of the deletes file. If this is -1, there are no deletes.
 * Anything above zero means there are deletes stored by [LiveDocsFormat].
 *  * DeletionCount records the number of deleted documents in this segment.
 *  * SegCodec is the [name][Codec.getName] of the Codec that encoded this segment.
 *  * SegID is the identifier of the Codec that encoded this segment.
 *  * CommitUserData stores an optional user-supplied opaque Map&lt;String,String&gt; that was
 * passed to [IndexWriter.setLiveCommitData].
 *  * FieldInfosGen is the generation count of the fieldInfos file. If this is -1, there are no
 * updates to the fieldInfos in that segment. Anything above zero means there are updates to
 * fieldInfos stored by [FieldInfosFormat] .
 *  * DocValuesGen is the generation count of the updatable DocValues. If this is -1, there are
 * no updates to DocValues in that segment. Anything above zero means there are updates to
 * DocValues stored by [DocValuesFormat].
 *  * UpdatesFiles stores the set of files that were updated in that segment per field.
 *
 *
 * @lucene.experimental
 */
class SegmentInfos(indexCreatedVersionMajor: Int) : Cloneable, Iterable<SegmentCommitInfo> {
    /** Used to name new segments.  */
    var counter: Long = 0

    /** Counts how often the index has been changed.  */
    var version: Long = 0

    /** Returns current generation.  */
    var generation: Long = 0 // generation of the "segments_N" for the next commit
        private set

    /** Returns last succesfully read or written generation.  */
    var lastGeneration: Long = 0 // generation of the "segments_N" file we last successfully read
        private set

    // or wrote; this is normally the same as generation except if
    // there was an IOException that had interrupted a commit
    /** Opaque Map&lt;String, String&gt; that user can specify during IndexWriter.commit  */
    var userData: MutableMap<String, String> = mutableMapOf<String, String>()

    private var segments: MutableList<SegmentCommitInfo> =
        ArrayList<SegmentCommitInfo>()

    /** Id for this commit; only written starting with Lucene 5.0  */
    private lateinit var id: ByteArray

    /** Which Lucene version wrote this commit.  */
    private var luceneVersion: Version? = null

    /** Version of the oldest segment in the index, or null if there are no segments.  */
    private var minSegmentLuceneVersion: Version? = null

    /**
     * Return the version major that was used to initially create the index. This version is set when
     * the index is first created and then never changes. This information was added as of version 7.0
     * so older indices report 6 as a creation version.
     */
    /** The Lucene version major that was used to create the index.  */
    val indexCreatedVersionMajor: Int

    /** Returns [SegmentCommitInfo] at the provided index.  */
    fun info(i: Int): SegmentCommitInfo {
        return segments.get(i)
    }

    val segmentsFileName: String?
        /** Get the segments_N filename in use by this segment infos.  */
        get() = IndexFileNames.fileNameFromGeneration(
            IndexFileNames.SEGMENTS,
            "",
            lastGeneration
        )

    private val nextPendingGeneration: Long
        /** return generation of the next pending_segments_N that will be written  */
        get() {
            if (generation == -1L) {
                return 1
            } else {
                return generation + 1
            }
        }

    /** Since Lucene 5.0, every commit (segments_N) writes a unique id. This will return that id  */
    fun getId(): ByteArray {
        return id.clone()
    }

    // Only true after prepareCommit has been called and
    // before finishCommit is called
    var pendingCommit: Boolean = false

    /**
     * Sole constructor.
     *
     * @param indexCreatedVersionMajor the Lucene version major at index creation time, or 6 if the
     * index was created before 7.0
     */
    init {
        require(indexCreatedVersionMajor <= Version.LATEST.major) { "indexCreatedVersionMajor is in the future: $indexCreatedVersionMajor" }
        require(indexCreatedVersionMajor >= 6) { "indexCreatedVersionMajor must be >= 6, got: $indexCreatedVersionMajor" }
        this.indexCreatedVersionMajor = indexCreatedVersionMajor
    }

    @Throws(IOException::class)
    private fun write(directory: Directory) {
        val nextGeneration = this.nextPendingGeneration
        val segmentFileName: String? =
            IndexFileNames.fileNameFromGeneration(
                IndexFileNames.PENDING_SEGMENTS,
                "",
                nextGeneration
            )

        // Always advance the generation on write:
        generation = nextGeneration

        var segnOutput: IndexOutput? = null
        var success = false

        try {
            segnOutput = directory.createOutput(segmentFileName!!, IOContext.DEFAULT)
            write(segnOutput)
            segnOutput.close()
            directory.sync(mutableSetOf<String>(segmentFileName))
            success = true
        } finally {
            if (success) {
                pendingCommit = true
            } else {
                // We hit an exception above; try to close the file
                // but suppress any exception:
                IOUtils.closeWhileHandlingException(segnOutput)
                // Try not to leave a truncated segments_N file in
                // the index:
                IOUtils.deleteFilesIgnoringExceptions(directory, segmentFileName!!)
            }
        }
    }

    /** Write ourselves to the provided [IndexOutput]  */
    @Throws(IOException::class)
    fun write(out: IndexOutput) {
        CodecUtil.writeIndexHeader(
            out,
            "segments",
            VERSION_CURRENT,
            StringHelper.randomId(),
            generation.toString(Character.MAX_RADIX.coerceIn(2, 36))
        )
        out.writeVInt(Version.LATEST.major)
        out.writeVInt(Version.LATEST.minor)
        out.writeVInt(Version.LATEST.bugfix)

        // System.out.println(Thread.currentThread().getName() + ": now write " + out.getName() + " with
        // version=" + version);
        out.writeVInt(indexCreatedVersionMajor)

        CodecUtil.writeBELong(out, version)
        out.writeVLong(counter) // write counter
        CodecUtil.writeBEInt(out, size())

        if (size() > 0) {
            var minSegmentVersion: Version? = null

            // We do a separate loop up front so we can write the minSegmentVersion before
            // any SegmentInfo; this makes it cleaner to throw IndexFormatTooOldExc at read time:
            for (siPerCommit in this) {
                val segmentVersion: Version = siPerCommit.info.getVersion()
                if (minSegmentVersion == null || segmentVersion.onOrAfter(minSegmentVersion) == false) {
                    minSegmentVersion = segmentVersion
                }
            }

            out.writeVInt(minSegmentVersion!!.major)
            out.writeVInt(minSegmentVersion.minor)
            out.writeVInt(minSegmentVersion.bugfix)
        }

        // write infos
        for (siPerCommit in this) {
            val si: SegmentInfo = siPerCommit.info
            check(!(indexCreatedVersionMajor >= 7 && si.minVersion == null)) {
                ("Segments must record minVersion if they have been created on or after Lucene 7: "
                        + si)
            }
            out.writeString(si.name)
            val segmentID: ByteArray = si.getId()
            check(segmentID.size == StringHelper.ID_LENGTH) {
                ("cannot write segment: invalid id segment="
                        + si.name
                        + "id="
                        + StringHelper.idToString(segmentID))
            }
            out.writeBytes(segmentID, segmentID.size)
            out.writeString(si.getCodec().name)

            CodecUtil.writeBELong(out, siPerCommit.delGen)
            val delCount: Int = siPerCommit.getDelCount()
            check(!(delCount < 0 || delCount > si.maxDoc())) {
                ("cannot write segment: invalid maxDoc segment="
                        + si.name
                        + " maxDoc="
                        + si.maxDoc()
                        + " delCount="
                        + delCount)
            }
            CodecUtil.writeBEInt(out, delCount)
            CodecUtil.writeBELong(out, siPerCommit.fieldInfosGen)
            CodecUtil.writeBELong(out, siPerCommit.docValuesGen)
            val softDelCount: Int = siPerCommit.getSoftDelCount()
            check(!(softDelCount < 0 || softDelCount > si.maxDoc())) {
                ("cannot write segment: invalid maxDoc segment="
                        + si.name
                        + " maxDoc="
                        + si.maxDoc()
                        + " softDelCount="
                        + softDelCount)
            }
            CodecUtil.writeBEInt(out, softDelCount)
            // we ensure that there is a valid ID for this SCI just in case
            // this is manually upgraded outside of IW
            val sciId: ByteArray? = siPerCommit.getId()
            if (sciId != null) {
                out.writeByte(1.toByte())
                assert(
                    sciId.size == StringHelper.ID_LENGTH
                ) { "invalid SegmentCommitInfo#id: " + sciId.contentToString() }
                out.writeBytes(sciId, 0, sciId.size)
            } else {
                out.writeByte(0.toByte())
            }

            out.writeSetOfStrings(siPerCommit.getFieldInfosFiles())
            val dvUpdatesFiles: MutableMap<Int, MutableSet<String>> = siPerCommit.docValuesUpdatesFiles
            CodecUtil.writeBEInt(out, dvUpdatesFiles.size)
            for (e in dvUpdatesFiles.entries) {
                CodecUtil.writeBEInt(out, e.key)
                out.writeSetOfStrings(e.value)
            }
        }
        out.writeMapOfStrings(userData)
        CodecUtil.writeFooter(out)
    }

    /** Returns a copy of this instance, also copying each SegmentInfo.  */
    public override fun clone(): SegmentInfos {
        try {
            val sis = /*super.clone() as SegmentInfos*/ SegmentInfos(indexCreatedVersionMajor)
            // deep clone, first recreate all collections:
            sis.segments = ArrayList<SegmentCommitInfo>(size())
            for (info in this) {
                checkNotNull(info.info.getCodec())
                // dont directly access segments, use add method!!!
                sis.add(info.clone())
            }
            sis.userData = HashMap<String, String>(userData)
            return sis
        } catch (e: /*CloneNotSupported*/Exception) {
            throw RuntimeException("should not happen", e)
        }
    }

    /** version number when this SegmentInfos was generated.  */
    fun getVersion(): Long {
        return version
    }

    /**
     * Utility class for executing code that needs to do something with the current segments file.
     * This is necessary with lock-less commits because from the time you locate the current segments
     * file name, until you actually open it, read its contents, or check modified time, etc., it
     * could have been deleted due to a writer commit finishing.
     */
    abstract class FindSegmentsFile<T> protected constructor(val directory: Directory) {

        /** Run [.doBody] on the provided commit.  */
        /** Locate the most recent `segments` file and run [.doBody] on it.  */
        @JvmOverloads
        @Throws(IOException::class)
        fun run(commit: IndexCommit? = null): T {
            if (commit != null) {
                if (directory !== commit.directory) throw IOException("the specified commit does not match the specified Directory")
                return doBody(commit.segmentsFileName!!)
            }

            var lastGen: Long = -1
            var gen: Long = -1
            var exc: IOException? = null

            // Loop until we succeed in calling doBody() without
            // hitting an IOException.  An IOException most likely
            // means an IW deleted our commit while opening
            // the time it took us to load the now-old infos files
            // (and segments files).  It's also possible it's a
            // true error (corrupt index).  To distinguish these,
            // on each retry we must see "forward progress" on
            // which generation we are trying to load.  If we
            // don't, then the original error is real and we throw
            // it.
            while (true) {
                lastGen = gen
                val files: Array<String> = directory.listAll()
                val files2: Array<String> = directory.listAll()
                Arrays.sort(files)
                Arrays.sort(files2)
                if (!files.contentEquals(files2)) {
                    // listAll() is weakly consistent, this means we hit "concurrent modification exception"
                    continue
                }
                gen = Companion.getLastCommitGeneration(files)

                if (infoStream != null) {
                    message("directory listing gen=$gen")
                }

                if (gen == -1L) {
                    throw IndexNotFoundException(
                        "no segments* file found in " + directory + ": files: " + files.contentToString()
                    )
                } else if (gen > lastGen) {
                    val segmentFileName: String? =
                        IndexFileNames.fileNameFromGeneration(
                            IndexFileNames.SEGMENTS,
                            "",
                            gen
                        )

                    try {
                        val t = doBody(segmentFileName!!)
                        if (infoStream != null) {
                            message("success on $segmentFileName")
                        }
                        return t
                    } catch (err: IOException) {
                        // Save the original root cause:
                        if (exc == null) {
                            exc = err
                        }

                        if (infoStream != null) {
                            message(
                                ("primary Exception on '"
                                        + segmentFileName
                                        + "': "
                                        + err
                                        + "'; will retry: gen = "
                                        + gen)
                            )
                        }
                    }
                } else {
                    throw exc as Throwable
                }
            }
        }

        /**
         * Subclass must implement this. The assumption is an IOException will be thrown if something
         * goes wrong during the processing that could have been caused by a writer committing.
         */
        @Throws(IOException::class)
        protected abstract fun doBody(segmentFileName: String): T
    }

    /**
     * Carry over generation numbers from another SegmentInfos
     *
     * @lucene.internal
     */
    fun updateGeneration(other: SegmentInfos) {
        lastGeneration = other.lastGeneration
        generation = other.generation
    }

    // Carry over generation numbers, and version/counter, from another SegmentInfos
    fun updateGenerationVersionAndCounter(other: SegmentInfos) {
        updateGeneration(other)
        this.version = other.version
        this.counter = other.counter
    }

    /** Set the generation to be used for the next commit  */
    fun setNextWriteGeneration(generation: Long) {
        check(generation >= this.generation) {
            ("cannot decrease generation to "
                    + generation
                    + " from current generation "
                    + this.generation)
        }
        this.generation = generation
    }

    fun rollbackCommit(dir: Directory) {
        if (pendingCommit) {
            pendingCommit = false

            // we try to clean up our pending_segments_N

            // Must carefully compute fileName from "generation"
            // since lastGeneration isn't incremented:
            val pending: String? =
                IndexFileNames.fileNameFromGeneration(
                    IndexFileNames.PENDING_SEGMENTS,
                    "",
                    generation
                )
            // Suppress so we keep throwing the original exception
            // in our caller
            IOUtils.deleteFilesIgnoringExceptions(dir, pending!!)
        }
    }

    /**
     * Call this to start a commit. This writes the new segments file, but writes an invalid checksum
     * at the end, so that it is not visible to readers. Once this is called you must call [ ][.finishCommit] to complete the commit or [.rollbackCommit] to abort it.
     *
     *
     * Note: [.changed] should be called prior to this method if changes have been made to
     * this [SegmentInfos] instance
     */
    @Throws(IOException::class)
    fun prepareCommit(dir: Directory) {
        check(!pendingCommit) { "prepareCommit was already called" }
        dir.syncMetaData()
        write(dir)
    }

    /**
     * Returns all file names referenced by SegmentInfo. The returned collection is recomputed on each
     * invocation.
     */
    @Throws(IOException::class)
    fun files(includeSegmentsFile: Boolean): MutableCollection<String> {
        val files = HashSet<String>()
        if (includeSegmentsFile) {
            val segmentFileName = this.segmentsFileName
            if (segmentFileName != null) {
                files.add(segmentFileName)
            }
        }
        val size = size()
        for (i in 0..<size) {
            val info: SegmentCommitInfo = info(i)
            files.addAll(info.files())
        }

        return files
    }

    /** Returns the committed segments_N filename.  */
    @Throws(IOException::class)
    fun finishCommit(dir: Directory): String {
        check(pendingCommit != false) { "prepareCommit was not called" }
        var successRenameAndSync = false
        val dest: String?
        try {
            val src: String? =
                IndexFileNames.fileNameFromGeneration(
                    IndexFileNames.PENDING_SEGMENTS,
                    "",
                    generation
                )
            dest = IndexFileNames.fileNameFromGeneration(
                IndexFileNames.SEGMENTS,
                "",
                generation
            )
            dir.rename(src!!, dest!!)
            try {
                dir.syncMetaData()
                successRenameAndSync = true
            } finally {
                if (successRenameAndSync == false) {
                    // at this point we already created the file but missed to sync directory let's also
                    // remove the
                    // renamed file
                    IOUtils.deleteFilesIgnoringExceptions(dir, dest)
                }
            }
        } finally {
            if (successRenameAndSync == false) {
                // deletes pending_segments_N:
                rollbackCommit(dir)
            }
        }

        pendingCommit = false
        lastGeneration = generation
        return dest
    }

    /**
     * Writes and syncs to the Directory dir, taking care to remove the segments file on exception
     *
     *
     * Note: [.changed] should be called prior to this method if changes have been made to
     * this [SegmentInfos] instance
     */
    @Throws(IOException::class)
    fun commit(dir: Directory) {
        prepareCommit(dir)
        finishCommit(dir)
    }

    /** Returns readable description of this segment.  */
    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append(this.segmentsFileName).append(": ")
        val count = size()
        for (i in 0..<count) {
            if (i > 0) {
                buffer.append(' ')
            }
            val info: SegmentCommitInfo = info(i)
            buffer.append(info.toString(0))
        }
        return buffer.toString()
    }

    /**
     * Return `userData` saved with this commit.
     *
     * @see IndexWriter.commit
     */
    fun getUserData(): MutableMap<String, String> {
        return userData!!
    }

    /** Sets the commit data.  */
    fun setUserData(data: MutableMap<String, String>, doIncrementVersion: Boolean) {
        if (data == null) {
            userData = mutableMapOf<String, String>()
        } else {
            userData = data
        }
        if (doIncrementVersion) {
            changed()
        }
    }

    /**
     * Replaces all segments in this instance, but keeps generation, version, counter so that future
     * commits remain write once.
     */
    fun replace(other: SegmentInfos) {
        rollbackSegmentInfos(other.asList())
        lastGeneration = other.lastGeneration
        userData = other.userData
    }

    /** Returns sum of all segment's maxDocs. Note that this does not include deletions  */
    fun totalMaxDoc(): Int {
        var count: Long = 0
        for (info in this) {
            count += info.info.maxDoc().toLong()
        }
        // we should never hit this, checks should happen elsewhere...
        assert(count <= IndexWriter.actualMaxDocs)
        return Math.toIntExact(count)
    }

    /** Call this before committing if changes have been made to the segments.  */
    fun changed() {
        version++
        // System.out.println(Thread.currentThread().getName() + ": SIS.change to version=" + version);
        // new Throwable().printStackTrace(System.out);
    }

    fun setVersion(newVersion: Long) {
        require(newVersion >= version) {
            ("newVersion (="
                    + newVersion
                    + ") cannot be less than current version (="
                    + version
                    + ")")
        }
        // System.out.println(Thread.currentThread().getName() + ": SIS.setVersion change from " +
        // version + " to " + newVersion);
        version = newVersion
    }

    /** applies all changes caused by committing a merge to this SegmentInfos  */
    fun applyMergeChanges(merge: OneMerge, dropSegment: Boolean) {
        require(!(indexCreatedVersionMajor >= 7 && merge.info.info.minVersion == null)) { "All segments must record the minVersion for indices created on or after Lucene 7" }

        val mergedAway: MutableSet<SegmentCommitInfo> =
            HashSet<SegmentCommitInfo>(merge.segments)
        var inserted = false
        var newSegIdx = 0
        var segIdx = 0
        val cnt = segments.size
        while (segIdx < cnt) {
            assert(segIdx >= newSegIdx)
            val info: SegmentCommitInfo = segments.get(segIdx)
            if (mergedAway.contains(info)) {
                if (!inserted && !dropSegment) {
                    segments.set(segIdx, merge.info)
                    inserted = true
                    newSegIdx++
                }
            } else {
                segments.set(newSegIdx, info)
                newSegIdx++
            }
            segIdx++
        }

        // the rest of the segments in list are duplicates, so don't remove from map, only list!
        segments.subList(newSegIdx, segments.size).clear()

        // Either we found place to insert segment, or, we did
        // not, but only because all segments we merged becamee
        // deleted while we are merging, in which case it should
        // be the case that the new segment is also all deleted,
        // we insert it at the beginning if it should not be dropped:
        if (!inserted && !dropSegment) {
            segments.add(0, merge.info)
        }
    }

    fun createBackupSegmentInfos(): MutableList<SegmentCommitInfo> {
        val list: MutableList<SegmentCommitInfo> =
            ArrayList<SegmentCommitInfo>(size())
        for (info in this) {
            checkNotNull(info.info.getCodec())
            list.add(info.clone())
        }
        return list
    }

    fun rollbackSegmentInfos(infos: MutableList<SegmentCommitInfo>) {
        this.clear()
        this.addAll(infos)
    }

    /** Returns an **unmodifiable** [Iterator] of contained segments in order.  */ // @Override (comment out until Java 6)
    override fun iterator(): MutableIterator<SegmentCommitInfo> {
        return asList().iterator()
    }

    /** Returns all contained segments as an **unmodifiable** [List] view.  */
    fun asList(): MutableList<SegmentCommitInfo> {
        return /*java.util.Collections.unmodifiableList<SegmentCommitInfo>(segments)*/ segments.toMutableList()
    }

    /** Returns number of [SegmentCommitInfo]s.  */
    fun size(): Int {
        return segments.size
    }

    /** Appends the provided [SegmentCommitInfo].  */
    fun add(si: SegmentCommitInfo) {
        require(!(indexCreatedVersionMajor >= 7 && si.info.minVersion == null)) { "All segments must record the minVersion for indices created on or after Lucene 7" }

        segments.add(si)
    }

    /** Appends the provided [SegmentCommitInfo]s.  */
    fun addAll(sis: Iterable<SegmentCommitInfo>) {
        for (si in sis) {
            this.add(si)
        }
    }

    /** Clear all [SegmentCommitInfo]s.  */
    fun clear() {
        segments.clear()
    }

    /**
     * Remove the provided [SegmentCommitInfo].
     *
     *
     * **WARNING**: O(N) cost
     */
    fun remove(si: SegmentCommitInfo): Boolean {
        return segments.remove(si)
    }

    /**
     * Remove the [SegmentCommitInfo] at the provided index.
     *
     *
     * **WARNING**: O(N) cost
     */
    fun remove(index: Int) {
        segments.removeAt(index)
    }

    /**
     * Return true if the provided [SegmentCommitInfo] is contained.
     *
     *
     * **WARNING**: O(N) cost
     */
    fun contains(si: SegmentCommitInfo): Boolean {
        return segments.contains(si)
    }

    /**
     * Returns index of the provided [SegmentCommitInfo].
     *
     *
     * **WARNING**: O(N) cost
     */
    fun indexOf(si: SegmentCommitInfo): Int {
        return segments.indexOf(si)
    }

    val commitLuceneVersion: Version?
        /**
         * Returns which Lucene [Version] wrote this commit, or null if the version this index was
         * written with did not directly record the version.
         */
        get() = luceneVersion

    /** Returns the version of the oldest segment, or null if there are no segments.  */
    fun getMinSegmentLuceneVersion(): Version? {
        return minSegmentLuceneVersion
    }

    companion object {
        /** The version at the time when 8.0 was released.  */
        const val VERSION_74: Int = 9

        /** The version that recorded SegmentCommitInfo IDs  */
        const val VERSION_86: Int = 10

        val VERSION_CURRENT: Int = VERSION_86

        /** Name of the generation reference file name  */
        const val OLD_SEGMENTS_GEN: String = "segments.gen"

        /**
         * If non-null, information about loading segments_N files will be printed here.
         *
         * @see .setInfoStream
         */
        private var infoStream: PrintStream? = null

        /**
         * Get the generation of the most recent commit to the list of index files (N in the segments_N
         * file).
         *
         * @param files -- array of file names to check
         */
        fun getLastCommitGeneration(files: Array<String>): Long {
            var max: Long = -1
            for (file in files) {
                if (file.startsWith(IndexFileNames.SEGMENTS)
                    &&  // skipping this file here helps deliver the right exception when opening an old index
                    file.startsWith(OLD_SEGMENTS_GEN) == false
                ) {
                    val gen = generationFromSegmentsFileName(file)
                    if (gen > max) {
                        max = gen
                    }
                }
            }
            return max
        }

        /**
         * Get the generation of the most recent commit to the index in this directory (N in the
         * segments_N file).
         *
         * @param directory -- directory to search for the latest segments_N file
         */
        @Throws(IOException::class)
        fun getLastCommitGeneration(directory: Directory): Long {
            return Companion.getLastCommitGeneration(directory.listAll())
        }

        /**
         * Get the filename of the segments_N file for the most recent commit in the list of index files.
         *
         * @param files -- array of file names to check
         */
        fun getLastCommitSegmentsFileName(files: Array<String>): String? {
            return IndexFileNames.fileNameFromGeneration(
                IndexFileNames.SEGMENTS, "", getLastCommitGeneration(files)
            )
        }

        /**
         * Get the filename of the segments_N file for the most recent commit to the index in this
         * Directory.
         *
         * @param directory -- directory to search for the latest segments_N file
         */
        @Throws(IOException::class)
        fun getLastCommitSegmentsFileName(directory: Directory): String? {
            return IndexFileNames.fileNameFromGeneration(
                IndexFileNames.SEGMENTS, "", getLastCommitGeneration(directory)
            )
        }

        /** Parse the generation off the segments file name and return it.  */
        fun generationFromSegmentsFileName(fileName: String): Long {
            require(fileName != OLD_SEGMENTS_GEN) { "\"$OLD_SEGMENTS_GEN\" is not a valid segment file name since 4.0" }
            if (fileName == IndexFileNames.SEGMENTS) {
                return 0
            } else if (fileName.startsWith(IndexFileNames.SEGMENTS)) {
                return fileName.substring(1 + IndexFileNames.SEGMENTS.length)
                    .toLong(Character.MAX_RADIX)
            } else {
                throw IllegalArgumentException("fileName \"$fileName\" is not a segments file")
            }
        }

        /**
         * Read a particular segmentFileName, as long as the commit's [ ][SegmentInfos.getIndexCreatedVersionMajor] is strictly greater than the provided minimum
         * supported major version. If the commit's version is older, an [ ] will be thrown. Note that this may throw an IOException if a commit
         * is in process.
         */
        /**
         * Read a particular segmentFileName. Note that this may throw an IOException if a commit is in
         * process.
         *
         * @param directory -- directory containing the segments file
         * @param segmentFileName -- segment file to load
         * @throws CorruptIndexException if the index is corrupt
         * @throws IOException if there is a low-level IO error
         */
        @JvmOverloads
        @Throws(IOException::class)
        fun readCommit(
            directory: Directory,
            segmentFileName: String,
            minSupportedMajorVersion: Int = Version.MIN_SUPPORTED_MAJOR
        ): SegmentInfos {
            val generation = generationFromSegmentsFileName(segmentFileName)
            directory.openChecksumInput(segmentFileName).use { input ->
                try {
                    return readCommit(directory, input, generation, minSupportedMajorVersion)
                } catch (e: EOFException) {
                    throw CorruptIndexException(
                        "Unexpected file read error while reading index.", input, e
                    )
                } catch (e: NoSuchFileException) {
                    throw CorruptIndexException(
                        "Unexpected file read error while reading index.", input, e
                    )
                } catch (e: FileNotFoundException) {
                    throw CorruptIndexException(
                        "Unexpected file read error while reading index.", input, e
                    )
                }
            }
        }

        /** Read the commit from the provided [ChecksumIndexInput].  */
        /** Read the commit from the provided [ChecksumIndexInput].  */
        @JvmOverloads
        @Throws(IOException::class)
        fun readCommit(
            directory: Directory,
            input: ChecksumIndexInput,
            generation: Long,
            minSupportedMajorVersion: Int = Version.MIN_SUPPORTED_MAJOR
        ): SegmentInfos {
            var priorE: Throwable? = null
            var format = -1
            try {
                // NOTE: as long as we want to throw indexformattooold (vs corruptindexexception), we need
                // to read the magic ourselves.
                val magic: Int = CodecUtil.readBEInt(input)
                if (magic != CodecUtil.CODEC_MAGIC) {
                    throw IndexFormatTooOldException(
                        input,
                        magic,
                        CodecUtil.CODEC_MAGIC,
                        CodecUtil.CODEC_MAGIC
                    )
                }
                format = CodecUtil.checkHeaderNoMagic(
                    input,
                    "segments",
                    VERSION_74,
                    VERSION_CURRENT
                )
                val id = ByteArray(StringHelper.ID_LENGTH)
                input.readBytes(id, 0, id.size)
                CodecUtil.checkIndexHeaderSuffix(
                    input,
                    generation.toString(Character.MAX_RADIX.coerceIn(2, 36))
                )

                val luceneVersion: Version =
                    Version.fromBits(input.readVInt(), input.readVInt(), input.readVInt())
                val indexCreatedVersion: Int = input.readVInt()
                if (luceneVersion.major < indexCreatedVersion) {
                    throw CorruptIndexException(
                        ("Creation version ["
                                + indexCreatedVersion
                                + ".x] can't be greater than the version that wrote the segment infos: ["
                                + luceneVersion
                                + "]"),
                        input
                    )
                }

                if (indexCreatedVersion < minSupportedMajorVersion) {
                    throw IndexFormatTooOldException(
                        input,
                        ("This index was initially created with Lucene "
                                + indexCreatedVersion
                                + ".x while the current version is "
                                + Version.LATEST
                                + " and Lucene only supports reading"
                                + (if (minSupportedMajorVersion == Version.MIN_SUPPORTED_MAJOR)
                            " the current and previous major versions"
                        else
                            " from version " + minSupportedMajorVersion + " upwards"))
                    )
                }

                val infos = SegmentInfos(indexCreatedVersion)
                infos.id = id
                infos.generation = generation
                infos.lastGeneration = generation
                infos.luceneVersion = luceneVersion
                parseSegmentInfos(directory, input, infos, format)
                return infos
            } catch (t: Throwable) {
                priorE = t
            } finally {
                if (format >= VERSION_74) { // oldest supported version
                    CodecUtil.checkFooter(input, priorE)
                } else {
                    throw IOUtils.rethrowAlways(priorE!!)
                }
            }
            throw Error("Unreachable code")
        }

        @Throws(IOException::class)
        private fun parseSegmentInfos(
            directory: Directory,
            input: DataInput,
            infos: SegmentInfos,
            format: Int
        ) {
            infos.version = CodecUtil.readBELong(input)
            // System.out.println("READ sis version=" + infos.version);
            infos.counter = input.readVLong()
            val numSegments: Int = CodecUtil.readBEInt(input)
            if (numSegments < 0) {
                throw CorruptIndexException("invalid segment count: " + numSegments, input)
            }

            if (numSegments > 0) {
                infos.minSegmentLuceneVersion =
                    Version.fromBits(input.readVInt(), input.readVInt(), input.readVInt())
            } else {
                // else leave as null: no segments
            }

            var totalDocs: Long = 0
            for (seg in 0..<numSegments) {
                val segName: String = input.readString()
                val segmentID = ByteArray(StringHelper.ID_LENGTH)
                input.readBytes(segmentID, 0, segmentID.size)
                val codec: Codec = readCodec(input)
                val info: SegmentInfo =
                    codec.segmentInfoFormat()
                        .read(directory, segName, segmentID, IOContext.DEFAULT)
                info.setCodec(codec)
                totalDocs += info.maxDoc().toLong()
                val delGen: Long = CodecUtil.readBELong(input)
                val delCount: Int = CodecUtil.readBEInt(input)
                if (delCount < 0 || delCount > info.maxDoc()) {
                    throw CorruptIndexException(
                        "invalid deletion count: " + delCount + " vs maxDoc=" + info.maxDoc(), input
                    )
                }
                val fieldInfosGen: Long = CodecUtil.readBELong(input)
                val dvGen: Long = CodecUtil.readBELong(input)
                val softDelCount: Int = CodecUtil.readBEInt(input)
                if (softDelCount < 0 || softDelCount > info.maxDoc()) {
                    throw CorruptIndexException(
                        "invalid deletion count: " + softDelCount + " vs maxDoc=" + info.maxDoc(), input
                    )
                }
                if (softDelCount + delCount > info.maxDoc()) {
                    throw CorruptIndexException(
                        "invalid deletion count: " + (softDelCount + delCount) + " vs maxDoc=" + info.maxDoc(),
                        input
                    )
                }
                val sciId: ByteArray?
                if (format > VERSION_74) {
                    val marker: Byte = input.readByte()
                    when (marker) {
                        1 -> {
                            sciId = ByteArray(StringHelper.ID_LENGTH)
                            input.readBytes(sciId, 0, sciId.size)
                        }

                        0 -> sciId = null
                        else -> throw CorruptIndexException(
                            "invalid SegmentCommitInfo ID marker: $marker", input
                        )
                    }
                } else {
                    sciId = null
                }
                val siPerCommit = SegmentCommitInfo(
                    info,
                    delCount,
                    softDelCount,
                    delGen,
                    fieldInfosGen,
                    dvGen,
                    sciId
                )
                siPerCommit.setFieldInfosFiles(input.readSetOfStrings())
                val dvUpdateFiles: MutableMap<Int, MutableSet<String>>
                val numDVFields: Int = CodecUtil.readBEInt(input)
                if (numDVFields == 0) {
                    dvUpdateFiles = mutableMapOf<Int, MutableSet<String>>()
                } else {
                    val map: MutableMap<Int, MutableSet<String>> =
                        CollectionUtil.newHashMap(numDVFields)
                    for (i in 0..<numDVFields) {
                        map.put(CodecUtil.readBEInt(input), input.readSetOfStrings())
                    }
                    dvUpdateFiles = /*java.util.Collections.unmodifiableMap<Int, MutableSet<String>>(map)*/ map.toMutableMap()
                }
                siPerCommit.docValuesUpdatesFiles = dvUpdateFiles
                infos.add(siPerCommit)

                val segmentVersion: Version = info.getVersion()

                if (segmentVersion.onOrAfter(infos.minSegmentLuceneVersion!!) == false) {
                    throw CorruptIndexException(
                        ("segments file recorded minSegmentLuceneVersion="
                                + infos.minSegmentLuceneVersion
                                + " but segment="
                                + info
                                + " has older version="
                                + segmentVersion),
                        input
                    )
                }

                if (infos.indexCreatedVersionMajor >= 7
                    && segmentVersion.major < infos.indexCreatedVersionMajor
                ) {
                    throw CorruptIndexException(
                        ("segments file recorded indexCreatedVersionMajor="
                                + infos.indexCreatedVersionMajor
                                + " but segment="
                                + info
                                + " has older version="
                                + segmentVersion),
                        input
                    )
                }

                if (infos.indexCreatedVersionMajor >= 7 && info.minVersion == null) {
                    throw CorruptIndexException(
                        "segments infos must record minVersion with indexCreatedVersionMajor="
                                + infos.indexCreatedVersionMajor,
                        input
                    )
                }
            }

            infos.userData = input.readMapOfStrings()

            // LUCENE-6299: check we are in bounds
            if (totalDocs > IndexWriter.actualMaxDocs) {
                throw CorruptIndexException(
                    ("Too many documents: an index cannot exceed "
                            + IndexWriter.actualMaxDocs
                            + " but readers have total maxDoc="
                            + totalDocs),
                    input
                )
            }
        }

        @Throws(IOException::class)
        private fun readCodec(input: DataInput): Codec {
            val name: String = input.readString()
            try {
                return Codec.forName(name)
            } catch (e: IllegalArgumentException) {
                // maybe it's an old default codec that moved
                if (name.startsWith("Lucene")) {
                    throw IllegalArgumentException(
                        ("Could not load codec '"
                                + name
                                + "'. Did you forget to add lucene-backward-codecs.jar"),
                        e
                    )
                }
                throw e
            }
        }

        /**
         * Find the latest commit (`segments_N file`) and load all [SegmentCommitInfo]s, as
         * long as the commit's [SegmentInfos.getIndexCreatedVersionMajor] is strictly greater
         * than the provided minimum supported major version. If the commit's version is older, an [ ] will be thrown.
         */
        /** Find the latest commit (`segments_N file`) and load all [SegmentCommitInfo]s.  */
        @JvmOverloads
        @Throws(IOException::class)
        fun readLatestCommit(
            directory: Directory,
            minSupportedMajorVersion: Int = Version.MIN_SUPPORTED_MAJOR
        ): SegmentInfos {
            return object : FindSegmentsFile<SegmentInfos>(directory) {
                @Throws(IOException::class)
                override fun doBody(segmentFileName: String): SegmentInfos {
                    return readCommit(directory, segmentFileName, minSupportedMajorVersion)
                }
            }.run()
        }

        /**
         * If non-null, information about retries when loading the segments file will be printed to this.
         */
        fun setInfoStream(infoStream: PrintStream) {
            Companion.infoStream = infoStream
        }

        /**
         * Returns `infoStream`.
         *
         * @see .setInfoStream
         */
        fun getInfoStream(): PrintStream {
            return infoStream!!
        }

        /**
         * Prints the given message to the infoStream. Note, this method does not check for null
         * infoStream. It assumes this check has been performed by the caller, which is recommended to
         * avoid the (usually) expensive message creation.
         */
        private fun message(message: String) {
            infoStream!!.println("SIS [" /*+ java.lang.Thread.currentThread().getName()*/ + "]: " + message)
        }
    }
}
