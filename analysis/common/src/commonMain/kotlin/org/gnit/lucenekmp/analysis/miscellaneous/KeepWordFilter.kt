package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * A TokenFilter that only keeps tokens with text contained in the required words. This filter
 * behaves like the inverse of StopFilter.
 *
 * @since solr 1.3
 */
class KeepWordFilter(`in`: TokenStream, private val words: CharArraySet) : FilteringTokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /**
     * Create a new [KeepWordFilter].
     *
     * <p><b>NOTE</b>: The words set passed to this constructor will be directly used by this filter
     * and should not be modified.
     *
     * @param in the [TokenStream] to consume
     * @param words the words to keep
     */
    override fun accept(): Boolean {
        return words.contains(termAtt.buffer(), 0, termAtt.length)
    }
}
