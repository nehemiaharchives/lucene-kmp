package org.gnit.lucenekmp.analysis.eu

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
import org.tartarus.snowball.ext.BasqueStemmer

/** [org.gnit.lucenekmp.analysis.Analyzer] for Basque. */
class BasqueAnalyzer : StopwordAnalyzerBase {
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
     * the text in the provided [org.gnit.lucenekmp.jdkport.Reader].
     *
     * @return A [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] built from an
     * [StandardTokenizer] filtered with [LowerCaseFilter], [StopFilter], [SetKeywordMarkerFilter]
     * if a stem exclusion set is provided and [SnowballFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SnowballFilter(result, BasqueStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Basque stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# example set of basque stopwords
al
anitz
arabera
asko
baina
bat
batean
batek
bati
batzuei
batzuek
batzuetan
batzuk
bera
beraiek
berau
berauek
bere
berori
beroriek
beste
bezala
da
dago
dira
ditu
du
dute
edo
egin
ere
eta
eurak
ez
gainera
gu
gutxi
guzti
haiei
haiek
haietan
hainbeste
hala
han
handik
hango
hara
hari
hark
hartan
hau
hauei
hauek
hauetan
hemen
hemendik
hemengo
hi
hona
honek
honela
honetan
honi
hor
hori
horiei
horiek
horietan
horko
horra
horrek
horrela
horretan
horri
hortik
hura
izan
ni
noiz
nola
non
nondik
nongo
nor
nora
ze
zein
zen
zenbait
zenbat
zer
zergatik
ziren
zituzten
zu
zuek
zuen
zuten
"""

        /**
         * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class accesses the
         * static final set the first time.
         */
        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), "#")
                } catch (ex: IOException) {
                    // default set should always be present as it is part of the
                    // distribution (JAR)
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
