package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseFieldInfoFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestLucene90FieldInfosFormat : BaseFieldInfoFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

    override fun supportDocValuesSkipIndex(): Boolean {
        return false
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
