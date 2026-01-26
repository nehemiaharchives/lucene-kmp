package org.gnit.lucenekmp.codecs.lucene94

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.tests.index.BaseFieldInfoFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.assertEquals
import kotlin.test.Test

class TestLucene94FieldInfosFormat : BaseFieldInfoFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

    // Ensures that all expected vector similarity functions are translatable
    // in the format.
    @Test
    fun testVectorSimilarityFuncs() {
        // This does not necessarily have to be all similarity functions, but
        // differences should be considered carefully.
        val expectedValues: MutableList<VectorSimilarityFunction> = VectorSimilarityFunction.entries.toMutableList()

        assertEquals(
            Lucene94FieldInfosFormat.SIMILARITY_FUNCTIONS,
            expectedValues
        )
    }

    @Test
    override fun testOneField() = super.testOneField()

    @Test
    override fun testImmutableAttributes() = super.testImmutableAttributes()

    @Test
    override fun testExceptionOnCreateOutput() = super.testExceptionOnCreateOutput()

    @Test
    override fun testExceptionOnCloseOutput() = super.testExceptionOnCloseOutput()

    @Test
    override fun testExceptionOnOpenInput() = super.testExceptionOnOpenInput()

    @Test
    override fun testExceptionOnCloseInput() = super.testExceptionOnCloseInput()

    @Test
    override fun testRandom() = super.testRandom()
}
