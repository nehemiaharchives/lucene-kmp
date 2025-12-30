package org.gnit.lucenekmp.analysis.ko.dict

import okio.IOException
import org.gnit.lucenekmp.analysis.ko.POS
import org.gnit.lucenekmp.analysis.ko.TestKoreanTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestUserDictionary : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testLookup() {
        val dictionary = TestKoreanTokenizer.readDict()
        var s = "세종"
        var sArray = s.toCharArray()
        var wordIds = dictionary.lookup(sArray, 0, s.length)
        assertEquals(1, wordIds.size)
        assertNull(dictionary.getMorphAttributes().getMorphemes(wordIds[0], sArray, 0, s.length))

        s = "세종시"
        sArray = s.toCharArray()
        wordIds = dictionary.lookup(sArray, 0, s.length)
        assertEquals(2, wordIds.size)
        assertNull(dictionary.getMorphAttributes().getMorphemes(wordIds[0], sArray, 0, s.length))

        val decompound = dictionary.getMorphAttributes().getMorphemes(wordIds[1], sArray, 0, s.length)
        assertNotNull(decompound)
        assertEquals(2, decompound.size)
        assertEquals(POS.Tag.NNG, decompound[0].posTag)
        assertEquals("세종", decompound[0].surfaceForm)
        assertEquals(POS.Tag.NNG, decompound[1].posTag)
        assertEquals("시", decompound[1].surfaceForm)

        s = "c++"
        sArray = s.toCharArray()
        wordIds = dictionary.lookup(sArray, 0, s.length)
        assertEquals(1, wordIds.size)
        assertNull(dictionary.getMorphAttributes().getMorphemes(wordIds[0], sArray, 0, s.length))
    }

    @Test
    fun testRead() {
        val dictionary = TestKoreanTokenizer.readDict()
        assertNotNull(dictionary)
    }
}
