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

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.MergeInfo
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.SameThreadExecutorService
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import org.gnit.lucenekmp.util.packed.PackedLongValues
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestSegmentMerger : LuceneTestCase() {
    // The variables for the new merged segment
    private lateinit var mergedDir: Directory
    private val mergedSegment = "test"

    // First segment to be merged
    private lateinit var merge1Dir: Directory
    private val doc1 = Document()
    private var reader1: SegmentReader? = null

    // Second Segment to be merged
    private lateinit var merge2Dir: Directory
    private val doc2 = Document()
    private var reader2: SegmentReader? = null

    @BeforeTest
    fun setUp() {
        mergedDir = newDirectory()
        merge1Dir = newDirectory()
        merge2Dir = newDirectory()
        DocHelper.setupDoc(doc1)
        val info1 = DocHelper.writeDoc(random(), merge1Dir, doc1)
        DocHelper.setupDoc(doc2)
        val info2 = DocHelper.writeDoc(random(), merge2Dir, doc2)
        reader1 = SegmentReader(info1, Version.LATEST.major, newIOContext(random()))
        reader2 = SegmentReader(info2, Version.LATEST.major, newIOContext(random()))
    }

    @AfterTest
    fun tearDown() {
        reader1?.close()
        reader2?.close()
        mergedDir.close()
        merge1Dir.close()
        merge2Dir.close()
    }

    @Test
    fun test() {
        assertTrue(this::mergedDir.isInitialized)
        assertTrue(this::merge1Dir.isInitialized)
        assertTrue(this::merge2Dir.isInitialized)
        assertNotNull(reader1)
        assertNotNull(reader2)
    }

    @Test
    fun testMerge() {
        val codec = Codec.default
        val si =
            SegmentInfo(
                mergedDir,
                Version.LATEST,
                null,
                mergedSegment,
                -1,
                isCompoundFile = false,
                hasBlocks = false,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                mutableMapOf(),
                null,
            )

        val merger =
            SegmentMerger(
                mutableListOf(requireNotNull(reader1), requireNotNull(reader2)),
                si,
                InfoStream.default,
                mergedDir,
                FieldInfos.FieldNumbers(null, null),
                newIOContext(random(), IOContext(MergeInfo(-1, -1, false, -1))),
                SameThreadExecutorService(),
            )
        val mergeState = merger.merge()
        val docsMerged = mergeState.segmentInfo.maxDoc()
        assertEquals(2, docsMerged)
        // Should be able to open a new SegmentReader against the new directory
        val mergedReader =
            SegmentReader(
                SegmentCommitInfo(
                    mergeState.segmentInfo,
                    0,
                    0,
                    -1L,
                    -1L,
                    -1L,
                    StringHelper.randomId(),
                ),
                Version.LATEST.major,
                newIOContext(random()),
            )
        assertEquals(2, mergedReader.numDocs())
        val newDoc1 = mergedReader.storedFields().document(0)
        // There are 2 unstored fields on the document
        assertEquals(DocHelper.numFields(doc1) - DocHelper.unstored.size, DocHelper.numFields(newDoc1))
        val newDoc2 = mergedReader.storedFields().document(1)
        assertEquals(DocHelper.numFields(doc2) - DocHelper.unstored.size, DocHelper.numFields(newDoc2))

        val termDocs =
            TestUtil.docs(random(), mergedReader, DocHelper.TEXT_FIELD_2_KEY, BytesRef("field"), null, 0)
        assertNotNull(termDocs)
        assertTrue(termDocs.nextDoc() != org.gnit.lucenekmp.search.DocIdSetIterator.NO_MORE_DOCS)

        var tvCount = 0
        for (fieldInfo in mergedReader.fieldInfos) {
            if (fieldInfo.hasTermVectors()) {
                tvCount++
            }
        }

        // System.out.println("stored size: " + stored.size());
        assertEquals(3, tvCount, "We do not have 3 fields that were indexed with term vector")

        val fields = requireNotNull(mergedReader.termVectors().get(0))
        val vector = fields.terms(DocHelper.TEXT_FIELD_2_KEY)
        assertNotNull(vector)
        assertEquals(3L, vector.size())
        val termsEnum = vector.iterator()

        var i = 0
        while (termsEnum.next() != null) {
            val term = requireNotNull(termsEnum.term()).utf8ToString()
            val freq = termsEnum.totalTermFreq().toInt()
            // System.out.println("Term: " + term + " Freq: " + freq);
            assertTrue(DocHelper.FIELD_2_TEXT.indexOf(term) != -1)
            assertEquals(DocHelper.FIELD_2_FREQS[i], freq)
            i++
        }

        TestSegmentReader.checkNorms(mergedReader)
        mergedReader.close()
    }

    @Test
    fun testBuildDocMap() {
        val maxDoc = TestUtil.nextInt(random(), 1, 128)
        val numDocs = TestUtil.nextInt(random(), 0, maxDoc)
        val liveDocs = FixedBitSet(maxDoc)
        repeat(numDocs) {
            while (true) {
                val docID = random().nextInt(maxDoc)
                if (!liveDocs.get(docID)) {
                    liveDocs.set(docID)
                    break
                }
            }
        }

        val docMap: PackedLongValues = MergeState.removeDeletes(maxDoc, liveDocs)

        // assert the mapping is compact
        var del = 0
        for (i in 0..<maxDoc) {
            if (!liveDocs.get(i)) {
                ++del
            } else {
                assertEquals((i - del).toLong(), docMap.get(i.toLong()))
            }
        }
    }
}

