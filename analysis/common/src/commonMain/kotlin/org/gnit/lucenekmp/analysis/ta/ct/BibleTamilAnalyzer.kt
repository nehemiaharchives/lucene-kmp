package org.gnit.lucenekmp.analysis.ta.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilter
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.ta.TamilAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.tartarus.snowball.ext.TamilStemmer

private const val JESUS_CHRIST_TEXT = "இயேசுகிறிஸ்து"

/** Analyzer for Tamil bible text with Jesus Christ form normalization. */
class BibleTamilAnalyzer : StopwordAnalyzerBase {
	private val stemExclusionSet: CharArraySet

	/** Builds an analyzer with the default stop words. */
	constructor() : this(TamilAnalyzer.getDefaultStopSet())

	/** Builds an analyzer with the given stop words. */
	constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

	/** Builds an analyzer with the given stop words and a stemming exclusion set. */
	constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
		this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
	}

	private fun buildKeywordExclusionSet(): CharArraySet {
		val keywordSet = CharArraySet(stemExclusionSet.size + 1, true)
		for (item in stemExclusionSet) {
			keywordSet.add(item)
		}
		keywordSet.add(JESUS_CHRIST_TEXT)
		return keywordSet
	}

	override fun createComponents(fieldName: String): TokenStreamComponents {
		val source: Tokenizer = StandardTokenizer()
		var result: TokenStream = LowerCaseFilter(source)
		result = DecimalDigitFilter(result)
		if (!stemExclusionSet.isEmpty()) {
			result = SetKeywordMarkerFilter(result, stemExclusionSet)
		}
		result = IndicNormalizationFilter(result)
		result = BibleTamilJesusChristFilter(result, emitOriginal = true)
		result = SetKeywordMarkerFilter(result, buildKeywordExclusionSet())
		result = StopFilter(result, stopwords)
		result = SnowballFilter(result, TamilStemmer())
		return TokenStreamComponents(source, result)
	}

	override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
		var result: TokenStream = LowerCaseFilter(`in`)
		result = DecimalDigitFilter(result)
		result = IndicNormalizationFilter(result)
		result = BibleTamilJesusChristFilter(result, emitOriginal = false)
		return result
	}
}

private class BibleTamilJesusChristFilter(
	input: TokenStream,
	private val emitOriginal: Boolean
) : TokenFilter(input) {
	private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
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
		if (startsWith(buffer, length, JESUS_CHRIST)) {
			return JESUS_CHRIST
		}

		// If the token is only slightly truncated (missing one or two trailing chars),
		// treat it as the full normalized form to handle declensions/truncations.
		val minPrefix = kotlin.math.max(JESUS_CHRIST.size - 2, 1)
		return if (length >= minPrefix && bufferIsPrefixOfTarget(buffer, length, JESUS_CHRIST)) {
			JESUS_CHRIST
		} else {
			null
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

	private fun startsWith(buffer: CharArray, length: Int, target: CharArray): Boolean {
		if (length < target.size) {
			return false
		}
		for (i in target.indices) {
			if (buffer[i] != target[i]) {
				return false
			}
		}
		return true
	}

	private fun bufferIsPrefixOfTarget(buffer: CharArray, length: Int, target: CharArray): Boolean {
		if (length > target.size) return false
		for (i in 0 until length) {
			if (buffer[i] != target[i]) return false
		}
		return true
	}

	companion object {
		private val JESUS_CHRIST = JESUS_CHRIST_TEXT.toCharArray()
	}
}
