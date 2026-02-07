package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.MergePolicy.MergeContext
import org.gnit.lucenekmp.index.MergePolicy.MergeSpecification
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.BaseMergePolicyTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalAtomicApi::class)
class TestTieredMergePolicy : BaseMergePolicyTestCase() {

    private data class DocCountAndSizeInBytes(val docCount: Int, val sizeInBytes: Long)

    public override fun mergePolicy(): TieredMergePolicy {
        return newTieredMergePolicy()
    }

    @Throws(IOException::class)
    override fun assertSegmentInfos(policy: MergePolicy, infos: SegmentInfos) {
        val tmp =
            policy as TieredMergePolicy

        val maxMergedSegmentBytes = (tmp.maxMergedSegmentMB * 1024 * 1024).toLong()

        var minSegmentBytes = Long.MAX_VALUE
        var totalDelCount = 0
        var totalMaxDoc = 0
        var totalBytes: Long = 0
        val segmentSizes: MutableList<DocCountAndSizeInBytes> = mutableListOf()
        for (sci in infos) {
            totalDelCount += sci.delCount
            totalMaxDoc += sci.info.maxDoc()
            val byteSize: Long = sci.sizeInBytes()
            val liveRatio: Double = 1 - sci.delCount.toDouble() / sci.info.maxDoc()
            val weightedByteSize = (liveRatio * byteSize).toLong()
            totalBytes += weightedByteSize
            segmentSizes.add(
                DocCountAndSizeInBytes(sci.info.maxDoc() - sci.delCount, weightedByteSize)
            )
            minSegmentBytes = min(minSegmentBytes, weightedByteSize)
        }
        segmentSizes.sortWith(compareBy(DocCountAndSizeInBytes::sizeInBytes))

        val delPercentage = 100.0 * totalDelCount / totalMaxDoc
        assertTrue(
            delPercentage <= tmp.deletesPctAllowed,
            "Percentage of deleted docs " + delPercentage + " is larger than the target: " + tmp.deletesPctAllowed
        )

        var levelSizeBytes = max(minSegmentBytes, (tmp.floorSegmentMB * 1024 * 1024).toLong())
        var bytesLeft = totalBytes
        var allowedSegCount = 0.0
        var biggestSegments = segmentSizes
        if (biggestSegments.size > tmp.targetSearchConcurrency - 1) {
            biggestSegments =
                biggestSegments.subList(
                    biggestSegments.size - tmp.targetSearchConcurrency + 1,
                    biggestSegments.size
                )
        }
        // Allow whole segments for the targetSearchConcurrency-1 biggest segments
        for (size in biggestSegments) {
            bytesLeft -= size.sizeInBytes
            allowedSegCount++
        }

        var tooBigCount = 0
        for (size in segmentSizes) {
            if (size.sizeInBytes >= maxMergedSegmentBytes / 2) {
                tooBigCount++
            }
        }

        // below we make the assumption that segments that reached the max segment
        // size divided by 2 don't need merging anymore
        val mergeFactor = tmp.segmentsPerTier.toInt()
        while (true) {
            val segCountLevel = bytesLeft / levelSizeBytes.toDouble()
            if (segCountLevel <= tmp.segmentsPerTier || levelSizeBytes >= maxMergedSegmentBytes / 2) {
                allowedSegCount += ceil(segCountLevel)
                break
            }
            allowedSegCount += tmp.segmentsPerTier
            bytesLeft = (bytesLeft - tmp.segmentsPerTier * levelSizeBytes).toLong()
            levelSizeBytes = min(levelSizeBytes * mergeFactor, maxMergedSegmentBytes / 2)
        }
        // Allow at least a full tier in addition of the too big segments.
        allowedSegCount = max(allowedSegCount, tooBigCount + tmp.segmentsPerTier)
        // Allow at least `targetSearchConcurrency` segments.
        allowedSegCount = max(allowedSegCount, tmp.targetSearchConcurrency.toDouble())

        // It's ok to be over the allowed segment count if none of the merges are legal, because they
        // are either not balanced or because they exceed the max merged segment doc count.
        // We only check pairwise merges instead of every possible merge to keep things simple. If none
        // of the pairwise merges are legal, chances are high that no merge is legal.
        val maxDocsPerSegment: Int = tmp.getMaxAllowedDocs(infos.totalMaxDoc(), totalDelCount)
        var hasLegalMerges = false
        for (i in 0..<segmentSizes.size - 1) {
            val size1 = segmentSizes[i]
            val size2 = segmentSizes[i + 1]
            val mergedSegmentSizeInBytes = size1.sizeInBytes + size2.sizeInBytes
            val mergedSegmentDocCount = size1.docCount + size2.docCount

            if (mergedSegmentSizeInBytes <= maxMergedSegmentBytes && size2.sizeInBytes * 1.5 <= mergedSegmentSizeInBytes && mergedSegmentDocCount <= maxDocsPerSegment) {
                hasLegalMerges = true
                break
            }
        }

        val numSegments: Int = infos.asList().size
        assertTrue(
            numSegments <= allowedSegCount || hasLegalMerges == false,
            "mergeFactor=$mergeFactor minSegmentBytes=$minSegmentBytes maxMergedSegmentBytes=$maxMergedSegmentBytes segmentsPerTier=${tmp.segmentsPerTier} numSegments=$numSegments allowed=$allowedSegCount totalBytes=$totalBytes delPercentage=$delPercentage deletesPctAllowed=${tmp.deletesPctAllowed} targetNumSegments=${tmp.targetSearchConcurrency}"
        )
    }

    override fun assertMerge(policy: MergePolicy, merges: MergeSpecification) {
        // anything to assert
    }

    @Test
    @Throws(Exception::class)
    fun testForceMergeDeletes() {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val tmp = newTieredMergePolicy()
        conf.setMergePolicy(tmp)
        conf.setMaxBufferedDocs(4)
        tmp.setSegmentsPerTier(100.0)
        tmp.setDeletesPctAllowed(50.0)
        tmp.setForceMergeDeletesPctAllowed(30.0)
        val w = IndexWriter(dir, conf)
        for (i in 0..79) {
            val doc = Document()
            doc.add(newTextField("content", "aaa " + (i % 4), Field.Store.NO))
            w.addDocument(doc)
        }
        assertEquals(80, w.getDocStats().maxDoc.toLong())
        assertEquals(80, w.getDocStats().numDocs.toLong())

        if (VERBOSE) {
            println("\nTEST: delete docs")
        }
        w.deleteDocuments(Term("content", "0"))
        w.forceMergeDeletes()

        assertEquals(80, w.getDocStats().maxDoc.toLong())
        assertEquals(60, w.getDocStats().numDocs.toLong())

        if (VERBOSE) {
            println("\nTEST: forceMergeDeletes2")
        }
        (w.config.mergePolicy as TieredMergePolicy).setForceMergeDeletesPctAllowed(10.0)
        w.forceMergeDeletes()
        assertEquals(60, w.getDocStats().maxDoc.toLong())
        assertEquals(60, w.getDocStats().numDocs.toLong())
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPartialMerge() {
        val num: Int = atLeast(10)
        for (iter in 0..<num) {
            if (VERBOSE) {
                println("TEST: iter=$iter")
            }
            val dir: Directory = newDirectory()
            val conf = newIndexWriterConfig(MockAnalyzer(random()))
            conf.setMergeScheduler(SerialMergeScheduler())
            val tmp = newTieredMergePolicy()
            conf.setMergePolicy(tmp)
            conf.setMaxBufferedDocs(2)
            tmp.setSegmentsPerTier(6.0)

            val w = IndexWriter(dir, conf)
            val numDocs: Int = TestUtil.nextInt(random(), 20, 100)
            for (i in 0..<numDocs) {
                val doc = Document()
                doc.add(newTextField("content", "aaa " + (i % 4), Field.Store.NO))
                w.addDocument(doc)
            }

            w.flush(true, true)

            val segmentCount: Int = w.getSegmentCount()
            val targetCount: Int = TestUtil.nextInt(
                random(),
                1,
                segmentCount
            )

            if (VERBOSE) {
                println("TEST: merge to $targetCount segs (current count=$segmentCount)")
            }
            w.forceMerge(targetCount)

            val maxSegmentSize: Double = max(tmp.maxMergedSegmentMB, tmp.floorSegmentMB)
            val max125Pct = ((maxSegmentSize * 1024.0 * 1024.0) * 1.25).toLong()
            // Other than in the case where the target count is 1 we can't say much except no segment
            // should be > 125% of max seg size.
            if (targetCount == 1) {
                assertEquals(
                    targetCount.toLong(),
                    w.getSegmentCount().toLong(),
                    "Should have merged down to one segment"
                )
            } else {
                // why can't we say much Well...
                // 1> the random numbers generated above mean we could have 10 segments and a target max
                // count of, say, 9. we
                //    could get there by combining only 2 segments. So tests like "no pair of segments
                // should total less than
                //    125% max segment size" aren't valid.
                //
                // 2> We could have 10 segments and a target count of 2. In that case there could be 5
                // segments resulting.
                //    as long as they're all < 125% max seg size, that's valid.
                for (info in w.cloneSegmentInfos()) {
                    assertTrue(max125Pct >= info.sizeInBytes(), "No segment should be more than 125% of max segment size ")
                }
            }

            w.close()
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testForceMergeDeletesMaxSegSize() {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val tmp = TieredMergePolicy()
        tmp.setMaxMergedSegmentMB(0.01)
        tmp.setForceMergeDeletesPctAllowed(0.0)
        conf.setMergePolicy(tmp)

        val w = IndexWriter(dir, conf)

        val numDocs: Int = atLeast(200)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(newStringField("id", "" + i, Field.Store.NO))
            doc.add(newTextField("content", "aaa $i", Field.Store.NO))
            w.addDocument(doc)
        }

        w.forceMerge(1)
        var r: IndexReader = DirectoryReader.open(w)
        assertEquals(numDocs.toLong(), r.maxDoc().toLong())
        assertEquals(numDocs.toLong(), r.numDocs().toLong())
        r.close()

        if (VERBOSE) {
            println("\nTEST: delete doc")
        }

        w.deleteDocuments(Term("id", "" + (42 + 17)))

        r = DirectoryReader.open(w)
        assertEquals(numDocs.toLong(), r.maxDoc().toLong())
        assertEquals((numDocs - 1).toLong(), r.numDocs().toLong())
        r.close()

        w.forceMergeDeletes()

        r = DirectoryReader.open(w)
        assertEquals((numDocs - 1).toLong(), r.maxDoc().toLong())
        assertEquals((numDocs - 1).toLong(), r.numDocs().toLong())
        r.close()

        w.close()

        dir.close()
    }

    // LUCENE-7976 makes findForceMergeDeletes and findForcedDeletes respect max segment size by
    // default, so ensure that this works.
    @Test
    @Throws(Exception::class)
    fun testForcedMergesRespectSegSize() {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val tmp = TieredMergePolicy()

        // Empirically, 100 docs the size below give us segments of 3,330 bytes. It's not all that
        // reliable in terms
        // of how big a segment _can_ get, so set it to prevent merges on commit.
        val mbSize = 0.004
        val maxSegBytes =
            ((1024.0 * 1024.0)).toLong() // fudge it up, we're trying to catch egregious errors and segbytes
        // don't really reflect the number for original merges.
        tmp.setMaxMergedSegmentMB(mbSize)
        conf.setMaxBufferedDocs(100)
        conf.setMergePolicy(tmp)
        conf.setMergeScheduler(SerialMergeScheduler())

        val w = IndexWriter(dir, conf)

        val numDocs: Int = atLeast(2400)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(newStringField("id", "" + i, Field.Store.NO))
            doc.add(newTextField("content", "aaa $i", Field.Store.NO))
            w.addDocument(doc)
        }

        w.commit()

        // These should be no-ops on an index with no deletions and segments are pretty big.
        var segNamesBefore = getSegmentNames(w)
        w.forceMergeDeletes()
        checkSegmentsInExpectations(w, segNamesBefore, false) // There should have been no merges.

        w.forceMerge(Int.MAX_VALUE)
        checkSegmentsInExpectations(w, segNamesBefore, true)
        checkSegmentSizeNotExceeded(w.cloneSegmentInfos(), maxSegBytes)

        // Delete 12-17% of each segment and expungeDeletes. This should result in:
        // > the same number of segments as before.
        // > no segments larger than maxSegmentSize.
        // > no deleted docs left.
        var remainingDocs = numDocs - deletePctDocsFromEachSeg(
            w,
            random().nextInt(5) + 12,
            true
        )
        w.forceMergeDeletes()
        w.commit()
        checkSegmentSizeNotExceeded(w.cloneSegmentInfos(), maxSegBytes)
        assertFalse(
            w.hasDeletions(), "There should be no deleted docs in the index."
        )

        // Check that deleting _fewer_ than 10% doesn't merge inappropriately. Nothing should be merged
        // since no segment
        // has had more than 10% of its docs deleted.
        segNamesBefore = getSegmentNames(w)
        var deletedThisPass = deletePctDocsFromEachSeg(w, random().nextInt(4) + 3,false)
        w.forceMergeDeletes()
        remainingDocs -= deletedThisPass
        checkSegmentsInExpectations(w, segNamesBefore, false) // There should have been no merges
        assertEquals(
            remainingDocs.toLong(),
            w.getDocStats().numDocs.toLong(),
            "NumDocs should reflect removed documents "
        )
        assertTrue(
            w.getDocStats().numDocs < w.getDocStats().maxDoc,
            "Should still be deleted docs in the index"
        )

        // This time, forceMerge. By default, this should respect max segment size.
        // Will change for LUCENE-8236
        w.forceMerge(Int.MAX_VALUE)
        checkSegmentSizeNotExceeded(w.cloneSegmentInfos(), maxSegBytes)

        // Now forceMerge down to one segment, there should be exactly remainingDocs in exactly one
        // segment.
        w.forceMerge(1)
        assertEquals(
            1,
            w.getSegmentCount().toLong(),
            "There should be exactly one segment now"
        )
        assertEquals(
            w.getDocStats().numDocs.toLong(),
            w.getDocStats().maxDoc.toLong(),
            "maxDoc and numDocs should be identical"
        )
        assertEquals(
            remainingDocs.toLong(),
            w.getDocStats().numDocs.toLong(),
            "There should be an exact number of documents in that one segment"
        )

        // Delete 5% and expunge, should be no change.
        segNamesBefore = getSegmentNames(w)
        remainingDocs -= deletePctDocsFromEachSeg(w, random().nextInt(5) + 1, false)
        w.forceMergeDeletes()
        checkSegmentsInExpectations(w, segNamesBefore, false)
        assertEquals(1, w.getSegmentCount().toLong(), "There should still be only one segment. ")
        assertTrue(w.getDocStats().numDocs < w.getDocStats().maxDoc, "The segment should have deleted documents")

        w.forceMerge(1) // back to one segment so deletePctDocsFromEachSeg still works

        // Test singleton merge for expungeDeletes
        remainingDocs -= deletePctDocsFromEachSeg(w, random().nextInt(5) + 20, true)
        w.forceMergeDeletes()

        assertEquals(
            1,
            w.getSegmentCount().toLong(),
            "There should still be only one segment. "
        )
        assertEquals(
            w.getDocStats().numDocs.toLong(),
            w.getDocStats().maxDoc.toLong(),
            "The segment should have no deleted documents"
        )

        // sanity check, at this point we should have an over`-large segment, we know we have exactly
        // one.
        assertTrue(
            w.getDocStats().numDocs > 1000, "Our single segment should have quite a few docs"
        )

        // Delete 60% of the documents and then add a few more docs and commit. This should "singleton
        // merge" the large segment
        // created above. 60% leaves some wriggle room, LUCENE-8263 will change this assumption and
        // should be tested
        // when we deal with that JIRA.
        deletedThisPass = deletePctDocsFromEachSeg(w, (w.getDocStats().numDocs * 60) / 100, true)
        remainingDocs -= deletedThisPass

        for (i in 0..49) {
            val doc = Document()
            doc.add(newStringField("id", "" + (i + numDocs), Field.Store.NO))
            doc.add(newTextField("content", "aaa $i",Field.Store.NO))
            w.addDocument(doc)
        }

        w.commit() // want to trigger merge no matter what.

        assertEquals(
            2,
            w.cloneSegmentInfos().size().toLong(),
            "There should be exactly one very large and one small segment"
        )
        val info0: SegmentCommitInfo = w.cloneSegmentInfos().info(0)
        val info1: SegmentCommitInfo = w.cloneSegmentInfos().info(1)
        val largeSegDocCount: Int = max(info0.info.maxDoc(), info1.info.maxDoc())
        val smallSegDocCount: Int = min(info0.info.maxDoc(), info1.info.maxDoc())
        assertEquals(
            largeSegDocCount.toLong(),
            remainingDocs.toLong(),
            "The large segment should have a bunch of docs"
        )
        assertEquals(
            smallSegDocCount.toLong(),
            50,
            "Small segment should have fewer docs"
        )

        w.close()

        dir.close()
    }

    /**
     * Returns how many segments are in the index after applying all merges from the `spec` to
     * an index with `startingSegmentCount` segments
     */
    private fun postMergesSegmentCount(startingSegmentCount: Int, spec: MergeSpecification): Int {
        var count = startingSegmentCount
        // remove the segments that are merged away
        for (merge in spec.merges) {
            count -= merge.segments.size
        }

        // add one for each newly merged segment
        count += spec.merges.size

        return count
    }

    // LUCENE-8688 reports that force-merges merged more segments that necessary to respect
    // maxSegmentCount as a result
    // of LUCENE-7976, so we ensure that it only does the minimum number of merges here.
    @Test
    @Throws(Exception::class)
    fun testForcedMergesUseLeastNumberOfMerges() {
        val tmp = TieredMergePolicy()
        var oneSegmentSizeMB = 1.0
        val maxMergedSegmentSizeMB = 10 * oneSegmentSizeMB
        tmp.setMaxMergedSegmentMB(maxMergedSegmentSizeMB)
        if (VERBOSE) {
            println("TEST: maxMergedSegmentSizeMB=$maxMergedSegmentSizeMB")
        }

        // create simulated 30 segment index where each segment is 1 MB
        var infos = SegmentInfos(Version.LATEST.major)
        val segmentCount = 30
        for (j in 0..<segmentCount) {
            infos.add(
                makeSegmentCommitInfo(
                    "_$j",
                    1000,
                    0,
                    oneSegmentSizeMB,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }

        var indexTotalSizeMB = segmentCount * oneSegmentSizeMB

        val maxSegmentCountAfterForceMerge: Int = random().nextInt(10) + 3
        if (VERBOSE) {
            println("TEST: maxSegmentCountAfterForceMerge=$maxSegmentCountAfterForceMerge")
        }
        var specification: MergeSpecification? =
            tmp.findForcedMerges(
                infos,
                maxSegmentCountAfterForceMerge,
                segmentsToMerge(infos),
                MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
            )
        if (VERBOSE) {
            println("TEST: specification=$specification")
        }
        assertMaxMergedSize(
            specification, maxMergedSegmentSizeMB, indexTotalSizeMB, maxSegmentCountAfterForceMerge
        )

        // verify we achieved exactly the max segment count post-force-merge (which is a bit odd -- the
        // API only ensures <= segments, not ==)
        // TODO: change this to a <= equality like the last assert below
        assertEquals(
            maxSegmentCountAfterForceMerge.toLong(),
            postMergesSegmentCount(infos.size(), specification!!).toLong()
        )

        // now create many segments index, containing 0.1 MB sized segments
        infos = SegmentInfos(Version.LATEST.major)
        val manySegmentsCount: Int = atLeast(100)
        if (VERBOSE) {
            println("TEST: manySegmentsCount=$manySegmentsCount")
        }
        oneSegmentSizeMB = 0.1
        for (j in 0..<manySegmentsCount) {
            infos.add(
                makeSegmentCommitInfo(
                    "_$j",
                    1000,
                    0,
                    oneSegmentSizeMB,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }

        indexTotalSizeMB = manySegmentsCount * oneSegmentSizeMB

        specification =
            tmp.findForcedMerges(
                infos,
                maxSegmentCountAfterForceMerge,
                segmentsToMerge(infos),
                MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
            )
        if (VERBOSE) {
            println("TEST: specification=$specification")
        }
        assertMaxMergedSize(specification, maxMergedSegmentSizeMB, indexTotalSizeMB, maxSegmentCountAfterForceMerge)
        assertTrue(postMergesSegmentCount(infos.size(), specification!!) >= maxSegmentCountAfterForceMerge)
    }

    // Make sure that TieredMergePolicy doesn't do the final merge while there are merges ongoing, but
    // does do non-final
    // merges while merges are ongoing.
    @Test
    @Throws(Exception::class)
    fun testForcedMergeWithPending() {
        val tmp = TieredMergePolicy()
        val maxSegmentSize = 10.0
        tmp.setMaxMergedSegmentMB(maxSegmentSize)

        val infos =
            SegmentInfos(Version.LATEST.major)
        for (j in 0..29) {
            infos.add(
                makeSegmentCommitInfo(
                    "_$j",
                    1000,
                    0,
                    1.0,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }
        val mergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        mergeContext.mergingSegments = mutableSetOf(infos.asList()[0])
        val expectedCount: Int = random().nextInt(10) + 3
        val specification: MergeSpecification? = tmp.findForcedMerges(infos, expectedCount, segmentsToMerge(infos), mergeContext)
        // Since we have fewer than 30 (the max merge count) segments more than the final size this
        // would have been the final merge, so we check that it was prevented.
        assertNull(specification)
    }

    // Having a segment with very few documents in it can happen because of the random nature of the
    // docs added to the index. For instance, let's say it just happens that the last segment has 3
    // docs in it.
    // It can easily be merged with a close-to-max sized segment during a forceMerge and still respect
    // the max segment
    // size.
    //
    // If the above is possible, the "twoMayHaveBeenMerged" will be true and we allow for a little
    // slop, checking that
    // exactly two segments are gone from the old list and exactly one is in the new list. Otherwise,
    // the lists must match
    // exactly.
    //
    // So forceMerge may not be a no-op, allow for that. There are two possibilities in forceMerge
    // only:
    // > there were no small segments, in which case the two lists will be identical
    // > two segments in the original list are replaced by one segment in the final list.
    //
    // finally, there are some cases of forceMerge where the expectation is that there be exactly no
    // differences.
    // this should be called after forceDeletesMerges with the boolean always false,
    // Depending on the state, forceMerge may call with the boolean true or false.
    fun checkSegmentsInExpectations(w: IndexWriter, segNamesBefore: MutableList<String>, twoMayHaveBeenMerged: Boolean) {
        val segNamesAfter = getSegmentNames(w)

        if (twoMayHaveBeenMerged == false || segNamesAfter.size == segNamesBefore.size) {
            if (segNamesAfter.size != segNamesBefore.size) {
                fail("Segment lists different sizes!: $segNamesBefore After list: $segNamesAfter")
            }

            if (segNamesAfter.containsAll(segNamesBefore) == false) {
                fail("Segment lists should be identical: $segNamesBefore After list: $segNamesAfter")
            }
            return
        }

        // forceMerge merged a tiny segment into a not-quite-max-sized segment so check that:
        // Two segments in the before list have been merged into one segment in the after list.
        if (segNamesAfter.size != segNamesBefore.size - 1) {
            fail("forceMerge didn't merge a small and large segment into one segment as expected: $segNamesBefore After list: $segNamesAfter")
        }

        // There should be exactly two segments in the before not in after and one in after not in
        // before.
        val testBefore: MutableList<String> = ArrayList(segNamesBefore)
        val testAfter: MutableList<String> = ArrayList(segNamesAfter)

        testBefore.removeAll(segNamesAfter)
        testAfter.removeAll(segNamesBefore)

        if (testBefore.size != 2 || testAfter.size != 1) {
            fail("Expected two unique 'before' segments and one unique 'after' segment: $segNamesBefore After list: $segNamesAfter")
        }
    }

    fun getSegmentNames(w: IndexWriter): MutableList<String> {
        val names: MutableList<String> = mutableListOf()
        for (info in w.cloneSegmentInfos()) {
            names.add(info.info.name)
        }
        return names
    }

    // Deletes some docs from each segment
    @Throws(IOException::class)
    fun deletePctDocsFromEachSeg(w: IndexWriter, pct: Int, roundUp: Boolean): Int {
        val reader: IndexReader = DirectoryReader.open(w)
        val toDelete: MutableList<Term> = mutableListOf()
        for (ctx in reader.leaves()) {
            toDelete.addAll(getRandTerms(ctx, pct, roundUp))
        }
        reader.close()

        val termsToDel: Array<Term> = toDelete.toTypedArray<Term>()

        w.deleteDocuments(*termsToDel)
        w.commit()
        return toDelete.size
    }

    // Get me some Ids to delete.
    // So far this supposes that there are no deleted docs in the segment.
    // When the numbers of docs in segments is small, rounding matters. So tests that want over a
    // percentage
    // pass "true" for roundUp, tests that want to be sure they're under some limit pass false.
    @Throws(IOException::class)
    private fun getRandTerms(ctx: LeafReaderContext, pct: Int, roundUp: Boolean): MutableList<Term> {
        assertFalse(ctx.reader().hasDeletions(), "This method assumes no deleted documents")
        // The indeterminate last segment is a pain, if we're there the number of docs is much less than
        // we expect
        val ret: MutableList<Term> = ArrayList(100)

        val numDocs = ctx.reader().numDocs().toDouble()
        val tmp = (numDocs * pct.toDouble()) / 100.0

        if (tmp <= 1.0) { // Calculations break down for segments with very few documents, the "tail end
            // Charlie"
            return ret
        }
        val mod = (numDocs / tmp).toInt()

        if (mod == 0) return ret

        val terms: Terms = ctx.reader().terms("id")!!
        val iter: TermsEnum = terms.iterator()
        var counter = 0

        // Small numbers are tricky, they're subject to off-by-one errors. bail if we're going to exceed
        // our target if we add another doc.
        var lim = (numDocs * pct.toDouble() / 100.0).toInt()
        if (roundUp) ++lim

        var br: BytesRef? = iter.next()
        while (br != null && ret.size < lim) {
            if ((counter % mod) == 0) {
                ret.add(Term("id", br))
            }
            ++counter
            br = iter.next()
        }
        return ret
    }

    @Throws(IOException::class)
    private fun checkSegmentSizeNotExceeded(infos: SegmentInfos, maxSegBytes: Long) {
        for (info in infos) {
            // assertTrue("Found an unexpectedly large segment: " + info.toString(), info.info.maxDoc() -
            // info.delCount <= docLim);
            assertTrue(info.sizeInBytes() <= maxSegBytes, "Found an unexpectedly large segment: $info")
        }
    }

    @Test
    fun testSetters() {
        val tmp = TieredMergePolicy()

        tmp.setMaxMergedSegmentMB(0.5)
        assertEquals(0.5, tmp.maxMergedSegmentMB, EPSILON)

        tmp.setMaxMergedSegmentMB(Double.POSITIVE_INFINITY)
        assertEquals(
            Long.MAX_VALUE / 1024.0 / 1024.0,
            tmp.maxMergedSegmentMB,
            EPSILON * Long.MAX_VALUE
        )

        tmp.setMaxMergedSegmentMB(Long.MAX_VALUE / 1024.0 / 1024.0)
        assertEquals(
            Long.MAX_VALUE / 1024.0 / 1024.0,
            tmp.maxMergedSegmentMB,
            EPSILON * Long.MAX_VALUE
        )

        expectThrows(IllegalArgumentException::class) {
            tmp.setMaxMergedSegmentMB(-2.0)
        }

        tmp.setFloorSegmentMB(2.0)
        assertEquals(2.0, tmp.floorSegmentMB, EPSILON)

        tmp.setFloorSegmentMB(Double.POSITIVE_INFINITY)
        assertEquals(
            Long.MAX_VALUE / 1024.0 / 1024.0,
            tmp.floorSegmentMB,
            EPSILON * Long.MAX_VALUE
        )

        tmp.setFloorSegmentMB(Long.MAX_VALUE / 1024.0 / 1024.0)
        assertEquals(
            Long.MAX_VALUE / 1024.0 / 1024.0,
            tmp.floorSegmentMB,
            EPSILON * Long.MAX_VALUE
        )

        expectThrows(IllegalArgumentException::class) {
            tmp.setFloorSegmentMB(-2.0)
        }

        tmp.maxCFSSegmentSizeMB = 2.0
        assertEquals(2.0, tmp.maxCFSSegmentSizeMB, EPSILON)

        tmp.maxCFSSegmentSizeMB = Double.POSITIVE_INFINITY
        assertEquals(
            Long.MAX_VALUE / 1024.0 / 1024.0,
            tmp.maxCFSSegmentSizeMB,
            EPSILON * Long.MAX_VALUE
        )

        tmp.maxCFSSegmentSizeMB = Long.MAX_VALUE / 1024.0 / 1024.0
        assertEquals(
            Long.MAX_VALUE / 1024.0 / 1024.0,
            tmp.maxCFSSegmentSizeMB,
            EPSILON * Long.MAX_VALUE
        )

        expectThrows(IllegalArgumentException::class) {
            tmp.maxCFSSegmentSizeMB = -2.0
        }

        // TODO: Add more checks for other non-double setters!
    }

    // LUCENE-5668
    @Test
    @Throws(Exception::class)
    fun testUnbalancedMergeSelection() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val tmp = iwc.mergePolicy as TieredMergePolicy
        tmp.setFloorSegmentMB(0.00001)
        // We need stable sizes for each segment:
        iwc.setCodec(TestUtil.getDefaultCodec())
        iwc.setMergeScheduler(SerialMergeScheduler())
        iwc.setMaxBufferedDocs(100)
        iwc.setRAMBufferSizeMB(-1.0)
        val w = IndexWriter(dir, iwc)
        for (i in 0..<15000 * RANDOM_MULTIPLIER) {
            val doc = Document()
            // Incompressible content so that merging 10 segments of size x creates a segment whose size
            // is about 10x
            val idBytes = ByteArray(128)
            random().nextBytes(idBytes)
            doc.add(StoredField("id", idBytes))
            w.addDocument(doc)
        }
        val r: IndexReader = DirectoryReader.open(w)

        // Make sure TMP always merged equal-number-of-docs segments:
        for (ctx in r.leaves()) {
            val numDocs: Int = ctx.reader().numDocs()
            assertTrue(numDocs == 100 || numDocs == 1000 || numDocs == 10000, "got numDocs=$numDocs")
        }
        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testManyMaxSizeSegments() {
        val policy = TieredMergePolicy()
        policy.setMaxMergedSegmentMB(1024.0) // 1GB
        val infos = SegmentInfos(Version.LATEST.major)
        val i = 0
        for (j in 0..29) {
            infos.add(
                makeSegmentCommitInfo(
                    "_$i",
                    1000,
                    0,
                    1024.0,
                    IndexWriter.SOURCE_MERGE
                )
            ) // max size
        }
        for (j in 0..7) {
            infos.add(
                makeSegmentCommitInfo(
                    "_$i",
                    1000,
                    0,
                    102.0,
                    IndexWriter.SOURCE_FLUSH
                )
            ) // 102MB flushes
        }

        // Only 8 segments on 1 tier in addition to the max-size segments, nothing to do
        var mergeSpec: MergeSpecification? =
            policy.findMerges(
                MergeTrigger.SEGMENT_FLUSH,
                infos,
                MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
            )
        assertNull(mergeSpec)

        for (j in 0..4) {
            infos.add(
                makeSegmentCommitInfo(
                    "_$i",
                    1000,
                    0,
                    102.0,
                    IndexWriter.SOURCE_FLUSH
                )
            ) // 102MB flushes
        }

        // Now 13 segments on 1 tier in addition to the max-size segments, 10 of them should get merged
        // in one merge
        mergeSpec = policy.findMerges(MergeTrigger.SEGMENT_FLUSH, infos, MockMergeContext { obj: SegmentCommitInfo -> obj.delCount })
        assertNotNull(mergeSpec)
        assertEquals(1, mergeSpec.merges.size.toLong())
        val merge: MergePolicy.OneMerge = mergeSpec.merges[0]
        assertEquals(10, merge.segments.size.toLong())
    }

    /** Make sure that singleton merges are considered when the max number of deletes is crossed.  */
    @Test
    @Throws(IOException::class)
    fun testMergePurelyToReclaimDeletes() {
        val mergePolicy = mergePolicy()
        var infos = SegmentInfos(Version.LATEST.major)
        // single 1GB segment with no deletes
        infos.add(
            makeSegmentCommitInfo(
                "_0",
                1000000,
                0,
                1024.0,
                IndexWriter.SOURCE_MERGE
            )
        )

        // not eligible for merging
        assertNull(mergePolicy.findMerges(MergeTrigger.EXPLICIT, infos, MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }))

        // introduce 15% deletes, still not eligible
        infos = applyDeletes(infos, (0.15 * 1000000).toInt())
        assertNull(mergePolicy.findMerges(MergeTrigger.EXPLICIT, infos, MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }))

        // now cross the delete threshold, becomes eligible
        infos = applyDeletes(infos, ((mergePolicy.deletesPctAllowed - 15 + 1) / 100 * 1000000).toInt())
        assertNotNull(mergePolicy.findMerges(MergeTrigger.EXPLICIT, infos, MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }))
    }

    @Test
    @Throws(IOException::class)
    override fun testSimulateAppendOnly() {
        val mergePolicy = mergePolicy()
        // Avoid low values of the max merged segment size which prevent this merge policy from scaling
        // well
        mergePolicy.setMaxMergedSegmentMB(TestUtil.nextInt(random(), 1024, 10 * 1024).toDouble())
        doTestSimulateAppendOnly(mergePolicy, 100000000, 10000)
    }

    @Test
    @Throws(IOException::class)
    override fun testSimulateUpdates() {
        val mergePolicy = mergePolicy()
        // Avoid low values of the max merged segment size which prevent this merge policy from scaling
        // well
        mergePolicy.setMaxMergedSegmentMB(TestUtil.nextInt(random(), 1024, 10 * 1024).toDouble())
        val numDocs: Int = if (TEST_NIGHTLY) atLeast(10000000) else atLeast(1000000)
        doTestSimulateUpdates(mergePolicy, numDocs, 2500)
    }

    @Test
    @Throws(IOException::class)
    fun testMergeSizeIsLessThanFloorSize() {
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }

        val infos = SegmentInfos(Version.LATEST.major)
        // 50 1MB segments
        for (i in 0..49) {
            infos.add(
                makeSegmentCommitInfo(
                    "_0",
                    1000000,
                    0,
                    1.0,
                    IndexWriter.SOURCE_FLUSH
                )
            )
        }

        val mergePolicy = TieredMergePolicy()
        mergePolicy.setFloorSegmentMB(0.1)

        // Segments are above the floor segment size, we get 4 merges of mergeFactor=10 segments each
        var mergeSpec: MergeSpecification? = mergePolicy.findMerges(MergeTrigger.FULL_FLUSH, infos, mergeContext)
        assertNotNull(mergeSpec)
        assertEquals(4, mergeSpec.merges.size.toLong())
        for (oneMerge in mergeSpec.merges) {
            assertEquals(
                mergePolicy.segmentsPerTier,
                oneMerge.segments.size.toDouble(),
                0.0
            )
        }

        // Segments are below the floor segment size and it takes 15 segments to go above the floor
        // segment size. We get 3 merges of 15 segments each
        mergePolicy.setFloorSegmentMB(15.0)
        mergeSpec = mergePolicy.findMerges(MergeTrigger.FULL_FLUSH, infos, mergeContext)
        assertNotNull(mergeSpec)
        assertEquals(3, mergeSpec.merges.size.toLong())
        for (oneMerge in mergeSpec.merges) {
            assertEquals(15, oneMerge.segments.size.toLong())
        }

        // Segments are below the floor segment size. We get one merge that merges the 50 segments
        // together.
        mergePolicy.setFloorSegmentMB(60.0)
        mergeSpec = mergePolicy.findMerges(MergeTrigger.FULL_FLUSH, infos, mergeContext)
        assertNotNull(mergeSpec)
        assertEquals(1, mergeSpec.merges.size.toLong())
        assertEquals(50, mergeSpec.merges[0].segments.size.toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testFullFlushMerges() {
        val segNameGenerator = AtomicLong(0)
        val stats = IOStats()
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos = SegmentInfos(Version.LATEST.major)

        val mp = TieredMergePolicy()

        for (i in 0..30) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    1,
                    0,
                    Double.MIN_VALUE,
                    IndexWriter.SOURCE_FLUSH
                )
            )
        }
        val spec: MergeSpecification? = mp.findFullFlushMerges(MergeTrigger.FULL_FLUSH, segmentInfos, mergeContext)
        assertNotNull(spec)
        for (merge in spec.merges) {
            segmentInfos = applyMerge(segmentInfos, merge, "_" + segNameGenerator.fetchAndIncrement(), stats)
        }
        assertEquals(1, segmentInfos.size().toLong())
    }

    // tests inherited from BaseMergePolicyTestCase

    @Test
    override fun testForceMergeNotNeeded() = super.testForceMergeNotNeeded()

    @Test
    override fun testFindForcedDeletesMerges() = super.testFindForcedDeletesMerges()

    @Test
    override fun testNoPathologicalMerges() = super.testNoPathologicalMerges()

    companion object {
        // verify that all merges in the spec do not create a final merged segment size too much bigger
        // than the configured maxMergedSegmentSizeMb
        @Throws(IOException::class)
        private fun assertMaxMergedSize(specification: MergeSpecification?, maxMergedSegmentSizeMB: Double, indexTotalSizeInMB: Double, maxMergedSegmentCount: Int) {
            // When the two configs conflict, TMP favors the requested number of segments. I.e., it will
            // produce
            // too-large (> maxMergedSegmentMB) merged segments.

            val maxMBPerSegment = indexTotalSizeInMB / maxMergedSegmentCount

            for (merge in specification!!.merges) {
                // compute total size of all segments being merged
                var mergeTotalSizeInBytes: Long = 0
                for (segment in merge.segments) {
                    mergeTotalSizeInBytes += segment.sizeInBytes()
                }

                // we allow up to 50% "violation" of the configured maxMergedSegmentSizeMb (why TMP itself is
                // on only adding 25% fudge factor):
                // TODO: drop this fudge factor back down to 25% -- TooMuchFudgeException!
                val limitBytes = (1024 * 1024 * max(maxMBPerSegment, maxMergedSegmentSizeMB) * 1.5).toLong()
                assertTrue(
                    mergeTotalSizeInBytes < limitBytes,
                    "mergeTotalSizeInBytes=$mergeTotalSizeInBytes limitBytes=$limitBytes maxMergedSegmentSizeMb=$maxMergedSegmentSizeMB"
                )
            }
        }

        private fun segmentsToMerge(infos: SegmentInfos): MutableMap<SegmentCommitInfo, Boolean> {
            val segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean> = mutableMapOf()
            for (info in infos) {
                segmentsToMerge[info] = true
            }
            return segmentsToMerge
        }

        private const val EPSILON = 1E-14
    }
}
