package org.gnit.lucenekmp.analysis.morfologik

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** TODO: The tests below rely on the order of returned lemmas, which is probably not good. */
class TestMorfologikAnalyzer : BaseTokenStreamTestCase() {
    private fun getTestAnalyzer(): Analyzer = MorfologikAnalyzer()

    @Test
    @Throws(Exception::class)
    fun testSingleTokens() {
        val a = getTestAnalyzer()
        assertAnalyzesTo(a, "a", arrayOf("a"))
        assertAnalyzesTo(a, "liście", arrayOf("liście", "liść", "list", "lista"))
        assertAnalyzesTo(a, "danych", arrayOf("dany", "dana", "dane", "dać"))
        assertAnalyzesTo(a, "ęóąśłżźćń", arrayOf("ęóąśłżźćń"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleTokens() {
        val a = getTestAnalyzer()
        assertAnalyzesTo(
            a,
            "liście danych",
            arrayOf("liście", "liść", "list", "lista", "dany", "dana", "dane", "dać"),
            intArrayOf(0, 0, 0, 0, 7, 7, 7, 7),
            intArrayOf(6, 6, 6, 6, 13, 13, 13, 13),
            intArrayOf(1, 0, 0, 0, 1, 0, 0, 0)
        )

        assertAnalyzesTo(
            a,
            "T. Glücksberg",
            arrayOf("tom", "tona", "Glücksberg"),
            intArrayOf(0, 0, 3),
            intArrayOf(1, 1, 13),
            intArrayOf(1, 0, 1)
        )
        a.close()
    }

    private fun assertPOSToken(ts: TokenStream, term: String, vararg tags: String) {
        ts.incrementToken()
        assertEquals(term, ts.getAttribute(CharTermAttribute::class).toString())

        val actual = TreeSet<String>()
        val expected = TreeSet<String>()
        val attribute = ts.getAttribute(MorphosyntacticTagsAttribute::class)
        attribute.getTags()?.forEach { actual.add(it.toString()) }
        tags.forEach { expected.add(it) }

        if (expected != actual) {
            println("Expected:\n$expected")
            println("Actual:\n$actual")
            assertEquals(expected, actual)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testLeftoverStems() {
        val a = getTestAnalyzer()
        val ts1 = a.tokenStream("dummy", "liście")
        try {
            val termAtt1 = ts1.getAttribute(CharTermAttribute::class)
            ts1.reset()
            ts1.incrementToken()
            assertEquals("liście", termAtt1.toString(), "first stream")
            ts1.end()
        } finally {
            ts1.close()
        }

        val ts2 = a.tokenStream("dummy", "danych")
        try {
            val termAtt2 = ts2.getAttribute(CharTermAttribute::class)
            ts2.reset()
            ts2.incrementToken()
            assertEquals("dany", termAtt2.toString(), "second stream")
            ts2.end()
        } finally {
            ts2.close()
        }
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCase() {
        val a = getTestAnalyzer()

        assertAnalyzesTo(a, "AGD", arrayOf("AGD", "artykuły gospodarstwa domowego"))
        assertAnalyzesTo(a, "agd", arrayOf("artykuły gospodarstwa domowego"))

        assertAnalyzesTo(a, "Poznania", arrayOf("Poznań"))
        assertAnalyzesTo(a, "poznania", arrayOf("poznanie", "poznać"))

        assertAnalyzesTo(a, "Aarona", arrayOf("Aaron"))
        assertAnalyzesTo(a, "aarona", arrayOf("aarona"))

        assertAnalyzesTo(a, "Liście", arrayOf("liście", "liść", "list", "lista"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPOSAttribute() {
        val a = getTestAnalyzer()
        val ts = a.tokenStream("dummy", "liście")
        try {
            ts.reset()
            assertPOSToken(ts, "liście", "subst:sg:acc:n2", "subst:sg:nom:n2", "subst:sg:voc:n2")
            assertPOSToken(ts, "liść", "subst:pl:acc:m3", "subst:pl:nom:m3", "subst:pl:voc:m3")
            assertPOSToken(ts, "list", "subst:sg:loc:m3", "subst:sg:voc:m3")
            assertPOSToken(ts, "lista", "subst:sg:dat:f", "subst:sg:loc:f")
            ts.end()
        } finally {
            ts.close()
            a.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testKeywordAttrTokens() {
        val a: Analyzer = object : MorfologikAnalyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val keywords = CharArraySet(1, false)
                keywords.add("liście")

                val src: Tokenizer = StandardTokenizer(MorfologikAttributeFactory())
                var result: TokenStream = SetKeywordMarkerFilter(src, keywords)
                result = MorfologikFilter(result)

                return TokenStreamComponents(src, result)
            }
        }

        assertAnalyzesTo(
            a,
            "liście danych",
            arrayOf("liście", "dany", "dana", "dane", "dać"),
            intArrayOf(0, 7, 7, 7, 7),
            intArrayOf(6, 13, 13, 13, 13),
            intArrayOf(1, 1, 0, 0, 0)
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandom() {
        val a = getTestAnalyzer()
        checkRandomData(random(), a, 1000 * RANDOM_MULTIPLIER)
        a.close()
    }
}
