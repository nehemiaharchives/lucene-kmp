package org.gnit.lucenekmp.analysis.de.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.de.GermanAnalyzer
import org.gnit.lucenekmp.analysis.de.GermanLightStemFilter
import org.gnit.lucenekmp.analysis.de.GermanNormalizationFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/** Analyzer for German bible text with Jesus/Christus declension normalization.
 *
 * The German Bible traditionally kept the Latin declensions for the name of Jesus. Since the New Testament was heavily influenced by Latin scholarship in the days of Martin Luther, the name was declined like this:
 *
 * Nominative (Subject): Jesus Christus
 * Genitive (Possessive): Jesu Christi
 * Dative (Indirect Object): Jesu Christo
 * Accusative (Direct Object): Jesum Christum
 *
 * In modern German, we usually just say "von Jesus Christus" to show possession, but the classic Luther Bible preserves these beautiful, older linguistic forms that signal "of" without needing a separate word.
 *
 * This class is to normalizes all the different forms of Jesus Christ in both classic Luter version of German and also modern German.
 */
class BibleGermanAnalyzer : StopwordAnalyzerBase {
	/** Contains words that should be indexed but not stemmed. */
	private val exclusionSet: CharArraySet

	/** Builds an analyzer with the default stop words. */
	constructor() : this(GermanAnalyzer.getDefaultStopSet())

	/** Builds an analyzer with the given stop words. */
	constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

	/** Builds an analyzer with the given stop words and a stemming exclusion set. */
	constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
		exclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
	}

	override fun createComponents(fieldName: String): TokenStreamComponents {
		val source: Tokenizer = StandardTokenizer()
		var result: TokenStream = LowerCaseFilter(source)
		result = StopFilter(result, stopwords)
		result = SetKeywordMarkerFilter(result, exclusionSet)
		result = BibleGermanJesusChristFilter(result, emitOriginal = true)
		result = GermanNormalizationFilter(result)
		result = GermanLightStemFilter(result)
		return TokenStreamComponents(source, result)
	}

	override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
		var result: TokenStream = LowerCaseFilter(`in`)
		result = BibleGermanJesusChristFilter(result, emitOriginal = false)
		result = GermanNormalizationFilter(result)
		return result
	}
}


private class BibleGermanJesusChristFilter(
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
			matches(buffer, length, JESU) || matches(buffer, length, JESUM) || matches(buffer, length, JESUS) -> JESUS
			matches(buffer, length, CHRISTI) || matches(buffer, length, CHRISTO) || matches(buffer, length, CHRISTUM) || matches(buffer, length, CHRISTUS) -> CHRISTUS
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
		private val JESUM = charArrayOf('j', 'e', 's', 'u', 'm')
		private val JESUS = charArrayOf('j', 'e', 's', 'u', 's')
		private val CHRISTI = charArrayOf('c', 'h', 'r', 'i', 's', 't', 'i')
		private val CHRISTO = charArrayOf('c', 'h', 'r', 'i', 's', 't', 'o')
		private val CHRISTUM = charArrayOf('c', 'h', 'r', 'i', 's', 't', 'u', 'm')
		private val CHRISTUS = charArrayOf('c', 'h', 'r', 'i', 's', 't', 'u', 's')
	}
}
