package org.gnit.lucenekmp.analysis.bg

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
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/**
 * [Analyzer] for Bulgarian.
 *
 * <p>This analyzer implements light-stemming as specified by: <i> Searching Strategies for the
 * Bulgarian Language </i> http://members.unine.ch/jacques.savoy/Papers/BUIR.pdf
 *
 * @since 3.1
 */
class BulgarianAnalyzer : StopwordAnalyzerBase {

    /**
     * File containing default Bulgarian stopwords.
     *
     * <p>Default stopword list is from http://members.unine.ch/jacques.savoy/clef/index.html The
     * stopword list is BSD-Licensed.
     */
    companion object {
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         *
         * @return an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        /**
         * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class accesses the
         * static final set the first time.;
         */
        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet = try {
                WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORDS), "#")
            } catch (ex: IOException) {
                // default set should always be present as it is part of the
                // distribution (JAR)
                throw UncheckedIOException("Unable to load default stopword set", ex)
            }
        }

        private const val DEFAULT_STOPWORDS: String =
            """# This file was created by Jacques Savoy and is distributed under the BSD license.
# See http://members.unine.ch/jacques.savoy/clef/index.html.
# Also see http://www.opensource.org/licenses/bsd-license.html
а
аз
ако
ала
бе
без
беше
би
бил
била
били
било
близо
бъдат
бъде
бяха
в
вас
ваш
ваша
вероятно
вече
взема
ви
вие
винаги
все
всеки
всички
всичко
всяка
във
въпреки
върху
г
ги
главно
го
д
да
дали
до
докато
докога
дори
досега
доста
е
едва
един
ето
за
зад
заедно
заради
засега
затова
защо
защото
и
из
или
им
има
имат
иска
й
каза
как
каква
какво
както
какъв
като
кога
когато
което
които
кой
който
колко
която
къде
където
към
ли
м
ме
между
мен
ми
мнозина
мога
могат
може
моля
момента
му
н
на
над
назад
най
направи
напред
например
нас
не
него
нея
ни
ние
никой
нито
но
някои
някой
няма
обаче
около
освен
особено
от
отгоре
отново
още
пак
по
повече
повечето
под
поне
поради
после
почти
прави
пред
преди
през
при
пък
първо
с
са
само
се
сега
си
скоро
след
сме
според
сред
срещу
сте
съм
със
също
т
тази
така
такива
такъв
там
твой
те
тези
ти
тн
то
това
тогава
този
той
толкова
точно
трябва
тук
тъй
тя
тях
у
харесва
ч
че
често
чрез
ще
щом
я"""
    }

    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the default stop words: {@link #DEFAULT_STOPWORD_FILE}. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /**
     * Builds an analyzer with the given stop words and a stem exclusion set. If a stem exclusion set
     * is provided this analyzer will add a [SetKeywordMarkerFilter] before [BulgarianStemFilter].
     */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    /**
     * Creates a [Analyzer.TokenStreamComponents] which tokenizes all the text in the provided
     * [org.gnit.lucenekmp.jdkport.Reader].
     *
     * @return a [Analyzer.TokenStreamComponents] built from a [StandardTokenizer] filtered with
     * [LowerCaseFilter], [StopFilter], [SetKeywordMarkerFilter] if a stem exclusion set is
     * provided and [BulgarianStemFilter].
     */
    override fun createComponents(fieldName: String): Analyzer.TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = BulgarianStemFilter(result)
        return Analyzer.TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }
}
