package org.gnit.lucenekmp.analysis.charfilter

import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Factory for [HTMLStripCharFilter].
 *
 * @since 3.1
 */
class HTMLStripCharFilterFactory : CharFilterFactory {
    /** Creates a new HTMLStripCharFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        escapedTags = getSet(args, "escapedTags")
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: Reader): HTMLStripCharFilter {
        val escapedTags = escapedTags
        return if (escapedTags == null) {
            HTMLStripCharFilter(input)
        } else {
            HTMLStripCharFilter(input, escapedTags)
        }
    }

    companion object {
        /** SPI name */
        const val NAME: String = "htmlStrip"

        val TAG_NAME_PATTERN: Regex = Regex("[^\\s,]+")
    }

    private val escapedTags: MutableSet<String>?
}
