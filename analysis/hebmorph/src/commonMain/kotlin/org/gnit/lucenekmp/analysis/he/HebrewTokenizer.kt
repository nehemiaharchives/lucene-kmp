package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.he.datastructures.DictRadix
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute

/**
 * Tokenizes a given stream using HebMorph's Tokenizer, removes prefixes where possible, and tags Tokens
 * with appropriate types where possible
 */
class HebrewTokenizer : Tokenizer {
    private val hebMorphTokenizer: HebMorphTokenizer
    private val prefixesTree: MutableMap<String, Int>

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val hebTypeAtt: HebrewTokenTypeAttribute

    constructor(prefixes: MutableMap<String, Int>) : this(prefixes, null)

    constructor(_prefixesTree: MutableMap<String, Int>, specialCases: DictRadix<Byte>?) : super() {
        addAttributeImpl(HebrewTokenTypeAttributeImpl())
        hebTypeAtt = addAttribute(HebrewTokenTypeAttribute::class)
        hebMorphTokenizer = HebMorphTokenizer(input, _prefixesTree, specialCases)
        prefixesTree = _prefixesTree
    }

    fun setSuffixForExactMatch(suffixForExactMatch: Char?) {
        this.hebMorphTokenizer.setSuffixForExactMatch(suffixForExactMatch)
    }

    interface TOKEN_TYPES {
        companion object {
            const val Hebrew = 0
            const val NonHebrew = 1
            const val Numeric = 2
            const val Construct = 3
            const val Acronym = 4
            const val Mixed = 5
        }
    }

    override fun incrementToken(): Boolean {
        clearAttributes()
        val nextToken = Reference("")
        var nextTokenVal: String
        var tokenType: Int
        while (true) {
            tokenType = hebMorphTokenizer.nextToken(nextToken)
            nextTokenVal = nextToken.ref
            if (tokenType == 0) return false
            if ((tokenType and HebMorphTokenizer.TokenType.Hebrew) > 0) {
                if ((tokenType and HebMorphTokenizer.TokenType.Construct) > 0) {
                    if (isLegalPrefix(nextToken.ref)) continue
                }
                if ((tokenType and HebMorphTokenizer.TokenType.Acronym) > 0) {
                    nextTokenVal = tryStrippingPrefix(nextToken.ref)
                    nextToken.ref = nextTokenVal
                    if (nextTokenVal.indexOf('"') == -1) {
                        tokenType = tokenType and HebMorphTokenizer.TokenType.Acronym.inv()
                    }
                }
            }
            break
        }
        termAtt.copyBuffer(nextTokenVal.toCharArray(), 0, nextTokenVal.length)
        offsetAtt.setOffset(
            correctOffset(hebMorphTokenizer.getOffset()),
            correctOffset(hebMorphTokenizer.getOffset() + hebMorphTokenizer.getLengthInSource())
        )
        if ((tokenType and HebMorphTokenizer.TokenType.Exact) > 0) hebTypeAtt.setExact(true)
        if ((tokenType and HebMorphTokenizer.TokenType.Hebrew) > 0) {
            if ((tokenType and HebMorphTokenizer.TokenType.Acronym) > 0) {
                hebTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.Acronym)
            } else if ((tokenType and HebMorphTokenizer.TokenType.Construct) > 0) {
                hebTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.Construct)
            } else {
                hebTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.Hebrew)
            }
        } else if ((tokenType and HebMorphTokenizer.TokenType.Numeric) > 0) {
            hebTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.Numeric)
        } else {
            hebTypeAtt.setType(HebrewTokenTypeAttribute.HebrewType.NonHebrew)
        }
        return true
    }

    override fun end() {
        super.end()
        val finalOffset = correctOffset(hebMorphTokenizer.getOffset())
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    override fun close() {
        super.close()
        hebMorphTokenizer.reset(input)
    }

    override fun reset() {
        super.reset()
        hebMorphTokenizer.reset(input)
    }

    fun isLegalPrefix(str: String): Boolean {
        return prefixesTree.containsKey(str)
    }

    fun tryStrippingPrefix(word: String): String {
        val firstQuote = word.indexOf('"')
        if (firstQuote > -1 && firstQuote < word.length - 2) {
            if (isLegalPrefix(word.substring(0, firstQuote))) {
                return word.substring(firstQuote + 1, firstQuote + 1 + word.length - firstQuote - 1)
            }
        }
        val firstSingleQuote = word.indexOf('\'')
        if (firstSingleQuote == -1) return word
        if ((firstQuote > -1) && (firstSingleQuote > firstQuote)) return word
        if (isLegalPrefix(word.substring(0, firstSingleQuote))) {
            return word.substring(firstSingleQuote + 1, firstSingleQuote + 1 + word.length - firstSingleQuote - 1)
        }
        return word
    }

    companion object {
        val TOKEN_TYPE_SIGNATURES = arrayOf("<HEBREW>", "<NON_HEBREW>", "<NUM>", "<CONSTRUCT>", "<ACRONYM>", "<MIXED>", null)

        fun tokenTypeSignature(tokenType: Int): String? {
            return TOKEN_TYPE_SIGNATURES[tokenType]
        }
    }
}
