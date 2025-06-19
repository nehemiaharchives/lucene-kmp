package org.gnit.lucenekmp.util.bkd

import org.gnit.lucenekmp.codecs.MutablePointTree
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestMutablePointTreeReaderUtils : LuceneTestCase() {
    @Test
    fun testSort() {
        repeat(3) { doTestSort(false) } // TODO originally 10 but reduced to 3 for dev speed
    }

    @Test
    fun testSortWithIncrementalDocId() {
        repeat(3) { doTestSort(true) } // TODO originally 10 but reduced to 3 for dev speed
    }

    private fun doTestSort(isDocIdIncremental: Boolean) {
        val bytesPerDim = TestUtil.nextInt(random(), 1, 16)
        val maxDoc = TestUtil.nextInt(random(), 1, 1 shl random().nextInt(30))
        val config = BKDConfig(1, 1, bytesPerDim, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        val points = createRandomPoints(config, maxDoc, IntArray(1), isDocIdIncremental)
        val reader = DummyPointsReader(points)
        MutablePointTreeReaderUtils.sort(config, maxDoc, reader, 0, points.size)
        points.sortWith { o1, o2 ->
            var cmp = o1.packedValue.compareTo(o2.packedValue)
            if (cmp == 0) {
                cmp = o1.doc.compareTo(o2.doc)
            }
            cmp
        }
        assertNotSame(points, reader.points)
        assertEquals(points.size, reader.points.size)
        var prevPoint: Point? = null
        for (i in points.indices) {
            assertEquals(points[i].packedValue, reader.points[i].packedValue)
            assertSame(points[i].packedValue, reader.points[i].packedValue)
            if (prevPoint != null && reader.points[i].packedValue == prevPoint.packedValue) {
                assertTrue(reader.points[i].doc >= prevPoint.doc)
            }
            prevPoint = reader.points[i]
        }
    }

    @Test
    fun testSortByDim() {
        repeat(5) { doTestSortByDim() }
    }

    private fun doTestSortByDim() {
        val config = createRandomConfig()
        val maxDoc = TestUtil.nextInt(random(), 1, 1 shl random().nextInt(30))
        val commonPrefixLengths = IntArray(config.numDims)
        val points = createRandomPoints(config, maxDoc, commonPrefixLengths, false)
        val reader = DummyPointsReader(points)
        val sortedDim = random().nextInt(config.numIndexDims)
        MutablePointTreeReaderUtils.sortByDim(
            config,
            sortedDim,
            commonPrefixLengths,
            reader,
            0,
            points.size,
            BytesRef(),
            BytesRef()
        )
        for (i in 1 until points.size) {
            val offset = sortedDim * config.bytesPerDim
            val previousValue = reader.points[i - 1].packedValue
            val currentValue = reader.points[i].packedValue
            var cmp = Arrays.compareUnsigned(
                previousValue.bytes,
                previousValue.offset + offset,
                previousValue.offset + offset + config.bytesPerDim,
                currentValue.bytes,
                currentValue.offset + offset,
                currentValue.offset + offset + config.bytesPerDim
            )
            if (cmp == 0) {
                val dataDimOffset = config.packedIndexBytesLength()
                val dataDimsLength = (config.numDims - config.numIndexDims) * config.bytesPerDim
                cmp = Arrays.compareUnsigned(
                    previousValue.bytes,
                    previousValue.offset + dataDimOffset,
                    previousValue.offset + dataDimOffset + dataDimsLength,
                    currentValue.bytes,
                    currentValue.offset + dataDimOffset,
                    currentValue.offset + dataDimOffset + dataDimsLength
                )
                if (cmp == 0) {
                    cmp = reader.points[i - 1].doc - reader.points[i].doc
                }
            }
            assertTrue(cmp <= 0)
        }
    }

    @Test
    fun testPartition() {
        repeat(5) { doTestPartition() }
    }

    private fun doTestPartition() {
        val config = createRandomConfig()
        val commonPrefixLengths = IntArray(config.numDims)
        val maxDoc = TestUtil.nextInt(random(), 1, 1 shl random().nextInt(30))
        val points = createRandomPoints(config, maxDoc, commonPrefixLengths, false)
        val splitDim = random().nextInt(config.numIndexDims)
        val reader = DummyPointsReader(points)
        val pivot = TestUtil.nextInt(random(), 0, points.size - 1)
        MutablePointTreeReaderUtils.partition(
            config,
            maxDoc,
            splitDim,
            commonPrefixLengths[splitDim],
            reader,
            0,
            points.size,
            pivot,
            BytesRef(),
            BytesRef()
        )
        val pivotValue = reader.points[pivot].packedValue
        val offset = splitDim * config.bytesPerDim
        for (i in points.indices) {
            val value = reader.points[i].packedValue
            var cmp = Arrays.compareUnsigned(
                value.bytes,
                value.offset + offset,
                value.offset + offset + config.bytesPerDim,
                pivotValue.bytes,
                pivotValue.offset + offset,
                pivotValue.offset + offset + config.bytesPerDim
            )
            if (cmp == 0) {
                val dataDimOffset = config.packedIndexBytesLength()
                val dataDimsLength = (config.numDims - config.numIndexDims) * config.bytesPerDim
                cmp = Arrays.compareUnsigned(
                    value.bytes,
                    value.offset + dataDimOffset,
                    value.offset + dataDimOffset + dataDimsLength,
                    pivotValue.bytes,
                    pivotValue.offset + dataDimOffset,
                    pivotValue.offset + dataDimOffset + dataDimsLength
                )
                if (cmp == 0) {
                    cmp = reader.points[i].doc - reader.points[pivot].doc
                }
            }
            when {
                i < pivot -> assertTrue(cmp <= 0)
                i > pivot -> assertTrue(cmp >= 0)
                else -> assertEquals(0, cmp)
            }
        }
    }

    private fun createRandomConfig(): BKDConfig {
        val numIndexDims = TestUtil.nextInt(random(), 1, BKDConfig.MAX_INDEX_DIMS)
        val numDims = TestUtil.nextInt(random(), numIndexDims, BKDConfig.MAX_DIMS)
        val bytesPerDim = TestUtil.nextInt(random(), 1, 16)
        val maxPointsInLeafNode = TestUtil.nextInt(random(), 50, 2000)
        return BKDConfig(numDims, numIndexDims, bytesPerDim, maxPointsInLeafNode)
    }

    private fun createRandomPoints(
        config: BKDConfig,
        maxDoc: Int,
        commonPrefixLengths: IntArray,
        isDocIdIncremental: Boolean
    ): Array<Point> {
        assertTrue(commonPrefixLengths.size == config.numDims)
        val numPoints = TestUtil.nextInt(random(), 1, 100000)
        val points = Array(numPoints) { Point(ByteArray(config.packedBytesLength()), 0) }
        if (random().nextInt(10) != 0) {
            for (i in points.indices) {
                val value = ByteArray(config.packedBytesLength())
                random().nextBytes(value)
                val doc = if (isDocIdIncremental) min(i, maxDoc - 1) else random().nextInt(maxDoc)
                points[i] = Point(value, doc)
            }
            for (i in 0 until config.numDims) {
                commonPrefixLengths[i] = TestUtil.nextInt(random(), 0, config.bytesPerDim)
            }
            val firstValue = points[0].packedValue
            for (i in 1 until points.size) {
                for (dim in 0 until config.numDims) {
                    val offset = dim * config.bytesPerDim
                    val packedValue = points[i].packedValue
                    System.arraycopy(
                        firstValue.bytes, firstValue.offset + offset,
                        packedValue.bytes, packedValue.offset + offset,
                        commonPrefixLengths[dim]
                    )
                }
            }
        } else {
            val numDataDims = config.numDims - config.numIndexDims
            val indexDims = ByteArray(config.packedIndexBytesLength())
            random().nextBytes(indexDims)
            val dataDims = ByteArray(numDataDims * config.bytesPerDim)
            for (i in points.indices) {
                val value = ByteArray(config.packedBytesLength())
                System.arraycopy(indexDims, 0, value, 0, config.packedIndexBytesLength())
                random().nextBytes(dataDims)
                System.arraycopy(
                    dataDims, 0, value, config.packedIndexBytesLength(),
                    numDataDims * config.bytesPerDim
                )
                val doc = if (isDocIdIncremental) min(i, maxDoc - 1) else random().nextInt(maxDoc)
                points[i] = Point(value, doc)
            }
            for (i in 0 until config.numIndexDims) {
                commonPrefixLengths[i] = config.bytesPerDim
            }
            for (i in config.numIndexDims until config.numDims) {
                commonPrefixLengths[i] = TestUtil.nextInt(random(), 0, config.bytesPerDim)
            }
            val firstValue = points[0].packedValue
            for (i in 1 until points.size) {
                for (dim in config.numIndexDims until config.numDims) {
                    val offset = dim * config.bytesPerDim
                    val packedValue = points[i].packedValue
                    System.arraycopy(
                        firstValue.bytes, firstValue.offset + offset,
                        packedValue.bytes, packedValue.offset + offset,
                        commonPrefixLengths[dim]
                    )
                }
            }
        }
        return points
    }

    private class Point(packed: ByteArray, val doc: Int) {
        val packedValue: BytesRef = BytesRef(packed.size + 1)

        init {
            packedValue.bytes[0] = random().nextInt(256).toByte()
            packedValue.offset = 1
            packedValue.length = packed.size
            System.arraycopy(packed, 0, packedValue.bytes, 1, packed.size)
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Point) return false
            return packedValue == other.packedValue && doc == other.doc
        }

        override fun hashCode(): Int {
            return 31 * packedValue.hashCode() + doc
        }

        override fun toString(): String {
            return "value=$packedValue doc=$doc"
        }
    }

    private class DummyPointsReader(points: Array<Point>) : MutablePointTree() {
        val points: Array<Point> = points.copyOf()
        private var temp: Array<Point>? = null

        override fun getValue(i: Int, packedValue: BytesRef) {
            val p = points[i].packedValue
            packedValue.bytes = p.bytes
            packedValue.offset = p.offset
            packedValue.length = p.length
        }

        override fun getByteAt(i: Int, k: Int): Byte {
            val p = points[i].packedValue
            return p.bytes[p.offset + k]
        }

        override fun getDocID(i: Int): Int = points[i].doc

        override fun swap(i: Int, j: Int) {
            ArrayUtil.swap(points, i, j)
        }

        override fun save(i: Int, j: Int) {
            if (temp == null) {
                temp = Array(points.size) { this.points[0] }
            }
            temp!![j] = points[i]
        }

        override fun restore(i: Int, j: Int) {
            temp?.let { tmp ->
                System.arraycopy(tmp, i, points, i, j - i)
            }
        }

        override fun size(): Long {
            throw UnsupportedOperationException()
        }

        override fun visitDocValues(visitor: PointValues.IntersectVisitor) {
            throw UnsupportedOperationException()
        }
    }
}

