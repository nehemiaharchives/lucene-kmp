package org.gnit.lucenekmp.util.bkd

import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.store.FSLockFactory
import org.gnit.lucenekmp.store.FlushInfo
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Ignore
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Ignore
class TestBKD : LuceneTestCase() {
    private lateinit var fakeFileSystem: FakeFileSystem

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        Files.setFileSystem(fakeFileSystem)
    }

    @AfterTest
    fun tearDown() {
        Files.resetFileSystem()
    }

    private fun getDirectory(): Directory {
        val path = "/tmp".toPath()
        fakeFileSystem.createDirectories(path)
        return NIOFSDirectory(path, FSLockFactory.default, fakeFileSystem)
    }

    private fun getPointValues(input: org.gnit.lucenekmp.store.IndexInput): org.gnit.lucenekmp.index.PointValues {
        return BKDReader(input, input, input)
    }

    @Test
    fun testBasicInts1D() {
        val config = BKDConfig(1, 1, 4, 2)
        getDirectory().use { dir ->
            val writer = BKDWriter(100, dir, "tmp", config, 1.0, 100)
            val scratch = ByteArray(4)
            for (docID in 0 until 100) {
                NumericUtils.intToSortableBytes(docID, scratch, 0)
                writer.add(scratch, docID)
            }

            val indexFP: Long
            dir.createOutput("bkd", IOContext(FlushInfo(100, 0L))).use { out ->
                val finalizer = writer.finish(out, out, out)
                indexFP = out.filePointer
                finalizer?.run()
            }

            dir.openInput("bkd", IOContext(FlushInfo(100, 0L))).use { `in` ->
                `in`.seek(indexFP)
                val r = getPointValues(`in`)
                val queryMin = Array(1) { ByteArray(4) }
                NumericUtils.intToSortableBytes(42, queryMin[0], 0)
                val queryMax = Array(1) { ByteArray(4) }
                NumericUtils.intToSortableBytes(87, queryMax[0], 0)

                val hits = BitSet()
                r.intersect(getIntersectVisitor(hits, queryMin, queryMax, config))

                for (docID in 0 until 100) {
                    val expected = docID in 42..87
                    val actual = hits.get(docID)
                    assertEquals(expected, actual, "docID=$docID")
                }
            }
        }
    }

    @Test
    fun testRandomIntsNDims() {
        val numDocs = atLeast(1000)
        getDirectory().use { dir ->
            val numDims = TestUtil.nextInt(random(), 1, 5)
            val numIndexDims = TestUtil.nextInt(random(), 1, numDims)
            val maxPointsInLeafNode = TestUtil.nextInt(random(), 50, 100)
            val maxMB = 3.0f + 3 * random().nextFloat()
            val config = BKDConfig(numDims, numIndexDims, 4, maxPointsInLeafNode)
            val writer = BKDWriter(numDocs, dir, "tmp", config, maxMB.toDouble(), numDocs.toLong())

            val docs = Array(numDocs) { IntArray(numDims) }
            val scratch = ByteArray(4 * numDims)
            val minValue = IntArray(numDims) { Int.MAX_VALUE }
            val maxValue = IntArray(numDims) { Int.MIN_VALUE }
            for (docID in 0 until numDocs) {
                val values = IntArray(numDims)
                for (dim in 0 until numDims) {
                    val v = random().nextInt()
                    values[dim] = v
                    if (v < minValue[dim]) minValue[dim] = v
                    if (v > maxValue[dim]) maxValue[dim] = v
                    NumericUtils.intToSortableBytes(v, scratch, dim * Int.SIZE_BYTES)
                }
                docs[docID] = values
                writer.add(scratch, docID)
            }

            val indexFP: Long
            dir.createOutput("bkd", IOContext(FlushInfo(numDocs, 0L))).use { out ->
                val finalizer = writer.finish(out, out, out)
                indexFP = out.filePointer
                finalizer?.run()
            }

            dir.openInput("bkd", IOContext(FlushInfo(numDocs, 0L))).use { input ->
                input.seek(indexFP)
                val r = getPointValues(input)
                val minPackedValue = r.minPackedValue
                val maxPackedValue = r.maxPackedValue
                for (dim in 0 until numIndexDims) {
                    assertEquals(minValue[dim], NumericUtils.sortableBytesToInt(minPackedValue, dim * Int.SIZE_BYTES))
                    assertEquals(maxValue[dim], NumericUtils.sortableBytesToInt(maxPackedValue, dim * Int.SIZE_BYTES))
                }

                val iters = atLeast(100)
                for (iter in 0 until iters) {
                    val queryMin = IntArray(numDims)
                    val queryMinBytes = Array(numDims) { ByteArray(4) }
                    val queryMax = IntArray(numDims)
                    val queryMaxBytes = Array(numDims) { ByteArray(4) }
                    for (dim in 0 until numIndexDims) {
                        var qMin = random().nextInt()
                        var qMax = random().nextInt()
                        if (qMin > qMax) {
                            val x = qMin
                            qMin = qMax
                            qMax = x
                        }
                        queryMin[dim] = qMin
                        queryMax[dim] = qMax
                        NumericUtils.intToSortableBytes(qMin, queryMinBytes[dim], 0)
                        NumericUtils.intToSortableBytes(qMax, queryMaxBytes[dim], 0)
                    }

                    val hits = BitSet()
                    r.intersect(getIntersectVisitor(hits, queryMinBytes, queryMaxBytes, config))

                    for (docID in 0 until numDocs) {
                        val docValues = docs[docID]
                        var expected = true
                        for (dim in 0 until numIndexDims) {
                            val x = docValues[dim]
                            if (x < queryMin[dim] || x > queryMax[dim]) {
                                expected = false
                                break
                            }
                        }
                        val actual = hits.get(docID)
                        assertEquals(expected, actual, "docID=$docID")
                    }
                }
            }
        }
    }

    @Test
    fun testBigIntNDims() {
        val numDocs = atLeast(1000)
        getDirectory().use { dir ->
            val numBytesPerDim = TestUtil.nextInt(random(), 2, 30)
            val numDims = TestUtil.nextInt(random(), 1, 5)
            val maxPointsInLeafNode = TestUtil.nextInt(random(), 50, 100)
            val maxMB = 3.0f + 3 * random().nextFloat()
            val config = BKDConfig(numDims, numDims, numBytesPerDim, maxPointsInLeafNode)
            val writer = BKDWriter(numDocs, dir, "tmp", config, maxMB.toDouble(), numDocs.toLong())

            val docs = Array(numDocs) { Array<BigInteger>(numDims) { BigInteger.ZERO } }
            val scratch = ByteArray(numBytesPerDim * numDims)
            for (docID in 0 until numDocs) {
                val values = Array(numDims) { randomBigInt(numBytesPerDim) }
                for (dim in 0 until numDims) {
                    NumericUtils.bigIntToSortableBytes(values[dim], numBytesPerDim, scratch, dim * numBytesPerDim)
                }
                docs[docID] = values
                writer.add(scratch, docID)
            }

            val indexFP: Long
            dir.createOutput("bkd", IOContext(FlushInfo(numDocs, 0L))).use { out ->
                val finalizer = writer.finish(out, out, out)
                indexFP = out.filePointer
                finalizer?.run()
            }

            dir.openInput("bkd", IOContext(FlushInfo(numDocs, 0L))).use { input ->
                input.seek(indexFP)
                val r = getPointValues(input)
                val iters = atLeast(100)
                for (iter in 0 until iters) {
                    val queryMin = Array(numDims) { randomBigInt(numBytesPerDim) }
                    val queryMinBytes = Array(numDims) { ByteArray(numBytesPerDim) }
                    val queryMax = Array(numDims) { randomBigInt(numBytesPerDim) }
                    val queryMaxBytes = Array(numDims) { ByteArray(numBytesPerDim) }
                    for (dim in 0 until numDims) {
                        if (queryMin[dim] > queryMax[dim]) {
                            val x = queryMin[dim]
                            queryMin[dim] = queryMax[dim]
                            queryMax[dim] = x
                        }
                        NumericUtils.bigIntToSortableBytes(queryMin[dim], numBytesPerDim, queryMinBytes[dim], 0)
                        NumericUtils.bigIntToSortableBytes(queryMax[dim], numBytesPerDim, queryMaxBytes[dim], 0)
                    }

                    val hits = BitSet()
                    r.intersect(getIntersectVisitor(hits, queryMinBytes, queryMaxBytes, config))

                    for (docID in 0 until numDocs) {
                        val docValues = docs[docID]
                        var expected = true
                        for (dim in 0 until numDims) {
                            val x = docValues[dim]
                            if (x < queryMin[dim] || x > queryMax[dim]) {
                                expected = false
                                break
                            }
                        }
                        val actual = hits.get(docID)
                        assertEquals(expected, actual, "docID=$docID")
                    }
                }
            }
        }
    }

    @Test
    fun testWithExceptions() {
        // TODO: implement test logic
    }

    @Test
    fun testRandomBinaryTiny() {
        doTestRandomBinary(10)
    }

    @Test
    fun testRandomBinaryMedium() {
        doTestRandomBinary(10000)
    }

    @Test
    fun testRandomBinaryBig() {
        doTestRandomBinary(200000)
    }

    @Test
    fun testTooLittleHeap() {
        getDirectory().use { dir ->
            val expected = expectThrows(IllegalArgumentException::class) {
                BKDWriter(1, dir, "bkd", BKDConfig(1, 1, 16, 1_000_000), 0.001, 0L)
            }
            assertTrue(expected!!.message!!.contains("either increase maxMBSortInHeap or decrease maxPointsInLeafNode"))
        }
    }

    @Test
    fun testAllEqual() {
        val numBytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val numDataDims = TestUtil.nextInt(random(), 1, org.gnit.lucenekmp.index.PointValues.MAX_DIMENSIONS)
        val numIndexDims = kotlin.math.min(TestUtil.nextInt(random(), 1, numDataDims), org.gnit.lucenekmp.index.PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs = atLeast(1000)
        val docValues = Array(numDocs) { Array(numDataDims) { ByteArray(numBytesPerDim) } }
        for (docID in 0 until numDocs) {
            if (docID == 0) {
                for (dim in 0 until numDataDims) {
                    random().nextBytes(docValues[docID][dim])
                }
            } else {
                docValues[docID] = docValues[0]
            }
        }
        verify(docValues, null, numDataDims, numIndexDims, numBytesPerDim)
    }

    @Test
    fun testIndexDimEqualDataDimDifferent() {
        val numBytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val numDataDims = TestUtil.nextInt(random(), 2, org.gnit.lucenekmp.index.PointValues.MAX_DIMENSIONS)
        val numIndexDims = kotlin.math.min(TestUtil.nextInt(random(), 1, numDataDims - 1), org.gnit.lucenekmp.index.PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs = atLeast(1000)
        val docValues = Array(numDocs) { Array(numDataDims) { ByteArray(numBytesPerDim) } }
        val indexDimensions = Array(numDataDims) { ByteArray(numBytesPerDim) }
        for (dim in 0 until numIndexDims) {
            random().nextBytes(indexDimensions[dim])
        }
        for (docID in 0 until numDocs) {
            for (dim in 0 until numIndexDims) {
                docValues[docID][dim] = indexDimensions[dim]
            }
            for (dim in numIndexDims until numDataDims) {
                random().nextBytes(docValues[docID][dim])
            }
        }
        verify(docValues, null, numDataDims, numIndexDims, numBytesPerDim)
    }

    @Test
    fun testOneDimEqual() {
        val numBytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val numDataDims = TestUtil.nextInt(random(), 1, org.gnit.lucenekmp.index.PointValues.MAX_DIMENSIONS)
        val numIndexDims = kotlin.math.min(TestUtil.nextInt(random(), 1, numDataDims), org.gnit.lucenekmp.index.PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs = atLeast(1000)
        val theEqualDim = random().nextInt(numDataDims)
        val docValues = Array(numDocs) { Array(numDataDims) { ByteArray(numBytesPerDim) } }
        for (docID in 0 until numDocs) {
            for (dim in 0 until numDataDims) {
                random().nextBytes(docValues[docID][dim])
            }
            if (docID > 0) {
                docValues[docID][theEqualDim] = docValues[0][theEqualDim]
            }
        }
        verify(docValues, null, numDataDims, numIndexDims, numBytesPerDim, TestUtil.nextInt(random(), 20, 50))
    }

    @Test
    fun testOneDimLowCard() {
        val numBytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val numDataDims = TestUtil.nextInt(random(), 2, org.gnit.lucenekmp.index.PointValues.MAX_DIMENSIONS)
        val numIndexDims = kotlin.math.min(TestUtil.nextInt(random(), 2, numDataDims), org.gnit.lucenekmp.index.PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs = atLeast(10000)
        val theLowCardDim = random().nextInt(numDataDims)

        val value1 = ByteArray(numBytesPerDim)
        random().nextBytes(value1)
        val value2 = value1.copyOf()
        if (value2[numBytesPerDim - 1] == 0.toByte() || random().nextBoolean()) {
            value2[numBytesPerDim - 1] = (value2[numBytesPerDim - 1] + 1).toByte()
        } else {
            value2[numBytesPerDim - 1] = (value2[numBytesPerDim - 1] - 1).toByte()
        }

        val docValues = Array(numDocs) { Array(numDataDims) { ByteArray(numBytesPerDim) } }
        for (docID in 0 until numDocs) {
            for (dim in 0 until numDataDims) {
                docValues[docID][dim] = if (dim == theLowCardDim) {
                    if (random().nextBoolean()) value1 else value2
                } else {
                    ByteArray(numBytesPerDim).also { random().nextBytes(it) }
                }
            }
        }
        verify(docValues, null, numDataDims, numIndexDims, numBytesPerDim, TestUtil.nextInt(random(), 20, 50))
    }

    @Test
    fun testOneDimTwoValues() {
        val numBytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val numDataDims = TestUtil.nextInt(random(), 1, org.gnit.lucenekmp.index.PointValues.MAX_DIMENSIONS)
        val numIndexDims = kotlin.math.min(TestUtil.nextInt(random(), 1, numDataDims), org.gnit.lucenekmp.index.PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs = atLeast(1000)
        val theDim = random().nextInt(numDataDims)
        val value1 = ByteArray(numBytesPerDim).also { random().nextBytes(it) }
        val value2 = ByteArray(numBytesPerDim).also { random().nextBytes(it) }

        val docValues = Array(numDocs) { Array(numDataDims) { ByteArray(numBytesPerDim) } }
        for (docID in 0 until numDocs) {
            for (dim in 0 until numDataDims) {
                docValues[docID][dim] = if (dim == theDim) {
                    if (random().nextBoolean()) value1 else value2
                } else {
                    ByteArray(numBytesPerDim).also { random().nextBytes(it) }
                }
            }
        }
        verify(docValues, null, numDataDims, numIndexDims, numBytesPerDim)
    }

    @Test
    fun testRandomFewDifferentValues() {
        val numBytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val numDataDims = TestUtil.nextInt(random(), 1, org.gnit.lucenekmp.index.PointValues.MAX_DIMENSIONS)
        val numIndexDims = kotlin.math.min(TestUtil.nextInt(random(), 1, numDataDims), org.gnit.lucenekmp.index.PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs = atLeast(10000)
        val cardinality = TestUtil.nextInt(random(), 2, 100)
        val values = Array(cardinality) { Array(numDataDims) { ByteArray(numBytesPerDim) } }
        for (i in 0 until cardinality) {
            for (j in 0 until numDataDims) {
                random().nextBytes(values[i][j])
            }
        }

        val docValues = Array(numDocs) { values[random().nextInt(cardinality)] }
        verify(docValues, null, numDataDims, numIndexDims, numBytesPerDim)
    }

    @Test
    fun testMultiValued() {
        val numBytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val numDataDims = TestUtil.nextInt(random(), 1, org.gnit.lucenekmp.index.PointValues.MAX_DIMENSIONS)
        val numIndexDims = kotlin.math.min(TestUtil.nextInt(random(), 1, numDataDims), org.gnit.lucenekmp.index.PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs = atLeast(1000)
        val docValues = mutableListOf<Array<ByteArray>>()
        val docIDs = mutableListOf<Int>()

        for (docID in 0 until numDocs) {
            val numValuesInDoc = TestUtil.nextInt(random(), 1, 5)
            for (ord in 0 until numValuesInDoc) {
                docIDs.add(docID)
                val values = Array(numDataDims) { ByteArray(numBytesPerDim) }
                for (dim in 0 until numDataDims) {
                    random().nextBytes(values[dim])
                }
                docValues.add(values)
            }
        }

        val docValuesArray = docValues.toTypedArray()
        val docIDsArray = docIDs.toIntArray()
        verify(docValuesArray, docIDsArray, numDataDims, numIndexDims, numBytesPerDim)
    }

    @Test
    fun testBitFlippedOnPartition1() {
        // TODO: implement test logic
    }

    @Test
    fun testBitFlippedOnPartition2() {
        // TODO: implement test logic
    }

    @Test
    fun testTieBreakOrder() {
        // TODO: implement test logic
    }

    @Test
    fun testCheckDataDimOptimalOrder() {
        // TODO: implement test logic
    }

    @Test
    fun test2DLongOrdsOffline() {
        // TODO: implement test logic
    }

    @Test
    fun testWastedLeadingBytes() {
        // TODO: implement test logic
    }

    @Test
    fun testEstimatePointCount() {
        // TODO: implement test logic
    }

    @Test
    fun testTotalPointCountValidation() {
        // TODO: implement test logic
    }

    @Test
    fun testTooManyPoints() {
        // TODO: implement test logic
    }

    @Test
    fun testTooManyPoints1D() {
        // TODO: implement test logic
    }

    private fun randomBigInt(numBytes: Int): BigInteger {
        val bytes = ByteArray(numBytes)
        random().nextBytes(bytes)
        // clear sign bit to ensure non-negative
        bytes[0] = (bytes[0].toInt() and 0x7f).toByte()
        var result = BigInteger.fromByteArray(bytes, Sign.POSITIVE)
        if (random().nextBoolean()) {
            result = result.negate()
        }
        return result
    }

    private fun verify(
        docValues: Array<Array<ByteArray>>,
        docIDs: IntArray?,
        numDataDims: Int,
        numIndexDims: Int,
        numBytesPerDim: Int,
        maxPointsInLeafNode: Int = TestUtil.nextInt(random(), 50, 1000)
    ) {
        // TODO: implement full verification logic
    }

    private fun doTestRandomBinary(count: Int) {
        // TODO: implement test logic
    }

    private fun getIntersectVisitor(
        hits: BitSet,
        queryMin: Array<ByteArray>,
        queryMax: Array<ByteArray>,
        config: BKDConfig
    ): org.gnit.lucenekmp.index.PointValues.IntersectVisitor {
        return object : org.gnit.lucenekmp.index.PointValues.IntersectVisitor {
            override fun visit(docID: Int) {
                hits.set(docID)
            }

            override fun visit(docID: Int, packedValue: ByteArray) {
                for (dim in 0 until config.numIndexDims) {
                    val start = dim * config.bytesPerDim
                    val end = start + config.bytesPerDim
                    if (org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            packedValue, start, end,
                            queryMin[dim], 0, config.bytesPerDim
                        ) < 0 ||
                        org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            packedValue, start, end,
                            queryMax[dim], 0, config.bytesPerDim
                        ) > 0
                    ) {
                        return
                    }
                }
                hits.set(docID)
            }

            override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): org.gnit.lucenekmp.index.PointValues.Relation {
                var crosses = false
                for (dim in 0 until config.numIndexDims) {
                    val start = dim * config.bytesPerDim
                    val end = start + config.bytesPerDim
                    if (org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            maxPackedValue, start, end,
                            queryMin[dim], 0, config.bytesPerDim
                        ) < 0 ||
                        org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            minPackedValue, start, end,
                            queryMax[dim], 0, config.bytesPerDim
                        ) > 0
                    ) {
                        return org.gnit.lucenekmp.index.PointValues.Relation.CELL_OUTSIDE_QUERY
                    } else if (org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            minPackedValue, start, end,
                            queryMin[dim], 0, config.bytesPerDim
                        ) < 0 ||
                        org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            maxPackedValue, start, end,
                            queryMax[dim], 0, config.bytesPerDim
                        ) > 0
                    ) {
                        crosses = true
                    }
                }
                return if (crosses) {
                    org.gnit.lucenekmp.index.PointValues.Relation.CELL_CROSSES_QUERY
                } else {
                    org.gnit.lucenekmp.index.PointValues.Relation.CELL_INSIDE_QUERY
                }
            }
        }
    }
}

