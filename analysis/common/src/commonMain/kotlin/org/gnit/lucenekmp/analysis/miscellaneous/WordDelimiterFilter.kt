package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.InPlaceMergeSorter

/**
 * Splits words into subwords and performs optional transformations on subword groups.
 *
 * @deprecated Use [WordDelimiterGraphFilter] instead: it produces a correct token graph so
 *     that e.g. PhraseQuery works correctly when it's used in the search time analyzer.
 */
@Deprecated("Use WordDelimiterGraphFilter instead")
class WordDelimiterFilter : TokenFilter {
    companion object {
        const val LOWER = 0x01
        const val UPPER = 0x02
        const val DIGIT = 0x04
        const val SUBWORD_DELIM = 0x08

        // combinations: for testing, not for setting bits
        const val ALPHA = 0x03
        const val ALPHANUM = 0x07

        /** Causes parts of words to be generated. */
        const val GENERATE_WORD_PARTS = 1

        /** Causes number subwords to be generated. */
        const val GENERATE_NUMBER_PARTS = 2

        /** Causes maximum runs of word parts to be catenated. */
        const val CATENATE_WORDS = 4

        /** Causes maximum runs of word parts to be catenated. */
        const val CATENATE_NUMBERS = 8

        /** Causes all subword parts to be catenated. */
        const val CATENATE_ALL = 16

        /** Causes original words are preserved and added to the subword list (Defaults to false). */
        const val PRESERVE_ORIGINAL = 32

        /**
         * If not set, causes case changes to be ignored (subwords will only be generated given
         * SUBWORD_DELIM tokens)
         */
        const val SPLIT_ON_CASE_CHANGE = 64

        /**
         * If not set, causes numeric changes to be ignored (subwords will only be generated given
         * SUBWORD_DELIM tokens).
         */
        const val SPLIT_ON_NUMERICS = 128

        /** Causes trailing "'s" to be removed for each subword. */
        const val STEM_ENGLISH_POSSESSIVE = 256

        /** Suppresses processing terms with [KeywordAttribute.isKeyword]=true. */
        const val IGNORE_KEYWORDS = 512

        fun isAlpha(type: Int): Boolean {
            return (type and ALPHA) != 0
        }

        fun isDigit(type: Int): Boolean {
            return (type and DIGIT) != 0
        }

        fun isSubwordDelim(type: Int): Boolean {
            return (type and SUBWORD_DELIM) != 0
        }

        fun isUpper(type: Int): Boolean {
            return (type and UPPER) != 0
        }
    }

    /** If not null is the set of tokens to protect from being delimited */
    val protWords: CharArraySet?

    private val flags: Int

    private val termAttribute: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttribute: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val offsetAttribute: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncAttribute: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val typeAttribute: TypeAttribute = addAttribute(TypeAttribute::class)

    // used for iterating word delimiter breaks
    private val iterator: WordDelimiterIterator

    // used for concatenating runs of similar typed subwords (word,number)
    private val concat = WordDelimiterConcatenation()
    // number of subwords last output by concat.
    private var lastConcatCount = 0

    // used for catenate all
    private val concatAll = WordDelimiterConcatenation()

    // used for accumulating position increment gaps
    private var accumPosInc = 0

    private var savedBuffer = CharArray(1024)
    private var savedStartOffset = 0
    private var savedEndOffset = 0
    private var savedType = ""
    private var hasSavedState = false
    // if length by start + end offsets doesn't match the term text then assume
    // this is a synonym and don't adjust the offsets.
    private var hasIllegalOffsets = false

    // for a run of the same subword type within a word, have we output anything?
    private var hasOutputToken = false
    // when preserve original is on, have we output any token following it?
    // this token must have posInc=0!
    private var hasOutputFollowingOriginal = false

    /**
     * Creates a new WordDelimiterFilter
     *
     * @param input TokenStream to be filtered
     * @param charTypeTable table containing character types
     * @param configurationFlags Flags configuring the filter
     * @param protWords If not null is the set of tokens to protect from being delimited
     */
    constructor(
        input: TokenStream,
        charTypeTable: ByteArray,
        configurationFlags: Int,
        protWords: CharArraySet?
    ) : super(input) {
        this.flags = configurationFlags
        this.protWords = protWords
        this.iterator =
            WordDelimiterIterator(
                charTypeTable,
                has(SPLIT_ON_CASE_CHANGE),
                has(SPLIT_ON_NUMERICS),
                has(STEM_ENGLISH_POSSESSIVE)
            )
    }

    /**
     * Creates a new WordDelimiterFilter using [WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE]
     * as its charTypeTable
     */
    constructor(input: TokenStream, configurationFlags: Int, protWords: CharArraySet?) : this(
        input,
        WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE,
        configurationFlags,
        protWords
    )

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        while (true) {
            if (!hasSavedState) {
                if (!input.incrementToken()) {
                    return false
                }
                if (has(IGNORE_KEYWORDS) && keywordAttribute.isKeyword) {
                    return true
                }
                val termLength = termAttribute.length
                val termBuffer = termAttribute.buffer()

                accumPosInc += posIncAttribute.getPositionIncrement()

                iterator.setText(termBuffer, termLength)
                iterator.next()

                if ((iterator.current == 0 && iterator.end == termLength) ||
                    (protWords != null && protWords.contains(termBuffer, 0, termLength))
                ) {
                    posIncAttribute.setPositionIncrement(accumPosInc)
                    accumPosInc = 0
                    first = false
                    return true
                }

                if (iterator.end == WordDelimiterIterator.DONE && !has(PRESERVE_ORIGINAL)) {
                    if (posIncAttribute.getPositionIncrement() == 1 && !first) {
                        accumPosInc--
                    }
                    continue
                }

                saveState()

                hasOutputToken = false
                hasOutputFollowingOriginal = !has(PRESERVE_ORIGINAL)
                lastConcatCount = 0

                if (has(PRESERVE_ORIGINAL)) {
                    posIncAttribute.setPositionIncrement(accumPosInc)
                    accumPosInc = 0
                    first = false
                    return true
                }
            }

            if (iterator.end == WordDelimiterIterator.DONE) {
                if (!concat.isEmpty()) {
                    if (flushConcatenation(concat)) {
                        buffer()
                        continue
                    }
                }

                if (!concatAll.isEmpty()) {
                    if (concatAll.subwordCount > lastConcatCount) {
                        concatAll.writeAndClear()
                        buffer()
                        continue
                    }
                    concatAll.clear()
                }

                if (bufferedPos < bufferedLen) {
                    if (bufferedPos == 0) {
                        sorter.sort(0, bufferedLen)
                    }
                    clearAttributes()
                    restoreState(buffered[bufferedPos++])
                    if (first && posIncAttribute.getPositionIncrement() == 0) {
                        posIncAttribute.setPositionIncrement(1)
                    }
                    first = false
                    return true
                }

                bufferedPos = 0
                bufferedLen = 0
                hasSavedState = false
                continue
            }

            if (iterator.isSingleWord()) {
                generatePart(true)
                iterator.next()
                first = false
                return true
            }

            val wordType = iterator.type()

            if (!concat.isEmpty() && (concat.type and wordType) == 0) {
                if (flushConcatenation(concat)) {
                    hasOutputToken = false
                    buffer()
                    continue
                }
                hasOutputToken = false
            }

            if (shouldConcatenate(wordType)) {
                if (concat.isEmpty()) {
                    concat.type = wordType
                }
                concatenate(concat)
            }

            if (has(CATENATE_ALL)) {
                concatenate(concatAll)
            }

            if (shouldGenerateParts(wordType)) {
                generatePart(false)
                buffer()
            }

            iterator.next()
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        hasSavedState = false
        concat.clear()
        concatAll.clear()
        accumPosInc = 0
        bufferedPos = 0
        bufferedLen = 0
        first = true
    }

    private var buffered = arrayOfNulls<AttributeSource.State>(8)
    private var startOff = IntArray(8)
    private var posInc = IntArray(8)
    private var bufferedLen = 0
    private var bufferedPos = 0
    private var first = true

    private inner class OffsetSorter : InPlaceMergeSorter() {
        override fun compare(i: Int, j: Int): Int {
            var cmp = startOff[i].compareTo(startOff[j])
            if (cmp == 0) {
                cmp = posInc[j].compareTo(posInc[i])
            }
            return cmp
        }

        override fun swap(i: Int, j: Int) {
            val tmp = buffered[i]
            buffered[i] = buffered[j]
            buffered[j] = tmp

            var tmp2 = startOff[i]
            startOff[i] = startOff[j]
            startOff[j] = tmp2

            tmp2 = posInc[i]
            posInc[i] = posInc[j]
            posInc[j] = tmp2
        }
    }

    private val sorter = OffsetSorter()

    private fun buffer() {
        if (bufferedLen == buffered.size) {
            val newSize = ArrayUtil.oversize(bufferedLen + 1, 8)
            buffered = ArrayUtil.growExact(buffered, newSize)
            startOff = ArrayUtil.growExact(startOff, newSize)
            posInc = ArrayUtil.growExact(posInc, newSize)
        }
        startOff[bufferedLen] = offsetAttribute.startOffset()
        posInc[bufferedLen] = posIncAttribute.getPositionIncrement()
        buffered[bufferedLen] = captureState()
        bufferedLen++
    }

    /** Saves the existing attribute states */
    private fun saveState() {
        savedStartOffset = offsetAttribute.startOffset()
        savedEndOffset = offsetAttribute.endOffset()
        hasIllegalOffsets = savedEndOffset - savedStartOffset != termAttribute.length
        savedType = typeAttribute.type()

        if (savedBuffer.size < termAttribute.length) {
            savedBuffer = CharArray(ArrayUtil.oversize(termAttribute.length, Character.BYTES))
        }

        termAttribute.buffer().copyInto(savedBuffer, 0, 0, termAttribute.length)
        iterator.text = savedBuffer

        hasSavedState = true
    }

    /**
     * Flushes the given WordDelimiterConcatenation by either writing its concat and then clearing, or
     * just clearing.
     *
     * @return `true` if the concatenation was written before it was cleared, `false`
     *     otherwise
     */
    private fun flushConcatenation(concatenation: WordDelimiterConcatenation): Boolean {
        lastConcatCount = concatenation.subwordCount
        if (concatenation.subwordCount != 1 || !shouldGenerateParts(concatenation.type)) {
            concatenation.writeAndClear()
            return true
        }
        concatenation.clear()
        return false
    }

    /** Determines whether to concatenate a word or number if the current word is the given type */
    private fun shouldConcatenate(wordType: Int): Boolean {
        return (has(CATENATE_WORDS) && isAlpha(wordType)) ||
            (has(CATENATE_NUMBERS) && isDigit(wordType))
    }

    /** Determines whether a word/number part should be generated for a word of the given type */
    private fun shouldGenerateParts(wordType: Int): Boolean {
        return (has(GENERATE_WORD_PARTS) && isAlpha(wordType)) ||
            (has(GENERATE_NUMBER_PARTS) && isDigit(wordType))
    }

    /** Concatenates the saved buffer to the given WordDelimiterConcatenation */
    private fun concatenate(concatenation: WordDelimiterConcatenation) {
        if (concatenation.isEmpty()) {
            concatenation.startOffset = savedStartOffset + iterator.current
        }
        concatenation.append(savedBuffer, iterator.current, iterator.end - iterator.current)
        concatenation.endOffset = savedStartOffset + iterator.end
    }

    /** Generates a word/number part, updating the appropriate attributes */
    private fun generatePart(isSingleWord: Boolean) {
        clearAttributes()
        termAttribute.copyBuffer(savedBuffer, iterator.current, iterator.end - iterator.current)
        val startOffset = savedStartOffset + iterator.current
        val endOffset = savedStartOffset + iterator.end

        if (hasIllegalOffsets) {
            if (isSingleWord && startOffset <= savedEndOffset) {
                offsetAttribute.setOffset(startOffset, savedEndOffset)
            } else {
                offsetAttribute.setOffset(savedStartOffset, savedEndOffset)
            }
        } else {
            offsetAttribute.setOffset(startOffset, endOffset)
        }
        posIncAttribute.setPositionIncrement(position(false))
        typeAttribute.setType(savedType)
    }

    /**
     * Get the position increment gap for a subword or concatenation
     *
     * @param inject true if this token wants to be injected
     * @return position increment gap
     */
    private fun position(inject: Boolean): Int {
        val posInc = accumPosInc

        if (hasOutputToken) {
            accumPosInc = 0
            return if (inject) 0 else kotlin.math.max(1, posInc)
        }

        hasOutputToken = true

        if (!hasOutputFollowingOriginal) {
            hasOutputFollowingOriginal = true
            return 0
        }
        accumPosInc = 0
        return kotlin.math.max(1, posInc)
    }

    /** Determines whether the given flag is set */
    private fun has(flag: Int): Boolean {
        return (flags and flag) != 0
    }

    /** A WDF concatenated 'run' */
    private inner class WordDelimiterConcatenation {
        val buffer = StringBuilder()
        var startOffset = 0
        var endOffset = 0
        var type = 0
        var subwordCount = 0

        /**
         * Appends the given text of the given length, to the concetenation at the given offset
         */
        fun append(text: CharArray, offset: Int, length: Int) {
            buffer.appendRange(text, offset, offset + length)
            subwordCount++
        }

        /** Writes the concatenation to the attributes */
        fun write() {
            clearAttributes()
            if (termAttribute.length < buffer.length) {
                termAttribute.resizeBuffer(buffer.length)
            }
            val termbuffer = termAttribute.buffer()

            buffer.toString().toCharArray().copyInto(termbuffer, 0, 0, buffer.length)
            termAttribute.setLength(buffer.length)

            if (hasIllegalOffsets) {
                offsetAttribute.setOffset(savedStartOffset, savedEndOffset)
            } else {
                offsetAttribute.setOffset(startOffset, endOffset)
            }
            posIncAttribute.setPositionIncrement(position(true))
            typeAttribute.setType(savedType)
            accumPosInc = 0
        }

        /** Determines if the concatenation is empty */
        fun isEmpty(): Boolean {
            return buffer.isEmpty()
        }

        /** Clears the concatenation and resets its state */
        fun clear() {
            buffer.setLength(0)
            startOffset = 0
            endOffset = 0
            type = 0
            subwordCount = 0
        }

        /** Convenience method for the common scenario of having to write the concetenation and then clearing its state */
        fun writeAndClear() {
            write()
            clear()
        }
    }
}
