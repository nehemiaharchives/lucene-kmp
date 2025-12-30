package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [KoreanPartOfSpeechStopFilter]. */
class KoreanPartOfSpeechStopFilterFactory : TokenFilterFactory {
    private lateinit var stopTags: Set<POS.Tag>

    constructor(args: MutableMap<String, String>) : super(args) {
        val stopTagStr = getSet(args, "tags")
        stopTags = if (stopTagStr == null) {
            KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS
        } else {
            stopTagStr.map { POS.resolveTag(it) }.toSet()
        }
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() {
        throw defaultCtorException()
    }

    override fun create(stream: TokenStream): TokenStream = KoreanPartOfSpeechStopFilter(stream, stopTags)

    companion object {
        const val NAME: String = "koreanPartOfSpeechStop"
    }
}
