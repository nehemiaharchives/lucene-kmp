/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.queryparser.flexible.standard

import kotlinx.datetime.TimeZone
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.queryparser.flexible.core.QueryParserHelper
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.builders.StandardQueryTreeBuilder
import org.gnit.lucenekmp.queryparser.flexible.standard.config.FuzzyConfig
import org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfig
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.Operator
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParser
import org.gnit.lucenekmp.queryparser.flexible.standard.processors.StandardQueryNodeProcessorPipeline
import org.gnit.lucenekmp.search.FuzzyQuery
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.Query

/**
 * The [StandardQueryParser] is a pre-assembled query parser that supports most features of the
 * classic Lucene query parser, allows dynamic configuration of some of its features (like
 * multi-field expansion or wildcard query restrictions) and adds support for new query types and
 * expressions.
 *
 * TODO restore the remaining upstream syntax examples here comment by comment during parity audit.
 */
open class StandardQueryParser() : QueryParserHelper(
    StandardQueryConfigHandler(),
    StandardSyntaxParser(),
    StandardQueryNodeProcessorPipeline(null),
    StandardQueryTreeBuilder()
), CommonQueryParserConfiguration {

    /**
     * Constructs a [StandardQueryParser] object and sets an [Analyzer] to it. The same as:
     *
     * <pre class="prettyprint">
     * StandardQueryParser qp = new StandardQueryParser();
     * qp.getQueryConfigHandler().setAnalyzer(analyzer);
     * </pre>
     *
     * @param analyzer the analyzer to be used by this query parser helper
     */
    constructor(analyzer: Analyzer) : this() {
        this.analyzer = analyzer
    }

    override fun toString(): String {
        return "<StandardQueryParser config=\"" + this.queryConfigHandler + "\"/>"
    }

    /**
     * Overrides [QueryParserHelper.parse] so it casts the return object to [Query]. For more
     * reference about this method, check [QueryParserHelper.parse].
     *
     * @param query the query string
     * @param defaultField the default field used by the text parser
     * @return the object built from the query
     */
    override fun parse(query: String, defaultField: String?): Query {
        return super.parse(query, defaultField) as Query
    }

    /** Gets implicit operator setting, which will be either [Operator.AND] or [Operator.OR]. */
    var defaultOperator: Operator
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.DEFAULT_OPERATOR)!!
        set(operator) {
            requireNotNull(queryConfigHandler).set(ConfigurationKeys.DEFAULT_OPERATOR, operator)
        }

    /**
     * Set to `true` to allow leading wildcard characters.
     *
     * <p>When set, `*` or `?` are allowed as the first character of a PrefixQuery and WildcardQuery.
     * Note that this can produce very slow queries on big indexes.
     *
     * <p>Default: false.
     */
    override var allowLeadingWildcard: Boolean
        get() = requireNotNull(queryConfigHandler)
            .get(ConfigurationKeys.ALLOW_LEADING_WILDCARD) ?: false
        set(allowLeadingWildcard) {
            requireNotNull(queryConfigHandler)
                .set(ConfigurationKeys.ALLOW_LEADING_WILDCARD, allowLeadingWildcard)
        }

    /**
     * Set to `true` to enable position increments in result query.
     *
     * <p>When set, result phrase and multi-phrase queries will be aware of position increments.
     * Useful when e.g. a StopFilter increases the position increment of the token that follows an
     * omitted token.
     *
     * <p>Default: false.
     */
    override var enablePositionIncrements: Boolean
        get() = requireNotNull(queryConfigHandler)
            .get(ConfigurationKeys.ENABLE_POSITION_INCREMENTS) ?: false
        set(enabled) {
            requireNotNull(queryConfigHandler)
                .set(ConfigurationKeys.ENABLE_POSITION_INCREMENTS, enabled)
        }

    /** Constructs a [StandardQueryParser] object. */
    init {
        enablePositionIncrements = true
    }

    override var multiTermRewriteMethod: MultiTermQuery.RewriteMethod
        get() = requireNotNull(queryConfigHandler)
            .get(ConfigurationKeys.MULTI_TERM_REWRITE_METHOD)!!
        set(method) {
            requireNotNull(queryConfigHandler)
                .set(ConfigurationKeys.MULTI_TERM_REWRITE_METHOD, method)
        }

    /**
     * Set the fields a query should be expanded to when the field is `null`
     *
     * @param fields the fields used to expand the query
     */
    var multiFields: Array<CharSequence>?
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.MULTI_FIELDS)
        set(fields) {
            requireNotNull(queryConfigHandler)
                .set(ConfigurationKeys.MULTI_FIELDS, fields ?: emptyArray())
        }

    /**
     * Set the prefix length for fuzzy queries. Default is 0.
     *
     * @param fuzzyPrefixLength The fuzzyPrefixLength to set.
     */
    override var fuzzyPrefixLength: Int
        get() {
            val fuzzyConfig =
                requireNotNull(queryConfigHandler).get(ConfigurationKeys.FUZZY_CONFIG)
            return fuzzyConfig?.prefixLength ?: FuzzyQuery.defaultPrefixLength
        }
        set(fuzzyPrefixLength) {
            val config: QueryConfigHandler = requireNotNull(queryConfigHandler)
            var fuzzyConfig = config.get(ConfigurationKeys.FUZZY_CONFIG)
            if (fuzzyConfig == null) {
                fuzzyConfig = FuzzyConfig()
                config.set(ConfigurationKeys.FUZZY_CONFIG, fuzzyConfig)
            }
            fuzzyConfig.prefixLength = fuzzyPrefixLength
        }

    var pointsConfigMap: Map<String, PointsConfig>?
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.POINTS_CONFIG_MAP)
        set(pointsConfigMap) {
            requireNotNull(queryConfigHandler)
                .set(ConfigurationKeys.POINTS_CONFIG_MAP, pointsConfigMap)
        }

    /** Set locale used by date range parsing. */
    override var locale: Locale
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.LOCALE)!!
        set(locale) {
            requireNotNull(queryConfigHandler).set(ConfigurationKeys.LOCALE, locale)
        }

    override var timeZone: TimeZone
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.TIMEZONE)!!
        set(timeZone) {
            requireNotNull(queryConfigHandler).set(ConfigurationKeys.TIMEZONE, timeZone)
        }

    /**
     * Sets the default slop for phrases. If zero, then exact phrase matches are required. Default
     * value is zero.
     */
    override var phraseSlop: Int
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.PHRASE_SLOP) ?: 0
        set(defaultPhraseSlop) {
            requireNotNull(queryConfigHandler)
                .set(ConfigurationKeys.PHRASE_SLOP, defaultPhraseSlop)
        }

    override var analyzer: Analyzer?
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.ANALYZER)
        set(analyzer) {
            requireNotNull(queryConfigHandler).set(ConfigurationKeys.ANALYZER, analyzer)
        }

    /** Get the minimal similarity for fuzzy queries. */
    override var fuzzyMinSim: Float
        get() {
            val fuzzyConfig =
                requireNotNull(queryConfigHandler).get(ConfigurationKeys.FUZZY_CONFIG)
            return fuzzyConfig?.minSimilarity ?: FuzzyQuery.defaultMaxEdits.toFloat()
        }
        set(fuzzyMinSim) {
            val config: QueryConfigHandler = requireNotNull(queryConfigHandler)
            var fuzzyConfig = config.get(ConfigurationKeys.FUZZY_CONFIG)
            if (fuzzyConfig == null) {
                fuzzyConfig = FuzzyConfig()
                config.set(ConfigurationKeys.FUZZY_CONFIG, fuzzyConfig)
            }
            fuzzyConfig.minSimilarity = fuzzyMinSim
        }

    /**
     * Sets the boost used for each field.
     *
     * @param boosts a collection that maps a field to its boost
     */
    var fieldsBoost: Map<String, Float>?
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.FIELD_BOOST_MAP)
        set(boosts) {
            requireNotNull(queryConfigHandler).set(ConfigurationKeys.FIELD_BOOST_MAP, boosts)
        }

    /**
     * Sets the default [DateTools.Resolution] used for certain field when no
     * [DateTools.Resolution] is defined for this field.
     */
    override var dateResolution: DateTools.Resolution
        get() = requireNotNull(queryConfigHandler).get(ConfigurationKeys.DATE_RESOLUTION)!!
        set(dateResolution) {
            requireNotNull(queryConfigHandler)
                .set(ConfigurationKeys.DATE_RESOLUTION, dateResolution)
        }

    /**
     * Returns the field to [DateTools.Resolution] map used to normalize each date field.
     *
     * @return the field to [DateTools.Resolution] map
     */
    var dateResolutionMap: Map<CharSequence, DateTools.Resolution>?
        get() = requireNotNull(queryConfigHandler)
            .get(ConfigurationKeys.FIELD_DATE_RESOLUTION_MAP)
        set(dateRes) {
            requireNotNull(queryConfigHandler)
                .set(ConfigurationKeys.FIELD_DATE_RESOLUTION_MAP, dateRes)
        }
}
