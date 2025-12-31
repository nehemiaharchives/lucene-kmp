package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import org.gnit.lucenekmp.util.ClasspathResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests for [JapanesePartOfSpeechStopFilterFactory]  */
class TestJapanesePartOfSpeechStopFilterFactory : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val tags = "#  verb-main:\n" + "動詞-自立\n"

        val tokenizerFactory = JapaneseTokenizerFactory(HashMap())
        tokenizerFactory.inform(StringMockResourceLoader(""))
        var ts: TokenStream = tokenizerFactory.create()
        (ts as Tokenizer).setReader(StringReader("私は制限スピードを超える。"))
        val args: MutableMap<String, String> = HashMap<String, String>()
        args["luceneMatchVersion"] = Version.LATEST.toString()
        args["tags"] = "stoptags.txt"
        val factory = JapanesePartOfSpeechStopFilterFactory(args)
        factory.inform(StringMockResourceLoader(tags))
        ts = factory.create(ts)
        assertTokenStreamContents(ts, arrayOf("私", "は", "制限", "スピード", "を"))
    }

    @Test
    /** If we don't specify "tags", then load the default stop tags.  */
    @Throws(IOException::class)
    fun testNoTagsSpecified() {
        val tokenizerFactory = JapaneseTokenizerFactory(HashMap())
        tokenizerFactory.inform(StringMockResourceLoader(""))
        var ts: TokenStream = tokenizerFactory.create()
        (ts as Tokenizer).setReader(StringReader("私は制限スピードを超える。"))
        val args: MutableMap<String, String> = HashMap()
        args["luceneMatchVersion"] = Version.LATEST.toString()
        val factory = JapanesePartOfSpeechStopFilterFactory(args)
        factory.inform(ClasspathResourceLoader(JapaneseAnalyzer::class))
        ts = factory.create(ts)
        assertTokenStreamContents(ts, arrayOf("私", "制限", "スピード", "超える"))
    }

    @Test
    /** Test that bogus arguments result in exception  */
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected: IllegalArgumentException =
            expectThrows(
                IllegalArgumentException::class
            ) {
                JapanesePartOfSpeechStopFilterFactory(mutableMapOf("luceneMatchVersion" to Version.LATEST.toString()))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
