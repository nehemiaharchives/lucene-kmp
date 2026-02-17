package org.gnit.lucenekmp.tests.index

import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.tests.index.MergeReaderWrapper
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.MergePolicy
import org.gnit.lucenekmp.index.MergeTrigger
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.index.SegmentInfos
import org.gnit.lucenekmp.index.SlowCodecReaderWrapper
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.FutureTask
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlinx.coroutines.CancellationException
import org.gnit.lucenekmp.jdkport.assert
import kotlin.math.min
import kotlin.random.Random


/** MergePolicy that makes random decisions for testing.  */
class MockRandomMergePolicy(random: Random) : MergePolicy() {
    // fork a private random, since we are called
    // unpredictably from threads:
    private val random: Random = Random(random.nextLong())

    /**
     * Set to true if sometimes readers to be merged should be wrapped in a FilterReader to mixup bulk
     * merging.
     */
    var doNonBulkMerges: Boolean = true

    override fun findMerges(
        mergeTrigger: MergeTrigger?,
        segmentInfos: SegmentInfos?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        var mergeSpec: MergeSpecification? = null

        // System.out.println("MRMP: findMerges sis=" + segmentInfos);
        val segments: MutableList<SegmentCommitInfo> = mutableListOf()
        val merging: MutableSet<SegmentCommitInfo> =
            mergeContext!!.mergingSegments

        for (sipc in segmentInfos!!) {
            if (!merging.contains(sipc)) {
                segments.add(sipc)
            }
        }

        val numSegments = segments.size

        if (numSegments > 1 && (numSegments > 30 || random.nextInt(5) == 3)) {
            segments.shuffle(random)

            // TODO: sometimes make more than 1 merge
            mergeSpec = MergeSpecification()
            val segsToMerge: Int =
                TestUtil.nextInt(random, 1, numSegments)
            if (doNonBulkMerges && random.nextBoolean()) {
                mergeSpec.add(
                    MockRandomOneMerge(
                        segments.subList(0, segsToMerge),
                        random.nextLong()
                    )
                )
            } else {
                mergeSpec.add(
                    OneMerge(
                        segments.subList(
                            0,
                            segsToMerge
                        )
                    )
                )
            }
        }

        return mergeSpec
    }

    @Throws(IOException::class)
    override fun findForcedMerges(
        segmentInfos: SegmentInfos?,
        maxSegmentCount: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        val eligibleSegments: MutableList<SegmentCommitInfo> = mutableListOf()
        for (info in segmentInfos!!) {
            if (segmentsToMerge!!.containsKey(info)) {
                eligibleSegments.add(info)
            }
        }

        // System.out.println("MRMP: findMerges sis=" + segmentInfos + " eligible=" + eligibleSegments);
        var mergeSpec: MergeSpecification? = null
        if (eligibleSegments.size > 1
            || (eligibleSegments.size == 1
                    && isMerged(segmentInfos, eligibleSegments[0], mergeContext!!) == false)
        ) {
            mergeSpec = MergeSpecification()
            // Already shuffled having come out of a set but
            // shuffle again for good measure:
            eligibleSegments.shuffle(random)
            var upto = 0
            while (upto < eligibleSegments.size) {
                val max = min(10, eligibleSegments.size - upto)
                val inc = if (max <= 2) max else TestUtil.nextInt(
                    random,
                    2,
                    max
                )
                if (doNonBulkMerges && random.nextBoolean()) {
                    mergeSpec.add(
                        MockRandomOneMerge(
                            eligibleSegments.subList(upto, upto + inc), random.nextLong()
                        )
                    )
                } else {
                    mergeSpec.add(
                        OneMerge(
                            eligibleSegments.subList(
                                upto,
                                upto + inc
                            )
                        )
                    )
                }
                upto += inc
            }
        }

        if (mergeSpec != null) {
            for (merge in mergeSpec.merges) {
                for (info in merge.segments) {
                    assert(segmentsToMerge!!.containsKey(info))
                }
            }
        }
        return mergeSpec
    }

    @Throws(IOException::class)
    override fun findForcedDeletesMerges(
        segmentInfos: SegmentInfos?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        return findMerges(null, segmentInfos, mergeContext)
    }

    @Throws(IOException::class)
    override fun findFullFlushMerges(
        mergeTrigger: MergeTrigger,
        segmentInfos: SegmentInfos,
        mergeContext: MergeContext
    ): MergeSpecification? {
        val mergeSpecification: MergeSpecification? =
            findMerges(null, segmentInfos, mergeContext)
        if (mergeSpecification == null) {
            return null
        }
        // Do not return any merges involving already-merging segments.
        val filteredMergeSpecification: MergeSpecification =
            MergeSpecification()
        for (oneMerge in mergeSpecification.merges) {
            var filtered = false
            val nonMergingSegments: MutableList<SegmentCommitInfo> = mutableListOf()
            for (sci in oneMerge.segments) {
                if (!mergeContext.mergingSegments.contains(sci)) {
                    nonMergingSegments.add(sci)
                } else {
                    filtered = true
                }
            }
            if (filtered == true) {
                if (nonMergingSegments.isNotEmpty()) {
                    filteredMergeSpecification.add(
                        OneMerge(
                            nonMergingSegments
                        )
                    )
                }
            } else {
                filteredMergeSpecification.add(oneMerge)
            }
        }
        if (filteredMergeSpecification.merges.isNotEmpty()) {
            return filteredMergeSpecification
        }
        return null
    }

    @Throws(IOException::class)
    override fun useCompoundFile(
        infos: SegmentInfos,
        mergedInfo: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Boolean {
        // 80% of the time we create CFS:
        return random.nextInt(5) != 1
    }

    internal class MockRandomOneMerge(
        segments: MutableList<SegmentCommitInfo>,
        seed: Long
    ) : OneMerge(segments) {
        val r: Random = Random(seed)

        @Throws(IOException::class)
        override fun wrapForMerge(reader: CodecReader): CodecReader {
            // wrap it (e.g. prevent bulk merge etc)
            // TODO: cut this over to FilterCodecReader api, we can explicitly
            // enable/disable bulk merge for portions of the index we want.

            val thingToDo: Int = r.nextInt(7)
            if (thingToDo == 0) {
                // simple no-op FilterReader
                if (LuceneTestCase.VERBOSE) {
                    println(
                        "NOTE: MockRandomMergePolicy now swaps in a SlowCodecReaderWrapper for merging reader="
                                + reader
                    )
                }
                return SlowCodecReaderWrapper.wrap(
                    object : FilterLeafReader(MergeReaderWrapper(reader)) {
                        override val coreCacheHelper: CacheHelper?
                            get() = `in`.coreCacheHelper

                        override val readerCacheHelper: CacheHelper?
                            get() = `in`.readerCacheHelper
                    })
            } else if (thingToDo == 1) {
                // renumber fields
                // NOTE: currently this only "blocks" bulk merges just by
                // being a FilterReader. But it might find bugs elsewhere,
                // and maybe the situation can be improved in the future.
                if (LuceneTestCase.VERBOSE) {
                    println(
                        "NOTE: MockRandomMergePolicy now swaps in a MismatchedLeafReader for merging reader="
                                + reader
                    )
                }
                return MismatchedCodecReader(reader, r)
            } else {
                // otherwise, reader is unchanged
                return reader
            }
        }

        @Throws(IOException::class)
        override fun reorder(
            reader: CodecReader,
            dir: Directory,
            executor: Executor
        ): Sorter.DocMap? {
            if (r.nextBoolean()) {
                if (LuceneTestCase.VERBOSE) {
                    println("NOTE: MockRandomMergePolicy now reverses reader=$reader")
                }
                // Reverse the doc ID order
                return reverse(reader)
            }
            if (executor != null && r.nextBoolean()) {
                // submit random work to the executor
                val dummyRunnable: Runnable = Runnable {}
                val task: FutureTask<Unit> = FutureTask(dummyRunnable, null)
                executor.execute(task)
                try {
                    runBlocking{ task.get() }
                } catch (e: /*InterruptedException*/ CancellationException) {
                    throw ThreadInterruptedException(e)
                } catch (e: ExecutionException) {
                    throw IOUtils.rethrowAlways(e.cause ?: e)
                }
            }
            return null
        }
    }

    companion object {
        @Throws(IOException::class)
        fun reverse(reader: CodecReader): Sorter.DocMap {
            val maxDoc: Int = reader.maxDoc()
            val parents: BitSet?
            if (reader.fieldInfos.parentField == null) {
                parents = null
            } else {
                parents =
                    BitSet.of(
                        DocValues.getNumeric(
                            reader,
                            reader.fieldInfos.parentField!!
                        ), maxDoc
                    )
            }
            return object : Sorter.DocMap() {
                override fun size(): Int {
                    return maxDoc
                }

                override fun oldToNew(docID: Int): Int {
                    if (parents == null) {
                        return maxDoc - 1 - docID
                    } else {
                        val oldBlockStart = if (docID == 0) 0 else parents.prevSetBit(docID - 1) + 1
                        val oldBlockEnd: Int = parents.nextSetBit(docID)
                        val newBlockEnd = maxDoc - 1 - oldBlockStart
                        return newBlockEnd - (oldBlockEnd - docID)
                    }
                }

                override fun newToOld(docID: Int): Int {
                    if (parents == null) {
                        return maxDoc - 1 - docID
                    } else {
                        val oldBlockEnd: Int = parents.nextSetBit(maxDoc - 1 - docID)
                        val newBlockEnd = oldToNew(oldBlockEnd)
                        return oldBlockEnd - (newBlockEnd - docID)
                    }
                }
            }
        }
    }
}
