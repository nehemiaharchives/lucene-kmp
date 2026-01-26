package org.gnit.lucenekmp.codecs.simpletext

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseFieldInfoFormatTestCase
import kotlin.test.Test

/** Tests SimpleTextFieldInfoFormat  */
class TestSimpleTextFieldInfoFormat : BaseFieldInfoFormatTestCase() {
    override val codec: Codec = SimpleTextCodec()

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
