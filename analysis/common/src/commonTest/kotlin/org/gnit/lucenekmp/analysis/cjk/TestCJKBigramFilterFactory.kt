package org.gnit.lucenekmp.analysis.cjk

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Simple tests to ensure the CJK bigram factory is working. */
class TestCJKBigramFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDefaults() {
        val reader: Reader = StringReader("多くの学生が試験に落ちた。")
        var stream: TokenStream = StandardTokenizer()
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("CJKBigram").create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf("多く", "くの", "の学", "学生", "生が", "が試", "試験", "験に", "に落", "落ち", "ちた")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testHanOnly() {
        val reader: Reader = StringReader("多くの学生が試験に落ちた。")
        var stream: TokenStream = StandardTokenizer()
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("CJKBigram", "hiragana", "false").create(stream)
        assertTokenStreamContents(stream, arrayOf("多", "く", "の", "学生", "が", "試験", "に", "落", "ち", "た"))
    }

    @Test
    @Throws(Exception::class)
    fun testHanOnlyUnigrams() {
        val reader: Reader = StringReader("多くの学生が試験に落ちた。")
        var stream: TokenStream = StandardTokenizer()
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("CJKBigram", "hiragana", "false", "outputUnigrams", "true").create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf("多", "く", "の", "学", "学生", "生", "が", "試", "試験", "験", "に", "落", "ち", "た")
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("CJKBigram", "bogusArg", "bogusValue")
        }
    }
}
