package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexableFieldType
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.internal.tests.IndexPackageAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper.Failure
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper.FakeIOException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Abstract class to do basic tests for fis format. NOTE: This test focuses on the fis impl, nothing
 * else. The [stretch] goal is for this test to be so thorough in testing a new fis format that if
 * this test passes, then all Lucene tests should also pass. Ie, if there is some bug in a given fis
 * Format that this test fails to catch then this test needs to be improved!
 */
abstract class BaseFieldInfoFormatTestCase : BaseIndexFileFormatTestCase() {
    /**
     * Override and return `false` if the format does not support setting doc values skip index.
     */
    protected open fun supportDocValuesSkipIndex(): Boolean {
        return true
    }

    /** Test field infos read/write with a single field  */
    @Throws(Exception::class)
    open fun testOneField() {
        val dir: Directory = newDirectory()
        val codec: Codec = codec
        val segmentInfo: SegmentInfo = newSegmentInfo(dir, "_123")
        val fi: FieldInfo = createFieldInfo()
        addAttributes(fi)

        val infos: FieldInfos = INDEX_PACKAGE_ACCESS.newFieldInfosBuilder(null, null).add(fi).finish()

        codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT)

        val infos2: FieldInfos = codec.fieldInfosFormat().read(dir, segmentInfo, "", IOContext.DEFAULT)
        assertEquals(1, infos2.size().toLong())
        assertNotNull(infos2.fieldInfo("field"))
        assertTrue(infos2.fieldInfo("field")!!.indexOptions != IndexOptions.NONE)
        assertFalse(infos2.fieldInfo("field")!!.docValuesType != DocValuesType.NONE)
        assertFalse(infos2.fieldInfo("field")!!.omitsNorms())
        assertFalse(infos2.fieldInfo("field")!!.hasPayloads())
        assertFalse(infos2.fieldInfo("field")!!.hasTermVectors())
        assertEquals(0, infos2.fieldInfo("field")!!.pointDimensionCount.toLong())
        assertEquals(0, infos2.fieldInfo("field")!!.vectorDimension.toLong())
        assertFalse(infos2.fieldInfo("field")!!.isSoftDeletesField)
        dir.close()
    }

    /** Test field infos attributes coming back are not mutable  */
    @Throws(Exception::class)
    open fun testImmutableAttributes() {
        val dir: Directory = newDirectory()
        val codec: Codec = codec
        val segmentInfo: SegmentInfo = newSegmentInfo(dir, "_123")
        val fi: FieldInfo = createFieldInfo()
        addAttributes(fi)
        fi.putAttribute("foo", "bar")
        fi.putAttribute("bar", "baz")

        val infos: FieldInfos = INDEX_PACKAGE_ACCESS.newFieldInfosBuilder(null, null).add(fi).finish()

        codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT)

        val infos2: FieldInfos = codec.fieldInfosFormat()
            .read(dir, segmentInfo, "", IOContext.DEFAULT)
        assertEquals(1, infos2.size().toLong())
        assertNotNull(infos2.fieldInfo("field"))
        val attributes: /*Mutable*/Map<String, String> = infos2.fieldInfo("field")!!.attributes()
        // shouldn't be able to modify attributes
        // in Kotlin, Map is not modifiable so no need to check following:
        /*expectThrows(
            UnsupportedOperationException::class
        ) {
            attributes["bogus"] = "bogus"
        }*/

        dir.close()
    }

    /**
     * Test field infos write that hits exception immediately on open. make sure we get our exception
     * back, no file handle leaks, etc.
     */
    @Throws(Exception::class)
    open fun testExceptionOnCreateOutput() {
        val fail: Failure =
            object : Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFail && callStackContainsAnyOf(
                            "createOutput"
                        )
                    ) {
                        throw MockDirectoryWrapper.FakeIOException()
                    }
                }
            }

        val dir: MockDirectoryWrapper = newMockDirectory()
        dir.failOn(fail)
        val codec: Codec = codec
        val segmentInfo: SegmentInfo = newSegmentInfo(dir, "_123")
        val fi: FieldInfo = createFieldInfo()
        addAttributes(fi)

        val infos: FieldInfos = INDEX_PACKAGE_ACCESS.newFieldInfosBuilder(null, null).add(fi).finish()

        fail.setDoFail()
        expectThrows(
            FakeIOException::class,
            LuceneTestCase.ThrowingRunnable {
                codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT)
            })
        fail.clearDoFail()

        dir.close()
    }

    /**
     * Test field infos write that hits exception on close. make sure we get our exception back, no
     * file handle leaks, etc.
     */
    @Throws(Exception::class)
    open fun testExceptionOnCloseOutput() {
        val fail: Failure =
            object : Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFail && callStackContainsAnyOf(
                            "close"
                        )
                    ) {
                        throw FakeIOException()
                    }
                }
            }

        val dir: MockDirectoryWrapper = newMockDirectory()
        dir.failOn(fail)
        val codec: Codec = codec
        val segmentInfo: SegmentInfo = newSegmentInfo(dir, "_123")
        val fi: FieldInfo = createFieldInfo()
        addAttributes(fi)

        val infos: FieldInfos = INDEX_PACKAGE_ACCESS.newFieldInfosBuilder(null, null).add(fi).finish()

        fail.setDoFail()
        expectThrows(
            FakeIOException::class
        ) {
            codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT)
        }
        fail.clearDoFail()

        dir.close()
    }

    /**
     * Test field infos read that hits exception immediately on open. make sure we get our exception
     * back, no file handle leaks, etc.
     */
    @Throws(Exception::class)
    open fun testExceptionOnOpenInput() {
        val fail: Failure =
            object : Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFail && callStackContainsAnyOf(
                            "openInput"
                        )
                    ) {
                        throw FakeIOException()
                    }
                }
            }

        val dir: MockDirectoryWrapper = newMockDirectory()
        dir.failOn(fail)
        val codec: Codec = codec
        val segmentInfo: SegmentInfo = newSegmentInfo(dir, "_123")
        val fi: FieldInfo = createFieldInfo()
        addAttributes(fi)

        val infos: FieldInfos = INDEX_PACKAGE_ACCESS.newFieldInfosBuilder(null, null).add(fi).finish()

        codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT)

        fail.setDoFail()
        expectThrows(FakeIOException::class) {
            codec.fieldInfosFormat().read(dir, segmentInfo, "", IOContext.DEFAULT)
        }
        fail.clearDoFail()

        dir.close()
    }

    /**
     * Test field infos read that hits exception on close. make sure we get our exception back, no
     * file handle leaks, etc.
     */
    @Throws(Exception::class)
    open fun testExceptionOnCloseInput() {
        val fail: Failure =
            object : Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFail && callStackContainsAnyOf(
                            "close"
                        )
                    ) {
                        throw FakeIOException()
                    }
                }
            }

        val dir: MockDirectoryWrapper = newMockDirectory()
        dir.failOn(fail)
        val codec: Codec = codec
        val segmentInfo: SegmentInfo = newSegmentInfo(dir, "_123")
        val fi: FieldInfo = createFieldInfo()
        addAttributes(fi)

        val infos: FieldInfos = INDEX_PACKAGE_ACCESS.newFieldInfosBuilder(null, null).add(fi).finish()

        codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT)

        fail.setDoFail()
        expectThrows(
            FakeIOException::class
        ) {
            codec.fieldInfosFormat().read(dir, segmentInfo, "", IOContext.DEFAULT)
        }
        fail.clearDoFail()

        dir.close()
    }

    // TODO: more tests
    /** Test field infos read/write with random fields, with different values.  */
    @Throws(Exception::class)
    open fun testRandom() {
        val dir: Directory = newDirectory()
        val codec: Codec = codec
        val segmentInfo: SegmentInfo = newSegmentInfo(dir, "_123")

        // generate a bunch of fields
        val numFields: Int = atLeast(2000)
        val fieldNames: MutableSet<String> = mutableSetOf()
        for (i in 0..<numFields) {
            fieldNames.add(TestUtil.randomUnicodeString(random()))
        }

        val softDeletesField: String? = if (random().nextBoolean()) TestUtil.randomUnicodeString(random()) else null

        var parentField: String? = if (random().nextBoolean()) TestUtil.randomUnicodeString(random()) else null

        if (softDeletesField != null && softDeletesField == parentField) {
            parentField = null
        }
        val builder: IndexPackageAccess.FieldInfosBuilder = INDEX_PACKAGE_ACCESS.newFieldInfosBuilder(softDeletesField, parentField)

        for (field in fieldNames) {
            val fieldType: IndexableFieldType = randomFieldType(random(), field)
            var storeTermVectors = false
            var storePayloads = false
            var omitNorms = false
            if (fieldType.indexOptions() != IndexOptions.NONE) {
                storeTermVectors = fieldType.storeTermVectors()
                omitNorms = fieldType.omitNorms()
                if (fieldType.indexOptions() >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                ) {
                    storePayloads = random().nextBoolean()
                }
            }
            var docValuesSkipIndexType: DocValuesSkipIndexType =
                DocValuesSkipIndexType.NONE
            if (setOf(
                    DocValuesType.NUMERIC,
                    DocValuesType.SORTED,
                    DocValuesType.SORTED_NUMERIC,
                    DocValuesType.SORTED_SET
                ).contains(fieldType.docValuesType())
            ) {
                docValuesSkipIndexType = fieldType.docValuesSkipIndexType()
            }
            val fi =
                FieldInfo(
                    field,
                    -1,
                    storeTermVectors,
                    omitNorms,
                    storePayloads,
                    fieldType.indexOptions(),
                    fieldType.docValuesType(),
                    docValuesSkipIndexType,
                    -1,
                    HashMap(),
                    fieldType.pointDimensionCount(),
                    fieldType.pointIndexDimensionCount(),
                    fieldType.pointNumBytes(),
                    fieldType.vectorDimension(),
                    fieldType.vectorEncoding(),
                    fieldType.vectorSimilarityFunction(),
                    field == softDeletesField,
                    field == parentField
                )
            addAttributes(fi)
            builder.add(fi)
        }
        val infos: FieldInfos = builder.finish()
        codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT)
        val infos2: FieldInfos = codec.fieldInfosFormat().read(dir, segmentInfo, "", IOContext.DEFAULT)
        assertEquals(infos, infos2)
        dir.close()
    }

    private fun getVectorsMaxDimensions(fieldName: String): Int {
        return Codec.default.knnVectorsFormat()
            .getMaxDimensions(fieldName)
    }

    private fun randomFieldType(r: Random, fieldName: String): IndexableFieldType {
        val type = FieldType()

        if (r.nextBoolean()) {
            val values: Array<IndexOptions> =
                IndexOptions.entries.toTypedArray()
            type.setIndexOptions(values[r.nextInt(values.size)])
            type.setOmitNorms(r.nextBoolean())

            if (r.nextBoolean()) {
                type.setStoreTermVectors(true)
                if (type.indexOptions() >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                ) {
                    type.setStoreTermVectorPositions(r.nextBoolean())
                    type.setStoreTermVectorOffsets(r.nextBoolean())
                    if (type.storeTermVectorPositions()) {
                        type.setStoreTermVectorPayloads(r.nextBoolean())
                    }
                }
            }
        }

        if (r.nextBoolean()) {
            val values: Array<DocValuesType> =
                DocValuesType.entries.toTypedArray()
            val current: DocValuesType = values[r.nextInt(values.size)]
            type.setDocValuesType(values[r.nextInt(values.size)])
            if (current == DocValuesType.NUMERIC || current == DocValuesType.SORTED_NUMERIC || current == DocValuesType.SORTED || current == DocValuesType.SORTED_SET) {
                type.setDocValuesSkipIndexType(
                    if (supportDocValuesSkipIndex())
                        DocValuesSkipIndexType.RANGE
                    else
                        DocValuesSkipIndexType.NONE
                )
            }
        }

        if (r.nextBoolean()) {
            val dimension: Int = 1 + r.nextInt(PointValues.MAX_DIMENSIONS)
            val indexDimension: Int = 1 + r.nextInt(min(dimension, PointValues.MAX_INDEX_DIMENSIONS))
            val dimensionNumBytes: Int =
                1 + r.nextInt(PointValues.MAX_NUM_BYTES)
            type.setDimensions(dimension, indexDimension, dimensionNumBytes)
        }

        if (r.nextBoolean() && getVectorsMaxDimensions(fieldName) > 0) {
            val dimension: Int = 1 + r.nextInt(getVectorsMaxDimensions(fieldName))
            val similarityFunction: VectorSimilarityFunction = RandomPicks.randomFrom(r, VectorSimilarityFunction.entries.toTypedArray())
            val encoding: VectorEncoding = RandomPicks.randomFrom(r, VectorEncoding.entries.toTypedArray())
            type.setVectorAttributes(dimension, encoding, similarityFunction)
        }

        return type
    }

    /** Hook to add any codec attributes to fieldinfo instances added in this test.  */
    protected fun addAttributes(fi: FieldInfo) {}

    /** equality for entirety of fieldinfos  */
    protected fun assertEquals(
        expected: FieldInfos,
        actual: FieldInfos
    ) {
        assertEquals(expected.size().toLong(), actual.size().toLong())
        for (expectedField in expected) {
            val actualField: FieldInfo? = actual.fieldInfo(expectedField.number)
            assertNotNull(actualField)
            assertEquals(expectedField, actualField)
        }
    }

    /** equality for two individual fieldinfo objects  */
    protected fun assertEquals(
        expected: FieldInfo,
        actual: FieldInfo
    ) {
        assertEquals(expected.number.toLong(), actual.number.toLong())
        assertEquals(expected.name, actual.name)
        assertEquals(expected.docValuesType, actual.docValuesType)
        assertEquals(
            expected.docValuesSkipIndexType(),
            actual.docValuesSkipIndexType()
        )
        assertEquals(expected.indexOptions, actual.indexOptions)
        assertEquals(expected.hasNorms(), actual.hasNorms())
        assertEquals(expected.hasPayloads(), actual.hasPayloads())
        assertEquals(expected.hasTermVectors(), actual.hasTermVectors())
        assertEquals(expected.omitsNorms(), actual.omitsNorms())
        assertEquals(expected.docValuesGen, actual.docValuesGen)
    }

    override fun addRandomFields(doc: Document) {
        doc.add(StoredField("foobar", TestUtil.randomSimpleString(random())))
    }

    private fun createFieldInfo(): FieldInfo {
        return FieldInfo(
            "field",
            -1,
            false,
            false,
            false,
            TextField.TYPE_STORED.indexOptions(),
            DocValuesType.NONE,
            DocValuesSkipIndexType.NONE,
            -1,
            HashMap(),
            0,
            0,
            0,
            0,
            VectorEncoding.FLOAT32,
            VectorSimilarityFunction.EUCLIDEAN,
            false,
            false
        )
    }

    companion object {
        private val INDEX_PACKAGE_ACCESS: IndexPackageAccess = TestSecrets.getIndexPackageAccess()

        /** Returns a new fake segment  */
        protected fun newSegmentInfo(
            dir: Directory,
            name: String
        ): SegmentInfo {
            val minVersion: Version? = if (random().nextBoolean()) null else Version.LATEST
            return SegmentInfo(
                dir,
                Version.LATEST,
                minVersion,
                name,
                10000,
                false,
                hasBlocks = false,
                codec = Codec.default,
                diagnostics = mutableMapOf(),
                id = StringHelper.randomId(),
                attributes = mutableMapOf(),
                indexSort = null
            )
        }
    }
}
