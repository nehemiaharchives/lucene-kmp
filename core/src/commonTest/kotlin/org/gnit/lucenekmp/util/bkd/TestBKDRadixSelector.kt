/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.util.bkd

import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.store.FSLockFactory
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.bkd.OfflinePointWriter
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Ignore

/**
 * Simplified port of Lucene's TestBKDRadixSelector.
 */
class TestBKDRadixSelector : LuceneTestCase() {
    private lateinit var fs: FakeFileSystem

    @BeforeTest
    fun setup() {
        fs = FakeFileSystem()
        Files.setFileSystem(fs)
    }

    @AfterTest
    fun teardown() {
        Files.resetFileSystem()
    }

    private fun getDirectory(): Directory {
        val path = "/tmp".toPath()
        fs.createDirectories(path)
        return NIOFSDirectory(path, FSLockFactory.default, fs)
    }

    @Test
    fun testBasic() {
        val values = 4
        val dir = getDirectory()
        val middle = 2L
        val config = BKDConfig(1, 1, Int.SIZE_BYTES, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        val points = HeapPointWriter(config, values)
        val value = ByteArray(config.packedBytesLength())
        NumericUtils.intToSortableBytes(1, value, 0)
        points.append(value, 0)
        NumericUtils.intToSortableBytes(2, value, 0)
        points.append(value, 1)
        NumericUtils.intToSortableBytes(3, value, 0)
        points.append(value, 2)
        NumericUtils.intToSortableBytes(4, value, 0)
        points.append(value, 3)
        points.close()
        val copy = copyPoints(config, dir, points)
        verify(config, dir, copy, 0L, values.toLong(), middle, 0)
        dir.close()
    }

    private fun copyPoints(config: BKDConfig, dir: Directory, source: PointWriter): PointWriter {
        val copy = getRandomPointWriter(config, dir, source.count())
        source.getReader(0, source.count()).use { reader ->
            while (reader.next()) {
                reader.pointValue()?.let { copy.append(it) }
            }
        }
        copy.close()
        return copy
    }

    private fun verify(
        config: BKDConfig,
        dir: Directory,
        points: PointWriter,
        start: Long,
        end: Long,
        middle: Long,
        sortedOnHeap: Int
    ) {
        val radixSelector = BKDRadixSelector(config, sortedOnHeap, dir, "test")
        val dataOnlyDims = config.numDims - config.numIndexDims
        for (splitDim in 0 until config.numIndexDims) {
            val inputSlice = BKDRadixSelector.PathSlice(copyPoints(config, dir, points), 0, points.count())
            val commonPrefix = getRandomCommonPrefix(config, inputSlice, splitDim)
            val slices = arrayOfNulls<BKDRadixSelector.PathSlice>(2)
            val partitionPoint = radixSelector.select(inputSlice, slices, start, end, middle, splitDim, commonPrefix)
            assertEquals(middle - start, slices[0]!!.count)
            assertEquals(end - middle, slices[1]!!.count)
            val max = getMax(config, slices[0]!!, splitDim)
            val min = getMin(config, slices[1]!!, splitDim)
            var cmp = Arrays.compareUnsigned(max, 0, config.bytesPerDim, min, 0, config.bytesPerDim)
            assertTrue(cmp <= 0)
            if (cmp == 0) {
                val maxDataDim = getMaxDataDimension(config, slices[0]!!, max, splitDim)
                val minDataDim = getMinDataDimension(config, slices[1]!!, min, splitDim)
                cmp = Arrays.compareUnsigned(maxDataDim, 0, dataOnlyDims * config.bytesPerDim, minDataDim, 0, dataOnlyDims * config.bytesPerDim)
                assertTrue(cmp <= 0)
                if (cmp == 0) {
                    val maxDocID = getMaxDocId(config, slices[0]!!, splitDim, partitionPoint, maxDataDim)
                    val minDocID = getMinDocId(config, slices[1]!!, splitDim, partitionPoint, minDataDim)
                    assertTrue(minDocID >= maxDocID)
                }
            }
            assertTrue(Arrays.equals(partitionPoint, 0, config.bytesPerDim, min, 0, config.bytesPerDim))
            slices[0]!!.writer.destroy()
            slices[1]!!.writer.destroy()
        }
        points.destroy()
    }

    private fun getRandomConfig(): BKDConfig {
        val numIndexDims = TestUtil.nextInt(random(), 1, BKDConfig.MAX_INDEX_DIMS)
        val numDims = TestUtil.nextInt(random(), numIndexDims, BKDConfig.MAX_DIMS)
        val bytesPerDim = TestUtil.nextInt(random(), 2, 30)
        val maxPointsInLeafNode = TestUtil.nextInt(random(), 50, 2000)
        return BKDConfig(numDims, numIndexDims, bytesPerDim, maxPointsInLeafNode)
    }

    private fun getRandomPointWriter(config: BKDConfig, dir: Directory, numPoints: Long): PointWriter {
        return HeapPointWriter(config, numPoints.toInt())
    }

    private fun getRandomCommonPrefix(config: BKDConfig, inputSlice: BKDRadixSelector.PathSlice, splitDim: Int): Int {
        val pointsMax = getMax(config, inputSlice, splitDim)
        val pointsMin = getMin(config, inputSlice, splitDim)
        var commonPrefix = Arrays.mismatch(pointsMin, 0, config.bytesPerDim, pointsMax, 0, config.bytesPerDim)
        if (commonPrefix == -1) {
            commonPrefix = config.bytesPerDim
        }
        return if (random().nextBoolean()) {
            commonPrefix
        } else {
            if (commonPrefix == 0) 0 else Random.nextInt(commonPrefix)
        }
    }

    private fun getMin(config: BKDConfig, slice: BKDRadixSelector.PathSlice, dimension: Int): ByteArray {
        val min = ByteArray(config.bytesPerDim) { 0xff.toByte() }
        slice.writer.getReader(slice.start, slice.count).use { reader ->
            val value = ByteArray(config.bytesPerDim)
            while (reader.next()) {
                val pv = reader.pointValue() ?: continue
                val packed = pv.packedValue()
                System.arraycopy(packed.bytes, packed.offset + dimension * config.bytesPerDim, value, 0, config.bytesPerDim)
                if (Arrays.compareUnsigned(min, 0, config.bytesPerDim, value, 0, config.bytesPerDim) > 0) {
                    System.arraycopy(value, 0, min, 0, config.bytesPerDim)
                }
            }
        }
        return min
    }

    private fun getMax(config: BKDConfig, slice: BKDRadixSelector.PathSlice, dimension: Int): ByteArray {
        val max = ByteArray(config.bytesPerDim)
        slice.writer.getReader(slice.start, slice.count).use { reader ->
            val value = ByteArray(config.bytesPerDim)
            while (reader.next()) {
                val pv = reader.pointValue() ?: continue
                val packed = pv.packedValue()
                System.arraycopy(packed.bytes, packed.offset + dimension * config.bytesPerDim, value, 0, config.bytesPerDim)
                if (Arrays.compareUnsigned(max, 0, config.bytesPerDim, value, 0, config.bytesPerDim) < 0) {
                    System.arraycopy(value, 0, max, 0, config.bytesPerDim)
                }
            }
        }
        return max
    }

    private fun getMinDataDimension(config: BKDConfig, slice: BKDRadixSelector.PathSlice, minDim: ByteArray, splitDim: Int): ByteArray {
        val numDataDims = config.numDims - config.numIndexDims
        val min = ByteArray(numDataDims * config.bytesPerDim) { 0xff.toByte() }
        val offset = splitDim * config.bytesPerDim
        slice.writer.getReader(slice.start, slice.count).use { reader ->
            val value = ByteArray(numDataDims * config.bytesPerDim)
            while (reader.next()) {
                val pv = reader.pointValue() ?: continue
                val packed = pv.packedValue()
                if (Arrays.mismatch(minDim, 0, config.bytesPerDim, packed.bytes, packed.offset + offset, packed.offset + offset + config.bytesPerDim) == -1) {
                    System.arraycopy(packed.bytes, packed.offset + config.numIndexDims * config.bytesPerDim, value, 0, numDataDims * config.bytesPerDim)
                    if (Arrays.compareUnsigned(min, 0, numDataDims * config.bytesPerDim, value, 0, numDataDims * config.bytesPerDim) > 0) {
                        System.arraycopy(value, 0, min, 0, numDataDims * config.bytesPerDim)
                    }
                }
            }
        }
        return min
    }

    private fun getMaxDataDimension(config: BKDConfig, slice: BKDRadixSelector.PathSlice, maxDim: ByteArray, splitDim: Int): ByteArray {
        val numDataDims = config.numDims - config.numIndexDims
        val max = ByteArray(numDataDims * config.bytesPerDim)
        val offset = splitDim * config.bytesPerDim
        slice.writer.getReader(slice.start, slice.count).use { reader ->
            val value = ByteArray(numDataDims * config.bytesPerDim)
            while (reader.next()) {
                val pv = reader.pointValue() ?: continue
                val packed = pv.packedValue()
                if (Arrays.mismatch(maxDim, 0, config.bytesPerDim, packed.bytes, packed.offset + offset, packed.offset + offset + config.bytesPerDim) == -1) {
                    System.arraycopy(packed.bytes, packed.offset + config.packedIndexBytesLength(), value, 0, numDataDims * config.bytesPerDim)
                    if (Arrays.compareUnsigned(max, 0, numDataDims * config.bytesPerDim, value, 0, numDataDims * config.bytesPerDim) < 0) {
                        System.arraycopy(value, 0, max, 0, numDataDims * config.bytesPerDim)
                    }
                }
            }
        }
        return max
    }

    private fun getMinDocId(config: BKDConfig, slice: BKDRadixSelector.PathSlice, dimension: Int, partitionPoint: ByteArray, dataDim: ByteArray): Int {
        var docID = Int.MAX_VALUE
        slice.writer.getReader(slice.start, slice.count).use { reader ->
            while (reader.next()) {
                val pv = reader.pointValue() ?: continue
                val packed = pv.packedValue()
                val offset = dimension * config.bytesPerDim
                val dataOffset = config.packedIndexBytesLength()
                val dataLength = (config.numDims - config.numIndexDims) * config.bytesPerDim
                if (Arrays.compareUnsigned(packed.bytes, packed.offset + offset, packed.offset + offset + config.bytesPerDim, partitionPoint, 0, config.bytesPerDim) == 0 &&
                    Arrays.compareUnsigned(packed.bytes, packed.offset + dataOffset, packed.offset + dataOffset + dataLength, dataDim, 0, dataLength) == 0) {
                    val newDocID = pv.docID()
                    if (newDocID < docID) {
                        docID = newDocID
                    }
                }
            }
        }
        return docID
    }

    private fun getMaxDocId(config: BKDConfig, slice: BKDRadixSelector.PathSlice, dimension: Int, partitionPoint: ByteArray, dataDim: ByteArray): Int {
        var docID = Int.MIN_VALUE
        slice.writer.getReader(slice.start, slice.count).use { reader ->
            while (reader.next()) {
                val pv = reader.pointValue() ?: continue
                val packed = pv.packedValue()
                val offset = dimension * config.bytesPerDim
                val dataOffset = config.packedIndexBytesLength()
                val dataLength = (config.numDims - config.numIndexDims) * config.bytesPerDim
                if (Arrays.compareUnsigned(packed.bytes, packed.offset + offset, packed.offset + offset + config.bytesPerDim, partitionPoint, 0, config.bytesPerDim) == 0 &&
                    Arrays.compareUnsigned(packed.bytes, packed.offset + dataOffset, packed.offset + dataOffset + dataLength, dataDim, 0, dataLength) == 0) {
                    val newDocID = pv.docID()
                    if (newDocID > docID) {
                        docID = newDocID
                    }
                }
            }
        }
        return docID
    }

    private fun doTestRandomBinary(count: Int) {
        val config = getRandomConfig()
        val values = TestUtil.nextInt(random(), count, count * 2)
        val dir = getDirectory()
        val start: Int
        val end: Int
        if (random().nextBoolean()) {
            start = 0
            end = values
        } else {
            start = TestUtil.nextInt(random(), 0, values - 3)
            end = TestUtil.nextInt(random(), start + 2, values)
        }
        val partitionPoint = TestUtil.nextInt(random(), start + 1, end - 1)
        val sortedOnHeap = random().nextInt(5000)
        val points = getRandomPointWriter(config, dir, values.toLong())
        val value = ByteArray(config.packedBytesLength())
        for (i in 0 until values) {
            Random.nextBytes(value)
            points.append(value, i)
        }
        points.close()
        verify(config, dir, points, start.toLong(), end.toLong(), partitionPoint.toLong(), sortedOnHeap)
        dir.close()
    }

    @Test
    fun testRandomBinaryTiny() {
        doTestRandomBinary(10)
    }

    @Test
    fun testRandomBinaryMedium() {
        doTestRandomBinary(25000)
    }

    @Ignore
    @LuceneTestCase.Companion.Nightly
    @Test
    fun testRandomBinaryBig() {
        doTestRandomBinary(500000)
    }

    @Ignore
    @Test
    fun testRandomAllDimensionsEquals() {
        val dimensions = TestUtil.nextInt(random(), 1, BKDConfig.MAX_INDEX_DIMS)
        val bytesPerDimensions = TestUtil.nextInt(random(), 2, 30)
        val config = BKDConfig(dimensions, dimensions, bytesPerDimensions, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        val values = TestUtil.nextInt(random(), 15000, 20000)
        val dir = getDirectory()
        val partitionPoint = random().nextInt(values)
        val sortedOnHeap = random().nextInt(5000)
        val points = getRandomPointWriter(config, dir, values.toLong())
        val value = ByteArray(config.packedBytesLength())
        Random.nextBytes(value)
        for (i in 0 until values) {
            if (random().nextBoolean()) {
                points.append(value, i)
            } else {
                points.append(value, random().nextInt(values))
            }
        }
        points.close()
        verify(config, dir, points, 0L, values.toLong(), partitionPoint.toLong(), sortedOnHeap)
        dir.close()
    }

    @Ignore
    @Test
    fun testRandomLastByteTwoValues() {
        val values = random().nextInt(15000) + 1
        val dir = getDirectory()
        val partitionPoint = random().nextInt(values)
        val sortedOnHeap = random().nextInt(5000)
        val config = getRandomConfig()
        val points = getRandomPointWriter(config, dir, values.toLong())
        val value = ByteArray(config.packedBytesLength())
        Random.nextBytes(value)
        for (i in 0 until values) {
            if (random().nextBoolean()) {
                points.append(value, 1)
            } else {
                points.append(value, 2)
            }
        }
        points.close()
        verify(config, dir, points, 0L, values.toLong(), partitionPoint.toLong(), sortedOnHeap)
        dir.close()
    }

    @Ignore
    @Test
    fun testRandomAllDocsEquals() {
        val values = random().nextInt(15000) + 1
        val dir = getDirectory()
        val partitionPoint = random().nextInt(values)
        val sortedOnHeap = random().nextInt(5000)
        val config = getRandomConfig()
        val points = getRandomPointWriter(config, dir, values.toLong())
        val value = ByteArray(config.packedBytesLength())
        Random.nextBytes(value)
        for (i in 0 until values) {
            points.append(value, 0)
        }
        points.close()
        verify(config, dir, points, 0L, values.toLong(), partitionPoint.toLong(), sortedOnHeap)
        dir.close()
    }

    @Ignore
    @Test
    fun testRandomFewDifferentValues() {
        val config = getRandomConfig()
        val values = atLeast(15000)
        val dir = getDirectory()
        val partitionPoint = random().nextInt(values)
        val sortedOnHeap = random().nextInt(5000)
        val points = getRandomPointWriter(config, dir, values.toLong())
        val numberValues = random().nextInt(8) + 2
        val differentValues = Array(numberValues) { ByteArray(config.packedBytesLength()) }
        for (i in 0 until numberValues) {
            Random.nextBytes(differentValues[i])
        }
        for (i in 0 until values) {
            points.append(differentValues[random().nextInt(numberValues)], i)
        }
        points.close()
        verify(config, dir, points, 0L, values.toLong(), partitionPoint.toLong(), sortedOnHeap)
        dir.close()
    }

    @Ignore
    @Test
    fun testRandomDataDimDiffValues() {
        val config = getRandomConfig()
        val values = atLeast(15000)
        val dir = getDirectory()
        val partitionPoint = random().nextInt(values)
        val sortedOnHeap = random().nextInt(5000)
        val points = getRandomPointWriter(config, dir, values.toLong())
        val value = ByteArray(config.packedBytesLength())
        val dataOnlyDims = config.numDims - config.numIndexDims
        val dataValue = ByteArray(dataOnlyDims * config.bytesPerDim)
        Random.nextBytes(value)
        for (i in 0 until values) {
            Random.nextBytes(dataValue)
            System.arraycopy(dataValue, 0, value, config.numIndexDims * config.bytesPerDim, dataOnlyDims * config.bytesPerDim)
            points.append(value, i)
        }
        points.close()
        verify(config, dir, points, 0L, values.toLong(), partitionPoint.toLong(), sortedOnHeap)
        dir.close()
    }
}
