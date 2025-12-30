package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

class TestUnknownDictionary : LuceneTestCase() {
    @Test
    fun testPutCharacterCategory() {
        val unkDic = UnknownDictionaryWriter(10 * 1024 * 1024)

        expectThrows(Exception::class) { unkDic.putCharacterCategory(0, "DUMMY_NAME") }
        expectThrows(Exception::class) { unkDic.putCharacterCategory(-1, "HANGUL") }

        unkDic.putCharacterCategory(0, "DEFAULT")
        unkDic.putCharacterCategory(1, "GREEK")
        unkDic.putCharacterCategory(2, "HANJA")
        unkDic.putCharacterCategory(3, "HANGUL")
        unkDic.putCharacterCategory(4, "KANJI")
    }

    @Test
    fun testPut() {
        val unkDic = UnknownDictionaryWriter(10 * 1024 * 1024)
        expectThrows(NumberFormatException::class) {
            unkDic.put(CSVUtil.parse("HANGUL,1800,3562,UNKNOWN,*,*,*,*,*,*,*"))
        }

        val entry1 = "ALPHA,1793,3533,795,SL,*,*,*,*,*,*,*"
        val entry2 = "HANGUL,1800,3562,10247,UNKNOWN,*,*,*,*,*,*,*"
        val entry3 = "HANJA,1792,3554,-821,SH,*,*,*,*,*,*,*"

        unkDic.putCharacterCategory(0, "ALPHA")
        unkDic.putCharacterCategory(1, "HANGUL")
        unkDic.putCharacterCategory(2, "HANJA")

        unkDic.put(CSVUtil.parse(entry1))
        unkDic.put(CSVUtil.parse(entry2))
        unkDic.put(CSVUtil.parse(entry3))
    }
}
