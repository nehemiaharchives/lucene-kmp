package org.gnit.lucenekmp.analysis.uk.ct

import morfologik.stemming.Dictionary
import okio.IOException
import okio.Buffer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
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
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
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
        result = BibleUkrainianNameFormFilter(result)
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
        val keywordSet = CharArraySet(stemExclusionSet.size + BIBLE_NAME_FORMS.size * 2, true)
        for (item in stemExclusionSet) {
            keywordSet.add(item)
        }
        for (form in BIBLE_NAME_FORMS) {
            keywordSet.add(form.term)
            keywordSet.add(form.canonicalTerm)
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

        private val BIBLE_NAME_FORMS: Array<BibleNameForm> = arrayOf(
            BibleNameForm("ісус", "Jesus", "nominative", "ісус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("ісуса", "Jesus", "genitive/accusative", "ісус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("ісусу", "Jesus", "dative", "ісус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("ісусом", "Jesus", "instrumental", "ісус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("ісусі", "Jesus", "prepositional", "ісус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("ісусов", "Jesus", "genitive adjective, masculine", "ісус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("ісусового", "Jesus", "genitive adjective, neuter", "ісус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("ісусовому", "Jesus", "dative adjective, neuter", "ісус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христос", "Christ", "nominative", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христа", "Christ", "genitive/accusative", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христу", "Christ", "dative", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христом", "Christ", "instrumental", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христі", "Christ", "prepositional", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христов", "Christ", "genitive adjective, masculine", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христового", "Christ", "genitive adjective, neuter", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христовому", "Christ", "dative adjective, neuter", "христос", BibleNameAction.PRESERVE_EXACT)
        )

        private val JESUS_OR_CHRIST_FORMS: Set<String> = BIBLE_NAME_FORMS.map { it.term }.toSet()
        internal val CANONICAL_BY_FORM: Map<String, String> = BIBLE_NAME_FORMS.associate { it.term to it.canonicalTerm }
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

private data class BibleNameForm(
    val term: String,
    val canonicalName: String,
    val grammar: String,
    val canonicalTerm: String,
    val action: BibleNameAction
)

private enum class BibleNameAction {
    PRESERVE_EXACT
}

private class BibleUkrainianNameFormFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private var pendingState: State? = null
    private var pendingCanonical: CharArray? = null

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (pendingState != null) {
            restoreState(pendingState!!)
            pendingState = null
            val canonical = pendingCanonical
            pendingCanonical = null
            if (canonical != null) {
                posIncAtt.setPositionIncrement(0)
                termAtt.copyBuffer(canonical, 0, canonical.size)
                keywordAtt.isKeyword = true
            }
            return true
        }

        if (!input.incrementToken()) {
            return false
        }

        val term = termAtt.toString()
        val canonical = BibleUkrainianAnalyzer.CANONICAL_BY_FORM[term]
        if (canonical != null) {
            keywordAtt.isKeyword = true
            if (canonical != term) {
                pendingState = captureState()
                pendingCanonical = canonical.toCharArray()
            }
        }
        return true
    }

    override fun reset() {
        super.reset()
        pendingState = null
        pendingCanonical = null
    }
}
