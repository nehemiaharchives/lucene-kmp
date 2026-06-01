package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [TypeAsSynonymFilter].
 *
 * In Solr this might be used as such
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_type_as_synonym" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.UAX29URLEmailTokenizerFactory"/&gt;
 *     &lt;filter class="solr.TypeAsSynonymFilterFactory" prefix="_type_" synFlagsMask="5" ignore="foo,bar"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * <p>If the optional `prefix` parameter is used, the specified value will be prepended to the
 * type, e.g. with prefix="_type_", for a token "example.com" with type "&lt;URL&gt;", the emitted
 * synonym will have text "_type_&lt;URL&gt;". If the optional synFlagsMask is used then the flags
 * on the synonym will be set to `synFlagsMask & tokenFlags`. The example above
 * transfers only the lowest and third lowest bits. If no mask is set then all flags are
 * transferred. The ignore parameter can be used to avoid creating synonyms for some types.
 *
 * @since 7.3.0
 * @lucene.spi {@value #NAME}
 */
class TypeAsSynonymFilterFactory : TokenFilterFactory {
    private var prefix: String? = null
    private var ignore: Set<String>? = null
    private var synFlagMask: Int by Delegates.notNull()

    constructor(args: MutableMap<String, String>) : super(args) {
        prefix = get(args, "prefix")
        ignore = getSet(args, "ignore")
        synFlagMask = getInt(args, "synFlagsMask", 0.inv())
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return TypeAsSynonymFilter(input, prefix, ignore, synFlagMask)
    }

    companion object {
        /** SPI name */
        const val NAME = "typeAsSynonym"
    }
}
