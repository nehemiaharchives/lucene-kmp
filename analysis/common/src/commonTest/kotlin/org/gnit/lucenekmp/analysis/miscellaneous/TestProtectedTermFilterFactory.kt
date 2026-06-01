package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Simple tests to ensure the simple truncation filter factory is working. */
class TestProtectedTermFilterFactory : BaseTokenStreamFactoryTestCase() {
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
        val loader =
            StringResourceLoader(
                mapOf(
                    "protected-1.txt" to "Foo\nBar\n",
                    "protected-2.txt" to "Baz\nQux\n"
                )
            )
        var factory =
            tokenFilterFactory(
                "ProtectedTerm",
                Version.LATEST,
                loader,
                "protected",
                "protected-1.txt",
                "ignoreCase",
                "true",
                "wrappedFilters",
                "lowercase"
            ) as ProtectedTermFilterFactory
        var protectedTerms: CharArraySet? = factory.getProtectedTerms()
        assertTrue(protectedTerms != null, "protectedTerms is null and it shouldn't be")
        assertTrue(protectedTerms.size == 2, "protectedTerms Size: ${protectedTerms.size} is not: 2")
        assertTrue(factory.isIgnoreCase() == true, "${factory.isIgnoreCase()} does not equal: true")

        factory =
            tokenFilterFactory(
                "ProtectedTerm",
                Version.LATEST,
                loader,
                "protected",
                "protected-1.txt, protected-2.txt",
                "ignoreCase",
                "true",
                "wrappedFilters",
                "lowercase"
            ) as ProtectedTermFilterFactory
        protectedTerms = factory.getProtectedTerms()
        assertTrue(protectedTerms != null, "protectedTerms is null and it shouldn't be")
        assertTrue(protectedTerms.size == 4, "protectedTerms Size: ${protectedTerms.size} is not: 4")
        assertTrue(factory.isIgnoreCase() == true, "${factory.isIgnoreCase()} does not equal: true")

        factory = tokenFilterFactory("ProtectedTerm", "protected", "protected-1.txt") as ProtectedTermFilterFactory
        assertEquals(false, factory.isIgnoreCase())
    }

    @Test
    fun testBasic() {
        val loader = StringResourceLoader(mapOf("protected-1.txt" to "Foo\nBar\n"))
        val str = "Foo Clara Bar David"
        var stream: TokenStream = whitespaceMockTokenizer(str)
        stream =
            tokenFilterFactory(
                "ProtectedTerm",
                Version.LATEST,
                loader,
                "ignoreCase",
                "true",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "lowercase"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Foo", "clara", "Bar", "david"))
    }

    @Test
    fun testMultipleWrappedFiltersWithParams() {
        val loader = StringResourceLoader(mapOf("protected-1.txt" to "Foo\nBar\n"))
        val str = "Foo Clara Bar David"
        var stream: TokenStream = whitespaceMockTokenizer(str)
        stream =
            tokenFilterFactory(
                "ProtectedTerm",
                Version.LATEST,
                loader,
                "ignoreCase",
                "true",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "lowercase, truncate",
                "truncate.prefixLength",
                "2"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Foo", "cl", "Bar", "da"))
    }

    @Test
    fun testMultipleSameNamedFiltersWithParams() {
        val loader = StringResourceLoader(mapOf("protected-1.txt" to "Foo\nBar\n"))
        val str = "Foo Clara Bar David"
        var stream: TokenStream = whitespaceMockTokenizer(str)
        stream =
            tokenFilterFactory(
                "ProtectedTerm",
                Version.LATEST,
                loader,
                "ignoreCase",
                "true",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "truncate-A, reversestring, truncate-B",
                "truncate-A.prefixLength",
                "3",
                "truncate-B.prefixLength",
                "2"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Foo", "al", "Bar", "va"))

        stream = whitespaceMockTokenizer(str)
        stream =
            tokenFilterFactory(
                "ProtectedTerm",
                Version.LATEST,
                loader,
                "ignoreCase",
                "true",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "truncate, reversestring, truncate-A",
                "truncate.prefixLength",
                "3",
                "truncate-A.prefixLength",
                "2"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Foo", "al", "Bar", "va"))

        stream = whitespaceMockTokenizer(str)
        stream =
            tokenFilterFactory(
                "ProtectedTerm",
                Version.LATEST,
                loader,
                "ignoreCase",
                "true",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "TRUNCATE-a, reversestring, truncate-b",
                "truncate-A.prefixLength",
                "3",
                "TRUNCATE-B.prefixLength",
                "2"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Foo", "al", "Bar", "va"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        var exception = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("ProtectedTerm", "protected", "protected-1.txt", "bogusArg", "bogusValue")
        }
        assertTrue(exception.message!!.contains("Unknown parameters"))

        exception = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "ProtectedTerm",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "truncate, truncate"
            )
        }
        assertTrue(exception.message!!.contains("wrappedFilters contains duplicate"))

        exception = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "ProtectedTerm",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "TRUNCATE, truncate"
            )
        }
        assertTrue(exception.message!!.contains("wrappedFilters contains duplicate"))

        exception = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "ProtectedTerm",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "truncate-ABC, truncate-abc"
            )
        }
        assertTrue(exception.message!!.contains("wrappedFilters contains duplicate"))

        exception = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "ProtectedTerm",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "truncate-A, reversestring, truncate-B",
                "truncate.prefixLength",
                "3",
                "truncate-A.prefixLength",
                "2"
            )
        }
        assertTrue(exception.message!!.contains("Unknown parameters: {truncate.prefixLength=3}"))

        val str = "Foo Clara Bar David"
        val stream = whitespaceMockTokenizer(str)
        exception = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "ProtectedTerm",
                "protected",
                "protected-1.txt",
                "wrappedFilters",
                "length"
            ).create(stream)
        }
        assertTrue(exception.message!!.contains("Configuration Error: missing parameter"))
    }
}
