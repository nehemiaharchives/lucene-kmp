package org.gnit.lucenekmp.analysis.km

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/**
 * Analyzer for Khmer text.
 *
 * Tokenizes text into grapheme clusters using [GraphemeClusterTokenizer], optionally applies
 * character-level normalization via [KhmerNormalizationCharFilter] before tokenization,
 * and then reorders characters within each token using [CharReorderFilter].
 *
 * @param normalizationlevel normalization level: 0 = none, 1 = formally confusable (default),
 *                            2 = also informally confusable, 3 = also digit mapping and more
 */
class KhmerAnalyzer : StopwordAnalyzerBase {
    private val normalizationlevel: Int
    private val enableStopwords: Boolean
    private val khmerNumber: Boolean

    @Throws(IOException::class)
    constructor() : this(1, false, false)

    @Throws(IOException::class)
    constructor(normalizationlevel: Int) : this(normalizationlevel, false, false)

    @Throws(IOException::class)
    constructor(normalizationlevel: Int, enableStopwords: Boolean, khmerNumber: Boolean) :
        this(normalizationlevel, enableStopwords, khmerNumber, getDefaultStopSet())

    constructor(
        normalizationlevel: Int,
        enableStopwords: Boolean,
        khmerNumber: Boolean,
        stopwords: CharArraySet
    ) : super(stopwords) {
        this.normalizationlevel = normalizationlevel
        this.enableStopwords = enableStopwords
        this.khmerNumber = khmerNumber
    }

    override fun initReader(fieldName: String, reader: Reader): Reader {
        if (normalizationlevel > 0)
            return KhmerNormalizationCharFilter(reader, normalizationlevel)
        return super.initReader(fieldName, reader)
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = GraphemeClusterTokenizer()
        var result: TokenStream = CharReorderFilter(source)
        if (enableStopwords) {
            result = StopFilter(result, stopwords)
        }
        if (khmerNumber) {
            result = KhmerNumberFilter(result)
        }
        return TokenStreamComponents(source, result)
    }

    companion object {
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT: String = "#"

        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET =
                        WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), STOPWORDS_COMMENT)
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Khmer stopwords
បន្ថែម
ខណៈនោះ
ខាងក្រោម
គ្រប់គ្រាន់
ដោយសារតែ
នៅពេលនោះ
ប្រទះឃើញ
ម្យ៉ាងទៀត
យ៉ាងខាប់
លើក
ប្រាំ
វា
កម្រិត
កាន់
ការ
ការបើ
ការផ្លាស់ប្ដូរ
ការរៀបចំ
កាលណា
កាលនោះ
ក្តី
ក្នុង
ក្នុងនេះ
ក្រាស់
ក្រោម
ក្រោយ
ក្រោយមក
ក្លាយ
ក្លាយជា
កំពុង
កំពូល
កំឡុងពេល
ក៏
ក៏ដោយ
ក៏បាន
ខាង
ខាងក្នុង
ខាងក្រោយ
ខាងក្រៅ
ខាងមុខ
ខាងលើ
ខុស
ខ្ងុំ
ខ្លួន
ខ្លួនឯង
ខ្លះ
គាត់
គាំទ្រ
គឺ
គឺជា
គួរតែ
គួរសម
គេ
គ្នា
គ្មាន
គ្មានមួយ
គ្រប់
គ្រា
គ្រាមួយ
ចង់
ចន្លោះ
ចាកពី
ចុង
ចុងក្រោយ
ចេញ
ចេញពី
ចៃដន្យ
ច្រើន
ច្រើនជាងគេ
ចំណែក
ចំណោម
ចំនួន
ចំនួនច្រើន
ចំពោះ
ចំហៀង
ឆ្ងាយ
ឆ្ងាយជាង
ឆ្ពោះទៅ
ជាដរាប
ជានិច្ចកាល
ជាមុន
ជាមួយគ្នា
ជាស្រេច
ជិត
ជុំវិញ
ជួនកាល
ជួយ
ជំរាល
ឈម
ញឹក
ញឹកញាប់
ញែក
ដកចេញ
ដង
ដដែល
ដល់
ដល់ម្ល៉េះ
ដូចគ្នា
ដូចជា
ដូចនេះ
ដូចនេះហើយ
ដូចនោះ
ដូចម្ដេច
ដូច្នេះ
ដូច្នេះហើយ
ដូច្នោះទេ
ដើម្បី
ដើម្បីនឹង
ដែរ
ដែល
ដែលក្រោយបំផុត
ដែលក្លាយ
ដែលជា
ដែលជួយ
ដែលនឹង
ដែលអាច
ដោយ
ដោយខ្លួនឯង
ដោយទីពីរ
ដោយភាគច្រើន
ដោយមិនដឹងជាយ៉ាងម៉េច
ដោយមិនដឹងជារឿងអ្វី
ដោយមិនដឹងម៉េចទេ
ដោយសារតែ
ដោយហេតុថា
ដោយហេតុនោះ
ដំបូង
ដ៏ទៃ
ណា
ណាមួយ
ណាស់
តាម
តាមចន្លោះ
តាំង
តាំងពី
តាំងពីនោះ
តើ
តែ
តែមួយ
តោងតែ
ត្រង់នោះហើយ
ត្រឹម
ត្រឹមតែ
ត្រូវ
ត្រូវបាន
ថា
ថែមទៀត
ថ្មី
ថ្វីបើ
ទទឹង
ទទួល
ទទេ
ទល់នឹង
ទាន់
ទាប
ទាល់តែ
ទាស់
ទាំង
ទាំងនេះ
ទាំងពីរ
ទាំងមូល
ទាំងឡាយ
ទាំងអស់
ទី
ទីកន្លែង
ទីណា
ទីនេះ
ទីនោះ
ទីពីរ
ទុក
ទុកបាន
ទូទាំង
ទៀត
ទេ
ទោះបី
ទោះបីជា
ទៅ
ទៅដល់
ទៅផុត
ទៅលើ
ទំនង
ធម្មតា
ធ្លាក់ចុះ
ធ្វើ
ធ្វើបាន
ធ្វើអោយបានចំរើន
នរណា
នាង
នាយ
និង
និមួយ
និយម
នីមួយ
នឹង
នូវ
នេះ
នេះទៅទៀត
នៃ
នោះ
នោះទេ
នោះមក
នោះឯង
នៅ
នៅក្នុង
នៅគ្រា
នៅជិតៗ
នៅតែ
នៅទី
នៅពេល
នៅមុខ
នៅម្ដុំនេះ
នៅលើ
ន័យនេះ
បង្អស់
បន្ដិច
បន្ថែម
បន្ទាប់
បន្ទាប់ពី
បន្ទាប់ពីនេះ
ប៉ុនគ្នា
ប៉ុន្ដែ
ប៉ុន្មាន
បានជា
បានដែរ
បី
បីនេះ
បួន
បើ
បើមិនមែន
បែបនេះ
ប្រឈម
ប្រមាណ
ប្រហែល
ប្រាំបី
ប្រាំបួន
ប្រាំមួយ
បំផុត
បំពេញ
ផង
ផុត
ផ្គាប់
ផ្ដល់នូវ
ផ្ទុយនឹង
ផ្ទុយពី
ផ្សេងទៀត
ពី
ពីនេះតទៅ
ពីនេះពីនោះ
ពីព្រោះ
ពីមុន
ពីរ
ពីលើ
ពុំ
ពួក
ពួកគេ
ពេញ
ពេញទាំង
ពេល
ពេលដែល
ពេលនោះ
ពោលគឺ
ព្រោះ
ព្រោះតែ
ភាព
មក
មកកាន់
មកពី
ម៉េច
មាន
មិនដែល
មិនត្រូវ
មិនទាន់
មិនទៀង
មិនព្រម
មិនមែន
មិនអាច
មូល
មូលហេតុ
មួយ
មួយចំនួន
មួយណា
មួយទៀត
មែន
មែនទែន
ម្ដង
ម្នាក់
ម្នាក់ៗ
ម្ភៃ
ម្យ៉ាងទៀត
ម្ល៉េះ
យក
យកចេញ
យល់ស្រប
យ៉ាង
យ៉ាងច្រើន
យ៉ាងណា
យ៉ាងណា
ក៏ដោយ
យ៉ាងណាក្តី
យ៉ាងនេះ
យ៉ាងនោះ
យើង
ឬ
រក្សា
រញៀវ
ឬទេ
របស់
របស់ខ្ញុំ
របស់គាត់
របស់គាត់
របស់គេ
របស់នាង
របស់លោក
របស់វា
រយះពេល
រយៈ
រយៈនេះ
រវាង
រហូតដល់
រាល់
រឺ
រឺក៏
រួចហើយ
រួម
រួមទាំង
លើ
លើក
លើកលែង
លើស
លេខមួយ
លែង
លោក
ល្អ
លំអិត
វា
វិញ
វែង
សព្វ
សម្រាប់
សរុប
សូម្បីតែ
សេចក្ដី
សោះ
ស្ងៀម
ស្ទើរ
ស្មើរគ្នា
ស្មោះ
ស្វែងរក
សំខាន់
សំរាប់
សំរេច
ហាម
ហាសិប
ហុកសិប
ហើយ
ហើយនឹង
ហេតុផល
ហេតុអ្វី
ហៅ
ឡើង
ឡើយ
ឯ
ឯការ
ឯកោ
អង្កាល់
អញ្ចឹង
ឯណា
ឥត
ឥតទៅណា
អតីត
ឯទៀត
អស់
ឥឡូវនេះ
អ៊ីចឹង
អាច
អី
អោយ
អ្នក
អ្នកក្រោយ
អ្នកណា
ឱ្យ
អ្វី
អ្វីខ្លះ
អ្វីមួយ
អំពី
។ល។
ជា
បាន
មិន
ក្នុង
នៅ
ដ៏
ទ្រង់
ម្នាល
ឲ្យ
ខ្ញុំ
បុគ្គល
ព្រះអង្គ
ធម៌
ឯង
ព្រះ
គួរ
លុះ
ប្រកបដោយ
ទើប
ទាំងនោះ
ត
សូម
ជាង
"""
    }
}
