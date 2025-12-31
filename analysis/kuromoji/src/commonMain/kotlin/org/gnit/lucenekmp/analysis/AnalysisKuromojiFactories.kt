package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.ja.JapaneseBaseFormFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapaneseCompletionFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapaneseHiraganaUppercaseFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapaneseIterationMarkCharFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapaneseKatakanaStemFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapaneseKatakanaUppercaseFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapaneseNumberFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapanesePartOfSpeechStopFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapaneseReadingFormFilterFactory
import org.gnit.lucenekmp.analysis.ja.JapaneseTokenizerFactory

/** Registers analysis/kuromoji factories for SPI lookups. */
object AnalysisKuromojiFactories {
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return

        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            JapaneseTokenizerFactory.NAME,
            JapaneseTokenizerFactory::class
        ) { args -> JapaneseTokenizerFactory(args) }

        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            JapaneseIterationMarkCharFilterFactory.NAME,
            JapaneseIterationMarkCharFilterFactory::class
        ) { args -> JapaneseIterationMarkCharFilterFactory(args) }

        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JapaneseBaseFormFilterFactory.NAME,
            JapaneseBaseFormFilterFactory::class
        ) { args -> JapaneseBaseFormFilterFactory(args) }

        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JapanesePartOfSpeechStopFilterFactory.NAME,
            JapanesePartOfSpeechStopFilterFactory::class
        ) { args -> JapanesePartOfSpeechStopFilterFactory(args) }

        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JapaneseReadingFormFilterFactory.NAME,
            JapaneseReadingFormFilterFactory::class
        ) { args -> JapaneseReadingFormFilterFactory(args) }

        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JapaneseCompletionFilterFactory.NAME,
            JapaneseCompletionFilterFactory::class
        ) { args -> JapaneseCompletionFilterFactory(args) }

        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JapaneseKatakanaStemFilterFactory.NAME,
            JapaneseKatakanaStemFilterFactory::class
        ) { args -> JapaneseKatakanaStemFilterFactory(args) }

        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JapaneseHiraganaUppercaseFilterFactory.NAME,
            JapaneseHiraganaUppercaseFilterFactory::class
        ) { args -> JapaneseHiraganaUppercaseFilterFactory(args) }

        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JapaneseKatakanaUppercaseFilterFactory.NAME,
            JapaneseKatakanaUppercaseFilterFactory::class
        ) { args -> JapaneseKatakanaUppercaseFilterFactory(args) }

        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JapaneseNumberFilterFactory.NAME,
            JapaneseNumberFilterFactory::class
        ) { args -> JapaneseNumberFilterFactory(args) }

        initialized = true
    }
}
