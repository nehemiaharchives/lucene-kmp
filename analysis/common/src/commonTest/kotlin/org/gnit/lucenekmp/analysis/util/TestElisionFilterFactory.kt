package org.gnit.lucenekmp.analysis.util

import okio.Buffer
import okio.IOException
import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.reflect.KClass

/** Simple tests to ensure the French elision filter factory is working. */
class TestElisionFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }

        val frenchArticlesTxt = """
            l
            m
            t
            qu
            n
            s
            j
        """.trimIndent()
    }

    private class StringResourceLoader(private val resources: Map<String, String>) : ResourceLoader {
        override fun openResource(resource: String): org.gnit.lucenekmp.jdkport.InputStream {
            val data = resources[resource] ?: throw IOException("Resource not found: $resource")
            val buffer = Buffer().writeUtf8(data)
            return OkioSourceInputStream(buffer)
        }

        override fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*> {
            throw RuntimeException("Cannot load class: $cname")
        }
    }

    /** Ensure the filter actually normalizes text. */
    @Test
    @Throws(Exception::class)
    fun testElision() {
        val reader: Reader = StringReader("l'avion")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        val loader = StringResourceLoader(mapOf("frenchArticles.txt" to frenchArticlesTxt))
        stream = tokenFilterFactory(
            "Elision",
            Version.LATEST,
            loader,
            "articles",
            "frenchArticles.txt"
        ).create(stream)
        assertTokenStreamContents(stream, arrayOf("avion"))
    }

    /** Test creating an elision filter without specifying any articles */
    @Test
    @Throws(Exception::class)
    fun testDefaultArticles() {
        val reader: Reader = StringReader("l'avion")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("Elision").create(stream)
        assertTokenStreamContents(stream, arrayOf("avion"))
    }

    /** Test setting ignoreCase=true */
    @Test
    @Throws(Exception::class)
    fun testCaseInsensitive() {
        val reader: Reader = StringReader("L'avion")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        val loader = StringResourceLoader(mapOf("frenchArticles.txt" to frenchArticlesTxt))
        stream = tokenFilterFactory(
            "Elision",
            Version.LATEST,
            loader,
            "articles",
            "frenchArticles.txt",
            "ignoreCase",
            "true"
        ).create(stream)
        assertTokenStreamContents(stream, arrayOf("avion"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("Elision", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}
