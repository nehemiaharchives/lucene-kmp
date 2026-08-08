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

import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.queryparser.flexible.core.config.FieldConfig
import org.gnit.lucenekmp.queryparser.flexible.core.config.FieldConfigListener
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys

/**
 * This listener listens for every field configuration request and assign a
 * [ConfigurationKeys.DATE_RESOLUTION] to the equivalent [FieldConfig] based on a defined map:
 * fieldName -&gt; [DateTools.Resolution] stored in [ConfigurationKeys.FIELD_DATE_RESOLUTION_MAP].
 *
 * @see ConfigurationKeys.DATE_RESOLUTION
 * @see ConfigurationKeys.FIELD_DATE_RESOLUTION_MAP
 * @see FieldConfig
 * @see FieldConfigListener
 */
class FieldDateResolutionFCListener(private val config: QueryConfigHandler) : FieldConfigListener {
    override fun buildFieldConfig(fieldConfig: FieldConfig) {
        var dateRes: DateTools.Resolution? = null
        val dateResMap = config.get(ConfigurationKeys.FIELD_DATE_RESOLUTION_MAP)

        if (dateResMap != null) {
            dateRes = dateResMap[fieldConfig.field]
        }

        if (dateRes == null) {
            dateRes = config.get(ConfigurationKeys.DATE_RESOLUTION)
        }

        if (dateRes != null) {
            fieldConfig.set(ConfigurationKeys.DATE_RESOLUTION, dateRes)
        }
    }
}
