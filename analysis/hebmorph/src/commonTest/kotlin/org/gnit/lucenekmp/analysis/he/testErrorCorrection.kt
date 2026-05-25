package org.gnit.lucenekmp.analysis.he

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class testErrorCorrection {
    private val _lemmatizer = Lemmatizer(HebrewTestUtil.dictionary)

    @Test
    fun SimpleAHVICases() {
        //AssertWord("שלחן", "שולחן");
        AssertWord("אמא", "אימא")
        AssertWord("אנצקלופדיה", "אנציקלופדיה")
        AssertWord("אינציקלופדיה", "אנציקלופדיה")
        //AssertWord("פינגוין", "פינגווין");
        //AssertNotCorrected("שלומי", "שלום");
        // ביבי -> בבבי
        // ביבי -> שביב
        // ביבי -> לבייב
    }

    private fun AssertWord(word: String, expectedWord: String) {
        assertTrue(_lemmatizer.lemmatize(expectedWord).size > 0)
        val results = _lemmatizer.lemmatizeTolerant(word)
        assertTrue(results.size > 0)
        assertEquals(expectedWord, results[0].getLemma())
    }
}
