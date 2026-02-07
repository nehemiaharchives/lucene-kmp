package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.jdkport.UnmodifiableMutableMap
import org.gnit.lucenekmp.jdkport.UnmodifiableMutableSet
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version


/**
 * Information about a segment such as its name, directory, and files related to the segment.
 *
 * @lucene.experimental
 */
class SegmentInfo(
    dir: Directory,
    version: Version,
    minVersion: Version?,
    name: String,
    maxDoc: Int,
    isCompoundFile: Boolean,
    hasBlocks: Boolean,
    codec: Codec?,
    diagnostics: MutableMap<String, String>,
    id: ByteArray,
    attributes: MutableMap<String, String>,
    indexSort: Sort?
) {
    /** Unique segment name in the directory.  */
    val name: String

    private var maxDoc: Int // number of docs in seg

    /** Where this segment resides.  */
    val dir: Directory

    /** Returns true if this segment is stored as a compound file; else, false.  */
    /**
     * Mark whether this segment is stored as a compound file.
     *
     * @param isCompoundFile true if this is a compound file; else, false
     */
    var useCompoundFile: Boolean

    /** Id that uniquely identifies this segment.  */
    private val id: ByteArray

    private var codecNullable: Codec? = null

    var codec: Codec
        /** Can only be called once.  */
        set(codec) {
            require(this.codecNullable == null)
            requireNotNull(codec) { "codec must be non-null" }
            this.codecNullable = codec
        }

        /** Return [Codec] that wrote this segment.  */
        get(): Codec {
            return codecNullable!!
        }

    var diagnostics: MutableMap<String, String> = mutableMapOf()
        get() {
            return UnmodifiableMutableMap(field)
        }
        set(newDiagnostics) {
            field = HashMap(newDiagnostics)
        }

    /**
     * Returns the internal codec attributes map.
     *
     * @return internal codec attributes map.
     */
    var attributes: MutableMap<String, String>
        get() {
            return UnmodifiableMutableMap(field)
        }
        private set

    val indexSort: Sort?

    // Tracks the Lucene version this segment was created with, since 3.1. Null
    // indicates an older than 3.0 index, and it's used to detect a too old index.
    // The format expected is "x.y" - "2.x" for pre-3.0 indexes (or null), and
    // specific versions afterwards ("3.0.0", "3.1.0" etc.).
    // see o.a.l.util.Version.
    val version: Version

    // Tracks the minimum version that contributed documents to a segment. For
    // flush segments, that is the version that wrote it. For merged segments,
    // this is the minimum minVersion of all the segments that have been merged
    // into this segment
    /**
     * Return the minimum Lucene version that contributed documents to this segment, or `null`
     * if it is unknown.
     */
    var minVersion: Version?

    /**
     * Returns true if this segment contains documents written as blocks.
     *
     * @see LeafMetaData.hasBlocks
     */
    var hasBlocks: Boolean
        private set

    /*fun setDiagnostics(diagnostics: MutableMap<String, String>) {
        this.diagnostics = HashMap(diagnostics)
    }*/

    /**
     * Adds or modifies this segment's diagnostics.
     *
     *
     * Entries in the given map whose keys are not present in the current diagnostics are added.
     * Otherwise, existing entries are modified with the given map's value.
     *
     * @param diagnostics the additional diagnostics
     */
    fun addDiagnostics(diagnostics: MutableMap<String, String>) {
        requireNotNull(diagnostics)
        val copy: MutableMap<String, String> = HashMap<String, String>(this.diagnostics)
        copy.putAll(diagnostics)
        this.diagnostics = HashMap(copy)
    }

    /** Returns diagnostics saved into the segment when it was written. The map is immutable.  */
    /*fun getDiagnostics(): MutableMap<String, String> {
        return diagnostics
    }*/

    /** Sets the hasBlocks property to true. This setting is viral and can't be unset.  */
    fun setHasBlocks() {
        hasBlocks = true
    }

    /** Returns number of documents in this segment (deletions are not taken into account).  */
    fun maxDoc(): Int {
        check(this.maxDoc != -1) { "maxDoc isn't set yet" }
        return maxDoc
    }

    // NOTE: leave package private
    fun setMaxDoc(maxDoc: Int) {
        check(this.maxDoc == -1) { "maxDoc was already set: this.maxDoc=" + this.maxDoc + " vs maxDoc=" + maxDoc }
        this.maxDoc = maxDoc
    }

    /** Return all files referenced by this SegmentInfo.  */
    fun files(): MutableSet<String> {
        checkNotNull(setFiles) { "files were not computed yet; segment=$name maxDoc=$maxDoc" }
        return UnmodifiableMutableSet(setFiles!!)
    }

    override fun toString(): String {
        return toString(0)
    }

    /**
     * Used for debugging. Format may suddenly change.
     *
     *
     * Current format looks like `_a(3.1):c45/4:[sorter=<long: "timestamp">!]`,
     * which means the segment's name is `_a`; it was created with Lucene 3.1 (or '' if
     * it's unknown); it's using compound file format (would be `C` if not compound); it
     * has 45 documents; it has 4 deletions (this part is left off when there are no deletions); it is
     * sorted by the timestamp field in descending order (this part is omitted for unsorted segments).
     */
    fun toString(delCount: Int): String {
        val s = StringBuilder()
        s.append(name).append('(').append(if (version == null) "" else version).append(')').append(':')
        val cfs = if (this.useCompoundFile) 'c' else 'C'
        s.append(cfs)

        s.append(maxDoc)

        if (delCount != 0) {
            s.append('/').append(delCount)
        }

        if (indexSort != null) {
            s.append(":[indexSort=")
            s.append(indexSort)
            s.append(']')
        }

        if (!diagnostics.isEmpty()) {
            s.append(":[diagnostics=")
            s.append(diagnostics.toString())
            s.append(']')
        }

        val attributes = this.attributes
        if (!attributes.isEmpty()) {
            s.append(":[attributes=")
            s.append(attributes.toString())
            s.append(']')
        }

        return s.toString()
    }

    /** We consider another SegmentInfo instance equal if it has the same dir and same name.  */
    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj is SegmentInfo) {
            return obj.dir === dir && obj.name == name
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return dir.hashCode() + name.hashCode()
    }

    /** Returns the version of the code which wrote the segment.  */
    /*fun getVersion(): Version {
        return version
    }*/

    /** Return the id that uniquely identifies this segment.  */
    fun getId(): ByteArray {
        return id.copyOf() as ByteArray // this cast is needed for kotlin/native target compilation to pass
    }

    private var setFiles: MutableSet<String>? = null

    /**
     * Construct a new complete SegmentInfo instance from input.
     *
     *
     * Note: this is public only to allow access from the codecs package.
     */
    init {
        require(dir !is TrackingDirectoryWrapper)
        this.dir = dir
        this.version = version
        this.minVersion = minVersion
        this.name = name
        this.maxDoc = maxDoc
        this.useCompoundFile = isCompoundFile
        this.hasBlocks = hasBlocks
        this.codecNullable = codec
        this.diagnostics = HashMap<String, String>(diagnostics)

        this.id = id
        require(id.size == StringHelper.ID_LENGTH) { "invalid id: " + id.contentToString() }
        this.attributes = HashMap<String, String>(attributes)

        this.indexSort = indexSort
    }

    /** Sets the files written for this segment.  */
    fun setFiles(files: MutableCollection<String>) {
        setFiles = HashSet()
        addFiles(files)
    }

    /** Add these files to the set of files written for this segment.  */
    fun addFiles(files: MutableCollection<String>) {
        checkFileNames(files)
        for (f in files) {
            setFiles!!.add(namedForThisSegment(f))
        }
    }

    /** Add this file to the set of files written for this segment.  */
    fun addFile(file: String) {
        checkFileNames(mutableSetOf(file))
        setFiles!!.add(namedForThisSegment(file))
    }

    private fun checkFileNames(files: MutableCollection<String>) {
        val pattern = IndexFileNames.CODEC_FILE_PATTERN
        for (file in files) {
            require(pattern.matches(file)) {
                "invalid codec filename '$file', must match: ${pattern.pattern}"
            }
            require(
                !file.lowercase().endsWith(".tmp")
            ) { "invalid codec filename '$file', cannot end with .tmp extension" }
        }
    }

    /**
     * strips any segment name from the file, naming it with this segment this is because "segment
     * names" can change, e.g. by addIndexes(Dir)
     */
    fun namedForThisSegment(file: String): String {
        return name + IndexFileNames.stripSegmentName(file)
    }

    /** Get a codec attribute value, or null if it does not exist  */
    fun getAttribute(key: String): String {
        return attributes[key]!!
    }

    /**
     * Puts a codec attribute value.
     *
     *
     * This is a key-value mapping for the field that the codec can use to store additional
     * metadata, and will be available to the codec when reading the segment via [ ][.getAttribute]
     *
     *
     * If a value already exists for the field, it will be replaced with the new value. This method
     * make a copy on write for every attribute change.
     */
    fun putAttribute(key: String, value: String): String? {
        val newMap: HashMap<String, String> = HashMap(attributes)
        val oldValue: String? = newMap.put(key, value)
        // This needs to be thread-safe because multiple threads may be updating (different) attributes
        // at the same time due to concurrent merging, plus some threads may be calling toString() on
        // segment info while other threads are updating attributes.
        attributes = newMap
        return oldValue
    }

    /** Return the sort order of this segment, or null if the index has no sort.  */
    /*fun getIndexSort(): Sort? {
        return indexSort
    }*/

    companion object {
        // TODO: remove these from this class, for now this is the representation
        /** Used by some member fields to mean not present (e.g., norms, deletions).  */
        const val NO: Int = -1 // e.g. no norms; no deletes;

        /** Used by some member fields to mean present (e.g., norms, deletions).  */
        const val YES: Int = 1 // e.g. have norms; have deletes;
    }
}
