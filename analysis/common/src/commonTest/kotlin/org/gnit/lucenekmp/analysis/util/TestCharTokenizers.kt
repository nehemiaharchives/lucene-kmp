package org.gnit.lucenekmp.analysis.util

import okio.IOException
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.core.LetterTokenizer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Testcase for [CharTokenizer] subclasses  */
class TestCharTokenizers : BaseTokenStreamTestCase() {
    /*
       * test to read surrogate pairs without loosing the pairing
       * if the surrogate pair is at the border of the internal IO buffer
       */
    @Test
    @Throws(IOException::class)
    fun testReadSupplementaryChars() {
        val builder = StringBuilder()
        // create random input
        var num: Int = 1024 + random().nextInt(1024)
        num *= RANDOM_MULTIPLIER
        for (i in 1..<num) {
            builder.append("\ud801\udc1cabc")
            if ((i % 10) == 0) builder.append(" ")
        }
        // internal buffer size is 1024 make sure we have a surrogate pair right at the border
        builder.insert(1023, "\ud801\udc1c")
        val tokenizer: Tokenizer =
            LetterTokenizer(newAttributeFactory())
        tokenizer.setReader(StringReader(builder.toString()))
        assertTokenStreamContents(
            LowerCaseFilter(tokenizer),
            builder.toString().lowercase().split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
    }

    /*
   * test to extend the buffer TermAttribute buffer internally. If the internal
   * alg that extends the size of the char array only extends by 1 char and the
   * next char to be filled in is a supplementary codepoint (using 2 chars) an
   * index out of bound exception is triggered.
   */
    @Test
    @Throws(IOException::class)
    fun testExtendCharBuffer() {
        for (i in 0..39) {
            val builder = StringBuilder()
            for (j in 0..<1 + i) {
                builder.append("a")
            }
            builder.append("\ud801\udc1cabc")
            val tokenizer: Tokenizer = LetterTokenizer(newAttributeFactory())
            tokenizer.setReader(StringReader(builder.toString()))
            assertTokenStreamContents(
                LowerCaseFilter(tokenizer),
                arrayOf<String>(builder.toString().lowercase())
            )
        }
    }

    /*
   * tests the max word length of 255 - tokenizer will split at the 255 char no matter what happens
   */
    @Test
    @Throws(IOException::class)
    fun testMaxWordLength() {
        val builder = StringBuilder()

        for (i in 0..254) {
            builder.append("A")
        }
        val tokenizer: Tokenizer = LetterTokenizer(newAttributeFactory())
        tokenizer.setReader(StringReader(builder.toString() + builder.toString()))
        assertTokenStreamContents(
            LowerCaseFilter(tokenizer),
            arrayOf<String>(
                builder.toString().lowercase(), builder.toString().lowercase()
            )
        )
    }

    /*
   * tests the max word length passed as parameter - tokenizer will split at the passed position char no matter what happens
   */
    @Test
    @Throws(IOException::class)
    fun testCustomMaxTokenLength() {
        val builder = StringBuilder()
        for (i in 0..99) {
            builder.append("A")
        }
        var tokenizer: Tokenizer = LetterTokenizer(
                newAttributeFactory(),
                100
            )
        // Tricky, passing two copies of the string to the reader....
        tokenizer.setReader(StringReader(builder.toString() + builder.toString()))
        assertTokenStreamContents(
            LowerCaseFilter(tokenizer),
            arrayOf(
                builder.toString().lowercase(), builder.toString().lowercase()
            )
        )

        var e: Exception = expectThrows(IllegalArgumentException::class) {
                LetterTokenizer(newAttributeFactory(), -1)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: -1", e.message)

        tokenizer = LetterTokenizer(newAttributeFactory(), 100)
        tokenizer.setReader(StringReader(builder.toString() + builder.toString()))
        assertTokenStreamContents(tokenizer, arrayOf(builder.toString(), builder.toString()))

        // Let's test that we can get a token longer than 255 through.
        builder.setLength(0)
        for (i in 0..499) {
            builder.append("Z")
        }
        tokenizer = LetterTokenizer(newAttributeFactory(), 500)
        tokenizer.setReader(StringReader(builder.toString()))
        assertTokenStreamContents( tokenizer, arrayOf(builder.toString()))

        // Just to be sure what is happening here, token lengths of zero make no sense,
        // Let's try the edge cases, token > I/O buffer (4096)
        builder.setLength(0)
        for (i in 0..599) {
            builder.append("aUrOkIjq") // 600 * 8 = 4800 chars.
        }

        e = expectThrows(IllegalArgumentException::class) {
                LetterTokenizer(newAttributeFactory(), 0)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 0", e.message)

        e = expectThrows(IllegalArgumentException::class) {
                LetterTokenizer(newAttributeFactory(), 10000000)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 10000000", e.message)

        tokenizer = LetterTokenizer(newAttributeFactory(), 4800)
        tokenizer.setReader(StringReader(builder.toString()))
        assertTokenStreamContents(LowerCaseFilter(tokenizer), arrayOf(builder.toString().lowercase()))

        e = expectThrows(IllegalArgumentException::class) {
                KeywordTokenizer(newAttributeFactory(), 0)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 0", e.message)

        e = expectThrows(IllegalArgumentException::class) {
                KeywordTokenizer(newAttributeFactory(), 10000000)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 10000000", e.message)

        tokenizer = KeywordTokenizer(newAttributeFactory(), 4800)
        tokenizer.setReader(StringReader(builder.toString()))
        assertTokenStreamContents(tokenizer, arrayOf(builder.toString()))

        e = expectThrows(IllegalArgumentException::class) {
                LetterTokenizer(newAttributeFactory(), 0)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 0", e.message)

        e = expectThrows(IllegalArgumentException::class) {
                LetterTokenizer(newAttributeFactory(), 2000000)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 2000000", e.message)

        tokenizer = LetterTokenizer(newAttributeFactory(), 4800)
        tokenizer.setReader(StringReader(builder.toString()))
        assertTokenStreamContents(tokenizer, arrayOf(builder.toString()))

        e = expectThrows(IllegalArgumentException::class) {
                WhitespaceTokenizer(newAttributeFactory(), 0)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 0", e.message)

        e = expectThrows(IllegalArgumentException::class) {
                WhitespaceTokenizer(newAttributeFactory(), 3000000)
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 3000000", e.message)

        tokenizer = WhitespaceTokenizer(newAttributeFactory(), 4800)
        tokenizer.setReader(StringReader(builder.toString()))
        assertTokenStreamContents(tokenizer, arrayOf(builder.toString()))
    }

    /*
   * tests the max word length of 255 with a surrogate pair at position 255
   */
    @Test
    @Throws(IOException::class)
    fun testMaxWordLengthWithSupplementary() {
        val builder = StringBuilder()

        for (i in 0..253) {
            builder.append("A")
        }
        builder.append("\ud801\udc1c")
        val tokenizer: Tokenizer = LetterTokenizer(newAttributeFactory())
        tokenizer.setReader(StringReader(builder.toString() + builder.toString()))
        assertTokenStreamContents(
            LowerCaseFilter(tokenizer),
            arrayOf(builder.toString().lowercase(), builder.toString().lowercase())
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDefinitionUsingMethodReference1() {
        val reader = StringReader("Tokenizer Test")
        val tokenizer: Tokenizer =
            CharTokenizer.fromSeparatorCharPredicate({ codePoint: Int ->
                codePoint.toChar().isWhitespace()
            })
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Tokenizer", "Test"))
    }

    @Test
    @Throws(Exception::class)
    fun testDefinitionUsingMethodReference2() {
        val reader = StringReader("Tokenizer(Test)")
        val tokenizer: Tokenizer =
            CharTokenizer.fromTokenCharPredicate({ codePoint: Int ->
                codePoint.toChar().isLetter()
            })
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Tokenizer", "Test"))
    }

    @Test
    @Throws(Exception::class)
    fun testDefinitionUsingLambda() {
        val reader = StringReader("Tokenizer\u00A0Test Foo")
        val tokenizer: Tokenizer =
            CharTokenizer.fromSeparatorCharPredicate({ c: Int ->
                c == '\u00A0'.code || c.toChar().isWhitespace()
            })
        tokenizer.setReader(reader)
        assertTokenStreamContents(
            tokenizer,
            arrayOf("Tokenizer", "Test", "Foo")
        )
    }
}
