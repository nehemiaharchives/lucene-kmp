package org.gnit.lucenekmp.analysis.te.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilter
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.te.TeluguAnalyzer
import org.gnit.lucenekmp.analysis.te.TeluguNormalizationFilter
import org.gnit.lucenekmp.analysis.te.TeluguStemFilter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/** Analyzer for Telugu bible text with Jesus/Christ inflection normalization. */
class BibleTeluguAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the default stop words. */
    constructor() : this(TeluguAnalyzer.getDefaultStopSet())

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the given stop words and a stemming exclusion set. */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    private fun buildKeywordExclusionSet(): CharArraySet {
        val keywordSet = CharArraySet(stemExclusionSet.size + 3, true)
        for (item in stemExclusionSet) {
            keywordSet.add(item)
        }
        keywordSet.add(JESUS_TEXT)
        keywordSet.add(CHRIST_TEXT)
        keywordSet.add(CHRIST_LOCATIVE_TEXT)
        return keywordSet
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = DecimalDigitFilter(source)
        result = IndicNormalizationFilter(result)
        result = TeluguNormalizationFilter(result)
        result = BibleTeluguJesusChristFilter(result, emitOriginal = true)
        result = SetKeywordMarkerFilter(result, buildKeywordExclusionSet())
        result = StopFilter(result, stopwords)
        result = TeluguStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = DecimalDigitFilter(`in`)
        result = IndicNormalizationFilter(result)
        result = TeluguNormalizationFilter(result)
        result = BibleTeluguJesusChristFilter(result, emitOriginal = false)
        return result
    }
}

private class BibleTeluguJesusChristFilter(
    input: TokenStream,
    private val emitOriginal: Boolean
) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private var pendingState: State? = null
    private var pendingTerm: CharArray? = null

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (pendingState != null) {
            restoreState(pendingState!!)
            pendingState = null
            val pending = pendingTerm
            pendingTerm = null
            if (pending != null) {
                posIncAtt.setPositionIncrement(0)
                termAtt.copyBuffer(pending, 0, pending.size)
                keywordAtt.isKeyword = true
            }
            return true
        }
        if (!input.incrementToken()) {
            return false
        }

        val buffer = termAtt.buffer()
        val length = termAtt.length
        val normalized = normalizedForm(buffer, length)
        if (normalized != null) {
            keywordAtt.isKeyword = true
            if (emitOriginal) {
                if (!matches(buffer, length, normalized)) {
                    pendingState = captureState()
                    pendingTerm = normalized
                }
            } else {
                termAtt.copyBuffer(normalized, 0, normalized.size)
            }
        }

        return true
    }

    override fun reset() {
        super.reset()
        pendingState = null
        pendingTerm = null
    }

    private fun normalizedForm(buffer: CharArray, length: Int): CharArray? {
        return when {
            matches(buffer, length, JESUS) -> JESUS
            matches(buffer, length, CHRIST) || matches(buffer, length, CHRIST_LOCATIVE) -> CHRIST
            else -> null
        }
    }

    private fun matches(buffer: CharArray, length: Int, target: CharArray): Boolean {
        if (length != target.size) {
            return false
        }
        for (i in 0 until length) {
            if (buffer[i] != target[i]) {
                return false
            }
        }
        return true
    }
}

private const val JESUS_TEXT = "యెసు"
private const val CHRIST_TEXT = "క్రిస్త"
private const val CHRIST_LOCATIVE_TEXT = "క్రిస్తులొ"
private val JESUS = JESUS_TEXT.toCharArray()
private val CHRIST = CHRIST_TEXT.toCharArray()
private val CHRIST_LOCATIVE = CHRIST_LOCATIVE_TEXT.toCharArray()
