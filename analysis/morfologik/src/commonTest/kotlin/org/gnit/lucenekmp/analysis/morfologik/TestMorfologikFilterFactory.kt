package org.gnit.lucenekmp.analysis.morfologik

import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.util.ResourceLoader
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Test for [MorfologikFilterFactory]. */
class TestMorfologikFilterFactory : BaseTokenStreamTestCase() {
    private fun whitespaceTokenizer(reader: Reader): MockTokenizer {
        val tokenizer = MockTokenizer(MorfologikAttributeFactory(), MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(reader)
        return tokenizer
    }

    private class ForbidResourcesLoader : ResourceLoader {
        override fun openResource(resource: String): InputStream {
            throw UnsupportedOperationException()
        }

        override fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*> {
            throw UnsupportedOperationException()
        }
    }

    private class FileResourceLoader(private val resources: Map<String, String>) : ResourceLoader {
        private fun resolvePath(pathString: String): okio.Path {
            var prefix = ""
            repeat(6) {
                val candidate = if (prefix.isEmpty()) {
                    pathString
                } else {
                    "$prefix/$pathString"
                }
                val path = candidate.toPath()
                if (FileSystem.SYSTEM.exists(path)) {
                    return path
                }
                prefix = if (prefix.isEmpty()) ".." else "$prefix/.."
            }
            throw IOException("Resource not found: $pathString")
        }

        override fun openResource(resource: String): InputStream {
            val pathString = resources[resource] ?: throw IOException("Resource not found: $resource")
            val path = resolvePath(pathString)
            val source = FileSystem.SYSTEM.source(path).buffer()
            return OkioSourceInputStream(source)
        }

        override fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*> {
            throw UnsupportedOperationException()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDefaultDictionary() {
        val reader = StringReader("rowery bilety")
        val factory = MorfologikFilterFactory(mutableMapOf())
        factory.inform(ForbidResourcesLoader())
        var stream: TokenStream = whitespaceTokenizer(reader)
        stream = factory.create(stream)
        assertTokenStreamContents(stream, arrayOf("rower", "bilet"))
    }

    @Test
    @Throws(Exception::class)
    fun testExplicitDictionary() {
        val loader = FileResourceLoader(
            mapOf(
                "custom-dictionary.dict" to "lucene/lucene/analysis/morfologik/src/test-files/org/apache/lucene/analysis/morfologik/custom-dictionary.dict",
                "custom-dictionary.info" to "lucene/lucene/analysis/morfologik/src/test-files/org/apache/lucene/analysis/morfologik/custom-dictionary.info"
            )
        )

        val reader = StringReader("inflected1 inflected2")
        val params = mutableMapOf<String, String>()
        params[MorfologikFilterFactory.DICTIONARY_ATTRIBUTE] = "custom-dictionary.dict"
        val factory = MorfologikFilterFactory(params)
        factory.inform(loader)
        var stream: TokenStream = whitespaceTokenizer(reader)
        stream = factory.create(stream)
        assertTokenStreamContents(stream, arrayOf("lemma1", "lemma2"))
    }

    @Test
    @Throws(Exception::class)
    fun testMissingDictionary() {
        val loader = FileResourceLoader(emptyMap())
        val expected = assertFailsWith<IOException> {
            val params = mutableMapOf<String, String>()
            params[MorfologikFilterFactory.DICTIONARY_ATTRIBUTE] = "missing-dictionary-resource.dict"
            val factory = MorfologikFilterFactory(params)
            factory.inform(loader)
        }
        assertTrue(expected.message?.contains("Resource not found") == true)
    }

    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            val params = mutableMapOf<String, String>()
            params["bogusArg"] = "bogusValue"
            MorfologikFilterFactory(params)
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
