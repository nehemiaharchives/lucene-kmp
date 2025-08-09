package org.gnit.lucenekmp.queryparser.flexible.standard

import kotlinx.datetime.TimeZone
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.document.DateTools.Resolution
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.search.WildcardQuery


/** Configuration options common across queryparser implementations.  */
interface CommonQueryParserConfiguration {
    /**
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
     * Default: false.
     */
    var enablePositionIncrements: Boolean

    /**
     * By default QueryParser uses [ ][MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE] when creating a [ ], [WildcardQuery] or [TermRangeQuery]. This implementation is generally
     * preferable because it a) Runs faster b) Does not have the scarcity of terms unduly influence
     * score c) avoids any [org.apache.lucene.search.IndexSearcher.TooManyClauses] exception.
     * However, if your application really needs to use the old-fashioned [BooleanQuery]
     * expansion rewriting and the above points are not relevant then use this to change the rewrite
     * method. As another alternative, if you prefer all terms to be rewritten as a filter up-front,
     * you can use [MultiTermQuery.CONSTANT_SCORE_REWRITE]. For more
     * information on the different rewrite methods available, see [ ] documentation.
     */
    //fun setMultiTermRewriteMethod(method: MultiTermQuery.RewriteMethod)

    /**
     * @see .setMultiTermRewriteMethod
     */
    var multiTermRewriteMethod: MultiTermQuery.RewriteMethod

    /** Returns current locale, allowing access by subclasses.  */
    var locale: Locale

    //fun setTimeZone(timeZone: TimeZone)

    var timeZone: TimeZone

    val analyzer: Analyzer

    /**
     * @see .setAllowLeadingWildcard
     */
    /**
     * Set to `true` to allow leading wildcard characters.
     *
     *
     * When set, `*` or `` are allowed as the first character of a
     * PrefixQuery and WildcardQuery. Note that this can produce very slow queries on big indexes.
     *
     *
     * Default: false.
     */
    var allowLeadingWildcard: Boolean

    /** Get the minimal similarity for fuzzy queries.  */
    /**
     * Set the minimum similarity for fuzzy queries. Default is defined on [ ][FuzzyQuery.defaultMaxEdits].
     */
    var fuzzyMinSim: Float

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
    var fuzzyPrefixLength: Int

    /** Gets the default slop for phrases.  */
    /**
     * Sets the default slop for phrases. If zero, then exact phrase matches are required. Default
     * value is zero.
     */
    var phraseSlop: Int

    /**
     * Sets the default [Resolution] used for certain field when no [Resolution] is
     * defined for this field.
     *
     * @param dateResolution the default [Resolution]
     */
    var dateResolution: Resolution
}
