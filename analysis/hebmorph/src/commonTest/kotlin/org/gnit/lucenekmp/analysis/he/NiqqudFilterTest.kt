package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NiqqudFilterTest {
    class NiqqudTestAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val src: Tokenizer = StandardTokenizer()
            val filter: TokenStream = NiqqudFilter(src)
            return TokenStreamComponents(src, filter)
        }
    }

    @Test
    fun testRemoveNiqqud() {
        val a = NiqqudTestAnalyzer()
        HebrewTestUtil.assertAnalyzesTo(a, "תָּכְנִית מַבְרִיקָה", arrayOf("תכנית", "מבריקה"))
    }

    @Test
    fun testOffsets() {
        // Niqqud characters occupy space and need to be taken into account
        val analyzer = NiqqudTestAnalyzer()
        val stream = analyzer.tokenStream("foo", StringReader("תָּכְנִית מַבְרִיקָה"))
        stream.use {
            val offsetAtt = it.addAttribute(OffsetAttribute::class)
            it.reset()
            assertTrue(it.incrementToken())
            assertEquals(0, offsetAtt.startOffset())
            assertEquals(9, offsetAtt.endOffset())
            assertTrue(it.incrementToken())
            assertEquals(10, offsetAtt.startOffset())
            assertEquals(20, offsetAtt.endOffset())
            assertFalse(it.incrementToken())
            it.end()
        }
    }
}
