package org.gnit.lucenekmp.analysis.payloads

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute

/**
 * Characters before the delimiter are the "token", those after are the payload.
 *
 * For example, if the delimiter is '|', then for the string "foo|bar", foo is the token and
 * "bar" is a payload.
 *
 * Note, you can also include a [PayloadEncoder] to
 * convert the payload in an appropriate way (from characters to bytes).
 *
 * Note make sure your Tokenizer doesn't split on the delimiter, or this won't work
 *
 * @see PayloadEncoder
 */
class DelimitedPayloadTokenFilter(input: TokenStream, private val delimiter: Char, private val encoder: PayloadEncoder) :
    TokenFilter(input) {
    companion object {
        const val DEFAULT_DELIMITER = '|'
    }

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val payAtt: PayloadAttribute = addAttribute(PayloadAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val buffer = termAtt.buffer()
            val length = termAtt.length
            for (i in 0 until length) {
                if (buffer[i] == delimiter) {
                    payAtt.payload = encoder.encode(buffer, i + 1, length - (i + 1))
                    termAtt.setLength(i)
                    return true
                }
            }
            payAtt.payload = null
            return true
        } else {
            return false
        }
    }
}
