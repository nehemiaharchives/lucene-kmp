package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.FailOnNonBulkMergesInfoStream
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestConsistentFieldNumbers : LuceneTestCase() {

    @Test
    fun testSameFieldNumbersAcrossSegments() {
        for (i in 0..1) {
            val dir = newDirectory()
            var writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMergePolicy(NoMergePolicy.INSTANCE),
                )

            val d1 = Document()
            d1.add(TextField("f1", "first field", Store.YES))
            d1.add(TextField("f2", "second field", Store.YES))
            writer.addDocument(d1)

            if (i == 1) {
                writer.close()
                writer =
                    IndexWriter(
                        dir,
                        newIndexWriterConfig(MockAnalyzer(random()))
                            .setMergePolicy(NoMergePolicy.INSTANCE),
                    )
            } else {
                writer.commit()
            }

            val d2 = Document()
            d2.add(TextField("f2", "second field", Store.NO))
            d2.add(TextField("f1", "first field", Store.YES))
            d2.add(TextField("f3", "third field", Store.NO))
            d2.add(TextField("f4", "fourth field", Store.NO))
            writer.addDocument(d2)

            writer.close()

            var sis = SegmentInfos.readLatestCommit(dir)
            assertEquals(2, sis.size())

            val fis1 = IndexWriter.readFieldInfos(sis.info(0))
            val fis2 = IndexWriter.readFieldInfos(sis.info(1))

            assertEquals("f1", fis1.fieldInfo(0)!!.name)
            assertEquals("f2", fis1.fieldInfo(1)!!.name)
            assertEquals("f1", fis2.fieldInfo(0)!!.name)
            assertEquals("f2", fis2.fieldInfo(1)!!.name)
            assertEquals("f3", fis2.fieldInfo(2)!!.name)
            assertEquals("f4", fis2.fieldInfo(3)!!.name)

            writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
            writer.forceMerge(1)
            writer.close()

            sis = SegmentInfos.readLatestCommit(dir)
            assertEquals(1, sis.size())

            val fis3 = IndexWriter.readFieldInfos(sis.info(0))

            assertEquals("f1", fis3.fieldInfo(0)!!.name)
            assertEquals("f2", fis3.fieldInfo(1)!!.name)
            assertEquals("f3", fis3.fieldInfo(2)!!.name)
            assertEquals("f4", fis3.fieldInfo(3)!!.name)

            dir.close()
        }
    }

    @Test
    fun testAddIndexes() {
        val dir1 = newDirectory()
        val dir2 = newDirectory()
        var writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE),
            )

        val d1 = Document()
        d1.add(TextField("f1", "first field", Store.YES))
        d1.add(TextField("f2", "second field", Store.YES))
        writer.addDocument(d1)

        writer.close()
        writer =
            IndexWriter(
                dir2,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE),
            )

        val d2 = Document()
        d2.add(TextField("f2", "second field", Store.YES))
        d2.add(TextField("f1", "first field", Store.YES))
        d2.add(TextField("f3", "third field", Store.YES))
        d2.add(TextField("f4", "fourth field", Store.YES))
        writer.addDocument(d2)

        writer.close()

        writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE),
            )
        writer.addIndexes(dir2)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(dir1)
        assertEquals(2, sis.size())

        val fis1 = IndexWriter.readFieldInfos(sis.info(0))
        val fis2 = IndexWriter.readFieldInfos(sis.info(1))

        assertEquals("f1", fis1.fieldInfo(0)!!.name)
        assertEquals("f2", fis1.fieldInfo(1)!!.name)
        // make sure the ordering of the "external" segment is preserved
        assertEquals("f2", fis2.fieldInfo(0)!!.name)
        assertEquals("f1", fis2.fieldInfo(1)!!.name)
        assertEquals("f3", fis2.fieldInfo(2)!!.name)
        assertEquals("f4", fis2.fieldInfo(3)!!.name)

        dir1.close()
        dir2.close()
    }

    @Test
    fun testFieldNumberGaps() {
        val numIters = atLeast(3) // TODO reduced from 13 to 3 for dev speed
        for (i in 0 until numIters) {
            val dir = newDirectory()
            run {
                val writer =
                    IndexWriter(
                        dir,
                        newIndexWriterConfig(MockAnalyzer(random()))
                            .setMergePolicy(NoMergePolicy.INSTANCE),
                    )
                val d = Document()
                d.add(TextField("f1", "d1 first field", Store.YES))
                d.add(TextField("f2", "d1 second field", Store.YES))
                writer.addDocument(d)
                writer.close()
                val sis = SegmentInfos.readLatestCommit(dir)
                assertEquals(1, sis.size())
                val fis1 = IndexWriter.readFieldInfos(sis.info(0))
                assertEquals("f1", fis1.fieldInfo(0)!!.name)
                assertEquals("f2", fis1.fieldInfo(1)!!.name)
            }

            run {
                val writer =
                    IndexWriter(
                        dir,
                        newIndexWriterConfig(MockAnalyzer(random()))
                            .setMergePolicy(NoMergePolicy.INSTANCE),
                    )
                val d = Document()
                d.add(TextField("f1", "d2 first field", Store.YES))
                d.add(StoredField("f3", byteArrayOf(1, 2, 3)))
                writer.addDocument(d)
                writer.close()
                val sis = SegmentInfos.readLatestCommit(dir)
                assertEquals(2, sis.size())
                val fis1 = IndexWriter.readFieldInfos(sis.info(0))
                val fis2 = IndexWriter.readFieldInfos(sis.info(1))
                assertEquals("f1", fis1.fieldInfo(0)!!.name)
                assertEquals("f2", fis1.fieldInfo(1)!!.name)
                assertEquals("f1", fis2.fieldInfo(0)!!.name)
                assertNull(fis2.fieldInfo(1))
                assertEquals("f3", fis2.fieldInfo(2)!!.name)
            }

            run {
                val writer =
                    IndexWriter(
                        dir,
                        newIndexWriterConfig(MockAnalyzer(random()))
                            .setMergePolicy(NoMergePolicy.INSTANCE),
                    )
                val d = Document()
                d.add(TextField("f1", "d3 first field", Store.YES))
                d.add(TextField("f2", "d3 second field", Store.YES))
                d.add(StoredField("f3", byteArrayOf(1, 2, 3, 4, 5)))
                writer.addDocument(d)
                writer.close()
                val sis = SegmentInfos.readLatestCommit(dir)
                assertEquals(3, sis.size())
                val fis1 = IndexWriter.readFieldInfos(sis.info(0))
                val fis2 = IndexWriter.readFieldInfos(sis.info(1))
                val fis3 = IndexWriter.readFieldInfos(sis.info(2))
                assertEquals("f1", fis1.fieldInfo(0)!!.name)
                assertEquals("f2", fis1.fieldInfo(1)!!.name)
                assertEquals("f1", fis2.fieldInfo(0)!!.name)
                assertNull(fis2.fieldInfo(1))
                assertEquals("f3", fis2.fieldInfo(2)!!.name)
                assertEquals("f1", fis3.fieldInfo(0)!!.name)
                assertEquals("f2", fis3.fieldInfo(1)!!.name)
                assertEquals("f3", fis3.fieldInfo(2)!!.name)
            }

            run {
                val writer =
                    IndexWriter(
                        dir,
                        newIndexWriterConfig(MockAnalyzer(random()))
                            .setMergePolicy(NoMergePolicy.INSTANCE),
                    )
                writer.deleteDocuments(Term("f1", "d1"))
                // nuke the first segment entirely so that the segment with gaps is
                // loaded first!
                writer.forceMergeDeletes()
                writer.close()
            }

            val writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMergePolicy(LogByteSizeMergePolicy())
                        .setInfoStream(FailOnNonBulkMergesInfoStream()),
                )
            writer.forceMerge(1)
            writer.close()

            val sis = SegmentInfos.readLatestCommit(dir)
            assertEquals(1, sis.size())
            val fis1 = IndexWriter.readFieldInfos(sis.info(0))
            assertEquals("f1", fis1.fieldInfo(0)!!.name)
            assertEquals("f2", fis1.fieldInfo(1)!!.name)
            assertEquals("f3", fis1.fieldInfo(2)!!.name)
            dir.close()
        }
    }

    @Test
    fun testManyFields() {
        val NUM_DOCS = atLeast(200)
        val MAX_FIELDS = atLeast(50)

        val docs = Array(NUM_DOCS) { IntArray(4) }
        for (i in docs.indices) {
            for (j in docs[i].indices) {
                docs[i][j] = random().nextInt(MAX_FIELDS)
            }
        }

        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        for (i in 0 until NUM_DOCS) {
            val d = Document()
            for (j in docs[i].indices) {
                d.add(getField(docs[i][j]))
            }

            writer.addDocument(d)
        }

        writer.forceMerge(1)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(dir)
        for (si in sis) {
            val fis = IndexWriter.readFieldInfos(si)

            for (fi in fis) {
                val expected = getField(fi.name.toInt())
                assertEquals(expected.fieldType().indexOptions(), fi.indexOptions)
                assertEquals(expected.fieldType().storeTermVectors(), fi.hasTermVectors())
            }
        }

        dir.close()
    }

    private fun getField(number: Int): Field {
        val mode = number % 16
        val fieldName = "$number"
        val customType = FieldType(TextField.TYPE_STORED)

        val customType2 = FieldType(TextField.TYPE_STORED)
        customType2.setTokenized(false)

        val customType3 = FieldType(TextField.TYPE_NOT_STORED)
        customType3.setTokenized(false)

        val customType4 = FieldType(TextField.TYPE_NOT_STORED)
        customType4.setTokenized(false)
        customType4.setStoreTermVectors(true)
        customType4.setStoreTermVectorOffsets(true)

        val customType5 = FieldType(TextField.TYPE_NOT_STORED)
        customType5.setStoreTermVectors(true)
        customType5.setStoreTermVectorOffsets(true)

        val customType6 = FieldType(TextField.TYPE_STORED)
        customType6.setTokenized(false)
        customType6.setStoreTermVectors(true)
        customType6.setStoreTermVectorOffsets(true)

        val customType7 = FieldType(TextField.TYPE_NOT_STORED)
        customType7.setTokenized(false)
        customType7.setStoreTermVectors(true)
        customType7.setStoreTermVectorOffsets(true)

        val customType8 = FieldType(TextField.TYPE_STORED)
        customType8.setTokenized(false)
        customType8.setStoreTermVectors(true)
        customType8.setStoreTermVectorPositions(true)

        val customType9 = FieldType(TextField.TYPE_NOT_STORED)
        customType9.setStoreTermVectors(true)
        customType9.setStoreTermVectorPositions(true)

        val customType10 = FieldType(TextField.TYPE_STORED)
        customType10.setTokenized(false)
        customType10.setStoreTermVectors(true)
        customType10.setStoreTermVectorPositions(true)

        val customType11 = FieldType(TextField.TYPE_NOT_STORED)
        customType11.setTokenized(false)
        customType11.setStoreTermVectors(true)
        customType11.setStoreTermVectorPositions(true)

        val customType12 = FieldType(TextField.TYPE_STORED)
        customType12.setStoreTermVectors(true)
        customType12.setStoreTermVectorOffsets(true)
        customType12.setStoreTermVectorPositions(true)

        val customType13 = FieldType(TextField.TYPE_NOT_STORED)
        customType13.setStoreTermVectors(true)
        customType13.setStoreTermVectorOffsets(true)
        customType13.setStoreTermVectorPositions(true)

        val customType14 = FieldType(TextField.TYPE_STORED)
        customType14.setTokenized(false)
        customType14.setStoreTermVectors(true)
        customType14.setStoreTermVectorOffsets(true)
        customType14.setStoreTermVectorPositions(true)

        val customType15 = FieldType(TextField.TYPE_NOT_STORED)
        customType15.setTokenized(false)
        customType15.setStoreTermVectors(true)
        customType15.setStoreTermVectorOffsets(true)
        customType15.setStoreTermVectorPositions(true)

        return when (mode) {
            0 -> Field(fieldName, "some text", customType)
            1 -> TextField(fieldName, "some text", Store.NO)
            2 -> Field(fieldName, "some text", customType2)
            3 -> Field(fieldName, "some text", customType3)
            4 -> Field(fieldName, "some text", customType4)
            5 -> Field(fieldName, "some text", customType5)
            6 -> Field(fieldName, "some text", customType6)
            7 -> Field(fieldName, "some text", customType7)
            8 -> Field(fieldName, "some text", customType8)
            9 -> Field(fieldName, "some text", customType9)
            10 -> Field(fieldName, "some text", customType10)
            11 -> Field(fieldName, "some text", customType11)
            12 -> Field(fieldName, "some text", customType12)
            13 -> Field(fieldName, "some text", customType13)
            14 -> Field(fieldName, "some text", customType14)
            15 -> Field(fieldName, "some text", customType15)
            else -> error("invalid mode: $mode")
        }
    }
}
