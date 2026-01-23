package org.gnit.lucenekmp.analysis.ja.ct

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.charfilter.MappingCharFilter
import org.gnit.lucenekmp.analysis.charfilter.NormalizeCharMap
import org.gnit.lucenekmp.analysis.ja.JapaneseAnalyzer
import org.gnit.lucenekmp.analysis.ja.JapaneseBaseFormFilter
import org.gnit.lucenekmp.analysis.ja.JapaneseKatakanaStemFilter
import org.gnit.lucenekmp.analysis.ja.JapanesePartOfSpeechStopFilter
import org.gnit.lucenekmp.analysis.ja.JapaneseTokenizer
import org.gnit.lucenekmp.analysis.ja.dict.UserDictionary
import org.gnit.lucenekmp.analysis.cjk.CJKWidthCharFilter
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Bible-specific Japanese analyzer that normalizes biblical terms such as
 * "キリスト・イエス" to the canonical "イエス・キリスト" form.
 */
class BibleJapaneseAnalyzer : StopwordAnalyzerBase {
    private val mode: JapaneseTokenizer.Mode
    private val stopTags: Set<String>
    private val userDict: UserDictionary?

    constructor() : this(null, JapaneseTokenizer.DEFAULT_MODE, JapaneseAnalyzer.getDefaultStopSet(), JapaneseAnalyzer.getDefaultStopTags())

    constructor(
        userDict: UserDictionary?,
        mode: JapaneseTokenizer.Mode,
        stopwords: CharArraySet,
        stopTags: Set<String>
    ) : super(stopwords) {
        this.userDict = userDict
        this.mode = mode
        this.stopTags = stopTags
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer: Tokenizer = JapaneseTokenizer(userDict, true,
            discardCompoundToken = true,
            mode = mode
        )
        var stream: TokenStream = JapaneseBaseFormFilter(tokenizer)
        stream = JapanesePartOfSpeechStopFilter(stream, stopTags)
        stream = StopFilter(stream, stopwords)
        stream = JapaneseKatakanaStemFilter(stream)
        stream = LowerCaseFilter(stream)
        return TokenStreamComponents(tokenizer, stream)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    override fun initReader(fieldName: String, reader: Reader): Reader {
        val widthNormalized: Reader = CJKWidthCharFilter(reader)
        return MappingCharFilter(BIBLE_TERM_MAP, widthNormalized)
    }

    override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
        val widthNormalized: Reader = CJKWidthCharFilter(reader)
        return MappingCharFilter(BIBLE_TERM_MAP, widthNormalized)
    }

    companion object {
        private val BIBLE_TERM_MAPPINGS: List<Pair<String, String>> = listOf(
            "キリスト・イエス" to "イエス・キリスト",
            "シモン・バルヨナ" to "バルヨナ・シモン",
            "イスカリオテ・ユダ" to "イスカリオテのユダ",
            "天の御国" to "天の国",
            "聖なる所" to "聖所",
        )

        private val BIBLE_TERM_MAP: NormalizeCharMap = NormalizeCharMap.Builder().apply {
            for ((from, to) in BIBLE_TERM_MAPPINGS) {
                add(from, to)
            }
        }.build()
    }
}
