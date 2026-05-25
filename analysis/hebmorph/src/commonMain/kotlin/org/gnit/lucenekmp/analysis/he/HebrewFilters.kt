package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

class NiqqudFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false
        val buffer = termAtt.buffer()
        val length = termAtt.length
        var j = 0
        for (i in 0 until length) {
            if (buffer[i].code < 1455 || buffer[i].code > 1476) {
                buffer[j++] = buffer[i]
            }
        }
        termAtt.setLength(j)
        return true
    }
}

class AddSuffixTokenFilter(input: TokenStream, private val suffix: Char) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val hebTypeAtt: HebrewTokenTypeAttribute

    init {
        addAttributeImpl(HebrewTokenTypeAttributeImpl())
        hebTypeAtt = addAttribute(HebrewTokenTypeAttribute::class)
    }

    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false
        if (hebTypeAtt.isHebrew() || hebTypeAtt.getType() == HebrewTokenTypeAttribute.HebrewType.NonHebrew) {
            termAtt.append(suffix)
        }
        return true
    }
}

class MarkHebrewTokensFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val hebTypeAtt: HebrewTokenTypeAttribute

    init {
        addAttributeImpl(HebrewTokenTypeAttributeImpl())
        hebTypeAtt = addAttribute(HebrewTokenTypeAttribute::class)
    }

    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false
        val buffer = termAtt.buffer()
        for (i in 0 until termAtt.length) {
            if (!HebrewUtils.isHebrewLetter(buffer[i]) && !HebrewUtils.isNiqqudChar(buffer[i]) &&
                !HebrewUtils.isOfChars(buffer[i], HebrewUtils.Gershayim) &&
                !HebrewUtils.isOfChars(buffer[i], HebrewUtils.Geresh)
            ) {
                if (buffer[0].isDigit()) hebTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.Numeric)
                else hebTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.NonHebrew)
                return true
            }
        }
        hebTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.Hebrew)
        return true
    }
}

class IgnoreOriginalTokenFilter(input: TokenStream) : FilteringTokenFilter(input) {
    private val hebTypeAtt: HebrewTokenTypeAttribute

    init {
        addAttributeImpl(HebrewTokenTypeAttributeImpl())
        hebTypeAtt = addAttribute(HebrewTokenTypeAttribute::class)
    }

    override fun accept(): Boolean {
        return !hebTypeAtt.isHebrew() || hebTypeAtt.isExact()
    }
}

class HebrewLemmatizerTokenFilter : TokenFilter {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val hebrewTypeAtt: HebrewTokenTypeAttribute
    private val posAtt: HebrewPosAttribute
    private var lemmatizer: Lemmatizer
    private var previousLemmas: MutableList<Token> = ArrayList()
    private var previousStartOffset = 0
    private var previousEndOffset = 0
    private var previousTolerated = false
    private var lemmatizeExactHebrewWords: Boolean
    private var lemmatizeExactNonHebrewWords: Boolean
    private var previousType: HebrewTokenTypeAttribute.HebrewType = HebrewTokenTypeAttribute.HebrewType.Unknown
    private val duplicateLemmas: MutableSet<String?> = HashSet(20)
    private val tokensList: MutableList<HebrewToken> = ArrayList(20)

    constructor(input: TokenStream, dict: DictHebMorph) : this(input, dict, true, true)

    constructor(input: TokenStream, dict: DictHebMorph, lemmatizeExactHebrewWords: Boolean, lemmatizeExactNonHebrewWords: Boolean) : super(input) {
        addAttributeImpl(HebrewTokenTypeAttributeImpl())
        addAttributeImpl(HebrewPosAttributeImpl())
        hebrewTypeAtt = addAttribute(HebrewTokenTypeAttribute::class)
        posAtt = addAttribute(HebrewPosAttribute::class)
        this.lemmatizer = Lemmatizer(dict)
        this.lemmatizeExactHebrewWords = lemmatizeExactHebrewWords
        this.lemmatizeExactNonHebrewWords = lemmatizeExactNonHebrewWords
    }

    override fun incrementToken(): Boolean {
        if (previousLemmas.isNotEmpty()) {
            clearAttributes()
            val tokenVal: String
            if (previousType == HebrewTokenTypeAttribute.HebrewType.Hebrew ||
                previousType == HebrewTokenTypeAttribute.HebrewType.Acronym ||
                previousType == HebrewTokenTypeAttribute.HebrewType.Construct
            ) {
                val hebToken = previousLemmas.removeAt(0) as HebrewToken
                tokenVal = hebToken.getLemma() ?: hebToken.getText().substring(hebToken.getPrefixLength().toInt())
                posAtt.setHebrewToken(hebToken)
            } else {
                tokenVal = previousLemmas.removeAt(0).getText()
                posAtt.setHebrewToken(null)
            }
            termAtt.setEmpty()!!.append(tokenVal)
            hebrewTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.Lemma)
            posIncrAtt.setPositionIncrement(0)
            offsetAtt.setOffset(previousStartOffset, previousEndOffset)
            return true
        }
        if (!input.incrementToken()) return false
        if (hebrewTypeAtt.isNumeric() || (hebrewTypeAtt.isExact() &&
                ((!lemmatizeExactHebrewWords && hebrewTypeAtt.isHebrew()) ||
                    (!lemmatizeExactNonHebrewWords && hebrewTypeAtt.getType() == HebrewTokenTypeAttribute.HebrewType.NonHebrew)))
        ) {
            return true
        }
        previousLemmas.clear()
        duplicateLemmas.clear()
        previousStartOffset = offsetAtt.startOffset()
        previousEndOffset = offsetAtt.endOffset()
        previousType = hebrewTypeAtt.getType()
        if (hebrewTypeAtt.isHebrew()) {
            previousTolerated = false
            val word = termAtt.toString()
            tokensList.clear()
            lemmatizer.lemmatize(word, tokensList)
            if (tokensList.isEmpty()) {
                lemmatizer.lemmatizeTolerant(word, tokensList)
                previousTolerated = true
            }
            tokensList.sortDescending()
            for (hebToken in tokensList) {
                if (isValidToken(hebToken) || !previousTolerated) {
                    if (duplicateLemmas.add(hebToken.getLemma())) previousLemmas.add(hebToken)
                }
            }
            if (tokensList.isNotEmpty() && previousLemmas.isEmpty()) {
                for (hebToken in tokensList) {
                    if (duplicateLemmas.add(hebToken.getLemma())) previousLemmas.add(hebToken)
                }
            }
            if (previousLemmas.isEmpty()) {
                previousLemmas.add(HebrewToken(termAtt.toString(), 0, DescFlag.D_EMPTY, word, PrefixType.PS_EMPTY, 1.0f))
            }
        } else {
            previousLemmas.add(Token(termAtt.toString()))
        }
        return true
    }

    fun isValidToken(t: HebrewToken): Boolean {
        if (t.getScore() < 0.7f) return false
        if (t.getMask() == DescFlag.D_VERB && t.getScore() < 0.85f) return false
        return true
    }
}
