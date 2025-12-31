package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for {@link JapaneseCompletionFilter}.
 *
 * <p>Supported attributes:
 *
 * <ul>
 *   <li>mode: Completion mode. see {@link JapaneseCompletionFilter.Mode}
 * </ul>
 *
 * @lucene.spi {@value #NAME}
 */
class JapaneseCompletionFilterFactory : TokenFilterFactory {
    private lateinit var mode: JapaneseCompletionFilter.Mode

    /** Creates a new [JapaneseCompletionFilterFactory]. */
    constructor(args: MutableMap<String, String>) : super(args) {
        val rawMode = get(args, MODE_PARAM, JapaneseCompletionFilter.DEFAULT_MODE.name).uppercase()
        mode = JapaneseCompletionFilter.Mode.valueOf(rawMode)
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return JapaneseCompletionFilter(input, mode)
    }

    companion object {
        const val NAME: String = "japaneseCompletion"
        private const val MODE_PARAM: String = "mode"
    }
}
