package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndicConjunctBreakTest {

    @Test
    fun testIsLinker() {
        assertTrue(IndicConjunctBreak.isLinker(0x094D)) // DEVANAGARI SIGN VIRAMA
        assertTrue(IndicConjunctBreak.isLinker(0x0D4D)) // MALAYALAM SIGN VIRAMA
        assertFalse(IndicConjunctBreak.isLinker(0x0915)) // DEVANAGARI LETTER KA
    }

    @Test
    fun testIsExtend() {
        assertTrue(IndicConjunctBreak.isExtend(0x0300)) // COMBINING GRAVE ACCENT
        assertTrue(IndicConjunctBreak.isExtend(0x200D)) // ZERO WIDTH JOINER
        assertFalse(IndicConjunctBreak.isExtend(0x0915)) // DEVANAGARI LETTER KA
    }

    @Test
    fun testIsConsonant() {
        assertTrue(IndicConjunctBreak.isConsonant(0x0915)) // DEVANAGARI LETTER KA
        assertTrue(IndicConjunctBreak.isConsonant(0x0D15)) // MALAYALAM LETTER KA
        assertFalse(IndicConjunctBreak.isConsonant(0x0904)) // DEVANAGARI LETTER SHORT A
        assertFalse(IndicConjunctBreak.isConsonant(0x0061)) // LATIN SMALL LETTER A
    }
}
