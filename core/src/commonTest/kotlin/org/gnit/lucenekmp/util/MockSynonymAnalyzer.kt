package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.MockSynonymFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer

/** Simple analyzer used by TestQueryBuilder that injects MockSynonymFilter. */
class MockSynonymAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer = MockTokenizer()
        return TokenStreamComponents(tokenizer, MockSynonymFilter(tokenizer))
    }
}
