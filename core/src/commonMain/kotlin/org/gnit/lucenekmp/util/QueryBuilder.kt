package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CachingTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostAttribute
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SynonymQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.util.graph.GraphTokenStreamFiniteStrings
import okio.IOException
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.isNaN
import kotlin.jvm.JvmOverloads


/**
 * Creates queries from the [Analyzer] chain.
 *
 *
 * Example usage:
 *
 * <pre class="prettyprint">
 * QueryBuilder builder = new QueryBuilder(analyzer);
 * Query a = builder.createBooleanQuery("body", "just a test");
 * Query b = builder.createPhraseQuery("body", "another test");
 * Query c = builder.createMinShouldMatchQuery("body", "another test", 0.5f);
</pre> *
 *
 *
 * This can also be used as a subclass for query parsers to make it easier to interact with the
 * analysis chain. Factory methods such as `newTermQuery` are provided so that the generated
 * queries can be customized.
 */
open class QueryBuilder(var analyzer: Analyzer = StandardAnalyzer()) {

    /**
     * Returns true if position increments are enabled.
     *
     * @see .setEnablePositionIncrements
     */
    /**
     * Set to `true` to enable position increments in result query.
     *
     *
     * When set, result phrase and multi-phrase queries will be aware of position increments.
     * Useful when e.g. a StopFilter increases the position increment of the token that follows an
     * omitted token.
     *
     *
     * Default: true.
     */
    var enablePositionIncrements: Boolean = true
    /**
     * Returns true if graph TokenStream processing is enabled (default).
     *
     * @lucene.experimental
     */
    /**
     * Enable or disable graph TokenStream processing (enabled by default).
     *
     * @lucene.experimental
     */
    var enableGraphQueries: Boolean = true
    /**
     * Returns true if phrase query should be automatically generated for multi terms synonyms.
     *
     * @see .setAutoGenerateMultiTermSynonymsPhraseQuery
     */
    /**
     * Set to `true` if phrase queries should be automatically generated for multi terms
     * synonyms. Default: false.
     */
    var autoGenerateMultiTermSynonymsPhraseQuery: Boolean = false

    /**
     * Wraps a term and boost
     *
     * @param term the term
     * @param boost the boost
     */
    class TermAndBoost(term: BytesRef, val boost: Float) {
        val term: BytesRef

        /** Creates a new TermAndBoost  */
        init {
            var term: BytesRef = term
            term = BytesRef.deepCopyOf(term)
            this.term = term
        }
    }

    /**
     * Creates a boolean query from the query text.
     *
     * @param field field name
     * @param queryText text to be passed to the analyzer
     * @param operator operator used for clauses between analyzer tokens.
     * @return `TermQuery` or `BooleanQuery`, based on the analysis of `queryText`
     */
    /**
     * Creates a boolean query from the query text.
     *
     *
     * This is equivalent to `createBooleanQuery(field, queryText, Occur.SHOULD)`
     *
     * @param field field name
     * @param queryText text to be passed to the analyzer
     * @return `TermQuery` or `BooleanQuery`, based on the analysis of `queryText`
     */
    @JvmOverloads
    fun createBooleanQuery(
        field: String,
        queryText: String,
        operator: BooleanClause.Occur = BooleanClause.Occur.SHOULD
    ): Query {
        require(!(operator !== BooleanClause.Occur.SHOULD && operator !== BooleanClause.Occur.MUST)) { "invalid operator: only SHOULD or MUST are allowed" }
        return createFieldQuery(analyzer, operator, field, queryText, false, 0)
    }

    /**
     * Creates a phrase query from the query text.
     *
     * @param field field name
     * @param queryText text to be passed to the analyzer
     * @param phraseSlop number of other words permitted between words in query phrase
     * @return `TermQuery`, `BooleanQuery`, `PhraseQuery`, or `MultiPhraseQuery`, based on the analysis of `queryText`
     */
    /**
     * Creates a phrase query from the query text.
     *
     *
     * This is equivalent to `createPhraseQuery(field, queryText, 0)`
     *
     * @param field field name
     * @param queryText text to be passed to the analyzer
     * @return `TermQuery`, `BooleanQuery`, `PhraseQuery`, or `MultiPhraseQuery`, based on the analysis of `queryText`
     */
    @JvmOverloads
    fun createPhraseQuery(field: String, queryText: String, phraseSlop: Int = 0): Query {
        return createFieldQuery(
            analyzer,
            BooleanClause.Occur.MUST,
            field,
            queryText,
            true,
            phraseSlop
        )
    }

    /**
     * Creates a minimum-should-match query from the query text.
     *
     * @param field field name
     * @param queryText text to be passed to the analyzer
     * @param fraction of query terms `[0..1]` that should match
     * @return `TermQuery` or `BooleanQuery`, based on the analysis of `queryText`
     */
    fun createMinShouldMatchQuery(
        field: String,
        queryText: String,
        fraction: Float
    ): Query {
        require(!(Float.isNaN(fraction) || fraction < 0 || fraction > 1)) { "fraction should be >= 0 and <= 1" }

        // TODO: weird that BQ equals/rewrite/scorer doesn't handle this
        if (fraction == 1f) {
            return createBooleanQuery(field, queryText, BooleanClause.Occur.MUST)
        }

        var query: Query =
            createFieldQuery(analyzer, BooleanClause.Occur.SHOULD, field, queryText, false, 0)
        if (query is BooleanQuery) {
            query = addMinShouldMatchToBoolean(query, fraction)
        }
        return query
    }

    /** Rebuilds a boolean query and sets a new minimum number should match value.  */
    private fun addMinShouldMatchToBoolean(
        query: BooleanQuery,
        fraction: Float
    ): BooleanQuery {
        val builder: BooleanQuery.Builder = BooleanQuery.Builder()
        builder.setMinimumNumberShouldMatch((fraction * query.clauses().size).toInt())
        for (clause in query) {
            builder.add(clause)
        }

        return builder.build()
    }

    /**
     * Returns the analyzer.
     *
     * @see .setAnalyzer
     */
    /*fun getAnalyzer(): Analyzer {
        return analyzer
    }*/

    /** Sets the analyzer used to tokenize text.  */
    /*fun setAnalyzer(analyzer: Analyzer) {
        this.analyzer = analyzer
    }*/

    /**
     * Creates a query from the analysis chain.
     *
     *
     * Expert: this is more useful for subclasses such as queryparsers. If using this class
     * directly, just use [.createBooleanQuery] and [ ][.createPhraseQuery]. This is a complex method and it is usually not necessary
     * to override it in a subclass; instead, override methods like [.newBooleanQuery], etc., if
     * possible.
     *
     * @param analyzer analyzer used for this query
     * @param operator default boolean operator used for this query
     * @param field field to create queries against
     * @param queryText text to be passed to the analysis chain
     * @param quoted true if phrases should be generated when terms occur at more than one position
     * @param phraseSlop slop factor for phrase/multiphrase queries
     */
    protected fun createFieldQuery(
        analyzer: Analyzer,
        operator: BooleanClause.Occur,
        field: String,
        queryText: String,
        quoted: Boolean,
        phraseSlop: Int
    ): Query {
        assert(operator === BooleanClause.Occur.SHOULD || operator === BooleanClause.Occur.MUST)

        // Use the analyzer to get all the tokens, and then build an appropriate
        // query based on the analysis chain.
        try {
            analyzer.tokenStream(field, queryText).use { source ->
                return createFieldQuery(source, operator, field, quoted, phraseSlop)!!
            }
        } catch (e: IOException) {
            throw RuntimeException("Error analyzing query text", e)
        }
    }

    /**
     * Creates a query from a token stream.
     *
     * @param source the token stream to create the query from
     * @param operator default boolean operator used for this query
     * @param field field to create queries against
     * @param quoted true if phrases should be generated when terms occur at more than one position
     * @param phraseSlop slop factor for phrase/multiphrase queries
     */
    protected fun createFieldQuery(
        source: TokenStream,
        operator: BooleanClause.Occur,
        field: String,
        quoted: Boolean,
        phraseSlop: Int
    ): Query? {
        assert(operator === BooleanClause.Occur.SHOULD || operator === BooleanClause.Occur.MUST)

        // Build an appropriate query based on the analysis chain.
        try {
            CachingTokenFilter(source).use { stream ->
                val termAtt: TermToBytesRefAttribute =
                    stream.getAttribute(TermToBytesRefAttribute::class)
                val posIncAtt: PositionIncrementAttribute =
                    stream.addAttribute(PositionIncrementAttribute::class)
                val posLenAtt: PositionLengthAttribute =
                    stream.addAttribute(PositionLengthAttribute::class)

                if (termAtt == null) {
                    return null
                }

                // phase 1: read through the stream and assess the situation:
                // counting the number of tokens/positions and marking if we have any synonyms.
                var numTokens = 0
                var positionCount = 0
                var hasSynonyms = false
                var isGraph = false

                stream.reset()
                while (stream.incrementToken()) {
                    numTokens++
                    val positionIncrement: Int = posIncAtt.getPositionIncrement()
                    if (positionIncrement != 0) {
                        positionCount += positionIncrement
                    } else {
                        hasSynonyms = true
                    }

                    val positionLength: Int = posLenAtt.positionLength
                    if (enableGraphQueries && positionLength > 1) {
                        isGraph = true
                    }
                }

                // phase 2: based on token count, presence of synonyms, and options
                // formulate a single term, boolean, or phrase.
                if (numTokens == 0) {
                    return null
                } else if (numTokens == 1) {
                    // single term
                    return analyzeTerm(field, stream)
                } else if (isGraph) {
                    // graph
                    return if (quoted) {
                        analyzeGraphPhrase(stream, field, phraseSlop)
                    } else {
                        analyzeGraphBoolean(field, stream, operator)
                    }
                } else if (quoted && positionCount > 1) {
                    // phrase
                    return if (hasSynonyms) {
                        // complex phrase with synonyms
                        analyzeMultiPhrase(field, stream, phraseSlop)
                    } else {
                        // simple phrase
                        analyzePhrase(field, stream, phraseSlop)
                    }
                } else {
                    // boolean
                    return if (positionCount == 1) {
                        // only one position, with synonyms
                        analyzeBoolean(field, stream)
                    } else {
                        // complex case: multiple positions
                        analyzeMultiBoolean(field, stream, operator)
                    }
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Error analyzing query text", e)
        }
    }

    /** Creates simple term query from the cached tokenstream contents  */
    @Throws(IOException::class)
    protected fun analyzeTerm(
        field: String,
        stream: TokenStream
    ): Query {
        val termAtt: TermToBytesRefAttribute = stream.getAttribute(TermToBytesRefAttribute::class)
        val boostAtt: BoostAttribute = stream.addAttribute(BoostAttribute::class)

        stream.reset()
        if (!stream.incrementToken()) {
            throw AssertionError()
        }

        return newTermQuery(Term(field, termAtt.bytesRef), boostAtt.boost)
    }

    /** Creates simple boolean query from the cached tokenstream contents  */
    @Throws(IOException::class)
    protected fun analyzeBoolean(
        field: String,
        stream: TokenStream
    ): Query {
        val termAtt: TermToBytesRefAttribute = stream.getAttribute(TermToBytesRefAttribute::class)
        val boostAtt: BoostAttribute = stream.addAttribute(BoostAttribute::class)

        stream.reset()
        val terms: MutableList<TermAndBoost> = mutableListOf()
        while (stream.incrementToken()) {
            terms.add(TermAndBoost(termAtt.bytesRef, boostAtt.boost))
        }

        return newSynonymQuery(
            field,
            terms.toTypedArray()
        )
    }

    protected fun add(
        field: String,
        q: BooleanQuery.Builder,
        current: MutableList<TermAndBoost>,
        operator: BooleanClause.Occur
    ) {
        if (current.isEmpty()) {
            return
        }
        if (current.size == 1) {
            q.add(
                newTermQuery(Term(field, current[0].term), current[0].boost),
                operator
            )
        } else {
            q.add(
                newSynonymQuery(
                    field,
                    current.toTypedArray()
                ), operator
            )
        }
    }

    /** Creates complex boolean query from the cached tokenstream contents  */
    @Throws(IOException::class)
    protected fun analyzeMultiBoolean(
        field: String,
        stream: TokenStream,
        operator: BooleanClause.Occur
    ): Query {
        val q: BooleanQuery.Builder = newBooleanQuery()
        val currentQuery: MutableList<TermAndBoost> = mutableListOf()

        val termAtt: TermToBytesRefAttribute =
            stream.getAttribute(TermToBytesRefAttribute::class)
        val posIncrAtt: PositionIncrementAttribute =
            stream.getAttribute(PositionIncrementAttribute::class)
        val boostAtt: BoostAttribute =
            stream.addAttribute(BoostAttribute::class)

        stream.reset()
        while (stream.incrementToken()) {
            if (posIncrAtt.getPositionIncrement() != 0) {
                add(field, q, currentQuery, operator)
                currentQuery.clear()
            }
            currentQuery.add(TermAndBoost(termAtt.bytesRef, boostAtt.boost))
        }
        add(field, q, currentQuery, operator)

        return q.build()
    }

    /** Creates simple phrase query from the cached tokenstream contents  */
    @Throws(IOException::class)
    protected fun analyzePhrase(
        field: String,
        stream: TokenStream,
        slop: Int
    ): Query {
        val builder: PhraseQuery.Builder = PhraseQuery.Builder()
        builder.setSlop(slop)

        val termAtt: TermToBytesRefAttribute =
            stream.getAttribute(TermToBytesRefAttribute::class)
        val boostAtt: BoostAttribute =
            stream.addAttribute(BoostAttribute::class)
        val posIncrAtt: PositionIncrementAttribute =
            stream.getAttribute(PositionIncrementAttribute::class)
        var position = -1
        var phraseBoost: Float = BoostAttribute.DEFAULT_BOOST
        stream.reset()
        while (stream.incrementToken()) {
            position += if (enablePositionIncrements) {
                posIncrAtt.getPositionIncrement()
            } else {
                1
            }
            builder.add(Term(field, termAtt.bytesRef), position)
            phraseBoost *= boostAtt.boost
        }
        val query: PhraseQuery = builder.build()
        if (phraseBoost == BoostAttribute.DEFAULT_BOOST) {
            return query
        }
        return BoostQuery(query, phraseBoost)
    }

    /** Creates complex phrase query from the cached tokenstream contents  */
    @Throws(IOException::class)
    protected fun analyzeMultiPhrase(
        field: String,
        stream: TokenStream,
        slop: Int
    ): Query {
        val mpqb: MultiPhraseQuery.Builder = newMultiPhraseQueryBuilder()
        mpqb.setSlop(slop)

        val termAtt: TermToBytesRefAttribute =
            stream.getAttribute(TermToBytesRefAttribute::class)

        val posIncrAtt: PositionIncrementAttribute =
            stream.getAttribute(PositionIncrementAttribute::class)
        var position = -1

        val multiTerms: MutableList<Term> = mutableListOf()
        stream.reset()
        while (stream.incrementToken()) {
            val positionIncrement: Int = posIncrAtt.getPositionIncrement()

            if (positionIncrement > 0 && multiTerms.isNotEmpty()) {
                if (enablePositionIncrements) {
                    mpqb.add(multiTerms.toTypedArray<Term>(), position)
                } else {
                    mpqb.add(multiTerms.toTypedArray<Term>())
                }
                multiTerms.clear()
            }
            position += positionIncrement
            multiTerms.add(Term(field, termAtt.bytesRef))
        }

        if (enablePositionIncrements) {
            mpqb.add(multiTerms.toTypedArray<Term>(), position)
        } else {
            mpqb.add(multiTerms.toTypedArray<Term>())
        }
        return mpqb.build()
    }

    /**
     * Creates a boolean query from a graph token stream. The articulation points of the graph are
     * visited in order and the queries created at each point are merged in the returned boolean
     * query.
     */
    @Throws(IOException::class)
    protected fun analyzeGraphBoolean(
        field: String,
        source: TokenStream,
        operator: BooleanClause.Occur
    ): Query {
        source.reset()
        val graph = GraphTokenStreamFiniteStrings(source)
        val builder: BooleanQuery.Builder = BooleanQuery.Builder()
        val articulationPoints: IntArray = graph.articulationPoints()
        var lastState = 0
        for (i in 0..articulationPoints.size) {
            val start = lastState
            var end = -1
            if (i < articulationPoints.size) {
                end = articulationPoints[i]
            }
            lastState = end
            val positionalQuery: Query
            if (graph.hasSidePath(start)) {
                val sidePathsIterator: MutableIterator<TokenStream> =
                    graph.getFiniteStrings(start, end)
                val queries: MutableIterator<Query> =
                    object : MutableIterator<Query> {
                        override fun hasNext(): Boolean {
                            return sidePathsIterator.hasNext()
                        }

                        override fun next(): Query {
                            val sidePath: TokenStream = sidePathsIterator.next()
                            return createFieldQuery(
                                sidePath,
                                BooleanClause.Occur.MUST,
                                field,
                                this@QueryBuilder.autoGenerateMultiTermSynonymsPhraseQuery,
                                0
                            )!!
                        }

                        override fun remove() {
                            throw UnsupportedOperationException(
                                "remove() is not supported"
                            )
                        }
                    }
                positionalQuery = newGraphSynonymQuery(queries)
            } else {
                val attributes: MutableList<AttributeSource> = graph.getTerms(start)
                val terms: Array<TermAndBoost> =
                    attributes
                        .map { s: AttributeSource ->
                            val t: TermToBytesRefAttribute =
                                s.addAttribute(TermToBytesRefAttribute::class)
                            val b: BoostAttribute =
                                s.addAttribute(BoostAttribute::class)
                            TermAndBoost(t.bytesRef, b.boost)
                        }
                        .toTypedArray()
                assert(terms.isNotEmpty())
                positionalQuery = if (terms.size == 1) {
                    newTermQuery(Term(field, terms[0].term), terms[0].boost)
                } else {
                    newSynonymQuery(field, terms)
                }
            }
            if (positionalQuery != null) {
                builder.add(positionalQuery, operator)
            }
        }
        return builder.build()
    }

    /** Creates graph phrase query from the tokenstream contents  */
    @Throws(IOException::class)
    protected fun analyzeGraphPhrase(
        source: TokenStream,
        field: String,
        phraseSlop: Int
    ): Query {
        source.reset()
        val graph = GraphTokenStreamFiniteStrings(source)

        // Creates a boolean query from the graph token stream by extracting all the
        // finite strings from the graph and using them to create phrase queries with
        // the appropriate slop.
        val builder: BooleanQuery.Builder = BooleanQuery.Builder()
        val it: MutableIterator<TokenStream> = graph.finiteStrings
        while (it.hasNext()) {
            val query: Query? =
                createFieldQuery(it.next(), BooleanClause.Occur.MUST, field, true, phraseSlop)
            if (query != null) {
                builder.add(query, BooleanClause.Occur.SHOULD)
            }
        }
        return builder.build()
    }

    /**
     * Builds a new BooleanQuery instance.
     *
     *
     * This is intended for subclasses that wish to customize the generated queries.
     *
     * @return new BooleanQuery instance
     */
    protected fun newBooleanQuery(): BooleanQuery.Builder {
        return BooleanQuery.Builder()
    }

    /**
     * Builds a new SynonymQuery instance.
     *
     *
     * This is intended for subclasses that wish to customize the generated queries.
     *
     * @return new Query instance
     */
    protected fun newSynonymQuery(field: String, terms: Array<TermAndBoost>): Query {
        val builder: SynonymQuery.Builder =
            SynonymQuery.Builder(field)
        for (t in terms) {
            builder.addTerm(t.term, t.boost)
        }
        return builder.build()
    }

    /**
     * Builds a new GraphQuery for multi-terms synonyms.
     *
     *
     * This is intended for subclasses that wish to customize the generated queries.
     *
     * @return new Query instance
     */
    protected fun newGraphSynonymQuery(queries: MutableIterator<Query>): Query {
        val builder: BooleanQuery.Builder = BooleanQuery.Builder()
        while (queries.hasNext()) {
            builder.add(queries.next(), BooleanClause.Occur.SHOULD)
        }
        val bq: BooleanQuery = builder.build()
        if (bq.clauses().size == 1) {
            return bq.clauses()[0].query
        }
        return bq
    }

    /**
     * Builds a new TermQuery instance.
     *
     *
     * This is intended for subclasses that wish to customize the generated queries.
     *
     * @param term term
     * @return new TermQuery instance
     */
    protected fun newTermQuery(term: Term, boost: Float): Query {
        val q: Query = TermQuery(term)
        if (boost == BoostAttribute.DEFAULT_BOOST) {
            return q
        }
        return BoostQuery(q, boost)
    }

    /**
     * Builds a new MultiPhraseQuery instance.
     *
     *
     * This is intended for subclasses that wish to customize the generated queries.
     *
     * @return new MultiPhraseQuery instance
     */
    protected fun newMultiPhraseQueryBuilder(): MultiPhraseQuery.Builder {
        return MultiPhraseQuery.Builder()
    }
}
