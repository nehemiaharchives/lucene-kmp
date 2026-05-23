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
package org.gnit.lucenekmp.analysis.tr

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [TurkishLowerCaseFilter].
 *
 * ```xml
 * <fieldType name="text_trlwr" class="solr.TextField" positionIncrementGap="100">
 *   <analyzer>
 *     <tokenizer class="solr.StandardTokenizerFactory"/>
 *     <filter class="solr.TurkishLowerCaseFilterFactory"/>
 *   </analyzer>
 * </fieldType>
 * ```
 *
 * @since 3.1.0
 * @lucene.spi [NAME]
 */
class TurkishLowerCaseFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args) {

    init {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    @Suppress("unused")
    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return TurkishLowerCaseFilter(input)
    }

    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "turkishLowercase"
    }
}

