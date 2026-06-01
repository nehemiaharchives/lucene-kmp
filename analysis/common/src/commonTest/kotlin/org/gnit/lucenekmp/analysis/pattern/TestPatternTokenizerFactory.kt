package org.gnit.lucenekmp.analysis.pattern

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple Tests to ensure this factory is working */
class TestPatternTokenizerFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testFactory() {
        val reader = StringReader("Günther Günther is here")
        // create PatternTokenizer
        val stream: Tokenizer = tokenizerFactory("Pattern", "pattern", "[,;/\\s]+").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("Günther", "Günther", "is", "here"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenizerFactory("Pattern", "pattern", "something", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
