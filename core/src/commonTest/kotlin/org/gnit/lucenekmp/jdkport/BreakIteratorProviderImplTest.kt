package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BreakIteratorProviderImplTest {

    @Test
    fun testGetAvailableLocalesFromLanguageTags() {
        val provider = BreakIteratorProviderImpl(
            LocaleProviderAdapter.Type.JRE,
            setOf("en", "th-TH-TH")
        )

        val locales = provider.getAvailableLocales()
        assertEquals(2, locales.size)

        val enLocale = locales.firstOrNull { it === Locale.US }
        assertNotNull(enLocale)

        val thLocale = locales.firstOrNull {
            it.language == "th" && it.country == "TH" && it.variant == "TH"
        }
        assertNotNull(thLocale)
    }

    @Test
    fun testGetCharacterInstanceReturnsGraphemeBreakIterator() {
        val provider = BreakIteratorProviderImpl(
            LocaleProviderAdapter.Type.JRE,
            setOf("en")
        )

        val instance = provider.getCharacterInstance(Locale.US)
        assertTrue(instance is BreakIteratorProviderImpl.GraphemeBreakIterator)
    }

    @Test
    fun testGetWordLineSentenceInstancesForNonThaiLocale() {
        val provider = BreakIteratorProviderImpl(
            LocaleProviderAdapter.Type.JRE,
            setOf("en")
        )

        assertTrue(provider.getWordInstance(Locale.US) is RuleBasedBreakIterator)
        assertTrue(provider.getLineInstance(Locale.US) is RuleBasedBreakIterator)
        assertTrue(provider.getSentenceInstance(Locale.US) is RuleBasedBreakIterator)
    }

    @Test
    fun testGetWordLineSentenceInstancesForThaiLocale() {
        val provider = BreakIteratorProviderImpl(
            LocaleProviderAdapter.Type.JRE,
            setOf("en")
        )
        val thai = Locale("th", "TH", "TH")

        assertTrue(provider.getWordInstance(thai) is DictionaryBasedBreakIterator)
        assertTrue(provider.getLineInstance(thai) is DictionaryBasedBreakIterator)
        assertTrue(provider.getSentenceInstance(thai) is RuleBasedBreakIterator)
    }
}
