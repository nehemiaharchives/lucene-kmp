package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.cjk.CJKWidthCharFilter
import org.gnit.lucenekmp.analysis.ja.dict.UserDictionary
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Analyzer for Japanese completion suggester.
 *
 * @see JapaneseCompletionFilter
 */
class JapaneseCompletionAnalyzer : Analyzer {
    private val mode: JapaneseCompletionFilter.Mode
    private val userDict: UserDictionary?

    /** Creates a new [JapaneseCompletionAnalyzer] with default configurations. */
    constructor() : this(null, JapaneseCompletionFilter.Mode.INDEX)

    /** Creates a new [JapaneseCompletionAnalyzer]. */
    constructor(userDict: UserDictionary?, mode: JapaneseCompletionFilter.Mode) {
        this.userDict = userDict
        this.mode = mode
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer: Tokenizer = JapaneseTokenizer(userDict, true, true, JapaneseTokenizer.Mode.NORMAL)
        var stream: TokenStream = JapaneseCompletionFilter(tokenizer, mode)
        stream = LowerCaseFilter(stream)
        return TokenStreamComponents(tokenizer, stream)
    }

    override fun initReader(fieldName: String, reader: Reader): Reader {
        return CJKWidthCharFilter(reader)
    }

    override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
        return CJKWidthCharFilter(reader)
    }
}
