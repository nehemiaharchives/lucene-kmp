package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.Analyzer

/** adds synonym of "dog" for "dogs", and synonym of "cavy" for "guinea pig". */
class MockSynonymAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer = MockTokenizer()
        return TokenStreamComponents(tokenizer, MockSynonymFilter(tokenizer))
    }
}
