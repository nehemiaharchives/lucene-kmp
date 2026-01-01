package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.ja.tokenattributes.PartOfSpeechAttribute
import org.gnit.lucenekmp.analysis.ja.tokenattributes.PartOfSpeechAttributeImpl
import okio.IOException

/** Removes tokens that match a set of part-of-speech tags. */
class JapanesePartOfSpeechStopFilter(
    input: TokenStream,
    private val stopTags: Set<String>
) : FilteringTokenFilter(input) {

    init {
        PartOfSpeechAttributeImpl.ensureRegistered()
        addAttributeImpl(PartOfSpeechAttributeImpl())
    }

    private val posAtt: PartOfSpeechAttribute = addAttribute(PartOfSpeechAttribute::class)

    @Throws(IOException::class)
    override fun accept(): Boolean {
        val pos = posAtt.getPartOfSpeech()
        return pos == null || !stopTags.contains(pos)
    }
}
