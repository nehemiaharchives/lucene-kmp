package org.gnit.lucenekmp.analysis.uk.ct

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
import org.gnit.lucenekmp.analysis.uk.UKRAINIAN_STOPWORD_DATA
import org.gnit.lucenekmp.analysis.uk.ukrainianDictData
import org.gnit.lucenekmp.analysis.uk.ukrainianInfoData
import org.gnit.lucenekmp.analysis.uk.UkrainianMorfologikAnalyzer
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader

/** Analyzer for Ukrainian bible text with Bible-name preservation and NT scoping. */
class BibleUkrainianAnalyzer : StopwordAnalyzerBase {
    private val dictionary: Dictionary
    private val stemExclusionSet: CharArraySet

    constructor() : this(UkrainianMorfologikAnalyzer.getDefaultStopwords())

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
        result = SetKeywordMarkerFilter(result, buildBibleNameExclusionSet())
        result = MorfologikFilter(result, dictionary)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    private fun buildBibleNameExclusionSet(): CharArraySet {
        val keywordSet = CharArraySet(stemExclusionSet.size + BIBLE_NAME_FORMS.size, true)
        for (item in stemExclusionSet) {
            keywordSet.add(item)
        }
        for (form in BIBLE_NAME_FORMS) {
            keywordSet.add(form)
        }
        return keywordSet
    }

    companion object {
        private val NORMALIZER_MAP: NormalizeCharMap = NormalizeCharMap.Builder().apply {
            add("\u2019", "'")
            add("\u2018", "'")
            add("\u02BC", "'")
            add("`", "'")
            add("´", "'")
            add("\u0301", "")
            add("\u00AD", "")
            add("ґ", "г")
            add("Ґ", "Г")
        }.build()

        private val BIBLE_NAME_FORMS: Array<String> = arrayOf(
            "ісус",
            "ісуса",
            "ісусу",
            "ісусом",
            "ісусі",
            "ісусов",
            "ісусового",
            "ісусовому",
            "христос",
            "христа",
            "христу",
            "христом",
            "христі",
            "христов",
            "христового",
            "христовому"
        )

        private val JESUS_OR_CHRIST_FORMS: Set<String> = BIBLE_NAME_FORMS.toSet()
        private val JOSHUA_CONTEXT_PREFIX = "навин"

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

        fun requiresNewTestamentScope(text: String): Boolean {
            val tokens = wordTokens(text)
            if (tokens.any { it.startsWith(JOSHUA_CONTEXT_PREFIX) }) {
                return false
            }
            return tokens.any { it in JESUS_OR_CHRIST_FORMS }
        }

        private fun wordTokens(text: String): List<String> {
            val tokens = ArrayList<String>()
            val current = StringBuilder()
            for (ch in text.lowercase()) {
                if (ch.isLetter()) {
                    current.append(ch)
                } else if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            }
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
            }
            return tokens
        }

        private data class DefaultResources(val stopSet: CharArraySet, val dictionary: Dictionary)

        private object DefaultResourcesHolder {
            val defaultStopSet: CharArraySet = defaultResources.stopSet
            val defaultDictionary: Dictionary = defaultResources.dictionary
        }
    }
}
