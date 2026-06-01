package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.analysis.en.PorterStemFilter
import org.gnit.lucenekmp.analysis.miscellaneous.StemmerOverrideFilter.StemmerOverrideMap
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestStemmerOverrideFilter : BaseTokenStreamTestCase() {
    @Throws(IOException::class)
    private fun keywordTokenizer(data: String): KeywordTokenizer {
        val tokenizer = KeywordTokenizer()
        tokenizer.setReader(StringReader(data))
        return tokenizer
    }

    @Test
    @Throws(IOException::class)
    fun testOverride() {
        val builder = StemmerOverrideFilter.Builder()
        builder.add("booked", "books")
        val tokenizer: Tokenizer = keywordTokenizer("booked")
        val stream: TokenStream =
            PorterStemFilter(StemmerOverrideFilter(tokenizer, builder.build()))
        assertTokenStreamContents(stream, arrayOf("books"))
    }

    @Test
    @Throws(IOException::class)
    fun testIgnoreCase() {
        val builder = StemmerOverrideFilter.Builder(true)
        builder.add("boOkEd", "books")
        val tokenizer: Tokenizer = keywordTokenizer("BooKeD")
        val stream: TokenStream =
            PorterStemFilter(StemmerOverrideFilter(tokenizer, builder.build()))
        assertTokenStreamContents(stream, arrayOf("books"))
    }

    @Test
    @Throws(IOException::class)
    fun testNoOverrides() {
        val builder = StemmerOverrideFilter.Builder(true)
        val tokenizer: Tokenizer = keywordTokenizer("book")
        val stream: TokenStream =
            PorterStemFilter(StemmerOverrideFilter(tokenizer, builder.build()))
        assertTokenStreamContents(stream, arrayOf("book"))
    }

    @Test
    @Throws(IOException::class)
    fun testRandomRealisticWhiteSpace() {
        val map = hashMapOf<String, String>()
        val seen = hashSetOf<String>()
        val numTerms = atLeast(50)
        val ignoreCase = random().nextBoolean()

        for (i in 0 until numTerms) {
            val randomRealisticUnicodeString = TestUtil.randomRealisticUnicodeString(random())
            val charArray = randomRealisticUnicodeString.toCharArray()
            val builder = StringBuilder()
            var j = 0
            while (j < charArray.size) {
                val cp = Character.codePointAt(charArray, j, charArray.size)
                if (!Character.isWhitespace(cp)) {
                    val dst = CharArray(2)
                    val len = Character.toChars(cp, dst, 0)
                    builder.append(String.fromCharArray(dst, 0, len))
                }
                j += Character.charCount(cp)
            }
            if (builder.isNotEmpty()) {
                val inputValue = builder.toString()

                val seenInputValue =
                    if (ignoreCase) {
                        val buffer = inputValue.toCharArray()
                        var k = 0
                        while (k < buffer.size) {
                            val cp = Character.codePointAt(buffer, k, buffer.size)
                            k += Character.toChars(Character.toLowerCase(cp), buffer, k)
                        }
                        String.fromCharArray(buffer)
                    } else {
                        inputValue
                    }

                if (!seen.contains(seenInputValue)) {
                    seen.add(seenInputValue)
                    val value = TestUtil.randomSimpleString(random())
                    map[inputValue] = if (value.isEmpty()) "a" else value
                }
            }
        }
        if (map.isEmpty()) {
            map["booked"] = "books"
        }
        val builder = StemmerOverrideFilter.Builder(ignoreCase)
        val entrySet = map.entries
        val input = StringBuilder()
        val output = mutableListOf<String>()
        for (entry in entrySet) {
            builder.add(entry.key, entry.value)
            if (random().nextBoolean() || output.isEmpty()) {
                input.append(entry.key).append(" ")
                output.add(entry.value)
            }
        }
        val tokenizer = WhitespaceTokenizer()
        tokenizer.setReader(StringReader(input.toString()))
        val stream: TokenStream =
            PorterStemFilter(StemmerOverrideFilter(tokenizer, builder.build()))
        assertTokenStreamContents(stream, output.toTypedArray())
    }

    @Test
    @Throws(IOException::class)
    fun testRandomRealisticKeyword() {
        val map = hashMapOf<String, String>()
        val numTerms = atLeast(50)
        for (i in 0 until numTerms) {
            val randomRealisticUnicodeString = TestUtil.randomRealisticUnicodeString(random())
            if (randomRealisticUnicodeString.isNotEmpty()) {
                val value = TestUtil.randomSimpleString(random())
                map[randomRealisticUnicodeString] = if (value.isEmpty()) "a" else value
            }
        }
        if (map.isEmpty()) {
            map["booked"] = "books"
        }
        val builder = StemmerOverrideFilter.Builder(false)
        val entrySet = map.entries
        for (entry in entrySet) {
            builder.add(entry.key, entry.value)
        }
        val build: StemmerOverrideMap = builder.build()
        for (entry in entrySet) {
            if (random().nextBoolean()) {
                val tokenizer = KeywordTokenizer()
                tokenizer.setReader(StringReader(entry.key))
                val stream: TokenStream = PorterStemFilter(StemmerOverrideFilter(tokenizer, build))
                assertTokenStreamContents(stream, arrayOf(entry.value))
            }
        }
    }
}
