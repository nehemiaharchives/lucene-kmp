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
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val copy = copyPoints(config, points)
        verify(config, dir, copy, 0L, values.toLong(), middle, 0)
        dir.close()
    }

    private fun copyPoints(config: BKDConfig, source: PointWriter): PointWriter {
        val copy = HeapPointWriter(config, source.count().toInt())
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
        val selector = BKDRadixSelector(config, sortedOnHeap, dir, "test")
        val inputSlice = BKDRadixSelector.PathSlice(points, 0, points.count())
        val slices = arrayOfNulls<BKDRadixSelector.PathSlice>(2)
        selector.select(inputSlice, slices, start, end, middle, 0, 0)
        assertEquals(middle - start, slices[0]!!.count)
        assertEquals(end - middle, slices[1]!!.count)
        slices[0]!!.writer.destroy()
        slices[1]!!.writer.destroy()
        points.destroy()
    }
}
