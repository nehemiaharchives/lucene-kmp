package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.en.EnglishAnalyzer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.util.ResourceLoader
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestStopFilterFactory : BaseTokenStreamFactoryTestCase() {
    @Test
    @Throws(Exception::class)
    fun testInform() {
        val loader: ResourceLoader = StringResourceLoader(resources)
        assertNotNull(loader, "loader is null and it shouldn't be")

        var factory = StopFilterFactory(mutableMapOf("words" to "stop-1.txt", "ignoreCase" to "true"))
        factory.inform(loader)
        val words1 = requireNotNull(factory.getStopWords())
        assertTrue(words1.size == 2, "words Size: ${words1.size} is not: 2")
        assertTrue(factory.isIgnoreCase(), "${factory.isIgnoreCase()} does not equal: true")

        factory = StopFilterFactory(
            mutableMapOf(
                "words" to "stop-1.txt, stop-2.txt",
                "ignoreCase" to "true"
            )
        )
        factory.inform(loader)
        val words2 = requireNotNull(factory.getStopWords())
        assertTrue(words2.size == 4, "words Size: ${words2.size} is not: 4")
        assertTrue(factory.isIgnoreCase(), "${factory.isIgnoreCase()} does not equal: true")

        factory = StopFilterFactory(
            mutableMapOf(
                "words" to "stop-snowball.txt",
                "format" to "snowball",
                "ignoreCase" to "true"
            )
        )
        factory.inform(loader)
        val words3 = requireNotNull(factory.getStopWords())
        assertEquals(8, words3.size)
        assertTrue(words3.contains("he"))
        assertTrue(words3.contains("him"))
        assertTrue(words3.contains("his"))
        assertTrue(words3.contains("himself"))
        assertTrue(words3.contains("she"))
        assertTrue(words3.contains("her"))
        assertTrue(words3.contains("hers"))
        assertTrue(words3.contains("herself"))

        // defaults
        factory = StopFilterFactory(mutableMapOf())
        factory.inform(loader)
        assertEquals(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET, factory.getStopWords())
        assertEquals(false, factory.isIgnoreCase())
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected =
            expectThrows(IllegalArgumentException::class) {
                StopFilterFactory(mutableMapOf("bogusArg" to "bogusValue"))
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusFormats() {
        val loader: ResourceLoader = StringResourceLoader(resources)

        var expected =
            expectThrows(IllegalArgumentException::class) {
                val factory = StopFilterFactory(
                    mutableMapOf("words" to "stop-snowball.txt", "format" to "bogus")
                )
                factory.inform(loader)
            }
        var msg = expected.message!!
        assertTrue(msg.contains("Unknown"), msg)
        assertTrue(msg.contains("format"), msg)
        assertTrue(msg.contains("bogus"), msg)

        expected =
            expectThrows(IllegalArgumentException::class) {
                val factory = StopFilterFactory(
                    // implicit default words file
                    mutableMapOf("format" to "bogus")
                )
                factory.inform(loader)
            }
        msg = expected.message!!
        assertTrue(msg.contains("can not be specified"), msg)
        assertTrue(msg.contains("format"), msg)
        assertTrue(msg.contains("bogus"), msg)
    }

    private class StringResourceLoader(private val resources: Map<String, String>) : ResourceLoader {
        @Throws(IOException::class)
        override fun openResource(resource: String): InputStream {
            val text = resources[resource] ?: throw IOException("Resource not found: $resource")
            val bytes = text.encodeToByteArray()
            return object : InputStream() {
                private var index = 0

                override fun read(): Int {
                    if (index >= bytes.size) {
                        return -1
                    }
                    return bytes[index++].toInt() and 0xFF
                }
            }
        }

        override fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*> {
            throw RuntimeException("Cannot load class: $cname")
        }
    }

    companion object {
        private val resources: Map<String, String> = mapOf(
            "stop-1.txt" to "foo\nbar\n",
            "stop-2.txt" to "junk\nmore\n",
            "stop-snowball.txt" to """
                 | This is a file in snowball format, empty lines are ignored, '|' is a comment
                 | Additionally, multiple words can be on the same line, allowing stopwords to be
                 | arranged in tables (useful in some languages where they might inflect)

                 | fictitious table below

                |third person singular
                |Subject Object Possessive Reflexive
                he       him    his        himself| masculine
                she      her    hers       herself| feminine
            """.trimIndent()
        )
    }
}
