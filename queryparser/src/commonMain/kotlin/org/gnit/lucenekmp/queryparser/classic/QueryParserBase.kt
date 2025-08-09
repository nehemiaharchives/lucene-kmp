package org.gnit.lucenekmp.queryparser.classic

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.LocalTime
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.document.DateTools.Resolution
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.queryparser.charstream.CharStream
import org.gnit.lucenekmp.queryparser.charstream.FastCharStream
import org.gnit.lucenekmp.queryparser.classic.QueryParser.Operator
import org.gnit.lucenekmp.queryparser.flexible.standard.CommonQueryParserConfiguration
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.FuzzyQuery
import org.gnit.lucenekmp.search.IndexSearcher.TooManyClauses
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.RegexpQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.search.WildcardQuery
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.QueryBuilder
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import org.gnit.lucenekmp.util.codePointCount
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * This class is overridden by QueryParser in QueryParser.jj and acts to separate the majority of
 * the Java code from the .jj grammar file.
 */
abstract class QueryParserBase

// So the generated QueryParser(CharStream) won't error out
protected constructor() : QueryBuilder(/*null*/),
    CommonQueryParserConfiguration {
    /** The actual operator that parser uses to combine query terms  */
    var operator: QueryParser.Operator = OR_OPERATOR

    override var multiTermRewriteMethod: MultiTermQuery.RewriteMethod = MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE
    /**
     * @see .setAllowLeadingWildcard
     */
    /**
     * Set to `true` to allow leading wildcard characters.
     *
     *
     * When set, `*` or `?` are allowed as the first character of a
     * PrefixQuery and WildcardQuery. Note that this can produce very slow queries on big indexes.
     *
     *
     * Default: false.
     */
    override var allowLeadingWildcard: Boolean = false

    /**
     * @return Returns the default field.
     */
    lateinit var field: String
        protected set
    /** Gets the default slop for phrases.  */
    /**
     * Sets the default slop for phrases. If zero, then exact phrase matches are required. Default
     * value is zero.
     */
    override var phraseSlop: Int = 0
    /** Get the minimal similarity for fuzzy queries.  */
    /** Set the minimum similarity for fuzzy queries. Default is 2f.  */
    override var fuzzyMinSim: Float = FuzzyQuery.defaultMaxEdits.toFloat()
    /**
     * Get the prefix length for fuzzy queries.
     *
     * @return Returns the fuzzyPrefixLength.
     */
    /**
     * Set the prefix length for fuzzy queries. Default is 0.
     *
     * @param fuzzyPrefixLength The fuzzyPrefixLength to set.
     */
    override var fuzzyPrefixLength: Int = FuzzyQuery.defaultPrefixLength
    override var locale: Locale = Locale() /*Locale.getDefault()*/
    override var timeZone: TimeZone = TimeZone.currentSystemDefault()

    // the default date resolution
    override lateinit var dateResolution: Resolution

    // maps field names to date resolutions
    var fieldToDateResolution: MutableMap<String, Resolution>? = null

    /**
     * @see .setAutoGeneratePhraseQueries
     */
    /**
     * Set to true if phrase queries will be automatically generated when the analyzer returns more
     * than one term from whitespace delimited text. NOTE: this behavior may not be suitable for all
     * languages.
     *
     *
     * Set to false if phrase queries should only be generated when surrounded by double quotes.
     */
    open var autoGeneratePhraseQueries: Boolean = false
    /**
     * @return the maximum effort that determinizing a regexp query can spend. If the query requires
     * more effort, a TooComplexToDeterminizeException is thrown.
     */
    /**
     * @param determinizeWorkLimit the maximum effort that determinizing a regexp query can spend. If
     * the query requires more effort, a TooComplexToDeterminizeException is thrown.
     */
    var determinizeWorkLimit: Int = Operations.DEFAULT_DETERMINIZE_WORK_LIMIT

    /**
     * Initializes a query parser. Called by the QueryParser constructor
     *
     * @param f the default field for query terms.
     * @param a used to find terms in the query text.
     */
    fun init(f: String, a: Analyzer) {
        analyzer = a
        field = f
        this.autoGeneratePhraseQueries = false
    }

    // the generated parser will create these in QueryParser
    abstract fun ReInit(stream: CharStream)

    @Throws(ParseException::class)
    abstract fun TopLevelQuery(field: String): Query

    /**
     * Parses a query string, returning a [Query].
     *
     * @param query the query string to be parsed.
     * @throws ParseException if the parsing fails
     */
    @Throws(ParseException::class)
    fun parse(query: String): Query? {
        ReInit(FastCharStream(StringReader(query)))
        try {
            // TopLevelQuery is a Query followed by the end-of-input (EOF)
            val res: Query? = TopLevelQuery(field)
            return if (res != null) res else newBooleanQuery().build()
        } catch (tme: ParseException) {
            // rethrow to include the original query:
            val e = ParseException(message = "Cannot parse '" + query + "': " + tme.message, cause = tme)
            throw e
        } catch (tme: TokenMgrError) {
            val e = ParseException(message = "Cannot parse '" + query + "': " + tme.message, cause = tme)
            throw e
        } catch (tmc: TooManyClauses) {
            val e = ParseException(message = "Cannot parse '$query': too many boolean clauses", cause = tmc)
            throw e
        }
    }

    /**
     * Sets the boolean operator of the QueryParser. In default mode (`OR_OPERATOR`) terms
     * without any modifiers are considered optional: for example `capital of Hungary` is
     * equal to `capital OR of OR Hungary`.<br></br>
     * In `AND_OPERATOR` mode terms are considered to be in conjunction: the
     * above-mentioned query is parsed as `capital AND of AND Hungary`
     */
    fun setDefaultOperator(op: Operator) {
        this.operator = op
    }

    val defaultOperator: Operator
        /** Gets implicit operator setting, which will be either AND_OPERATOR or OR_OPERATOR.  */
        get() = operator

    /*override fun setMultiTermRewriteMethod(method: MultiTermQuery.RewriteMethod) {
        multiTermRewriteMethod = method
    }*/

    /**
     * @see .setMultiTermRewriteMethod
     */
    /*override fun getMultiTermRewriteMethod(): MultiTermQuery.RewriteMethod {
        return multiTermRewriteMethod
    }*/

    /** Set locale used by date range parsing, lowercasing, and other locale-sensitive operations.  */
    /*override fun setLocale(locale: Locale) {
        this.locale = locale
    }*/

    /** Returns current locale, allowing access by subclasses.  */
    /*override fun getLocale(): Locale {
        return locale
    }*/

    /*override fun setTimeZone(timeZone: TimeZone?) {
        this.timeZone = timeZone
    }*/

    /*override fun getTimeZone(): TimeZone? {
        return timeZone
    }*/

    /**
     * Sets the default date resolution used by RangeQueries for fields for which no specific date
     * resolutions has been set. Field specific resolutions can be set with [ ][.setDateResolution].
     *
     * @param dateResolution the default date resolution to set
     */
    /*override fun setDateResolution(dateResolution: Resolution) {
        this.dateResolution = dateResolution
    }*/

    /**
     * Sets the date resolution used by RangeQueries for a specific field.
     *
     * @param fieldName field for which the date resolution is to be set
     * @param dateResolution date resolution to set
     */
    fun setDateResolution(fieldName: String, dateResolution: Resolution) {
        //requireNotNull(fieldName) { "Field must not be null." }

        if (fieldToDateResolution == null) {
            // lazily initialize HashMap
            fieldToDateResolution = HashMap()
        }

        fieldToDateResolution!![fieldName] = dateResolution
    }

    /**
     * Returns the date resolution that is used by RangeQueries for the given field. Returns null, if
     * no default or field specific date resolution has been set for the given field.
     */
    fun getDateResolution(fieldName: String): Resolution {
        //requireNotNull(fieldName) { "Field must not be null." }

        if (fieldToDateResolution == null) {
            // no field specific date resolutions set; return default date resolution instead
            return this.dateResolution
        }

        var resolution: Resolution? = fieldToDateResolution!![fieldName]
        if (resolution == null) {
            // no date resolutions set for the given field; return default date resolution instead
            resolution = this.dateResolution
        }

        return resolution
    }

    protected fun addClause(
        clauses: MutableList<BooleanClause>,
        conj: Int,
        mods: Int,
        q: Query?
    ) {
        var required: Boolean
        val prohibited: Boolean

        // If this term is introduced by AND, make the preceding term required,
        // unless it's already prohibited
        if (clauses.isNotEmpty() && conj == CONJ_AND) {
            val c: BooleanClause = clauses[clauses.size - 1]
            if (!c.isProhibited) clauses[clauses.size - 1] = BooleanClause(c.query, Occur.MUST)
        }

        if (clauses.isNotEmpty() && operator == AND_OPERATOR && conj == CONJ_OR) {
            // If this term is introduced by OR, make the preceding term optional,
            // unless it's prohibited (that means we leave -a OR b but +a OR b-->a OR b)
            // notice if the input is a OR b, first term is parsed as required; without
            // this modification a OR b would be parsed as +a OR b
            val c: BooleanClause = clauses[clauses.size - 1]
            if (!c.isProhibited) clauses[clauses.size - 1] = BooleanClause(c.query, Occur.SHOULD)
        }

        // We might have been passed a null query; the term might have been
        // filtered away by the analyzer.
        if (q == null) return

        if (operator == OR_OPERATOR) {
            // We set REQUIRED if we're introduced by AND or +; PROHIBITED if
            // introduced by NOT or -; make sure not to set both.
            prohibited = (mods == MOD_NOT)
            required = (mods == MOD_REQ)
            if (conj == CONJ_AND && !prohibited) {
                required = true
            }
        } else {
            // We set PROHIBITED if we're introduced by NOT or -; We set REQUIRED
            // if not PROHIBITED and not introduced by OR
            prohibited = (mods == MOD_NOT)
            required = (!prohibited && conj != CONJ_OR)
        }
        if (required && !prohibited) clauses.add(newBooleanClause(q, Occur.MUST))
        else if (!required && !prohibited) clauses.add(
            newBooleanClause(
                q,
                Occur.SHOULD
            )
        )
        else if (!required && prohibited) clauses.add(
            newBooleanClause(
                q,
                Occur.MUST_NOT
            )
        )
        else throw RuntimeException("Clause cannot be both required and prohibited")
    }

    /**
     * Adds clauses generated from analysis over text containing whitespace. There are no operators,
     * so the query's clauses can either be MUST (if the default operator is AND) or SHOULD (default
     * OR).
     *
     *
     * If all of the clauses in the given Query are TermQuery-s, this method flattens the result by
     * adding the TermQuery-s individually to the output clause list; otherwise, the given Query is
     * added as a single clause including its nested clauses.
     */
    protected fun addMultiTermClauses(
        clauses: MutableList<BooleanClause>,
        q: Query?
    ) {
        // We might have been passed a null query; the term might have been
        // filtered away by the analyzer.
        if (q == null) {
            return
        }
        var allNestedTermQueries = false
        if (q is BooleanQuery) {
            allNestedTermQueries = true
            for (clause in q.clauses()) {
                if (clause.query !is TermQuery) {
                    allNestedTermQueries = false
                    break
                }
            }
        }
        if (allNestedTermQueries) {
            clauses.addAll((q as BooleanQuery).clauses())
        } else {
            val occur: Occur =
                if (operator == OR_OPERATOR) Occur.SHOULD else Occur.MUST
            if (q is BooleanQuery) {
                for (clause in q.clauses()) {
                    clauses.add(newBooleanClause(clause.query, occur))
                }
            } else {
                clauses.add(newBooleanClause(q, occur))
            }
        }
    }

    /**
     * @exception ParseException throw in overridden method to
     * disallow
     */
    @Throws(ParseException::class)
    protected fun getFieldQuery(field: String, queryText: String, quoted: Boolean): Query {
        return newFieldQuery(analyzer, field, queryText, quoted)
    }

    /**
     * @exception ParseException throw in overridden method to
     * disallow
     */
    @Throws(ParseException::class)
    protected fun newFieldQuery(
        analyzer: Analyzer,
        field: String,
        queryText: String,
        quoted: Boolean
    ): Query {
        val occur: Occur =
            if (operator == Operator.AND) Occur.MUST else Occur.SHOULD
        return createFieldQuery(
            analyzer, occur, field, queryText, quoted || autoGeneratePhraseQueries, phraseSlop
        )
    }

    /**
     * Base implementation delegates to [.getFieldQuery]. This method may
     * be overridden, for example, to return a SpanNearQuery instead of a PhraseQuery.
     *
     * @exception ParseException throw in overridden method to
     * disallow
     */
    @Throws(ParseException::class)
    protected fun getFieldQuery(field: String, queryText: String, slop: Int): Query {
        var query: Query = getFieldQuery(field, queryText, true)

        if (query is PhraseQuery) {
            query = addSlopToPhrase(query, slop)
        } else if (query is MultiPhraseQuery) {
            if (slop != query.slop) {
                query = MultiPhraseQuery.Builder(query).setSlop(slop).build()
            }
        }

        return query
    }

    /** Rebuild a phrase query with a slop value  */
    private fun addSlopToPhrase(
        query: PhraseQuery,
        slop: Int
    ): PhraseQuery {
        val builder: PhraseQuery.Builder = PhraseQuery.Builder()
        builder.setSlop(slop)
        val terms: Array<Term> = query.terms
        val positions: IntArray = query.positions
        for (i in terms.indices) {
            builder.add(terms[i], positions[i])
        }

        return builder.build()
    }

    @OptIn(ExperimentalTime::class)
    @Throws(ParseException::class)
    protected fun getRangeQuery(
        field: String, part1: String?, part2: String?, startInclusive: Boolean, endInclusive: Boolean
    ): Query {
        var part1 = part1
        var part2 = part2
        val resolution: Resolution = getDateResolution(field)

        if(part1 != null){
            try {
                part1 = DateTools.dateToString(Instant.parse(part1), resolution)
            } catch (e: Exception) {
            }
        }

        if(part2 != null) {
            try {
                var d2: Instant = Instant.parse(part2)
                if (endInclusive) {
                    // The user can only specify the date, not the time, so make sure
                    // the time is set to the latest possible time of that date to really
                    // include all documents:
                    val localDate = d2.toLocalDateTime(timeZone).date
                    d2 = localDate.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(timeZone)
                }
                part2 = DateTools.dateToString(d2, resolution)
            } catch (e: Exception) {
            }
        }

        return newRangeQuery(field, part1, part2, startInclusive, endInclusive)
    }

    /**
     * Builds a new BooleanClause instance
     *
     * @param q sub query
     * @param occur how this clause should occur when matching documents
     * @return new BooleanClause instance
     */
    protected fun newBooleanClause(
        q: Query,
        occur: Occur
    ): BooleanClause {
        return BooleanClause(q, occur)
    }

    /**
     * Builds a new PrefixQuery instance
     *
     * @param prefix Prefix term
     * @return new PrefixQuery instance
     */
    protected fun newPrefixQuery(prefix: Term): Query {
        return PrefixQuery(prefix, multiTermRewriteMethod)
    }

    /**
     * Builds a new RegexpQuery instance
     *
     * @param regexp Regexp term
     * @return new RegexpQuery instance
     */
    protected fun newRegexpQuery(regexp: Term): Query {
        return RegexpQuery(
            regexp,
            RegExp.ALL,
            0,
            RegexpQuery.DEFAULT_PROVIDER,
            determinizeWorkLimit,
            multiTermRewriteMethod
        )
    }

    /**
     * Builds a new FuzzyQuery instance
     *
     * @param term Term
     * @param minimumSimilarity minimum similarity
     * @param prefixLength prefix length
     * @return new FuzzyQuery Instance
     */
    protected fun newFuzzyQuery(
        term: Term,
        minimumSimilarity: Float,
        prefixLength: Int
    ): Query {
        // FuzzyQuery doesn't yet allow constant score rewrite
        val text: String = term.text()
        val numEdits: Int =
            FuzzyQuery.floatToEdits(minimumSimilarity, text.codePointCount(0, text.length))
        return FuzzyQuery(term, numEdits, prefixLength)
    }

    /**
     * Builds a new [TermRangeQuery] instance
     *
     * @param field Field
     * @param part1 min
     * @param part2 max
     * @param startInclusive true if the start of the range is inclusive
     * @param endInclusive true if the end of the range is inclusive
     * @return new [TermRangeQuery] instance
     */
    protected fun newRangeQuery(
        field: String, part1: String?, part2: String?, startInclusive: Boolean, endInclusive: Boolean
    ): Query {

        val start: BytesRef? = if (part1 == null) {
            null
        } else {
            analyzer.normalize(field, part1)
        }

        val end: BytesRef? = if (part2 == null) {
            null
        } else {
            analyzer.normalize(field, part2)
        }

        return TermRangeQuery(
            field, start!!, end, startInclusive, endInclusive, multiTermRewriteMethod
        )
    }

    /**
     * Builds a new MatchAllDocsQuery instance
     *
     * @return new MatchAllDocsQuery instance
     */
    protected fun newMatchAllDocsQuery(): Query {
        return MatchAllDocsQuery()
    }

    /**
     * Builds a new WildcardQuery instance
     *
     * @param t wildcard term
     * @return new WildcardQuery instance
     */
    protected fun newWildcardQuery(t: Term): Query {
        return WildcardQuery(t, determinizeWorkLimit, multiTermRewriteMethod)
    }

    /**
     * Factory method for generating query, given a set of clauses. By default creates a boolean query
     * composed of clauses passed in.
     *
     *
     * Can be overridden by extending classes, to modify query being returned.
     *
     * @param clauses List that contains [BooleanClause] instances to
     * join.
     * @return Resulting [Query] object.
     * @exception ParseException throw in overridden method to
     * disallow
     */
    @Throws(ParseException::class)
    protected fun getBooleanQuery(clauses: MutableList<BooleanClause>): Query? {
        if (clauses.isEmpty()) {
            return null // all clause words were filtered away by the analyzer.
        }
        val query: BooleanQuery.Builder = newBooleanQuery()

        clauses.forEach { clause ->
            query.add(clause)
        }

        return query.build()
    }

    /**
     * Factory method for generating a query. Called when parser parses an input term token that
     * contains one or more wildcard characters (? and *), but is not a prefix term token (one that
     * has just a single * character at the end)
     *
     *
     * Depending on settings, prefix term may be lower-cased automatically. It will not go through
     * the default Analyzer, however, since normal Analyzers are unlikely to work properly with
     * wildcard templates.
     *
     *
     * Can be overridden by extending classes, to provide custom handling for wildcard queries,
     * which may be necessary due to missing analyzer calls.
     *
     * @param field Name of the field query will use.
     * @param termStr Term token that contains one or more wild card characters (? or *), but is not
     * simple prefix term
     * @return Resulting [Query] built for the term
     * @exception ParseException throw in overridden method to
     * disallow
     */
    @Throws(ParseException::class)
    protected fun getWildcardQuery(field: String, termStr: String): Query {
        if ("*" == field) {
            if ("*" == termStr) return newMatchAllDocsQuery()
        }
        if (!allowLeadingWildcard && (termStr.startsWith("*") || termStr.startsWith("?"))) throw ParseException(
            "'*' or '?' not allowed as first character in WildcardQuery"
        )

        val t = Term(field, analyzeWildcard(field, termStr))
        return newWildcardQuery(t)
    }

    private fun analyzeWildcard(field: String, termStr: String): BytesRef {
        // best effort to not pass the wildcard characters and escaped characters through #normalize
        val sb = BytesRefBuilder()
        var last = 0

        WILDCARD_PATTERN.findAll(termStr).forEach { matchResult ->
            if (matchResult.range.first > 0) {
                val chunk = termStr.substring(last, matchResult.range.first)
                val normalized: BytesRef = analyzer.normalize(field, chunk)
                sb.append(normalized)
            }
            // append the matched group - without normalizing
            sb.append(BytesRef(matchResult.value))

            last = matchResult.range.last + 1
        }
        if (last < termStr.length) {
            val chunk = termStr.substring(last)
            val normalized: BytesRef = analyzer.normalize(field, chunk)
            sb.append(normalized)
        }
        return sb.toBytesRef()
    }

    /**
     * Factory method for generating a query. Called when parser parses an input term token that
     * contains a regular expression query.
     *
     *
     * Depending on settings, pattern term may be lower-cased automatically. It will not go through
     * the default Analyzer, however, since normal Analyzers are unlikely to work properly with
     * regular expression templates.
     *
     *
     * Can be overridden by extending classes, to provide custom handling for regular expression
     * queries, which may be necessary due to missing analyzer calls.
     *
     * @param field Name of the field query will use.
     * @param termStr Term token that contains a regular expression
     * @return Resulting [Query] built for the term
     * @exception ParseException throw in overridden method to
     * disallow
     */
    @Throws(ParseException::class)
    protected fun getRegexpQuery(field: String, termStr: String): Query {
        // We need to pass the whole string to #normalize, which will not work with
        // custom attribute factories for the binary term impl, and may not work
        // with some analyzers
        val term: BytesRef = analyzer.normalize(field, termStr)
        val t = Term(field, term)
        return newRegexpQuery(t)
    }

    /**
     * Factory method for generating a query (similar to [.getWildcardQuery]). Called when
     * parser parses an input term token that uses prefix notation; that is, contains a single '*'
     * wildcard character as its last character. Since this is a special case of generic wildcard
     * term, and such a query can be optimized easily, this usually results in a different query
     * object.
     *
     *
     * Depending on settings, a prefix term may be lower-cased automatically. It will not go
     * through the default Analyzer, however, since normal Analyzers are unlikely to work properly
     * with wildcard templates.
     *
     *
     * Can be overridden by extending classes, to provide custom handling for wild card queries,
     * which may be necessary due to missing analyzer calls.
     *
     * @param field Name of the field query will use.
     * @param termStr Term token to use for building term for the query (**without** trailing '*'
     * character!)
     * @return Resulting [Query] built for the term
     * @exception ParseException throw in overridden method to
     * disallow
     */
    @Throws(ParseException::class)
    protected fun getPrefixQuery(field: String, termStr: String): Query {
        if (!allowLeadingWildcard && termStr.startsWith("*")) throw ParseException(
            "'*' not allowed as first character in PrefixQuery"
        )
        val term: BytesRef = analyzer.normalize(field, termStr)
        val t = Term(field, term)
        return newPrefixQuery(t)
    }

    /**
     * Factory method for generating a query (similar to [.getWildcardQuery]). Called when
     * parser parses an input term token that has the fuzzy suffix (~) appended.
     *
     * @param field Name of the field query will use.
     * @param termStr Term token to use for building term for the query
     * @return Resulting [Query] built for the term
     * @exception ParseException throw in overridden method to
     * disallow
     */
    @Throws(ParseException::class)
    protected fun getFuzzyQuery(
        field: String,
        termStr: String,
        minSimilarity: Float
    ): Query {
        val term: BytesRef = analyzer.normalize(field, termStr)
        val t = Term(field, term)
        return newFuzzyQuery(t, minSimilarity, fuzzyPrefixLength)
    }

    // extracted from the .jj grammar
    @Throws(ParseException::class)
    fun handleBareTokenQuery(
        qfield: String,
        term: Token,
        fuzzySlop: Token,
        prefix: Boolean,
        wildcard: Boolean,
        fuzzy: Boolean,
        regexp: Boolean
    ): Query {
        val q: Query?

        val termImage = discardEscapeChar(term.image!!)
        q = if (wildcard) {
            getWildcardQuery(qfield, term.image!!)
        } else if (prefix) {
            getPrefixQuery(
                qfield, discardEscapeChar(term.image!!.substring(0, term.image!!.length - 1))
            )
        } else if (regexp) {
            getRegexpQuery(qfield, term.image!!.substring(1, term.image!!.length - 1))
        } else if (fuzzy) {
            handleBareFuzzy(qfield, fuzzySlop, termImage)
        } else {
            getFieldQuery(qfield, termImage, false)
        }
        return q
    }

    /**
     * Determines the similarity distance for the given fuzzy token and term string.
     *
     *
     * The default implementation uses the string image of the `fuzzyToken` in an attempt to
     * parse it to a primitive float value. Otherwise, the [minimal][.getFuzzyMinSim] distance is returned. Subclasses can override this method to return a similarity
     * distance, say based on the `termStr`, if the `fuzzyToken` does not specify a
     * distance.
     *
     * @param fuzzyToken The Fuzzy token
     * @param termStr The Term string
     * @return The similarity distance
     */
    protected fun getFuzzyDistance(fuzzyToken: Token, termStr: String?): Float {
        try {
            return fuzzyToken.image!!.substring(1).toFloat()
        } catch (ignored: Exception) {
        }
        return fuzzyMinSim
    }

    @Throws(ParseException::class)
    fun handleBareFuzzy(
        qfield: String,
        fuzzySlop: Token,
        termImage: String
    ): Query {
        val fms = getFuzzyDistance(fuzzySlop, termImage)
        if (fms < 0.0f) {
            throw ParseException(
                "Minimum similarity for a FuzzyQuery has to be between 0.0f and 1.0f !"
            )
        } else if (fms >= 1.0f && fms != fms.toInt().toFloat()) {
            throw ParseException("Fractional edit distances are not allowed!")
        }
        return getFuzzyQuery(qfield, termImage, fms)
    }

    // extracted from the .jj grammar
    @Throws(ParseException::class)
    fun handleQuotedTerm(
        qfield: String,
        term: Token,
        fuzzySlop: Token?
    ): Query {
        var s = phraseSlop // default
        if (fuzzySlop != null) {
            try {
                s = fuzzySlop.image!!.substring(1).toFloat().toInt()
            } catch (ignored: Exception) {
            }
        }
        return getFieldQuery(
            qfield, discardEscapeChar(term.image!!.substring(1, term.image!!.length - 1)), s
        )
    }

    // extracted from the .jj grammar
    fun handleBoost(
        q: Query?,
        boost: Token?
    ): Query {
        var q: Query? = q
        if (boost != null) {
            var f = 1.0.toFloat()
            try {
                f = boost.image!!.toFloat()
            } catch (ignored: Exception) {
                /* Should this be handled somehow? (defaults to "no boost", if
         * boost number is invalid)
         */
            }

            // avoid boosting null queries, such as those caused by stop words
            if (q != null) {
                q = BoostQuery(q, f)
            }
        }
        return q!!
    }

    /**
     * Returns a String where the escape char has been removed, or kept only once if there was a
     * double escape.
     *
     *
     * Supports escaped Unicode characters, e.g. translates `\u005Cu0041` to `A`.
     */
    @Throws(ParseException::class)
    fun discardEscapeChar(input: String): String {
        // Create char array to hold unescaped char sequence
        val output = CharArray(input.length)

        // The length of the output can be less than the input
        // due to discarded escape chars. This variable holds
        // the actual length of the output
        var length = 0

        // We remember whether the last processed character was
        // an escape character
        var lastCharWasEscapeChar = false

        // The multiplier the current unicode digit must be multiplied with.
        // E.g. the first digit must be multiplied with 16^3, the second with 16^2...
        var codePointMultiplier = 0

        // Used to calculate the codepoint of the escaped unicode character
        var codePoint = 0

        for (i in 0..<input.length) {
            val curChar = input[i]
            if (codePointMultiplier > 0) {
                codePoint += hexToInt(curChar) * codePointMultiplier
                codePointMultiplier = codePointMultiplier ushr 4
                if (codePointMultiplier == 0) {
                    output[length++] = codePoint.toChar()
                    codePoint = 0
                }
            } else if (lastCharWasEscapeChar) {
                if (curChar == 'u') {
                    // found an escaped unicode character
                    codePointMultiplier = 16 * 16 * 16
                } else {
                    // this character was escaped
                    output[length] = curChar
                    length++
                }
                lastCharWasEscapeChar = false
            } else {
                if (curChar == '\\') {
                    lastCharWasEscapeChar = true
                } else {
                    output[length] = curChar
                    length++
                }
            }
        }

        if (codePointMultiplier > 0) {
            throw ParseException("Truncated Unicode escape sequence.")
        }

        if (lastCharWasEscapeChar) {
            throw ParseException("Term can not end with escape character.")
        }

        return String.fromCharArray(output, 0, length)
    }

    companion object {
        const val CONJ_NONE: Int = 0
        const val CONJ_AND: Int = 1
        const val CONJ_OR: Int = 2

        const val MOD_NONE: Int = 0
        const val MOD_NOT: Int = 10
        const val MOD_REQ: Int = 11

        // make it possible to call setDefaultOperator() without accessing
        // the nested class:
        /** Alternative form of QueryParser.Operator.AND  */
        val AND_OPERATOR: Operator = Operator.AND

        /** Alternative form of QueryParser.Operator.OR  */
        val OR_OPERATOR: Operator = Operator.OR

        private val WILDCARD_PATTERN: Regex = Regex("""(\\\.)|([?*]+)""")

        /** Returns the numeric value of the hexadecimal character  */
        @Throws(ParseException::class)
        fun hexToInt(c: Char): Int {
            return if ('0' <= c && c <= '9') {
                c.code - '0'.code
            } else if ('a' <= c && c <= 'f') {
                c.code - 'a'.code + 10
            } else if ('A' <= c && c <= 'F') {
                c.code - 'A'.code + 10
            } else {
                throw ParseException("Non-hex character in Unicode escape sequence: $c")
            }
        }

        /**
         * Returns a String where those characters that QueryParser expects to be escaped are escaped by a
         * preceding `\`.
         */
        fun escape(s: String): String {
            val sb = StringBuilder()
            for (i in 0..<s.length) {
                val c = s[i]
                // These characters are part of the query syntax and must be escaped
                if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':' || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~' || c == '*' || c == '?' || c == '|' || c == '&' || c == '/') {
                    sb.append('\\')
                }
                sb.append(c)
            }
            return sb.toString()
        }
    }
}
