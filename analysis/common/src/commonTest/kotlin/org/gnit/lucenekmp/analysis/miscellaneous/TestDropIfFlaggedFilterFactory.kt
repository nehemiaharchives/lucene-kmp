package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test

/** This test just ensures the factory works, detailed tests in [TestDropIfFlaggedFilter] */
class TestDropIfFlaggedFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    companion object {
        private val TOKENS = arrayOf(token("foo", 1, 0, 2), token("bar", 3, 4, 6))

        private fun token(term: String, flags: Int, soff: Int, eoff: Int): Token {
            val token = Token()
            token.setEmpty()
            token.append(term)
            token.flags = flags
            token.setOffset(soff, eoff)
            return token
        }
    }

    @Test
    fun testFactory() {
        var stream: TokenStream = CannedTokenStream(*TOKENS)
        val tokenFilterFactory: TokenFilterFactory = tokenFilterFactory("dropIfFlagged", "dropFlags", "2")
        stream = tokenFilterFactory.create(stream)
        assertTokenStreamContents(stream, arrayOf("foo"), null, null, arrayOf("word"), intArrayOf(1))
    }
}
