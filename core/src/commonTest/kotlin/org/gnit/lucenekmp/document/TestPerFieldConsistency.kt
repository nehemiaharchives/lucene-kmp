package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomDouble
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomFloat
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomInt
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomIntBetween
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomLong
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPerFieldConsistency : LuceneTestCase() {
    companion object {
        private fun randomIndexedField(random: Random, fieldName: String): Field {
            val fieldType = FieldType()
            var indexOptions = RandomPicks.randomFrom(random, IndexOptions.entries.toTypedArray())
            while (indexOptions == IndexOptions.NONE) {
                indexOptions = RandomPicks.randomFrom(random, IndexOptions.entries.toTypedArray())
            }
            fieldType.setIndexOptions(indexOptions)
            fieldType.setStoreTermVectors(random.nextBoolean())
            if (fieldType.storeTermVectors()) {
                fieldType.setStoreTermVectorPositions(random.nextBoolean())
                if (fieldType.storeTermVectorPositions()) {
                    fieldType.setStoreTermVectorPayloads(random.nextBoolean())
                    fieldType.setStoreTermVectorOffsets(random.nextBoolean())
                }
            }
            fieldType.setOmitNorms(random.nextBoolean())
            fieldType.setStored(random.nextBoolean())
            fieldType.freeze()

            return Field(fieldName, "randomValue", fieldType)
        }

        private fun randomPointField(random: Random, fieldName: String): Field {
            return when (random.nextInt(4)) {
                0 -> LongPoint(fieldName, randomLong())
                1 -> IntPoint(fieldName, randomInt())
                2 -> DoublePoint(fieldName, randomDouble())
                else -> FloatPoint(fieldName, randomFloat())
            }
        }

        private fun randomDocValuesField(random: Random, fieldName: String): Field {
            return when (random.nextInt(4)) {
                0 -> BinaryDocValuesField(fieldName, BytesRef("randomValue"))
                1 -> NumericDocValuesField(fieldName, randomLong())
                2 -> DoubleDocValuesField(fieldName, randomDouble())
                else -> SortedSetDocValuesField(fieldName, BytesRef("randomValue"))
            }
        }

        private fun randomKnnVectorField(random: Random, fieldName: String): Field {
            val similarityFunction =
                RandomPicks.randomFrom(random, VectorSimilarityFunction.entries.toTypedArray())
            val values = FloatArray(randomIntBetween(1, 10))
            for (i in values.indices) {
                values[i] = randomFloat()
            }
            return KnnFloatVectorField(fieldName, values, similarityFunction)
        }

        private fun randomFieldsWithTheSameName(fieldName: String): Array<Field> {
            val textField = randomIndexedField(random(), fieldName)
            val docValuesField = randomDocValuesField(random(), fieldName)
            val pointField = randomPointField(random(), fieldName)
            val vectorField = randomKnnVectorField(random(), fieldName)
            return arrayOf(textField, docValuesField, pointField, vectorField)
        }

        private fun doTestDocWithMissingSchemaOptionsThrowsError(
            fields: Array<Field>,
            missing: Int,
            writer: IndexWriter,
            errorMsg: String
        ) {
            val doc = Document()
            for (i in fields.indices) {
                if (i != missing) {
                    doc.add(fields[i])
                }
            }
            val exception = expectThrows(IllegalArgumentException::class) { writer.addDocument(doc) }
            val message = exception.message!!
            assertTrue(
                message.contains(errorMsg)
                    || message.contains(
                    "cannot have docValuesSkipIndexType=RANGE with doc values type NONE"
                ),
                "'$errorMsg' not found in '$message'"
            )
        }

        private fun doTestDocWithExtraSchemaOptionsThrowsError(
            existing: Field,
            extra: Field,
            writer: IndexWriter,
            errorMsg: String
        ) {
            val doc = Document()
            doc.add(existing)
            doc.add(extra)
            val exception = expectThrows(IllegalArgumentException::class) { writer.addDocument(doc) }
            val message = exception.message!!
            assertTrue(
                message.contains(errorMsg)
                    || message.contains(
                    "cannot have docValuesSkipIndexType=RANGE with doc values type NONE"
                ),
                "'$errorMsg' not found in '$message'"
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDocWithMissingSchemaOptionsThrowsError() {
        newDirectory().use { dir: Directory ->
            IndexWriter(
                dir,
                IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
            ).use { writer ->
                val fields = randomFieldsWithTheSameName("myfield")
                val doc0 = Document()
                for (field in fields) {
                    doc0.add(field)
                }
                writer.addDocument(doc0)

                // the same segment: indexing a doc with a missing field throws error
                var numNotIndexedDocs = 0
                for (missingFieldIdx in fields.indices) {
                    numNotIndexedDocs++
                    doTestDocWithMissingSchemaOptionsThrowsError(
                        fields,
                        missingFieldIdx,
                        writer,
                        "Inconsistency of field data structures across documents for field [myfield] of doc [$numNotIndexedDocs]."
                    )
                }
                writer.flush()
                DirectoryReader.open(writer).use { reader: IndexReader ->
                    assertEquals(1, reader.leaves().size)
                    assertEquals(1, reader.leaves()[0].reader().numDocs())
                    assertEquals(numNotIndexedDocs, reader.leaves()[0].reader().numDeletedDocs())
                }

                // diff segment, same index: indexing a doc with a missing field throws error
                numNotIndexedDocs = 0
                for (missingFieldIdx in fields.indices) {
                    numNotIndexedDocs++
                    doTestDocWithMissingSchemaOptionsThrowsError(
                        fields,
                        missingFieldIdx,
                        writer,
                        "cannot change field \"myfield\" from "
                    )
                }
                writer.addDocument(doc0) // add document with correct data structures
                writer.flush()
                DirectoryReader.open(writer).use { reader: IndexReader ->
                    assertEquals(2, reader.leaves().size)
                    assertEquals(1, reader.leaves()[1].reader().numDocs())
                    assertEquals(numNotIndexedDocs, reader.leaves()[1].reader().numDeletedDocs())
                }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDocWithExtraSchemaOptionsThrowsError() {
        newDirectory().use { dir: Directory ->
            IndexWriter(
                dir,
                IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
            ).use { writer ->
                val fields = randomFieldsWithTheSameName("myfield")
                val doc0 = Document()
                val existingFieldIdx = randomIntBetween(0, fields.size - 1)
                doc0.add(fields[existingFieldIdx])
                writer.addDocument(doc0)

                // the same segment: indexing a field with extra field indexing options returns error
                var numNotIndexedDocs = 0
                for (extraFieldIndex in fields.indices) {
                    if (extraFieldIndex == existingFieldIdx) continue
                    numNotIndexedDocs++
                    doTestDocWithExtraSchemaOptionsThrowsError(
                        fields[existingFieldIdx],
                        fields[extraFieldIndex],
                        writer,
                        "Inconsistency of field data structures across documents for field [myfield] of doc [$numNotIndexedDocs]."
                    )
                }
                writer.flush()
                DirectoryReader.open(writer).use { reader: IndexReader ->
                    assertEquals(1, reader.leaves().size)
                    assertEquals(1, reader.leaves()[0].reader().numDocs())
                    assertEquals(numNotIndexedDocs, reader.leaves()[0].reader().numDeletedDocs())
                }

                // diff segment, same index: indexing a field with extra field indexing options returns error
                numNotIndexedDocs = 0
                for (extraFieldIndex in fields.indices) {
                    if (extraFieldIndex == existingFieldIdx) continue
                    numNotIndexedDocs++
                    doTestDocWithExtraSchemaOptionsThrowsError(
                        fields[existingFieldIdx],
                        fields[extraFieldIndex],
                        writer,
                        "cannot change field \"myfield\" from "
                    )
                }
                try {
                    writer.addDocument(doc0) // add document with correct data structures
                } catch (e: IllegalArgumentException) {
                    val message = e.message ?: ""
                    assertTrue(
                        message.contains("cannot have docValuesSkipIndexType=RANGE with doc values type NONE")
                    )
                    return@use
                }
                writer.flush()
                DirectoryReader.open(writer).use { reader: IndexReader ->
                    assertEquals(2, reader.leaves().size)
                    assertEquals(1, reader.leaves()[1].reader().numDocs())
                    assertEquals(numNotIndexedDocs, reader.leaves()[1].reader().numDeletedDocs())
                }
            }
        }
    }
}
