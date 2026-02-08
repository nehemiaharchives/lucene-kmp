package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.Version

/**
 * This [MergePolicy] is used for upgrading all existing segments of an index when calling
 * [IndexWriter.forceMerge]. All other methods delegate to the base `MergePolicy`
 * given to the constructor. This allows for an as-cheap-as possible upgrade of an older index by
 * only upgrading segments that are created by previous Lucene versions. forceMerge does no longer
 * really merge; it is just used to &quot;forceMerge&quot; older segment versions away.
 *
 *
 * In general one would use [IndexUpgrader], but for a fully customizeable upgrade, you can
 * use this like any other `MergePolicy` and call [IndexWriter.forceMerge]:
 *
 * <pre class="prettyprint lang-java">
 * IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_XX, new KeywordAnalyzer());
 * iwc.setMergePolicy(new UpgradeIndexMergePolicy(iwc.getMergePolicy()));
 * IndexWriter w = new IndexWriter(dir, iwc);
 * w.forceMerge(1);
 * w.close();
</pre> *
 *
 *
 * **Warning:** This merge policy may reorder documents if the index was partially upgraded
 * before calling forceMerge (e.g., documents were added). If your application relies on
 * &quot;monotonicity&quot; of doc IDs (which means that the order in which the documents were added
 * to the index is preserved), do a forceMerge(1) instead. Please note, the delegate `MergePolicy` may also reorder documents.
 *
 * @lucene.experimental
 * @see IndexUpgrader
 */
class UpgradeIndexMergePolicy
/**
 * Wrap the given [MergePolicy] and intercept forceMerge requests to only upgrade segments
 * written with previous Lucene versions.
 */
    (`in`: MergePolicy) : FilterMergePolicy(`in`) {
    /**
     * Returns if the given segment should be upgraded. The default implementation will return `!Version.LATEST.equals(si.getVersion())`, so all segments created with a different version
     * number than this Lucene version will get upgraded.
     */
    fun shouldUpgradeSegment(si: SegmentCommitInfo): Boolean {
        return Version.LATEST != si.info.version
    }

    @Throws(IOException::class)
    override fun findMerges(
        mergeTrigger: MergeTrigger?,
        segmentInfos: SegmentInfos?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        return `in`.findMerges(null, segmentInfos, mergeContext)
    }

    @Throws(IOException::class)
    override fun findForcedMerges(
        segmentInfos: SegmentInfos?,
        maxSegmentCount: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        // first find all old segments
        val oldSegments: MutableMap<SegmentCommitInfo, Boolean> = mutableMapOf()
        for (si in segmentInfos!!) {
            val v = segmentsToMerge!!.get(si)
            if (v != null && shouldUpgradeSegment(si)) {
                oldSegments[si] = v
            }
        }

        if (verbose(mergeContext)) {
            message("findForcedMerges: segmentsToUpgrade=$oldSegments", mergeContext)
        }

        if (oldSegments.isEmpty()) return null

        var spec: MergeSpecification? =
            `in`.findForcedMerges(segmentInfos, maxSegmentCount, oldSegments, mergeContext)

        if (spec != null) {
            // remove all segments that are in merge specification from oldSegments,
            // the resulting set contains all segments that are left over
            // and will be merged to one additional segment:
            for (om in spec.merges) {
                // om.segments.forEach(::remove) is used here instead of oldSegments.keySet().removeAll()
                // for performance reasons; when om.segments.size() == oldSegments.size()
                // the AbstractSet#removeAll() implementation will iterate the set elements
                // calling list.contains() for each of them, resulting in O(n^2) performance
                om.segments.forEach { key: Any ->
                    oldSegments.remove(key)
                }
            }
        }

        if (!oldSegments.isEmpty()) {
            if (verbose(mergeContext)) {
                message(
                    ("findForcedMerges: "
                            + `in`::class.simpleName
                            + " does not want to merge all old segments, merge remaining ones into new segment: "
                            + oldSegments),
                    mergeContext
                )
            }
            val newInfos: MutableList<SegmentCommitInfo> = mutableListOf()
            for (si in segmentInfos) {
                if (oldSegments.containsKey(si)) {
                    newInfos.add(si)
                }
            }
            // add the final merge
            if (spec == null) {
                spec = MergeSpecification()
            }
            spec.add(OneMerge(newInfos))
        }

        return spec
    }
}
