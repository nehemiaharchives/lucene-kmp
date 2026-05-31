package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute

/** Removes tokens whose types appear in a set of blocked types from a token stream. */
class TypeTokenFilter : FilteringTokenFilter {
    private val stopTypes: Set<String>
    private val typeAttribute: TypeAttribute = addAttribute(TypeAttribute::class)
    private val useWhiteList: Boolean

    /**
     * Create a new [TypeTokenFilter].
     *
     * @param input the [TokenStream] to consume
     * @param stopTypes the types to filter
     * @param useWhiteList if true, then tokens whose type is in stopTypes will be kept, otherwise
     *     they will be filtered out
     */
    constructor(input: TokenStream, stopTypes: Set<String>, useWhiteList: Boolean) : super(input) {
        this.stopTypes = requireNotNull(stopTypes) { "stopTypes" }
        this.useWhiteList = useWhiteList
    }

    /** Create a new [TypeTokenFilter] that filters tokens out (useWhiteList=false). */
    constructor(input: TokenStream, stopTypes: Set<String>) : this(input, stopTypes, false)

    /**
     * By default accept the token if its type is not a stop type. When the useWhiteList parameter is
     * set to true then accept the token if its type is contained in the stopTypes
     */
    override fun accept(): Boolean {
        return useWhiteList == stopTypes.contains(typeAttribute.type())
    }
}

