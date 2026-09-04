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

import org.gnit.lucenekmp.queryparser.flexible.core.config.FieldConfig
import org.gnit.lucenekmp.queryparser.flexible.core.config.FieldConfigListener
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys

/**
 * This listener is used to listen to [FieldConfig] requests in [QueryConfigHandler] and add
 * [ConfigurationKeys.POINTS_CONFIG] based on the [ConfigurationKeys.POINTS_CONFIG_MAP] set in the
 * [QueryConfigHandler].
 *
 * @see PointsConfig
 * @see QueryConfigHandler
 * @see ConfigurationKeys.POINTS_CONFIG
 * @see ConfigurationKeys.POINTS_CONFIG_MAP
 */
class PointsConfigListener(config: QueryConfigHandler) : FieldConfigListener {
    private val config: QueryConfigHandler

    /**
     * Constructs a [PointsConfigListener] object using the given [QueryConfigHandler].
     *
     * @param config the [QueryConfigHandler] it will listen too
     */
    init {
        this.config = config
    }

    override fun buildFieldConfig(fieldConfig: FieldConfig) {
        val pointsConfigMap = config.get(ConfigurationKeys.POINTS_CONFIG_MAP)

        if (pointsConfigMap != null) {
            val pointsConfig = pointsConfigMap[fieldConfig.field]

            if (pointsConfig != null) {
                fieldConfig.set(ConfigurationKeys.POINTS_CONFIG, pointsConfig)
            }
        }
    }
}
