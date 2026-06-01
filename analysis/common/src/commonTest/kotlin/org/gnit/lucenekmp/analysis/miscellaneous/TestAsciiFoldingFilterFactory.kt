package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test

class TestAsciiFoldingFilterFactory : BaseTokenStreamFactoryTestCase() {
    @Test
    @Throws(IOException::class)
    fun testMultiTermAnalysis() {
        var factory: TokenFilterFactory = ASCIIFoldingFilterFactory(mutableMapOf())
        var stream: TokenStream = CannedTokenStream(Token("Été", 0, 3))
        stream = factory.create(stream)
        assertTokenStreamContents(stream, arrayOf("Ete"))

        stream = CannedTokenStream(Token("Été", 0, 3))
        stream = factory.normalize(stream)
        assertTokenStreamContents(stream, arrayOf("Ete"))

        factory = ASCIIFoldingFilterFactory(mutableMapOf("preserveOriginal" to "true"))
        stream = CannedTokenStream(Token("Été", 0, 3))
        stream = factory.create(stream)
        assertTokenStreamContents(stream, arrayOf("Ete", "Été"))

        stream = CannedTokenStream(Token("Été", 0, 3))
        stream = factory.normalize(stream)
        assertTokenStreamContents(stream, arrayOf("Ete"))
    }
}
