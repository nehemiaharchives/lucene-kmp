package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.he.datastructures.DictRadix
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute

class StreamLemmasFilter : Tokenizer {
    private val _streamLemmatizer: StreamLemmatizer
    private val commonWords: CharArraySet

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val posAtt: HebrewPosAttribute

    private val lemmaFilter: LemmaFilterBase?
    private val stack = ArrayList<Token>()
    private val filterCache = ArrayList<Token>()
    private var index = 0
    private val previousLemmas = HashSet<String?>()
    private var keepOriginalWord = false

    constructor(dict: DictHebMorph) : this(dict, null, null, null)

    constructor(dict: DictHebMorph, commonWords: CharArraySet?, lemmaFilter: LemmaFilterBase?) : this(dict, null, commonWords, lemmaFilter)

    constructor(
        dict: DictHebMorph,
        specialTokenizationCases: DictRadix<Byte>?,
        commonWords: CharArraySet?,
        lemmaFilter: LemmaFilterBase?,
    ) : super() {
        addAttributeImpl(HebrewPosAttributeImpl())
        posAtt = addAttribute(HebrewPosAttribute::class)
        _streamLemmatizer = StreamLemmatizer(input, dict, specialTokenizationCases)
        this.commonWords = commonWords ?: CharArraySet.EMPTY_SET
        this.lemmaFilter = lemmaFilter
    }

    fun setSuffixForExactMatch(c: Char?) {
        _streamLemmatizer.setSuffixForExactMatch(c)
    }

    private val tempRefObject = Reference("")

    private var currentStartOffset = 0
    private var currentEndOffset = 0

    override fun incrementToken(): Boolean {
        clearAttributes()

        // Index all unique lemmas at the same position
        while (index < stack.size) {
            val res = stack[index] as? HebrewToken
            index++

            if ((res == null) || !previousLemmas.add(res.getLemma())) {
                continue
            } // Skip multiple lemmas (we will merge morph properties later)

            createHebrewToken(res)
            offsetAtt.setOffset(currentStartOffset, currentEndOffset)
            typeAtt.setType(HebrewTokenizer.tokenTypeSignature(HebrewTokenizer.TOKEN_TYPES.Hebrew)!!)
            posIncrAtt.setPositionIncrement(0)

            return true
        }

        // Reset state
        index = 0
        stack.clear()
        previousLemmas.clear()

        // Lemmatize next word in stream. The HebMorph lemmatizer will always return a token, unless
        // an unrecognized Hebrew word is hit, then an empty tokens array will be returned.
        val tokenType = _streamLemmatizer.getLemmatizeNextToken(tempRefObject, stack)
        if (tokenType == 0) { // EOS
            return false
        }

        // Store the location of the word in the original stream
        currentStartOffset = correctOffset(_streamLemmatizer.getStartOffset())
        currentEndOffset = correctOffset(_streamLemmatizer.getEndOffset())
        offsetAtt.setOffset(currentStartOffset, currentEndOffset)

        val word = tempRefObject.ref
        if (commonWords.contains(word)) { // common words should be treated later using dedicated filters
            termAtt.copyBuffer(word.toCharArray(), 0, word.length)
            typeAtt.setType(HebrewTokenizer.tokenTypeSignature(HebrewTokenizer.TOKEN_TYPES.Hebrew)!!)
            stack.clear()

            if (!keepOriginalWord) {
                if ((tokenType and HebMorphTokenizer.TokenType.Exact) > 0) {
                    keywordAtt.isKeyword = true
                }
                return true
            }

            keywordAtt.isKeyword = true
            if ((tokenType and HebMorphTokenizer.TokenType.Exact) == 0) {
                stack.add(HebrewToken(word, 0.toByte(), DescFlag.D_EMPTY, word, PrefixType.PS_EMPTY, 1.0f))
            }

            return true
        }

        // Mark request for exact matches in queries, if configured in the tokenizer
        if ((tokenType and HebMorphTokenizer.TokenType.Exact) > 0) {
            keywordAtt.isKeyword = true
        }

        // A non-Hebrew word
        if (stack.size == 1 && stack[0] !is HebrewToken) {
            termAtt.copyBuffer(word.toCharArray(), 0, word.length)

            val tkn = stack[0]
            if (tkn.isNumeric()) {
                typeAtt.setType(HebrewTokenizer.tokenTypeSignature(HebrewTokenizer.TOKEN_TYPES.Numeric)!!)
            } else {
                typeAtt.setType(HebrewTokenizer.tokenTypeSignature(HebrewTokenizer.TOKEN_TYPES.NonHebrew)!!)
            }

            applyLowercaseFilter()

            stack.clear()
            return true
        }

        // If we arrived here, we hit a Hebrew word
        typeAtt.setType(HebrewTokenizer.tokenTypeSignature(HebrewTokenizer.TOKEN_TYPES.Hebrew)!!)

        // Do some filtering if requested...
        if (lemmaFilter != null && lemmaFilter.filterCollection(word, stack, filterCache) != null) {
            stack.clear()
            stack.addAll(filterCache)
        }

        // OOV case - store the word as-is, and also output a suffixed version of it
        if (stack.isEmpty()) {
            termAtt.copyBuffer(word.toCharArray(), 0, word.length)

            if (keepOriginalWord) {
                keywordAtt.isKeyword = true
            }

            if ((tokenType and HebMorphTokenizer.TokenType.Mixed) > 0) {
                typeAtt.setType(HebrewTokenizer.tokenTypeSignature(HebrewTokenizer.TOKEN_TYPES.Mixed)!!)
                applyLowercaseFilter()
                return true
            }
            if ((tokenType and HebMorphTokenizer.TokenType.Exact) > 0) {
                applyLowercaseFilter()
                return true
            }

            if (keepOriginalWord) {
                stack.add(HebrewToken(word, 0.toByte(), DescFlag.D_EMPTY, word, PrefixType.PS_EMPTY, 1.0f))
            }

            return true
        }

        // Mark and store the original term to increase precision, while all lemmas
        // will be popped out of the stack and get stored at the next call to IncrementToken.
        if (keepOriginalWord) {
            termAtt.copyBuffer(word.toCharArray(), 0, word.length)
            keywordAtt.isKeyword = true
            return true
        }

        // If !keepOriginalWord
        val hebToken = stack[0] as HebrewToken
        if (stack.size == 1) { // only one lemma was found
            stack.clear()
        } else { // // more than one lemma exist.
            index = 1
            previousLemmas.add(hebToken.getLemma())
        }
        createHebrewToken(hebToken)

        return true
    }

    private fun applyLowercaseFilter() {
        val buffer = termAtt.buffer()
        for (i in 0 until termAtt.length) {
            buffer[i] = buffer[i].lowercaseChar()
        }
    }

    protected fun createHebrewToken(hebToken: HebrewToken) {
        val tokenVal = hebToken.getLemma() ?: hebToken.getText().substring(hebToken.getPrefixLength().toInt())
        termAtt.copyBuffer(tokenVal.toCharArray(), 0, tokenVal.length)
        posAtt.setHebrewToken(hebToken)
    }

    override fun end() {
        super.end()
        // set final offset
        val finalOffset = correctOffset(_streamLemmatizer.getEndOffset())
        currentStartOffset = finalOffset
        currentEndOffset = finalOffset
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    override fun close() {
        super.close()
        stack.clear()
        filterCache.clear()
        previousLemmas.clear()
        index = 0
        _streamLemmatizer.reset(input)
    }

    override fun reset() {
        super.reset()
        stack.clear()
        filterCache.clear()
        previousLemmas.clear()
        index = 0
        currentStartOffset = 0
        currentEndOffset = 0
        _streamLemmatizer.reset(input)
    }

    fun setKeepOriginalWord(keepOriginalWord: Boolean) {
        this.keepOriginalWord = keepOriginalWord
    }
}
