package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.en.EnglishMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.en.EnglishPossessiveFilterFactory
import org.gnit.lucenekmp.analysis.en.KStemFilterFactory
import org.gnit.lucenekmp.analysis.en.PorterStemFilterFactory
import org.gnit.lucenekmp.analysis.de.GermanLightStemFilterFactory
import org.gnit.lucenekmp.analysis.de.GermanMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.de.GermanNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.de.GermanStemFilterFactory
import org.gnit.lucenekmp.analysis.es.SpanishLightStemFilterFactory
import org.gnit.lucenekmp.analysis.es.SpanishPluralStemFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseLightStemFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseStemFilterFactory
import org.gnit.lucenekmp.analysis.ru.RussianLightStemFilterFactory
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
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GermanLightStemFilterFactory.NAME,
            GermanLightStemFilterFactory::class
        ) { args -> GermanLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GermanMinimalStemFilterFactory.NAME,
            GermanMinimalStemFilterFactory::class
        ) { args -> GermanMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GermanStemFilterFactory.NAME,
            GermanStemFilterFactory::class
        ) { args -> GermanStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GermanNormalizationFilterFactory.NAME,
            GermanNormalizationFilterFactory::class
        ) { args -> GermanNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SpanishLightStemFilterFactory.NAME,
            SpanishLightStemFilterFactory::class
        ) { args -> SpanishLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SpanishPluralStemFilterFactory.NAME,
            SpanishPluralStemFilterFactory::class
        ) { args -> SpanishPluralStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PortugueseLightStemFilterFactory.NAME,
            PortugueseLightStemFilterFactory::class
        ) { args -> PortugueseLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PortugueseMinimalStemFilterFactory.NAME,
            PortugueseMinimalStemFilterFactory::class
        ) { args -> PortugueseMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PortugueseStemFilterFactory.NAME,
            PortugueseStemFilterFactory::class
        ) { args -> PortugueseStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            RussianLightStemFilterFactory.NAME,
            RussianLightStemFilterFactory::class
        ) { args -> RussianLightStemFilterFactory(args) }
        initialized = true
    }
}
