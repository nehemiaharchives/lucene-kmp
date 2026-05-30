package org.gnit.lucenekmp.analysis.charfilter

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestMappingCharFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testParseString() {
        val f = charFilterFactory("Mapping") as MappingCharFilterFactory

        assertFailsWith<IllegalArgumentException> {
            f.parseString("\\")
        }

            assertEquals(
              "\\\"\n\t\r\b\u000C",
              f.parseString("\\\\\\\"\\n\\t\\r\\b\\f"),
              "unexpected escaped characters"
            )
            assertEquals("A", f.parseString("\\u0041"), "unexpected escaped characters")
            assertEquals("AB", f.parseString("\\u0041\\u0042"), "unexpected escaped characters")

        assertFailsWith<IllegalArgumentException> {
            f.parseString("\\u000")
        }

        // invalid hex number
        assertFailsWith<NumberFormatException> {
            f.parseString("\\u123x")
        }
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            charFilterFactory("Mapping", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}

