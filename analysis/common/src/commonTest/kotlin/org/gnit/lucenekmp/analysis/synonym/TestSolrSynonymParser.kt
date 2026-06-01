package org.gnit.lucenekmp.analysis.synonym

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.en.EnglishAnalyzer
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/**
 * Tests parser for the Solr synonyms format
 *
 * @lucene.experimental
 */
@Deprecated("Tests deprecated Solr synonym parser path")
class TestSolrSynonymParser : BaseSynonymParserTestCase() {
    /** Tests some simple examples from the solr wiki */
    @Test
    fun testSimple() {
        val testFile =
            "i-pod, ipod, ipoooood\n" + "foo => foo bar\n" + "foo => baz\n" + "this test, that testing"

        var analyzer: Analyzer = MockAnalyzer(random())
        val parser = SolrSynonymParser(true, true, analyzer)
        parser.parse(StringReader(testFile))
        val map = parser.build()
        analyzer.close()

        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
                    return TokenStreamComponents(tokenizer, SynonymFilter(tokenizer, map, true))
                }
            }

        assertAnalyzesTo(analyzer, "ball", arrayOf("ball"), intArrayOf(1))

        assertAnalyzesTo(
            analyzer,
            "i-pod",
            arrayOf("i-pod", "ipod", "ipoooood"),
            intArrayOf(1, 0, 0)
        )

        assertAnalyzesTo(analyzer, "foo", arrayOf("foo", "baz", "bar"), intArrayOf(1, 0, 1))

        assertAnalyzesTo(
            analyzer,
            "this test",
            arrayOf("this", "that", "test", "testing"),
            intArrayOf(1, 0, 1, 0)
        )
        analyzer.close()
    }

    /** parse a syn file with bad syntax */
    @Test
    fun testInvalidDoubleMap() {
        val testFile = "a => b => c"
        val analyzer: Analyzer = MockAnalyzer(random())
        val parser = SolrSynonymParser(true, true, analyzer)
        expectThrows(ParseException::class) {
            parser.parse(StringReader(testFile))
        }
        analyzer.close()
    }

    /** parse a syn file with bad syntax */
    @Test
    fun testInvalidAnalyzesToNothingOutput() {
        val testFile = "a => 1"
        val analyzer: Analyzer = MockAnalyzer(random(), MockTokenizer.SIMPLE, false)
        val parser = SolrSynonymParser(true, true, analyzer)
        expectThrows(ParseException::class) {
            parser.parse(StringReader(testFile))
        }
        analyzer.close()
    }

    /** parse a syn file with bad syntax */
    @Test
    fun testInvalidAnalyzesToNothingInput() {
        val testFile = "1 => a"
        val analyzer: Analyzer = MockAnalyzer(random(), MockTokenizer.SIMPLE, false)
        val parser = SolrSynonymParser(true, true, analyzer)
        expectThrows(ParseException::class) {
            parser.parse(StringReader(testFile))
        }
        analyzer.close()
    }

    /** parse a syn file with bad syntax */
    @Test
    fun testInvalidPositionsInput() {
        val testFile = "testola => the test"
        val analyzer: Analyzer = EnglishAnalyzer()
        val parser = SolrSynonymParser(true, true, analyzer)
        expectThrows(ParseException::class) {
            parser.parse(StringReader(testFile))
        }
        analyzer.close()
    }

    /** parse a syn file with bad syntax */
    @Test
    fun testInvalidPositionsOutput() {
        val testFile = "the test => testola"
        val analyzer: Analyzer = EnglishAnalyzer()
        val parser = SolrSynonymParser(true, true, analyzer)
        expectThrows(ParseException::class) {
            parser.parse(StringReader(testFile))
        }
        analyzer.close()
    }

    /** parse a syn file with some escaped syntax chars */
    @Test
    fun testEscapedStuff() {
        val testFile = "a\\=>a => b\\=>b\n" + "a\\,a => b\\,b"
        var analyzer: Analyzer = MockAnalyzer(random(), MockTokenizer.KEYWORD, false)
        val parser = SolrSynonymParser(true, true, analyzer)
        parser.parse(StringReader(testFile))
        val map = parser.build()
        analyzer.close()
        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.KEYWORD, false)
                    return TokenStreamComponents(tokenizer, SynonymFilter(tokenizer, map, false))
                }
            }

        assertAnalyzesTo(analyzer, "ball", arrayOf("ball"), intArrayOf(1))
        assertAnalyzesTo(analyzer, "a=>a", arrayOf("b=>b"), intArrayOf(1))
        assertAnalyzesTo(analyzer, "a,a", arrayOf("b,b"), intArrayOf(1))
        analyzer.close()
    }

    /** Verify type of token and positionLength after analyzer. */
    @Test
    fun testPositionLengthAndTypeSimple() {
        val testFile = "spider man, spiderman"

        var analyzer: Analyzer = MockAnalyzer(random())
        val parser = SolrSynonymParser(true, true, analyzer)
        parser.parse(StringReader(testFile))
        val map = parser.build()
        analyzer.close()

        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
                    return TokenStreamComponents(tokenizer, SynonymFilter(tokenizer, map, true))
                }
            }

        assertAnalyzesToPositions(
            analyzer,
            "spider man",
            arrayOf("spider", "spiderman", "man"),
            arrayOf("word", "SYNONYM", "word"),
            intArrayOf(1, 0, 1),
            intArrayOf(1, 2, 1)
        )
    }

    /** Verify type of original token is "word", others are Synonym. */
    @Test
    fun testTypes() {
        val testFile = "woods, wood, forest"

        var analyzer: Analyzer = MockAnalyzer(random())
        val parser = SolrSynonymParser(true, true, analyzer)
        parser.parse(StringReader(testFile))
        val map = parser.build()
        analyzer.close()

        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
                    return TokenStreamComponents(tokenizer, SynonymFilter(tokenizer, map, true))
                }
            }

        assertAnalyzesTo(
            analyzer,
            "lost in the forest",
            arrayOf("lost", "in", "the", "forest", "woods", "wood"),
            arrayOf("word", "word", "word", "word", "SYNONYM", "SYNONYM")
        )
    }

    /** Test parsing of simple examples. */
    @Test
    fun testParseSimple() {
        val testFile =
            "spider man, spiderman\n" +
                "usa,united states,u s a,united states of america\n" +
                "mystyped, mistyped => mistyped\n" +
                "foo => foo bar\n" +
                "foo => baz"

        val analyzer: Analyzer = MockAnalyzer(random())
        val parser = SolrSynonymParser(true, true, analyzer)
        parser.parse(StringReader(testFile))
        val map = parser.build()
        analyzer.close()

        assertEntryEquals(map, "spiderman", true, "spider man")
        assertEntryEquals(map, "spider man", true, "spiderman")

        assertEntryEquals(
            map,
            "usa",
            true,
            arrayOf("united states", "u s a", "united states of america")
        )
        assertEntryEquals(
            map,
            "united states",
            true,
            arrayOf("usa", "u s a", "united states of america")
        )
        assertEntryEquals(
            map,
            "u s a",
            true,
            arrayOf("usa", "united states", "united states of america")
        )
        assertEntryEquals(
            map,
            "united states of america",
            true,
            arrayOf("usa", "u s a", "united states")
        )

        assertEntryEquals(map, "mistyped", false, "mistyped")
        assertEntryEquals(map, "mystyped", false, "mistyped")

        assertEntryEquals(map, "foo", false, arrayOf("foo bar", "baz"))
        assertEntryAbsent(map, "baz")
        assertEntryAbsent(map, "bar")
    }
}
