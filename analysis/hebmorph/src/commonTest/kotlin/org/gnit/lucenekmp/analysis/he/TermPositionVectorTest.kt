package org.gnit.lucenekmp.analysis.he

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TermPositionVectorTest {
    @Test
    fun storesPositionCorrectly() {
        val str = "קשת רשת דבשת מיץ יבשת יבלת גחלת גדר אינציקלופדיה חבר"
        val terms = HebrewTestUtil.tokenData(HebrewIndexingAnalyzer(HebrewTestUtil.dictionary), str)
        assertPosition(terms, "קשת", 0)
        assertPosition(terms, "אנציקלופדיה", 8)
        assertPosition(terms, "חבר", 9)
    }

    private fun assertPosition(terms: List<HebrewTestUtil.TokenData>, term: String, expectedPosition: Int) {
        var position = -1
        for (token in terms) {
            position += token.positionIncrement
            if (token.term == term) {
                assertEquals(expectedPosition, position)
                return
            }
        }
        assertTrue(false, "term $term not found in $terms")
    }
}
