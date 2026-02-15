package org.gnit.lucenekmp.codecs.lucene90

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
import org.gnit.lucenekmp.util.bkd.BKDConfig
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLucene90PointsFormat : BasePointsFormatTestCase() {
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
    override fun testWithExceptions() = super.testWithExceptions()
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
