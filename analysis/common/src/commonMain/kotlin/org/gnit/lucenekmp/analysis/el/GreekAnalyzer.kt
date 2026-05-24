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
package org.gnit.lucenekmp.analysis.el

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/**
 * [Analyzer] for the Greek language.
 *
 * Supports an external list of stopwords (words that will not be indexed at all). A default set
 * of stopwords is used unless an alternative list is specified.
 *
 * **NOTE**: This class uses the same [org.gnit.lucenekmp.util.Version] dependent settings as
 * [StandardAnalyzer].
 *
 * @since 3.1
 */
class GreekAnalyzer : StopwordAnalyzerBase {
    /**
     * Builds an analyzer with the default stop words.
     */
    constructor() : this(DefaultSetHolder.DEFAULT_SET)

    /**
     * Builds an analyzer with the given stop words.
     *
     * **NOTE:** The stopwords set should be pre-processed with the logic of [GreekLowerCaseFilter]
     * for best results.
     *
     * @param stopwords a stopword set
     */
    constructor(stopwords: CharArraySet) : super(stopwords)

    /**
     * Creates [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] used to tokenize all
     * the text in the provided [org.gnit.lucenekmp.jdkport.Reader].
     *
     * @return [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] built from a
     * [StandardTokenizer] filtered with [GreekLowerCaseFilter], [StopFilter], and [GreekStemFilter]
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = GreekLowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        result = GreekStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return GreekLowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Greek stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT: String = "#"

        /**
         * Returns a set of default Greek-stopwords
         *
         * @return a set of default Greek-stopwords
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Lucene Greek Stopwords list
# Note: by default this file is used after GreekLowerCaseFilter,
# so when modifying this file use 'σ' instead of 'ς'
ο
η
το
οι
τα
του
τησ
των
τον
την
και
κι
κ
ειμαι
εισαι
ειναι
ειμαστε
ειστε
στο
στον
στη
στην
μα
αλλα
απο
για
προσ
με
σε
ωσ
παρα
αντι
κατα
μετα
θα
να
δε
δεν
μη
μην
επι
ενω
εαν
αν
τοτε
που
πωσ
ποιοσ
ποια
ποιο
ποιοι
ποιεσ
ποιων
ποιουσ
αυτοσ
αυτη
αυτο
αυτοι
αυτων
αυτουσ
αυτεσ
αυτα
εκεινοσ
εκεινη
εκεινο
εκεινοι
εκεινεσ
εκεινα
εκεινων
εκεινουσ
οπωσ
ομωσ
ισωσ
οσο
οτι
"""

        private object DefaultSetHolder {
            val DEFAULT_SET: CharArraySet

            init {
                try {
                    DEFAULT_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), STOPWORDS_COMMENT)
                } catch (ex: IOException) {
                    // default set should always be present as it is part of the
                    // distribution (JAR)
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}

