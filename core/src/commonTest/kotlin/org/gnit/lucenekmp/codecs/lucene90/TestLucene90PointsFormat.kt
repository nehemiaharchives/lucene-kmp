package org.gnit.lucenekmp.codecs.lucene90

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.BasePointsFormatTestCase
import org.gnit.lucenekmp.tests.index.MockRandomMergePolicy
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.NativeCrashProbe
import org.gnit.lucenekmp.util.bkd.BKDConfig
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLucene90PointsFormat : BasePointsFormatTestCase() {
    private val logger = KotlinLogging.logger {}
    override val codec: Codec
    private val maxPointsInLeafNode: Int

    init {
        // standard issue
        val defaultCodec: Codec = TestUtil.getDefaultCodec()
        if (random().nextBoolean()) {
            // randomize parameters
            maxPointsInLeafNode = TestUtil.nextInt(random(), 50, 500)
            val maxMBSortInHeap: Double = 3.0 + (3 * random().nextDouble())
            if (VERBOSE) {
                println(("TEST: using Lucene60PointsFormat with maxPointsInLeafNode=$maxPointsInLeafNode and maxMBSortInHeap=$maxMBSortInHeap"))
            }

            // sneaky impersonation!
            codec =
                object : FilterCodec(defaultCodec.name, defaultCodec) {
                    override fun pointsFormat(): PointsFormat {
                        return object : PointsFormat() {
                            @Throws(IOException::class)
                            override fun fieldsWriter(writeState: SegmentWriteState): PointsWriter {
                                return Lucene90PointsWriter(writeState, maxPointsInLeafNode, maxMBSortInHeap)
                            }

                            @Throws(IOException::class)
                            override fun fieldsReader(readState: SegmentReadState): PointsReader {
                                return Lucene90PointsReader(readState)
                            }
                        }
                    }
                }
        } else {
            // standard issue
            codec = defaultCodec
            maxPointsInLeafNode = BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE
        }
    }

    /*override fun getCodec(): Codec {
        return codec
    }*/

    @Test
    @Throws(Exception::class)
    override fun testMergeStability() {
        if (codec is FilterCodec || !mergeIsStable()) {
            return
        }
        super.testMergeStability()
    }

    // TODO: clean up the math/estimation here rather than suppress so many warnings
    @Test
    @Throws(IOException::class)
    fun testEstimatePointCount() {
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Avoid mockRandomMP since it may cause non-optimal merges that make the
        // number of points per leaf hard to predict
        while (iwc.mergePolicy is MockRandomMergePolicy) {
            iwc.setMergePolicy(newMergePolicy())
        }
        val w = IndexWriter(dir, iwc)
        val pointValue = ByteArray(3)
        val uniquePointValue = ByteArray(3)
        random().nextBytes(uniquePointValue)
        val numDocs: Int = if (TEST_NIGHTLY) atLeast(10000) else atLeast(500) // at night, make sure we have several leaves
        val multiValues: Boolean = random().nextBoolean()
        var totalValues = 0
        for (i in 0..<numDocs) {
            val doc = Document()
            if (i == numDocs / 2) {
                totalValues++
                doc.add(BinaryPoint("f", arrayOf(uniquePointValue)))
            } else {
                val numValues = if (multiValues) TestUtil.nextInt(random(), 2, 100) else 1
                for (j in 0..<numValues) {
                    do {
                        random().nextBytes(pointValue)
                    } while (pointValue.contentEquals(uniquePointValue))
                    doc.add(BinaryPoint("f", arrayOf(pointValue)))
                    totalValues++
                }
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val r: IndexReader = DirectoryReader.open(w)
        w.close()
        val lr: LeafReader = getOnlyLeafReader(r)
        val points: PointValues = lr.getPointValues("f")!!

        val allPointsVisitor: PointValues.IntersectVisitor =
            object : PointValues.IntersectVisitor {
                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_INSIDE_QUERY
                }
            }

        assertEquals(totalValues.toLong(), points.estimatePointCount(allPointsVisitor))
        assertEquals(numDocs.toLong(), points.estimateDocCount(allPointsVisitor))

        val noPointsVisitor: PointValues.IntersectVisitor =
            object : PointValues.IntersectVisitor {
                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_OUTSIDE_QUERY
                }
            }

        // Return 0 if no points match
        assertEquals(0, points.estimatePointCount(noPointsVisitor))
        assertEquals(0, points.estimateDocCount(noPointsVisitor))

        val onePointMatchVisitor: PointValues.IntersectVisitor =
            object : PointValues.IntersectVisitor {
                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    if (Arrays.compareUnsigned(uniquePointValue, 0, 3, maxPackedValue, 0, 3) > 0
                        || Arrays.compareUnsigned(uniquePointValue, 0, 3, minPackedValue, 0, 3) < 0
                    ) {
                        return PointValues.Relation.CELL_OUTSIDE_QUERY
                    }
                    return PointValues.Relation.CELL_CROSSES_QUERY
                }
            }

        // If only one point matches, then the point count is (maxPointsInLeafNode + 1) / 2
        // in general, or maybe 2x that if the point is a split value
        val pointCount: Long = points.estimatePointCount(onePointMatchVisitor)
        val lastNodePointCount = (totalValues % maxPointsInLeafNode).toLong()
        assertTrue(
            pointCount == ((maxPointsInLeafNode + 1) / 2).toLong() // common case
                    || pointCount == (lastNodePointCount + 1) / 2 // not fully populated leaf
                    || pointCount == (2 * ((maxPointsInLeafNode + 1) / 2)).toLong() // if the point is a split value
                    || (pointCount
                    == ((maxPointsInLeafNode + 1) / 2)
                    + ((lastNodePointCount + 1)
                    / 2)),
            "" + pointCount
        ) // if the point is a split value and one leaf is not fully populated

        val docCount: Long = points.estimateDocCount(onePointMatchVisitor)

        if (multiValues) {
            assertEquals(
                docCount,
                (docCount
                        * (1.0
                        - ((numDocs - pointCount) / points.size()).toDouble().pow((points.size() / docCount).toDouble()))).toLong()
            )
        } else {
            assertEquals(min(pointCount, numDocs.toLong()), docCount)
        }
        r.close()
        dir.close()
    }

    // The tree is always balanced in the N dims case, and leaves are
    // not all full so things are a bit different
    // TODO: clean up the math/estimation here rather than suppress so many warnings
    @Test
    @Throws(IOException::class)
    fun testEstimatePointCount2Dims() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val pointValue: Array<ByteArray> = arrayOfNulls<ByteArray>(2) as Array<ByteArray>
        pointValue[0] = ByteArray(3)
        pointValue[1] = ByteArray(3)
        val uniquePointValue: Array<ByteArray> = arrayOfNulls<ByteArray>(2) as Array<ByteArray>
        uniquePointValue[0] = ByteArray(3)
        uniquePointValue[1] = ByteArray(3)
        random().nextBytes(uniquePointValue[0])
        random().nextBytes(uniquePointValue[1])
        val numDocs: Int =
            if (TEST_NIGHTLY)
                atLeast(10000)
            else
                atLeast(1000) // in nightly, make sure we have several leaves
        val multiValues: Boolean = random().nextBoolean()
        var totalValues = 0
        for (i in 0..<numDocs) {
            val doc = Document()
            if (i == numDocs / 2) {
                doc.add(BinaryPoint("f", uniquePointValue))
                totalValues++
            } else {
                val numValues = if (multiValues) TestUtil.nextInt(random(), 2, 100) else 1
                for (j in 0..<numValues) {
                    do {
                        random().nextBytes(pointValue[0])
                        random().nextBytes(pointValue[1])
                    } while (pointValue[0].contentEquals(uniquePointValue[0]) || pointValue[1].contentEquals(uniquePointValue[1]))
                    doc.add(BinaryPoint("f", pointValue))
                    totalValues++
                }
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val r: IndexReader = DirectoryReader.open(w)
        w.close()
        val lr: LeafReader = getOnlyLeafReader(r)
        val points: PointValues = lr.getPointValues("f")!!

        val allPointsVisitor: PointValues.IntersectVisitor =
            object : PointValues.IntersectVisitor {
                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_INSIDE_QUERY
                }
            }

        assertEquals(totalValues.toLong(), points.estimatePointCount(allPointsVisitor))
        assertEquals(numDocs.toLong(), points.estimateDocCount(allPointsVisitor))

        val noPointsVisitor: PointValues.IntersectVisitor =
            object : PointValues.IntersectVisitor {
                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_OUTSIDE_QUERY
                }
            }

        // Return 0 if no points match
        assertEquals(0, points.estimatePointCount(noPointsVisitor))
        assertEquals(0, points.estimateDocCount(noPointsVisitor))

        val onePointMatchVisitor: PointValues.IntersectVisitor =
            object : PointValues.IntersectVisitor {
                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    for (dim in 0..1) {
                        if ((Arrays.compareUnsigned(
                                uniquePointValue[dim], 0, 3, maxPackedValue, dim * 3, dim * 3 + 3
                            )
                                    > 0)
                            || (Arrays.compareUnsigned(
                                uniquePointValue[dim], 0, 3, minPackedValue, dim * 3, dim * 3 + 3
                            )
                                    < 0)
                        ) {
                            return PointValues.Relation.CELL_OUTSIDE_QUERY
                        }
                    }
                    return PointValues.Relation.CELL_CROSSES_QUERY
                }
            }

        val pointCount: Long = points.estimatePointCount(onePointMatchVisitor)
        val lastNodePointCount = (totalValues % maxPointsInLeafNode).toLong()
        assertTrue(
            pointCount == ((maxPointsInLeafNode + 1) / 2).toLong() // common case
                    || pointCount == (lastNodePointCount + 1) / 2 // not fully populated leaf
                    || pointCount == (2 * ((maxPointsInLeafNode + 1) / 2)).toLong() // if the point is a split value
                    || pointCount == ((maxPointsInLeafNode + 1) / 2) + ((lastNodePointCount + 1) / 2) // in extreme cases, a point can be shared by 4 leaves
                    || pointCount == (4 * ((maxPointsInLeafNode + 1) / 2)).toLong() || pointCount == 3 * ((maxPointsInLeafNode + 1) / 2) + ((lastNodePointCount + 1) / 2),
            "" + pointCount
        )

        val docCount: Long = points.estimateDocCount(onePointMatchVisitor)
        if (multiValues) {
            assertEquals(
                docCount,
                (docCount
                        * (1.0
                        - ((numDocs - pointCount) / points.size()).toDouble().pow((points.size() / docCount).toDouble()))).toLong()
            )
        } else {
            assertEquals(min(pointCount, numDocs.toLong()), docCount)
        }
        r.close()
        dir.close()
    }

    // tests inherited from BasePointsFormatTestCase

    @Test
    override fun testBasic() = super.testBasic()

    @Test
    override fun testMerge() = super.testMerge()

    @Test
    override fun testAllPointDocsDeletedInSegment() = super.testAllPointDocsDeletedInSegment()

    @Test
    override fun testWithExceptions() {
        configureTestLogging()
        val totalRuns = 1000
        val runTimeout = 10.seconds
        repeat(totalRuns) { runIndex ->
            val run = runIndex + 1
            val startedAt = TimeSource.Monotonic.markNow()
            NativeCrashProbe.mark(run, 0, NativeCrashProbe.PHASE_TEST_RUN_START)
            logger.error { "TestLucene90PointsFormat.testWithExceptions run=$run/$totalRuns start codec=${codec.name}" }
            try {
                val scope = CoroutineScope(Dispatchers.Default)
                val deferred = scope.async {
                    NativeCrashProbe.mark(run, 0, NativeCrashProbe.PHASE_TEST_SUPER_CALL_ENTER)
                    super.testWithExceptions()
                    NativeCrashProbe.mark(run, 0, NativeCrashProbe.PHASE_TEST_SUPER_CALL_EXIT)
                }
                try {
                    runBlocking {
                        withTimeout(runTimeout) {
                            deferred.await()
                        }
                    }
                } catch (timeout: TimeoutCancellationException) {
                    val probeRunBeforeTimeout = NativeCrashProbe.run()
                    val probeAttemptBeforeTimeout = NativeCrashProbe.attempt()
                    val probePhaseBeforeTimeout = NativeCrashProbe.phase()
                    val probeUpdatesBeforeTimeout = NativeCrashProbe.updates()
                    val elapsed = startedAt.elapsedNow()
                    val message =
                        "TestLucene90PointsFormat.testWithExceptions run=$run/$totalRuns timeout elapsed=$elapsed limit=$runTimeout probeBeforeRun=$probeRunBeforeTimeout probeBeforeAttempt=$probeAttemptBeforeTimeout probeBeforePhase=$probePhaseBeforeTimeout probeBeforeUpdates=$probeUpdatesBeforeTimeout"
                    logger.error(timeout) { message }
                    NativeCrashProbe.requestNativeProbeDump(times = 1)
                    deferred.cancel("timed out after $runTimeout")
                    scope.cancel("timed out after $runTimeout")
                    throw AssertionError(message, timeout)
                }
                NativeCrashProbe.mark(run, 0, NativeCrashProbe.PHASE_TEST_RUN_END)
                logger.error { "TestLucene90PointsFormat.testWithExceptions run=$run/$totalRuns end" }
            } catch (t: Throwable) {
                if (t is AssertionError && t.message?.contains("timeout elapsed=") != true) {
                    NativeCrashProbe.mark(run, 0, NativeCrashProbe.PHASE_TEST_RUN_THROWABLE)
                }
                logger.error(t) {
                    "TestLucene90PointsFormat.testWithExceptions run=$run/$totalRuns throwable=${t::class.simpleName} message=${t.message}"
                }
                throw t
            }
        }
        NativeCrashProbe.clear()
    }

    @Test
    override fun testMultiValued() = super.testMultiValued()

    @Test
    override fun testAllEqual() = super.testAllEqual()

    @Test
    override fun testOneDimEqual() = super.testOneDimEqual()

    @Test
    override fun testOneDimTwoValues() = super.testOneDimTwoValues()

    @Test
    override fun testBigIntNDims() = super.testBigIntNDims()

    @Test
    override fun testRandomBinaryTiny() = super.testRandomBinaryTiny()

    @Test
    override fun testRandomBinaryMedium() = super.testRandomBinaryMedium()

    @Test
    override fun testRandomBinaryBig() = super.testRandomBinaryBig()

    @Test
    override fun testAddIndexes() = super.testAddIndexes()

    @Test
    override fun testMergeMissing() = super.testMergeMissing()

    @Test
    override fun testDocCountEdgeCases() = super.testDocCountEdgeCases()

    @Test
    override fun testRandomDocCount() = super.testRandomDocCount()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

}
