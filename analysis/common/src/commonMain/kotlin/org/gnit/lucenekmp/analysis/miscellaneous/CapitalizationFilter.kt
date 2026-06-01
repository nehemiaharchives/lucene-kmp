package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Character

/**
 * A filter to apply normal capitalization rules to Tokens. It will make the first letter capital
 * and the rest lower case.
 *
 * <p>This filter is particularly useful to build nice looking facet parameters. This filter is not
 * appropriate if you intend to use a prefix query.
 */
class CapitalizationFilter(
    `in`: TokenStream,
    private val onlyFirstWord: Boolean = true,
    private val keep: CharArraySet? = null,
    private val forceFirstLetter: Boolean = true,
    private val okPrefix: Collection<CharArray>? = null,
    private val minWordLength: Int = 0,
    private val maxWordCount: Int = DEFAULT_MAX_WORD_COUNT,
    private val maxTokenLength: Int = DEFAULT_MAX_TOKEN_LENGTH
) : TokenFilter(`in`) {
    private val termAtt = addAttribute(CharTermAttribute::class)

    /**
     * Creates a CapitalizationFilter with the specified parameters.
     *
     * @param in input tokenstream
     * @param onlyFirstWord should each word be capitalized or all of the words?
     * @param keep a keep word list. Each word that should be kept separated by whitespace.
     * @param forceFirstLetter Force the first letter to be capitalized even if it is in the keep
     * list.
     * @param okPrefix do not change word capitalization if a word begins with something in this list.
     * @param minWordLength how long the word needs to be to get capitalization applied. If the
     * minWordLength is 3, "and" &gt; "And" but "or" stays "or".
     * @param maxWordCount if the token contains more then maxWordCount words, the capitalization is
     * assumed to be correct.
     * @param maxTokenLength ???
     */
    init {
        if (minWordLength < 0) {
            throw IllegalArgumentException("minWordLength must be greater than or equal to zero")
        }
        if (maxWordCount < 1) {
            throw IllegalArgumentException("maxWordCount must be greater than zero")
        }
        if (maxTokenLength < 1) {
            throw IllegalArgumentException("maxTokenLength must be greater than zero")
        }
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false

        val termBuffer = termAtt.buffer()
        val termBufferLength = termAtt.length
        var backup: CharArray? = null

        if (maxWordCount < DEFAULT_MAX_WORD_COUNT) {
            // make a backup in case we exceed the word count
            backup = CharArray(termBufferLength)
            termBuffer.copyInto(backup, 0, 0, termBufferLength)
        }

        if (termBufferLength < maxTokenLength) {
            var wordCount = 0

            var lastWordStart = 0
            var i = 0
            while (i < termBufferLength) {
                val c = termBuffer[i]
                if (c <= ' ' || c == '.') {
                    val len = i - lastWordStart
                    if (len > 0) {
                        processWord(termBuffer, lastWordStart, len, wordCount++)
                        lastWordStart = i + 1
                        i++
                    }
                }
                i++
            }

            // process the last word
            if (lastWordStart < termBufferLength) {
                processWord(termBuffer, lastWordStart, termBufferLength - lastWordStart, wordCount++)
            }

            if (wordCount > maxWordCount) {
                termAtt.copyBuffer(backup!!, 0, termBufferLength)
            }
        }

        return true
    }

    private fun processWord(buffer: CharArray, offset: Int, length: Int, wordCount: Int) {
        if (length < 1) {
            return
        }

        if (onlyFirstWord && wordCount > 0) {
            for (i in 0 until length) {
                buffer[offset + i] = Character.toLowerCase(buffer[offset + i].code).toChar()
            }
            return
        }

        if (keep != null && keep.contains(buffer, offset, length)) {
            if (wordCount == 0 && forceFirstLetter) {
                buffer[offset] = Character.toUpperCase(buffer[offset].code).toChar()
            }
            return
        }

        if (length < minWordLength) {
            return
        }

        if (okPrefix != null) {
            for (prefix in okPrefix) {
                // don't bother checking if the buffer length is less than the prefix
                if (length >= prefix.size) {
                    var match = true
                    for (i in prefix.indices) {
                        if (prefix[i] != buffer[offset + i]) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        return
                    }
                }
            }
        }

        // We know it has at least one character
        buffer[offset] = Character.toUpperCase(buffer[offset].code).toChar()

        for (i in 1 until length) {
            buffer[offset + i] = Character.toLowerCase(buffer[offset + i].code).toChar()
        }
    }

    companion object {
        const val DEFAULT_MAX_WORD_COUNT: Int = Int.MAX_VALUE
        const val DEFAULT_MAX_TOKEN_LENGTH: Int = Int.MAX_VALUE
    }
}
