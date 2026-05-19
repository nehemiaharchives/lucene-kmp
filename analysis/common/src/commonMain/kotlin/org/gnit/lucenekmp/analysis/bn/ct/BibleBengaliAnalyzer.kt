package org.gnit.lucenekmp.analysis.bn.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilter
import org.gnit.lucenekmp.analysis.bn.BengaliAnalyzer
import org.gnit.lucenekmp.analysis.bn.BengaliNormalizationFilter
import org.gnit.lucenekmp.analysis.bn.BengaliStemFilter
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/** Analyzer for Bengali bible text with Jesus/Christ form canonicalization. */
class BibleBengaliAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the default stop words. */
    constructor() : this(BengaliAnalyzer.getDefaultStopSet())

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the given stop words and a stemming exclusion set. */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = BengaliNormalizationFilter(result)
        result = BibleBengaliJesusChristFilter(result, emitOriginal = true)
        result = StopFilter(result, stopwords)
        result = BengaliStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = BengaliNormalizationFilter(result)
        result = BibleBengaliJesusChristFilter(result, emitOriginal = false)
        return result
    }
}

private class BibleBengaliJesusChristFilter(
    input: TokenStream,
    private val emitOriginal: Boolean
) : TokenFilter(input) {
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

        val buffer = termAtt.buffer()
        val length = termAtt.length
        val canonical = canonicalForm(buffer, length)
        if (canonical != null) {
            keywordAtt.isKeyword = true
            if (emitOriginal) {
                if (!matches(buffer, length, canonical)) {
                    pendingState = captureState()
                    pendingCanonical = canonical
                }
            } else {
                termAtt.copyBuffer(canonical, 0, canonical.size)
            }
        }

        return true
    }

    override fun reset() {
        super.reset()
        pendingState = null
        pendingCanonical = null
    }

    private fun canonicalForm(buffer: CharArray, length: Int): CharArray? {
        return when {
            matches(buffer, length, JESUS_NOMINATIVE) ||
                matches(buffer, length, JESUS_GENITIVE) ||
                matches(buffer, length, JESUS_OBJECT) -> JESUS_CANONICAL
            matches(buffer, length, CHRIST_NOMINATIVE) ||
                matches(buffer, length, CHRIST_GENITIVE) ||
                matches(buffer, length, CHRIST_OBJECT) -> CHRIST_CANONICAL
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

private val JESUS_NOMINATIVE = "যিসু".toCharArray()
private val JESUS_GENITIVE = "যিসুর".toCharArray()
private val JESUS_OBJECT = "যিসুকে".toCharArray()
private val JESUS_CANONICAL = "যিসু".toCharArray()

private val CHRIST_NOMINATIVE = "খ্রিস্ট".toCharArray()
private val CHRIST_GENITIVE = "খ্রিস্টের".toCharArray()
private val CHRIST_OBJECT = "খ্রিস্টকে".toCharArray()
private val CHRIST_CANONICAL = "খ্রিস্ট".toCharArray()
