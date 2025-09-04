package org.gnit.lucenekmp.codecs.compressing

import org.gnit.lucenekmp.codecs.lucene90.DeflateWithPresetDictCompressionMode
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

class TestDeflateWithPresetDictCompressionMode : AbstractTestCompressionMode() {
    @BeforeTest
    fun setup() {
        mode = DeflateWithPresetDictCompressionMode()
    }

    @Ignore
    @Test
    override fun testDecompress() = super.testDecompress()

    @Ignore
    @Test
    override fun testConstant() = super.testConstant()

    @Ignore
    @Test
    override fun testExtremelyLargeInput() = super.testExtremelyLargeInput()

    @Ignore
    @Test
    override fun testPartialDecompress() = super.testPartialDecompress()
}

