package org.gnit.lucenekmp.analysis.km

import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests for [GraphemeClusterTokenizer]. */
class TestGraphemeClusterTokenizer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testKhmerClusters() {
        val tokenizer = GraphemeClusterTokenizer()
        tokenizer.setReader(StringReader("ខ្ញុំ ច_ង់៕ធ្វើការ"))
        assertTokenStreamContents(tokenizer, arrayOf("ខ្ញុំ", "ច", "ង់", "ធ្វើ", "កា", "រ"))
    }

    @Test
    @Throws(Exception::class)
    fun testDigitsAndPunctuationBoundaries() {
        val tokenizer = GraphemeClusterTokenizer()
        tokenizer.setReader(StringReader("១២៣៕456៧"))
        assertTokenStreamContents(
            tokenizer,
            arrayOf("១២៣", "456៧"),
            intArrayOf(0, 4),
            intArrayOf(3, 8)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testIgnoresLatinLetters() {
        val tokenizer = GraphemeClusterTokenizer()
        tokenizer.setReader(StringReader("abcខ្ញុំxyz"))
        assertTokenStreamContents(tokenizer, arrayOf("ខ្ញុំ"), intArrayOf(3), intArrayOf(8))
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyInput() {
        val tokenizer = GraphemeClusterTokenizer()
        tokenizer.setReader(StringReader(""))
        assertTokenStreamContents(tokenizer, arrayOf())
    }
}
