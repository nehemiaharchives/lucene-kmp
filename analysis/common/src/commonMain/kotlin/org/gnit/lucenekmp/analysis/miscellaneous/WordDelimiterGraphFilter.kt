package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.InPlaceMergeSorter
import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * Splits words into subwords and performs optional transformations on subword groups, producing a
 * correct token graph so that e.g. PhraseQuery can work correctly when this filter is used
 * in the search-time analyzer. Unlike the deprecated WordDelimiterFilter, this token filter
 * produces a correct token graph as output. However, it cannot consume an input token graph
 * correctly. Processing is suppressed by [KeywordAttribute.isKeyword]=true.
 */
class WordDelimiterGraphFilter : TokenFilter {
    companion object {
        /**
         * Causes parts of words to be generated:
         *
         * "PowerShot" => "Power" "Shot"
         */
        const val GENERATE_WORD_PARTS = 1

        /**
         * Causes number subwords to be generated:
         *
         * "500-42" => "500" "42"
         */
        const val GENERATE_NUMBER_PARTS = 2

        /**
         * Causes maximum runs of word parts to be catenated:
         *
         * "wi-fi" => "wifi"
         */
        const val CATENATE_WORDS = 4

        /**
         * Causes maximum runs of number parts to be catenated:
         *
         * "500-42" => "50042"
         */
        const val CATENATE_NUMBERS = 8

        /**
         * Causes all subword parts to be catenated:
         *
         * "wi-fi-4000" => "wifi4000"
         */
        const val CATENATE_ALL = 16

        /**
         * Causes original words are preserved and added to the subword list (Defaults to false)
         *
         * "500-42" => "500" "42" "500-42"
         */
        const val PRESERVE_ORIGINAL = 32

        /** Causes lowercase -> uppercase transition to start a new subword. */
        const val SPLIT_ON_CASE_CHANGE = 64

        /**
         * If not set, causes numeric changes to be ignored (subwords will only be generated given
         * SUBWORD_DELIM tokens).
         */
        const val SPLIT_ON_NUMERICS = 128

        /**
         * Causes trailing "'s" to be removed for each subword
         *
         * "O'Neil's" => "O", "Neil"
         */
        const val STEM_ENGLISH_POSSESSIVE = 256

        /** Suppresses processing terms with [KeywordAttribute.isKeyword]=true. */
        const val IGNORE_KEYWORDS = 512

        /** Returns string representation of configuration flags */
        fun flagsToString(flags: Int): String {
            val b = StringBuilder()
            if ((flags and GENERATE_WORD_PARTS) != 0) {
                b.append("GENERATE_WORD_PARTS")
            }
            if ((flags and GENERATE_NUMBER_PARTS) != 0) {
                if (b.isNotEmpty()) b.append(" | ")
                b.append("GENERATE_NUMBER_PARTS")
            }
            if ((flags and CATENATE_WORDS) != 0) {
                if (b.isNotEmpty()) b.append(" | ")
                b.append("CATENATE_WORDS")
            }
            if ((flags and CATENATE_NUMBERS) != 0) {
                if (b.isNotEmpty()) b.append(" | ")
                b.append("CATENATE_NUMBERS")
            }
            if ((flags and CATENATE_ALL) != 0) {
                if (b.isNotEmpty()) b.append(" | ")
                b.append("CATENATE_ALL")
            }
            if ((flags and PRESERVE_ORIGINAL) != 0) {
                if (b.isNotEmpty()) b.append(" | ")
                b.append("PRESERVE_ORIGINAL")
            }
            if ((flags and SPLIT_ON_CASE_CHANGE) != 0) {
                if (b.isNotEmpty()) b.append(" | ")
                b.append("SPLIT_ON_CASE_CHANGE")
            }
            if ((flags and SPLIT_ON_NUMERICS) != 0) {
                if (b.isNotEmpty()) b.append(" | ")
                b.append("SPLIT_ON_NUMERICS")
            }
            if ((flags and STEM_ENGLISH_POSSESSIVE) != 0) {
                if (b.isNotEmpty()) b.append(" | ")
                b.append("STEM_ENGLISH_POSSESSIVE")
            }
            return b.toString()
        }
    }

    /** If not null is the set of tokens to protect from being delimited */
    val protWords: CharArraySet?

    private val flags: Int

    // packs start pos, end pos, start part, end part (= slice of the term text) for each buffered part:
    private var bufferedParts = IntArray(16)
    private var bufferedLen = 0
    private var bufferedPos = 0

    // holds text for each buffered part, or null if it's a simple slice of the original term
    private var bufferedTermParts = arrayOfNulls<CharArray>(4)

    private val termAttribute: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttribute: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val offsetAttribute: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncAttribute: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLenAttribute: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)

    // used for iterating word delimiter breaks
    private val iterator: WordDelimiterIterator

    // used for concatenating runs of similar typed subwords (word,number)
    private val concat = WordDelimiterConcatenation()

    private val adjustInternalOffsets: Boolean

    // number of subwords last output by concat.
    private var lastConcatCount = 0

    // used for catenate all
    private val concatAll = WordDelimiterConcatenation()

    // used for accumulating position increment gaps so that we preserve incoming holes:
    private var accumPosInc = 0

    private var savedTermBuffer = CharArray(16)
    private var savedTermLength = 0
    private var savedStartOffset = 0
    private var savedEndOffset = 0
    private var savedState: AttributeSource.State? = null
    private var lastStartOffset = 0
    private var adjustingOffsets = false

    private var wordPos = 0

    /**
     * Creates a new WordDelimiterGraphFilter
     *
     * @param input TokenStream to be filtered
     * @param adjustInternalOffsets if the offsets of partial terms should be adjusted
     * @param charTypeTable table containing character types
     * @param configurationFlags Flags configuring the filter
     * @param protWords If not null is the set of tokens to protect from being delimited
     */
    constructor(
        input: TokenStream,
        adjustInternalOffsets: Boolean,
        charTypeTable: ByteArray,
        configurationFlags: Int,
        protWords: CharArraySet?
    ) : super(input) {
        require(
            (configurationFlags and
                (GENERATE_WORD_PARTS or
                    GENERATE_NUMBER_PARTS or
                    CATENATE_WORDS or
                    CATENATE_NUMBERS or
                    CATENATE_ALL or
                    PRESERVE_ORIGINAL or
                    SPLIT_ON_CASE_CHANGE or
                    SPLIT_ON_NUMERICS or
                    STEM_ENGLISH_POSSESSIVE or
                    IGNORE_KEYWORDS).inv()) == 0
        ) { "flags contains unrecognized flag: $configurationFlags" }
        this.flags = configurationFlags
        this.protWords = protWords
        this.iterator =
            WordDelimiterIterator(
                charTypeTable,
                has(SPLIT_ON_CASE_CHANGE),
                has(SPLIT_ON_NUMERICS),
                has(STEM_ENGLISH_POSSESSIVE)
            )
        this.adjustInternalOffsets = adjustInternalOffsets
    }

    /**
     * Creates a new WordDelimiterGraphFilter using [WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE] as its charTypeTable
     */
    constructor(input: TokenStream, configurationFlags: Int, protWords: CharArraySet?) : this(
        input,
        false,
        WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE,
        configurationFlags,
        protWords
    )

    /** Iterates all words parts and concatenations, buffering up the term parts we should return. */
    @Throws(IOException::class)
    private fun bufferWordParts() {
        saveState()

        adjustingOffsets = adjustInternalOffsets && savedEndOffset - savedStartOffset == savedTermLength

        bufferedLen = 0
        lastConcatCount = 0
        wordPos = 0

        if (has(PRESERVE_ORIGINAL)) {
            buffer(0, 1, 0, savedTermLength)
        }

        if (iterator.isSingleWord()) {
            buffer(wordPos, wordPos + 1, iterator.current, iterator.end)
            wordPos++
            iterator.next()
        } else {
            while (iterator.end != WordDelimiterIterator.DONE) {
                val wordType = iterator.type()

                if (concat.isNotEmpty() && (concat.type and wordType) == 0) {
                    flushConcatenation(concat)
                }

                if (shouldConcatenate(wordType)) {
                    concatenate(concat)
                }

                if (has(CATENATE_ALL)) {
                    concatenate(concatAll)
                }

                if (shouldGenerateParts(wordType)) {
                    buffer(wordPos, wordPos + 1, iterator.current, iterator.end)
                    wordPos++
                }
                iterator.next()
            }

            if (concat.isNotEmpty()) {
                flushConcatenation(concat)
            }

            if (concatAll.isNotEmpty()) {
                if (concatAll.subwordCount > lastConcatCount) {
                    if (wordPos == concatAll.startPos) {
                        wordPos++
                    }
                    concatAll.write()
                }
                concatAll.clear()
            }
        }

        if (has(PRESERVE_ORIGINAL)) {
            if (wordPos == 0) {
                wordPos++
            }
            bufferedParts[1] = wordPos
        }

        sorter.sort(if (has(PRESERVE_ORIGINAL)) 1 else 0, bufferedLen)
        wordPos = 0
        bufferedPos = 0
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        while (true) {
            if (savedState == null) {
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
                    return true
                }

                if (iterator.end == WordDelimiterIterator.DONE) {
                    if (!has(PRESERVE_ORIGINAL)) {
                        continue
                    } else {
                        accumPosInc = 0
                        return true
                    }
                }

                bufferWordParts()
            }

            if (bufferedPos < bufferedLen) {
                clearAttributes()
                restoreState(savedState)

                val termPart = bufferedTermParts[bufferedPos]
                val startPos = bufferedParts[4 * bufferedPos]
                val endPos = bufferedParts[4 * bufferedPos + 1]
                val startPart = bufferedParts[4 * bufferedPos + 2]
                val endPart = bufferedParts[4 * bufferedPos + 3]
                bufferedPos++

                var startOffset: Int
                var endOffset: Int

                if (!adjustingOffsets) {
                    startOffset = savedStartOffset
                    endOffset = savedEndOffset
                } else {
                    startOffset = savedStartOffset + startPart
                    endOffset = savedStartOffset + endPart
                }

                startOffset = kotlin.math.max(startOffset, lastStartOffset)
                endOffset = kotlin.math.max(endOffset, lastStartOffset)

                offsetAttribute.setOffset(startOffset, endOffset)
                lastStartOffset = startOffset

                if (termPart == null) {
                    termAttribute.copyBuffer(savedTermBuffer, startPart, endPart - startPart)
                } else {
                    termAttribute.copyBuffer(termPart, 0, termPart.size)
                }

                posIncAttribute.setPositionIncrement(accumPosInc + startPos - wordPos)
                accumPosInc = 0
                posLenAttribute.positionLength = endPos - startPos
                wordPos = startPos
                return true
            }

            savedState = null
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        accumPosInc = 0
        savedState = null
        lastStartOffset = 0
        concat.clear()
        concatAll.clear()
    }

    private inner class PositionSorter : InPlaceMergeSorter() {
        override fun compare(i: Int, j: Int): Int {
            var iOff = bufferedParts[4 * i + 2]
            var jOff = bufferedParts[4 * j + 2]
            var cmp = iOff.compareTo(jOff)
            if (cmp != 0) {
                return cmp
            }

            iOff = bufferedParts[4 * i + 3]
            jOff = bufferedParts[4 * j + 3]
            return jOff.compareTo(iOff)
        }

        override fun swap(i: Int, j: Int) {
            val iOffset = 4 * i
            val jOffset = 4 * j
            for (x in 0 until 4) {
                val tmp = bufferedParts[iOffset + x]
                bufferedParts[iOffset + x] = bufferedParts[jOffset + x]
                bufferedParts[jOffset + x] = tmp
            }

            val tmp2 = bufferedTermParts[i]
            bufferedTermParts[i] = bufferedTermParts[j]
            bufferedTermParts[j] = tmp2
        }
    }

    private val sorter = PositionSorter()

    /**
     * startPos, endPos -> graph start/end position startPart, endPart -> slice of the original term
     * for this part
     */
    private fun buffer(startPos: Int, endPos: Int, startPart: Int, endPart: Int) {
        buffer(null, startPos, endPos, startPart, endPart)
    }

    /** a null termPart means it's a simple slice of the original term */
    private fun buffer(termPart: CharArray?, startPos: Int, endPos: Int, startPart: Int, endPart: Int) {
        check(endPos > startPos) { "startPos=$startPos endPos=$endPos" }
        check(endPart > startPart || (endPart == 0 && startPart == 0 && savedTermLength == 0)) {
            "startPart=$startPart endPart=$endPart"
        }
        if ((bufferedLen + 1) * 4 > bufferedParts.size) {
            bufferedParts = ArrayUtil.grow(bufferedParts, (bufferedLen + 1) * 4)
        }
        if (bufferedTermParts.size == bufferedLen) {
            val newSize = ArrayUtil.oversize(bufferedLen + 1, RamUsageEstimator.NUM_BYTES_OBJECT_REF)
            val newArray = arrayOfNulls<CharArray>(newSize)
            bufferedTermParts.copyInto(newArray, 0, 0, bufferedTermParts.size)
            bufferedTermParts = newArray
        }
        bufferedTermParts[bufferedLen] = termPart
        bufferedParts[bufferedLen * 4] = startPos
        bufferedParts[bufferedLen * 4 + 1] = endPos
        bufferedParts[bufferedLen * 4 + 2] = startPart
        bufferedParts[bufferedLen * 4 + 3] = endPart
        bufferedLen++
    }

    /** Saves the existing attribute states */
    private fun saveState() {
        savedTermLength = termAttribute.length
        savedStartOffset = offsetAttribute.startOffset()
        savedEndOffset = offsetAttribute.endOffset()
        savedState = captureState()

        if (savedTermBuffer.size < savedTermLength) {
            savedTermBuffer = CharArray(ArrayUtil.oversize(savedTermLength, Character.BYTES))
        }

        termAttribute.buffer().copyInto(savedTermBuffer, 0, 0, savedTermLength)
    }

    /**
     * Flushes the given WordDelimiterConcatenation by either writing its concat and then clearing, or
     * just clearing.
     */
    private fun flushConcatenation(concat: WordDelimiterConcatenation) {
        if (wordPos == concat.startPos) {
            wordPos++
        }
        lastConcatCount = concat.subwordCount
        if (concat.subwordCount != 1 || !shouldGenerateParts(concat.type)) {
            concat.write()
        }
        concat.clear()
    }

    /**
     * Determines whether to concatenate a word or number if the current word is the given type
     */
    private fun shouldConcatenate(wordType: Int): Boolean {
        return (has(CATENATE_WORDS) && WordDelimiterIterator.isAlpha(wordType)) ||
            (has(CATENATE_NUMBERS) && WordDelimiterIterator.isDigit(wordType))
    }

    /**
     * Determines whether a word/number part should be generated for a word of the given type
     */
    private fun shouldGenerateParts(wordType: Int): Boolean {
        return (has(GENERATE_WORD_PARTS) && WordDelimiterIterator.isAlpha(wordType)) ||
            (has(GENERATE_NUMBER_PARTS) && WordDelimiterIterator.isDigit(wordType))
    }

    /**
     * Concatenates the saved buffer to the given WordDelimiterConcatenation
     */
    private fun concatenate(concatenation: WordDelimiterConcatenation) {
        if (concatenation.isEmpty()) {
            concatenation.type = iterator.type()
            concatenation.startPart = iterator.current
            concatenation.startPos = wordPos
        }
        concatenation.append(savedTermBuffer, iterator.current, iterator.end - iterator.current)
        concatenation.endPart = iterator.end
    }

    /**
     * Determines whether the given flag is set
     */
    private fun has(flag: Int): Boolean {
        return (flags and flag) != 0
    }

    /** A WDF concatenated 'run' */
    private inner class WordDelimiterConcatenation {
        val buffer = StringBuilder()
        var startPart = 0
        var endPart = 0
        var startPos = 0
        var type = 0
        var subwordCount = 0

        /**
         * Appends the given text of the given length, to the concetenation at the given offset
         */
        fun append(text: CharArray, offset: Int, length: Int) {
            buffer.appendRange(text, offset, offset + length)
            subwordCount++
        }

        /** Writes the concatenation to part buffer */
        fun write() {
            val termPart = CharArray(buffer.length)
            buffer.toString().toCharArray().copyInto(termPart, 0, 0, buffer.length)
            buffer(termPart, startPos, wordPos, startPart, endPart)
        }

        /**
         * Determines if the concatenation is empty
         */
        fun isEmpty(): Boolean {
            return buffer.isEmpty()
        }

        fun isNotEmpty(): Boolean {
            return !isEmpty()
        }

        /** Clears the concatenation and resets its state */
        fun clear() {
            buffer.setLength(0)
            startPart = 0
            endPart = 0
            type = 0
            subwordCount = 0
        }
    }

    override fun toString(): String {
        val b = StringBuilder()
        b.append("WordDelimiterGraphFilter(flags=")
        b.append(flagsToString(flags))
        b.append(')')
        return b.toString()
    }
}
