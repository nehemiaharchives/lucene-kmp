package org.gnit.lucenekmp.analysis.br

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/**
 * [org.gnit.lucenekmp.analysis.Analyzer] for Brazilian Portuguese language.
 *
 * Supports an external list of stopwords (words that will not be indexed at all) and an external
 * list of exclusions (words that will not be stemmed, but indexed).
 *
 * **NOTE**: This class uses the same `Version` dependent settings as `StandardAnalyzer`.
 */
class BrazilianAnalyzer : StopwordAnalyzerBase {
    /** Contains words that should be indexed but not stemmed. */
    private var excltable: CharArraySet = CharArraySet.EMPTY_SET

    /** Builds an analyzer with the default stop words ([getDefaultStopSet]). */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    /**
     * Builds an analyzer with the given stop words
     *
     * @param stopwords a stopword set
     */
    constructor(stopwords: CharArraySet) : super(stopwords)

    /**
     * Builds an analyzer with the given stop words and stemming exclusion words
     *
     * @param stopwords a stopword set
     */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : this(stopwords) {
        excltable = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!excltable.isEmpty()) {
            result = SetKeywordMarkerFilter(result, excltable)
        }
        return TokenStreamComponents(source, BrazilianStemFilter(result))
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Brazilian Portuguese stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         *
         * @return an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
a
ainda
alem
ambas
ambos
antes
ao
aonde
aos
apos
aquele
aqueles
as
assim
com
como
contra
contudo
cuja
cujas
cujo
cujos
da
das
de
dela
dele
deles
demais
depois
desde
desta
deste
dispoe
dispoem
diversa
diversas
diversos
do
dos
durante
e
ela
elas
ele
eles
em
entao
entre
essa
essas
esse
esses
esta
estas
este
estes
ha
isso
isto
logo
mais
mas
mediante
menos
mesma
mesmas
mesmo
mesmos
na
no
nao
nas
nem
nesse
neste
nos
o
os
ou
outra
outras
outro
outros
pelas
pelas
pelo
pelos
perante
pois
por
porque
portanto
proprio
propios
quais
qual
qualquer
quando
quanto
que
quem
quer
se
seja
sem
sendo
seu
seus
sob
sobre
sua
suas
tal
tambem
teu
teus
toda
todas
todo
todos
tua
tuas
tudo
um
uma
umas
uns
"""

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), "#")
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
