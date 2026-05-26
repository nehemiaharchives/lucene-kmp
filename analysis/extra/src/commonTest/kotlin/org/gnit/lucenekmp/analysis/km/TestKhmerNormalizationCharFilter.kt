package org.gnit.lucenekmp.analysis.km

import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Tests for [KhmerNormalizationCharFilter]. */
class TestKhmerNormalizationCharFilter {
    private fun readFully(input: KhmerNormalizationCharFilter): String {
        input.use { reader ->
            val builder = StringBuilder()
            val buffer = CharArray(16)
            while (true) {
                val read = reader.read(buffer, 0, buffer.size)
                if (read == -1) {
                    break
                }
                builder.appendRange(buffer, 0, read)
            }
            return builder.toString()
        }
    }

    @Test
    fun testLevelOneNormalization() {
        val reader = KhmerNormalizationCharFilter(StringReader("េី េា"), 1)
        assertEquals("ើ ោ", readFully(reader))
    }

    @Test
    fun testLevelTwoNormalization() {
        val reader = KhmerNormalizationCharFilter(StringReader(":"), 2)
        assertEquals("ៈ", readFully(reader))
    }

    @Test
    fun testLevelThreeNormalization() {
        val reader = KhmerNormalizationCharFilter(StringReader("១២៣៤៥ ស៉"), 3)
        assertEquals("12345 ស៊", readFully(reader))
    }

    @Test
    fun testDefaultConstructorUsesLevelOne() {
        val reader = KhmerNormalizationCharFilter(StringReader("េី"))
        assertEquals("ើ", readFully(reader))
    }

    @Test
    fun testMemoizedNormalizeCharMap() {
        val levelTwoA = KhmerNormalizationCharFilter.getTibNormalizeCharMapMemoized(2)
        val levelTwoB = KhmerNormalizationCharFilter.getTibNormalizeCharMapMemoized(2)
        val levelThree = KhmerNormalizationCharFilter.getTibNormalizeCharMapMemoized(3)
        assertSame(levelTwoA, levelTwoB)
        assertNotSame(levelTwoA, levelThree)
    }
}
