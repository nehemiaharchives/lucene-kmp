package org.gnit.lucenekmp.analysis.hi.ct

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilter
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.hi.HindiAnalyzer
import org.gnit.lucenekmp.analysis.hi.HindiNormalizationFilter
import org.gnit.lucenekmp.analysis.hi.HindiStemFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer

/** Analyzer for Hindi bible text with Jesus/Christ form preservation. */
class BibleHindiAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the default stop words. */
    constructor() : this(HindiAnalyzer.getDefaultStopSet())

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the given stop words and a stemming exclusion set. */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    private fun buildKeywordExclusionSet(): CharArraySet {
        val keywordSet = CharArraySet(stemExclusionSet.size + BIBLE_NAME_FORMS.size, true)
        for (item in stemExclusionSet) {
            keywordSet.add(item)
        }
        for (form in BIBLE_NAME_FORMS) {
            keywordSet.add(form)
        }
        return keywordSet
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        result = SetKeywordMarkerFilter(result, buildKeywordExclusionSet())
        result = IndicNormalizationFilter(result)
        result = HindiNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = HindiStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = HindiNormalizationFilter(result)
        return result
    }

    companion object {
        private val BIBLE_NAME_FORMS: Array<String> = arrayOf(
            "यीशु",
            "मसीह"
        )
    }
}
