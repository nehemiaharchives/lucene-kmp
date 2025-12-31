package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [JapaneseKatakanaUppercaseFilter]. */
class JapaneseKatakanaUppercaseFilterFactory : TokenFilterFactory {
    /** Creates a new JapaneseKatakanaUppercaseFilterFactory */
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
        return JapaneseKatakanaUppercaseFilter(input)
    }

    companion object {
        const val NAME: String = "japaneseKatakanaUppercase"
    }
}
