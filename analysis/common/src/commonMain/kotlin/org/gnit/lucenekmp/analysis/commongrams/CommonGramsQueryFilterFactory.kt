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
package org.gnit.lucenekmp.analysis.commongrams

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Construct [CommonGramsQueryFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_cmmngrmsqry" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.CommonGramsQueryFilterFactory" words="commongramsquerystopwords.txt" ignoreCase="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class CommonGramsQueryFilterFactory : CommonGramsFilterFactory {

    /** Creates a new CommonGramsQueryFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args)

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    /** Create a CommonGramsFilter and wrap it with a CommonGramsQueryFilter */
    override fun create(input: TokenStream): TokenFilter {
        val commonGrams = super.create(input) as CommonGramsFilter
        return CommonGramsQueryFilter(commonGrams)
    }

    companion object {
        const val NAME: String = "commonGramsQuery"
    }
}
