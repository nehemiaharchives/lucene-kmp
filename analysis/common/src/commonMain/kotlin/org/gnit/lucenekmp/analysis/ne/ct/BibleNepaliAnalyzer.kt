package org.gnit.lucenekmp.analysis.ne.ct

import okio.IOException
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
import org.gnit.lucenekmp.analysis.ne.NepaliAnalyzer
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.tartarus.snowball.ext.NepaliStemmer

/**
 * Bible-specific Nepali analyzer to normalize ZWJ/ZWNJ in Nepali scripture text.
 */
class BibleNepaliAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    constructor() : this(NepaliAnalyzer.getDefaultStopSet())

    override fun initReader(fieldName: String, reader: Reader): Reader {
        return MappingCharFilter(ZWJ_MAP, reader)
    }

    override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
        return MappingCharFilter(ZWJ_MAP, reader)
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = IndicNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = SnowballFilter(result, NepaliStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        return result
    }

    companion object {
        private val ZWJ_MAP: NormalizeCharMap = NormalizeCharMap.Builder().apply {
            add("\u200C", "")
            add("\u200D", "")
        }.build()
    }
}
