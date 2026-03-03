package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.standard.StandardTokenizerFactory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestAnalysisSPILoader : LuceneTestCase() {

    init {
        registerTestFactories()
    }

    private fun versionArgOnly(): MutableMap<String, String> {
        return mutableMapOf("luceneMatchVersion" to Version.LATEST.toString())
    }

    private fun registerTestFactories() {
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            StandardTokenizerFactory.NAME,
            StandardTokenizerFactory::class,
            ::StandardTokenizerFactory
        )
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            FakeTokenFilterFactory.NAME,
            FakeTokenFilterFactory::class,
            ::FakeTokenFilterFactory
        )
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            FakeCharFilterFactory.NAME,
            FakeCharFilterFactory::class,
            ::FakeCharFilterFactory
        )
    }

    @Test
    fun testLookupTokenizer() {
        assertEquals(
            StandardTokenizerFactory::class,
            TokenizerFactory.forName("Standard", versionArgOnly())::class
        )
        assertEquals(
            StandardTokenizerFactory::class,
            TokenizerFactory.forName("STANDARD", versionArgOnly())::class
        )
        assertEquals(
            StandardTokenizerFactory::class,
            TokenizerFactory.forName("standard", versionArgOnly())::class
        )
    }

    @Test
    fun testBogusLookupTokenizer() {
        expectThrows(IllegalArgumentException::class) {
            TokenizerFactory.forName("sdfsdfsdfdsfsdfsdf", mutableMapOf())
        }

        expectThrows(IllegalArgumentException::class) {
            TokenizerFactory.forName("!(**#${'$'}U*#${'$'}*", mutableMapOf())
        }
    }

    @Test
    fun testLookupTokenizerClass() {
        assertEquals(StandardTokenizerFactory::class, TokenizerFactory.lookupClass("Standard"))
        assertEquals(StandardTokenizerFactory::class, TokenizerFactory.lookupClass("STANDARD"))
        assertEquals(StandardTokenizerFactory::class, TokenizerFactory.lookupClass("standard"))
    }

    @Test
    fun testBogusLookupTokenizerClass() {
        expectThrows(IllegalArgumentException::class) {
            TokenizerFactory.lookupClass("sdfsdfsdfdsfsdfsdf")
        }

        expectThrows(IllegalArgumentException::class) {
            TokenizerFactory.lookupClass("!(**#${'$'}U*#${'$'}*")
        }
    }

    @Test
    fun testAvailableTokenizers() {
        assertTrue(TokenizerFactory.availableTokenizers().contains("standard"))
    }

    @Test
    fun testLookupTokenFilter() {
        assertEquals(
            FakeTokenFilterFactory::class,
            TokenFilterFactory.forName("Fake", versionArgOnly())::class
        )
        assertEquals(
            FakeTokenFilterFactory::class,
            TokenFilterFactory.forName("FAKE", versionArgOnly())::class
        )
        assertEquals(
            FakeTokenFilterFactory::class,
            TokenFilterFactory.forName("fake", versionArgOnly())::class
        )
    }

    @Test
    fun testBogusLookupTokenFilter() {
        expectThrows(IllegalArgumentException::class) {
            TokenFilterFactory.forName("sdfsdfsdfdsfsdfsdf", mutableMapOf())
        }

        expectThrows(IllegalArgumentException::class) {
            TokenFilterFactory.forName("!(**#${'$'}U*#${'$'}*", mutableMapOf())
        }
    }

    @Test
    fun testLookupTokenFilterClass() {
        assertEquals(FakeTokenFilterFactory::class, TokenFilterFactory.lookupClass("Fake"))
        assertEquals(FakeTokenFilterFactory::class, TokenFilterFactory.lookupClass("FAKE"))
        assertEquals(FakeTokenFilterFactory::class, TokenFilterFactory.lookupClass("fake"))
    }

    @Test
    fun testBogusLookupTokenFilterClass() {
        expectThrows(IllegalArgumentException::class) {
            TokenFilterFactory.lookupClass("sdfsdfsdfdsfsdfsdf")
        }

        expectThrows(IllegalArgumentException::class) {
            TokenFilterFactory.lookupClass("!(**#${'$'}U*#${'$'}*")
        }
    }

    @Test
    fun testAvailableTokenFilters() {
        assertTrue(TokenFilterFactory.availableTokenFilters().contains("fake"))
    }

    @Test
    fun testLookupCharFilter() {
        assertEquals(
            FakeCharFilterFactory::class,
            CharFilterFactory.forName("Fake", versionArgOnly())::class
        )
        assertEquals(
            FakeCharFilterFactory::class,
            CharFilterFactory.forName("FAKE", versionArgOnly())::class
        )
        assertEquals(
            FakeCharFilterFactory::class,
            CharFilterFactory.forName("fake", versionArgOnly())::class
        )
    }

    @Test
    fun testBogusLookupCharFilter() {
        expectThrows(IllegalArgumentException::class) {
            CharFilterFactory.forName("sdfsdfsdfdsfsdfsdf", mutableMapOf())
        }

        expectThrows(IllegalArgumentException::class) {
            CharFilterFactory.forName("!(**#${'$'}U*#${'$'}*", mutableMapOf())
        }
    }

    @Test
    fun testLookupCharFilterClass() {
        assertEquals(FakeCharFilterFactory::class, CharFilterFactory.lookupClass("Fake"))
        assertEquals(FakeCharFilterFactory::class, CharFilterFactory.lookupClass("FAKE"))
        assertEquals(FakeCharFilterFactory::class, CharFilterFactory.lookupClass("fake"))
    }

    @Test
    fun testBogusLookupCharFilterClass() {
        expectThrows(IllegalArgumentException::class) {
            CharFilterFactory.lookupClass("sdfsdfsdfdsfsdfsdf")
        }

        expectThrows(IllegalArgumentException::class) {
            CharFilterFactory.lookupClass("!(**#${'$'}U*#${'$'}*")
        }
    }

    @Test
    fun testAvailableCharFilters() {
        assertTrue(CharFilterFactory.availableCharFilters().contains("fake"))
    }
}
