package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.synonym.SynonymGraphFilter
import org.gnit.lucenekmp.analysis.synonym.SynonymMap
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.CharsRefBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestConcatenateGraphFilter : BaseTokenStreamTestCase() {
    companion object {
        private val SEP_LABEL = ConcatenateGraphFilter.SEP_LABEL.toChar()
    }

    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testBasic() {
        val tokenStream: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
        val input = "mykeyword"
        tokenStream.setReader(StringReader(input))
        val stream = ConcatenateGraphFilter(tokenStream)
        assertTokenStreamContents(stream, arrayOf(input), posIncrements = intArrayOf(1))
    }

    @Test
    fun testWithNoPreserveSep() {
        val tokenStream: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
        val input = "mykeyword another keyword"
        tokenStream.setReader(StringReader(input))
        val stream = ConcatenateGraphFilter(tokenStream, null, false, 100)
        assertTokenStreamContents(stream, arrayOf("mykeywordanotherkeyword"), posIncrements = intArrayOf(1))
    }

    @Test
    fun testWithMultipleTokens() {
        val tokenStream: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
        val input = "mykeyword another keyword"
        tokenStream.setReader(StringReader(input))
        val stream = ConcatenateGraphFilter(tokenStream)
        val builder = CharsRefBuilder()
        builder.append("mykeyword")
        builder.append(SEP_LABEL)
        builder.append("another")
        builder.append(SEP_LABEL)
        builder.append("keyword")
        assertTokenStreamContents(stream, arrayOf(builder.toCharsRef().toString()), posIncrements = intArrayOf(1))
    }

    @Test
    fun testWithStopword() {
        for (preservePosInc in booleanArrayOf(true, false)) {
            val tokenStream: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
            val input = "a mykeyword a keyword"
            tokenStream.setReader(StringReader(input))
            val tokenFilter: TokenFilter = StopFilter(tokenStream, StopFilter.makeStopSet("a"))
            val concatStream = ConcatenateGraphFilter(tokenFilter, SEP_LABEL, preservePosInc, 10)
            val builder = CharsRefBuilder()
            if (preservePosInc) {
                builder.append(SEP_LABEL)
            }
            builder.append("mykeyword")
            builder.append(SEP_LABEL)
            if (preservePosInc) {
                builder.append(SEP_LABEL)
            }
            builder.append("keyword")
            assertTokenStreamContents(concatStream, arrayOf(builder.toCharsRef().toString()))
        }
    }

    @Test
    fun testEmpty() {
        val tokenizer = whitespaceMockTokenizer("")
        val filter = ConcatenateGraphFilter(tokenizer)
        assertTokenStreamContents(filter, emptyArray())
    }

    @Test
    fun testSeparator() {
        val tokenStream: Tokenizer = MockTokenizer(MockTokenizer.SIMPLE, true)
        val input = "...mykeyword.another.keyword."
        tokenStream.setReader(StringReader(input))
        val stream = ConcatenateGraphFilter(tokenStream, ' ', false, 100)
        assertTokenStreamContents(stream, arrayOf("mykeyword another keyword"), posIncrements = intArrayOf(1))
    }

    @Test
    fun testSeparatorWithStopWords() {
        val tokenStream: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        val input = "A B C D E F J H"
        tokenStream.setReader(StringReader(input))
        val tokenFilter: TokenStream = StopFilter(tokenStream, StopFilter.makeStopSet("A", "D", "E", "J"))
        val stream = ConcatenateGraphFilter(tokenFilter, '-', false, 100)
        assertTokenStreamContents(stream, arrayOf("B-C-F-H"), posIncrements = intArrayOf(1))
    }

    @Test
    fun testSeparatorWithStopWordsAndPreservePositionIncrements() {
        val tokenStream: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        val input = "A B C D E F J H"
        tokenStream.setReader(StringReader(input))
        val tokenFilter: TokenStream = StopFilter(tokenStream, StopFilter.makeStopSet("A", "D", "E", "J"))
        val stream = ConcatenateGraphFilter(tokenFilter, '-', true, 100)
        assertTokenStreamContents(stream, arrayOf("-B-C---F--H"), posIncrements = intArrayOf(1))
    }

    @Test
    @Throws(IOException::class)
    fun testSeparatorWithSynonyms() {
        val builder = SynonymMap.Builder(true)
        builder.add(CharsRef("mykeyword"), CharsRef("mysynonym"), true)
        builder.add(CharsRef("mykeyword"), CharsRef("three words synonym"), true)
        val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
        val input = " mykeyword another keyword   "
        tokenizer.setReader(StringReader(input))
        val filter = SynonymGraphFilter(tokenizer, builder.build(), true)
        val stream = ConcatenateGraphFilter(filter, '-', false, 100)
        assertTokenStreamContents(
            stream,
            arrayOf(
                "mykeyword-another-keyword",
                "mysynonym-another-keyword",
                "three words synonym-another-keyword"
            ),
            posIncrements = intArrayOf(1, 0, 0)
        )
    }

    @Test
    fun testValidNumberOfExpansions() {
        val builder = SynonymMap.Builder(true)
        for (i in 0 until 256) {
            builder.add(CharsRef("${i + 1}"), CharsRef("${1000 + (i + 1)}"), true)
        }
        val valueBuilder = StringBuilder()
        for (i in 0 until 8) {
            valueBuilder.append(i + 1)
            valueBuilder.append(" ")
        }
        val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
        tokenizer.setReader(StringReader(valueBuilder.toString()))
        val filter = SynonymGraphFilter(tokenizer, builder.build(), true)

        var count: Int
        ConcatenateGraphFilter(filter).use { stream ->
            stream.reset()
            val attr = stream.addAttribute(ConcatenateGraphFilter.BytesRefBuilderTermAttribute::class)
            count = 0
            while (stream.incrementToken()) {
                count++
                assertNotNull(attr.bytesRef)
                assertNotNull(attr.bytesRef.bytes)
                assertEquals(true, attr.bytesRef.length > 0)
            }
        }
        assertEquals(256, count)
    }
}
