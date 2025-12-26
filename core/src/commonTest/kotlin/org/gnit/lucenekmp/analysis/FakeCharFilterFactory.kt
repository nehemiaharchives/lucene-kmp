package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.Reader

/** Fake char filter factory for testing  */
class FakeCharFilterFactory : CharFilterFactory {
    /** Create a FakeCharFilterFactory  */
    constructor(args: MutableMap<String, String>) : super(args)

    /** Default ctor for compatibility with SPI  */
    constructor() {
        defaultCtorException()
    }

    override fun create(input: Reader): Reader {
        return input
    }

    companion object {
        const val NAME: String = "fake"
    }
}
