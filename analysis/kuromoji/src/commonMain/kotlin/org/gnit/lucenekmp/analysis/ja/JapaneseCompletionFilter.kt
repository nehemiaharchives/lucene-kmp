package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.ja.completion.CharSequenceUtils
import org.gnit.lucenekmp.analysis.ja.completion.KatakanaRomanizer
import org.gnit.lucenekmp.analysis.ja.tokenattributes.ReadingAttribute
import org.gnit.lucenekmp.analysis.ja.tokenattributes.ReadingAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.util.CharsRef

/**
 * A [TokenFilter] that adds Japanese romanized tokens to the term attribute.
 * Also keeps original tokens (surface forms). Main usage is query auto-completion.
 */
class JapaneseCompletionFilter(input: TokenStream, private val mode: Mode = DEFAULT_MODE) : TokenFilter(input) {

    enum class Mode {
        /** Simple romanization. Expected to be used when indexing. */
        INDEX,

        /** Input-Method aware romanization. Expected to be used when querying. */
        QUERY
    }

    companion object {
        val DEFAULT_MODE: Mode = Mode.INDEX
    }

    init {
        ReadingAttributeImpl.ensureRegistered()
        addAttributeImpl(ReadingAttributeImpl())
    }

    private val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val readingAttr: ReadingAttribute = addAttribute(ReadingAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    private val tokenGenerator: CompletionTokenGenerator = CompletionTokenGenerator(mode)

    private var inputStreamConsumed: Boolean = false

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        tokenGenerator.reset()
        inputStreamConsumed = false
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        mayIncrementToken()
        if (tokenGenerator.hasNext()) {
            clearAttributes()
            val token = tokenGenerator.next()
            termAttr.setEmpty()!!.append(token.term)
            posIncAtt.setPositionIncrement(if (token.isFirst) 1 else 0)
            offsetAtt.setOffset(token.startOffset, token.endOffset)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    private fun mayIncrementToken() {
        while (!tokenGenerator.hasNext()) {
            if (!inputStreamConsumed && input.incrementToken()) {
                val surface = termAttr.toString()
                var reading = readingAttr.getReading()
                val startOffset = offsetAtt.startOffset()
                val endOffset = offsetAtt.endOffset()

                if (reading == null && CharSequenceUtils.isKana(surface)) {
                    // Use the surface form as reading when possible.
                    reading = CharSequenceUtils.toKatakana(surface)
                }

                tokenGenerator.addToken(surface, reading, startOffset, endOffset)
            } else {
                inputStreamConsumed = true
                if (tokenGenerator.hasPendingToken()) {
                    tokenGenerator.finish()
                } else {
                    break
                }
            }
        }
    }

    private data class CompletionToken(val term: String, val isFirst: Boolean, val startOffset: Int, val endOffset: Int)

    private class CompletionTokenGenerator(private val mode: Mode) {

        private var outputs: ArrayDeque<CompletionToken> = ArrayDeque()

        private var pdgSurface: StringBuilder? = null
        private var pdgReading: StringBuilder? = null
        private var pdgStartOffset: Int = 0
        private var pdgEndOffset: Int = 0

        fun reset() {
            clearPendingToken()
            outputs.clear()
        }

        fun hasNext(): Boolean = outputs.isNotEmpty()

        fun next(): CompletionToken = outputs.removeFirst()

        fun addToken(surface: String, reading: String?, startOffset: Int, endOffset: Int) {
            if (hasPendingToken()) {
                if (
                    mode == Mode.QUERY &&
                    pdgReading != null &&
                    !CharSequenceUtils.isLowercaseAlphabets(pdgSurface!!.toString()) &&
                    CharSequenceUtils.isLowercaseAlphabets(surface)
                ) {
                    // Recover IME mid-composition split tokens when querying.
                    // In this case reading is typically null; use surface in place of reading.
                    pdgSurface!!.append(surface)
                    pdgReading!!.append(surface)
                    pdgEndOffset = endOffset
                    generateOutputs()
                    clearPendingToken()
                } else if (
                    mode == Mode.QUERY &&
                    CharSequenceUtils.isKana(pdgSurface!!.toString()) &&
                    CharSequenceUtils.isKana(surface)
                ) {
                    // Concatenate all-kana sequences when querying.
                    pdgSurface!!.append(surface)
                    pdgReading!!.append(reading ?: "")
                    pdgEndOffset = endOffset
                } else {
                    generateOutputs()
                    resetPendingToken(surface, reading ?: "", startOffset, endOffset)
                }
            } else {
                resetPendingToken(surface, reading ?: "", startOffset, endOffset)
            }
        }

        fun finish() {
            generateOutputs()
            clearPendingToken()
        }

        private fun generateOutputs() {
            val surface = pdgSurface ?: return
            val reading = pdgReading

            // Preserve original surface form as an output.
            outputs.addLast(CompletionToken(surface.toString(), true, pdgStartOffset, pdgEndOffset))

            // Skip readings that cannot be translated to romaji.
            if (reading == null || reading.isEmpty() || !CharSequenceUtils.isKatakanaOrHWAlphabets(reading)) {
                return
            }

            val romaji = KatakanaRomanizer.getInstance().romanize(CharsRef(reading.toString()))
            for (ref in romaji) {
                outputs.addLast(CompletionToken(ref.toString(), false, pdgStartOffset, pdgEndOffset))
            }
        }

        fun hasPendingToken(): Boolean = pdgSurface != null

        private fun resetPendingToken(surface: CharSequence, reading: CharSequence, startOffset: Int, endOffset: Int) {
            if (pdgSurface == null) {
                pdgSurface = StringBuilder()
            } else {
                pdgSurface!!.setLength(0)
            }
            pdgSurface!!.append(surface)

            if (pdgReading == null) {
                pdgReading = StringBuilder()
            } else {
                pdgReading!!.setLength(0)
            }
            pdgReading!!.append(reading)

            pdgStartOffset = startOffset
            pdgEndOffset = endOffset
        }

        private fun clearPendingToken() {
            pdgSurface = null
            pdgReading = null
            pdgStartOffset = 0
            pdgEndOffset = 0
        }
    }
}
