package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.util.ResourceLoader

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Testcase for [TypeTokenFilterFactory] */
class TestTypeTokenFilterFactory : BaseTokenStreamFactoryTestCase() {
    @Test
    @Throws(Exception::class)
    fun testInform() {
        val loader: ResourceLoader = StringResourceLoader(resources)

        var factory = TypeTokenFilterFactory(mutableMapOf("types" to "stoptypes-1.txt"))
        factory.inform(loader)
        var types = factory.getStopTypes()
        assertNotNull(types, "types is null and it shouldn't be")
        assertTrue(types.size == 2, "types Size: ${types.size} is not: 2")

        factory = TypeTokenFilterFactory(
            mutableMapOf(
                "types" to "stoptypes-1.txt, stoptypes-2.txt",
                "useWhitelist" to "true"
            )
        )
        factory.inform(loader)
        types = factory.getStopTypes()
        assertNotNull(types, "types is null and it shouldn't be")
        assertTrue(types.size == 4, "types Size: ${types.size} is not: 4")
    }

    @Test
    @Throws(Exception::class)
    fun testCreationWithBlackList() {
        val loader: ResourceLoader = StringResourceLoader(resources)
        val factory: TokenFilterFactory =
            TypeTokenFilterFactory(mutableMapOf("types" to "stoptypes-1.txt, stoptypes-2.txt"))
        (factory as TypeTokenFilterFactory).inform(loader)
        val input = CannedTokenStream()
        factory.create(input)
    }

    @Test
    @Throws(Exception::class)
    fun testCreationWithWhiteList() {
        val loader: ResourceLoader = StringResourceLoader(resources)
        val factory: TokenFilterFactory =
            TypeTokenFilterFactory(
                mutableMapOf(
                    "types" to "stoptypes-1.txt, stoptypes-2.txt",
                    "useWhitelist" to "true"
                )
            )
        (factory as TypeTokenFilterFactory).inform(loader)
        val input = CannedTokenStream()
        factory.create(input)
    }

    @Test
    @Throws(Exception::class)
    fun testMissingTypesParameter() {
        // not supplying 'types' parameter should cause an IllegalArgumentException
        expectThrows(IllegalArgumentException::class) {
            TypeTokenFilterFactory(mutableMapOf())
        }
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected =
            expectThrows(IllegalArgumentException::class) {
                TypeTokenFilterFactory(
                    mutableMapOf(
                        "types" to "stoptypes-1.txt",
                        "bogusArg" to "bogusValue"
                    )
                )
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
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
            "stoptypes-1.txt" to "<NUM>\n<EMAIL>\n",
            "stoptypes-2.txt" to "<HOST>\n<APOSTROPHE>\n"
        )
    }
}
