package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.he.datastructures.DictRadix
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer

abstract class HebrewAnalyzer : Analyzer {
    protected var dict: DictHebMorph
    protected val originalTermSuffix = '$'
    protected var SPECIAL_TOKENIZATION_CASES: DictRadix<Byte>? = null
    protected var commonWords: CharArraySet? = null

    protected constructor(dict: DictHebMorph) : super() {
        this.dict = dict
    }

    constructor() : this(HSpellDictionaryLoader().loadDictionaryFromDefaultPath())

    fun setCustomTokenizationCases(wordsList: Iterable<String>?): DictRadix<Byte>? {
        if (wordsList != null) {
            val radix = DictRadix<Byte>(false)
            for (word in wordsList) {
                radix.addNode(word.toCharArray(), 0)
            }
            SPECIAL_TOKENIZATION_CASES = radix
        }
        return SPECIAL_TOKENIZATION_CASES
    }

    fun isRecognizedWord(word: String, tolerate: Boolean): WordType {
        return isRecognizedWord(word, tolerate, this.dict)
    }

    companion object {
        private val dummyData: Byte = 0

        fun isHebrewWord(word: CharSequence): Boolean {
            for (i in 0 until word.length) {
                if (HebrewUtils.isHebrewLetter(word[i])) return true
            }
            return false
        }

        fun isRecognizedWord(word: String, tolerate: Boolean, dict: DictHebMorph): WordType {
            var prefLen: Byte = 0
            var prefixMask: Int?
            var md: MorphData?
            val prefixesTree = dict.getPref()
            val dictRadix = dict.getRadix()
            if (!isHebrewWord(word)) return WordType.NON_HEBREW
            if (dict.lookup(word) != null) return WordType.HEBREW
            if (word.endsWith("'")) {
                if (dict.lookup(word.substring(0, word.length - 1)) != null) return WordType.HEBREW
            }
            prefLen = 0
            while (true) {
                if (word.length - prefLen < 2) break
                prefLen++
                prefixMask = prefixesTree[word.substring(0, prefLen.toInt())]
                if (prefixMask == null) break
                md = dict.lookup(word.substring(prefLen.toInt()))
                if (md != null && ((md.getPrefixes() and prefixMask) > 0)) {
                    for (lemma in md.getLemmas()) {
                        if ((lemma.getPrefix().getValue().toInt() and prefixMask) > 0) {
                            return WordType.HEBREW_WITH_PREFIX
                        }
                    }
                }
            }
            if (tolerate) {
                if (word.length > 19) return WordType.UNRECOGNIZED
                var tolerated = dictRadix.lookupTolerant(word, LookupTolerators.TolerateEmKryiaAll)
                if (tolerated != null && tolerated.isNotEmpty()) return WordType.HEBREW_TOLERATED
                prefLen = 0
                while (true) {
                    if (word.length - prefLen < 2) break
                    prefLen++
                    prefixMask = prefixesTree[word.substring(0, prefLen.toInt())]
                    if (prefixMask == null) break
                    tolerated = dictRadix.lookupTolerant(word.substring(prefLen.toInt()), LookupTolerators.TolerateEmKryiaAll)
                    if (tolerated != null) {
                        for (lr in tolerated) {
                            for (lemma in lr.getData().getLemmas()) {
                                if ((lemma.getPrefix().getValue().toInt() and prefixMask) > 0) {
                                    return WordType.HEBREW_TOLERATED_WITH_PREFIX
                                }
                            }
                        }
                    }
                }
            }
            return WordType.UNRECOGNIZED
        }
    }
}

class HebrewExactAnalyzer : HebrewAnalyzer {
    constructor(dict: DictHebMorph) : super(dict)
    constructor() : super()

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src = HebrewTokenizer(dict.getPref(), SPECIAL_TOKENIZATION_CASES)
        src.setSuffixForExactMatch(originalTermSuffix)
        var tok: TokenStream = NiqqudFilter(src)
        tok = ASCIIFoldingFilter(tok)
        tok = LowerCaseFilter(tok)
        tok = AddSuffixTokenFilter(tok, '$')
        return TokenStreamComponents(src, tok)
    }
}

class HebrewIndexingAnalyzer : HebrewAnalyzer {
    constructor(dict: DictHebMorph) : super(dict)
    constructor() : super()

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src: Tokenizer = StandardTokenizer()
        var tok: TokenStream = NiqqudFilter(src)
        tok = ASCIIFoldingFilter(tok)
        tok = LowerCaseFilter(tok)
        tok = MarkHebrewTokensFilter(tok)
        tok = HebrewLemmatizerTokenFilter(tok, dict)
        tok = AddSuffixTokenFilter(tok, '$')
        return TokenStreamComponents(src, tok)
    }
}

class HebrewQueryAnalyzer : HebrewAnalyzer {
    constructor(dict: DictHebMorph) : super(dict)
    constructor() : super()

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src: Tokenizer = StandardTokenizer()
        var tok: TokenStream = NiqqudFilter(src)
        tok = ASCIIFoldingFilter(tok)
        tok = LowerCaseFilter(tok)
        tok = MarkHebrewTokensFilter(tok)
        tok = HebrewLemmatizerTokenFilter(tok, dict, false, true)
        tok = AddSuffixTokenFilter(tok, '$')
        return TokenStreamComponents(src, tok)
    }
}

class HebrewQueryLightAnalyzer : HebrewAnalyzer {
    constructor(dict: DictHebMorph) : super(dict)
    constructor() : super()

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src = HebrewTokenizer(dict.getPref(), SPECIAL_TOKENIZATION_CASES)
        src.setSuffixForExactMatch(originalTermSuffix)
        var tok: TokenStream = NiqqudFilter(src)
        tok = ASCIIFoldingFilter(tok)
        tok = LowerCaseFilter(tok)
        tok = HebrewLemmatizerTokenFilter(tok, dict, false, false)
        tok = IgnoreOriginalTokenFilter(tok)
        tok = AddSuffixTokenFilter(tok, '$')
        return TokenStreamComponents(src, tok)
    }
}

class HebrewLegacyIndexingAnalyzer : HebrewAnalyzer {
    constructor(dict: DictHebMorph) : super(dict)
    constructor() : super()

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src = HebrewTokenizer(dict.getPref(), SPECIAL_TOKENIZATION_CASES)
        src.setSuffixForExactMatch(originalTermSuffix)
        var tok: TokenStream = NiqqudFilter(src)
        tok = ASCIIFoldingFilter(tok)
        tok = LowerCaseFilter(tok)
        tok = HebrewLemmatizerTokenFilter(tok, dict)
        tok = AddSuffixTokenFilter(tok, '$')
        return TokenStreamComponents(src, tok)
    }
}

class HebrewLegacyQueryAnalyzer : HebrewAnalyzer {
    constructor(dict: DictHebMorph) : super(dict)
    constructor() : super()

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src = HebrewTokenizer(dict.getPref(), SPECIAL_TOKENIZATION_CASES)
        src.setSuffixForExactMatch(originalTermSuffix)
        var tok: TokenStream = NiqqudFilter(src)
        tok = ASCIIFoldingFilter(tok)
        tok = LowerCaseFilter(tok)
        tok = HebrewLemmatizerTokenFilter(tok, dict, false, true)
        tok = AddSuffixTokenFilter(tok, '$')
        return TokenStreamComponents(src, tok)
    }
}
