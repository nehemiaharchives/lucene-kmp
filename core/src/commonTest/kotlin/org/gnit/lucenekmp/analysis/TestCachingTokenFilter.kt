package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.jdkport.AtomicInteger
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestCachingTokenFilter : BaseTokenStreamTestCase() {
    private val tokens = arrayOf("term1", "term2", "term3", "term2")

    @Test
    @Throws(IOException::class)
    fun testCaching() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        val resetCount = AtomicInteger(0)
        var stream: TokenStream = object : TokenStream() {
            private var index = 0
            private val termAtt = addAttribute(CharTermAttribute::class)
            private val offsetAtt = addAttribute(OffsetAttribute::class)

            @Throws(IOException::class)
            override fun reset() {
                super.reset()
                resetCount.store(resetCount.load() + 1)
            }

            override fun incrementToken(): Boolean {
                return if (index == tokens.size) {
                    false
                } else {
                    clearAttributes()
                    termAtt.append(tokens[index++])
                    offsetAtt.setOffset(0, 0)
                    true
                }
            }
        }

        stream = CachingTokenFilter(stream)

        doc.add(TextField("preanalyzed", stream))

        // 1) we consume all tokens twice before we add the doc to the index
        assertFalse((stream as CachingTokenFilter).isCached)
        stream.reset()
        assertFalse(stream.isCached)
        checkTokens(stream)
        stream.reset()
        checkTokens(stream)
        assertTrue(stream.isCached)

        // 2) now add the document to the index and verify if all tokens are indexed
        //    don't reset the stream here, the DocumentWriter should do that implicitly
        writer.addDocument(doc)

        val reader: IndexReader = writer.reader
        var termPositions: PostingsEnum? =
            MultiTerms.getTermPostingsEnum(reader, "preanalyzed", BytesRef("term1"))
        assertTrue(termPositions!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(1, termPositions.freq())
        assertEquals(0, termPositions.nextPosition())

        termPositions = MultiTerms.getTermPostingsEnum(reader, "preanalyzed", BytesRef("term2"))
        assertTrue(termPositions!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(2, termPositions.freq())
        assertEquals(1, termPositions.nextPosition())
        assertEquals(3, termPositions.nextPosition())

        termPositions = MultiTerms.getTermPostingsEnum(reader, "preanalyzed", BytesRef("term3"))
        assertTrue(termPositions!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(1, termPositions.freq())
        assertEquals(2, termPositions.nextPosition())
        reader.close()
        writer.close()
        // 3) reset stream and consume tokens again
        stream.reset()
        checkTokens(stream)

        assertEquals(1, resetCount.load())

        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDoubleResetFails() {
        val analyzer = MockAnalyzer(random())
        val input: TokenStream = analyzer.tokenStream("field", "abc")
        val buffer = CachingTokenFilter(input)
        buffer.reset() // ok
        val e = expectThrows(IllegalStateException::class) {
            buffer.reset() // bad (this used to work which we don't want)
        }
        assertEquals("double reset()", e.message)
    }

    @Throws(IOException::class)
    private fun checkTokens(stream: TokenStream) {
        var count = 0

        val termAtt = stream.getAttribute(CharTermAttribute::class)
        while (stream.incrementToken()) {
            assertTrue(count < tokens.size)
            assertEquals(tokens[count], termAtt.toString())
            count++
        }

        assertEquals(tokens.size, count)
    }
}
