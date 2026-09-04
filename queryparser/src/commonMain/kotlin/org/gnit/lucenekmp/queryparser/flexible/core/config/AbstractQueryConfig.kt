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

/**
 * This class is the base of [QueryConfigHandler] and [FieldConfig]. It has operations to set, unset
 * and get configuration values.
 *
 * Each configuration is a key-&gt;value pair. The key should be a unique [ConfigurationKey]
 * instance, and it also holds the value's type.
 *
 * @see ConfigurationKey
 */
abstract class AbstractQueryConfig internal constructor() {
    private val configMap: MutableMap<ConfigurationKey<*>, Any> = mutableMapOf()

    // although this class is public, it can only be constructed from package

    /**
     * Returns the value held by the given key.
     *
     * @param T the value's type
     * @param key the key, cannot be `null`
     * @return the value held by the given key
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: ConfigurationKey<T>): T? {
        return configMap[key] as T?
    }

    /**
     * Returns true if there is a value set with the given key, otherwise false.
     *
     * @param T the value's type
     * @param key the key, cannot be `null`
     * @return true if there is a value set with the given key, otherwise false
     */
    fun <T> has(key: ConfigurationKey<T>): Boolean {
        return configMap.containsKey(key)
    }

    /**
     * Sets a key and its value.
     *
     * @param T the value's type
     * @param key the key, cannot be `null`
     * @param value value to set
     */
    fun <T> set(key: ConfigurationKey<T>, value: T?) {
        if (value == null) {
            unset(key)
        } else {
            configMap[key] = value
        }
    }

    /**
     * Unsets the given key and its value.
     *
     * @param T the value's type
     * @param key the key
     * @return true if the key and value was set and removed, otherwise false
     */
    fun <T> unset(key: ConfigurationKey<T>): Boolean {
        return configMap.remove(key) != null
    }
}
