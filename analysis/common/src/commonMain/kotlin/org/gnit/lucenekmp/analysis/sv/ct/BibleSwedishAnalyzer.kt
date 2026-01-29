package org.gnit.lucenekmp.analysis.sv.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.sv.SwedishAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.tartarus.snowball.ext.SwedishStemmer

/** Analyzer for Swedish bible text with Jesus/Kristus declension normalization. */
class BibleSwedishAnalyzer : StopwordAnalyzerBase {
	private val stemExclusionSet: CharArraySet

	/** Builds an analyzer with the default stop words. */
	constructor() : this(SwedishAnalyzer.getDefaultStopSet())

	/** Builds an analyzer with the given stop words. */
	constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

	/**
	 * Builds an analyzer with the given stop words. If a non-empty stem exclusion set is provided
	 * this analyzer will add a SetKeywordMarkerFilter before stemming.
	 */
	constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
		this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
	}

	override fun createComponents(fieldName: String): TokenStreamComponents {
		val source: Tokenizer = StandardTokenizer()
		var result: TokenStream = LowerCaseFilter(source)
		result = StopFilter(result, stopwords)
		if (!stemExclusionSet.isEmpty()) {
			result = SetKeywordMarkerFilter(result, stemExclusionSet)
		}
		result = BibleSwedishJesusChristFilter(result, emitOriginal = true)
		result = SnowballFilter(result, SwedishStemmer())
		return TokenStreamComponents(source, result)
	}

	override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
		var result: TokenStream = LowerCaseFilter(`in`)
		result = BibleSwedishJesusChristFilter(result, emitOriginal = false)
		return result
	}
}

private class BibleSwedishJesusChristFilter(
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
		return when {
			matches(buffer, length, JESU) || matches(buffer, length, JESUS) -> JESUS
			matches(buffer, length, KRISTI) || matches(buffer, length, KRISTUS) -> KRISTUS
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

	companion object {
		private val JESU = charArrayOf('j', 'e', 's', 'u')
		private val JESUS = charArrayOf('j', 'e', 's', 'u', 's')
		private val KRISTI = charArrayOf('k', 'r', 'i', 's', 't', 'i')
		private val KRISTUS = charArrayOf('k', 'r', 'i', 's', 't', 'u', 's')
	}
}