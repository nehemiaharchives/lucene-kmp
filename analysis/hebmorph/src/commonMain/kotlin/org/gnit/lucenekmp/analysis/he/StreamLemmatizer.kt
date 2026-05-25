package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.he.datastructures.DictRadix
import org.gnit.lucenekmp.jdkport.Reader

class StreamLemmatizer : Lemmatizer {
    private val _tokenizer: HebMorphTokenizer

    constructor(input: Reader, dict: DictHebMorph) : this(input, dict, null)

    constructor(input: Reader, dict: DictHebMorph, specialTokenizationCases: DictRadix<Byte>?) : super(dict) {
        _tokenizer = HebMorphTokenizer(input, dict.getPref(), specialTokenizationCases)
    }

    fun reset(input: Reader) {
        _tokenizer.reset(input)
        _startOffset = 0
        _endOffset = 0
    }

    private var _startOffset = 0
    private var _endOffset = 0

    fun getStartOffset(): Int = _startOffset

    fun getEndOffset(): Int = _endOffset

    private var tolerateWhenLemmatizingStream = true

    // should only be set to true when querying, and if support for a query like word$
    // is wanted
    fun setSuffixForExactMatch(suffixForExactMatch: Char?) {
        this._tokenizer.setSuffixForExactMatch(suffixForExactMatch)
    }

    fun getLemmatizeNextToken(nextToken: Reference<String>, retTokens: MutableList<Token>): Int {
        retTokens.clear()

        var tokenType: Int
        // Used to loop over certain noise cases
        while (true) {
            tokenType = _tokenizer.nextToken(nextToken)
            _startOffset = _tokenizer.getOffset()
            _endOffset = _startOffset + _tokenizer.getLengthInSource()
            if (tokenType == 0) {
                break // EOS
            }

            if ((tokenType and HebMorphTokenizer.TokenType.Hebrew) > 0) {
                // Right now we are blindly removing all Niqqud characters. Later we will try and make some
                // use of Niqqud for some cases. We do this before everything else to allow for a correct
                // identification of prefixes.
                nextToken.ref = Lemmatizer.removeNiqqud(nextToken.ref)

                // Ignore "words" which are actually only prefixes in a single word.
                // This first case is easy to spot, since the prefix and the following word will be
                // separated by a dash marked as a construct (סמיכות) by the Tokenizer
                if (((tokenType and HebMorphTokenizer.TokenType.Construct) > 0) ||
                    ((tokenType and HebMorphTokenizer.TokenType.Acronym) > 0)
                ) {
                    if (isLegalPrefix(nextToken.ref)) {
                        continue // this should be treated as a word prefix
                    }
                }

                // An exact match request was identified
                // Returning an with an empty stack will force consumer to use the tokenized word,
                // available through the Reference passed to this method
                if ((tokenType and HebMorphTokenizer.TokenType.Exact) > 0) {
                    break // report this as an OOV, will force treating as Exact
                }

                // Strip Hebrew prefixes for mixed words, only if the word itself is a Non-Hebrew word
                // Useful for English company names or numbers that have Hebrew prefixes stuck to them without
                // proper separation
                if ((tokenType and HebMorphTokenizer.TokenType.Mixed) > 0 || (tokenType and HebMorphTokenizer.TokenType.Custom) > 0) {
                    var curChar = 0
                    var startOfNonHebrew: Int
                    while (curChar < nextToken.ref.length && HebrewUtils.isHebrewLetter(nextToken.ref[curChar])) {
                        curChar++
                    }
                    if (curChar > 0 && curChar < nextToken.ref.length - 1 && isLegalPrefix(nextToken.ref.substring(0, curChar))) {
                        startOfNonHebrew = curChar
                        while (curChar < nextToken.ref.length && !HebrewUtils.isHebrewLetter(nextToken.ref[curChar])) {
                            curChar++
                        }
                        if (curChar == nextToken.ref.length) {
                            nextToken.ref = nextToken.ref.substring(startOfNonHebrew, nextToken.ref.length)
                            tokenType = HebMorphTokenizer.TokenType.NonHebrew
                            retTokens.add(Token(nextToken.ref))
                            break
                        }
                    }
                }

                // This second case is a bit more complex. We take a risk of splitting a valid acronym or
                // abbrevated word into two, so we send it to an external function to analyze the word, and
                // get a possibly corrected word. Examples for words we expect to simplify by this operation
                // are ה"שטיח", ש"המידע.
                if ((tokenType and HebMorphTokenizer.TokenType.Acronym) > 0) {
                    nextToken.ref = tryStrippingPrefix(nextToken.ref)

                    // Re-detect acronym, in case it was a false positive
                    if (nextToken.ref.indexOf('"') == -1) {
                        tokenType = tokenType and HebMorphTokenizer.TokenType.Acronym.inv()
                    }
                }

                // TODO: Perhaps by easily identifying the prefixes above we can also rule out some of the
                // stem ambiguities retreived later...

                var lemmas = lemmatize(nextToken.ref)

                if (lemmas.size > 0) {
                    // TODO: Filter Stop Words based on morphological data (hspell 'x' identification)
                    // TODO: Check for worthy lemmas, if there are none then perform tolerant lookup and check again...
                    if ((tokenType and HebMorphTokenizer.TokenType.Construct) > 0) {
                        // TODO: Test for (lemma.Mask & DMask.D_OSMICHUT) > 0
                    }

                    // temp catch-all
                    retTokens.addAll(lemmas)
                }

                if (retTokens.isEmpty() && ((tokenType and HebMorphTokenizer.TokenType.Acronym) > 0)) {
                    // TODO: Perform Gimatria test
                    // TODO: Treat an acronym as a noun and strip affixes accordingly?
                    // TODO: proper values for acronym?
                    retTokens.add(HebrewToken(nextToken.ref, 0.toByte(), DescFlag.D_ACRONYM, nextToken.ref, PrefixType.PS_NONDEF, 1.0f))
                } else if (tolerateWhenLemmatizingStream && retTokens.isEmpty()) {
                    lemmas = lemmatizeTolerant(nextToken.ref)
                    if (lemmas.size > 0) {
                        // TODO: Keep only worthy lemmas, based on characteristics and score / confidence

                        if ((tokenType and HebMorphTokenizer.TokenType.Construct) > 0) {
                            // TODO: Test for (lemma.Mask & DMask.D_OSMICHUT) > 0
                        }

                        // temp catch-all
                        retTokens.addAll(lemmas)
                    } else {
                        // Word unknown to hspell - OOV case
                        // TODO: Right now we store the word as-is. Perhaps we can assume this is a Noun or a name,
                        // and try removing prefixes and suffixes based on that?
                        //retTokens.Add(new HebrewToken(nextToken, 0, 0, null, 1.0f));
                    }
                }
            } else if ((tokenType and HebMorphTokenizer.TokenType.Numeric) > 0) {
                retTokens.add(Token(nextToken.ref, true))
            } else {
                retTokens.add(Token(nextToken.ref))
            }

            break
        }

        return tokenType
    }
}
