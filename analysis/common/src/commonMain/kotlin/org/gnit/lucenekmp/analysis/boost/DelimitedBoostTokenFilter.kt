package org.gnit.lucenekmp.analysis.boost

/*import org.apache.lucene.util.IgnoreRandomChains*/
import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.search.BoostAttribute

/**
 * Characters before the delimiter are the "token", those after are the boost.
 *
 *
 * For example, if the delimiter is '|', then for the string "foo|0.7", foo is the token and 0.7
 * is the boost.
 *
 *
 * Note make sure your Tokenizer doesn't split on the delimiter, or this won't work
 */
/*@IgnoreRandomChains(reason = "requires a special encoded token value, so it may fail with random data")*/
class DelimitedBoostTokenFilter(
    input: TokenStream,
    private val delimiter: Char
) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val boostAtt: BoostAttribute = addAttribute(BoostAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val buffer: CharArray = termAtt.buffer()
            val length: Int = termAtt.length
            for (i in 0..<length) {
                if (buffer[i] == delimiter) {
                    val boost = String.fromCharArray(buffer, i + 1, (length - (i + 1))).toFloat()
                    boostAtt.boost = boost
                    termAtt.setLength(i)
                    return true
                }
            }
            return true
        } else {
            return false
        }
    }
}
