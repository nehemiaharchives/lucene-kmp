package org.gnit.lucenekmp.analysis.horn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests generated Horn lexicon loading and lookup edge cases. */
class TestHornLexicons {
    @Test
    fun testAmharicGeezAndSeraLookup() {
        assertEquals("hager", HornLexicons.amharicStem("ሀገር"))
        assertEquals("hager", HornLexicons.amharicStem("hager"))
    }

    @Test
    fun testAmharicGeneratedPrefixSuffixLookup() {
        assertEquals("hager", HornLexicons.amharicStem("yehagerocn"))
        assertEquals("mT'", HornLexicons.amharicStem("na"))
    }

    @Test
    fun testOromoCaseInsensitiveLookup() {
        assertEquals("fedh", HornLexicons.oromoStem("fedhi"))
        assertEquals("ameerikaa", HornLexicons.oromoStem("Ameerikaatti"))
    }

    @Test
    fun testUnknownTermsReturnNull() {
        assertNull(HornLexicons.amharicStem("zzzzzz"))
        assertNull(HornLexicons.oromoStem("zzzzzz"))
    }
}
