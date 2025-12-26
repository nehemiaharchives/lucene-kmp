package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.en.EnglishMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.en.EnglishPossessiveFilterFactory
import org.gnit.lucenekmp.analysis.en.KStemFilterFactory
import org.gnit.lucenekmp.analysis.en.PorterStemFilterFactory
import org.gnit.lucenekmp.analysis.util.ElisionFilterFactory

/** Registers analysis/common factories for SPI lookups. */
object AnalysisCommonFactories {
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ElisionFilterFactory.NAME,
            ElisionFilterFactory::class
        ) { args -> ElisionFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            EnglishMinimalStemFilterFactory.NAME,
            EnglishMinimalStemFilterFactory::class
        ) { args -> EnglishMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            EnglishPossessiveFilterFactory.NAME,
            EnglishPossessiveFilterFactory::class
        ) { args -> EnglishPossessiveFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            KStemFilterFactory.NAME,
            KStemFilterFactory::class
        ) { args -> KStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PorterStemFilterFactory.NAME,
            PorterStemFilterFactory::class
        ) { args -> PorterStemFilterFactory(args) }
        initialized = true
    }
}
