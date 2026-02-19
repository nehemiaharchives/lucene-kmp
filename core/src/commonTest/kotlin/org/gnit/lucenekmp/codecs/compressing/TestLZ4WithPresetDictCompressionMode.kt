package org.gnit.lucenekmp.codecs.compressing

import org.gnit.lucenekmp.codecs.lucene90.LZ4WithPresetDictCompressionMode
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestLZ4WithPresetDictCompressionMode : AbstractTestCompressionMode() {
    @BeforeTest
    fun setUp() {
        mode = LZ4WithPresetDictCompressionMode()
    }

    // tests inherited from AbstractTestCompressionMode
    @Test
    override fun testDecompress() = super.testDecompress()

    @Test
    override fun testConstant() = super.testConstant()

    @Test
    override fun testExtremelyLargeInput() = super.testExtremelyLargeInput()

    @Test
    override fun testPartialDecompress() = super.testPartialDecompress()
}
