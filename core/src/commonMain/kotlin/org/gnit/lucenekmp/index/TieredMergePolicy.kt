package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.sort
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


/**
 * Merges segments of approximately equal size, subject to an allowed number of segments per tier.
 * This is similar to [LogByteSizeMergePolicy], except this merge policy is able to merge
 * non-adjacent segment. This merge policy also does not over-merge (i.e. cascade merges).
 *
 *
 * For normal merging, this policy first computes a "budget" of how many segments are allowed to
 * be in the index. If the index is over-budget, then the policy sorts segments by decreasing size
 * (pro-rating by percent deletes), and then finds the least-cost merge. Merge cost is measured by a
 * combination of the "skew" of the merge (size of largest segment divided by smallest segment),
 * total merge size and percent deletes reclaimed, so that merges with lower skew, smaller size and
 * those reclaiming more deletes, are favored.
 *
 *
 * If a merge will produce a segment that's larger than [.setMaxMergedSegmentMB], then the
 * policy will merge fewer segments (down to 1 at once, if that one has deletions) to keep the
 * segment size under budget.
 *
 *
 * **NOTE**: this policy freely merges non-adjacent segments; if this is a problem, use [ ].
 *
 *
 * **NOTE**: This policy always merges by byte size of the segments, always pro-rates by
 * percent deletes
 *
 *
 * **NOTE** Starting with Lucene 7.5, if you call [IndexWriter.forceMerge] with
 * this (default) merge policy, if [.setMaxMergedSegmentMB] is in conflict with `maxNumSegments` passed to [IndexWriter.forceMerge] then `maxNumSegments` wins. For
 * example, if your index has 50 1 GB segments, and you have [.setMaxMergedSegmentMB] at 1024
 * (1 GB), and you call `forceMerge(10)`, the two settings are clearly in conflict. `TieredMergePolicy` will choose to break the [.setMaxMergedSegmentMB] constraint and try to
 * merge down to at most ten segments, each up to 5 * 1.25 GB in size (since an extra 25% buffer
 * increase in the expected segment size is targeted).
 *
 *
 * findForcedDeletesMerges should never produce segments greater than maxSegmentSize.
 *
 *
 * **NOTE**: This policy returns natural merges whose size is below the [ ][.setFloorSegmentMB] for [full-flush][.findFullFlushMerges].
 *
 * @lucene.experimental
 */
// TODO
//   - we could try to take into account whether a large
//     merge is already running (under CMS) and then bias
//     ourselves towards picking smaller merges if so (or,
//     maybe CMS should do so)
open class TieredMergePolicy
/** Sole constructor, setting all settings to their defaults.  */
    : MergePolicy(
    DEFAULT_NO_CFS_RATIO,
    DEFAULT_MAX_CFS_SEGMENT_SIZE
) {
    private var maxMergedSegmentBytes = 5 * 1024 * 1024 * 1024L

    private var floorSegmentBytes = 16 * 1024 * 1024L

    /**
     * Returns the current segmentsPerTier setting.
     *
     * @see .setSegmentsPerTier
     */
    var segmentsPerTier: Double = 10.0
        private set

    /**
     * Returns the current forceMergeDeletesPctAllowed setting.
     *
     * @see .setForceMergeDeletesPctAllowed
     */
    var forceMergeDeletesPctAllowed: Double = 10.0
        private set

    /**
     * Returns the current deletesPctAllowed setting.
     *
     * @see .setDeletesPctAllowed
     */
    var deletesPctAllowed: Double = 20.0
        private set

    /** Returns the target search concurrency.  */
    var targetSearchConcurrency: Int = 1
        private set

    private enum class MERGE_TYPE {
        NATURAL,
        FORCE_MERGE,
        FORCE_MERGE_DELETES
    }

    // TODO: should addIndexes do explicit merging, too  And,
    // if user calls IW.maybeMerge "explicitly"
    /**
     * Maximum sized segment to produce during normal merging. This setting is approximate: the
     * estimate of the merged segment size is made by summing sizes of to-be-merged segments
     * (compensating for percent deleted docs). Default is 5 GB.
     */
    fun setMaxMergedSegmentMB(v: Double): TieredMergePolicy {
        var v = v
        require(!(v < 0.0)) { "maxMergedSegmentMB must be >=0 (got $v)" }
        v *= (1024 * 1024).toDouble()
        maxMergedSegmentBytes = if (v > Long.MAX_VALUE) Long.MAX_VALUE else v.toLong()
        return this
    }

    val maxMergedSegmentMB: Double
        /**
         * Returns the current maxMergedSegmentMB setting.
         *
         * @see .setMaxMergedSegmentMB
         */
        get() = maxMergedSegmentBytes / 1024.0 / 1024.0

    /**
     * Sets the maximum percentage of doc id space taken by deleted docs. The denominator includes
     * both active and deleted documents. Lower values make the index more space efficient at the
     * expense of increased CPU and I/O activity. Values must be between 5 and 50. Default value is
     * 20.
     *
     *
     * When the maximum delete percentage is lowered, the indexing thread will call for merges more
     * often, meaning that write amplification factor will be increased. Write amplification factor
     * measures the number of times each document in the index is written. A higher write
     * amplification factor will lead to higher CPU and I/O activity as indicated above.
     */
    fun setDeletesPctAllowed(v: Double): TieredMergePolicy {
        require(v in 5.0..50.0) { "indexPctDeletedTarget must be >= 5.0 and <= 50 (got $v)" }
        deletesPctAllowed = v
        return this
    }

    /**
     * Segments smaller than this size are merged more aggressively:
     *
     *
     *  * They are candidates for full-flush merges, in order to reduce the number of segments in
     * the index prior to opening a new point-in-time view of the index.
     *  * For background merges, smaller segments are "rounded up" to this size.
     *
     *
     * In both cases, this helps prevent frequent flushing of tiny segments to create a long tail of
     * small segments in the index. Default is 16MB.
     */
    fun setFloorSegmentMB(v: Double): TieredMergePolicy {
        var v = v
        require(!(v <= 0.0)) { "floorSegmentMB must be > 0.0 (got $v)" }
        v *= (1024 * 1024).toDouble()
        floorSegmentBytes = if (v > Long.MAX_VALUE) Long.MAX_VALUE else v.toLong()
        return this
    }

    val floorSegmentMB: Double
        /**
         * Returns the current floorSegmentMB.
         *
         * @see .setFloorSegmentMB
         */
        get() = floorSegmentBytes / (1024 * 1024.0)

    override fun maxFullFlushMergeSize(): Long {
        return floorSegmentBytes
    }

    /**
     * When forceMergeDeletes is called, we only merge away a segment if its delete percentage is over
     * this threshold. Default is 10%.
     */
    fun setForceMergeDeletesPctAllowed(v: Double): TieredMergePolicy {
        require(v in 0.0..100.0) { "forceMergeDeletesPctAllowed must be between 0.0 and 100.0 inclusive (got $v)" }
        forceMergeDeletesPctAllowed = v
        return this
    }

    /**
     * Sets the allowed number of segments per tier. Smaller values mean more merging but fewer
     * segments.
     *
     *
     * Default is 10.0.
     */
    fun setSegmentsPerTier(v: Double): TieredMergePolicy {
        require(!(v < 2.0)) { "segmentsPerTier must be >= 2.0 (got $v)" }
        this.segmentsPerTier = v
        return this
    }

    /**
     * Sets the target search concurrency. This prevents creating segments that are bigger than
     * maxDoc/targetSearchConcurrency, which in turn makes the work parallelizable into
     * targetSearchConcurrency slices of similar doc counts. It also makes merging less aggressive, as
     * higher values result in indices that do less merging and have more segments
     */
    fun setTargetSearchConcurrency(targetSearchConcurrency: Int): TieredMergePolicy {
        require(targetSearchConcurrency >= 1) { "targetSearchConcurrency must be >= 1 (got $targetSearchConcurrency)" }
        this.targetSearchConcurrency = targetSearchConcurrency
        return this
    }

    class SegmentSizeAndDocs(
        info: SegmentCommitInfo,
        /** Size of the segment in bytes, pro-rated by the number of live documents. */
        val sizeInBytes: Long,
        segDelCount: Int
    ) {
        val segInfo: SegmentCommitInfo = info

        val delCount: Int = segDelCount
        val maxDoc: Int = info.info.maxDoc()
        val name: String = info.info.name
    }

    /** Holds score and explanation for a single candidate merge.  */
    protected abstract class MergeScore
    /** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
    protected constructor() {
        /** Returns the score for this merge candidate; lower scores are better.  */
        abstract val score: Double

        /** Human readable explanation of how the merge got this score.  */
        abstract val explanation: String
    }

    // The size can change concurrently while we are running here, because deletes
    // are now applied concurrently, and this can piss off TimSort!  So we
    // call size() once per segment and sort by that:
    @Throws(IOException::class)
    private fun getSortedBySegmentSize(
        infos: SegmentInfos?, mergeContext: MergeContext?
    ): MutableList<SegmentSizeAndDocs> {
        val sortedBySize: MutableList<SegmentSizeAndDocs> = mutableListOf()

        for (info in infos!!) {
            sortedBySize.add(
                SegmentSizeAndDocs(
                    info, size(info, mergeContext!!), mergeContext.numDeletesToMerge(info)
                )
            )
        }

        sortedBySize.sort { o1: SegmentSizeAndDocs, o2: SegmentSizeAndDocs ->
            // Sort by largest size:
            var cmp: Int = Long.compare(o2.sizeInBytes, o1.sizeInBytes)
            if (cmp == 0) {
                cmp = o1.name.compareTo(o2.name)
            }
            cmp
        }

        return sortedBySize
    }

    @Throws(IOException::class)
    override fun findMerges(
        mergeTrigger: MergeTrigger?,
        infos: SegmentInfos?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        val merging: MutableSet<SegmentCommitInfo> = mergeContext!!.mergingSegments
        // Compute total index bytes & print details about the index
        var totIndexBytes: Long = 0
        var minSegmentBytes = Long.MAX_VALUE

        var totalDelDocs = 0
        var totalMaxDoc = 0

        var mergingBytes: Long = 0

        val sortedInfos = getSortedBySegmentSize(infos, mergeContext)
        var iter = sortedInfos.iterator()
        while (iter.hasNext()) {
            val segSizeDocs = iter.next()
            val segBytes = segSizeDocs.sizeInBytes
            if (verbose(mergeContext)) {
                var extra = if (merging.contains(segSizeDocs.segInfo)) " [merging]" else ""
                if (segBytes >= maxMergedSegmentBytes) {
                    extra += " [skip: too large]"
                } else if (segBytes < floorSegmentBytes) {
                    extra += " [floored]"
                }
                message(
                    ("  seg="
                            + segString(
                        mergeContext,
                        mutableSetOf(segSizeDocs.segInfo)
                    ) +
                            "${segBytes / 1024.0 / 1024.0} MB"),
                    mergeContext
                )
            }
            if (merging.contains(segSizeDocs.segInfo)) {
                mergingBytes += segSizeDocs.sizeInBytes
                iter.remove()
                // if this segment is merging, then its deletes are being reclaimed already.
                // only count live docs in the total max doc
                totalMaxDoc += segSizeDocs.maxDoc - segSizeDocs.delCount
            } else {
                totalDelDocs += segSizeDocs.delCount
                totalMaxDoc += segSizeDocs.maxDoc
            }

            minSegmentBytes = min(segBytes, minSegmentBytes)
            totIndexBytes += segBytes
        }
        assert(totalMaxDoc >= 0)
        assert(totalDelDocs >= 0)

        val totalDelPct = 100 * totalDelDocs.toDouble() / totalMaxDoc
        var allowedDelCount = (deletesPctAllowed * totalMaxDoc / 100).toInt()

        // If we have too-large segments, grace them out of the maximum segment count
        // If we're above certain thresholds of deleted docs, we can merge very large segments.
        var tooBigCount = 0
        // We relax merging for the bigger segments for concurrency reasons, as we want to have several
        // segments on the highest tier without over-merging on the lower tiers.
        var concurrencyCount = 0
        iter = sortedInfos.iterator()

        var allowedSegCount = 0.0

        // remove large segments from consideration under two conditions.
        // 1> Overall percent deleted docs relatively small and this segment is larger than 50%
        // maxSegSize
        // 2> overall percent deleted docs large and this segment is large and has few deleted docs
        while (iter.hasNext()) {
            val segSizeDocs = iter.next()
            val segDelPct = 100 * segSizeDocs.delCount.toDouble() / segSizeDocs.maxDoc.toDouble()
            if (segSizeDocs.sizeInBytes > maxMergedSegmentBytes / 2
                && (totalDelPct <= deletesPctAllowed || segDelPct <= deletesPctAllowed)
            ) {
                iter.remove()
                tooBigCount++
                totIndexBytes -= segSizeDocs.sizeInBytes
                allowedDelCount -= segSizeDocs.delCount
            } else if (concurrencyCount + tooBigCount < targetSearchConcurrency - 1) {
                // Make sure we count a whole segment for the first targetSearchConcurrency-1 segments to
                // avoid over merging on the lower levels.
                concurrencyCount++
                allowedSegCount++
                totIndexBytes -= segSizeDocs.sizeInBytes
            }
        }
        allowedDelCount = max(0, allowedDelCount)

        val mergeFactor = segmentsPerTier.toInt()
        // Compute max allowed segments for the remainder of the index
        var levelSize = max(minSegmentBytes, floorSegmentBytes)
        var bytesLeft = totIndexBytes
        while (true) {
            val segCountLevel = bytesLeft / levelSize.toDouble()
            if (segCountLevel < this.segmentsPerTier || levelSize == maxMergedSegmentBytes) {
                allowedSegCount += ceil(segCountLevel)
                break
            }
            allowedSegCount += this.segmentsPerTier
            bytesLeft = (bytesLeft - this.segmentsPerTier * levelSize).toLong()
            levelSize = min(maxMergedSegmentBytes, levelSize * mergeFactor)
        }
        // allowedSegCount may occasionally be less than segsPerTier
        // if segment sizes are below the floor size
        allowedSegCount = max(allowedSegCount, this.segmentsPerTier)
        // No need to merge if the total number of segments (including too big segments) is less than or
        // equal to the target search concurrency.
        allowedSegCount = max(allowedSegCount, (targetSearchConcurrency - tooBigCount).toDouble())
        val allowedDocCount = getMaxAllowedDocs(totalMaxDoc, totalDelDocs)

        if (verbose(mergeContext) && tooBigCount > 0) {
            message(
                ("  allowedSegmentCount="
                        + allowedSegCount
                        + " vs count="
                        + infos!!.size()
                        + " (eligible count="
                        + sortedInfos.size
                        + ") tooBigCount= "
                        + tooBigCount
                        + "  allowedDocCount="
                        + allowedDocCount
                        + " vs doc count="
                        + infos.totalMaxDoc()),
                mergeContext
            )
        }
        return doFindMerges(
            sortedInfos,
            maxMergedSegmentBytes,
            mergeFactor,
            allowedSegCount.toInt(),
            allowedDelCount,
            allowedDocCount,
            MERGE_TYPE.NATURAL,
            mergeContext,
            mergingBytes >= maxMergedSegmentBytes
        )
    }

    @Throws(IOException::class)
    private fun doFindMerges(
        sortedEligibleInfos: MutableList<SegmentSizeAndDocs>,
        maxMergedSegmentBytes: Long,
        mergeFactor: Int,
        allowedSegCount: Int,
        allowedDelCount: Int,
        allowedDocCount: Int,
        mergeType: MERGE_TYPE,
        mergeContext: MergeContext,
        maxMergeIsRunning: Boolean
    ): MergeSpecification? {
        val sortedEligible: MutableList<SegmentSizeAndDocs> = mutableListOf()

        val segInfosSizes: MutableMap<SegmentCommitInfo, SegmentSizeAndDocs> = mutableMapOf()
        for (segSizeDocs in sortedEligible) {
            segInfosSizes[segSizeDocs.segInfo] = segSizeDocs
        }

        val originalSortedSize = sortedEligible.size
        if (verbose(mergeContext)) {
            message("findMerges: $originalSortedSize segments", mergeContext)
        }
        if (originalSortedSize == 0) {
            return null
        }

        val toBeMerged: MutableSet<SegmentCommitInfo> = mutableSetOf()

        var spec: MergeSpecification? = null

        // Cycle to possibly select more than one merge:
        // The trigger point for total deleted documents in the index leads to a bunch of large segment
        // merges at the same time. So only put one large merge in the list of merges per cycle. We'll
        // pick up another
        // merge next time around.
        var haveOneLargeMerge = false

        while (true) {
            // Gather eligible segments for merging, ie segments
            // not already being merged and not already picked (by
            // prior iteration of this loop) for merging:

            // Remove ineligible segments. These are either already being merged or already picked by
            // prior iterations

            val iter = sortedEligible.iterator()
            while (iter.hasNext()) {
                val segSizeDocs = iter.next()
                if (toBeMerged.contains(segSizeDocs.segInfo)) {
                    iter.remove()
                }
            }

            if (verbose(mergeContext)) {
                message(
                    ("  allowedSegmentCount="
                            + allowedSegCount
                            + " vs count="
                            + originalSortedSize
                            + " (eligible count="
                            + sortedEligible.size
                            + ")"),
                    mergeContext
                )
            }

            if (sortedEligible.isEmpty()) {
                return spec
            }

            val remainingDelCount: Int = sortedEligible.sumOf { c: SegmentSizeAndDocs -> c.delCount }
            if (mergeType == MERGE_TYPE.NATURAL && sortedEligible.size <= allowedSegCount && remainingDelCount <= allowedDelCount) {
                return spec
            }

            // OK we are over budget -- find best merge!
            var bestScore: MergeScore? = null
            var best: MutableList<SegmentCommitInfo>? = null
            var bestTooLarge = false
            var bestMergeBytes: Long = 0

            for (startIdx in sortedEligible.indices) {
                val candidate: MutableList<SegmentCommitInfo> = mutableListOf()
                var hitTooLarge = false
                var bytesThisMerge: Long = 0
                var docCountThisMerge: Long = 0
                var idx = startIdx
                while (idx < sortedEligible.size // We allow merging more than mergeFactor segments together if the merged segment
                    // would be less than the floor segment size. This is important because segments
                    // below the floor segment size are more aggressively merged by this policy, so we
                    // need to grow them as quickly as possible.
                    && (candidate.size < mergeFactor || bytesThisMerge < floorSegmentBytes)
                    && bytesThisMerge < maxMergedSegmentBytes && (bytesThisMerge < floorSegmentBytes || docCountThisMerge <= allowedDocCount)
                ) {
                    val segSizeDocs = sortedEligible[idx]
                    val segBytes = segSizeDocs.sizeInBytes
                    val segDocCount = segSizeDocs.maxDoc - segSizeDocs.delCount
                    if (bytesThisMerge + segBytes > maxMergedSegmentBytes
                        || (bytesThisMerge > floorSegmentBytes
                                && docCountThisMerge + segDocCount > allowedDocCount)
                    ) {
                        // Only set hitTooLarge when reaching the maximum byte size, as this will create
                        // segments of the maximum size which will no longer be eligible for merging for a long
                        // time (until they accumulate enough deletes).
                        hitTooLarge = hitTooLarge or (bytesThisMerge + segBytes > maxMergedSegmentBytes)
                        // We should never have something coming in that _cannot_ be merged, so handle
                        // singleton merges
                        if (candidate.isNotEmpty()) {
                            // NOTE: we continue, so that we can try
                            // "packing" smaller segments into this merge
                            // to see if we can get closer to the max
                            // size; this in general is not perfect since
                            // this is really "bin packing" and we'd have
                            // to try different permutations.
                            idx++
                            continue
                        }
                    }
                    candidate.add(segSizeDocs.segInfo)
                    bytesThisMerge += segBytes
                    docCountThisMerge += segDocCount.toLong()
                    idx++
                }

                // We should never see an empty candidate: we iterated over maxMergeAtOnce
                // segments, and already pre-excluded the too-large segments:
                assert(candidate.isNotEmpty())

                val maxCandidateSegmentSize: SegmentSizeAndDocs = segInfosSizes[candidate[0]]!!
                if (hitTooLarge == false && mergeType == MERGE_TYPE.NATURAL && bytesThisMerge < maxCandidateSegmentSize.sizeInBytes * 1.5 && (maxCandidateSegmentSize.delCount
                            < maxCandidateSegmentSize.maxDoc * deletesPctAllowed / 100)
                ) {
                    // Ignore any merge where the resulting segment is not at least 50% larger than the
                    // biggest input segment.
                    // Otherwise we could run into pathological O(N^2) merging where merges keep rewriting
                    // again and again the biggest input segment into a segment that is barely bigger.
                    // The only exception we make is when the merge would reclaim lots of deletes in the
                    // biggest segment. This is important for cases when lots of documents get deleted at once
                    // without introducing new segments of a similar size for instance.
                    continue
                }

                // A singleton merge with no deletes makes no sense. We can get here when forceMerge is
                // looping around...
                if (candidate.size == 1 && maxCandidateSegmentSize.delCount == 0) {
                    continue
                }

                // If we didn't find a too-large merge and have a list of candidates
                // whose length is less than the merge factor, it means we are reaching
                // the tail of the list of segments and will only find smaller merges.
                // Stop here.
                if (bestScore != null && hitTooLarge == false && candidate.size < mergeFactor) {
                    break
                }

                val score = score(candidate, hitTooLarge, segInfosSizes)
                if (verbose(mergeContext)) {
                    message(
                        ("  maybe="
                                + segString(mergeContext, candidate)
                                + " score="
                                + score.score
                                + " "
                                + score.explanation
                                + " tooLarge="
                                + hitTooLarge
                                + " size="
                                + "${bytesThisMerge / 1024.0 / 1024.0} MB"),
                        mergeContext
                    )
                }

                if ((bestScore == null || score.score < bestScore.score)
                    && (!hitTooLarge || !maxMergeIsRunning)
                ) {
                    best = candidate
                    bestScore = score
                    bestTooLarge = hitTooLarge
                    bestMergeBytes = bytesThisMerge
                }
            }

            if (best == null) {
                return spec
            }
            // The mergeType == FORCE_MERGE_DELETES behaves as the code does currently and can create a
            // large number of
            // concurrent big merges. If we make findForcedDeletesMerges behave as findForcedMerges and
            // cycle through
            // we should remove this.
            if (haveOneLargeMerge == false || bestTooLarge == false || mergeType == MERGE_TYPE.FORCE_MERGE_DELETES) {
                haveOneLargeMerge = haveOneLargeMerge or bestTooLarge

                if (spec == null) {
                    spec = MergeSpecification()
                }
                val merge = OneMerge(best)
                spec.add(merge)

                if (verbose(mergeContext)) {
                    message(
                        ("  add merge="
                                + segString(mergeContext, merge.segments)
                                + " size="
                                + "${bestMergeBytes / 1024.0 / 1024.0} MB"

                                + " score="
                                + "${bestScore!!.score}" + " "
                                + bestScore.explanation
                                + (if (bestTooLarge) " [max merge]" else "")),
                        mergeContext
                    )
                }
            }
            // whether we're going to return this list in the spec of not, we need to remove it from
            // consideration on the next loop.
            toBeMerged.addAll(best)
        }
    }

    /** Expert: scores one merge; subclasses can override.  */
    @Throws(IOException::class)
    protected fun score(
        candidate: MutableList<SegmentCommitInfo>,
        hitTooLarge: Boolean,
        segmentsSizes: MutableMap<SegmentCommitInfo, SegmentSizeAndDocs>
    ): MergeScore {
        var totBeforeMergeBytes: Long = 0
        var totAfterMergeBytes: Long = 0
        var totAfterMergeBytesFloored: Long = 0
        for (info in candidate) {
            val segBytes = segmentsSizes[info]!!.sizeInBytes
            totAfterMergeBytes += segBytes
            totAfterMergeBytesFloored += floorSize(segBytes)
            totBeforeMergeBytes += info.sizeInBytes()
        }

        // Roughly measure "skew" of the merge, i.e. how
        // "balanced" the merge is (whether the segments are
        // about the same size), which can range from
        // 1.0/numSegsBeingMerged (good) to 1.0 (poor). Heavily
        // lopsided merges (skew near 1.0) is no good; it means
        // O(N^2) merge cost over time:
        val skew: Double
        if (hitTooLarge) {
            // Pretend the merge has perfect skew; skew doesn't
            // matter in this case because this merge will not
            // "cascade" and so it cannot lead to N^2 merge cost
            // over time:
            val mergeFactor = segmentsPerTier.toInt()
            skew = 1.0 / mergeFactor
        } else {
            skew =
                ((floorSize(segmentsSizes[candidate[0]]!!.sizeInBytes).toDouble())
                        / totAfterMergeBytesFloored)
        }

        // Strongly favor merges with less skew (smaller
        // mergeScore is better):
        var mergeScore = skew

        // Gently favor smaller merges over bigger ones.  We
        // don't want to make this exponent too large else we
        // can end up doing poor merges of small segments in
        // order to avoid the large merges:
        mergeScore *= totAfterMergeBytes.toDouble().pow(0.05)

        // Strongly favor merges that reclaim deletes:
        val nonDelRatio = (totAfterMergeBytes.toDouble()) / totBeforeMergeBytes
        mergeScore *= nonDelRatio.pow(2.0)

        val finalMergeScore = mergeScore

        return object : MergeScore() {
            override val score: Double
                get(): Double {
                    return finalMergeScore
                }

            override val explanation
                get(): String {
                    return ("skew="
                            + skew + " nonDelRatio="
                            + nonDelRatio)
                }
        }
    }

    @Throws(IOException::class)
    override fun findForcedMerges(
        infos: SegmentInfos?,
        maxSegmentCount: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        if (verbose(mergeContext)) {
            message(
                ("findForcedMerges maxSegmentCount="
                        + maxSegmentCount
                        + " infos="
                        + segString(mergeContext, infos)
                        + " segmentsToMerge="
                        + segmentsToMerge),
                mergeContext
            )
        }

        val sortedSizeAndDocs = getSortedBySegmentSize(infos, mergeContext)

        var totalMergeBytes: Long = 0
        val merging: MutableSet<SegmentCommitInfo> = mergeContext!!.mergingSegments

        // Trim the list down, remove if we're respecting max segment size and it's not original.
        // Presumably it's been merged before and is close enough to the max segment size we
        // shouldn't add it in again.
        var iter = sortedSizeAndDocs.iterator()
        var forceMergeRunning = false
        while (iter.hasNext()) {
            val segSizeDocs = iter.next()
            val isOriginal = segmentsToMerge!![segSizeDocs.segInfo]
            if (isOriginal == null) {
                iter.remove()
            } else {
                if (merging.contains(segSizeDocs.segInfo)) {
                    forceMergeRunning = true
                    iter.remove()
                } else {
                    totalMergeBytes += segSizeDocs.sizeInBytes
                }
            }
        }

        var maxMergeBytes = maxMergedSegmentBytes

        // Set the maximum segment size based on how many segments have been specified.
        if (maxSegmentCount == 1) {
            maxMergeBytes = Long.MAX_VALUE
        } else if (maxSegmentCount != Int.MAX_VALUE) {
            maxMergeBytes = max(
                ((totalMergeBytes.toDouble() / maxSegmentCount.toDouble())).toLong(),
                maxMergedSegmentBytes
            )
            // Fudge this up a bit so we have a better chance of not having to do a second pass of merging
            // to get
            // down to the requested target segment count. If we use the exact size, it's almost
            // guaranteed
            // that the segments selected below won't fit perfectly and we'll be left with more segments
            // than
            // we want and have to re-merge in the code at the bottom of this method.
            maxMergeBytes = (maxMergeBytes.toDouble() * 1.25).toLong()
        }

        iter = sortedSizeAndDocs.iterator()
        var foundDeletes = false
        while (iter.hasNext()) {
            val segSizeDocs = iter.next()
            val isOriginal = segmentsToMerge!![segSizeDocs.segInfo]
            if (segSizeDocs.delCount != 0) {
                // This is forceMerge; all segments with deleted docs should be merged.
                if (isOriginal != null && isOriginal) {
                    foundDeletes = true
                }
                continue
            }
            // Let the scoring handle whether to merge large segments.
            if (maxSegmentCount == Int.MAX_VALUE && isOriginal != null && isOriginal == false) {
                iter.remove()
            }
            // Don't try to merge a segment with no deleted docs that's over the max size.
            if (maxSegmentCount != Int.MAX_VALUE && segSizeDocs.sizeInBytes >= maxMergeBytes) {
                iter.remove()
            }
        }

        // Nothing to merge this round.
        if (sortedSizeAndDocs.isEmpty()) {
            return null
        }

        // We only bail if there are no deletions
        if (!foundDeletes) {
            val infoZero: SegmentCommitInfo = sortedSizeAndDocs[0].segInfo
            if ((maxSegmentCount != Int.MAX_VALUE && maxSegmentCount > 1 && sortedSizeAndDocs.size <= maxSegmentCount)
                || (maxSegmentCount == 1 && sortedSizeAndDocs.size == 1 && (segmentsToMerge!![infoZero] != null
                        || isMerged(infos!!, infoZero, mergeContext)))
            ) {
                if (verbose(mergeContext)) {
                    message("already merged", mergeContext)
                }
                return null
            }
        }

        if (verbose(mergeContext)) {
            message("eligible=$sortedSizeAndDocs", mergeContext)
        }

        val startingSegmentCount = sortedSizeAndDocs.size
        if (forceMergeRunning) {
            // hmm this is a little dangerous -- if a user kicks off a forceMerge, it is taking forever,
            // lots of
            // new indexing/segments happened since, and they want to kick off another to ensure those
            // newly
            // indexed segments partake in the force merge, they (silently) won't due to this
            return null
        }

        // This is the special case of merging down to one segment
        if (maxSegmentCount == 1 && totalMergeBytes < maxMergeBytes) {
            val spec = MergeSpecification()
            val allOfThem: MutableList<SegmentCommitInfo> = mutableListOf()
            for (segSizeDocs in sortedSizeAndDocs) {
                allOfThem.add(segSizeDocs.segInfo)
            }
            spec.add(OneMerge(allOfThem))
            return spec
        }

        var spec: MergeSpecification? = null

        var index = startingSegmentCount - 1
        var resultingSegments = startingSegmentCount
        while (true) {
            val candidate: MutableList<SegmentCommitInfo> = mutableListOf()
            var currentCandidateBytes = 0L
            while (index >= 0 && resultingSegments > maxSegmentCount) {
                val current: SegmentCommitInfo = sortedSizeAndDocs[index].segInfo
                val initialCandidateSize = candidate.size
                val currentSegmentSize: Long = current.sizeInBytes()
                // We either add to the bin because there's space or because the it is the smallest possible
                // bin since
                // decrementing the index will move us to even larger segments.
                if (currentCandidateBytes + currentSegmentSize <= maxMergeBytes
                    || initialCandidateSize < 2
                ) {
                    candidate.add(current)
                    --index
                    currentCandidateBytes += currentSegmentSize
                    if (initialCandidateSize > 0) {
                        // Any merge that handles two or more segments reduces the resulting number of segments
                        // by the number of segments handled - 1
                        --resultingSegments
                    }
                } else {
                    break
                }
            }
            val candidateSize = candidate.size
            // While a force merge is running, only merges that cover the maximum allowed number of
            // segments or that create a segment close to the
            // maximum allowed segment sized are permitted
            if (candidateSize > 1
                && (forceMergeRunning == false || currentCandidateBytes > 0.7 * maxMergeBytes)
            ) {
                val merge = OneMerge(candidate)
                if (verbose(mergeContext)) {
                    message("add merge=" + segString(mergeContext, merge.segments), mergeContext)
                }
                if (spec == null) {
                    spec = MergeSpecification()
                }
                spec.add(merge)
            } else {
                return spec
            }
        }
    }

    @Throws(IOException::class)
    override fun findForcedDeletesMerges(
        infos: SegmentInfos?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        if (verbose(mergeContext)) {
            message(
                ("findForcedDeletesMerges infos="
                        + segString(mergeContext, infos)
                        + " forceMergeDeletesPctAllowed="
                        + forceMergeDeletesPctAllowed),
                mergeContext
            )
        }

        // First do a quick check that there's any work to do.
        // NOTE: this makes BaseMergePOlicyTestCase.testFindForcedDeletesMerges work
        val merging: MutableSet<SegmentCommitInfo> = mergeContext!!.mergingSegments

        var haveWork = false
        var totalDelCount = 0
        for (info in infos!!) {
            val delCount: Int = mergeContext.numDeletesToMerge(info)
            assert(assertDelCount(delCount, info))
            totalDelCount += delCount
            val pctDeletes: Double = 100.0 * (delCount.toDouble()) / info.info.maxDoc()
            haveWork = haveWork || (pctDeletes > forceMergeDeletesPctAllowed && !merging.contains(info))
        }

        if (haveWork == false) {
            return null
        }

        val sortedInfos = getSortedBySegmentSize(infos, mergeContext)

        val iter = sortedInfos.iterator()
        while (iter.hasNext()) {
            val segSizeDocs = iter.next()
            val pctDeletes = 100.0 * (segSizeDocs.delCount.toDouble() / segSizeDocs.maxDoc.toDouble())
            if (merging.contains(segSizeDocs.segInfo) || pctDeletes <= forceMergeDeletesPctAllowed) {
                iter.remove()
            }
        }

        if (verbose(mergeContext)) {
            message("eligible=$sortedInfos", mergeContext)
        }
        return doFindMerges(
            sortedInfos,
            maxMergedSegmentBytes,
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            0,
            getMaxAllowedDocs(infos.totalMaxDoc(), totalDelCount),
            MERGE_TYPE.FORCE_MERGE_DELETES,
            mergeContext,
            false
        )
    }

    fun getMaxAllowedDocs(totalMaxDoc: Int, totalDelDocs: Int): Int {
        return Math.ceilDiv(totalMaxDoc - totalDelDocs, targetSearchConcurrency)
    }

    private fun floorSize(bytes: Long): Long {
        return max(floorSegmentBytes, bytes)
    }

    override fun toString(): String {
        val sb = StringBuilder("[" + this::class.simpleName + ": ")
        sb.append("maxMergedSegmentMB=").append(maxMergedSegmentBytes / 1024.0 / 1024.0).append(", ")
        sb.append("floorSegmentMB=").append(floorSegmentBytes / 1024.0 / 1024.0).append(", ")
        sb.append("forceMergeDeletesPctAllowed=").append(forceMergeDeletesPctAllowed).append(", ")
        sb.append("segmentsPerTier=").append(this.segmentsPerTier).append(", ")
        sb.append("maxCFSSegmentSizeMB=").append(maxCFSSegmentSizeMB).append(", ")
        sb.append("noCFSRatio=").append(noCFSRatio).append(", ")
        sb.append("deletesPctAllowed=").append(deletesPctAllowed).append(", ")
        sb.append("targetSearchConcurrency=").append(targetSearchConcurrency)
        return sb.toString()
    }

    companion object {
        /**
         * Default noCFSRatio. If a merge's size is `>= 10%` of the index, then we disable compound
         * file for it.
         *
         * @see MergePolicy.setNoCFSRatio
         */
        const val DEFAULT_NO_CFS_RATIO: Double = 0.1
    }
}
