package org.gnit.lucenekmp.analysis.ru.ct

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.ru.RussianAnalyzer
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
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
        result = SetKeywordMarkerFilter(result, buildBibleNameExclusionSet())
        result = SnowballFilter(result, RussianStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    private fun buildBibleNameExclusionSet(): CharArraySet {
        val preservedForms = BIBLE_NAME_FORMS.filter { it.action == BibleNameAction.PRESERVE_EXACT }
        val keywordSet = CharArraySet(stemExclusionSet.size + preservedForms.size, true)
        for (item in stemExclusionSet) {
            keywordSet.add(item)
        }
        for (form in preservedForms) {
            keywordSet.add(form.term)
        }
        return keywordSet
    }

    companion object {
        private val BIBLE_NAME_FORMS: Array<BibleNameForm> = arrayOf(
            BibleNameForm("иисус", "Jesus", "nominative", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("иисуса", "Jesus", "genitive/accusative", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("иисусу", "Jesus", "dative", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("иисусом", "Jesus", "instrumental", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("иисусе", "Jesus", "prepositional/vocative", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христос", "Christ", "nominative", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христа", "Christ", "genitive/accusative", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христу", "Christ", "dative", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христом", "Christ", "instrumental", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христе", "Christ", "prepositional/vocative", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христова", "Christ", "genitive adjective, feminine/neuter", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христов", "Christ", "genitive adjective, masculine", BibleNameAction.PRESERVE_EXACT),
            BibleNameForm("христовы", "Christ", "genitive adjective, plural", BibleNameAction.PRESERVE_EXACT)
        )

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
    val action: BibleNameAction
)

private enum class BibleNameAction {
    PRESERVE_EXACT
}
