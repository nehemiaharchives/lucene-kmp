package org.gnit.lucenekmp.analysis.cjk

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [CJKBigramFilter].
 *
 * @since 3.6.0
 * @lucene.spi {@value #NAME}
 */
class CJKBigramFilterFactory : TokenFilterFactory {
    private var flags: Int = 0
    private var outputUnigrams: Boolean = false

    /** Creates a new CJKBigramFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        var flags = 0
        if (getBoolean(args, "han", true)) {
            flags = flags or CJKBigramFilter.HAN
        }
        if (getBoolean(args, "hiragana", true)) {
            flags = flags or CJKBigramFilter.HIRAGANA
        }
        if (getBoolean(args, "katakana", true)) {
            flags = flags or CJKBigramFilter.KATAKANA
        }
        if (getBoolean(args, "hangul", true)) {
            flags = flags or CJKBigramFilter.HANGUL
        }
        this.flags = flags
        this.outputUnigrams = getBoolean(args, "outputUnigrams", false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return CJKBigramFilter(input, flags, outputUnigrams)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "cjkBigram"
    }
}
