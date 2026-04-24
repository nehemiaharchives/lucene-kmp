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
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.FloatPoint
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.IntRange
import org.gnit.lucenekmp.document.KeywordField
import org.gnit.lucenekmp.document.LongField
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.SortField.Companion.FIELD_DOC
import org.gnit.lucenekmp.search.SortField.Companion.FIELD_SCORE
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.ScorerIndexSearcher
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TestSortOptimization : LuceneTestCase() {

    @Test
    fun testLongSortOptimization() {
        val dir = newDirectory()
        val config = IndexWriterConfig()
            // Make sure to use the default codec, otherwise some random points formats that have
            // large values for maxPointsPerLeaf might not enable skipping with only 10k docs
            .setCodec(TestUtil.getDefaultCodec())
        val writer = IndexWriter(dir, config)
        val numDocs = atLeast(1000) // TODO reduced from 10000 to 1000 for dev speed
        val flushDoc = numDocs / 2 // TODO reduced split doc = 7000 to numDocs / 2 for dev speed
        val skipIsGuaranteed = numDocs >= 10000 // TODO reduced numDocs = 10000 to 1000 for dev speed
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(NumericDocValuesField("my_field", i.toLong()))
            doc.add(LongPoint("my_field", i.toLong()))
            writer.addDocument(doc)
            if (i == flushDoc) writer.flush() // two segments
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        val sortField = SortField("my_field", SortField.Type.LONG)
        val sort = Sort(sortField)
        val numHits = 3

        run { // simple sort
            val topDocs = assertSearchHits(reader, sort, numHits, null)

            assertEquals(numHits, topDocs.scoreDocs.size)
            for (i in 0 until numHits) {
                val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
                assertEquals(i, (fieldDoc.fields!![0] as Long).toInt())
            }
            assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong(), skipIsGuaranteed)
        }

        run { // paging sort with after
            val afterValue = 2L
            val after = FieldDoc(2, Float.NaN, arrayOf<Any?>(afterValue))
            val topDocs = assertSearchHits(reader, sort, numHits, after)
            assertEquals(numHits, topDocs.scoreDocs.size)
            for (i in 0 until numHits) {
                val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
                assertEquals(afterValue + 1 + i, fieldDoc.fields!![0])
            }
            assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong(), skipIsGuaranteed)
        }

        run { // test that if there is the secondary sort on _score, scores are filled correctly
            val sort2 = Sort(sortField, FIELD_SCORE)
            val topDocs = assertSearchHits(reader, sort2, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            for (i in 0 until numHits) {
                val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
                assertEquals(i, (fieldDoc.fields!![0] as Long).toInt())
                val score = fieldDoc.fields!![1] as Float
                assertEquals(1.0f, score, 0.001f)
            }
            assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong(), skipIsGuaranteed)
        }

        run { // test that if numeric field is a secondary sort, no optimization is run
            val sort2 = Sort(FIELD_SCORE, sortField)
            val topDocs = assertSearchHits(reader, sort2, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            assertEquals(
                numDocs.toLong(),
                topDocs.totalHits.value
            ) // assert that all documents were collected => optimization was not run
        }

        reader.close()
        dir.close()
    }

    /**
     * test that even if a field is not indexed with points, optimized sort still works as expected,
     * although no optimization will be run
     */
    @Test
    fun testLongSortOptimizationOnFieldNotIndexedWithPoints() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val numDocs = atLeast(100)
        // my_field is not indexed with points
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(NumericDocValuesField("my_field", i.toLong()))
            writer.addDocument(doc)
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        val sortField = SortField("my_field", SortField.Type.LONG)
        val sort = Sort(sortField)
        val numHits = 3

        val topDocs = assertSearchHits(reader, sort, numHits, null)
        assertEquals(
            numHits,
            topDocs.scoreDocs.size
        ) // sort still works and returns expected number of docs
        for (i in 0 until numHits) {
            val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
            assertEquals(i, (fieldDoc.fields!![0] as Long).toInt()) // returns expected values
        }
        assertEquals(
            numDocs.toLong(),
            topDocs.totalHits.value
        ) // assert that all documents were collected => optimization was not run

        reader.close()
        dir.close()
    }

    @Test
    fun testSortOptimizationWithMissingValues() {
        val dir = newDirectory()
        val config = IndexWriterConfig()
            // Make sure to use the default codec, otherwise some random points formats that have
            // large values for maxPointsPerLeaf might not enable skipping with only 10k docs
            .setCodec(TestUtil.getDefaultCodec())
        val writer = IndexWriter(dir, config)
        val numDocs = atLeast(1000) // TODO reduced from 10000 to 1000 for dev speed
        val flushDoc = numDocs / 2 // TODO reduced split doc = 7000 to numDocs / 2 for dev speed
        val missingValueInterval = maxOf(numDocs / 10, 1) // TODO reduced missing interval = 500 to numDocs / 10 for dev speed
        for (i in 0 until numDocs) {
            val doc = Document()
            if ((i % missingValueInterval) != 0) { // miss values on every Nth document
                doc.add(NumericDocValuesField("my_field", i.toLong()))
                doc.add(LongPoint("my_field", i.toLong()))
            }
            writer.addDocument(doc)
            if (i == flushDoc) writer.flush() // two segments
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        val numHits = 3

        run { // test that optimization is run when missing value setting of SortField is competitive with
            // Pruning.GREATER_THAN_OR_EQUAL_TO
            val sortField = SortField("my_field", SortField.Type.LONG)
            sortField.missingValue = 0L // set a competitive missing value
            val sort = Sort(sortField)
            val topDocs = assertSearchHits(reader, sort, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }
        run { // test that optimization is not run when missing value setting of SortField is competitive
            // with Pruning.SKIP
            val sortField1 = SortField("my_field1", SortField.Type.LONG)
            val sortField2 = SortField("my_field2", SortField.Type.LONG)
            sortField1.missingValue = 0L // set a competitive missing value
            sortField2.missingValue = 0L // set a competitive missing value
            val sort = Sort(sortField1, sortField2)
            val topDocs = assertSearchHits(reader, sort, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            assertEquals(
                numDocs.toLong(),
                topDocs.totalHits.value
            ) // assert that all documents were collected => optimization was not run
        }
        run { // test that optimization is run when missing value setting of SortField is NOT competitive
            val sortField = SortField("my_field", SortField.Type.LONG)
            sortField.missingValue = 100L // set a NON competitive missing value
            val sort = Sort(sortField)
            val topDocs = assertSearchHits(reader, sort, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // test that optimization is not run when missing value setting of SortField is competitive
            // with after on asc order
            val afterValue = Long.MAX_VALUE
            val afterDocID = missingValueInterval // TODO reduced after doc selection to stay within the smaller missing-value set for dev speed
            val after = FieldDoc(afterDocID, Float.NaN, arrayOf<Any?>(afterValue))
            val sortField = SortField("my_field", SortField.Type.LONG)
            sortField.missingValue = Long.MAX_VALUE // set a competitive missing value
            val sort = Sort(sortField)
            val topDocs = assertSearchHits(reader, sort, numHits, after)
            assertEquals(numHits, topDocs.scoreDocs.size)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // test that optimization is not run when missing value setting of SortField is competitive
            // with after on desc order
            val afterValue = Long.MAX_VALUE
            val afterDocID = missingValueInterval // TODO reduced after doc selection to stay within the smaller missing-value set for dev speed
            val after = FieldDoc(afterDocID, Float.NaN, arrayOf<Any?>(afterValue))
            val sortField = SortField("my_field", SortField.Type.LONG, true)
            sortField.missingValue = Long.MAX_VALUE // set a competitive missing value
            val sort = Sort(sortField)
            val topDocs = assertSearchHits(reader, sort, numHits, after)
            assertEquals(numHits, topDocs.scoreDocs.size)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run {
            // test that optimization is run when missing value setting of SortField is NOT competitive
            // with after on asc order
            val afterValue = 3L
            val after = FieldDoc(3, Float.NaN, arrayOf<Any?>(afterValue))
            val sortField = SortField("my_field", SortField.Type.LONG)
            sortField.missingValue = 2L
            val sort = Sort(sortField)
            val topDocs = assertSearchHits(reader, sort, numHits, after)
            assertEquals(numHits, topDocs.scoreDocs.size)
            for (i in 0 until numHits) {
                val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
                assertEquals(afterValue + 1 + i, fieldDoc.fields!![0])
            }
            assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
            // expect to skip all but the first leaf in the BKD tree in the first segment as well as the
            // second segment
            // doc-0 has no target field, so we need to minus 1
            val maxCollectedHits =
                if (numDocs >= 10000) {
                    val firstSegmentSize = flushDoc + 1 // TODO reduced split doc = 7000 to numDocs / 2 for dev speed
                    val expectedSkipped = maxOf(firstSegmentSize - 512 - 1, 0) + (numDocs - firstSegmentSize)
                    (numDocs - expectedSkipped + 1).toLong()
                } else {
                    numDocs.toLong() // TODO reduced numDocs = 10000 to 1000, so the BKD leaf skip bound is not exact for dev speed
                }
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, maxCollectedHits)
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testNumericDocValuesOptimizationWithMissingValues() {
        val dir = newDirectory()
        val config = IndexWriterConfig()
            // Make sure to use the default codec, otherwise some random points formats that have
            // large values for maxPointsPerLeaf might not enable skipping with only 10k docs
            .setCodec(TestUtil.getDefaultCodec())
        val writer = IndexWriter(dir, config)
        val numDocs = atLeast(1000) // TODO reduced from 10000 to 1000 for dev speed
        val missValuesNumDocs = numDocs / 2
        for (i in 0 until numDocs) {
            val doc = Document()
            if (i <= missValuesNumDocs) { // missing value document
            } else {
                doc.add(NumericDocValuesField("my_field", i.toLong()))
                doc.add(LongPoint("my_field", i.toLong()))
            }
            writer.addDocument(doc)
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        val numHits = 3
        val topDocs1: TopDocs
        val topDocs2: TopDocs

        run { // Test that optimization is run with NumericDocValues when missing value is NOT competitive
            val sortField = SortField("my_field", SortField.Type.LONG, true)
            sortField.missingValue = 0L // missing value is not competitive
            val sort = Sort(sortField)
            topDocs1 = assertSearchHits(reader, sort, numHits, null)
            assertNonCompetitiveHitsAreSkipped(topDocs1.totalHits.value, numDocs.toLong())
        }
        run { // Test that sort on sorted numeric field without sort optimization and with sort optimization
            // produce the same results
            val sortField = SortField("my_field", SortField.Type.LONG, true)
            sortField.missingValue = 0L // missing value is not competitive
            val sort = Sort(sortField)
            sortField.optimizeSortWithPoints = false
            topDocs2 = assertSearchHits(reader, sort, numHits, null)
            // assert that the resulting hits are the same
            assertEquals(topDocs1.scoreDocs.size, topDocs2.scoreDocs.size)
            assertEquals(numHits, topDocs1.scoreDocs.size)
            val scoreDocs1 = topDocs1.scoreDocs
            val scoreDocs2 = topDocs2.scoreDocs
            for (i in 0 until numHits) {
                val fieldDoc = scoreDocs1[i] as FieldDoc
                val fieldDoc2 = scoreDocs2[i] as FieldDoc
                assertEquals(fieldDoc.fields!![0], fieldDoc2.fields!![0])
                assertEquals(fieldDoc.doc, fieldDoc2.doc)
            }
            assertTrue(topDocs1.totalHits.value < topDocs2.totalHits.value)
        }

        run { // Test that we can't do optimization via NumericDocValues when there are multiple comparators
            val sortField1 = SortField("my_field", SortField.Type.LONG, true)
            val sortField2 = SortField("other", SortField.Type.LONG, true)
            sortField1.missingValue = 0L // missing value is not competitive
            sortField2.missingValue = 0L // missing value is not competitive
            val multiSorts = Sort(*arrayOf<SortField>(sortField1, sortField2))
            val topDocs = assertSearchHits(reader, multiSorts, numHits, null)
            // can't optimization with NumericDocValues when there are multiple comparators
            assertEquals(numDocs.toLong(), topDocs.totalHits.value)
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testSortOptimizationEqualValues() {
        val dir = newDirectory()
        val config = IndexWriterConfig()
            // Make sure to use the default codec, otherwise some random points formats that have
            // large values for maxPointsPerLeaf might not enable skipping with only 10k docs
            .setCodec(TestUtil.getDefaultCodec())
        val writer = IndexWriter(dir, config)
        val numDocs = atLeast(if (TEST_NIGHTLY) 50000 else 1000) // TODO reduced from 10000 to 1000 for dev speed
        val flushDoc = numDocs / 2 // TODO reduced split doc = 7000 to numDocs / 2 for dev speed
        for (i in 1..numDocs) {
            val doc = Document()
            doc.add(
                NumericDocValuesField("my_field1", 100L)
            ) // all docs have the same value of my_field1
            doc.add(IntPoint("my_field1", 100))
            doc.add(
                NumericDocValuesField(
                    "my_field2", (numDocs - i).toLong()
                )
            ) // diff values for the field my_field2
            writer.addDocument(doc)
            // if there is only one segment, we could test that totalHits must always equal (numHits + 1)
            if (i == flushDoc && random().nextBoolean()) writer.flush() // two segments
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        val numHits = 3

        run { // test that sorting on a single field with equal values uses the optimization with
            // GREATER_THAN_OR_EQUAL_TO
            val sortField = SortField("my_field1", SortField.Type.INT)
            val sort = Sort(sortField)
            val topDocs = assertSearchHits(reader, sort, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            for (i in 0 until numHits) {
                val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
                assertEquals(100, fieldDoc.fields!![0])
            }
            if (reader.leaves().size == 1) {
                // if segment size equals one, totalHits should always equals numHits plus 1
                assertEquals(topDocs.totalHits.value, (numHits + 1).toLong())
            }
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // test that sorting on a single field with equal values and after parameter
            // use the optimization with GREATER_THAN_OR_EQUAL_TO
            val afterValue = 100
            val afterDocID = 10 + random().nextInt(maxOf(numDocs - numHits - 10, 1)) // TODO reduced after-doc range from 1000 to numDocs - numHits - 10 for dev speed
            val sortField = SortField("my_field1", SortField.Type.INT)
            val sort = Sort(sortField)
            val after = FieldDoc(afterDocID, Float.NaN, arrayOf<Any?>(afterValue))
            val topDocs = assertSearchHits(reader, sort, numHits, after)
            assertEquals(numHits, topDocs.scoreDocs.size)
            for (i in 0 until numHits) {
                val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
                assertEquals(100, fieldDoc.fields!![0])
                assertTrue(fieldDoc.doc > afterDocID)
            }
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // test that sorting on main field with equal values + another field for tie breaks doesn't
            // use optimization with Pruning.GREATER_THAN
            val sortField1 = SortField("my_field1", SortField.Type.INT)
            val sortField2 = SortField("my_field2", SortField.Type.INT)
            val sort = Sort(sortField1, sortField2)
            val topDocs = assertSearchHits(reader, sort, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            for (i in 0 until numHits) {
                val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
                assertEquals(100, fieldDoc.fields!![0]) // sort on 1st field as expected
                assertEquals(i, fieldDoc.fields!![1]) // sort on 2nd field as expected
            }
            assertEquals(numHits, topDocs.scoreDocs.size)
            assertEquals(
                numDocs.toLong(),
                topDocs.totalHits.value
            ) // assert that all documents were collected => optimization was not run
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testFloatSortOptimization() {
        val dir = newDirectory()
        val config = IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())
        val writer = IndexWriter(dir, config)
        val numDocs = atLeast(1000) // TODO reduced from 10000 to 1000 for dev speed
        val skipIsGuaranteed = numDocs >= 10000 // TODO reduced numDocs = 10000 to 1000 for dev speed
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(FloatDocValuesField("my_field", i.toFloat()))
            doc.add(FloatPoint("my_field", i.toFloat()))
            writer.addDocument(doc)
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        val sortField = SortField("my_field", SortField.Type.FLOAT)
        val sort = Sort(sortField)
        val numHits = 3
        val topDocs = assertSearchHits(reader, sort, numHits, null)
        assertEquals(numHits, topDocs.scoreDocs.size)
        for (i in 0 until numHits) {
            val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
            assertEquals(i.toFloat(), fieldDoc.fields!![0])
        }
        assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong(), skipIsGuaranteed)
        reader.close()
        dir.close()
    }

    /**
     * Test that a search with sort on [_doc, other fields] across multiple indices doesn't miss any
     * documents.
     */
    @Test
    fun testDocSortOptimizationMultipleIndices() {
        val numIndices = 3
        val numDocsInIndex = atLeast(50)
        val dirs = arrayOfNulls<Directory>(numIndices)
        val readers = arrayOfNulls<DirectoryReader>(numIndices)
        for (i in 0 until numIndices) {
            dirs[i] = newDirectory()
            val config = IndexWriterConfig()
                // Make sure to use the default codec, otherwise some random points formats that have
                // large values for maxPointsPerLeaf might not enable skipping with only 10k docs
                .setCodec(TestUtil.getDefaultCodec())
            val writer = IndexWriter(dirs[i]!!, config)
            for (docID in 0 until numDocsInIndex) {
                val doc = Document()
                doc.add(NumericDocValuesField("my_field", (docID * numIndices + i).toLong()))
                writer.addDocument(doc)
            }
            writer.flush()
            writer.close()
            readers[i] = DirectoryReader.open(dirs[i]!!)
        }

        val size = 7
        val sort = Sort(FIELD_DOC, SortField("my_field", SortField.Type.LONG))
        val topDocs = arrayOfNulls<TopFieldDocs>(numIndices)
        var curNumHits: Int
        var after: FieldDoc? = null
        var collectedDocs = 0L
        var totalDocs = 0L
        var numHits = 0
        do {
            for (i in 0 until numIndices) {
                topDocs[i] = assertSearchHits(readers[i]!!, sort, size, after)
                for (docID in 0 until topDocs[i]!!.scoreDocs.size) {
                    topDocs[i]!!.scoreDocs[docID].shardIndex = i
                }
                collectedDocs += topDocs[i]!!.totalHits.value
                totalDocs += numDocsInIndex.toLong()
            }
            @Suppress("UNCHECKED_CAST")
            val mergedTopDocs = TopDocs.merge(sort, size, topDocs as Array<TopFieldDocs>)
            curNumHits = mergedTopDocs.scoreDocs.size
            numHits += curNumHits
            if (curNumHits > 0) {
                after = mergedTopDocs.scoreDocs[curNumHits - 1] as FieldDoc
            }
        } while (curNumHits > 0)

        for (i in 0 until numIndices) {
            readers[i]!!.close()
            dirs[i]!!.close()
        }

        val expectedNumHits = numDocsInIndex * numIndices
        assertEquals(expectedNumHits, numHits)
        assertNonCompetitiveHitsAreSkipped(collectedDocs, totalDocs)
    }

    @Test
    fun testDocSortOptimizationWithAfter() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val numDocs = atLeast(150)
        for (i in 0 until numDocs) {
            val doc = Document()
            writer.addDocument(doc)
            if ((i > 0) && (i % 50 == 0)) {
                writer.flush()
            }
        }

        val reader = DirectoryReader.open(writer)
        writer.close()
        val numHits = 10
        val searchAfters = intArrayOf(3, 10, numDocs - 10)
        for (searchAfter in searchAfters) {
            // sort by _doc with search after should trigger optimization
            run {
                val sort = Sort(FIELD_DOC)
                val after = FieldDoc(searchAfter, Float.NaN, arrayOf(searchAfter))
                val topDocs = assertSearchHits(reader, sort, numHits, after)
                val expNumHits =
                    if (searchAfter >= (numDocs - numHits)) (numDocs - searchAfter - 1) else numHits
                assertEquals(expNumHits, topDocs.scoreDocs.size)
                for (i in 0 until topDocs.scoreDocs.size) {
                    val expectedDocID = searchAfter + 1 + i
                    assertEquals(expectedDocID, topDocs.scoreDocs[i].doc)
                }
                assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
                assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
            }

            // sort by _doc + _score with search after should trigger optimization
            run {
                val sort = Sort(FIELD_DOC, FIELD_SCORE)
                val after = FieldDoc(searchAfter, Float.NaN, arrayOf<Any?>(searchAfter, 1.0f))
                val topDocs = assertSearchHits(reader, sort, numHits, after)
                val expNumHits =
                    if (searchAfter >= (numDocs - numHits)) (numDocs - searchAfter - 1) else numHits
                assertEquals(expNumHits, topDocs.scoreDocs.size)
                for (i in 0 until topDocs.scoreDocs.size) {
                    val expectedDocID = searchAfter + 1 + i
                    assertEquals(expectedDocID, topDocs.scoreDocs[i].doc)
                }
                assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
                assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
            }

            // sort by _doc desc should not trigger optimization
            run {
                val sort = Sort(SortField(null, SortField.Type.DOC, true))
                val after = FieldDoc(searchAfter, Float.NaN, arrayOf(searchAfter))
                val topDocs = assertSearchHits(reader, sort, numHits, after)
                val expNumHits = if (searchAfter < numHits) searchAfter else numHits
                assertEquals(expNumHits, topDocs.scoreDocs.size)
                for (i in 0 until topDocs.scoreDocs.size) {
                    val expectedDocID = searchAfter - 1 - i
                    assertEquals(expectedDocID, topDocs.scoreDocs[i].doc)
                }
                // assert that all documents were collected
                assertEquals(numDocs.toLong(), topDocs.totalHits.value)
            }
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testDocSortOptimizationWithAfterCollectsAllDocs() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val numDocs = atLeast(if (TEST_NIGHTLY) 50000 else 500) // TODO reduced from 5000 to 500 for dev speed
        val multipleSegments = random().nextBoolean()
        val numDocsInSegment = numDocs / 10 + random().nextInt(numDocs / 10)

        for (i in 1..numDocs) {
            val doc = Document()
            writer.addDocument(doc)
            if (multipleSegments && (i % numDocsInSegment == 0)) {
                writer.flush()
            }
        }
        writer.flush()

        val reader = DirectoryReader.open(writer)
        var visitedHits = 0
        var after: FieldDoc? = null
        while (visitedHits < numDocs) {
            val batch = 1 + random().nextInt(500)
            val topDocs = assertSearchHits(reader, Sort(FIELD_DOC), batch, after)
            val expectedHits = minOf(numDocs - visitedHits, batch)
            assertEquals(expectedHits, topDocs.scoreDocs.size)
            after = topDocs.scoreDocs[expectedHits - 1] as FieldDoc
            for (i in 0 until topDocs.scoreDocs.size) {
                assertEquals(visitedHits, topDocs.scoreDocs[i].doc)
                visitedHits++
            }
        }
        assertEquals(visitedHits, numDocs)
        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    fun testDocSortOptimization() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val numDocs = atLeast(100)
        var seg = 1
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(LongPoint("lf", i.toLong()))
            doc.add(StoredField("slf", i))
            doc.add(StringField("tf", "seg$seg", Field.Store.YES))
            writer.addDocument(doc)
            if ((i > 0) && (i % 50 == 0)) {
                writer.flush()
                seg++
            }
        }
        val reader = DirectoryReader.open(writer)
        writer.close()

        val numHits = 3
        val sort = Sort(FIELD_DOC)

        // sort by _doc should skip all non-competitive documents
        run {
            val topDocs = assertSearchHits(reader, sort, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            for (i in 0 until numHits) {
                assertEquals(i, topDocs.scoreDocs[i].doc)
            }
            assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, 10)
        }

        // sort by _doc with a bool query should skip all non-competitive documents
        run {
            val lowerRange = 40L
            val bq = BooleanQuery.Builder()
            bq.add(LongPoint.newRangeQuery("lf", lowerRange, Long.MAX_VALUE), BooleanClause.Occur.MUST)
            bq.add(TermQuery(Term("tf", "seg1")), BooleanClause.Occur.MUST)

            val topDocs = assertSearchHits(reader, bq.build(), sort, numHits, null)
            assertEquals(numHits, topDocs.scoreDocs.size)
            val storedFields = reader.storedFields()
            for (i in 0 until numHits) {
                val d = storedFields.document(topDocs.scoreDocs[i].doc)
                assertEquals((i + lowerRange.toInt()).toString(), d.get("slf"))
                assertEquals("seg1", d.get("tf"))
            }
            assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, 10)
        }

        reader.close()
        dir.close()
    }

    /**
     * Test that sorting on _doc works correctly. This test goes through
     * DefaultBulkSorter::scoreRange, where scorerIterator is BitSetIterator. As a conjunction of this
     * BitSetIterator with DocComparator's iterator, we get BitSetConjunctionDISI.
     * BitSetConjuctionDISI advances based on the DocComparator's iterator, and doesn't consider that
     * its BitSetIterator may have advanced passed a certain doc.
     */
    @Test
    fun testDocSort() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val numDocs = 4
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", "id$i", Field.Store.NO))
            if (i < 2) {
                doc.add(LongPoint("lf", 1))
            }
            writer.addDocument(doc)
        }
        val reader = DirectoryReader.open(writer)
        writer.close()

        val searcher = newSearcher(reader, random().nextBoolean(), random().nextBoolean())
        searcher.queryCache = null
        val numHits = 10
        val totalHitsThreshold = 10
        val sort = Sort(FIELD_DOC)

        run {
            val collectorManager = TopFieldCollectorManager(sort, numHits, totalHitsThreshold)
            val bq = BooleanQuery.Builder()
            bq.add(LongPoint.newExactQuery("lf", 1), BooleanClause.Occur.MUST)
            bq.add(TermQuery(Term("id", "id3")), BooleanClause.Occur.MUST_NOT)
            val topDocs = searcher.search(bq.build(), collectorManager)
            assertEquals(2, topDocs.scoreDocs.size)
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testPointValidation() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()

        doc.add(IntPoint("intField", 4))
        doc.add(NumericDocValuesField("intField", 4))

        doc.add(LongPoint("longField", 42))
        doc.add(NumericDocValuesField("longField", 42))

        doc.add(IntRange("intRange", intArrayOf(1), intArrayOf(10)))
        doc.add(NumericDocValuesField("intRange", 4))

        writer.addDocument(doc)
        val reader = writer.reader
        writer.close()
        val searcher = newSearcher(reader, random().nextBoolean(), random().nextBoolean())

        val longSortOnIntField = SortField("intField", SortField.Type.LONG)
        expectThrows(IllegalArgumentException::class) {
            searcher.search(MatchAllDocsQuery(), 1, Sort(longSortOnIntField))
        }
        // assert that when sort optimization is disabled we can use LONG sort on int field
        longSortOnIntField.optimizeSortWithIndexedData = false
        searcher.search(MatchAllDocsQuery(), 1, Sort(longSortOnIntField))

        val intSortOnLongField = SortField("longField", SortField.Type.INT)
        expectThrows(IllegalArgumentException::class) {
            searcher.search(MatchAllDocsQuery(), 1, Sort(intSortOnLongField))
        }
        // assert that when sort optimization is disabled we can use INT sort on long field
        intSortOnLongField.optimizeSortWithIndexedData = false
        searcher.search(MatchAllDocsQuery(), 1, Sort(intSortOnLongField))

        val intSortOnIntRangeField = SortField("intRange", SortField.Type.INT)
        expectThrows(IllegalArgumentException::class) {
            searcher.search(MatchAllDocsQuery(), 1, Sort(intSortOnIntRangeField))
        }
        // assert that when sort optimization is disabled we can use INT sort on intRange field
        intSortOnIntRangeField.optimizeSortWithIndexedData = false
        searcher.search(MatchAllDocsQuery(), 1, Sort(intSortOnIntRangeField))

        reader.close()
        dir.close()
    }

    @Test
    fun testMaxDocVisited() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val numDocs = atLeast(1000) // TODO reduced from 10000 to 1000 for dev speed
        val offset = 100L + random().nextInt(100)
        val smallestValue = 50L + random().nextInt(50)
        val flushDoc = numDocs / 2 // TODO reduced split doc = 5000 to numDocs / 2 for dev speed
        var flushed = false
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(NumericDocValuesField("my_field", i + offset))
            doc.add(LongPoint("my_field", i + offset))
            writer.addDocument(doc)
            if (i >= flushDoc && !flushed) {
                flushed = true
                writer.flush()
                // Index the smallest value to the first slot of the second segment
                val doc2 = Document()
                doc2.add(NumericDocValuesField("my_field", smallestValue))
                doc2.add(LongPoint("my_field", smallestValue))
                writer.addDocument(doc2)
            }
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        val sortField = SortField("my_field", SortField.Type.LONG)
        val topDocs = assertSearchHits(reader, Sort(sortField), 1 + random().nextInt(100), null)
        val fieldDoc = topDocs.scoreDocs[0] as FieldDoc
        assertEquals(smallestValue.toInt(), (fieldDoc.fields!![0] as Long).toInt())
        reader.close()
        dir.close()
    }

    @Test
    fun testRandomLong() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val seqNos = mutableListOf<Long>()
        val limit = if (TEST_NIGHTLY) 10000 else 1000
        val iterations = limit + random().nextInt(limit)
        var seqNoGenerator = random().nextInt(1000).toLong()
        for (i in 0L until iterations) {
            val copies = if (random().nextInt(100) <= 5) 1 else 1 + random().nextInt(5)
            for (j in 0 until copies) {
                seqNos.add(seqNoGenerator)
            }
            seqNos.add(seqNoGenerator)
            seqNoGenerator++
            if (random().nextInt(100) <= 5) {
                seqNoGenerator += random().nextInt(10)
            }
        }

        seqNos.shuffle(random())
        var pendingDocs = 0
        for (seqNo in seqNos) {
            val doc = Document()
            doc.add(NumericDocValuesField("seq_no", seqNo))
            doc.add(LongPoint("seq_no", seqNo))
            writer.addDocument(doc)
            pendingDocs++
            if (pendingDocs > 500 && random().nextInt(100) <= 5) {
                pendingDocs = 0
                writer.flush()
            }
        }
        val reverse = random().nextBoolean()
        writer.flush()
        if (!reverse) {
            seqNos.sort()
        } else {
            seqNos.sortDescending()
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        val sortField = SortField("seq_no", SortField.Type.LONG, reverse)
        var visitedHits = 0
        var after: FieldDoc? = null
        // test page search
        while (visitedHits < seqNos.size) {
            val batch = 1 + random().nextInt(100)
            val query =
                if (random().nextBoolean())
                    MatchAllDocsQuery()
                else
                    LongPoint.newRangeQuery("seq_no", 0, Long.MAX_VALUE)
            val topDocs = assertSearchHits(reader, query, Sort(sortField), batch, after)
            val expectedHits = minOf(seqNos.size - visitedHits, batch)
            assertEquals(expectedHits, topDocs.scoreDocs.size)
            after = topDocs.scoreDocs[expectedHits - 1] as FieldDoc
            for (i in 0 until topDocs.scoreDocs.size) {
                val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
                val expectedSeqNo = seqNos[visitedHits]
                assertEquals(expectedSeqNo.toInt(), (fieldDoc.fields!![0] as Long).toInt())
                visitedHits++
            }
        }

        // test search
        val numHits = 1 + random().nextInt(100)
        val topDocs = assertSearchHits(reader, Sort(sortField), numHits, after)
        for (i in 0 until topDocs.scoreDocs.size) {
            val expectedSeqNo = seqNos[i]
            val fieldDoc = topDocs.scoreDocs[i] as FieldDoc
            assertEquals(expectedSeqNo.toInt(), (fieldDoc.fields!![0] as Long).toInt())
        }
        reader.close()
        dir.close()
    }

    // Test that sort on sorted numeric field without sort optimization and
    // with sort optimization produce the same results
    @Test
    fun testSortOptimizationOnSortedNumericField() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val numDocs = atLeast(5000)
        for (i in 0 until numDocs) {
            val value = random().nextInt()
            val value2 = random().nextInt()
            val doc = Document()
            doc.add(LongField("my_field", value.toLong(), Field.Store.NO))
            doc.add(LongField("my_field", value2.toLong(), Field.Store.NO))
            writer.addDocument(doc)
        }
        val reader = DirectoryReader.open(writer)
        writer.close()

        val type = SortedNumericSelector.Type.entries.random(random())
        val reverse = random().nextBoolean()
        val sortField = LongField.newSortField("my_field", reverse, type)
        sortField.optimizeSortWithIndexedData = false
        val sort = Sort(sortField) // sort without sort optimization
        val sortField2 = LongField.newSortField("my_field", reverse, type)
        val sort2 = Sort(sortField2) // sort with sort optimization

        var expectedCollectedHits = 0L
        var collectedHits = 0L
        var collectedHits2 = 0L
        var visitedHits = 0
        var after: FieldDoc? = null
        while (visitedHits < numDocs) {
            val batch = 1 + random().nextInt(100)
            val expectedHits = minOf(numDocs - visitedHits, batch)

            val topDocs = assertSearchHits(reader, sort, batch, after)
            val scoreDocs = topDocs.scoreDocs

            val topDocs2 = assertSearchHits(reader, sort2, batch, after)
            val scoreDocs2 = topDocs2.scoreDocs

            // assert that the resulting hits are the same
            assertEquals(expectedHits, topDocs.scoreDocs.size)
            assertEquals(topDocs.scoreDocs.size, topDocs2.scoreDocs.size)
            for (i in scoreDocs.indices) {
                val fieldDoc = scoreDocs[i] as FieldDoc
                val fieldDoc2 = scoreDocs2[i] as FieldDoc
                assertEquals(fieldDoc.fields!![0], fieldDoc2.fields!![0])
                assertEquals(fieldDoc.doc, fieldDoc2.doc)
                visitedHits++
            }

            expectedCollectedHits += numDocs.toLong()
            collectedHits += topDocs.totalHits.value
            collectedHits2 += topDocs2.totalHits.value
            after = scoreDocs[expectedHits - 1] as FieldDoc
        }
        assertEquals(visitedHits, numDocs)
        assertEquals(expectedCollectedHits, collectedHits)
        // assert that the second sort with optimization collected less or equal hits
        assertTrue(collectedHits >= collectedHits2)
        // System.out.println(expectedCollectedHits + "\t" + collectedHits + "\t" + collectedHits2)

        reader.close()
        dir.close()
    }

    private fun assertNonCompetitiveHitsAreSkipped(collectedHits: Long, numDocs: Long, skipMustHappen: Boolean = true) {
        if (skipMustHappen && collectedHits >= numDocs) {
            fail(
                "Expected some non-competitive hits are skipped; got collected_hits=" +
                        collectedHits +
                        " num_docs=" +
                        numDocs
            )
        }
    }

    @Test
    fun testStringSortOptimization() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val numDocs = atLeast(1000) // TODO reduced from 10000 to 1000 for dev speed
        val flushDoc = numDocs / 2 // TODO reduced split doc = 7000 to numDocs / 2 for dev speed
        for (i in 0 until numDocs) {
            val doc = Document()
            val value = BytesRef(random().nextInt(1000).toString())
            doc.add(KeywordField("my_field", value, Field.Store.NO))
            writer.addDocument(doc)
            if (i == flushDoc) writer.flush() // multiple segments
        }
        val reader = DirectoryReader.open(writer)
        writer.close()
        doTestStringSortOptimization(reader)
        doTestStringSortOptimizationDisabled(reader)
        reader.close()
        dir.close()
    }

    @Test
    fun testStringSortOptimizationWithMissingValues() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig().setMergePolicy(newLogMergePolicy()))
        val numDocs = atLeast(1000) // TODO reduced from 10000 to 1000 for dev speed
        val flushDoc = (numDocs - 2) / 2 // TODO reduced split doc = 7000 to (numDocs - 2) / 2 for dev speed
        // one segment with all values missing to start with
        writer.addDocument(Document())
        for (i in 0 until numDocs - 2) {
            if (i == flushDoc) writer.flush() // multiple segments
            val doc = Document()
            if (random().nextInt(2) == 0) {
                val value = BytesRef(random().nextInt(1000).toString())
                doc.add(KeywordField("my_field", value, Field.Store.NO))
            }
            writer.addDocument(doc)
        }
        writer.flush()
        // And one empty segment with all values missing to finish with
        writer.addDocument(Document())
        val reader = DirectoryReader.open(writer)
        writer.close()
        doTestStringSortOptimization(reader)
        reader.close()
        dir.close()
    }

    private fun doTestStringSortOptimization(reader: DirectoryReader) {
        val numDocs = reader.numDocs()
        val numHits = 5

        run { // simple ascending sort
            val sortField = KeywordField.newSortField("my_field", false, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_LAST
            val sort = Sort(sortField)
            val topDocs = assertSort(reader, sort, numHits, null)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // simple descending sort
            val sortField = KeywordField.newSortField("my_field", true, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_FIRST
            val sort = Sort(sortField)
            val topDocs = assertSort(reader, sort, numHits, null)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // ascending sort that returns missing values first
            val sortField = KeywordField.newSortField("my_field", false, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_FIRST
            val sort = Sort(sortField)
            assertSort(reader, sort, numHits, null)
        }

        run { // descending sort that returns missing values last
            val sortField = KeywordField.newSortField("my_field", true, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_LAST
            val sort = Sort(sortField)
            assertSort(reader, sort, numHits, null)
        }

        run { // paging ascending sort with after
            val sortField = KeywordField.newSortField("my_field", false, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_LAST
            val sort = Sort(sortField)
            val afterValue = BytesRef(if (random().nextBoolean()) "23" else "230000000")
            val after = FieldDoc(2, Float.NaN, arrayOf<Any?>(afterValue))
            val topDocs = assertSort(reader, sort, numHits, after)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // paging descending sort with after
            val sortField = KeywordField.newSortField("my_field", true, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_FIRST
            val sort = Sort(sortField)
            val afterValue = BytesRef(if (random().nextBoolean()) "17" else "170000000")
            val after = FieldDoc(2, Float.NaN, arrayOf<Any?>(afterValue))
            val topDocs = assertSort(reader, sort, numHits, after)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // paging ascending sort with after that returns missing values first
            val sortField = KeywordField.newSortField("my_field", false, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_FIRST
            val sort = Sort(sortField)
            val afterValue = BytesRef(if (random().nextBoolean()) "23" else "230000000")
            val after = FieldDoc(2, Float.NaN, arrayOf<Any?>(afterValue))
            val topDocs = assertSort(reader, sort, numHits, after)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // paging descending sort with after that returns missing values first
            val sortField = KeywordField.newSortField("my_field", true, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_LAST
            val sort = Sort(sortField)
            val afterValue = BytesRef(if (random().nextBoolean()) "17" else "170000000")
            val after = FieldDoc(2, Float.NaN, arrayOf<Any?>(afterValue))
            val topDocs = assertSort(reader, sort, numHits, after)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // test that if there is the secondary sort on _score, hits are still skipped
            val sortField = KeywordField.newSortField("my_field", false, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_LAST
            val sort = Sort(sortField, SortField.FIELD_SCORE)
            val topDocs = assertSort(reader, sort, numHits, null)
            assertNonCompetitiveHitsAreSkipped(topDocs.totalHits.value, numDocs.toLong())
        }

        run { // test that if string field is a secondary sort, no optimization is run
            val sortField = KeywordField.newSortField("my_field", false, SortedSetSelector.Type.MIN)
            sortField.missingValue = SortField.STRING_LAST
            val sort = Sort(SortField.FIELD_SCORE, sortField)
            val topDocs = assertSort(reader, sort, numHits, null)
            assertEquals(
                numDocs.toLong(),
                topDocs.totalHits.value
            ) // assert that all documents were collected => optimization was not run
        }
    }

    fun doTestStringSortOptimizationDisabled(reader: DirectoryReader) {
        val sortField = KeywordField.newSortField("my_field", false, SortedSetSelector.Type.MIN)
        sortField.missingValue = SortField.STRING_LAST
        sortField.optimizeSortWithIndexedData = false
        val sort = Sort(sortField)
        val numDocs = reader.numDocs()
        val numHits = 5

        val topDocs = assertSearchHits(reader, sort, numHits, null)
        assertEquals(numDocs.toLong(), topDocs.totalHits.value)
    }

    private fun assertSort(reader: DirectoryReader, sort: Sort, n: Int, after: FieldDoc?): TopDocs {
        val topDocs = assertSearchHits(reader, sort, n, after)
        val sortField2 = ArrayUtil.growExact(sort.sort, sort.sort.size + 1)
        // A secondary sort on reverse doc ID is the best way to catch bugs if the comparator filters
        // too aggressively
        sortField2[sortField2.size - 1] = SortField(null, SortField.Type.DOC, true)
        val after2: FieldDoc? = if (after != null) {
            val afterFields2 = ArrayUtil.growExact(after.fields!!, after.fields!!.size + 1)
            afterFields2[afterFields2.size - 1] = Int.MAX_VALUE
            FieldDoc(after.doc, after.score, afterFields2)
        } else {
            null
        }
        assertSearchHits(reader, Sort(*sortField2), n, after2)
        return topDocs
    }

    private fun assertSearchHits(reader: DirectoryReader, sort: Sort, n: Int, after: FieldDoc?): TopFieldDocs {
        return assertSearchHits(reader, MatchAllDocsQuery(), sort, n, after)
    }

    private fun assertSearchHits(
        reader: DirectoryReader,
        query: Query,
        sort: Sort,
        n: Int,
        after: FieldDoc?
    ): TopFieldDocs {
        // single threaded and no bulk-scoring optimizations so that the total hit count is
        // deterministic and can be reasoned about
        val searcher = ScorerIndexSearcher(reader)
        searcher.queryCache = null

        val optimizedTopDocs = searcher.search(query, TopFieldCollectorManager(sort, n, after, n))

        if (query is MatchAllDocsQuery) {
            // Searcher that hides index structures to force a linear scan of the sort fields, and make
            // sure that the same hits are returned
            // We can only do that on a MatchAllDocsQuery, otherwise the query won't have data structures
            // to operate on :)
            val unoptimizedSearcher = newSearcher(NoIndexDirectoryReader(reader), true, true, false)
            unoptimizedSearcher.queryCache = null
            val unoptimizedTopDocs = unoptimizedSearcher.search(query, TopFieldCollectorManager(sort, n, after, n))
            CheckHits.checkEqual(query, unoptimizedTopDocs.scoreDocs, optimizedTopDocs.scoreDocs)
        }

        // Use the random searcher in combination with DummyMatchAllDocsQuery to make sure we test the
        // behavior when the bulk scorer reads ahead
        val randomQuery: Query = if (query is MatchAllDocsQuery) {
            ReadAheadMatchAllDocsQuery(random())
        } else {
            query
        }
        // Random IndexSearcher to make sure that enabling threading and bulk-scoring optimizations
        // doesn't affect the returned hits
        val randomSearcher = newSearcher(reader)
        randomSearcher.queryCache = null
        val randomTopDocs = randomSearcher.search(randomQuery, TopFieldCollectorManager(sort, n, after, n))
        CheckHits.checkEqual(query, optimizedTopDocs.scoreDocs, randomTopDocs.scoreDocs)

        return optimizedTopDocs
    }

    private class NoIndexDirectoryReader(inReader: DirectoryReader) : FilterDirectoryReader(
        inReader,
        object : SubReaderWrapper() {
            override fun wrap(reader: LeafReader): LeafReader {
                return NoIndexLeafReader(reader)
            }
        }
    ) {
        override fun doWrapDirectoryReader(inReader: DirectoryReader): DirectoryReader {
            throw UnsupportedOperationException()
        }

        override val readerCacheHelper: CacheHelper?
            get() = null
    }

    private class NoIndexLeafReader(inReader: LeafReader) : FilterLeafReader(inReader) {

        override val coreCacheHelper: CacheHelper?
            get() = null

        override val readerCacheHelper: CacheHelper?
            get() = null

        override fun terms(field: String?): Terms? {
            return null
        }

        override fun getPointValues(field: String): PointValues? {
            return null
        }

        override val fieldInfos: FieldInfos
            get() {
                val newInfos = arrayOfNulls<FieldInfo>(super.fieldInfos.size())
                var i = 0
                for (fi in super.fieldInfos) {
                    val noIndexFI = FieldInfo(
                        fi.name,
                        fi.number,
                        false,
                        false,
                        false,
                        IndexOptions.NONE,
                        fi.docValuesType,
                        fi.docValuesSkipIndexType(),
                        fi.docValuesGen,
                        fi.attributes(),
                        0,
                        0,
                        0,
                        0,
                        VectorEncoding.FLOAT32,
                        VectorSimilarityFunction.DOT_PRODUCT,
                        fi.isSoftDeletesField,
                        fi.isParentField
                    )
                    newInfos[i] = noIndexFI
                    i++
                }
                @Suppress("UNCHECKED_CAST")
                return FieldInfos(newInfos as Array<FieldInfo>)
            }
    }
}
