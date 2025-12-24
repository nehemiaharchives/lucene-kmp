package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JRELocaleProviderAdapterTest {

    @Test
    fun testAdapterBasics() {
        val adapter = JRELocaleProviderAdapter()
        assertEquals(LocaleProviderAdapter.Type.JRE, adapter.adapterType)
        assertEquals(1, adapter.availableLocales.size)
        assertEquals(Locale.US, adapter.availableLocales[0])
    }

    @Test
    fun testBreakIteratorProviderAndLanguageTags() {
        val adapter = JRELocaleProviderAdapter()
        val provider = adapter.breakIteratorProvider
        assertTrue(provider is BreakIteratorProviderImpl)

        val tags = adapter.getLanguageTagSet("FormatData")
        assertTrue(tags.contains("en"))
    }

    @Test
    fun testGetLocaleServiceProvider() {
        val adapter = JRELocaleProviderAdapter()
        val provider = adapter.getLocaleServiceProvider(BreakIteratorProvider::class)
        assertTrue(provider is BreakIteratorProviderImpl)

        assertFailsWith<UnsupportedOperationException> {
            adapter.getLocaleServiceProvider(LocaleServiceProvider::class)
        }
    }
}
