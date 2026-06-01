package org.gnit.lucenekmp.analysis.custom

import okio.IOException
import okio.Path
import org.gnit.lucenekmp.analysis.AbstractAnalysisFactory
import org.gnit.lucenekmp.analysis.AnalysisSPILoader
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.miscellaneous.ConditionalTokenFilter
import org.gnit.lucenekmp.analysis.miscellaneous.ConditionalTokenFilterFactory
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.util.FilesystemResourceLoader
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.putIfAbsent
import org.gnit.lucenekmp.util.ClasspathResourceLoader
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import org.gnit.lucenekmp.util.SetOnce
import org.gnit.lucenekmp.util.Version
import kotlin.reflect.KClass

/**
 * A general-purpose Analyzer that can be created with a builder-style API. Under the hood it uses
 * the factory classes [TokenizerFactory], [TokenFilterFactory], and [CharFilterFactory].
 *
 * <p>You can create an instance of this Analyzer using the builder by passing the SPI names (as
 * defined by the Java `ServiceLoader` interface) to it:
 *
 * <pre class="prettyprint">
 * Analyzer ana = CustomAnalyzer.builder(Paths.get(&quot;/path/to/config/dir&quot;))
 *   .withTokenizer(StandardTokenizerFactory.NAME)
 *   .addTokenFilter(LowerCaseFilterFactory.NAME)
 *   .addTokenFilter(StopFilterFactory.NAME, &quot;ignoreCase&quot;, &quot;false&quot;, &quot;words&quot;, &quot;stopwords.txt&quot;, &quot;format&quot;, &quot;wordset&quot;)
 *   .build();
 * </pre>
 *
 * The parameters passed to components are also used by Apache Solr and are documented on their
 * corresponding factory classes. Refer to documentation of subclasses of [TokenizerFactory],
 * [TokenFilterFactory], and [CharFilterFactory].
 *
 * <p>This is the same as the above:
 *
 * <pre class="prettyprint">
 * Analyzer ana = CustomAnalyzer.builder(Paths.get(&quot;/path/to/config/dir&quot;))
 *   .withTokenizer(&quot;standard&quot;)
 *   .addTokenFilter(&quot;lowercase&quot;)
 *   .addTokenFilter(&quot;stop&quot;, &quot;ignoreCase&quot;, &quot;false&quot;, &quot;words&quot;, &quot;stopwords.txt&quot;, &quot;format&quot;, &quot;wordset&quot;)
 *   .build();
 * </pre>
 *
 * <p>The list of names to be used for components can be looked up through: [TokenizerFactory.availableTokenizers],
 * [TokenFilterFactory.availableTokenFilters], and [CharFilterFactory.availableCharFilters].
 *
 * <p>You can create conditional branches in the analyzer by using [Builder.when] and [Builder.whenTerm]:
 *
 * <pre class="prettyprint">
 * Analyzer ana = CustomAnalyzer.builder()
 *    .withTokenizer(&quot;standard&quot;)
 *    .addTokenFilter(&quot;lowercase&quot;)
 *    .whenTerm(t -&gt; t.length() &gt; 10)
 *      .addTokenFilter(&quot;reversestring&quot;)
 *    .endwhen()
 *    .build();
 * </pre>
 *
 * @since 5.0.0
 */
class CustomAnalyzer internal constructor(
    private val charFilters: Array<CharFilterFactory>,
    /** Returns the tokenizer that is used in this analyzer.  */
    val tokenizerFactory: TokenizerFactory,
    private val tokenFilters: Array<TokenFilterFactory>,
    private val posIncGap: Int?,
    private val offsetGap: Int?
) : Analyzer() {
    override fun initReader(fieldName: String, reader: Reader): Reader {
        var current = reader
        for (charFilter in charFilters) {
            current = charFilter.create(current)
        }
        return current
    }

    override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
        var current = reader
        for (charFilter in charFilters) {
            current = charFilter.normalize(current)
        }
        return current
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tk: Tokenizer = tokenizerFactory.create(attributeFactory(fieldName))
        var ts: TokenStream = tk
        for (filter in tokenFilters) {
            ts = filter.create(ts)
        }
        return TokenStreamComponents(tk, ts)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result = `in`
        for (filter in tokenFilters) {
            result = filter.normalize(result)
        }
        return result
    }

    override fun getPositionIncrementGap(fieldName: String?): Int {
        // use default from Analyzer base class if null
        return posIncGap ?: super.getPositionIncrementGap(fieldName)
    }

    override fun getOffsetGap(fieldName: String?): Int {
        // use default from Analyzer base class if null
        return offsetGap ?: super.getOffsetGap(fieldName)
    }

    /** Returns the list of char filters that are used in this analyzer. */
    val charFilterFactories: List<CharFilterFactory>
        get() = charFilters.asList()

    /** Returns the list of token filters that are used in this analyzer. */
    val tokenFilterFactories: List<TokenFilterFactory>
        get() = tokenFilters.asList()

    override fun toString(): String {
        val sb = StringBuilder(this::class.simpleName!!).append('(')
        for (filter in charFilters) {
            sb.append(filter).append(',')
        }
        sb.append(tokenizerFactory)
        for (filter in tokenFilters) {
            sb.append(',').append(filter)
        }
        return sb.append(')').toString()
    }

    /**
     * Builder for [CustomAnalyzer].
     *
     * @see CustomAnalyzer.builder
     * @see CustomAnalyzer.builder
     * @see CustomAnalyzer.builder
     */
    class Builder internal constructor(private val loader: ResourceLoader) {
        private val defaultMatchVersion = SetOnce<Version>()
        private val charFilters = mutableListOf<CharFilterFactory>()
        private val tokenizer = SetOnce<TokenizerFactory>()
        private val tokenFilters = mutableListOf<TokenFilterFactory>()
        private val posIncGap = SetOnce<Int>()
        private val offsetGap = SetOnce<Int>()

        private var componentsAdded = false

        /**
         * This match version is passed as default to all tokenizers or filters. It is used unless you
         * pass the parameter {code luceneMatchVersion} explicitly. It defaults to undefined, so the
         * underlying factory will (in most cases) use [Version.LATEST].
         */
        fun withDefaultMatchVersion(version: Version): Builder {
            require(!componentsAdded) {
                "You may only set the default match version before adding tokenizers, token filters, or char filters."
            }
            defaultMatchVersion.set(version)
            return this
        }

        /**
         * Sets the position increment gap of the analyzer. The default is defined in the analyzer base
         * class.
         *
         * @see Analyzer.getPositionIncrementGap
         */
        fun withPositionIncrementGap(posIncGap: Int): Builder {
            require(posIncGap >= 0) { "posIncGap must be >= 0" }
            this.posIncGap.set(posIncGap)
            return this
        }

        /**
         * Sets the offset gap of the analyzer. The default is defined in the analyzer base class.
         *
         * @see Analyzer.getOffsetGap
         */
        fun withOffsetGap(offsetGap: Int): Builder {
            require(offsetGap >= 0) { "offsetGap must be >= 0" }
            this.offsetGap.set(offsetGap)
            return this
        }

        /**
         * Uses the given tokenizer.
         *
         * @param factory class that is used to create the tokenizer.
         * @param params a list of factory string params as key/value pairs. The number of parameters
         * must be an even number, as they are pairs.
         */
        @Throws(IOException::class)
        fun withTokenizer(factory: KClass<out TokenizerFactory>, vararg params: String): Builder {
            return withTokenizer(factory, paramsToMap(*params))
        }

        /**
         * Uses the given tokenizer.
         *
         * @param factory class that is used to create the tokenizer.
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun withTokenizer(factory: KClass<out TokenizerFactory>, params: MutableMap<String, String>): Builder {
            tokenizer.set(applyResourceLoader(AnalysisSPILoader.newFactoryClassInstance(factory, applyDefaultParams(params))))
            componentsAdded = true
            return this
        }

        /**
         * Uses the given tokenizer.
         *
         * @param name is used to look up the factory with [TokenizerFactory.forName]. The list of possible names can be looked up with [TokenizerFactory.availableTokenizers].
         * @param params a list of factory string params as key/value pairs. The number of parameters
         * must be an even number, as they are pairs.
         */
        @Throws(IOException::class)
        fun withTokenizer(name: String, vararg params: String): Builder {
            return withTokenizer(name, paramsToMap(*params))
        }

        /**
         * Uses the given tokenizer.
         *
         * @param name is used to look up the factory with [TokenizerFactory.forName]. The list of possible names can be looked up with [TokenizerFactory.availableTokenizers].
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun withTokenizer(name: String, params: MutableMap<String, String>): Builder {
            tokenizer.set(applyResourceLoader(TokenizerFactory.forName(name, applyDefaultParams(params))))
            componentsAdded = true
            return this
        }

        /**
         * Adds the given token filter.
         *
         * @param factory class that is used to create the token filter.
         * @param params a list of factory string params as key/value pairs. The number of parameters
         * must be an even number, as they are pairs.
         */
        @Throws(IOException::class)
        fun addTokenFilter(factory: KClass<out TokenFilterFactory>, vararg params: String): Builder {
            return addTokenFilter(factory, paramsToMap(*params))
        }

        /**
         * Adds the given token filter.
         *
         * @param factory class that is used to create the token filter.
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun addTokenFilter(factory: KClass<out TokenFilterFactory>, params: MutableMap<String, String>): Builder {
            tokenFilters.add(applyResourceLoader(AnalysisSPILoader.newFactoryClassInstance(factory, applyDefaultParams(params))))
            componentsAdded = true
            return this
        }

        /**
         * Adds the given token filter.
         *
         * @param name is used to look up the factory with [TokenFilterFactory.forName]. The list of possible names can be looked up with [TokenFilterFactory.availableTokenFilters].
         * @param params a list of factory string params as key/value pairs. The number of parameters
         * must be an even number, as they are pairs.
         */
        @Throws(IOException::class)
        fun addTokenFilter(name: String, vararg params: String): Builder {
            return addTokenFilter(name, paramsToMap(*params))
        }

        /**
         * Adds the given token filter.
         *
         * @param name is used to look up the factory with [TokenFilterFactory.forName]. The list of possible names can be looked up with [TokenFilterFactory.availableTokenFilters].
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun addTokenFilter(name: String, params: MutableMap<String, String>): Builder {
            tokenFilters.add(applyResourceLoader(TokenFilterFactory.forName(name, applyDefaultParams(params))))
            componentsAdded = true
            return this
        }

        internal fun addTokenFilter(factory: TokenFilterFactory): Builder {
            tokenFilters.add(factory)
            componentsAdded = true
            return this
        }

        /**
         * Adds the given char filter.
         *
         * @param factory class that is used to create the char filter.
         * @param params a list of factory string params as key/value pairs. The number of parameters
         * must be an even number, as they are pairs.
         */
        @Throws(IOException::class)
        fun addCharFilter(factory: KClass<out CharFilterFactory>, vararg params: String): Builder {
            return addCharFilter(factory, paramsToMap(*params))
        }

        /**
         * Adds the given char filter.
         *
         * @param factory class that is used to create the char filter.
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun addCharFilter(factory: KClass<out CharFilterFactory>, params: MutableMap<String, String>): Builder {
            charFilters.add(applyResourceLoader(AnalysisSPILoader.newFactoryClassInstance(factory, applyDefaultParams(params))))
            componentsAdded = true
            return this
        }

        /**
         * Adds the given char filter.
         *
         * @param name is used to look up the factory with [CharFilterFactory.forName]. The list of possible names can be looked up with [CharFilterFactory.availableCharFilters].
         * @param params a list of factory string params as key/value pairs. The number of parameters
         * must be an even number, as they are pairs.
         */
        @Throws(IOException::class)
        fun addCharFilter(name: String, vararg params: String): Builder {
            return addCharFilter(name, paramsToMap(*params))
        }

        /**
         * Adds the given char filter.
         *
         * @param name is used to look up the factory with [CharFilterFactory.forName]. The list of possible names can be looked up with [CharFilterFactory.availableCharFilters].
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun addCharFilter(name: String, params: MutableMap<String, String>): Builder {
            charFilters.add(applyResourceLoader(CharFilterFactory.forName(name, applyDefaultParams(params))))
            componentsAdded = true
            return this
        }

        /**
         * Add a [ConditionalTokenFilterFactory] to the analysis chain
         *
         * <p>TokenFilters added by subsequent calls to [ConditionBuilder.addTokenFilter] and related
         * functions will only be used if the current token matches the condition. Consumers must call
         * [ConditionBuilder.endwhen] to return to the normal tokenfilter chain once conditional filters
         * have been added
         *
         * @param name is used to look up the factory with [TokenFilterFactory.forName]
         * @param params the parameters to be passed to the factory
         */
        @Throws(IOException::class)
        fun `when`(name: String, vararg params: String): ConditionBuilder {
            return `when`(name, paramsToMap(*params))
        }

        /**
         * Add a [ConditionalTokenFilterFactory] to the analysis chain
         *
         * <p>TokenFilters added by subsequent calls to [ConditionBuilder.addTokenFilter] and related
         * functions will only be used if the current token matches the condition. Consumers must call
         * [ConditionBuilder.endwhen] to return to the normal tokenfilter chain once conditional filters
         * have been added
         *
         * @param name is used to look up the factory with [TokenFilterFactory.forName]
         * @param params the parameters to be passed to the factory. The map must be modifiable
         */
        @Throws(IOException::class)
        fun `when`(name: String, params: MutableMap<String, String>): ConditionBuilder {
            val factory = TokenFilterFactory.forName(name, applyDefaultParams(params))
            require(factory is ConditionalTokenFilterFactory) {
                "TokenFilterFactory $name is not a ConditionalTokenFilterFactory"
            }
            return `when`(factory)
        }

        /**
         * Add a [ConditionalTokenFilterFactory] to the analysis chain
         *
         * <p>TokenFilters added by subsequent calls to [ConditionBuilder.addTokenFilter] and related
         * functions will only be used if the current token matches the condition. Consumers must call
         * [ConditionBuilder.endwhen] to return to the normal tokenfilter chain once conditional filters
         * have been added
         *
         * @param factory class that is used to create the ConditionalTokenFilter
         * @param params the parameters to be passed to the factory
         */
        @Throws(IOException::class)
        fun `when`(factory: KClass<out ConditionalTokenFilterFactory>, vararg params: String): ConditionBuilder {
            return `when`(factory, paramsToMap(*params))
        }

        /**
         * Add a [ConditionalTokenFilterFactory] to the analysis chain
         *
         * <p>TokenFilters added by subsequent calls to [ConditionBuilder.addTokenFilter] and related
         * functions will only be used if the current token matches the condition. Consumers must call
         * [ConditionBuilder.endwhen] to return to the normal tokenfilter chain once conditional filters
         * have been added
         *
         * @param factory class that is used to create the ConditionalTokenFilter
         * @param params the parameters to be passed to the factory. The map must be modifiable
         */
        @Throws(IOException::class)
        fun `when`(factory: KClass<out ConditionalTokenFilterFactory>, params: MutableMap<String, String>): ConditionBuilder {
            return `when`(AnalysisSPILoader.newFactoryClassInstance(factory, applyDefaultParams(params)))
        }

        /**
         * Add a [ConditionalTokenFilterFactory] to the analysis chain
         *
         * <p>TokenFilters added by subsequent calls to [ConditionBuilder.addTokenFilter] and related
         * functions will only be used if the current token matches the condition. Consumers must call
         * [ConditionBuilder.endwhen] to return to the normal tokenfilter chain once conditional filters
         * have been added
         */
        fun `when`(factory: ConditionalTokenFilterFactory): ConditionBuilder {
            return ConditionBuilder(factory, this)
        }

        /**
         * Apply subsequent token filters if the current token's term matches a predicate
         *
         * <p>This is the equivalent of:
         *
         * <pre>
         * when(new ConditionalTokenFilterFactory(Collections.emptyMap()) {
         *   @Override
         *   protected ConditionalTokenFilter create(TokenStream input, Function&lt;TokenStream, TokenStream&gt; inner) {
         *     return new ConditionalTokenFilter(input, inner) {
         *       CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
         *       @Override
         *       protected boolean shouldFilter() {
         *         return predicate.test(termAtt);
         *       }
         *     };
         *   }
         * });
         * </pre>
         */
        fun whenTerm(predicate: (CharSequence) -> Boolean): ConditionBuilder {
            return ConditionBuilder(
                object : ConditionalTokenFilterFactory(mutableMapOf()) {
                    override fun create(input: TokenStream, inner: (TokenStream) -> TokenStream): ConditionalTokenFilter {
                        return object : ConditionalTokenFilter(input, inner) {
                            private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

                            override fun shouldFilter(): Boolean {
                                return predicate(termAtt)
                            }
                        }
                    }
                },
                this
            )
        }

        /** Builds the analyzer. */
        fun build(): CustomAnalyzer {
            checkNotNull(tokenizer.get()) { "You have to set at least a tokenizer." }
            return CustomAnalyzer(
                charFilters.toTypedArray(),
                tokenizer.get()!!,
                tokenFilters.toTypedArray(),
                posIncGap.get(),
                offsetGap.get()
            )
        }

        internal fun applyDefaultParams(map: MutableMap<String, String>): MutableMap<String, String> {
            val v = defaultMatchVersion.get()
            if (v != null) {
                map.putIfAbsent(AbstractAnalysisFactory.LUCENE_MATCH_VERSION_PARAM, v.toString())
            }
            return map
        }

        internal fun paramsToMap(vararg params: String): MutableMap<String, String> {
            require(params.size % 2 == 0) {
                "Key-value pairs expected, so the number of params must be even."
            }
            val map = LinkedHashMap<String, String>(params.size / 2)
            var i = 0
            while (i < params.size) {
                requireNotNull(params[i]) { "Key of param may not be null." }
                map[params[i]] = params[i + 1]
                i += 2
            }
            return map
        }

        @Throws(IOException::class)
        internal fun <T> applyResourceLoader(factory: T): T {
            if (factory is ResourceLoaderAware) {
                factory.inform(loader)
            }
            return factory
        }
    }

    /** Factory class for a [ConditionalTokenFilter] */
    class ConditionBuilder internal constructor(
        private val factory: ConditionalTokenFilterFactory,
        private val parent: Builder
    ) {
        private val innerFilters = mutableListOf<TokenFilterFactory>()

        /**
         * Adds the given token filter.
         *
         * @param name is used to look up the factory with [TokenFilterFactory.forName]. The list of possible names can be looked up with [TokenFilterFactory.availableTokenFilters].
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun addTokenFilter(name: String, params: MutableMap<String, String>): ConditionBuilder {
            innerFilters.add(TokenFilterFactory.forName(name, parent.applyDefaultParams(params)))
            return this
        }

        /**
         * Adds the given token filter.
         *
         * @param name is used to look up the factory with [TokenFilterFactory.forName]. The list of possible names can be looked up with [TokenFilterFactory.availableTokenFilters].
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun addTokenFilter(name: String, vararg params: String): ConditionBuilder {
            return addTokenFilter(name, parent.paramsToMap(*params))
        }

        /**
         * Adds the given token filter.
         *
         * @param factory class that is used to create the token filter.
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun addTokenFilter(factory: KClass<out TokenFilterFactory>, params: MutableMap<String, String>): ConditionBuilder {
            innerFilters.add(AnalysisSPILoader.newFactoryClassInstance(factory, parent.applyDefaultParams(params)))
            return this
        }

        /**
         * Adds the given token filter.
         *
         * @param factory class that is used to create the token filter.
         * @param params the map of parameters to be passed to factory. The map must be modifiable.
         */
        @Throws(IOException::class)
        fun addTokenFilter(factory: KClass<out TokenFilterFactory>, vararg params: String): ConditionBuilder {
            return addTokenFilter(factory, parent.paramsToMap(*params))
        }

        /** Close the branch and return to the main analysis chain */
        @Throws(IOException::class)
        fun endwhen(): Builder {
            factory.setInnerFilters(innerFilters)
            parent.applyResourceLoader(factory)
            parent.addTokenFilter(factory)
            return parent
        }
    }

    companion object {
        /**
         * Returns a builder for custom analyzers that loads all resources from Lucene's classloader. All
         * path names given must be absolute with package prefixes.
         */
        fun builder(): Builder {
            return builder(ClasspathResourceLoader(CustomAnalyzer::class))
        }

        /**
         * Returns a builder for custom analyzers that loads all resources from the given file system base
         * directory. Place, e.g., stop word files there. Files that are not in the given directory are
         * loaded from Lucene's classloader.
         */
        fun builder(configDir: Path): Builder {
            return builder(FilesystemResourceLoader(configDir, ClasspathResourceLoader(CustomAnalyzer::class)))
        }

        /**
         * Returns a builder for custom analyzers that loads all resources using the given [ResourceLoader].
         */
        fun builder(loader: ResourceLoader): Builder {
            return Builder(loader)
        }
    }
}
