/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 */
package org.gnit.lucenekmp.analysis.compound

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/** Factory for [DictionaryCompoundWordTokenFilter]. */
class DictionaryCompoundWordTokenFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    private var dictionary: CharArraySet? = null
    private var dictFile: String = ""
    private var minWordSize: Int = 0
    private var minSubwordSize: Int = 0
    private var maxSubwordSize: Int = 0
    private var onlyLongestMatch: Boolean = false
    private var reuseChars: Boolean = false

    /** Creates a new DictionaryCompoundWordTokenFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        dictFile = require(args, "dictionary")
        minWordSize = getInt(args, "minWordSize", CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE)
        minSubwordSize =
            getInt(args, "minSubwordSize", CompoundWordTokenFilterBase.DEFAULT_MIN_SUBWORD_SIZE)
        maxSubwordSize =
            getInt(args, "maxSubwordSize", CompoundWordTokenFilterBase.DEFAULT_MAX_SUBWORD_SIZE)
        onlyLongestMatch = getBoolean(args, "onlyLongestMatch", true)
        reuseChars = getBoolean(args, "reuseChars", true)
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        dictionary = super.getWordSet(loader, dictFile, false)
    }

    override fun create(input: TokenStream): TokenStream {
        // if the dictionary is null, it means it was empty
        val dictionary = dictionary ?: return input
        return DictionaryCompoundWordTokenFilter(
            input,
            dictionary,
            minWordSize,
            minSubwordSize,
            maxSubwordSize,
            onlyLongestMatch,
            reuseChars
        )
    }

    companion object {
        /** SPI name */
        const val NAME: String = "dictionaryCompoundWord"
    }
}
