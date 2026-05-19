package org.gnit.lucenekmp.analysis.ru.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.ru.RussianAnalyzer
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.tartarus.snowball.ext.RussianStemmer

/** Analyzer for Russian bible text with sensitive Bible-name case-form preservation. */
class BibleRussianAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    constructor() : this(RussianAnalyzer.getDefaultStopSet())

    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        result = BibleRussianNameFormFilter(result)
        result = SetKeywordMarkerFilter(result, buildBibleNameExclusionSet())
        result = SnowballFilter(result, RussianStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    private fun buildBibleNameExclusionSet(): CharArraySet {
        val preservedForms = BIBLE_NAME_FORMS.filter { it.action == BibleNameAction.PRESERVE_EXACT }
        val keywordSet = CharArraySet(stemExclusionSet.size + preservedForms.size * 2, true)
        for (item in stemExclusionSet) {
            keywordSet.add(item)
        }
        for (form in preservedForms) {
            keywordSet.add(form.term)
            keywordSet.add(form.canonicalTerm)
        }
        return keywordSet
    }

    companion object {
        private val BIBLE_NAME_FORMS: Array<BibleNameForm> = arrayOf(
            BibleNameForm("иисус", "Jesus", "nominative", "иисус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("иисуса", "Jesus", "genitive/accusative", "иисус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("иисусу", "Jesus", "dative", "иисус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("иисусом", "Jesus", "instrumental", "иисус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("иисусе", "Jesus", "prepositional/vocative", "иисус", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христос", "Christ", "nominative", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христа", "Christ", "genitive/accusative", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христу", "Christ", "dative", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христом", "Christ", "instrumental", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христе", "Christ", "prepositional/vocative", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христова", "Christ", "genitive adjective, feminine/neuter", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христов", "Christ", "genitive adjective, masculine", "христос", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христовы", "Christ", "genitive adjective, plural", "христос", BibleNameAction.PRESERVE_EXACT)
        )

        internal val CANONICAL_BY_FORM: Map<String, String> = BIBLE_NAME_FORMS
            .associate { it.term to it.canonicalTerm }

        private val JESUS_OR_CHRIST_FORMS: Set<String> = BIBLE_NAME_FORMS
            .filter { it.canonicalName == "Jesus" || it.canonicalName == "Christ" }
            .map { it.term }
            .toSet()

        private val JOSHUA_CONTEXT_FORMS: Set<String> = setOf(
            "навин",
            "навина",
            "навину",
            "навином",
            "навине"
        )

        fun requiresNewTestamentScope(text: String): Boolean {
            val tokens = wordTokens(text)
            if (tokens.any { it in JOSHUA_CONTEXT_FORMS }) {
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

private class BibleRussianNameFormFilter(input: TokenStream) : TokenFilter(input) {
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
        val canonical = BibleRussianAnalyzer.CANONICAL_BY_FORM[term]
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
