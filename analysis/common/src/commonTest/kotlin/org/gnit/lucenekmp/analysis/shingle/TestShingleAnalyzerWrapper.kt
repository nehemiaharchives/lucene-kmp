package org.gnit.lucenekmp.analysis.shingle

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A test class for ShingleAnalyzerWrapper as regards queries and scoring. */
class TestShingleAnalyzerWrapper : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer
    private lateinit var searcher: IndexSearcher
    private lateinit var reader: IndexReader
    private lateinit var directory: Directory

    /**
     * Set up a new index in RAM with three test phrases and the supplied Analyzer.
     *
     * @throws Exception if an error occurs with index writer or searcher
     */
    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        analyzer = ShingleAnalyzerWrapper(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false), 2)
        directory = newDirectory()
        val writer = IndexWriter(directory, IndexWriterConfig(analyzer))

        var doc = Document()
        doc.add(TextField("content", "please divide this sentence into shingles", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(TextField("content", "just another test sentence", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(TextField("content", "a sentence which contains no test", Field.Store.YES))
        writer.addDocument(doc)

        writer.close()

        reader = DirectoryReader.open(directory)
        searcher = newSearcher(reader)
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        directory.close()
        analyzer.close()
    }

    @Throws(Exception::class)
    private fun compareRanks(hits: Array<ScoreDoc>, ranks: IntArray) {
        assertEquals(ranks.size, hits.size)
        for (i in ranks.indices) {
            assertEquals(ranks[i], hits[i].doc)
        }
    }

    /*
     * This shows how to construct a phrase query containing shingles.
     */
    @Test
    @Throws(Exception::class)
    fun testShingleAnalyzerWrapperPhraseQuery() {
        val builder = PhraseQuery.Builder()
        analyzer.tokenStream("content", "this sentence").use { ts ->
            var j = -1

            val posIncrAtt = ts.addAttribute(PositionIncrementAttribute::class)
            val termAtt = ts.addAttribute(CharTermAttribute::class)

            ts.reset()
            while (ts.incrementToken()) {
                j += posIncrAtt.getPositionIncrement()
                val termText = termAtt.toString()
                builder.add(Term("content", termText), j)
            }
            ts.end()
        }

        val q = builder.build()
        val hits = searcher.search(q, 1000).scoreDocs
        val ranks = intArrayOf(0)
        compareRanks(hits, ranks)
    }

    /*
     * How to construct a boolean query with shingles. A query like this will
     * implicitly score those documents higher that contain the words in the query
     * in the right order and adjacent to each other.
     */
    @Test
    @Throws(Exception::class)
    fun testShingleAnalyzerWrapperBooleanQuery() {
        val q = BooleanQuery.Builder()

        analyzer.tokenStream("content", "test sentence").use { ts ->
            val termAtt = ts.addAttribute(CharTermAttribute::class)

            ts.reset()
            while (ts.incrementToken()) {
                val termText = termAtt.toString()
                q.add(TermQuery(Term("content", termText)), BooleanClause.Occur.SHOULD)
            }
            ts.end()
        }

        val hits = searcher.search(q.build(), 1000).scoreDocs
        val ranks = intArrayOf(1, 2, 0)
        compareRanks(hits, ranks)
    }

    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        val a = ShingleAnalyzerWrapper(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false), 2)
        assertAnalyzesTo(
            a,
            "please divide into shingles",
            arrayOf("please", "please divide", "divide", "divide into", "into", "into shingles", "shingles"),
            intArrayOf(0, 0, 7, 7, 14, 14, 19),
            intArrayOf(6, 13, 13, 18, 18, 27, 27),
            intArrayOf(1, 0, 1, 0, 1, 0, 1),
        )
        assertAnalyzesTo(
            a,
            "divide me up again",
            arrayOf("divide", "divide me", "me", "me up", "up", "up again", "again"),
            intArrayOf(0, 0, 7, 7, 10, 10, 13),
            intArrayOf(6, 9, 9, 12, 12, 18, 18),
            intArrayOf(1, 0, 1, 0, 1, 0, 1),
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNonDefaultMinShingleSize() {
        var analyzer =
            ShingleAnalyzerWrapper(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false), 3, 4)
        assertAnalyzesTo(
            analyzer,
            "please divide this sentence into shingles",
            arrayOf(
                "please",
                "please divide this",
                "please divide this sentence",
                "divide",
                "divide this sentence",
                "divide this sentence into",
                "this",
                "this sentence into",
                "this sentence into shingles",
                "sentence",
                "sentence into shingles",
                "into",
                "shingles"
            ),
            intArrayOf(0, 0, 0, 7, 7, 7, 14, 14, 14, 19, 19, 28, 33),
            intArrayOf(6, 18, 27, 13, 27, 32, 18, 32, 41, 27, 41, 32, 41),
            intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 1),
        )
        analyzer.close()

        analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                3,
                4,
                ShingleFilter.DEFAULT_TOKEN_SEPARATOR,
                false,
                false,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(
            analyzer,
            "please divide this sentence into shingles",
            arrayOf(
                "please divide this", "please divide this sentence",
                "divide this sentence", "divide this sentence into",
                "this sentence into", "this sentence into shingles",
                "sentence into shingles"
            ),
            intArrayOf(0, 0, 7, 7, 14, 14, 19),
            intArrayOf(18, 27, 27, 32, 32, 41, 41),
            intArrayOf(1, 0, 1, 0, 1, 0, 1),
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNonDefaultMinAndSameMaxShingleSize() {
        var analyzer =
            ShingleAnalyzerWrapper(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false), 3, 3)
        assertAnalyzesTo(
            analyzer,
            "please divide this sentence into shingles",
            arrayOf(
                "please",
                "please divide this",
                "divide",
                "divide this sentence",
                "this",
                "this sentence into",
                "sentence",
                "sentence into shingles",
                "into",
                "shingles"
            ),
            intArrayOf(0, 0, 7, 7, 14, 14, 19, 19, 28, 33),
            intArrayOf(6, 18, 13, 27, 18, 32, 27, 41, 32, 41),
            intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 1),
        )
        analyzer.close()

        analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                3,
                3,
                ShingleFilter.DEFAULT_TOKEN_SEPARATOR,
                false,
                false,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(
            analyzer,
            "please divide this sentence into shingles",
            arrayOf(
                "please divide this",
                "divide this sentence",
                "this sentence into",
                "sentence into shingles"
            ),
            intArrayOf(0, 7, 14, 19),
            intArrayOf(18, 27, 32, 41),
            intArrayOf(1, 1, 1, 1),
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNoTokenSeparator() {
        var analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                "",
                true,
                false,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(
            analyzer,
            "please divide into shingles",
            arrayOf("please", "pleasedivide", "divide", "divideinto", "into", "intoshingles", "shingles"),
            intArrayOf(0, 0, 7, 7, 14, 14, 19),
            intArrayOf(6, 13, 13, 18, 18, 27, 27),
            intArrayOf(1, 0, 1, 0, 1, 0, 1),
        )
        analyzer.close()

        analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                "",
                false,
                false,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(
            analyzer,
            "please divide into shingles",
            arrayOf("pleasedivide", "divideinto", "intoshingles"),
            intArrayOf(0, 7, 14),
            intArrayOf(13, 18, 27),
            intArrayOf(1, 1, 1),
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNullTokenSeparator() {
        var analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                null,
                true,
                false,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(
            analyzer,
            "please divide into shingles",
            arrayOf("please", "pleasedivide", "divide", "divideinto", "into", "intoshingles", "shingles"),
            intArrayOf(0, 0, 7, 7, 14, 14, 19),
            intArrayOf(6, 13, 13, 18, 18, 27, 27),
            intArrayOf(1, 0, 1, 0, 1, 0, 1),
        )
        analyzer.close()

        analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                "",
                false,
                false,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(
            analyzer,
            "please divide into shingles",
            arrayOf("pleasedivide", "divideinto", "intoshingles"),
            intArrayOf(0, 7, 14),
            intArrayOf(13, 18, 27),
            intArrayOf(1, 1, 1),
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAltTokenSeparator() {
        var analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                "<SEP>",
                true,
                false,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(
            analyzer,
            "please divide into shingles",
            arrayOf("please", "please<SEP>divide", "divide", "divide<SEP>into", "into", "into<SEP>shingles", "shingles"),
            intArrayOf(0, 0, 7, 7, 14, 14, 19),
            intArrayOf(6, 13, 13, 18, 18, 27, 27),
            intArrayOf(1, 0, 1, 0, 1, 0, 1),
        )
        analyzer.close()

        analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                "<SEP>",
                false,
                false,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(
            analyzer,
            "please divide into shingles",
            arrayOf("please<SEP>divide", "divide<SEP>into", "into<SEP>shingles"),
            intArrayOf(0, 7, 14),
            intArrayOf(13, 18, 27),
            intArrayOf(1, 1, 1),
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAltFillerToken() {
        var delegate: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val stopSet: CharArraySet = StopFilter.makeStopSet("into")
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val filter: TokenFilter = StopFilter(tokenizer, stopSet)
                    return TokenStreamComponents(tokenizer, filter)
                }
            }

        var analyzer =
            ShingleAnalyzerWrapper(
                delegate,
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_TOKEN_SEPARATOR,
                true,
                false,
                "--"
            )
        assertAnalyzesTo(
            analyzer,
            "please divide into shingles",
            arrayOf("please", "please divide", "divide", "divide --", "-- shingles", "shingles"),
            intArrayOf(0, 0, 7, 7, 19, 19),
            intArrayOf(6, 13, 13, 19, 27, 27),
            intArrayOf(1, 0, 1, 0, 1, 1),
        )
        analyzer.close()

        delegate =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val stopSet: CharArraySet = StopFilter.makeStopSet("into")
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val filter: TokenFilter = StopFilter(tokenizer, stopSet)
                    return TokenStreamComponents(tokenizer, filter)
                }
            }
        analyzer =
            ShingleAnalyzerWrapper(
                delegate,
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_TOKEN_SEPARATOR,
                false,
                false,
                ""
            )
        assertAnalyzesTo(
            analyzer,
            "please divide into shingles",
            arrayOf("please divide", "divide ", " shingles"),
            intArrayOf(0, 7, 19),
            intArrayOf(13, 19, 27),
            intArrayOf(1, 1, 1),
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testOutputUnigramsIfNoShinglesSingleToken() {
        val analyzer =
            ShingleAnalyzerWrapper(
                MockAnalyzer(random(), MockTokenizer.WHITESPACE, false),
                ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE,
                ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE,
                "",
                false,
                true,
                ShingleFilter.DEFAULT_FILLER_TOKEN
            )
        assertAnalyzesTo(analyzer, "please", arrayOf("please"), intArrayOf(0), intArrayOf(6), intArrayOf(1))
        analyzer.close()
    }
}
