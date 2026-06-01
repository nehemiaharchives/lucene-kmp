package org.gnit.lucenekmp.analysis.pattern

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * This tokenizer uses regex pattern matching to construct distinct tokens for the input stream. It
 * takes two arguments: "pattern" and "group".
 *
 * <ul>
 *   <li>"pattern" is the regular expression.
 *   <li>"group" says which group to extract into tokens.
 * </ul>
 *
 * <p>group=-1 (the default) is equivalent to "split". In this case, the tokens will be equivalent
 * to the output from (without empty tokens): [String.split]
 *
 * <p>Using group >= 0 selects the matching group as the token. For example, if you have:<br></br>
 *
 * <pre>
 *  pattern = \'([^\']+)\'
 *  group = 0
 *  input = aaa 'bbb' 'ccc'
 * </pre>
 *
 * the output will be two tokens: 'bbb' and 'ccc' (including the ' marks). With the same input but
 * using group=1, the output would be: bbb and ccc (no ' marks)
 *
 * <p>NOTE: This Tokenizer does not output tokens that are of zero length.
 *
 * @see Regex
 */
class PatternTokenizer : Tokenizer {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    private val str = StringBuilder()
    private var index = 0

    private val group: Int
    private val pattern: Regex
    private var matcher: MatchResult? = null

    /** creates a new PatternTokenizer returning tokens from group (-1 for split functionality) */
    constructor(pattern: Regex, group: Int) : this(DEFAULT_TOKEN_ATTRIBUTE_FACTORY, pattern, group)

    /** creates a new PatternTokenizer returning tokens from group (-1 for split functionality) */
    constructor(factory: AttributeFactory, pattern: Regex, group: Int) : super(factory) {
        this.group = group
        this.pattern = pattern
    }

    override fun incrementToken(): Boolean {
        if (index >= str.length) return false
        clearAttributes()
        if (group >= 0) {
            // match a specific group
            while (matcher != null) {
                val match = matcher!!
                matcher = match.next()
                if (group != 0) {
                    throw UnsupportedOperationException("PatternTokenizer only supports group 0 in common code")
                }
                val groupRange = match.range
                if (groupRange.isEmpty()) {
                    continue
                }
                val start = groupRange.start
                val endIndex = groupRange.endInclusive + 1
                if (start == endIndex) continue
                termAtt.setEmpty()
                termAtt.append(str, start, endIndex)
                offsetAtt.setOffset(correctOffset(start), correctOffset(endIndex))
                index = start
                return true
            }

            index = Int.MAX_VALUE // mark exhausted
            return false
        } else {
            // String.split() functionality
            while (matcher != null) {
                val match = matcher!!
                val start = match.range.first
                if (start - index > 0) {
                    // found a non-zero-length token
                    termAtt.setEmpty()
                    termAtt.append(str, index, start)
                    offsetAtt.setOffset(correctOffset(index), correctOffset(start))
                    index = match.range.last + 1
                    matcher = match.next()
                    return true
                }

                index = match.range.last + 1
                matcher = match.next()
            }

            if (str.length - index == 0) {
                index = Int.MAX_VALUE // mark exhausted
                return false
            }

            termAtt.setEmpty()
            termAtt.append(str, index, str.length)
            offsetAtt.setOffset(correctOffset(index), correctOffset(str.length))
            index = Int.MAX_VALUE // mark exhausted
            return true
        }
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        val ofs = correctOffset(str.length)
        offsetAtt.setOffset(ofs, ofs)
    }

    override fun close() {
        try {
            super.close()
        } finally {
            str.setLength(0)
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        fillBuffer(input)
        matcher = pattern.find(str)
        index = 0
    }

    // TODO: we should see if we can make this tokenizer work without reading
    // the entire document into RAM, perhaps with Matcher.hitEnd/requireEnd ?
    private val buffer = CharArray(8192)

    @Throws(IOException::class)
    private fun fillBuffer(input: Reader) {
        var len: Int
        str.setLength(0)
        while (input.read(buffer, 0, buffer.size).also { len = it } > 0) {
            str.appendRange(buffer, 0, len)
        }
    }
}
