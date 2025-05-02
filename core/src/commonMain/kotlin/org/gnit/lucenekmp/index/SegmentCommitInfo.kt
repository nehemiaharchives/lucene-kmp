package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.StringHelper
import kotlin.concurrent.Volatile

/**
 * Embeds a [read-only] SegmentInfo and adds per-commit fields.
 *
 * @lucene.experimental
 */
class SegmentCommitInfo(
    /** The [SegmentInfo] that we wrap.  */
    val info: SegmentInfo,
// How many deleted docs in the segment:
    private var delCount: Int,
    softDelCount: Int,
    delGen: Long,
    fieldInfosGen: Long,
    docValuesGen: Long,
    id: ByteArray?
) {
    /** Id that uniquely identifies this segment commit.  */
    private var id: ByteArray?

    // How many soft-deleted docs in the segment that are not also hard-deleted:
    private var softDelCount: Int

    /** Returns generation number of the live docs file or -1 if there are no deletes yet.  */
    // Generation number of the live docs file (-1 if there
    // are no deletes yet):
    var delGen: Long
        private set

    /** Returns the next available generation number of the live docs file.  */
    // Normally 1+delGen, unless an exception was hit on last
    // attempt to write:
    var nextDelGen: Long
        private set

    /**
     * Returns the generation number of the field infos file or -1 if there are no field updates yet.
     */
    // Generation number of the FieldInfos (-1 if there are no updates)
    var fieldInfosGen: Long
        private set

    /** Returns the next available generation number of the FieldInfos files.  */
    // Normally 1+fieldInfosGen, unless an exception was hit on last attempt to
    // write
    var nextFieldInfosGen: Long
        private set

    /**
     * Returns the generation number of the DocValues file or -1 if there are no doc-values updates
     * yet.
     */
    // Generation number of the DocValues (-1 if there are no updates)
    var docValuesGen: Long
        private set

    /** Returns the next available generation number of the DocValues files.  */
    // Normally 1+dvGen, unless an exception was hit on last attempt to
    // write
    var nextDocValuesGen: Long
        private set

    // Track the per-field DocValues update files
    private val dvUpdatesFiles: MutableMap<Int, MutableSet<String>> = HashMap<Int, MutableSet<String>>()

    // TODO should we add .files() to FieldInfosFormat, like we have on
    // LiveDocsFormat
    // track the fieldInfos update files
    private val fieldInfosFiles: MutableSet<String> = HashSet<String>()

    @Volatile
    private var sizeInBytes: Long = -1

    // NOTE: only used in-RAM by IW to track buffered deletes;
    // this is never written to/read from the Directory
    var bufferedDeletesGen: Long = -1
        set(v) {
            if (field == -1L) {
                field = v
                generationAdvanced()
            } else {
                throw IllegalStateException("buffered deletes gen should only be set once")
            }
        }

    /**
     * Sole constructor.
     *
     * @param info [SegmentInfo] that we wrap
     * @param delCount number of deleted documents in this segment
     * @param delGen deletion generation number (used to name deletion files)
     * @param fieldInfosGen FieldInfos generation number (used to name field-infos files)
     * @param docValuesGen DocValues generation number (used to name doc-values updates files)
     * @param id Id that uniquely identifies this segment commit. This id must be 16 bytes long. See
     * [StringHelper.randomId]
     */
    init {
        this.delCount = delCount
        this.softDelCount = softDelCount
        this.delGen = delGen
        this.nextDelGen = if (delGen == -1L) 1 else delGen + 1
        this.fieldInfosGen = fieldInfosGen
        this.nextFieldInfosGen = if (fieldInfosGen == -1L) 1 else fieldInfosGen + 1
        this.docValuesGen = docValuesGen
        this.nextDocValuesGen = if (docValuesGen == -1L) 1 else docValuesGen + 1
        this.id = id
        require(!(id != null && id.size != StringHelper.ID_LENGTH)) { "invalid id: " + id.contentToString() }
    }

    var docValuesUpdatesFiles: MutableMap<Int, MutableSet<String>>
        /** Returns the per-field DocValues updates files.  */
        get() = dvUpdatesFiles
        /** Sets the DocValues updates file names, per field number. Does not deep clone the map.  */
        set(dvUpdatesFiles) {
            this.dvUpdatesFiles.clear()
            for (kv in dvUpdatesFiles.entries) {
                // rename the set
                val set: MutableSet<String> = HashSet<String>()
                for (file in kv.value) {
                    set.add(info.namedForThisSegment(file))
                }
                this.dvUpdatesFiles.put(kv.key, set)
            }
        }

    /** Returns the FieldInfos file names.  */
    fun getFieldInfosFiles(): MutableSet<String> {
        return fieldInfosFiles
    }

    /** Sets the FieldInfos file names.  */
    fun setFieldInfosFiles(fieldInfosFiles: MutableSet<String>) {
        this.fieldInfosFiles.clear()
        for (file in fieldInfosFiles) {
            this.fieldInfosFiles.add(info.namedForThisSegment(file))
        }
    }

    /** Called when we succeed in writing deletes  */
    fun advanceDelGen() {
        delGen = this.nextDelGen
        this.nextDelGen = delGen + 1
        generationAdvanced()
    }

    /**
     * Called if there was an exception while writing deletes, so that we don't try to write to the
     * same file more than once.
     */
    fun advanceNextWriteDelGen() {
        this.nextDelGen++
    }

    /** Gets the nextWriteDelGen.  */
    fun getNextWriteDelGen(): Long {
        return this.nextDelGen
    }

    /** Sets the nextWriteDelGen.  */
    fun setNextWriteDelGen(v: Long) {
        this.nextDelGen = v
    }

    /** Called when we succeed in writing a new FieldInfos generation.  */
    fun advanceFieldInfosGen() {
        fieldInfosGen = this.nextFieldInfosGen
        this.nextFieldInfosGen = fieldInfosGen + 1
        generationAdvanced()
    }

    /**
     * Called if there was an exception while writing a new generation of FieldInfos, so that we don't
     * try to write to the same file more than once.
     */
    fun advanceNextWriteFieldInfosGen() {
        this.nextFieldInfosGen++
    }

    /** Gets the nextWriteFieldInfosGen.  */
    fun getNextWriteFieldInfosGen(): Long {
        return this.nextFieldInfosGen
    }

    /** Sets the nextWriteFieldInfosGen.  */
    fun setNextWriteFieldInfosGen(v: Long) {
        this.nextFieldInfosGen = v
    }

    /** Called when we succeed in writing a new DocValues generation.  */
    fun advanceDocValuesGen() {
        docValuesGen = this.nextDocValuesGen
        this.nextDocValuesGen = docValuesGen + 1
        generationAdvanced()
    }

    /**
     * Called if there was an exception while writing a new generation of DocValues, so that we don't
     * try to write to the same file more than once.
     */
    fun advanceNextWriteDocValuesGen() {
        this.nextDocValuesGen++
    }

    /** Gets the nextWriteDocValuesGen.  */
    fun getNextWriteDocValuesGen(): Long {
        return this.nextDocValuesGen
    }

    /** Sets the nextWriteDocValuesGen.  */
    fun setNextWriteDocValuesGen(v: Long) {
        this.nextDocValuesGen = v
    }

    /** Returns total size in bytes of all files for this segment.  */
    @Throws(IOException::class)
    fun sizeInBytes(): Long {
        if (sizeInBytes == -1L) {
            var sum: Long = 0
            for (fileName in files()) {
                sum += info.dir.fileLength(fileName)
            }
            sizeInBytes = sum
        }

        return sizeInBytes
    }

    /** Returns all files in use by this segment.  */
    @Throws(IOException::class)
    fun files(): MutableCollection<String> {
        // Start from the wrapped info's files:
        val files: MutableCollection<String> = HashSet(info.files())

        // TODO we could rely on TrackingDir.getCreatedFiles() (like we do for
        // updates) and then maybe even be able to remove LiveDocsFormat.files().

        // Must separately add any live docs files:
        if (hasDeletions()) {
            info.getCodec().liveDocsFormat().files(this, files)
        }

        // must separately add any field updates files
        for (updatefiles in dvUpdatesFiles.values) {
            files.addAll(updatefiles)
        }

        // must separately add fieldInfos files
        files.addAll(fieldInfosFiles)

        return files
    }

    /** Returns true if there are any deletions for the segment at this commit.  */
    fun hasDeletions(): Boolean {
        return delGen != -1L
    }

    /** Returns true if there are any field updates for the segment in this commit.  */
    fun hasFieldUpdates(): Boolean {
        return fieldInfosGen != -1L
    }

    /** Returns the number of deleted docs in the segment.  */
    fun getDelCount(): Int {
        return delCount
    }

    /** Returns the number of only soft-deleted docs.  */
    fun getSoftDelCount(): Int {
        return softDelCount
    }

    fun setDelCount(delCount: Int) {
        require(!(delCount < 0 || delCount > info.maxDoc())) { "invalid delCount=" + delCount + " (maxDoc=" + info.maxDoc() + ")" }
        require(
            softDelCount + delCount <= info.maxDoc()
        ) { "maxDoc=" + info.maxDoc() + ",delCount=" + delCount + ",softDelCount=" + softDelCount }
        this.delCount = delCount
    }

    fun setSoftDelCount(softDelCount: Int) {
        require(!(softDelCount < 0 || softDelCount > info.maxDoc())) { "invalid softDelCount=" + softDelCount + " (maxDoc=" + info.maxDoc() + ")" }
        require(
            softDelCount + delCount <= info.maxDoc()
        ) { "maxDoc=" + info.maxDoc() + ",delCount=" + delCount + ",softDelCount=" + softDelCount }
        this.softDelCount = softDelCount
    }

    /** Returns a description of this segment.  */
    fun toString(pendingDelCount: Int): String {
        var s: String = info.toString(delCount + pendingDelCount)
        if (delGen != -1L) {
            s += ":delGen=$delGen"
        }
        if (fieldInfosGen != -1L) {
            s += ":fieldInfosGen=$fieldInfosGen"
        }
        if (docValuesGen != -1L) {
            s += ":dvGen=$docValuesGen"
        }
        if (softDelCount > 0) {
            s += " :softDel=$softDelCount"
        }
        if (this.id != null) {
            s += " :id=" + StringHelper.idToString(id)
        }

        return s
    }

    override fun toString(): String {
        return toString(0)
    }

    fun clone(): SegmentCommitInfo {
        val other =
            SegmentCommitInfo(
                info, delCount, softDelCount, delGen, fieldInfosGen, docValuesGen, getId()
            )
        // Not clear that we need to carry over nextWriteDelGen
        // (i.e. do we ever clone after a failed write and
        // before the next successful write), but just do it to
        // be safe:
        other.nextDelGen = this.nextDelGen
        other.nextFieldInfosGen = this.nextFieldInfosGen
        other.nextDocValuesGen = this.nextDocValuesGen

        // deep clone
        for (e in dvUpdatesFiles.entries) {
            other.dvUpdatesFiles.put(e.key, HashSet<String>(e.value))
        }

        other.fieldInfosFiles.addAll(fieldInfosFiles)

        return other
    }

    fun getDelCount(includeSoftDeletes: Boolean): Int {
        return if (includeSoftDeletes) getDelCount() + getSoftDelCount() else getDelCount()
    }

    private fun generationAdvanced() {
        sizeInBytes = -1
        id = StringHelper.randomId()
    }

    /**
     * Returns and Id that uniquely identifies this segment commit or `null` if there is no
     * ID assigned. This ID changes each time the segment changes due to a delete, doc-value or field
     * update.
     */
    fun getId(): ByteArray? {
        return if (id == null) null else id!!.copyOf() as ByteArray // this cast is needed for kotlin/native target compilation to pass
    }
}
