package org.gnit.lucenekmp.queryparser.classic

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMultiPhraseQueryParsing : LuceneTestCase() {
    private data class TokenAndPos(val token: String, val pos: Int)

    private class CannedAnalyzer(private val tokens: Array<TokenAndPos>) : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            return TokenStreamComponents(CannedTokenizer(tokens))
        }
    }

    private class CannedTokenizer(private val tokens: Array<TokenAndPos>) : Tokenizer() {
        private var upto = 0
        private var lastPos = 0
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val posIncrAtt: PositionIncrementAttribute =
            addAttribute(PositionIncrementAttribute::class)

        override fun incrementToken(): Boolean {
            clearAttributes()
            if (upto < tokens.size) {
                val token = tokens[upto++]
                termAtt.setEmpty()
                termAtt.append(token.token)
                posIncrAtt.setPositionIncrement(token.pos - lastPos)
                lastPos = token.pos
                return true
            } else {
                return false
            }
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            this.upto = 0
            this.lastPos = 0
        }
    }

    @Test
    fun testMultiPhraseQueryParsing() {
        val INCR_0_QUERY_TOKENS_AND = arrayOf(
            TokenAndPos("a", 0),
            TokenAndPos("1", 0),
            TokenAndPos("b", 1),
            TokenAndPos("1", 1),
            TokenAndPos("c", 2),
        )

        val qp = QueryParser("field", CannedAnalyzer(INCR_0_QUERY_TOKENS_AND))
        val q: Query = requireNotNull(qp.parse("\"this text is acually ignored\""))
        assertTrue(q is MultiPhraseQuery, "wrong query type!")

        val multiPhraseQueryBuilder = MultiPhraseQuery.Builder()
        multiPhraseQueryBuilder.add(arrayOf(Term("field", "a"), Term("field", "1")), -1)
        multiPhraseQueryBuilder.add(arrayOf(Term("field", "b"), Term("field", "1")), 0)
        multiPhraseQueryBuilder.add(arrayOf(Term("field", "c")), 1)

        assertEquals(multiPhraseQueryBuilder.build(), q)
    }
}
