package org.gnit.lucenekmp.analysis.uk

import morfologik.stemming.Dictionary
import okio.Buffer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.charfilter.MappingCharFilter
import org.gnit.lucenekmp.analysis.charfilter.NormalizeCharMap
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.morfologik.MorfologikFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader

/** A dictionary-based Analyzer for Ukrainian. */
class UkrainianMorfologikAnalyzer : StopwordAnalyzerBase {
    private val dictionary: Dictionary
    private val stemExclusionSet: CharArraySet

    constructor() : this(DefaultResourcesHolder.defaultStopSet)

    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
        this.dictionary = DefaultResourcesHolder.defaultDictionary
    }

    override fun initReader(fieldName: String, reader: Reader): Reader {
        return MappingCharFilter(NORMALIZER_MAP, reader)
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = MorfologikFilter(result, dictionary)
        return TokenStreamComponents(source, result)
    }

    companion object {
        private val NORMALIZER_MAP: NormalizeCharMap = NormalizeCharMap.Builder().apply {
            // different apostrophes
            add("\u2019", "'")
            add("\u2018", "'")
            add("\u02BC", "'")
            add("`", "'")
            add("´", "'")
            // ignored characters
            add("\u0301", "")
            add("\u00AD", "")
            add("ґ", "г")
            add("Ґ", "Г")
        }.build()

        private val defaultResources: DefaultResources by lazy(LazyThreadSafetyMode.NONE) {
            val wordList = WordlistLoader.getSnowballWordSet(StringReader(UKRAINIAN_STOPWORD_DATA))
            val dictStream = OkioSourceInputStream(Buffer().apply { write(ukrainianDictData) })
            try {
                val infoStream = OkioSourceInputStream(Buffer().apply { write(ukrainianInfoData) })
                try {
                    val dict = Dictionary.read(dictStream, infoStream)
                    DefaultResources(wordList, dict)
                } finally {
                    infoStream.close()
                }
            } finally {
                dictStream.close()
            }
        }

        /** Returns the default stopword set for this analyzer. */
        fun getDefaultStopwords(): CharArraySet {
            return CharArraySet.unmodifiableSet(defaultResources.stopSet)
        }

        private data class DefaultResources(val stopSet: CharArraySet, val dictionary: Dictionary)

        private object DefaultResourcesHolder {
            val defaultStopSet: CharArraySet = defaultResources.stopSet
            val defaultDictionary: Dictionary = defaultResources.dictionary
        }
    }
}
