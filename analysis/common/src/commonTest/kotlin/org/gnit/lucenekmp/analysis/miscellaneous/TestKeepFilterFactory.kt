package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestKeepFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    private class StringResourceLoader(private val resources: Map<String, String>) : ResourceLoader {
        override fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*> {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun openResource(resource: String): InputStream {
            val text = resources[resource] ?: throw IOException("resource not found: $resource")
            return ByteArrayInputStream(text.encodeToByteArray())
        }
    }

    @Test
    fun testInform() {
        val loader: ResourceLoader =
            StringResourceLoader(
                mapOf(
                    "keep-1.txt" to "foo\nbar\n",
                    "keep-2.txt" to "baz\nqux\n",
                    "keep-snowball.txt" to " | comment\nhe\nhim\nhis\nhimself\nshe\nher\nhers\nherself\n"
                )
            )
        assertTrue(loader != null, "loader is null and it shouldn't be")
        var factory =
            tokenFilterFactory("KeepWord", Version.LATEST, loader, "words", "keep-1.txt", "ignoreCase", "true") as KeepWordFilterFactory
        var words: CharArraySet? = factory.getWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.size == 2, "words Size: ${words.size} is not: 2")

        factory =
            tokenFilterFactory(
                "KeepWord",
                Version.LATEST,
                loader,
                "words",
                "keep-1.txt, keep-2.txt",
                "ignoreCase",
                "true"
            ) as KeepWordFilterFactory
        words = factory.getWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.size == 4, "words Size: ${words.size} is not: 4")

        factory =
            tokenFilterFactory(
                "KeepWord",
                Version.LATEST,
                loader,
                "words",
                "keep-snowball.txt",
                "format",
                "snowball",
                "ignoreCase",
                "true"
            ) as KeepWordFilterFactory
        words = factory.getWords()
        assertEquals(8, words!!.size)
        assertTrue(words.contains("he"))
        assertTrue(words.contains("him"))
        assertTrue(words.contains("his"))
        assertTrue(words.contains("himself"))
        assertTrue(words.contains("she"))
        assertTrue(words.contains("her"))
        assertTrue(words.contains("hers"))
        assertTrue(words.contains("herself"))

        factory = tokenFilterFactory("KeepWord") as KeepWordFilterFactory
        assertTrue(factory.getWords() == null)
        assertEquals(false, factory.isIgnoreCase())
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("KeepWord", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
