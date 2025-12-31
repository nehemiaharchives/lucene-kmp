package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [JapaneseHiraganaUppercaseFilter]. */
class JapaneseHiraganaUppercaseFilterFactory : TokenFilterFactory {
    /** Creates a new JapaneseHiraganaUppercaseFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return JapaneseHiraganaUppercaseFilter(input)
    }

    companion object {
        const val NAME: String = "japaneseHiraganaUppercase"
    }
}
