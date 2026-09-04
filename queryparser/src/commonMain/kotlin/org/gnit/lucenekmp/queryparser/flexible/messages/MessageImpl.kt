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
package org.gnit.lucenekmp.queryparser.flexible.messages

import org.gnit.lucenekmp.jdkport.Locale

/**
 * Default implementation of Message interface. For Native Language Support (NLS), system of
 * software internationalization.
 */
class MessageImpl(
    override val key: String,
    vararg args: Any?,
) : Message {
    override val arguments: Array<out Any?> = args

    override val localizedMessage: String
        get() = getLocalizedMessage(Locale.getDefault())

    override fun getLocalizedMessage(locale: Locale): String {
        return NLS.getLocalizedMessage(key, locale, *arguments)
    }

    override fun toString(): String {
        val args = arguments
        val sb = StringBuilder(key)
        for (i in args.indices) {
            sb.append(if (i == 0) " " else ", ").append(args[i])
        }
        return sb.toString()
    }
}
