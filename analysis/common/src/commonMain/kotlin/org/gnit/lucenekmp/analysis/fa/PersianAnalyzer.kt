package org.gnit.lucenekmp.analysis.fa

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.ar.ArabicNormalizationFilter
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/**
 * [Analyzer] for Persian.
 *
 * This Analyzer uses [PersianCharFilter] which implies tokenizing around zero-width non-joiner in
 * addition to whitespace. Some persian-specific variant forms (such as farsi yeh and keheh) are
 * standardized. "Stemming" is accomplished via stopwords.
 */
class PersianAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the given stop word and stem exclusion set. */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE]. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        result = ArabicNormalizationFilter(result)
        /* additional persian-specific normalization */
        result = PersianNormalizationFilter(result)
        /*
         * the order here is important: the stopword list is normalized with the
         * above!
         */
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        return TokenStreamComponents(source, PersianStemFilter(result))
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = ArabicNormalizationFilter(result)
        /* additional persian-specific normalization */
        result = PersianNormalizationFilter(result)
        return result
    }

    /** Wraps the Reader with [PersianCharFilter] */
    override fun initReader(fieldName: String, reader: Reader): Reader {
        return PersianCharFilter(reader)
    }

    companion object {
        /** File containing default Persian stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        /** The comment character in the stopwords file. All lines prefixed with this will be ignored */
        const val STOPWORDS_COMMENT: String = "#"

        /** Returns an unmodifiable instance of the default stop-words set. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# This file was created by Jacques Savoy and is distributed under the BSD license.
# See http://members.unine.ch/jacques.savoy/clef/index.html.
# Also see http://www.opensource.org/licenses/bsd-license.html
# Note: by default this file is used after normalization, so when adding entries
# to this file, use the arabic 'ي' instead of 'ی'
انان
نداشته
سراسر
خياه
ايشان
وي
تاكنون
بيشتري
دوم
پس
ناشي
وگو
يا
داشتند
سپس
هنگام
هرگز
پنج
نشان
امسال
ديگر
گروهي
شدند
چطور
ده
و
دو
نخستين
ولي
چرا
چه
وسط
ه
كدام
قابل
يك
رفت
هفت
همچنين
در
هزار
بله
بلي
شايد
اما
شناسي
گرفته
دهد
داشته
دانست
داشتن
خواهيم
ميليارد
وقتيكه
امد
خواهد
جز
اورده
شده
بلكه
خدمات
شدن
برخي
نبود
بسياري
جلوگيري
حق
كردند
نوعي
بعري
نكرده
نظير
نبايد
بوده
بودن
داد
اورد
هست
جايي
شود
دنبال
داده
بايد
سابق
هيچ
همان
انجا
كمتر
كجاست
گردد
كسي
تر
مردم
تان
دادن
بودند
سري
جدا
ندارند
مگر
يكديگر
دارد
دهند
بنابراين
هنگامي
سمت
جا
انچه
خود
دادند
زياد
دارند
اثر
بدون
بهترين
بيشتر
البته
به
براساس
بيرون
كرد
بعضي
گرفت
توي
اي
ميليون
او
جريان
تول
بر
مانند
برابر
باشيم
مدتي
گويند
اكنون
تا
تنها
جديد
چند
بي
نشده
كردن
كردم
گويد
كرده
كنيم
نمي
نزد
روي
قصد
فقط
بالاي
ديگران
اين
ديروز
توسط
سوم
ايم
دانند
سوي
استفاده
شما
كنار
داريم
ساخته
طور
امده
رفته
نخست
بيست
نزديك
طي
كنيد
از
انها
تمامي
داشت
يكي
طريق
اش
چيست
روب
نمايد
گفت
چندين
چيزي
تواند
ام
ايا
با
ان
ايد
ترين
اينكه
ديگري
راه
هايي
بروز
همچنان
پاعين
كس
حدود
مختلف
مقابل
چيز
گيرد
ندارد
ضد
همچون
سازي
شان
مورد
باره
مرسي
خويش
برخوردار
چون
خارج
شش
هنوز
تحت
ضمن
هستيم
گفته
فكر
بسيار
پيش
براي
روزهاي
انكه
نخواهد
بالا
كل
وقتي
كي
چنين
كه
گيري
نيست
است
كجا
كند
نيز
يابد
بندي
حتي
توانند
عقب
خواست
كنند
بين
تمام
همه
ما
باشند
مثل
شد
اري
باشد
اره
طبق
بعد
اگر
صورت
غير
جاي
بيش
ريزي
اند
زيرا
چگونه
بار
لطفا
مي
درباره
من
ديده
همين
گذاري
برداري
علت
گذاشته
هم
فوق
نه
ها
شوند
اباد
همواره
هر
اول
خواهند
چهار
نام
امروز
مان
هاي
قبل
كنم
سعي
تازه
را
هستند
زير
جلوي
عنوان
بود
"""

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), STOPWORDS_COMMENT)
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}

