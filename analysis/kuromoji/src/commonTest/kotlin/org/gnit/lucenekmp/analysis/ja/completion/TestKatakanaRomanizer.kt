package org.gnit.lucenekmp.analysis.ja.completion

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.CharsRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestKatakanaRomanizer : LuceneTestCase() {
    private val romanizer = KatakanaRomanizer.getInstance()

    @Test
    fun testRomanize() {
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("hasi"), CharsRef("hashi")),
            romanizer.romanize(CharsRef("ハシ"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("yuukyuu")),
            romanizer.romanize(CharsRef("ユウキュウ"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("yakyuu")),
            romanizer.romanize(CharsRef("ヤキュウ"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("toukyou")),
            romanizer.romanize(CharsRef("トウキョウ"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("toーkyoー")),
            romanizer.romanize(CharsRef("トーキョー"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("sakka")),
            romanizer.romanize(CharsRef("サッカ"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("hyakkaten"), CharsRef("hyakkatenn")),
            romanizer.romanize(CharsRef("ヒャッカテン"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("voruteーru"), CharsRef("vuxoruteーru")),
            romanizer.romanize(CharsRef("ヴォルテール"))
        )
    }

    @Test
    fun testRomanizeWithAlphabets() {
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("toukyout")),
            romanizer.romanize(CharsRef("トウキョウt"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("kodakk")),
            romanizer.romanize(CharsRef("コダッk"))
        )
        assertCharsRefListEqualsUnordered(
            listOf(CharsRef("syousy"), CharsRef("shousy")),
            romanizer.romanize(CharsRef("ショウsy"))
        )
    }

    private fun assertCharsRefListEqualsUnordered(
        expected: List<CharsRef>,
        actual: List<CharsRef>
    ) {
        assertEquals(expected.size, actual.size)
        for (ref in expected) {
            assertTrue(actual.contains(ref), "$ref is not contained in $actual")
        }
    }
}
