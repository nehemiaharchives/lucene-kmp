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
package org.gnit.lucenekmp.analysis.sr

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.tartarus.snowball.ext.SerbianStemmer

/**
 * [org.gnit.lucenekmp.analysis.Analyzer] for Serbian.
 *
 * @since 8.6
 */
class SerbianAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE]. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    /**
     * Builds an analyzer with the given stop words.
     *
     * @param stopwords a stopword set
     */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /**
     * Builds an analyzer with the given stop words. If a non-empty stem exclusion set is provided
     * this analyzer will add a [SetKeywordMarkerFilter] before stemming.
     *
     * @param stopwords a stopword set
     * @param stemExclusionSet a set of terms not to be stemmed
     */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    /**
     * Creates a [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] which tokenizes all
     * the text in the provided [Reader][org.gnit.lucenekmp.jdkport.Reader].
     *
     * @return A [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] built from an
     *     [StandardTokenizer] filtered with [LowerCaseFilter], [StopFilter],
     *     [SetKeywordMarkerFilter] if a stem exclusion set is provided, [SnowballFilter]
     *     ([SerbianStemmer] https://snowballstem.org/algorithms/serbian/stemmer.html), and
     *     [SerbianNormalizationFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SnowballFilter(result, SerbianStemmer())
        result = SerbianNormalizationFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Serbian stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        /** The comment character in the stopwords file. All lines prefixed with this will be ignored. */
        private const val STOPWORDS_COMMENT: String = "#"

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        /**
         * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class accesses the
         * static final set the first time.
         */
        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), STOPWORDS_COMMENT)
                } catch (ex: IOException) {
                    // default set should always be present as it is part of the
                    // distribution (JAR)
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
i
ili
a
ali
pa
biti
ne
jesam
sam
jesi
si
je
jesmo
smo
jeste
ste
jesu
su
nijesam
nisam
nijesi
nisi
nije
nijesmo
nismo
nijeste
niste
nijesu
nisu
budem
budeš
bude
budemo
budete
budu
budes
bih
bi
bismo
biste
biše
bise
bio
bili
budimo
budite
bila
bilo
bile
ću
ćeš
će
ćemo
ćete
neću
nećeš
neće
nećemo
nećete
cu
ces
ce
cemo
cete
necu
neces
nece
necemo
necete
mogu
možeš
može
možemo
možete
mozes
moze
mozemo
mozete
и
или
а
али
па
бити
не
јесам
сам
јеси
си
је
јесмо
смо
јесте
сте
јесу
су
нијесам
нисам
нијеси
ниси
није
нијесмо
нисмо
нијесте
нисте
нијесу
нису
будем
будеш
буде
будемо
будете
буду
будес
бих
би
бисмо
бисте
бише
бисе
био
били
будимо
будите
била
било
биле
ћу
ћеш
ће
ћемо
ћете
нећу
нећеш
неће
нећемо
нећете
цу
цес
це
цемо
цете
нецу
нецес
неце
нецемо
нецете
могу
можеш
може
можемо
можете
мозес
мозе
моземо
мозете
"""
    }
}
