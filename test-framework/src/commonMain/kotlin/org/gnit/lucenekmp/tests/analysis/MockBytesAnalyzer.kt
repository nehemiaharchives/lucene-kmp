package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeImpl
import kotlin.reflect.KClass

/** Analyzer for testing that encodes terms as UTF-16 bytes. */
class MockBytesAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val t: Tokenizer = MockTokenizer(
            MockUTF16TermAttributeImpl.UTF16_TERM_ATTRIBUTE_FACTORY,
            MockTokenizer.KEYWORD,
            false,
            MockTokenizer.DEFAULT_MAX_TOKEN_LENGTH
        )
        return TokenStreamComponents(t)
    }

    override fun attributeFactory(fieldName: String?): AttributeFactory {
        return object : AttributeFactory() {
            override fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl {
                return if (attClass == CharTermAttribute::class || attClass == TermToBytesRefAttribute::class) {
                    MockUTF16TermAttributeImpl()
                } else {
                    AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY.createAttributeInstance(attClass)
                }
            }
        }
    }
}
