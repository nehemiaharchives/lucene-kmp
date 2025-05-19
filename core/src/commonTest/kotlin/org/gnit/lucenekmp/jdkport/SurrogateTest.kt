package org.gnit.lucenekmp.jdkport

import kotlin.test.*

class SurrogateTest {

    @Test
    fun testIsHigh() {
        assertTrue(Surrogate.isHigh(Surrogate.MIN_HIGH.code))
        assertTrue(Surrogate.isHigh(Surrogate.MAX_HIGH.code))
        assertFalse(Surrogate.isHigh('A'.code))
        assertFalse(Surrogate.isHigh(Surrogate.MIN_LOW.code))
    }

    @Test
    fun testIsLow() {
        assertTrue(Surrogate.isLow(Surrogate.MIN_LOW.code))
        assertTrue(Surrogate.isLow(Surrogate.MAX_LOW.code))
        assertFalse(Surrogate.isLow('A'.code))
        assertFalse(Surrogate.isLow(Surrogate.MIN_HIGH.code))
    }

    @Test
    fun testIs() {
        assertTrue(Surrogate.`is`(Surrogate.MIN.code))
        assertTrue(Surrogate.`is`(Surrogate.MAX.code))
        assertFalse(Surrogate.`is`('A'.code))
    }

    @Test
    fun testNeededFor() {
        assertTrue(Surrogate.neededFor(Surrogate.UCS4_MIN))
        assertFalse(Surrogate.neededFor('A'.code))
    }

    @Test
    fun testHighLow() {
        val codePoint = 0x1F600 // 😀
        val high = Surrogate.high(codePoint)
        val low = Surrogate.low(codePoint)
        assertTrue(Character.isHighSurrogate(high))
        assertTrue(low.isLowSurrogate())
        assertEquals(codePoint, Surrogate.toUCS4(high, low))
    }

    @Test
    fun testToUCS4Throws() {
        val high = 'A'
        val low = 'B'
        assertFailsWith<IllegalArgumentException> {
            Surrogate.toUCS4(high, low)
        }
    }

    @Test
    fun testParserParseValidPair() {
        val codePoint = 0x1F600 // 😀
        val high = Character.highSurrogate(codePoint)
        val low = Character.lowSurrogate(codePoint)
        val buf = CharBuffer.wrap(charArrayOf(low))
        val parser = Surrogate.Parser()
        val result = parser.parse(high, buf)
        assertEquals(codePoint, result)
        assertTrue(parser.isPair())
        assertEquals(2, parser.increment())
        assertEquals(codePoint, parser.character())
    }

    @Test
    fun testParserParseSingleChar() {
        val c = 'A'
        val buf = CharBuffer.allocate(0)
        val parser = Surrogate.Parser()
        val result = parser.parse(c, buf)
        assertEquals(c.code, result)
        assertFalse(parser.isPair())
        assertEquals(1, parser.increment())
        assertEquals(c.code, parser.character())
    }

    @Test
    fun testParserParseMalformed() {
        val parser = Surrogate.Parser()
        val result = parser.parse(Surrogate.MIN_LOW, CharBuffer.allocate(0))
        assertEquals(-1, result)
        assertEquals(CoderResult.malformedForLength(1), parser.error())
    }

    @Test
    fun testParserParseUnderflow() {
        val parser = Surrogate.Parser()
        val result = parser.parse(Surrogate.MIN_HIGH, CharBuffer.allocate(0))
        assertEquals(-1, result)
        assertEquals(CoderResult.UNDERFLOW, parser.error())
    }

    @Test
    fun testParserArrayParseValidPair() {
        val codePoint = 0x1F600 // 😀
        val high = Character.highSurrogate(codePoint)
        val low = Character.lowSurrogate(codePoint)
        val arr = charArrayOf(high, low)
        val parser = Surrogate.Parser()
        val result = parser.parse(high, arr, 0, 2)
        assertEquals(codePoint, result)
        assertTrue(parser.isPair())
        assertEquals(2, parser.increment())
        assertEquals(codePoint, parser.character())
    }

    @Test
    fun testParserArrayParseMalformed() {
        val arr = charArrayOf(Surrogate.MIN_LOW)
        val parser = Surrogate.Parser()
        val result = parser.parse(Surrogate.MIN_LOW, arr, 0, 1)
        assertEquals(-1, result)
        assertEquals(CoderResult.malformedForLength(1), parser.error())
    }

    @Test
    fun testParserArrayParseUnderflow() {
        val arr = charArrayOf(Surrogate.MIN_HIGH)
        val parser = Surrogate.Parser()
        val result = parser.parse(Surrogate.MIN_HIGH, arr, 0, 1)
        assertEquals(-1, result)
        assertEquals(CoderResult.UNDERFLOW, parser.error())
    }

    @Test
    fun testGeneratorGenerateBmp() {
        val gen = Surrogate.Generator()
        val buf = CharBuffer.allocate(1)
        val written = gen.generate('A'.code, 1, buf)
        assertEquals(1, written)
        buf.flip()
        assertEquals('A', buf.get())
    }

    @Test
    fun testGeneratorGenerateSupplementary() {
        val gen = Surrogate.Generator()
        val codePoint = 0x1F600 // 😀
        val buf = CharBuffer.allocate(2)
        val written = gen.generate(codePoint, 1, buf)
        assertEquals(2, written)
        buf.flip()
        assertEquals(Character.highSurrogate(codePoint), buf.get())
        assertEquals(Character.lowSurrogate(codePoint), buf.get())
    }

    @Test
    fun testGeneratorGenerateOverflow() {
        val gen = Surrogate.Generator()
        val codePoint = 0x1F600 // 😀
        val buf = CharBuffer.allocate(1)
        val written = gen.generate(codePoint, 1, buf)
        assertEquals(-1, written)
        assertEquals(CoderResult.OVERFLOW, gen.error())
    }

    @Test
    fun testGeneratorGenerateMalformed() {
        val gen = Surrogate.Generator()
        val c = Surrogate.MIN_HIGH.code
        val buf = CharBuffer.allocate(1)
        val written = gen.generate(c, 1, buf)
        assertEquals(-1, written)
        assertEquals(CoderResult.malformedForLength(1), gen.error())
    }

    @Test
    fun testGeneratorArrayGenerateBmp() {
        val gen = Surrogate.Generator()
        val arr = CharArray(1)
        val written = gen.generate('A'.code, 1, arr, 0, 1)
        assertEquals(1, written)
        assertEquals('A', arr[0])
    }

    @Test
    fun testGeneratorArrayGenerateSupplementary() {
        val gen = Surrogate.Generator()
        val codePoint = 0x1F600 // 😀
        val arr = CharArray(2)
        val written = gen.generate(codePoint, 1, arr, 0, 2)
        assertEquals(2, written)
        assertEquals(Character.highSurrogate(codePoint), arr[0])
        assertEquals(Character.lowSurrogate(codePoint), arr[1])
    }

    @Test
    fun testGeneratorArrayGenerateOverflow() {
        val gen = Surrogate.Generator()
        val codePoint = 0x1F600 // 😀
        val arr = CharArray(1)
        val written = gen.generate(codePoint, 1, arr, 0, 1)
        assertEquals(-1, written)
        assertEquals(CoderResult.OVERFLOW, gen.error())
    }

    @Test
    fun testGeneratorArrayGenerateMalformed() {
        val gen = Surrogate.Generator()
        val c = Surrogate.MIN_HIGH.code
        val arr = CharArray(1)
        val written = gen.generate(c, 1, arr, 0, 1)
        assertEquals(-1, written)
        assertEquals(CoderResult.malformedForLength(1), gen.error())
    }
}
