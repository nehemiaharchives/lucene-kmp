package org.gnit.lucenekmp.analysis.synonym

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

class TestWordnetSynonymParser : BaseTokenStreamTestCase() {
    private val synonymsFile =
        "s(100000001,1,'woods',n,1,0).\n" +
            "s(100000001,2,'wood',n,1,0).\n" +
            "s(100000001,3,'forest',n,1,0).\n" +
            "s(100000002,1,'wolfish',n,1,0).\n" +
            "s(100000002,2,'ravenous',n,1,0).\n" +
            "s(100000003,1,'king',n,1,1).\n" +
            "s(100000003,2,'baron',n,1,1).\n" +
            "s(100000004,1,'king''s evil',n,1,1).\n" +
            "s(100000004,2,'king''s meany',n,1,1).\n"

    @Suppress("DEPRECATION")
    @Test
    fun testSynonyms() {
        var analyzer: Analyzer = MockAnalyzer(random())
        val parser = WordnetSynonymParser(true, true, analyzer)
        parser.parse(StringReader(synonymsFile))
        val map = parser.build()
        analyzer.close()

        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, SynonymFilter(tokenizer, map, false))
                }
            }

        /* all expansions */
        assertAnalyzesTo(
            analyzer,
            "Lost in the woods",
            arrayOf("Lost", "in", "the", "woods", "wood", "forest"),
            intArrayOf(0, 5, 8, 12, 12, 12),
            intArrayOf(4, 7, 11, 17, 17, 17),
            intArrayOf(1, 1, 1, 1, 0, 0)
        )

        /* single quote */
        assertAnalyzesTo(analyzer, "king", arrayOf("king", "baron"))

        /* multi words */
        assertAnalyzesTo(analyzer, "king's evil", arrayOf("king's", "king's", "evil", "meany"))

        /* all expansions, test types */
        assertAnalyzesTo(
            analyzer,
            "Lost in the forest",
            arrayOf("Lost", "in", "the", "forest", "woods", "wood"),
            arrayOf("word", "word", "word", "word", "SYNONYM", "SYNONYM")
        )
        analyzer.close()
    }
}
