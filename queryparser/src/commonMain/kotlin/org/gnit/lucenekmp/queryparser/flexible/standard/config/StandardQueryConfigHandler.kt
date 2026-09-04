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
package org.gnit.lucenekmp.queryparser.flexible.standard.config

import kotlinx.datetime.TimeZone
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.queryparser.flexible.core.config.ConfigurationKey
import org.gnit.lucenekmp.queryparser.flexible.core.config.FieldConfig
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.search.MultiTermQuery

/**
 * This query configuration handler is used for almost every processor defined in the
 * [StandardQueryNodeProcessorPipeline] processor pipeline. It holds configuration methods that
 * reproduce the configuration methods that could be set on the old lucene 2.4 QueryParser class.
 *
 * @see StandardQueryNodeProcessorPipeline
 */
class StandardQueryConfigHandler : QueryConfigHandler() {
    /** Class holding keys for StandardQueryNodeProcessorPipeline options. */
    object ConfigurationKeys {
        /**
         * Key used to set whether position increments is enabled
         *
         * @see StandardQueryParser.setEnablePositionIncrements
         * @see StandardQueryParser.getEnablePositionIncrements
         */
        val ENABLE_POSITION_INCREMENTS: ConfigurationKey<Boolean> =
            ConfigurationKey.newInstance()

        /**
         * Key used to set whether leading wildcards are supported
         *
         * @see StandardQueryParser.setAllowLeadingWildcard
         * @see StandardQueryParser.getAllowLeadingWildcard
         */
        val ALLOW_LEADING_WILDCARD: ConfigurationKey<Boolean> = ConfigurationKey.newInstance()

        /**
         * Key used to set the [Analyzer] used for terms found in the query
         *
         * @see StandardQueryParser.setAnalyzer
         * @see StandardQueryParser.getAnalyzer
         */
        val ANALYZER: ConfigurationKey<Analyzer> = ConfigurationKey.newInstance()

        /**
         * Key used to set the default boolean operator
         *
         * @see StandardQueryParser.setDefaultOperator
         * @see StandardQueryParser.getDefaultOperator
         */
        val DEFAULT_OPERATOR: ConfigurationKey<Operator> = ConfigurationKey.newInstance()

        /**
         * Key used to set the default phrase slop
         *
         * @see StandardQueryParser.setPhraseSlop
         * @see StandardQueryParser.getPhraseSlop
         */
        val PHRASE_SLOP: ConfigurationKey<Int> = ConfigurationKey.newInstance()

        /**
         * Key used to set the [Locale] used when parsing the query
         *
         * @see StandardQueryParser.setLocale
         * @see StandardQueryParser.getLocale
         */
        val LOCALE: ConfigurationKey<Locale> = ConfigurationKey.newInstance()

        val TIMEZONE: ConfigurationKey<TimeZone> = ConfigurationKey.newInstance()

        /**
         * Key used to set the [MultiTermQuery.RewriteMethod] used when creating queries
         *
         * @see StandardQueryParser.setMultiTermRewriteMethod
         * @see StandardQueryParser.getMultiTermRewriteMethod
         */
        val MULTI_TERM_REWRITE_METHOD: ConfigurationKey<MultiTermQuery.RewriteMethod> =
            ConfigurationKey.newInstance()

        /**
         * Key used to set the fields a query should be expanded to when the field is `null`
         *
         * @see StandardQueryParser.setMultiFields
         * @see StandardQueryParser.getMultiFields
         */
        val MULTI_FIELDS: ConfigurationKey<Array<CharSequence>> = ConfigurationKey.newInstance()

        /**
         * Key used to set a field to boost map that is used to set the boost for each field
         *
         * @see StandardQueryParser.setFieldsBoost
         * @see StandardQueryParser.getFieldsBoost
         */
        val FIELD_BOOST_MAP: ConfigurationKey<Map<String, Float>> = ConfigurationKey.newInstance()

        /**
         * Key used to set a field to [DateTools.Resolution] map that is used to normalize each date
         * field value.
         *
         * @see StandardQueryParser.setDateResolutionMap
         * @see StandardQueryParser.getDateResolutionMap
         */
        val FIELD_DATE_RESOLUTION_MAP:
            ConfigurationKey<Map<CharSequence, DateTools.Resolution>> = ConfigurationKey.newInstance()

        /**
         * Key used to set the [FuzzyConfig] used to create fuzzy queries.
         *
         * @see StandardQueryParser.setFuzzyMinSim
         * @see StandardQueryParser.setFuzzyPrefixLength
         * @see StandardQueryParser.getFuzzyMinSim
         * @see StandardQueryParser.getFuzzyPrefixLength
         */
        val FUZZY_CONFIG: ConfigurationKey<FuzzyConfig> = ConfigurationKey.newInstance()

        /**
         * Key used to set default [DateTools.Resolution].
         *
         * @see StandardQueryParser.setDateResolution
         * @see StandardQueryParser.getDateResolution
         */
        val DATE_RESOLUTION: ConfigurationKey<DateTools.Resolution> = ConfigurationKey.newInstance()

        /**
         * Key used to set the boost value in [FieldConfig] objects.
         *
         * @see StandardQueryParser.setFieldsBoost
         * @see StandardQueryParser.getFieldsBoost
         */
        val BOOST: ConfigurationKey<Float> = ConfigurationKey.newInstance()

        /**
         * Key used to set a field to its [PointsConfig].
         *
         * @see StandardQueryParser.setPointsConfigMap
         * @see StandardQueryParser.getPointsConfigMap
         */
        val POINTS_CONFIG: ConfigurationKey<PointsConfig> = ConfigurationKey.newInstance()

        /**
         * Key used to set the [PointsConfig] in [FieldConfig] for point fields.
         *
         * @see StandardQueryParser.setPointsConfigMap
         * @see StandardQueryParser.getPointsConfigMap
         */
        val POINTS_CONFIG_MAP: ConfigurationKey<Map<String, PointsConfig>> =
            ConfigurationKey.newInstance()
    }

    /** Boolean Operator: AND or OR */
    enum class Operator {
        AND,
        OR,
    }

    init {
        // Add listener that will build the FieldConfig.
        addFieldConfigListener(FieldBoostMapFCListener(this))
        addFieldConfigListener(FieldDateResolutionFCListener(this))
        addFieldConfigListener(PointsConfigListener(this))

        // Default Values
        set(ConfigurationKeys.ALLOW_LEADING_WILDCARD, false) // default in 2.9
        set(ConfigurationKeys.ANALYZER, null) // default value 2.4
        set(ConfigurationKeys.DEFAULT_OPERATOR, Operator.OR)
        set(ConfigurationKeys.PHRASE_SLOP, 0) // default value 2.4
        set(ConfigurationKeys.ENABLE_POSITION_INCREMENTS, false) // default value 2.4
        set(ConfigurationKeys.FIELD_BOOST_MAP, linkedMapOf())
        set(ConfigurationKeys.FUZZY_CONFIG, FuzzyConfig())
        set(ConfigurationKeys.LOCALE, Locale.getDefault())
        set(
            ConfigurationKeys.MULTI_TERM_REWRITE_METHOD,
            MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE,
        )
        set(ConfigurationKeys.FIELD_DATE_RESOLUTION_MAP, mutableMapOf())
    }
}
