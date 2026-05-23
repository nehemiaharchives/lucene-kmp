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

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
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
import org.tartarus.snowball.ext.TurkishStemmer

/**
 * [Analyzer] for Turkish.
 *
 * @since 3.1
 */
class TurkishAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** File containing default Turkish stopwords. */
    companion object {
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

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(
                        StringReader(DEFAULT_STOPWORD_DATA),
                        STOPWORDS_COMMENT
                    )
                } catch (ex: IOException) {
                    // default set should always be present as it is part of the
                    // distribution (JAR)
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Turkish stopwords from LUCENE-559
# merged with the list from "Information Retrieval on Turkish Texts"
#   (http://www.users.muohio.edu/canf/papers/JASIST2008offPrint.pdf)
acaba
altmış
altı
ama
ancak
arada
aslında
ayrıca
bana
bazı
belki
ben
benden
beni
benim
beri
beş
bile
bin
bir
birçok
biri
birkaç
birkez
birşey
birşeyi
biz
bize
bizden
bizi
bizim
böyle
böylece
bu
buna
bunda
bundan
bunlar
bunları
bunların
bunu
bunun
burada
çok
çünkü
da
daha
dahi
de
defa
değil
diğer
diye
doksan
dokuz
dolayı
dolayısıyla
dört
edecek
eden
ederek
edilecek
ediliyor
edilmesi
ediyor
eğer
elli
en
etmesi
etti
ettiği
ettiğini
gibi
göre
halen
hangi
hatta
hem
henüz
hep
hepsi
her
herhangi
herkesin
hiç
hiçbir
için
iki
ile
ilgili
ise
işte
itibaren
itibariyle
kadar
karşın
katrilyon
kendi
kendilerine
kendini
kendisi
kendisine
kendisini
kez
ki
kim
kimden
kime
kimi
kimse
kırk
milyar
milyon
mu
mü
mı
nasıl
ne
neden
nedenle
nerde
nerede
nereye
niye
niçin
o
olan
olarak
oldu
olduğu
olduğunu
olduklarını
olmadı
olmadığı
olmak
olması
olmayan
olmaz
olsa
olsun
olup
olur
olursa
oluyor
on
ona
ondan
onlar
onlardan
onları
onların
onu
onun
otuz
oysa
öyle
pek
rağmen
sadece
sanki
sekiz
seksen
sen
senden
seni
senin
siz
sizden
sizi
sizin
şey
şeyden
şeyi
şeyler
şöyle
şu
şuna
şunda
şundan
şunları
şunu
tarafından
trilyon
tüm
üç
üzere
var
vardı
ve
veya
ya
yani
yapacak
yapılan
yapılması
yapıyor
yapmak
yaptı
yaptığı
yaptığını
yaptıkları
yedi
yerine
yetmiş
yine
yirmi
yoksa
yüz
zaten
"""
    }

    /**
     * Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE].
     */
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
     *     [StandardTokenizer] filtered with [TurkishLowerCaseFilter], [StopFilter],
     *     [SetKeywordMarkerFilter] if a stem exclusion set is provided and [SnowballFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = ApostropheFilter(source)
        result = TurkishLowerCaseFilter(result)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SnowballFilter(result, TurkishStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return TurkishLowerCaseFilter(`in`)
    }
}

