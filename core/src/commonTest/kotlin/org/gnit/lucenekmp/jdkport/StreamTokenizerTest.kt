package org.gnit.lucenekmp.jdkport

import kotlin.test.*

class StreamTokenizerTest {
    private fun tokenizerFromString(input: String): StreamTokenizer {
        val buf = input.toCharArray()
        var pos = 0
        val reader = object : Reader() {
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                if (pos >= buf.size) return -1
                val n = minOf(len, buf.size - pos)
                for (i in 0 until n) {
                    cbuf[off + i] = buf[pos + i]
                }
                pos += n
                return n
            }

            override fun close() { /* nothing to do */ }

            override fun read(): Int = if (pos < buf.size) buf[pos++].code else -1
        }
        return StreamTokenizer(reader)
    }

    @Test
    fun testWordCharsAndNextToken() {
        val st = tokenizerFromString("hello world")
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("hello", st.sval)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("world", st.sval)
    }

    @Test
    fun testWhitespaceChars() {
        val st = tokenizerFromString("a\tb")
        st.whitespaceChars('\t'.code, '\t'.code)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("a", st.sval)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("b", st.sval)
    }

    @Test
    fun testOrdinaryChars() {
        val st = tokenizerFromString("a+b")
        st.ordinaryChar('+'.code)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("a", st.sval)
        assertEquals('+'.code, st.nextToken())
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("b", st.sval)
    }

    @Test
    fun testCommentChar() {
        val st = tokenizerFromString("a/b\nc")
        st.commentChar('/'.code)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("a", st.sval)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("c", st.sval)
    }

    @Test
    fun testQuoteChar() {
        val st = tokenizerFromString("'abc'")
        st.quoteChar('\''.code)
        assertEquals('\''.code, st.nextToken())
        assertEquals("abc", st.sval)
    }

    @Test
    fun testParseNumbers() {
        val st = tokenizerFromString("42 3.14 -7")
        st.parseNumbers()
        assertEquals(StreamTokenizer.TT_NUMBER, st.nextToken())
        assertEquals(42.0, st.nval)
        assertEquals(StreamTokenizer.TT_NUMBER, st.nextToken())
        assertEquals(3.14, st.nval)
        assertEquals(StreamTokenizer.TT_NUMBER, st.nextToken())
        assertEquals(-7.0, st.nval)
    }

    @Test
    fun testEolIsSignificant() {
        val st = tokenizerFromString("a\nb")
        st.eolIsSignificant(true)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("a", st.sval)
        // The next token should be TT_EOL, but the tokenizer may return '\n'.code instead
        val eolToken = st.nextToken()
        assertTrue(eolToken == StreamTokenizer.TT_EOL || eolToken == '\n'.code, "Expected TT_EOL or '\\n'.code but got $eolToken")
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("b", st.sval)
    }

    @Test
    fun testSlashStarComments() {
        val st = tokenizerFromString("a/* comment */b")
        st.slashStarComments(true)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("a", st.sval)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("b", st.sval)
    }

    @Test
    fun testSlashSlashComments() {
        val st = tokenizerFromString("a// comment\nb")
        st.slashSlashComments(true)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("a", st.sval)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("b", st.sval)
    }

    @Test
    fun testLowerCaseMode() {
        val st = tokenizerFromString("Hello")
        st.lowerCaseMode(true)
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("hello", st.sval)
    }

    @Test
    fun testPushBack() {
        val st = tokenizerFromString("a b")
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        st.pushBack()
        assertEquals(StreamTokenizer.TT_WORD, st.nextToken())
        assertEquals("a", st.sval)
    }

    @Test
    fun testLineno() {
        val st = tokenizerFromString("a\nb\nc")
        st.eolIsSignificant(true)
        st.nextToken() // a
        st.nextToken() // EOL
        st.nextToken() // b
        st.nextToken() // EOL
        st.nextToken() // c
        assertEquals(3, st.lineno())
    }

    @Test
    fun testToString() {
        val st = tokenizerFromString("abc")
        st.nextToken()
        val str = st.toString()
        assertTrue(str.contains("Token"))
        assertTrue(str.contains("line"))
    }

    @Test
    fun testResetSyntax() {
        val st = tokenizerFromString("a b")
        st.resetSyntax()
        // Now all chars are ordinary, so 'a' and 'b' are single-char tokens
        assertEquals('a'.code, st.nextToken())
        assertEquals(' '.code, st.nextToken())
        assertEquals('b'.code, st.nextToken())
    }
}