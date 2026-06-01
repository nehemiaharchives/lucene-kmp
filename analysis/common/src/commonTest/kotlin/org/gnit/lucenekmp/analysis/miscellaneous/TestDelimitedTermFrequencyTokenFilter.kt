package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestDelimitedTermFrequencyTokenFilter : BaseTokenStreamTestCase() {
    @Test
    fun testTermFrequency() {
        val test = "The quick|40 red|4 fox|06 jumped|1 over the lazy|2 brown|123 dogs|1024"
        val filter = DelimitedTermFrequencyTokenFilter(whitespaceMockTokenizer(test))
        val termAtt = filter.getAttribute(CharTermAttribute::class)!!
        val tfAtt = filter.getAttribute(TermFrequencyAttribute::class)!!
        filter.reset()
        assertTermEquals("The", filter, termAtt, tfAtt, 1)
        assertTermEquals("quick", filter, termAtt, tfAtt, 40)
        assertTermEquals("red", filter, termAtt, tfAtt, 4)
        assertTermEquals("fox", filter, termAtt, tfAtt, 6)
        assertTermEquals("jumped", filter, termAtt, tfAtt, 1)
        assertTermEquals("over", filter, termAtt, tfAtt, 1)
        assertTermEquals("the", filter, termAtt, tfAtt, 1)
        assertTermEquals("lazy", filter, termAtt, tfAtt, 2)
        assertTermEquals("brown", filter, termAtt, tfAtt, 123)
        assertTermEquals("dogs", filter, termAtt, tfAtt, 1024)
        assertFalse(filter.incrementToken())
        filter.end()
        filter.close()
    }

    @Test
    fun testInvalidNegativeTf() {
        val test = "foo bar|-20"
        val filter = DelimitedTermFrequencyTokenFilter(whitespaceMockTokenizer(test))
        val termAtt = filter.getAttribute(CharTermAttribute::class)!!
        val tfAtt = filter.getAttribute(TermFrequencyAttribute::class)!!
        filter.reset()
        assertTermEquals("foo", filter, termAtt, tfAtt, 1)
        val iae = expectThrows(IllegalArgumentException::class) { filter.incrementToken() }
        assertEquals("Term frequency must be 1 or greater; got -20", iae.message)
    }

    @Test
    fun testInvalidFloatTf() {
        val test = "foo bar|1.2"
        val filter = DelimitedTermFrequencyTokenFilter(whitespaceMockTokenizer(test))
        val termAtt = filter.getAttribute(CharTermAttribute::class)!!
        val tfAtt = filter.getAttribute(TermFrequencyAttribute::class)!!
        filter.reset()
        assertTermEquals("foo", filter, termAtt, tfAtt, 1)
        expectThrows(NumberFormatException::class) { filter.incrementToken() }
    }

    fun assertTermEquals(
        expected: String,
        stream: TokenStream,
        termAtt: CharTermAttribute,
        tfAtt: TermFrequencyAttribute,
        expectedTf: Int,
    ) {
        assertTrue(stream.incrementToken())
        assertEquals(expected, termAtt.toString())
        assertEquals(expectedTf, tfAtt.termFrequency)
    }
}
