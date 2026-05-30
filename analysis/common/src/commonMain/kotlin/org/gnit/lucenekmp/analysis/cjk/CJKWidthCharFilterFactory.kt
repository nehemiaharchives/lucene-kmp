package org.gnit.lucenekmp.analysis.cjk

import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Factory for [CJKWidthCharFilter].
 *
 * @lucene.spi {@value #NAME}
 */
class CJKWidthCharFilterFactory : CharFilterFactory {
    /** Creates a new CJKWidthCharFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: Reader): Reader {
        return CJKWidthCharFilter(input)
    }

    override fun normalize(input: Reader): Reader {
        return create(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "cjkWidth"
    }
}
