package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestWordlistLoader : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testWordlistLoading() {
        val s = "ONE\n  two \nthree\n\n"
        val wordSet1 = WordlistLoader.getWordSet(StringReader(s))
        checkSet(wordSet1)
        val wordSet2 = WordlistLoader.getWordSet(BufferedReader(StringReader(s)))
        checkSet(wordSet2)
    }

    @Test
    @Throws(Exception::class)
    fun testComments() {
        val s = "ONE\n  two \nthree\n#comment"
        val wordSet1 = WordlistLoader.getWordSet(StringReader(s), "#")
        checkSet(wordSet1)
        assertFalse(wordSet1.contains("#comment"))
        assertFalse(wordSet1.contains("comment"))
    }

    private fun checkSet(wordset: CharArraySet) {
        assertEquals(3, wordset.size)
        assertTrue(wordset.contains("ONE")) // case is not modified
        assertTrue(wordset.contains("two")) // surrounding whitespace is removed
        assertTrue(wordset.contains("three"))
        assertFalse(wordset.contains("four"))
    }

    /** Test stopwords in snowball format */
    @Test
    @Throws(IOException::class)
    fun testSnowballListLoading() {
        val s =
            "|comment\n" + // commented line
                " |comment\n" + // commented line with leading whitespace
                "\n" + // blank line
                "  \t\n" + // line with only whitespace
                " |comment | comment\n" + // commented line with comment
                "ONE\n" + // stopword, in uppercase
                "   two   \n" + // stopword with leading/trailing space
                " three   four five \n" + // multiple stopwords
                "six seven | comment\n" // multiple stopwords + comment
        val wordset = WordlistLoader.getSnowballWordSet(StringReader(s))
        assertEquals(7, wordset.size)
        assertTrue(wordset.contains("ONE"))
        assertTrue(wordset.contains("two"))
        assertTrue(wordset.contains("three"))
        assertTrue(wordset.contains("four"))
        assertTrue(wordset.contains("five"))
        assertTrue(wordset.contains("six"))
        assertTrue(wordset.contains("seven"))
    }

    @Test
    @Throws(IOException::class)
    fun testGetLines() {
        val s = "One \n#Comment \n \n Two \n  Three  \n"
        val charset: Charset = StandardCharsets.UTF_8
        val sByteArr = s.encodeToByteArray()
        val sInputStream = ByteArrayInputStream(sByteArr)
        val lines = WordlistLoader.getLines(sInputStream, charset)
        assertEquals(3, lines.size)
        assertEquals("One", lines[0])
        assertEquals("Two", lines[1])
        assertEquals("Three", lines[2])
    }
}
