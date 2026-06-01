package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.AnalyzerWrapper
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.core.SimpleAnalyzer
import org.gnit.lucenekmp.analysis.core.WhitespaceAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockCharFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestPerFieldAnalyzerWrapper : BaseTokenStreamTestCase() {
    @Test
    fun testPerField() {
        val text = "Qwerty"

        val analyzerPerField = mapOf("special" to SimpleAnalyzer() as Analyzer)

        val defaultAnalyzer: Analyzer = WhitespaceAnalyzer()

        val analyzer = PerFieldAnalyzerWrapper(defaultAnalyzer, analyzerPerField)

        analyzer.tokenStream("field", text).use { tokenStream ->
            val termAtt = tokenStream.getAttribute(CharTermAttribute::class)!!
            tokenStream.reset()

            assertTrue(tokenStream.incrementToken())
            assertEquals("Qwerty", termAtt.toString(), "WhitespaceAnalyzer does not lowercase")
            assertFalse(tokenStream.incrementToken())
            tokenStream.end()
        }

        analyzer.tokenStream("special", text).use { tokenStream ->
            val termAtt = tokenStream.getAttribute(CharTermAttribute::class)!!
            tokenStream.reset()

            assertTrue(tokenStream.incrementToken())
            assertEquals("qwerty", termAtt.toString(), "SimpleAnalyzer lowercases")
            assertFalse(tokenStream.incrementToken())
            tokenStream.end()
        }
        // TODO: fix this about PFAW, this is crazy
        analyzer.close()
        defaultAnalyzer.close()
        IOUtils.close(analyzerPerField.values)
    }

    @Test
    fun testReuseWrapped() {
        val text = "Qwerty"

        val specialAnalyzer: Analyzer = SimpleAnalyzer()
        val defaultAnalyzer: Analyzer = WhitespaceAnalyzer()

        var ts1: TokenStream
        var ts2: TokenStream
        var ts3: TokenStream
        var ts4: TokenStream

        val wrapper1 =
            PerFieldAnalyzerWrapper(
                defaultAnalyzer,
                mapOf("special" to specialAnalyzer)
            )

        // test that the PerFieldWrapper returns the same instance as original Analyzer:
        ts1 = defaultAnalyzer.tokenStream("something", text)
        ts2 = wrapper1.tokenStream("something", text)
        ts3 = wrapper1.tokenStream("somethingElse", text)
        assertSame(ts1, ts2)
        assertSame(ts2, ts3)

        ts1 = specialAnalyzer.tokenStream("special", text)
        ts2 = wrapper1.tokenStream("special", text)
        assertSame(ts1, ts2)

        // Wrap with another wrapper, which does *not* extend DelegatingAnalyzerWrapper:
        val wrapper2 =
            object : AnalyzerWrapper(wrapper1.reuseStrategy) {
                override fun getWrappedAnalyzer(fieldName: String): Analyzer {
                    return wrapper1
                }

                override fun wrapComponents(
                    fieldName: String,
                    components: Analyzer.TokenStreamComponents,
                ): Analyzer.TokenStreamComponents {
                    assertNotSame(
                        specialAnalyzer.tokenStream("special", text),
                        components.tokenStream
                    )
                    val filter: TokenFilter = ASCIIFoldingFilter(components.tokenStream)
                    return Analyzer.TokenStreamComponents(components.getSource(), filter)
                }
            }
        ts3 = wrapper2.tokenStream("special", text)
        assertNotSame(ts1, ts3)
        assertTrue(ts3 is ASCIIFoldingFilter)
        // check that cache did not get corrumpted:
        ts2 = wrapper1.tokenStream("special", text)
        assertSame(ts1, ts2)

        // Wrap PerField with another PerField. In that case all TokenStreams returned must be the same:
        val wrapper3 =
            PerFieldAnalyzerWrapper(
                wrapper1,
                mapOf("moreSpecial" to specialAnalyzer)
            )
        ts1 = specialAnalyzer.tokenStream("special", text)
        ts2 = wrapper3.tokenStream("special", text)
        assertSame(ts1, ts2)
        ts3 = specialAnalyzer.tokenStream("moreSpecial", text)
        ts4 = wrapper3.tokenStream("moreSpecial", text)
        assertSame(ts3, ts4)
        assertSame(ts2, ts3)
        IOUtils.close(wrapper3, wrapper2, wrapper1, specialAnalyzer, defaultAnalyzer)
    }

    @Test
    fun testCharFilters() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(MockTokenizer())
                }

                override fun initReader(fieldName: String, reader: Reader): Reader {
                    return MockCharFilter(reader, 7)
                }
            }
        assertAnalyzesTo(a, "ab", arrayOf("aab"), intArrayOf(0), intArrayOf(2))

        // now wrap in PFAW
        val p = PerFieldAnalyzerWrapper(a, emptyMap())

        assertAnalyzesTo(p, "ab", arrayOf("aab"), intArrayOf(0), intArrayOf(2))
        p.close()
        a.close() // TODO: fix this about PFAW, its a trap
    }
}
