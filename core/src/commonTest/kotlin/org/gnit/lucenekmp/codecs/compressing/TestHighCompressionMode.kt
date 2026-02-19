package org.gnit.lucenekmp.codecs.compressing

import kotlin.test.BeforeTest
import kotlin.test.Test

class TestHighCompressionMode : AbstractTestCompressionMode() {
    @BeforeTest
    fun setUp() {
        mode = CompressionMode.HIGH_COMPRESSION
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
