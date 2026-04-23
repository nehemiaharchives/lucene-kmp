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
package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RamUsageTester
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.LongValues
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestOrdinalMap : LuceneTestCase() {

    companion object {
        private val ORDINAL_MAP_ACCUMULATOR =
            object : RamUsageTester.Accumulator() {
                override fun accumulateObject(
                    o: Any,
                    shallowSize: Long,
                    fieldValues: Collection<Any>,
                    queue: MutableCollection<Any>,
                ): Long {
                    if (o == LongValues.ZEROES
                        || o == LongValues.IDENTITY
                        || o == PackedInts.NullReader.forCount(PackedLongValues.DEFAULT_PAGE_SIZE)
                    ) {
                        return 0L
                    }
                    return super.accumulateObject(o, shallowSize, fieldValues, queue)
                }
            }
    }

    @Test
    @Throws(IOException::class)
    fun testRamBytesUsed() {
        val dir: Directory = newDirectory()
        val cfg =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setCodec(TestUtil.alwaysDocValuesFormat(TestUtil.getDefaultDocValuesFormat()))
        val iw = RandomIndexWriter(random(), dir, cfg)
        val maxDoc = TestUtil.nextInt(random(), 10, 1000)
        val maxTermLength = TestUtil.nextInt(random(), 1, 4)
        for (i in 0 until maxDoc) {
            val d = Document()
            if (random().nextBoolean()) {
                d.add(
                    SortedDocValuesField(
                        "sdv",
                        BytesRef(TestUtil.randomSimpleString(random(), maxTermLength)),
                    ),
                )
            }
            val numSortedSet = random().nextInt(3)
            for (j in 0 until numSortedSet) {
                d.add(
                    SortedSetDocValuesField(
                        "ssdv",
                        BytesRef(TestUtil.randomSimpleString(random(), maxTermLength)),
                    ),
                )
            }
            iw.addDocument(d)
            if (rarely()) {
                iw.getReader(true, false).close()
            }
        }
        iw.commit()
        val r = iw.getReader(true, false)
        val sdv = MultiDocValues.getSortedValues(r, "sdv")
        if (sdv is MultiDocValues.MultiSortedDocValues) {
            val map = sdv.mapping
            assertEquals(RamUsageTester.ramUsed(map, ORDINAL_MAP_ACCUMULATOR), map.ramBytesUsed())
        }
        val ssdv = MultiDocValues.getSortedSetValues(r, "ssdv")
        if (ssdv is MultiDocValues.MultiSortedSetDocValues) {
            val map = ssdv.mapping
            assertEquals(RamUsageTester.ramUsed(map, ORDINAL_MAP_ACCUMULATOR), map.ramBytesUsed())
        }
        iw.close()
        r.close()
        dir.close()
    }

    /**
     * Tests the case where one segment contains all of the global ords. In this case, we apply a
     * small optimization and hardcode the first segment indices and global ord deltas as all zeroes.
     */
    @Test
    @Throws(IOException::class)
    fun testOneSegmentWithAllValues() {
        val dir: Directory = newDirectory()
        val cfg =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setCodec(TestUtil.alwaysDocValuesFormat(TestUtil.getDefaultDocValuesFormat()))
                .setMergePolicy(NoMergePolicy.INSTANCE)
        val iw = IndexWriter(dir, cfg)

        val numTerms = 1000
        for (i in 0 until numTerms) {
            val d = Document()
            val term = i.toString()
            d.add(SortedDocValuesField("sdv", BytesRef(term)))
            iw.addDocument(d)
        }
        iw.forceMerge(1)

        for (i in 0 until 10) {
            val d = Document()
            val term = random().nextInt(numTerms).toString()
            d.add(SortedDocValuesField("sdv", BytesRef(term)))
            iw.addDocument(d)
        }
        iw.commit()

        val r = DirectoryReader.open(iw)
        val sdv = MultiDocValues.getSortedValues(r, "sdv")
        assertNotNull(sdv)
        assertTrue(sdv is MultiDocValues.MultiSortedDocValues)

        // Check that the optimization kicks in.
        val map = sdv.mapping
        assertEquals(LongValues.ZEROES, map.firstSegments)
        assertEquals(LongValues.ZEROES, map.globalOrdDeltas)

        // Check the map's basic behavior.
        assertEquals(numTerms, map.valueCount.toInt())
        for (i in 0 until numTerms) {
            assertEquals(0, map.getFirstSegmentNumber(i.toLong()))
            assertEquals(i.toLong(), map.getFirstSegmentOrd(i.toLong()))
        }

        iw.close()
        r.close()
        dir.close()
    }
}
