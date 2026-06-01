package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test

class TestTypeAsSynonymFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    companion object {
        private val TOKENS = arrayOf(token("Visit", "<ALPHANUM>"), token("example.com", "<URL>"))

        private fun token(term: String, type: String): Token {
            val token = Token()
            token.setEmpty()
            token.append(term)
            token.setType(type)
            return token
        }
    }

    @Test
    fun testBasic() {
        var stream: TokenStream = CannedTokenStream(*TOKENS)
        stream = tokenFilterFactory("TypeAsSynonym").create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf("Visit", "<ALPHANUM>", "example.com", "<URL>"),
            null,
            null,
            arrayOf("<ALPHANUM>", "<ALPHANUM>", "<URL>", "<URL>"),
            intArrayOf(1, 0, 1, 0)
        )
    }

    @Test
    fun testPrefix() {
        var stream: TokenStream = CannedTokenStream(*TOKENS)
        stream = tokenFilterFactory("TypeAsSynonym", "prefix", "_type_").create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf("Visit", "_type_<ALPHANUM>", "example.com", "_type_<URL>"),
            null,
            null,
            arrayOf("<ALPHANUM>", "<ALPHANUM>", "<URL>", "<URL>"),
            intArrayOf(1, 0, 1, 0)
        )
    }

    @Test
    fun testIgnore() {
        var stream: TokenStream = CannedTokenStream(*TOKENS)
        stream = tokenFilterFactory("typeAsSynonym", "prefix", "_type_", "ignore", "<ALPHANUM>").create(stream)
        assertTokenStreamContents(
            stream,
            arrayOf("Visit", "example.com", "_type_<URL>"),
            null,
            null,
            arrayOf("<ALPHANUM>", "<URL>", "<URL>"),
            intArrayOf(1, 1, 0)
        )
    }
}
