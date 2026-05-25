package org.gnit.lucenekmp.analysis.he

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorphDataTester {
    @Test
    fun lemmaTestEquals() {
        var lemma1 = MorphData.Lemma("asd", DescFlag.D_EMPTY, PrefixType.PS_ALL)
        var lemma2 = MorphData.Lemma("asd", DescFlag.D_EMPTY, PrefixType.PS_ALL)
        assertTrue(lemma1 == lemma2)
        lemma1 = MorphData.Lemma("asd", DescFlag.D_ACRONYM, PrefixType.PS_ALL)
        assertFalse(lemma1 == lemma2)
        lemma2 = MorphData.Lemma("asd", DescFlag.D_ACRONYM, PrefixType.PS_ALL)
        assertTrue(lemma1 == lemma2)
        lemma1 = MorphData.Lemma("a", DescFlag.D_ACRONYM, PrefixType.PS_ALL)
        assertFalse(lemma1 == lemma2)
        lemma2 = MorphData.Lemma("a", DescFlag.D_ACRONYM, PrefixType.PS_ALL)
        assertTrue(lemma1 == lemma2)
        lemma1 = MorphData.Lemma(null, DescFlag.D_ACRONYM, PrefixType.PS_ALL)
        assertFalse(lemma1 == lemma2)
        lemma2 = MorphData.Lemma(null, DescFlag.D_ACRONYM, PrefixType.PS_ALL)
        assertTrue(lemma1 == lemma2)
    }
}
