package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [ConcatenateGraphFilter].
 *
 * - `tokenSeparator`: Separator to use for concatenation. If not present, [ConcatenateGraphFilter.DEFAULT_TOKEN_SEPARATOR] will be used. If empty, tokens will be
 *       concatenated without any separators.
 * - `preservePositionIncrements`: Whether to add an empty token for missing
 *       positions. The effect is a consecutive [ConcatenateGraphFilter.SEP_LABEL]. When
 *       false, it's as if there were no missing positions (we pretend the surrounding tokens were
 *       adjacent).
 * - `maxGraphExpansions`: If the tokenStream graph has more than this many possible
 *       paths through, then we'll throw [org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException] to preserve the
 *       stability and memory of the machine.
 *
 * @see ConcatenateGraphFilter
 * @since 7.4.0
 * @lucene.spi [NAME]
 */
class ConcatenateGraphFilterFactory : TokenFilterFactory {
    companion object {
        /** SPI name */
        const val NAME = "concatenateGraph"
    }

    private var tokenSeparator: Char? = null
    private var preservePositionIncrements = false
    private var maxGraphExpansions = 0

    constructor(args: MutableMap<String, String>) : super(args) {
        tokenSeparator = getCharacter(args, "tokenSeparator", ConcatenateGraphFilter.DEFAULT_TOKEN_SEPARATOR)
        preservePositionIncrements =
            getBoolean(args, "preservePositionIncrements", ConcatenateGraphFilter.DEFAULT_PRESERVE_POSITION_INCREMENTS)
        maxGraphExpansions =
            getInt(args, "maxGraphExpansions", ConcatenateGraphFilter.DEFAULT_MAX_GRAPH_EXPANSIONS)

        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : super() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return ConcatenateGraphFilter(input, tokenSeparator, preservePositionIncrements, maxGraphExpansions)
    }

    protected fun getCharacter(args: MutableMap<String, String>, name: String, defaultVal: Char?): Char? {
        val s = args.remove(name)
        return if (s == null) {
            defaultVal
        } else if (s.isEmpty()) {
            null
        } else {
            require(s.length == 1) { "$name should be a char. \"$s\" is invalid" }
            s[0]
        }
    }
}
