package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.ko.KoreanTokenizer.DecompoundMode
import org.gnit.lucenekmp.analysis.ko.dict.UserDictionary

/**
 * Analyzer for Korean that uses morphological analysis.
 */
class KoreanAnalyzer(
    private val userDict: UserDictionary? = null,
    private val mode: DecompoundMode = KoreanTokenizer.DEFAULT_DECOMPOUND,
    private val stopTags: Set<POS.Tag> = KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
    private val outputUnknownUnigrams: Boolean = false
) : Analyzer() {

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer: Tokenizer = KoreanTokenizer(TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY, userDict, mode, outputUnknownUnigrams)
        var stream: TokenStream = KoreanPartOfSpeechStopFilter(tokenizer, stopTags)
        stream = KoreanReadingFormFilter(stream)
        stream = LowerCaseFilter(stream)
        return TokenStreamComponents(tokenizer, stream)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }
}
