package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocaleServiceProviderTest {

    private class DummyLocaleServiceProvider(
        private val locales: Array<Locale>
    ) : LocaleServiceProvider() {
        override fun getAvailableLocales(): Array<Locale> = locales
    }

    @Test
    fun testIsSupportedLocaleMatchesAvailableLocales() {
        val provider = DummyLocaleServiceProvider(arrayOf(Locale.ROOT, Locale.US))

        assertTrue(provider.isSupportedLocale(Locale.ROOT))
        assertTrue(provider.isSupportedLocale(Locale.US))
        assertFalse(provider.isSupportedLocale(Locale("fr", "FR")))
    }
}
