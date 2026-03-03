package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestGraphTokenFilter : BaseTokenStreamTestCase() {

    class TestFilter(input: TokenStream) : GraphTokenFilter(input) {

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            return incrementBaseToken()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testGraphTokenStream() {

        val tok = TestGraphTokenizers.GraphTokenizer()
        val graph = TestFilter(tok)

        val termAtt = graph.addAttribute(CharTermAttribute::class)
        val posIncAtt = graph.addAttribute(PositionIncrementAttribute::class)

        tok.setReader(StringReader("a b/c d e/f:3 g/h i j k"))
        tok.reset()

        assertFalse(graph.incrementGraph())
        assertEquals(0, graph.cachedTokenCount())

        assertTrue(graph.incrementBaseToken())
        assertEquals("a", termAtt.toString())
        assertEquals(1, posIncAtt.getPositionIncrement())
        assertTrue(graph.incrementGraphToken())
        assertEquals("b", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("d", termAtt.toString())
        assertTrue(graph.incrementGraph())
        assertEquals("a", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("c", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("d", termAtt.toString())
        assertFalse(graph.incrementGraph())
        assertEquals(5, graph.cachedTokenCount())

        assertTrue(graph.incrementBaseToken())
        assertEquals("b", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("d", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("e", termAtt.toString())
        assertTrue(graph.incrementGraph())
        assertEquals("b", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("d", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("f", termAtt.toString())
        assertFalse(graph.incrementGraph())
        assertEquals(6, graph.cachedTokenCount())

        assertTrue(graph.incrementBaseToken())
        assertEquals("c", termAtt.toString())
        assertEquals(0, posIncAtt.getPositionIncrement())
        assertTrue(graph.incrementGraphToken())
        assertEquals("d", termAtt.toString())
        assertFalse(graph.incrementGraph())
        assertEquals(6, graph.cachedTokenCount())

        assertTrue(graph.incrementBaseToken())
        assertEquals("d", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("e", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("g", termAtt.toString())
        assertTrue(graph.incrementGraph())
        assertEquals("d", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("e", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("h", termAtt.toString())
        assertTrue(graph.incrementGraph())
        assertEquals("d", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("f", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("j", termAtt.toString())
        assertFalse(graph.incrementGraph())
        assertEquals(8, graph.cachedTokenCount())

        // tok.setReader(new StringReader("a b/c d e/f:3 g/h i j k"));

        assertTrue(graph.incrementBaseToken())
        assertEquals("e", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("g", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("i", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("j", termAtt.toString())
        assertTrue(graph.incrementGraph())
        assertEquals("e", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("h", termAtt.toString())
        assertFalse(graph.incrementGraph())
        assertEquals(8, graph.cachedTokenCount())

        assertTrue(graph.incrementBaseToken())
        assertEquals("f", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("j", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("k", termAtt.toString())
        assertFalse(graph.incrementGraphToken())
        assertFalse(graph.incrementGraph())
        assertEquals(8, graph.cachedTokenCount())

        assertTrue(graph.incrementBaseToken())
        assertEquals("g", termAtt.toString())
        assertTrue(graph.incrementGraphToken())
        assertEquals("i", termAtt.toString())
        assertFalse(graph.incrementGraph())
        assertEquals(8, graph.cachedTokenCount())

        assertTrue(graph.incrementBaseToken())
        assertEquals("h", termAtt.toString())
        assertFalse(graph.incrementGraph())
        assertEquals(8, graph.cachedTokenCount())

        assertTrue(graph.incrementBaseToken())
        assertTrue(graph.incrementBaseToken())
        assertTrue(graph.incrementBaseToken())
        assertEquals("k", termAtt.toString())
        assertFalse(graph.incrementGraphToken())
        assertEquals(0, graph.getTrailingPositions())
        assertFalse(graph.incrementGraph())
        assertFalse(graph.incrementBaseToken())
        assertEquals(8, graph.cachedTokenCount())
    }

    @Test
    @Throws(IOException::class)
    fun testTrailingPositions() {

        // a/b:2 c _
        val cts = CannedTokenStream(
            1,
            5,
            Token("a", 0, 1),
            Token("b", 0, 0, 1, 2),
            Token("c", 1, 2, 3)
        )

        val gts: GraphTokenFilter = TestFilter(cts)
        assertFalse(gts.incrementGraph())
        assertTrue(gts.incrementBaseToken())
        assertTrue(gts.incrementGraphToken())
        assertFalse(gts.incrementGraphToken())
        assertEquals(1, gts.getTrailingPositions())
        assertFalse(gts.incrementGraph())
        assertTrue(gts.incrementBaseToken())
        assertFalse(gts.incrementGraphToken())
        assertEquals(1, gts.getTrailingPositions())
        assertFalse(gts.incrementGraph())
    }

    @Test
    @Throws(IOException::class)
    fun testMaximumGraphCacheSize() {

        val tokens = Array(GraphTokenFilter.MAX_TOKEN_CACHE_SIZE + 5) { i ->
            Token("a", 1, i * 2, i * 2 + 1)
        }

        val gts: GraphTokenFilter = TestFilter(CannedTokenStream(*tokens))
        val e = expectThrows(IllegalStateException::class) {
            gts.reset()
            gts.incrementBaseToken()
            while (true) {
                gts.incrementGraphToken()
            }
        }
        assertEquals("Too many cached tokens (> 100)", e.message)

        gts.reset()
        // after reset, the cache should be cleared and so we can read ahead once more
        gts.incrementBaseToken()
        gts.incrementGraphToken()
    }

    @Test
    fun testGraphPathCountLimits() {

        val tokens = Array(50) { Token("term", 1, 0, 1) }
        tokens[0] = Token("term", 1, 0, 1)
        tokens[1] = Token("term1", 1, 2, 3)
        for (i in 2..<50) {
            tokens[i] = Token("term$i", i % 2, 2, 3)
        }

        val e = expectThrows(IllegalStateException::class) {
            val graph: GraphTokenFilter = TestFilter(CannedTokenStream(*tokens))
            graph.reset()
            graph.incrementBaseToken()
            for (i in 0..<10) {
                graph.incrementGraphToken()
            }
            while (graph.incrementGraph()) {
                for (i in 0..<10) {
                    graph.incrementGraphToken()
                }
            }
        }
        assertEquals("Too many graph paths (> 1000)", e.message)
    }
}
