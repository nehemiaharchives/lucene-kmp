package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
/*import org.apache.lucene.util.IgnoreRandomChains*/
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.util.ArrayUtil

/**
 * Characters before the delimiter are the "token", the textual integer after is the term frequency.
 * To use this `TokenFilter` the field must be indexed with [ ][IndexOptions.DOCS_AND_FREQS] but no positions or offsets.
 *
 *
 * For example, if the delimiter is '|', then for the string "foo|5", "foo" is the token and "5"
 * is a term frequency. If there is no delimiter, the TokenFilter does not modify the term
 * frequency.
 *
 *
 * Note make sure your Tokenizer doesn't split on the delimiter, or this won't work
 */
/*@IgnoreRandomChains(reason = "requires a special encoded token value, so it may fail with random data")*/
class DelimitedTermFrequencyTokenFilter(
    input: TokenStream,
    private val delimiter: Char = DEFAULT_DELIMITER
) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val tfAtt: TermFrequencyAttribute = addAttribute(TermFrequencyAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val buffer: CharArray = termAtt.buffer()
            val length: Int = termAtt.length
            var i = 0
            while (i < length) {
                if (buffer[i] == delimiter) {
                    termAtt.setLength(i) // simply set a new length
                    i++
                    tfAtt.termFrequency = ArrayUtil.parseInt(buffer, i, length - i)
                    return true
                }
                i++
            }
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_DELIMITER: Char = '|'
    }
}
