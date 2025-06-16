package org.gnit.lucenekmp.util.bkd

import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.store.FSLockFactory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TestBKDRadixSort : LuceneTestCase() {
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

    @Test
    fun testRandom() {
        val config = getRandomConfig()
        val numPoints = TestUtil.nextInt(random(), 1, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        val points = HeapPointWriter(config, numPoints)
        val value = ByteArray(config.packedBytesLength())
        for (i in 0 until numPoints) {
            random().nextBytes(value)
            points.append(value, i)
        }
        verifySort(config, points, 0, numPoints)
    }

    @Test
    fun testRandomAllEquals() {
        val config = getRandomConfig()
        val numPoints = TestUtil.nextInt(random(), 1, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        val points = HeapPointWriter(config, numPoints)
        val value = ByteArray(config.packedBytesLength())
        random().nextBytes(value)
        for (i in 0 until numPoints) {
            points.append(value, random().nextInt(numPoints))
        }
        verifySort(config, points, 0, numPoints)
    }

    @Test
    fun testRandomLastByteTwoValues() {
        val config = getRandomConfig()
        val numPoints = TestUtil.nextInt(random(), 1, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        val points = HeapPointWriter(config, numPoints)
        val value = ByteArray(config.packedBytesLength())
        random().nextBytes(value)
        for (i in 0 until numPoints) {
            if (random().nextBoolean()) {
                points.append(value, 1)
            } else {
                points.append(value, 2)
            }
        }
        verifySort(config, points, 0, numPoints)
    }

    @Test
    fun testRandomFewDifferentValues() {
        val config = getRandomConfig()
        val numPoints = TestUtil.nextInt(random(), 1, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        val points = HeapPointWriter(config, numPoints)
        val numberValues = random().nextInt(8) + 2
        val differentValues = Array(numberValues) { ByteArray(config.packedBytesLength()) }
        for (i in 0 until numberValues) {
            random().nextBytes(differentValues[i])
        }
        for (i in 0 until numPoints) {
            points.append(differentValues[random().nextInt(numberValues)], i)
        }
        verifySort(config, points, 0, numPoints)
    }

    @Test
    fun testRandomDataDimDifferent() {
        val config = getRandomConfig()
        val numPoints = TestUtil.nextInt(random(), 1, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        val points = HeapPointWriter(config, numPoints)
        val value = ByteArray(config.packedBytesLength())
        val totalDataDimension = config.numDims - config.numIndexDims
        val dataDimensionValues = ByteArray(totalDataDimension * config.bytesPerDim)
        random().nextBytes(value)
        for (i in 0 until numPoints) {
            random().nextBytes(dataDimensionValues)
            System.arraycopy(
                dataDimensionValues,
                0,
                value,
                config.packedIndexBytesLength(),
                totalDataDimension * config.bytesPerDim
            )
            points.append(value, random().nextInt(numPoints))
        }
        verifySort(config, points, 0, numPoints)
    }

    private fun verifySort(config: BKDConfig, points: HeapPointWriter, start: Int, end: Int) {
        getDirectory().use { dir ->
            val radixSelector = BKDRadixSelector(config, 1000, dir, "test")
            for (splitDim in 0 until config.numDims) {
                radixSelector.heapRadixSort(
                    points,
                    start,
                    end,
                    splitDim,
                    getRandomCommonPrefix(config, points, start, end, splitDim)
                )
                val previous = ByteArray(config.packedBytesLength())
                var previousDocId = -1
                org.gnit.lucenekmp.jdkport.Arrays.fill(previous, 0.toByte())
                val dimOffset = splitDim * config.bytesPerDim
                for (j in start until end) {
                    val pointValue = points.getPackedValueSlice(j)!!
                    val valueRef: BytesRef = pointValue.packedValue()
                    var cmp = org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                        valueRef.bytes,
                        valueRef.offset + dimOffset,
                        valueRef.offset + dimOffset + config.bytesPerDim,
                        previous,
                        dimOffset,
                        dimOffset + config.bytesPerDim
                    )
                    assertTrue(cmp >= 0)
                    if (cmp == 0) {
                        val dataOffset = config.numIndexDims * config.bytesPerDim
                        cmp = org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            valueRef.bytes,
                            valueRef.offset + dataOffset,
                            valueRef.offset + config.packedBytesLength(),
                            previous,
                            dataOffset,
                            config.packedBytesLength()
                        )
                        assertTrue(cmp >= 0)
                    }
                    if (cmp == 0) {
                        assertTrue(pointValue.docID() >= previousDocId)
                    }
                    System.arraycopy(valueRef.bytes, valueRef.offset, previous, 0, config.packedBytesLength())
                    previousDocId = pointValue.docID()
                }
            }
        }
    }

    private fun getRandomCommonPrefix(
        config: BKDConfig,
        points: HeapPointWriter,
        start: Int,
        end: Int,
        sortDim: Int
    ): Int {
        var commonPrefixLength = config.bytesPerDim
        var value = points.getPackedValueSlice(start)!!
        var bytesRef = value.packedValue()
        val firstValue = ByteArray(config.bytesPerDim)
        val offset = sortDim * config.bytesPerDim
        System.arraycopy(bytesRef.bytes, bytesRef.offset + offset, firstValue, 0, config.bytesPerDim)
        for (i in start + 1 until end) {
            value = points.getPackedValueSlice(i)!!
            bytesRef = value.packedValue()
            val diff = org.gnit.lucenekmp.jdkport.Arrays.mismatch(
                bytesRef.bytes,
                bytesRef.offset + offset,
                bytesRef.offset + offset + config.bytesPerDim,
                firstValue,
                0,
                config.bytesPerDim
            )
            if (diff != -1 && commonPrefixLength > diff) {
                if (diff == 0) {
                    return diff
                }
                commonPrefixLength = diff
            }
        }
        return if (random().nextBoolean()) commonPrefixLength else random().nextInt(commonPrefixLength)
    }

    private fun getRandomConfig(): BKDConfig {
        val numIndexDims = TestUtil.nextInt(random(), 1, BKDConfig.MAX_INDEX_DIMS)
        val numDims = TestUtil.nextInt(random(), numIndexDims, BKDConfig.MAX_DIMS)
        val bytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val maxPointsInLeafNode = TestUtil.nextInt(random(), 50, 2000)
        return BKDConfig(numDims, numIndexDims, bytesPerDim, maxPointsInLeafNode)
    }
}

