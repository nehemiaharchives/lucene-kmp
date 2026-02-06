package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseLiveDocsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestLucene90LiveDocsFormat : BaseLiveDocsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

    @Test
    override fun testDenseLiveDocs() = super.testDenseLiveDocs()

    @Test
    override fun testEmptyLiveDocs() = super.testEmptyLiveDocs()

    @Test
    override fun testSparseLiveDocs() = super.testSparseLiveDocs()

    @Test
    override fun testOverflow() = super.testOverflow()
}
