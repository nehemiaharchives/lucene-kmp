package org.gnit.lucenekmp.codecs.compressing

import org.gnit.lucenekmp.codecs.lucene90.DeflateWithPresetDictCompressionMode
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestDeflateWithPresetDictCompressionMode : AbstractTestCompressionMode() {
    @BeforeTest
    fun setup() {
        mode = DeflateWithPresetDictCompressionMode()
    }

    @Test
    override fun testDecompress() = super.testDecompress()

    @Test
    override fun testConstant() = super.testConstant()

    @Test
    override fun testExtremelyLargeInput() = super.testExtremelyLargeInput()

    @Test
    override fun testPartialDecompress() = super.testPartialDecompress()
}
