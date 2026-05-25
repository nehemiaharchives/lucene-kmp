package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import kotlin.test.Test
import kotlin.test.fail

class TestSearchAndIndexingCompatibility {
    @Test
    fun testBasic() {
        indexAndSearch("אמא", "אמא")
        indexAndSearch("אימא", "אמא")
        indexAndSearch("אמא", "אימא")
        indexAndSearch("אינציקלופדיה", "אינציקלופדיה")
        indexAndSearch("אנציקלופדיה", "אנציקלופדיה")
        indexAndSearch("אנצקלופדיה", "אנציקלופדיה")
        indexAndSearch("אנציקלופדיה", "אנצקלופדיה")
    }

    private fun indexAndSearch(indexingTerm: String, searchTerm: String) {
        val indexingAnalyzer: Analyzer = HebrewIndexingAnalyzer(HebrewTestUtil.dictionary)
        val searchAnalyzer: Analyzer = HebrewQueryAnalyzer(HebrewTestUtil.dictionary)

        val indexedTerms = HebrewTestUtil.tokens(indexingAnalyzer, indexingTerm).toHashSet()
        val searchTerms = HebrewTestUtil.tokens(searchAnalyzer, searchTerm).toHashSet()

        for (term in searchTerms) {
            if (indexedTerms.contains(term)) return
        }

        fail(
            "Search term $searchTerm couldn't be found in indexed terms produced by $indexingTerm" +
                "\n\tIndexed terms:\t$indexedTerms" +
                "\n\tSearch terms:\t$searchTerms",
        )
    }
}
