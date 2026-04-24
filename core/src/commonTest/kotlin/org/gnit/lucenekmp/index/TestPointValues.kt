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
import org.gnit.lucenekmp.codecs.simpletext.SimpleTextCodec
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoublePoint
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FloatPoint
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Test Indexing/IndexWriter with points */
class TestPointValues : LuceneTestCase() {

    // Suddenly add points to an existing field:
    @Test
    fun testUpgradeFieldToPoints() {
        val dir: Directory = newDirectory()
        var iwc = newIndexWriterConfig()
        var w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(newStringField("dim", "foo", Field.Store.NO))
        w.addDocument(doc)
        w.close()

        iwc = newIndexWriterConfig()
        w = IndexWriter(dir, iwc)
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.close()
        dir.close()
    }

    // Illegal schema change tests:

    @Test
    fun testIllegalDimChangeOneDoc() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
        val expected =
            expectThrows(IllegalArgumentException::class) { w.addDocument(doc) }
        assertEquals(
            "Inconsistency of field data structures across documents for field [dim] of doc [0]." +
                " point dimension: expected '1', but it has '2'.",
            expected.message
        )
        w.close()
        dir.close()
    }

    @Test
    fun testIllegalDimChangeTwoDocs() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)

        val doc2 = Document()
        doc2.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
        val expected =
            expectThrows(IllegalArgumentException::class) { w.addDocument(doc2) }
        assertEquals(
            "Inconsistency of field data structures across documents for field [dim] of doc [1]." +
                " point dimension: expected '1', but it has '2'.",
            expected.message
        )
        w.close()
        dir.close()
    }

    @Test
    fun testIllegalDimChangeTwoSegments() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.commit()

        val doc2 = Document()
        doc2.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
        val expected =
            expectThrows(IllegalArgumentException::class) { w.addDocument(doc2) }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=1, indexDimensionCount=1, numBytes=4 " +
                "to inconsistent dimensionCount=2, indexDimensionCount=2, numBytes=4",
            expected.message
        )
        w.close()
        dir.close()
    }

    @Test
    fun testIllegalDimChangeTwoWriters() {
        val dir: Directory = newDirectory()
        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        var w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.close()
        iwc = IndexWriterConfig(MockAnalyzer(random()))

        val w2 = IndexWriter(dir, iwc)
        val doc2 = Document()
        doc2.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
        val expected =
            expectThrows(IllegalArgumentException::class) { w2.addDocument(doc2) }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=1, indexDimensionCount=1, numBytes=4 " +
                "to inconsistent dimensionCount=2, indexDimensionCount=2, numBytes=4",
            expected.message
        )
        w2.close()
        dir.close()
    }

    @Test
    fun testIllegalDimChangeViaAddIndexesDirectory() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.close()

        val dir2: Directory = newDirectory()
        val w2 = IndexWriter(dir2, IndexWriterConfig(MockAnalyzer(random())))
        doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
        w2.addDocument(doc)
        val expected =
            expectThrows(IllegalArgumentException::class) { w2.addIndexes(dir) }

        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=2, indexDimensionCount=2, numBytes=4 " +
                "to inconsistent dimensionCount=1, indexDimensionCount=1, numBytes=4",
            expected.message
        )
        IOUtils.close(w2, dir, dir2)
    }

    @Test
    fun testIllegalDimChangeViaAddIndexesCodecReader() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.close()

        val dir2: Directory = newDirectory()
        val w2 = IndexWriter(dir2, IndexWriterConfig(MockAnalyzer(random())))
        doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
        w2.addDocument(doc)
        val r = DirectoryReader.open(dir)
        val expected =
            expectThrows(IllegalArgumentException::class) {
                w2.addIndexes(getOnlyLeafReader(r) as CodecReader)
            }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=2, indexDimensionCount=2, numBytes=4 " +
                "to inconsistent dimensionCount=1, indexDimensionCount=1, numBytes=4",
            expected.message
        )
        IOUtils.close(r, w2, dir, dir2)
    }

    @Test
    fun testIllegalDimChangeViaAddIndexesSlowCodecReader() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.close()

        val dir2: Directory = newDirectory()
        val iwc2 = IndexWriterConfig(MockAnalyzer(random()))
        val w2 = IndexWriter(dir2, iwc2)
        doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
        w2.addDocument(doc)
        val r = DirectoryReader.open(dir)
        val expected =
            expectThrows(IllegalArgumentException::class) { TestUtil.addIndexesSlowly(w2, r) }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=2, indexDimensionCount=2, numBytes=4 " +
                "to inconsistent dimensionCount=1, indexDimensionCount=1, numBytes=4",
            expected.message
        )
        IOUtils.close(r, w2, dir, dir2)
    }

    @Test
    fun testIllegalNumBytesChangeOneDoc() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(6))))
        val expected =
            expectThrows(IllegalArgumentException::class) { w.addDocument(doc) }
        assertEquals(
            "Inconsistency of field data structures across documents for field [dim] of doc [0]." +
                " point num bytes: expected '4', but it has '6'.",
            expected.message
        )
        w.close()
        dir.close()
    }

    @Test
    fun testIllegalNumBytesChangeTwoDocs() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)

        val doc2 = Document()
        doc2.add(BinaryPoint("dim", arrayOf(ByteArray(6))))
        val expected =
            expectThrows(IllegalArgumentException::class) { w.addDocument(doc2) }
        assertEquals(
            "Inconsistency of field data structures across documents for field [dim] of doc [1]." +
                " point num bytes: expected '4', but it has '6'.",
            expected.message
        )
        w.close()
        dir.close()
    }

    @Test
    fun testIllegalNumBytesChangeTwoSegments() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.commit()

        val doc2 = Document()
        doc2.add(BinaryPoint("dim", arrayOf(ByteArray(6))))
        val expected =
            expectThrows(IllegalArgumentException::class) { w.addDocument(doc2) }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=1, indexDimensionCount=1, numBytes=4 " +
                "to inconsistent dimensionCount=1, indexDimensionCount=1, numBytes=6",
            expected.message
        )
        w.close()
        dir.close()
    }

    @Test
    fun testIllegalNumBytesChangeTwoWriters() {
        val dir: Directory = newDirectory()
        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        var w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.close()

        iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w2 = IndexWriter(dir, iwc)
        val doc2 = Document()
        doc2.add(BinaryPoint("dim", arrayOf(ByteArray(6))))

        val expected =
            expectThrows(IllegalArgumentException::class) { w2.addDocument(doc2) }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=1, indexDimensionCount=1, numBytes=4 " +
                "to inconsistent dimensionCount=1, indexDimensionCount=1, numBytes=6",
            expected.message
        )
        w2.close()
        dir.close()
    }

    @Test
    fun testIllegalNumBytesChangeViaAddIndexesDirectory() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.close()

        val dir2: Directory = newDirectory()
        val iwc2 = IndexWriterConfig(MockAnalyzer(random()))
        val w2 = IndexWriter(dir2, iwc2)
        doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(6))))
        w2.addDocument(doc)
        val expected =
            expectThrows(IllegalArgumentException::class) { w2.addIndexes(dir) }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=1, indexDimensionCount=1, numBytes=6 " +
                "to inconsistent dimensionCount=1, indexDimensionCount=1, numBytes=4",
            expected.message
        )
        IOUtils.close(w2, dir, dir2)
    }

    @Test
    fun testIllegalNumBytesChangeViaAddIndexesCodecReader() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.close()

        val dir2: Directory = newDirectory()
        val iwc2 = IndexWriterConfig(MockAnalyzer(random()))
        val w2 = IndexWriter(dir2, iwc2)
        doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(6))))
        w2.addDocument(doc)
        val r = DirectoryReader.open(dir)
        val expected =
            expectThrows(IllegalArgumentException::class) {
                w2.addIndexes(getOnlyLeafReader(r) as CodecReader)
            }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=1, indexDimensionCount=1, numBytes=6 " +
                "to inconsistent dimensionCount=1, indexDimensionCount=1, numBytes=4",
            expected.message
        )
        IOUtils.close(r, w2, dir, dir2)
    }

    @Test
    fun testIllegalNumBytesChangeViaAddIndexesSlowCodecReader() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(4))))
        w.addDocument(doc)
        w.close()

        val dir2: Directory = newDirectory()
        val iwc2 = IndexWriterConfig(MockAnalyzer(random()))
        val w2 = IndexWriter(dir2, iwc2)
        doc = Document()
        doc.add(BinaryPoint("dim", arrayOf(ByteArray(6))))
        w2.addDocument(doc)
        val r = DirectoryReader.open(dir)
        val expected =
            expectThrows(IllegalArgumentException::class) { TestUtil.addIndexesSlowly(w2, r) }
        assertEquals(
            "cannot change field \"dim\" from points dimensionCount=1, indexDimensionCount=1, numBytes=6 " +
                "to inconsistent dimensionCount=1, indexDimensionCount=1, numBytes=4",
            expected.message
        )
        IOUtils.close(r, w2, dir, dir2)
    }

    @Test
    fun testIllegalTooManyBytes() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        expectThrows(IllegalArgumentException::class) {
            doc.add(BinaryPoint("dim", arrayOf(ByteArray(PointValues.MAX_NUM_BYTES + 1))))
        }

        val doc2 = Document()
        doc2.add(IntPoint("dim", 17))
        w.addDocument(doc2)
        w.close()
        dir.close()
    }

    @Test
    fun testIllegalTooManyDimensions() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        val values = Array(PointValues.MAX_INDEX_DIMENSIONS + 1) { ByteArray(4) }
        expectThrows(IllegalArgumentException::class) { doc.add(BinaryPoint("dim", values)) }

        val doc2 = Document()
        doc2.add(IntPoint("dim", 17))
        w.addDocument(doc2)
        w.close()
        dir.close()
    }

    // Write point values, one segment with Lucene84, another with SimpleText, then forceMerge with
    // SimpleText
    @Test
    fun testDifferentCodecs1() {
        // KMP workaround: ServiceLoader/ClassLoader is unavailable, so force codec registration.
        SimpleTextCodec()
        val dir: Directory = newDirectory()
        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setCodec(TestUtil.getDefaultCodec())
        var w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(IntPoint("int", 1))
        w.addDocument(doc)
        w.close()

        iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setCodec(Codec.forName("SimpleText"))
        w = IndexWriter(dir, iwc)
        doc = Document()
        doc.add(IntPoint("int", 1))
        w.addDocument(doc)

        w.forceMerge(1)
        w.close()
        dir.close()
    }

    // Write point values, one segment with Lucene84, another with SimpleText, then forceMerge with
    // Lucene84
    @Test
    fun testDifferentCodecs2() {
        // KMP workaround: ServiceLoader/ClassLoader is unavailable, so force codec registration.
        SimpleTextCodec()
        val dir: Directory = newDirectory()
        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setCodec(Codec.forName("SimpleText"))
        var w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(IntPoint("int", 1))
        w.addDocument(doc)
        w.close()

        iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setCodec(TestUtil.getDefaultCodec())
        w = IndexWriter(dir, iwc)
        doc = Document()
        doc.add(IntPoint("int", 1))
        w.addDocument(doc)

        w.forceMerge(1)
        w.close()
        dir.close()
    }

    @Test
    fun testInvalidIntPointUsage() {
        val field = IntPoint("field", 17, 42)

        expectThrows(IllegalArgumentException::class) { field.setIntValue(14) }

        expectThrows(IllegalStateException::class) { field.numericValue() }
    }

    @Test
    fun testInvalidLongPointUsage() {
        val field = LongPoint("field", 17, 42)

        expectThrows(IllegalArgumentException::class) { field.setLongValue(14) }

        expectThrows(IllegalStateException::class) { field.numericValue() }
    }

    @Test
    fun testInvalidFloatPointUsage() {
        val field = FloatPoint("field", 17f, 42f)

        expectThrows(IllegalArgumentException::class) { field.setFloatValue(14f) }

        expectThrows(IllegalStateException::class) { field.numericValue() }
    }

    @Test
    fun testInvalidDoublePointUsage() {
        val field = DoublePoint("field", 17.0, 42.0)

        expectThrows(IllegalArgumentException::class) { field.setDoubleValue(14.0) }

        expectThrows(IllegalStateException::class) { field.numericValue() }
    }

    @Test
    fun testTieBreakByDocID() {
        val dir: Directory = newFSDirectory(createTempDir())
        val iwc = newIndexWriterConfig()
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(IntPoint("int", 17))
        val numDocs = if (TEST_NIGHTLY) 300000 else 3000
        for (i in 0..<numDocs) {
            w.addDocument(doc)
            if (random().nextInt(1000) == 17) {
                w.commit()
            }
        }

        val r = DirectoryReader.open(w)

        for (ctx in r.leaves()) {
            val points = requireNotNull(ctx.reader().getPointValues("int"))
            points.intersect(
                object : IntersectVisitor {
                    var lastDocID = -1

                    override fun visit(docID: Int) {
                        if (docID < lastDocID) {
                            fail("docs out of order: docID=$docID but lastDocID=$lastDocID")
                        }
                        lastDocID = docID
                    }

                    override fun visit(docID: Int, packedValue: ByteArray) {
                        visit(docID)
                    }

                    override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                        return if (random().nextBoolean()) {
                            Relation.CELL_CROSSES_QUERY
                        } else {
                            Relation.CELL_INSIDE_QUERY
                        }
                    }
                }
            )
        }

        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testDeleteAllPointDocs() {
        val dir: Directory = newDirectory()
        val iwc = newIndexWriterConfig()
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        doc.add(IntPoint("int", 17))
        w.addDocument(doc)
        w.addDocument(Document())
        w.commit()

        w.deleteDocuments(Term("id", "0"))

        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        assertNull(r.leaves()[0].reader().getPointValues("int"))
        w.close()
        r.close()
        dir.close()
    }

    @Test
    fun testPointsFieldMissingFromOneSegment() {
        val dir: Directory = FSDirectory.open(createTempDir())
        val iwc = IndexWriterConfig()
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        doc.add(IntPoint("int0", 0))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(IntPoint("int1", 17))
        w.addDocument(doc)
        w.forceMerge(1)

        w.close()
        dir.close()
    }

    @Test
    fun testSparsePoints() {
        val dir: Directory = newDirectory()
        val numDocs = atLeast(1000)
        val numFields = TestUtil.nextInt(random(), 1, 10)
        val w = RandomIndexWriter(random(), dir)
        val fieldDocCounts = IntArray(numFields)
        val fieldSizes = IntArray(numFields)
        for (i in 0..<numDocs) {
            val doc = Document()
            for (field in 0..<numFields) {
                val fieldName = "int$field"
                if (random().nextInt(100) == 17) {
                    doc.add(IntPoint(fieldName, random().nextInt()))
                    fieldDocCounts[field]++
                    fieldSizes[field]++

                    if (random().nextInt(10) == 5) {
                        // add same field again!
                        doc.add(IntPoint(fieldName, random().nextInt()))
                        fieldSizes[field]++
                    }
                }
            }
            w.addDocument(doc)
        }

        val r = w.getReader(true, false)
        for (field in 0..<numFields) {
            var docCount = 0
            var size = 0L
            val fieldName = "int$field"
            for (ctx in r.leaves()) {
                val points = ctx.reader().getPointValues(fieldName)
                if (points != null) {
                    docCount += points.docCount
                    size += points.size()
                }
            }
            assertEquals(fieldDocCounts[field], docCount)
            assertEquals(fieldSizes[field].toLong(), size)
        }
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testCheckIndexIncludesPoints() {
        val dir: Directory = ByteBuffersDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        var doc = Document()
        doc.add(IntPoint("int1", 17))
        w.addDocument(doc)

        doc = Document()
        doc.add(IntPoint("int1", 44))
        doc.add(IntPoint("int2", -17))
        w.addDocument(doc)
        w.close()

        val output = ByteArrayOutputStream()
        val status =
            TestUtil.checkIndex(
                dir,
                CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS,
                true,
                true,
                output
            )
        assertEquals(1, status.segmentInfos.size)
        val segStatus = status.segmentInfos[0]
        // total 3 point values were index:
        val pointsStatus = requireNotNull(segStatus.pointsStatus)
        assertEquals(3L, pointsStatus.totalValuePoints)
        // ... across 2 fields:
        assertEquals(2, pointsStatus.totalValueFields)

        // Make sure CheckIndex in fact declares that it is testing points!
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: points..."))
        dir.close()
    }

    @Test
    fun testMergedStatsEmptyReader() {
        val reader: IndexReader = MultiReader()
        assertNull(PointValues.getMinPackedValue(reader, "field"))
        assertNull(PointValues.getMaxPackedValue(reader, "field"))
        assertEquals(0, PointValues.getDocCount(reader, "field"))
        assertEquals(0L, PointValues.size(reader, "field"))
    }

    @Test
    fun testMergedStatsOneSegmentWithoutPoints() {
        val dir: Directory = ByteBuffersDirectory()
        val w =
            IndexWriter(dir, IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
        w.addDocument(Document())
        DirectoryReader.open(w).close()
        val doc = Document()
        doc.add(IntPoint("field", Int.MIN_VALUE))
        w.addDocument(doc)
        val reader: IndexReader = DirectoryReader.open(w)

        assertContentEquals(ByteArray(4), requireNotNull(PointValues.getMinPackedValue(reader, "field")))
        assertContentEquals(ByteArray(4), requireNotNull(PointValues.getMaxPackedValue(reader, "field")))
        assertEquals(1, PointValues.getDocCount(reader, "field"))
        assertEquals(1L, PointValues.size(reader, "field"))

        assertNull(PointValues.getMinPackedValue(reader, "field2"))
        assertNull(PointValues.getMaxPackedValue(reader, "field2"))
        assertEquals(0, PointValues.getDocCount(reader, "field2"))
        assertEquals(0L, PointValues.size(reader, "field2"))
    }

    @Test
    fun testMergedStatsAllPointsDeleted() {
        val dir: Directory = ByteBuffersDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        w.addDocument(Document())
        val doc = Document()
        doc.add(IntPoint("field", Int.MIN_VALUE))
        doc.add(StringField("delete", "yes", Store.NO))
        w.addDocument(doc)
        w.forceMerge(1)
        w.deleteDocuments(Term("delete", "yes"))
        w.addDocument(Document())
        w.forceMerge(1)
        val reader: IndexReader = DirectoryReader.open(w)

        assertNull(PointValues.getMinPackedValue(reader, "field"))
        assertNull(PointValues.getMaxPackedValue(reader, "field"))
        assertEquals(0, PointValues.getDocCount(reader, "field"))
        assertEquals(0L, PointValues.size(reader, "field"))
    }

    @Test
    fun testMergedStats() {
        val iters = atLeast(3)
        for (iter in 0..<iters) {
            doTestMergedStats()
        }
    }

    private fun doTestMergedStats() {
        val numDims = TestUtil.nextInt(random(), 1, 8)
        val numBytesPerDim = TestUtil.nextInt(random(), 1, 16)
        val dir: Directory = ByteBuffersDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        val numDocs = TestUtil.nextInt(random(), 10, 20)
        for (i in 0..<numDocs) {
            val doc = Document()
            val numPoints = random().nextInt(3)
            for (j in 0..<numPoints) {
                doc.add(BinaryPoint("field", randomBinaryValue(numDims, numBytesPerDim)))
            }
            w.addDocument(doc)
            if (random().nextBoolean()) {
                DirectoryReader.open(w).close()
            }
        }

        val reader1: IndexReader = DirectoryReader.open(w)
        w.forceMerge(1)
        val reader2: IndexReader = DirectoryReader.open(w)
        val expected = getOnlyLeafReader(reader2).getPointValues("field")
        if (expected == null) {
            assertNull(PointValues.getMinPackedValue(reader1, "field"))
            assertNull(PointValues.getMaxPackedValue(reader1, "field"))
            assertEquals(0, PointValues.getDocCount(reader1, "field"))
            assertEquals(0L, PointValues.size(reader1, "field"))
        } else {
            assertContentEquals(expected.minPackedValue, PointValues.getMinPackedValue(reader1, "field"))
            assertContentEquals(expected.maxPackedValue, PointValues.getMaxPackedValue(reader1, "field"))
            assertEquals(expected.docCount, PointValues.getDocCount(reader1, "field"))
            assertEquals(expected.size(), PointValues.size(reader1, "field"))
        }
        IOUtils.close(w, reader1, reader2, dir)
    }

    companion object {
        private fun randomBinaryValue(numDims: Int, numBytesPerDim: Int): Array<ByteArray> {
            val bytes = Array(numDims) { ByteArray(numBytesPerDim) }
            for (i in 0..<numDims) {
                random().nextBytes(bytes[i])
            }
            return bytes
        }
    }
}
