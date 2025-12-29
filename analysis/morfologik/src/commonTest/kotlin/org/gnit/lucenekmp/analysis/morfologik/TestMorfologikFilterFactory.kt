package org.gnit.lucenekmp.analysis.morfologik

import okio.IOException
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
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

    private class InMemoryResourceLoader(private val resources: Map<String, ByteArray>) : ResourceLoader {
        override fun openResource(resource: String): InputStream {
            val bytes = resources[resource] ?: throw IOException("Resource not found: $resource")
            val buffer = Buffer().apply { write(bytes) }
            return OkioSourceInputStream(buffer)
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
        val loader = InMemoryResourceLoader(
            mapOf(
                "custom-dictionary.dict" to CUSTOM_DICT_BYTES,
                "custom-dictionary.info" to CUSTOM_INFO_BYTES
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
        val loader = InMemoryResourceLoader(emptyMap())
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

    companion object {
        private val CUSTOM_DICT_BYTES: ByteArray =
            requireNotNull("XGZzYQVfKwIAAABeBmkGbgZmBmwGZQZjBnQGZQZkBjG4ATIGOwZBBkQGRgZtBm0GYQYyBjsGdAZhBmcGMgMAOwZBBkQGRgZtBm0GYQYxBjsGdAZhBmcGMQMA".decodeBase64())
                .toByteArray()

        private val CUSTOM_INFO_BYTES: ByteArray =
            requireNotNull("IwojIEFuIGV4YW1wbGUgc3RlbW1pbmcgZGljdGlvbmFyeSBmaWxlIGZvciBNb3Jmb2xvZ2lrIGZpbHRlci4KIwojIENvbXBpbGUgd2l0aCBNb3Jmb2xvZ2lrLXN0ZW1taW5nLCBzZWUKIyBodHRwczovL2dpdGh1Yi5jb20vbW9yZm9sb2dpay9tb3Jmb2xvZ2lrLXN0ZW1taW5nL3dpa2kvRXhhbXBsZXMKIwoKIyBBdXRob3Igb2YgdGhlIGRpY3Rpb25hcnkuCmZzYS5kaWN0LmF1dGhvcj1BY21lIEluYy4KCiMgRGF0ZSB0aGUgZGljdGlvbmFyeSBkYXRhIHdhcyBhc3NlbWJsZWQgKG5vdCBjb21waWxhdGlvbiB0aW1lISkuCmZzYS5kaWN0LmNyZWF0ZWQ9MjAxNS8xMC8wOCAwOToxNjowMAoKIyBUaGUgbGljZW5zZSBmb3IgdGhlIGRpY3Rpb25hcnkgZGF0YS4KZnNhLmRpY3QubGljZW5zZT1BU0wgMi4wCgojIENoYXJhY3RlciBlbmNvZGluZyBpbnNpZGUgdGhlIGF1dG9tYXRvbiAoYW5kIGlucHV0IGZpbGUpLgpmc2EuZGljdC5lbmNvZGluZz1VVEYtOAoKIyBmaWVsZCBzZXBhcmF0b3IgKGxlbW1hO2luZmxlY3RlZDt0YWcpCmZzYS5kaWN0LnNlcGFyYXRvcj07CgojIHR5cGUgb2YgYmFzZS9sZW1tYSBjb21wcmVzc2lvbiBlbmNvZGluZyBiZWZvcmUgYXV0b21hdG9uIGNvbXByZXNzaW9uLgpmc2EuZGljdC5lbmNvZGVyPUlORklY".decodeBase64())
                .toByteArray()
    }
}
