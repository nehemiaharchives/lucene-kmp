package org.gnit.lucenekmp.analysis.mr.ct

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilter
import org.gnit.lucenekmp.analysis.charfilter.MappingCharFilter
import org.gnit.lucenekmp.analysis.charfilter.NormalizeCharMap
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.mr.MarathiAnalyzer
import org.gnit.lucenekmp.analysis.mr.MarathiNormalizationFilter
import org.gnit.lucenekmp.analysis.mr.MarathiStemFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Bible-specific Marathi analyzer that normalizes biblical terms such as
 * "ख्रिस्त येशू" to the canonical "येशू ख्रिस्त" form.
 */
class BibleMarathiAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    constructor() : this(MarathiAnalyzer.getDefaultStopSet())

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = IndicNormalizationFilter(result)
        result = MarathiNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = MarathiStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = MarathiNormalizationFilter(result)
        return result
    }

    override fun initReader(fieldName: String, reader: Reader): Reader {
        return MappingCharFilter(BIBLE_TERM_MAP, reader)
    }

    override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
        return MappingCharFilter(BIBLE_TERM_MAP, reader)
    }

    companion object {
        private val BIBLE_TERM_MAPPINGS: List<Pair<String, String>> = listOf(
            "ख्रिस्त येशू" to "येशू ख्रिस्त",
            "ख्रिस्ताचा" to "ख्रिस्त"
        )
        private val BIBLE_TERM_MAP: NormalizeCharMap = NormalizeCharMap.Builder().apply {
            for ((from, to) in BIBLE_TERM_MAPPINGS) {
                add(from, to)
            }
        }.build()
    }
}
