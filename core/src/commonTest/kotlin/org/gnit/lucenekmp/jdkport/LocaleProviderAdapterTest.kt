package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocaleProviderAdapterTest {

    @Test
    fun testToLocaleArraySpecialCasesAndDistinct() {
        val tags = setOf("en", "th-TH-TH", "ja-JP-JP", "en")
        val locales = LocaleProviderAdapter.toLocaleArray(tags)

        assertEquals(3, locales.size)
        assertTrue(locales.any { it === Locale.US })
        assertTrue(locales.any { it.language == "th" && it.country == "TH" && it.variant == "TH" })
        assertTrue(locales.any { it.language == "ja" && it.country == "JP" && it.variant == "JP" })
    }

    @Test
    fun testGetAdapterReturnsNonNull() {
        val adapter = LocaleProviderAdapter.getAdapter(BreakIteratorProvider::class, Locale.US)
        assertNotNull(adapter)
        assertEquals(LocaleProviderAdapter.Type.JRE, adapter.adapterType)
    }

    @Test
    fun testForTypeAndForJRE() {
        val jre = LocaleProviderAdapter.forJRE()
        assertEquals(LocaleProviderAdapter.Type.JRE, jre.adapterType)

        val jreByType = LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.JRE)
        assertEquals(LocaleProviderAdapter.Type.JRE, jreByType.adapterType)
    }

    @Test
    fun testFindAdapterUnsupportedLocaleFallsBack() {
        val adapter = LocaleProviderAdapter.getAdapter(BreakIteratorProvider::class, Locale("fr", "FR"))
        assertEquals(LocaleProviderAdapter.Type.JRE, adapter.adapterType)
    }
}
