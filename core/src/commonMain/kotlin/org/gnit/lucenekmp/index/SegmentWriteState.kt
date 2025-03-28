package org.gnit.lucenekmp.index


import org.gnit.lucenekmp.codecs.PostingsFormat // javadocs
import org.gnit.lucenekmp.codecs.perfield.PerFieldPostingsFormat // javadocs
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.InfoStream

/**
 * Holder class for common parameters used during write.
 *
 * @lucene.experimental
 */
class SegmentWriteState {
    /** [InfoStream] used for debugging messages.  */
    val infoStream: InfoStream

    /** [Directory] where this segment will be written to.  */
    val directory: Directory

    /** [SegmentInfo] describing this segment.  */
    val segmentInfo: SegmentInfo

    /** [FieldInfos] describing all fields in this segment.  */
    val fieldInfos: FieldInfos

    /** Number of deleted documents set while flushing the segment.  */
    var delCountOnFlush: Int = 0

    /** Number of only soft deleted documents set while flushing the segment.  */
    var softDelCountOnFlush: Int = 0

    /**
     * Deletes and updates to apply while we are flushing the segment. A Term is enrolled in here if
     * it was deleted/updated at one point, and it's mapped to the docIDUpto, meaning any docID &lt;
     * docIDUpto containing this term should be deleted/updated.
     */
    val segUpdates: BufferedUpdates

    /**
     * [FixedBitSet] recording live documents; this is only set if there is one or more deleted
     * documents.
     */
    var liveDocs: FixedBitSet? = null

    /**
     * Unique suffix for any postings files written for this segment. [PerFieldPostingsFormat]
     * sets this for each of the postings formats it wraps. If you create a new [PostingsFormat]
     * then any files you write/read must be derived using this suffix (use [ ][IndexFileNames.segmentFileName]).
     *
     *
     * Note: the suffix must be either empty, or be a textual suffix contain exactly two parts
     * (separated by underscore), or be a base36 generation.
     */
    val segmentSuffix: String

    /**
     * [IOContext] for all writes; you should pass this to [ ][Directory.createOutput].
     */
    val context: IOContext

    /** Sole constructor.  */
    constructor(
        infoStream: InfoStream,
        directory: Directory,
        segmentInfo: SegmentInfo,
        fieldInfos: FieldInfos,
        segUpdates: BufferedUpdates,
        context: IOContext
    ) : this(infoStream, directory, segmentInfo, fieldInfos, segUpdates, context, "")

    /**
     * Constructor which takes segment suffix.
     *
     * @see .SegmentWriteState
     */
    constructor(
        infoStream: InfoStream,
        directory: Directory,
        segmentInfo: SegmentInfo,
        fieldInfos: FieldInfos,
        segUpdates: BufferedUpdates,
        context: IOContext,
        segmentSuffix: String
    ) {
        this.infoStream = infoStream
        this.segUpdates = segUpdates
        this.directory = directory
        this.segmentInfo = segmentInfo
        this.fieldInfos = fieldInfos
        require(assertSegmentSuffix(segmentSuffix))
        this.segmentSuffix = segmentSuffix
        this.context = context
    }

    /** Create a shallow copy of [SegmentWriteState] with a new segment suffix.  */
    constructor(state: SegmentWriteState, segmentSuffix: String) {
        infoStream = state.infoStream
        directory = state.directory
        segmentInfo = state.segmentInfo
        fieldInfos = state.fieldInfos
        context = state.context
        this.segmentSuffix = segmentSuffix
        segUpdates = state.segUpdates
        delCountOnFlush = state.delCountOnFlush
        liveDocs = state.liveDocs
    }

    // currently only used by assert clean up and make real check
    // either it's a segment suffix (_X_Y) or it's a parsable generation
    // TODO: this is very confusing how ReadersAndUpdates passes generations via
    // this mechanism, maybe add 'generation' explicitly to ctor create the 'actual suffix' here
    private fun assertSegmentSuffix(segmentSuffix: String): Boolean {
        checkNotNull(segmentSuffix)
        if (!segmentSuffix.isEmpty()) {
            val numParts = segmentSuffix.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size
            if (numParts == 2) {
                return true
            } else if (numParts == 1) {
                segmentSuffix.toLong(Character.MAX_RADIX)
                return true
            }
            return false // invalid
        }
        return true
    }
}
