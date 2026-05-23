package org.gnit.lucenekmp.analysis.fa

import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.jdkport.Reader

/** Factory for [PersianCharFilter]. */
class PersianCharFilterFactory : CharFilterFactory {
    /** Creates a new PersianCharFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: Reader): Reader {
        return PersianCharFilter(input)
    }

    override fun normalize(input: Reader): Reader {
        return create(input)
    }

    companion object {
        const val NAME: String = "persian"
    }
}

