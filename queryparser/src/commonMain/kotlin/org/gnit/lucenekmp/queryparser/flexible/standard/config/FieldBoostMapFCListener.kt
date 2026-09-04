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
 * This listener listens for every field configuration request and assign a
 * [ConfigurationKeys.BOOST] to the equivalent [FieldConfig] based on a defined map: fieldName
 * -&gt; boostValue stored in [ConfigurationKeys.FIELD_BOOST_MAP].
 *
 * @see ConfigurationKeys.FIELD_BOOST_MAP
 * @see ConfigurationKeys.BOOST
 * @see FieldConfig
 * @see FieldConfigListener
 */
class FieldBoostMapFCListener(private val config: QueryConfigHandler) : FieldConfigListener {
    override fun buildFieldConfig(fieldConfig: FieldConfig) {
        val fieldBoostMap = config.get(ConfigurationKeys.FIELD_BOOST_MAP)

        if (fieldBoostMap != null) {
            val boost = fieldBoostMap[fieldConfig.field]

            if (boost != null) {
                fieldConfig.set(ConfigurationKeys.BOOST, boost)
            }
        }
    }
}
