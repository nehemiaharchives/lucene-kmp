package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/** Factory for [JapaneseKatakanaStemFilter]. */
class JapaneseKatakanaStemFilterFactory : TokenFilterFactory {
    private var minimumLength by Delegates.notNull<Int>()

    /** Creates a new JapaneseKatakanaStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        minimumLength = getInt(args, MINIMUM_LENGTH_PARAM, JapaneseKatakanaStemFilter.DEFAULT_MINIMUM_LENGTH)
        require(minimumLength >= 2) {
            "Illegal $MINIMUM_LENGTH_PARAM $minimumLength (must be 2 or greater)"
        }
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return JapaneseKatakanaStemFilter(input, minimumLength)
    }

    companion object {
        const val NAME: String = "japaneseKatakanaStem"
        private const val MINIMUM_LENGTH_PARAM: String = "minimumLength"
    }
}
