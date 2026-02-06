package org.gnit.lucenekmp.codecs.simpletext

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseLiveDocsFormatTestCase
import kotlin.test.Test

class TestSimpleTextLiveDocsFormat : BaseLiveDocsFormatTestCase() {
    override val codec: Codec = SimpleTextCodec()

    @Test
    override fun testDenseLiveDocs() = super.testDenseLiveDocs()

    @Test
    override fun testEmptyLiveDocs() = super.testEmptyLiveDocs()

    @Test
    override fun testSparseLiveDocs() = super.testSparseLiveDocs()

    @Test
    override fun testOverflow() = super.testOverflow()
}
