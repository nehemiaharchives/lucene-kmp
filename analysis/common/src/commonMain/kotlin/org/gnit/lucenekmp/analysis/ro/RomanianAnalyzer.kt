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
package org.gnit.lucenekmp.analysis.ro

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
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
import org.tartarus.snowball.ext.RomanianStemmer

/**
 * [Analyzer] for Romanian.
 *
 * @since 3.1
 */
class RomanianAnalyzer : StopwordAnalyzerBase {
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
     *     [RomanianNormalizationFilter], [SetKeywordMarkerFilter] if a stem exclusion set is
     *     provided and [SnowballFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        result = RomanianNormalizationFilter(result)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SnowballFilter(result, RomanianStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = RomanianNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Romanian stopwords. */
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
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), STOPWORDS_COMMENT)
                } catch (ex: IOException) {
                    // default set should always be present as it is part of the
                    // distribution (JAR)
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# This file was created by Jacques Savoy and is distributed under the BSD license.
# See http://members.unine.ch/jacques.savoy/clef/index.html.
# Also see http://www.opensource.org/licenses/bsd-license.html
# Updated to include s&t with comma (ș/ț), which are more standard than cedilla (ş/ţ)
acea
aceasta
această
aceea
acei
aceia
acel
acela
acele
acelea
acest
acesta
aceste
acestea
acești
aceşti
aceștia
aceştia
acolo
acum
ai
aia
aibă
aici
al
ăla
ale
alea
ălea
altceva
altcineva
am
ar
are
aș
aş
așadar
aşadar
asemenea
asta
ăsta
astăzi
astea
ăstea
ăștia
ăştia
asupra
ați
aţi
au
avea
avem
aveți
aveţi
azi
bine
bucur
bună
ca
că
căci
când
care
cărei
căror
cărui
cât
câte
câți
câţi
către
câtva
ce
cel
ceva
chiar
cînd
cine
cineva
cît
cîte
cîți
cîţi
cîtva
contra
cu
cum
cumva
curând
curînd
da
dă
dacă
dar
datorită
de
deci
deja
deoarece
departe
deși
deşi
din
dinaintea
dintr
dintre
drept
după
ea
ei
el
ele
eram
este
ești
eşti
eu
face
fără
fi
fie
fiecare
fii
fim
fiți
fiţi
iar
ieri
îi
îl
îmi
împotriva
în
înainte
înaintea
încât
încît
încotro
între
întrucât
întrucît
îți
îţi
la
lângă
le
li
lîngă
lor
lui
mă
mâine
mea
mei
mele
mereu
meu
mi
mine
mult
multă
mulți
mulţi
ne
nicăieri
nici
nimeni
niște
nişte
noastră
noastre
noi
noștri
noştri
nostru
nu
ori
oricând
oricare
oricât
orice
oricînd
oricine
oricît
oricum
oriunde
până
pe
pentru
peste
pînă
poate
pot
prea
prima
primul
prin
printr
sa
să
săi
sale
sau
său
se
și
şi
sînt
sîntem
sînteți
sînteţi
spre
sub
sunt
suntem
sunteți
sunteţi
ta
tăi
tale
tău
te
ți
ţi
ție
ţie
tine
toată
toate
tot
toți
toţi
totuși
totuşi
tu
un
una
unde
undeva
unei
unele
uneori
unor
vă
vi
voastră
voastre
voi
voștri
voştri
vostru
vouă
vreo
vreun
"""
    }
}