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
package org.gnit.lucenekmp.queryparser.flexible.core.config

import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessor
import org.gnit.lucenekmp.queryparser.flexible.core.util.StringUtils

/**
 * This class can be used to hold any query configuration and no field configuration. For field
 * configuration, it creates an empty [FieldConfig] object and delegate it to field config
 * listeners, these are responsible for setting up all the field configuration.
 *
 * [QueryConfigHandler] should be extended by classes that intend to provide configuration to
 * [QueryNodeProcessor] objects.
 *
 * The class that extends [QueryConfigHandler] should also provide [FieldConfig] objects for each
 * collection field.
 *
 * @see FieldConfig
 * @see FieldConfigListener
 * @see QueryConfigHandler
 */
abstract class QueryConfigHandler : AbstractQueryConfig() {
    private val listeners: MutableList<FieldConfigListener> = mutableListOf()

    /**
     * Returns an implementation of [FieldConfig] for a specific field name. If the implemented
     * [QueryConfigHandler] does not know a specific field name, it may return `null`, indicating
     * there is no configuration for that field.
     *
     * @param fieldName the field name
     * @return a [FieldConfig] object containing the field name configuration or `null`, if the
     * implemented [QueryConfigHandler] has no configuration for that field
     */
    open fun getFieldConfig(fieldName: String): FieldConfig? {
        val fieldConfig = FieldConfig(requireNotNull(StringUtils.toString(fieldName)))

        for (listener in listeners) {
            listener.buildFieldConfig(fieldConfig)
        }

        return fieldConfig
    }

    /**
     * Adds a listener. The added listeners are called in the order they are added.
     *
     * @param listener the listener to be added
     */
    fun addFieldConfigListener(listener: FieldConfigListener) {
        listeners.add(listener)
    }
}
