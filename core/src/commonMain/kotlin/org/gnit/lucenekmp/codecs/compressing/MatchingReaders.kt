package org.gnit.lucenekmp.codecs.compressing


import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.MergeState

/**
 * Computes which segments have identical field name to number mappings, which allows stored fields
 * and term vectors in this codec to be bulk-merged.
 *
 * @lucene.internal
 */
class MatchingReaders(mergeState: MergeState) {
    /**
     * [SegmentReader]s that have identical field name/number mapping, so their stored fields
     * and term vectors may be bulk merged.
     */
    val matchingReaders: BooleanArray

    /** How many [.matchingReaders] are set.  */
    val count: Int

    init {
        // If the i'th reader is a SegmentReader and has
        // identical fieldName -> number mapping, then this
        // array will be non-null at position i:
        val numReaders: Int = mergeState.maxDocs.size
        var matchedCount = 0
        matchingReaders = BooleanArray(numReaders)

        // If this reader is a SegmentReader, and all of its
        // field name -> number mappings match the "merged"
        // FieldInfos, then we can do a bulk copy of the
        // stored fields:
        nextReader@ for (i in 0..<numReaders) {
            for (fi in mergeState.fieldInfos[i]!!) {
                val other: FieldInfo? = mergeState.mergeFieldInfos!!.fieldInfo(fi.number)
                if (other == null || other.name != fi.name) {
                    continue@nextReader
                }
            }
            matchingReaders[i] = true
            matchedCount++
        }

        this.count = matchedCount

        if (mergeState.infoStream.isEnabled("SM")) {
            mergeState.infoStream.message(
                "SM", "merge store matchedCount=$count vs $numReaders"
            )
            if (count != numReaders) {
                mergeState.infoStream.message("SM", "" + (numReaders - count) + " non-bulk merges")
            }
        }
    }
}
