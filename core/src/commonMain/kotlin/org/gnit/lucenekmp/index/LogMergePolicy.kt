package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min


/**
 * This class implements a [MergePolicy] that tries to merge segments into levels of
 * exponentially increasing size, where each level has fewer segments than the value of the merge
 * factor. Whenever extra segments (beyond the merge factor upper bound) are encountered, all
 * segments within the level are merged. You can get or set the merge factor using [ ][.getMergeFactor] and [.setMergeFactor] respectively.
 *
 *
 * This class is abstract and requires a subclass to define the [.size] method which
 * specifies how a segment's size is determined. [LogDocMergePolicy] is one subclass that
 * measures size by document count in the segment. [LogByteSizeMergePolicy] is another
 * subclass that measures size as the total byte size of the file(s) for the segment.
 *
 *
 * **NOTE**: This policy returns natural merges whose size is below the [ minimum merge size][.minMergeSize] for [full-flush merges][.findFullFlushMerges].
 */
abstract class LogMergePolicy
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
    : MergePolicy(DEFAULT_NO_CFS_RATIO, DEFAULT_MAX_CFS_SEGMENT_SIZE) {
    /** How many segments to merge at a time.  */
    var mergeFactor: Int = DEFAULT_MERGE_FACTOR
        /**
         * Determines how often segment indices are merged by addDocument(). With smaller values, less RAM
         * is used while indexing, and searches are faster, but indexing speed is slower. With larger
         * values, more RAM is used during indexing, and while searches is slower, indexing is faster.
         * Thus larger values (`> 10`) are best for batch index creation, and smaller values (`< 10`) for indices that are interactively maintained.
         */
        set(mergeFactor) {
            require(mergeFactor >= 2) { "mergeFactor cannot be less than 2" }
            field = mergeFactor
        }

    /**
     * Any segments whose size is smaller than this value will be candidates for full-flush merges and
     * merged more aggressively.
     */
    protected var minMergeSize: Long = 0

    /** If the size of a segment exceeds this value then it will never be merged.  */
    protected var maxMergeSize: Long = 0

    // Although the core MPs set it explicitly, we must default in case someone
    // out there wrote his own LMP ...
    /**
     * If the size of a segment exceeds this value then it will never be merged during [ ][IndexWriter.forceMerge].
     */
    protected var maxMergeSizeForForcedMerge: Long = Long.MAX_VALUE

    /**
     * Returns the largest segment (measured by document count) that may be merged with other
     * segments.
     *
     * @see .setMaxMergeDocs
     */
    /**
     * Determines the largest segment (measured by document count) that may be merged with other
     * segments. Small values (e.g., less than 10,000) are best for interactive indexing, as this
     * limits the length of pauses while indexing to a few seconds. Larger values are best for batched
     * indexing and speedier searches.
     *
     *
     * The default value is [Integer.MAX_VALUE].
     *
     *
     * The default merge policy ([LogByteSizeMergePolicy]) also allows you to set this limit
     * by net size (in MB) of the segment, using [LogByteSizeMergePolicy.setMaxMergeMB].
     */
    /** If a segment has more than this many documents then it will never be merged.  */
    var maxMergeDocs: Int = DEFAULT_MAX_MERGE_DOCS

    /**
     * Returns true if the segment size should be calibrated by the number of deletes when choosing
     * segments for merge.
     */
    /**
     * Sets whether the segment size should be calibrated by the number of deletes when choosing
     * segments for merge.
     */
    /** If true, we pro-rate a segment's size by the percentage of non-deleted documents.  */
    var calibrateSizeByDeletes: Boolean = true

    /**
     * Target search concurrency. This merge policy will avoid creating segments that have more than
     * `maxDoc / targetSearchConcurrency` documents.
     */
    var targetSearchConcurrency: Int = 1
        /**
         * Sets the target search concurrency. This prevents creating segments that are bigger than
         * maxDoc/targetSearchConcurrency, which in turn makes the work parallelizable into
         * targetSearchConcurrency slices of similar doc counts.
         *
         *
         * **NOTE:** Configuring a value greater than 1 will increase the number of segments in the
         * index linearly with the value of `targetSearchConcurrency` and also increase write
         * amplification.
         */
        set(targetSearchConcurrency) {
            require(targetSearchConcurrency >= 1) { "targetSearchConcurrency must be >= 1 (got $targetSearchConcurrency)" }
            field = targetSearchConcurrency
        }

    /**
     * Returns the number of segments that are merged at once and also controls the total number of
     * segments allowed to accumulate in the index.
     */
    /*fun getMergeFactor(): Int {
        return mergeFactor
    }*/

    /** Returns the target search concurrency.  */
    /*fun getTargetSearchConcurrency(): Int {
        return targetSearchConcurrency
    }*/

    /**
     * Return the number of documents in the provided [SegmentCommitInfo], pro-rated by
     * percentage of non-deleted documents if [.setCalibrateSizeByDeletes] is set.
     */
    @Throws(IOException::class)
    protected fun sizeDocs(
        info: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Long {
        if (calibrateSizeByDeletes) {
            val delCount: Int = mergeContext.numDeletesToMerge(info)
            assert(assertDelCount(delCount, info))
            return (info.info.maxDoc() - delCount.toLong())
        } else {
            return info.info.maxDoc().toLong()
        }
    }

    /**
     * Return the byte size of the provided [SegmentCommitInfo], pro-rated by percentage of
     * non-deleted documents if [.setCalibrateSizeByDeletes] is set.
     */
    @Throws(IOException::class)
    protected fun sizeBytes(
        info: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Long {
        if (calibrateSizeByDeletes) {
            return super.size(info, mergeContext)
        }
        return info.sizeInBytes()
    }

    /**
     * Returns true if the number of segments eligible for merging is less than or equal to the
     * specified `maxNumSegments`.
     */
    @Throws(IOException::class)
    protected fun isMerged(
        infos: SegmentInfos,
        maxNumSegments: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>,
        mergeContext: MergeContext
    ): Boolean {
        val numSegments: Int = infos.size()
        var numToMerge = 0
        var mergeInfo: SegmentCommitInfo? = null
        var segmentIsOriginal = false
        var i = 0
        while (i < numSegments && numToMerge <= maxNumSegments) {
            val info: SegmentCommitInfo = infos.info(i)
            val isOriginal = segmentsToMerge[info]
            if (isOriginal != null) {
                segmentIsOriginal = isOriginal
                numToMerge++
                mergeInfo = info
            }
            i++
        }

        return numToMerge <= maxNumSegments && (numToMerge != 1 || !segmentIsOriginal || isMerged(infos, mergeInfo!!, mergeContext))
    }

    override fun maxFullFlushMergeSize(): Long {
        return minMergeSize
    }

    /**
     * Returns the merges necessary to merge the index, taking the max merge size or max merge docs
     * into consideration. This method attempts to respect the `maxNumSegments` parameter,
     * however it might be, due to size constraints, that more than that number of segments will
     * remain in the index. Also, this method does not guarantee that exactly `maxNumSegments`
     * will remain, but &lt;= that number.
     */
    @Throws(IOException::class)
    private fun findForcedMergesSizeLimit(
        infos: SegmentInfos,
        last: Int,
        mergeContext: MergeContext
    ): MergeSpecification? {
        var last = last
        val spec = MergeSpecification()
        val segments: MutableList<SegmentCommitInfo> = infos.asList()

        var start = last - 1
        while (start >= 0) {
            val info: SegmentCommitInfo = infos.info(start)
            if (size(info, mergeContext) > maxMergeSizeForForcedMerge
                || sizeDocs(info, mergeContext) > maxMergeDocs
            ) {
                if (verbose(mergeContext)) {
                    message(
                        ("findForcedMergesSizeLimit: skip segment="
                                + info
                                + ": size is > maxMergeSize ("
                                + maxMergeSizeForForcedMerge
                                + ") or sizeDocs is > maxMergeDocs ("
                                + maxMergeDocs
                                + ")"),
                        mergeContext
                    )
                }
                // need to skip that segment + add a merge for the 'right' segments,
                // unless there is only 1 which is merged.
                if (last - start - 1 > 1
                    || (start != last - 1 && !isMerged(infos, infos.info(start + 1), mergeContext))
                ) {
                    // there is more than 1 segment to the right of
                    // this one, or a mergeable single segment.
                    spec.add(
                        OneMerge(
                            segments.subList(
                                start + 1,
                                last
                            )
                        )
                    )
                }
                last = start
            } else if (last - start == mergeFactor) {
                // mergeFactor eligible segments were found, add them as a merge.
                spec.add(
                    OneMerge(
                        segments.subList(
                            start,
                            last
                        )
                    )
                )
                last = start
            }
            --start
        }

        // Add any left-over segments, unless there is just 1
        // already fully merged
        if (last > 0 && (++start + 1 < last || !isMerged(infos, infos.info(start), mergeContext))) {
            spec.add(OneMerge(segments.subList(start, last)))
        }

        return if (spec.merges.isEmpty()) null else spec
    }

    /**
     * Returns the merges necessary to forceMerge the index. This method constraints the returned
     * merges only by the `maxNumSegments` parameter, and guaranteed that exactly that number of
     * segments will remain in the index.
     */
    @Throws(IOException::class)
    private fun findForcedMergesMaxNumSegments(
        infos: SegmentInfos,
        maxNumSegments: Int,
        last: Int,
        mergeContext: MergeContext
    ): MergeSpecification? {
        var last = last
        val spec = MergeSpecification()
        val segments: MutableList<SegmentCommitInfo> = infos.asList()

        // First, enroll all "full" merges (size
        // mergeFactor) to potentially be run concurrently:
        while (last - maxNumSegments + 1 >= mergeFactor) {
            spec.add(
                OneMerge(segments.subList(last - mergeFactor, last))
            )
            last -= mergeFactor
        }

        // Only if there are no full merges pending do we
        // add a final partial (< mergeFactor segments) merge:
        if (spec.merges.isEmpty()) {
            if (maxNumSegments == 1) {
                // Since we must merge down to 1 segment, the
                // choice is simple:

                if (last > 1 || !isMerged(infos, infos.info(0), mergeContext)) {
                    spec.add(OneMerge(segments.subList(0, last)))
                }
            } else if (last > maxNumSegments) {
                // Take care to pick a partial merge that is
                // least cost, but does not make the index too
                // lopsided.  If we always just picked the
                // partial tail then we could produce a highly
                // lopsided index over time:

                // We must merge this many segments to leave
                // maxNumSegments in the index (from when
                // forceMerge was first kicked off):

                val finalMergeSize = last - maxNumSegments + 1

                // Consider all possible starting points:
                var bestSize: Long = 0
                var bestStart = 0

                for (i in 0..<last - finalMergeSize + 1) {
                    var sumSize: Long = 0
                    for (j in 0..<finalMergeSize) {
                        sumSize += size(infos.info(j + i), mergeContext)
                    }
                    if (i == 0 || (sumSize < 2 * size(infos.info(i - 1), mergeContext) && sumSize < bestSize)) {
                        bestStart = i
                        bestSize = sumSize
                    }
                }

                spec.add(OneMerge(segments.subList(bestStart, bestStart + finalMergeSize)))
            }
        }
        return if (spec.merges.isEmpty()) null else spec
    }

    /**
     * Returns the merges necessary to merge the index down to a specified number of segments. This
     * respects the [.maxMergeSizeForForcedMerge] setting. By default, and assuming `maxNumSegments=1`, only one segment will be left in the index, where that segment has no
     * deletions pending nor separate norms, and it is in compound file format if the current
     * useCompoundFile setting is true. This method returns multiple merges (mergeFactor at a time) so
     * the [MergeScheduler] in use may make use of concurrency.
     */
    @Throws(IOException::class)
    override fun findForcedMerges(
        infos: SegmentInfos,
        maxNumSegments: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>,
        mergeContext: MergeContext
    ): MergeSpecification? {
        assert(maxNumSegments > 0)
        if (verbose(mergeContext)) {
            message(
                "findForcedMerges: maxNumSegs=$maxNumSegments segsToMerge=$segmentsToMerge",
                mergeContext
            )
        }

        // If the segments are already merged (e.g. there's only 1 segment), or
        // there are <maxNumSegments:.
        if (isMerged(infos, maxNumSegments, segmentsToMerge, mergeContext)) {
            if (verbose(mergeContext)) {
                message("already merged; skip", mergeContext)
            }
            return null
        }

        // Find the newest (rightmost) segment that needs to
        // be merged (other segments may have been flushed
        // since merging started):
        var last: Int = infos.size()
        while (last > 0) {
            val info: SegmentCommitInfo = infos.info(--last)
            if (segmentsToMerge[info] != null) {
                last++
                break
            }
        }

        if (last == 0) {
            if (verbose(mergeContext)) {
                message("last == 0; skip", mergeContext)
            }
            return null
        }

        // There is only one segment already, and it is merged
        if (maxNumSegments == 1 && last == 1 && isMerged(infos, infos.info(0), mergeContext)) {
            if (verbose(mergeContext)) {
                message("already 1 seg; skip", mergeContext)
            }
            return null
        }

        // Check if there are any segments above the threshold
        var anyTooLarge = false
        for (i in 0..<last) {
            val info: SegmentCommitInfo = infos.info(i)
            if (size(info, mergeContext) > maxMergeSizeForForcedMerge
                || sizeDocs(info, mergeContext) > maxMergeDocs
            ) {
                anyTooLarge = true
                break
            }
        }

        if (anyTooLarge) {
            return findForcedMergesSizeLimit(infos, last, mergeContext)
        } else {
            return findForcedMergesMaxNumSegments(infos, maxNumSegments, last, mergeContext)
        }
    }

    /**
     * Finds merges necessary to force-merge all deletes from the index. We simply merge adjacent
     * segments that have deletes, up to mergeFactor at a time.
     */
    @Throws(IOException::class)
    override fun findForcedDeletesMerges(
        segmentInfos: SegmentInfos,
        mergeContext: MergeContext
    ): MergeSpecification {
        val segments: MutableList<SegmentCommitInfo> = segmentInfos.asList()
        val numSegments = segments.size

        if (verbose(mergeContext)) {
            message("findForcedDeleteMerges: $numSegments segments", mergeContext)
        }

        val spec = MergeSpecification()
        var firstSegmentWithDeletions = -1
        checkNotNull(mergeContext)
        for (i in 0..<numSegments) {
            val info: SegmentCommitInfo = segmentInfos.info(i)
            val delCount: Int = mergeContext.numDeletesToMerge(info)
            assert(assertDelCount(delCount, info))
            if (delCount > 0) {
                if (verbose(mergeContext)) {
                    message("  segment " + info.info.name + " has deletions", mergeContext)
                }
                if (firstSegmentWithDeletions == -1) firstSegmentWithDeletions = i
                else if (i - firstSegmentWithDeletions == mergeFactor) {
                    // We've seen mergeFactor segments in a row with
                    // deletions, so force a merge now:
                    if (verbose(mergeContext)) {
                        message("  add merge " + firstSegmentWithDeletions + " to " + (i - 1) + " inclusive", mergeContext)
                    }
                    spec.add(OneMerge(segments.subList(firstSegmentWithDeletions, i)))
                    firstSegmentWithDeletions = i
                }
            } else if (firstSegmentWithDeletions != -1) {
                // End of a sequence of segments with deletions, so,
                // merge those past segments even if it's fewer than
                // mergeFactor segments
                if (verbose(mergeContext)) {
                    message("  add merge " + firstSegmentWithDeletions + " to " + (i - 1) + " inclusive", mergeContext)
                }
                spec.add(OneMerge(segments.subList(firstSegmentWithDeletions, i)))
                firstSegmentWithDeletions = -1
            }
        }

        if (firstSegmentWithDeletions != -1) {
            if (verbose(mergeContext)) {
                message("  add merge " + firstSegmentWithDeletions + " to " + (numSegments - 1) + " inclusive", mergeContext)
            }
            spec.add(OneMerge(segments.subList(firstSegmentWithDeletions, numSegments)))
        }

        return spec
    }

    private class SegmentInfoAndLevel(
        val info: SegmentCommitInfo,
        val level: Float
    ) : Comparable<SegmentInfoAndLevel> {
        // Sorts largest to smallest
        override fun compareTo(other: SegmentInfoAndLevel): Int {
            return Float.compare(other.level, level)
        }
    }

    /**
     * Checks if any merges are now necessary and returns a [MergePolicy.MergeSpecification] if
     * so. A merge is necessary when there are more than [.setMergeFactor] segments at a given
     * level. When multiple levels have too many segments, this method will return multiple merges,
     * allowing the [MergeScheduler] to use concurrency.
     */
    @Throws(IOException::class)
    override fun findMerges(
        mergeTrigger: MergeTrigger?,
        infos: SegmentInfos,
        mergeContext: MergeContext
    ): MergeSpecification? {
        val numSegments: Int = infos.size()
        if (verbose(mergeContext)) {
            message("findMerges: $numSegments segments", mergeContext)
        }

        // Compute levels, which is just log (base mergeFactor)
        // of the size of each segment
        val levels: MutableList<SegmentInfoAndLevel> = mutableListOf()
        val norm = ln(mergeFactor.toDouble()).toFloat()

        val mergingSegments: MutableSet<SegmentCommitInfo> = mergeContext.mergingSegments

        var totalDocCount = 0
        for (i in 0..<numSegments) {
            val info: SegmentCommitInfo = infos.info(i)
            totalDocCount += sizeDocs(info, mergeContext).toInt()
            var size: Long = size(info, mergeContext)

            // Floor tiny segments
            if (size < 1) {
                size = 1
            }

            val infoLevel =
                SegmentInfoAndLevel(info, ln(size.toDouble()).toFloat() / norm)
            levels.add(infoLevel)

            if (verbose(mergeContext)) {
                val segBytes = sizeBytes(info, mergeContext)
                var extra = if (mergingSegments.contains(info)) " [merging]" else ""
                if (size >= maxMergeSize) {
                    extra += " [skip: too large]"
                }
                message(("seg=" + segString(mergeContext, mutableSetOf(info)) + " level=" + infoLevel.level + " size=" + "${segBytes / 1024.0 / 1024.0} MB" + extra), mergeContext)
            }
        }

        val levelFloor: Float
        if (minMergeSize <= 0) levelFloor = 0.0.toFloat()
        else levelFloor = (ln(minMergeSize.toDouble()) / norm).toFloat()

        // Now, we quantize the log values into levels.  The
        // first level is any segment whose log size is within
        // LEVEL_LOG_SPAN of the max size, or, who has such as
        // segment "to the right".  Then, we find the max of all
        // other segments and use that to define the next level
        // segment, etc.
        var spec: MergeSpecification? = null

        val numMergeableSegments = levels.size

        // precompute the max level on the right side.
        // arr size is numMergeableSegments + 1 to handle the case
        // when numMergeableSegments is 0.
        val maxLevels = FloatArray(numMergeableSegments + 1)
        // -1 is definitely the minimum value, because Math.log(1) is 0.
        maxLevels[numMergeableSegments] = -1.0f
        for (i in numMergeableSegments - 1 downTo 0) {
            maxLevels[i] = max(levels[i].level, maxLevels[i + 1])
        }

        var start = 0
        while (start < numMergeableSegments) {
            // Find max level of all segments not already
            // quantized.

            val maxLevel = maxLevels[start]

            // Now search backwards for the rightmost segment that
            // falls into this level:
            val levelBottom: Float
            if (maxLevel > levelFloor) {
                // With a merge factor of 10, this means that the biggest segment and the smallest segment
                // that take part of a merge have a size difference of at most 5.6x.
                levelBottom = (maxLevel - LEVEL_LOG_SPAN).toFloat()
            } else {
                // For segments below the floor size, we allow more unbalanced merges, but still somewhat
                // balanced to avoid running into O(n^2) merging.
                // With a merge factor of 10, this means that the biggest segment and the smallest segment
                // that take part of a merge have a size difference of at most 31.6x.
                levelBottom = (maxLevel - 2 * LEVEL_LOG_SPAN).toFloat()
            }

            var upto = numMergeableSegments - 1
            while (upto >= start) {
                if (levels[upto].level >= levelBottom) {
                    break
                }
                upto--
            }
            if (verbose(mergeContext)) {
                message("  level " + levelBottom + " to " + maxLevel + ": " + (1 + upto - start) + " segments", mergeContext)
            }

            val maxMergeDocs = min(
                this.maxMergeDocs,
                Math.ceilDiv(totalDocCount, targetSearchConcurrency)
            )

            // Finally, record all merges that are viable at this level:
            var end = start + mergeFactor
            while (end <= 1 + upto) {
                var anyMerging = false
                var mergeSize: Long = 0
                var mergeDocs: Long = 0
                var i = start
                while (i < end) {
                    val segLevel = levels[i]
                    val info: SegmentCommitInfo = segLevel.info
                    if (mergingSegments.contains(info)) {
                        anyMerging = true
                        break
                    }
                    val segmentSize: Long = size(info, mergeContext)
                    val segmentDocs = sizeDocs(info, mergeContext)
                    if (mergeSize + segmentSize > maxMergeSize || mergeDocs + segmentDocs > maxMergeDocs) {
                        // This merge is full, stop adding more segments to it
                        if (i == start) {
                            // This segment alone is too large, return a singleton merge
                            if (verbose(mergeContext)) {
                                message("    $i is larger than the max merge size/docs; ignoring", mergeContext)
                            }
                            end = i + 1
                        } else {
                            // Previous segments are under the max merge size, return them
                            end = i
                        }
                        break
                    }
                    mergeSize += segmentSize
                    mergeDocs += segmentDocs
                    i++
                }

                if (end - start >= mergeFactor && minMergeSize < maxMergeSize && mergeSize < minMergeSize && anyMerging == false) {
                    // If the merge has mergeFactor segments but is still smaller than the min merged segment
                    // size, keep packing candidate segments.
                    while (end < 1 + upto) {
                        val segLevel = levels[end]
                        val info: SegmentCommitInfo = segLevel.info
                        if (mergingSegments.contains(info)) {
                            anyMerging = true
                            break
                        }
                        val segmentSize: Long = size(info, mergeContext)
                        val segmentDocs = sizeDocs(info, mergeContext)
                        if (mergeSize + segmentSize > minMergeSize || mergeDocs + segmentDocs > maxMergeDocs) {
                            break
                        }

                        mergeSize += segmentSize
                        mergeDocs += segmentDocs
                        end++
                    }
                }

                if (anyMerging || end - start <= 1) {
                    // skip: there is an ongoing merge at the current level or the computed merge has a single
                    // segment and this merge policy doesn't do singleton merges
                } else {
                    if (spec == null) {
                        spec = MergeSpecification()
                    }
                    val mergeInfos: MutableList<SegmentCommitInfo> = mutableListOf()
                    for (i in start..<end) {
                        mergeInfos.add(levels[i].info)
                        assert(infos.contains(levels[i].info))
                    }
                    if (verbose(mergeContext)) {
                        message(("  add merge=" + segString(mergeContext, mergeInfos) + " start=" + start + " end=" + end), mergeContext)
                    }
                    spec.add(OneMerge(mergeInfos))
                }

                start = end
                end = start + mergeFactor
            }

            start = 1 + upto
        }

        return spec
    }

    override fun toString(): String {
        val sb = StringBuilder("[" + this::class.simpleName + ": ")
        sb.append("minMergeSize=").append(minMergeSize).append(", ")
        sb.append("mergeFactor=").append(mergeFactor).append(", ")
        sb.append("maxMergeSize=").append(maxMergeSize).append(", ")
        sb.append("maxMergeSizeForForcedMerge=").append(maxMergeSizeForForcedMerge).append(", ")
        sb.append("calibrateSizeByDeletes=").append(calibrateSizeByDeletes).append(", ")
        sb.append("maxMergeDocs=").append(maxMergeDocs).append(", ")
        sb.append("maxCFSSegmentSizeMB=").append(maxCFSSegmentSizeMB).append(", ")
        sb.append("noCFSRatio=").append(noCFSRatio)
        sb.append("]")
        return sb.toString()
    }

    companion object {
        /**
         * Defines the allowed range of log(size) for each level. A level is computed by taking the max
         * segment log size, minus LEVEL_LOG_SPAN, and finding all segments falling within that range.
         */
        const val LEVEL_LOG_SPAN: Double = 0.75

        /** Default merge factor, which is how many segments are merged at a time  */
        const val DEFAULT_MERGE_FACTOR: Int = 10

        /**
         * Default maximum segment size. A segment of this size or larger will never be merged. @see
         * setMaxMergeDocs
         */
        const val DEFAULT_MAX_MERGE_DOCS: Int = Int.MAX_VALUE

        /**
         * Default noCFSRatio. If a merge's size is `>= 10%` of the index, then we disable compound
         * file for it.
         *
         * @see MergePolicy.setNoCFSRatio
         */
        const val DEFAULT_NO_CFS_RATIO: Double = 0.1
    }
}
