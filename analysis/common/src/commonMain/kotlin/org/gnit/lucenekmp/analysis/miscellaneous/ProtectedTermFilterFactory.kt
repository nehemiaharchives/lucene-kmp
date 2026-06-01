package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.ResourceLoader

/**
 * Factory for a [ProtectedTermFilter]
 *
 * <p>CustomAnalyzer example:
 *
 * <pre class="prettyprint">
 * Analyzer ana = CustomAnalyzer.builder()
 *   .withTokenizer("standard")
 *   .when("protectedterm", "ignoreCase", "true", "protected", "protectedTerms.txt")
 *     .addTokenFilter("truncate", "prefixLength", "4")
 *     .addTokenFilter("lowercase")
 *   .endwhen()
 *   .build();
 * </pre>
 *
 * <p>Solr example, in which conditional filters are specified via the <code>wrappedFilters</code>
 * parameter - a comma-separated list of case-insensitive TokenFilter SPI names - and conditional
 * filter args are specified via <code>filterName.argName</code> parameters:
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="reverse_lower_with_exceptions" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ProtectedTermFilterFactory" ignoreCase="true" protected="protectedTerms.txt"
 *             wrappedFilters="truncate,lowercase" truncate.prefixLength="4" /&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * <p>When using the <code>wrappedFilters</code> parameter, each filter name must be unique, so if
 * you need to specify the same filter more than once, you must add case-insensitive unique '-id'
 * suffixes (note that the '-id' suffix is stripped prior to SPI lookup), e.g.:
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="double_synonym_with_exceptions" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ProtectedTermFilterFactory" ignoreCase="true" protected="protectedTerms.txt"
 *             wrappedFilters="synonymgraph-A,synonymgraph-B"
 *             synonymgraph-A.synonyms="synonyms-1.txt"
 *             synonymgraph-B.synonyms="synonyms-2.txt"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 7.4.0
 */
class ProtectedTermFilterFactory(args: MutableMap<String, String>) : ConditionalTokenFilterFactory(args) {
    private val termFiles = require(args, PROTECTED_TERMS)
    private val ignoreCase = getBoolean(args, "ignoreCase", false)
    private val wrappedFilters = get(args, "wrappedFilters")

    private var protectedTerms: CharArraySet? = null

    init {
        if (wrappedFilters != null) {
            handleWrappedFilterArgs(args)
        }
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    private fun handleWrappedFilterArgs(args: MutableMap<String, String>) {
        val wrappedFilterArgs = linkedMapOf<String, MutableMap<String, String>>()
        splitAt(',', wrappedFilters!!).forEach { filterNameValue ->
            val filterName = filterNameValue.trim().lowercase()
            if (wrappedFilterArgs.containsKey(filterName)) {
                throw IllegalArgumentException(
                    "wrappedFilters contains duplicate '$filterName'. Add unique '-id' suffixes (stripped prior to SPI lookup)."
                )
            }
            wrappedFilterArgs[filterName] = hashMapOf()
        }
        val iterator = args.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val filterArgKey = entry.key
            val argValue = entry.value
            val splitKey = splitAt(FILTER_ARG_SEPARATOR, filterArgKey)
            if (splitKey.size == 2) {
                val filterName = splitKey[0].lowercase()
                if (wrappedFilterArgs.containsKey(filterName)) {
                    val filterArgs = wrappedFilterArgs.getOrPut(filterName) { hashMapOf() }
                    val argKey = splitKey[1]
                    filterArgs[argKey] = argValue
                    iterator.remove()
                }
            }
        }
        if (args.isEmpty()) {
            populateInnerFilters(wrappedFilterArgs)
        }
    }

    private fun populateInnerFilters(wrappedFilterArgs: LinkedHashMap<String, MutableMap<String, String>>) {
        val innerFilters = mutableListOf<TokenFilterFactory>()
        wrappedFilterArgs.forEach { (originalFilterName, filterArgs) ->
            var filterName = originalFilterName
            val idSuffixPos = filterName.indexOf(FILTER_NAME_ID_SEPARATOR)
            if (idSuffixPos != -1) {
                filterName = filterName.substring(0, idSuffixPos)
            }
            innerFilters.add(TokenFilterFactory.forName(filterName, filterArgs))
        }
        setInnerFilters(innerFilters)
    }

    fun isIgnoreCase(): Boolean {
        return ignoreCase
    }

    fun getProtectedTerms(): CharArraySet? {
        return protectedTerms
    }

    override fun create(input: TokenStream, inner: (TokenStream) -> TokenStream): ConditionalTokenFilter {
        return ProtectedTermFilter(protectedTerms!!, input, inner)
    }

    @Throws(IOException::class)
    override fun doInform(loader: ResourceLoader) {
        protectedTerms = getWordSet(loader, termFiles, ignoreCase)
    }

    companion object {
        const val NAME = "protectedTerm"
        const val PROTECTED_TERMS = "protected"
        const val FILTER_ARG_SEPARATOR = '.'
        const val FILTER_NAME_ID_SEPARATOR = '-'
    }
}
