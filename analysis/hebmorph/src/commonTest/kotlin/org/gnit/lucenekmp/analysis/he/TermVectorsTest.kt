package org.gnit.lucenekmp.analysis.he

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class TermVectorsTest {
    @Test
    fun testTermVectors() {
        val text = "הוראה זו משקפת איזון בין זכות הנושה לגבות את חובו לבין זכותו של החייב ליהנות מקורת גג לו ולבני משפחתו בצה\"ל 2342 23דג asdשגכ דגכ234 שדגasd"

        val analyzer1 = HebrewIndexingAnalyzer(HebrewTestUtil.dictionary)
        val analyzer2 = HebrewIndexingAnalyzer(HebrewTestUtil.dictionary)

        val results1 = HebrewTestUtil.fullTokenData(analyzer1, text)
        val results2 = HebrewTestUtil.fullTokenData(analyzer2, text)

        for (i in 0 until min(results1.size, results2.size)) {
            assertEquals(results1[i].term, results2[i].term, "term mismatch in position $i")
            assertEquals(results1[i].startOffset, results2[i].startOffset, "offset mismatch in position $i")
            assertEquals(results1[i].endOffset, results2[i].endOffset, "offset mismatch in position $i")
            assertEquals(results1[i].positionIncrement, results2[i].positionIncrement, "pos inc mismatch in position $i")
            assertEquals(results1[i].positionLength, results2[i].positionLength, "pos len mismatch in position $i")
        }

        assertEquals(results1.size, results2.size)
    }
}
