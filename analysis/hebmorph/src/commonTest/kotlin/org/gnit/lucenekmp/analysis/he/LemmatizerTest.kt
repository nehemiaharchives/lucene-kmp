package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.assertTrue

class LemmatizerTest {
    @Test
    fun testLemmatizer() {
        val text = "רפאל ולדן הוא פרופסור לרפואה ישראלי, מלמד באוניברסיטת תל אביב, סגן מנהל בית החולים שיבא ופעיל חברתי. מתמחה בכירוגיה כללית ובכלי דם." +
            "ולדן נולד בצרפת ועלה לישראל בגיל 9. הוא שימש בבית החולים שיבא כמנהל האגף לכירורגיה ומנהל היחידה לכלי דם." +
            "ולדן פעיל וחבר בהנהלה בעמותת רופאים לזכויות אדם וכמו כן חבר בהנהלת ארגון לתת. ולדן זכה באות לגיון הכבוד הצרפתי (Légion d'Honneur) של ממשלת צרפת בזכות על פעילותו במסגרת רופאים לזכויות אדם לקידום שיתוף הפעולה בין פלסטינים לישראלים. האות הוענק לו על ידי שר החוץ של צרפת, ברנאר קושנר, בטקס בשגרירות צרפת בתל אביב." +
            "נשוי לבלשנית צביה ולדן, בתו של שמעון פרס והוא משמש כרופאו האישי של פרס."
        val reader = StringReader(text)
        val m_lemmatizer = StreamLemmatizer(reader, HebrewTestUtil.dictionary)

        val word = Reference("")
        val tokens = ArrayList<Token>()
        var hebrewTokens = 0
        var nonHebrewTokens = 0
        while (m_lemmatizer.getLemmatizeNextToken(word, tokens) > 0) {
            if (tokens.size == 0) {
                continue
            }

            if ((tokens.size == 1) && tokens[0] !is HebrewToken) {
                if (!tokens[0].isNumeric()) {
                    nonHebrewTokens++
                }
                continue
            }

            for (r in tokens) {
                if (r !is HebrewToken) continue
                assertTrue(r.getText().isNotEmpty())
                hebrewTokens++
            }
        }
        assertTrue(hebrewTokens > 0)
        assertTrue(nonHebrewTokens > 0)
    }
}
