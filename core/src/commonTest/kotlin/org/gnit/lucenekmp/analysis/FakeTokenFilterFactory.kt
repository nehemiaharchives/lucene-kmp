package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.TokenFilterFactory


/** Fake token filter factory for testing  */
class FakeTokenFilterFactory : TokenFilterFactory {
    /** Create a FakeTokenFilterFactory  */
    constructor(args: MutableMap<String, String>) : super(args)

    /** Default ctor for compatibility with SPI  */
    constructor() {
        defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return input
    }

    companion object {
        const val NAME: String = "fake"
    }
}
