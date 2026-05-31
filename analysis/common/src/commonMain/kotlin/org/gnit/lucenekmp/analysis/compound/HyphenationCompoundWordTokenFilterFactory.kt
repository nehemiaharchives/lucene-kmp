/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 */
package org.gnit.lucenekmp.analysis.compound

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.compound.hyphenation.HyphenationTree
import org.gnit.lucenekmp.jdkport.InputSource
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/** Factory for [HyphenationCompoundWordTokenFilter]. */
class HyphenationCompoundWordTokenFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    private var dictionary: CharArraySet? = null
    private var hyphenator: HyphenationTree? = null
    private var dictFile: String? = null
    private var hypFile: String = ""
    private var encoding: String? = null
    private var minWordSize: Int = 0
    private var minSubwordSize: Int = 0
    private var maxSubwordSize: Int = 0
    private var onlyLongestMatch: Boolean = false
    private var noSubMatches: Boolean = false
    private var noOverlappingMatches: Boolean = false

    /** Creates a new HyphenationCompoundWordTokenFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        dictFile = get(args, "dictionary")
        encoding = get(args, "encoding")
        hypFile = require(args, "hyphenator")
        minWordSize = getInt(args, "minWordSize", CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE)
        minSubwordSize =
            getInt(args, "minSubwordSize", CompoundWordTokenFilterBase.DEFAULT_MIN_SUBWORD_SIZE)
        maxSubwordSize =
            getInt(args, "maxSubwordSize", CompoundWordTokenFilterBase.DEFAULT_MAX_SUBWORD_SIZE)
        onlyLongestMatch = getBoolean(args, "onlyLongestMatch", false)
        noSubMatches = getBoolean(args, "noSubMatches", false)
        noOverlappingMatches = getBoolean(args, "noOverlappingMatches", false)
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
        val dictFile = dictFile
        if (dictFile != null) // the dictionary can be empty.
            dictionary = getWordSet(loader, dictFile, false)
        // TODO: Broken, because we cannot resolve real system id
        // ResourceLoader should also supply method like ClassLoader to get resource URL
        loader.openResource(hypFile).use { stream ->
            val inputSource = InputSource(stream)
            inputSource.encoding = encoding // if it's null let xml parser decide
            inputSource.systemId = hypFile
            hyphenator = HyphenationCompoundWordTokenFilter.getHyphenationTree(inputSource)
        }
    }

    override fun create(input: TokenStream): TokenFilter {
        return HyphenationCompoundWordTokenFilter(
            input,
            hyphenator!!,
            dictionary,
            minWordSize,
            minSubwordSize,
            maxSubwordSize,
            onlyLongestMatch,
            noSubMatches,
            noOverlappingMatches
        )
    }

    companion object {
        /** SPI name */
        const val NAME: String = "hyphenationCompoundWord"
    }
}
