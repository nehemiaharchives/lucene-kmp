package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.ja.tokenattributes.PartOfSpeechAttribute

/** Removes tokens that match a set of part-of-speech tags. */
class JapanesePartOfSpeechStopFilter(
    input: TokenStream,
    private val stopTags: Set<String>
) : TokenFilter(input) {

    private val posAtt: PartOfSpeechAttribute = addAttribute(PartOfSpeechAttribute::class)

    override fun incrementToken(): Boolean {
        while (input.incrementToken()) {
            val pos = posAtt.getPartOfSpeech()
            if (pos == null || !stopTags.contains(pos)) {
                return true
            }
        }
        return false
    }
}
