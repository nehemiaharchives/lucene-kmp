package org.gnit.lucenekmp.analysis.lv

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
 * [org.gnit.lucenekmp.analysis.Analyzer] for Latvian.
 *
 * @since 3.2
 */
class LatvianAnalyzer : StopwordAnalyzerBase {
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
     * if a stem exclusion set is provided and [LatvianStemFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) result = SetKeywordMarkerFilter(result, stemExclusionSet)
        result = LatvianStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Latvian stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val DEFAULT_STOPWORD_DATA: String = """
# Set of Latvian stopwords from A Stemming Algorithm for Latvian, Karlis Kreslins
# the original list of over 800 forms was refined: 
#   pronouns, adverbs, interjections were removed
# 
# prepositions
aiz
ap
ar
apakš
ārpus
augšpus
bez
caur
dēļ
gar
iekš
iz
kopš
labad
lejpus
līdz
no
otrpus
pa
par
pār
pēc
pie
pirms
pret
priekš
starp
šaipus
uz
viņpus
virs
virspus
zem
apakšpus
# Conjunctions
un
bet
jo
ja
ka
lai
tomēr
tikko
turpretī
arī
kaut
gan
tādēļ
tā
ne
tikvien
vien
kā
ir
te
vai
kamēr
# Particles
ar
diezin
droši
diemžēl
nebūt
ik
it
taču
nu
pat
tiklab
iekšpus
nedz
tik
nevis
turpretim
jeb
iekam
iekām
iekāms
kolīdz
līdzko
tiklīdz
jebšu
tālab
tāpēc
nekā
itin
jā
jau
jel
nē
nezin
tad
tikai
vis
tak
iekams
vien
# modal verbs
būt  
biju 
biji
bija
bijām
bijāt
esmu
esi
esam
esat 
būšu     
būsi
būs
būsim
būsiet
tikt
tiku
tiki
tika
tikām
tikāt
tieku
tiec
tiek
tiekam
tiekat
tikšu
tiks
tiksim
tiksiet
tapt
tapi
tapāt
topat
tapšu
tapsi
taps
tapsim
tapsiet
kļūt
kļuvu
kļuvi
kļuva
kļuvām
kļuvāt
kļūstu
kļūsti
kļūst
kļūstam
kļūstat
kļūšu
kļūsi
kļūs
kļūsim
kļūsiet
# verbs
varēt
varēju
varējām
varēšu
varēsim
var
varēji
varējāt
varēsi
varēsiet
varat
varēja
varēs
"""

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
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), "#")
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
