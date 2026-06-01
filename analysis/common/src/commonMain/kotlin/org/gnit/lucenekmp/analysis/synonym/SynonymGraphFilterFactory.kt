package org.gnit.lucenekmp.analysis.synonym

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.cjk.CJKAnalyzer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizerFactory
import org.gnit.lucenekmp.analysis.pattern.PatternTokenizerFactory
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import kotlin.properties.Delegates

/**
 * Factory for [SynonymGraphFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_synonym" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.SynonymGraphFilterFactory" synonyms="synonyms.txt"
 *             format="solr" ignoreCase="false" expand="true"
 *             tokenizerFactory="solr.WhitespaceTokenizerFactory"
 *             [optional tokenizer factory parameters]/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * <p>An optional param name prefix of "tokenizerFactory." may be used for any init params that the
 * SynonymGraphFilterFactory needs to pass to the specified TokenizerFactory. If the
 * TokenizerFactory expects an init parameters with the same name as an init param used by the
 * SynonymGraphFilterFactory, the prefix is mandatory.
 *
 * <p>The optional {@code format} parameter controls how the synonyms will be parsed: It supports
 * the short names of {@code solr} for [SolrSynonymParser] and {@code wordnet} for and [WordnetSynonymParser],
 * or your own [SynonymMap.Parser] class name. The default is {@code solr}. A custom [SynonymMap.Parser]
 * is expected to have a constructor taking:
 *
 * <ul>
 *   <li><code>boolean dedup</code> - true if duplicates should be ignored, false otherwise
 *   <li><code>boolean expand</code> - true if conflation groups should be expanded, false if they
 *       are one-directional
 *   <li><code>[Analyzer] analyzer</code> - an analyzer used for each raw synonym
 * </ul>
 *
 * @see SolrSynonymParser SolrSynonymParser: default format
 * @lucene.experimental
 * @since 6.4.0
 * @lucene.spi {@value #NAME}
 */
class SynonymGraphFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    companion object {
        /** SPI name */
        const val NAME = "synonymGraph"
    }

    private var ignoreCase: Boolean by Delegates.notNull()
    private var tokenizerFactory: String? = null
    private var synonyms: String by Delegates.notNull()
    private var format: String? = null
    private var expand: Boolean by Delegates.notNull()
    private var analyzerName: String? = null
    private val tokArgs = mutableMapOf<String, String>()

    private lateinit var map: SynonymMap

    constructor(args: MutableMap<String, String>) : super(args) {
        ignoreCase = getBoolean(args, "ignoreCase", false)
        synonyms = require(args, "synonyms")
        format = get(args, "format")
        expand = getBoolean(args, "expand", true)

        analyzerName = get(args, "analyzer")
        tokenizerFactory = get(args, "tokenizerFactory")
        if (analyzerName != null && tokenizerFactory != null) {
            throw IllegalArgumentException(
                "Analyzer and TokenizerFactory can't be specified both: $analyzerName and $tokenizerFactory"
            )
        }

        if (tokenizerFactory != null) {
            tokArgs["luceneMatchVersion"] = luceneMatchVersion.toString()
            val keys = args.keys.toList()
            for (key in keys) {
                tokArgs[key.replace(Regex("^tokenizerFactory\\."), "")] = args[key]!!
                args.remove(key)
            }
        }
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        // if the fst is null, it means there's actually no synonyms... just return the original stream
        // as there is nothing to do here.
        return if (map.fst == null) input else SynonymGraphFilter(input, map, ignoreCase)
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        val factory = tokenizerFactory?.let { loadTokenizerFactory(loader, it) }
        val analyzer: Analyzer =
            if (analyzerName != null) {
                loadAnalyzer(loader, analyzerName!!)
            } else {
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val tokenizer = factory?.create() ?: WhitespaceTokenizer()
                        val stream: TokenStream = if (ignoreCase) LowerCaseFilter(tokenizer) else tokenizer
                        return TokenStreamComponents(tokenizer, stream)
                    }
                }
            }

        analyzer.use { a ->
            try {
                var formatClass = format
                if (format == null || format == "solr") {
                    formatClass = SolrSynonymParser::class.qualifiedName
                } else if (format == "wordnet") {
                    formatClass = WordnetSynonymParser::class.qualifiedName
                }
                // TODO: expose dedup as a parameter?
                map = loadSynonyms(loader, formatClass!!, true, a)
            } catch (e: ParseException) {
                throw IOException("Error parsing synonyms file:", e)
            }
        }
    }

    /** Load synonyms with the given [SynonymMap.Parser] class. */
    @Throws(IOException::class, ParseException::class)
    private fun loadSynonyms(loader: ResourceLoader, cname: String, dedup: Boolean, analyzer: Analyzer): SynonymMap {
        val decoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)

        val parser =
            when {
                isClassName(cname, SolrSynonymParser::class, "org.apache.lucene.analysis.synonym.SolrSynonymParser") ->
                    SolrSynonymParser(dedup, expand, analyzer)
                isClassName(cname, WordnetSynonymParser::class, "org.apache.lucene.analysis.synonym.WordnetSynonymParser") ->
                    WordnetSynonymParser(dedup, expand, analyzer)
                else -> throw RuntimeException("Cannot load class: $cname")
            }

        val files = splitFileNames(synonyms)
        for (file in files) {
            decoder.reset()
            InputStreamReader(loader.openResource(file), decoder).use { isr ->
                parser.parse(isr)
            }
        }
        return parser.build()
    }

    // (there are no tests for this functionality)
    @Throws(IOException::class)
    private fun loadTokenizerFactory(loader: ResourceLoader, cname: String): TokenizerFactory {
        val clazz = loader.findClass<TokenizerFactory>(cname, TokenizerFactory::class)
        val tokFactory =
            when (clazz) {
                PatternTokenizerFactory::class -> PatternTokenizerFactory(tokArgs.toMutableMap())
                WhitespaceTokenizerFactory::class -> WhitespaceTokenizerFactory(tokArgs.toMutableMap())
                else -> throw RuntimeException("Cannot load class: $cname")
            }
        if (tokFactory is ResourceLoaderAware) {
            tokFactory.inform(loader)
        }
        return tokFactory
    }

    @Throws(IOException::class)
    private fun loadAnalyzer(loader: ResourceLoader, cname: String): Analyzer {
        val clazz = loader.findClass<Analyzer>(cname, Analyzer::class)
        val analyzer =
            when (clazz) {
                CJKAnalyzer::class -> CJKAnalyzer()
                else -> throw RuntimeException("Cannot load class: $cname")
            }
        if (analyzer is ResourceLoaderAware) {
            analyzer.inform(loader)
        }
        return analyzer
    }

    private fun isClassName(cname: String, localClass: kotlin.reflect.KClass<*>, upstreamName: String): Boolean {
        return cname == localClass.qualifiedName || cname == upstreamName
    }
}
