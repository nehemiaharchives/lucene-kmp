package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.ar.ArabicNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ar.ArabicStemFilterFactory
import org.gnit.lucenekmp.analysis.bn.BengaliNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.bn.BengaliStemFilterFactory
import org.gnit.lucenekmp.analysis.br.BrazilianStemFilterFactory
import org.gnit.lucenekmp.analysis.ckb.SoraniNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ckb.SoraniStemFilterFactory
import org.gnit.lucenekmp.analysis.charfilter.HTMLStripCharFilterFactory
import org.gnit.lucenekmp.analysis.charfilter.MappingCharFilterFactory
import org.gnit.lucenekmp.analysis.cz.CzechStemFilterFactory
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
import org.gnit.lucenekmp.analysis.el.GreekLowerCaseFilterFactory
import org.gnit.lucenekmp.analysis.el.GreekStemFilterFactory
import org.gnit.lucenekmp.analysis.fa.PersianCharFilterFactory
import org.gnit.lucenekmp.analysis.fa.PersianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.fa.PersianStemFilterFactory
import org.gnit.lucenekmp.analysis.hi.HindiNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.hi.HindiStemFilterFactory
import org.gnit.lucenekmp.analysis.hu.HungarianLightStemFilterFactory
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.id.IndonesianStemFilterFactory
import org.gnit.lucenekmp.analysis.it.ItalianLightStemFilterFactory
import org.gnit.lucenekmp.analysis.no.NorwegianLightStemFilterFactory
import org.gnit.lucenekmp.analysis.no.NorwegianMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.no.NorwegianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseLightStemFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseStemFilterFactory
import org.gnit.lucenekmp.analysis.ro.RomanianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ru.RussianLightStemFilterFactory
import org.gnit.lucenekmp.analysis.sr.SerbianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.sv.SwedishLightStemFilterFactory
import org.gnit.lucenekmp.analysis.sv.SwedishMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.te.TeluguNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.te.TeluguStemFilterFactory
import org.gnit.lucenekmp.analysis.th.ThaiTokenizerFactory
import org.gnit.lucenekmp.analysis.util.ElisionFilterFactory

/** Registers analysis/common factories for SPI lookups. */
object AnalysisCommonFactories {
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            HTMLStripCharFilterFactory.NAME,
            HTMLStripCharFilterFactory::class
        ) { args -> HTMLStripCharFilterFactory(args) }
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            MappingCharFilterFactory.NAME,
            MappingCharFilterFactory::class
        ) { args -> MappingCharFilterFactory(args) }
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            PersianCharFilterFactory.NAME,
            PersianCharFilterFactory::class
        ) { args -> PersianCharFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ArabicNormalizationFilterFactory.NAME,
            ArabicNormalizationFilterFactory::class
        ) { args -> ArabicNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ArabicStemFilterFactory.NAME,
            ArabicStemFilterFactory::class
        ) { args -> ArabicStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PersianNormalizationFilterFactory.NAME,
            PersianNormalizationFilterFactory::class
        ) { args -> PersianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PersianStemFilterFactory.NAME,
            PersianStemFilterFactory::class
        ) { args -> PersianStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BengaliNormalizationFilterFactory.NAME,
            BengaliNormalizationFilterFactory::class
        ) { args -> BengaliNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BengaliStemFilterFactory.NAME,
            BengaliStemFilterFactory::class
        ) { args -> BengaliStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BrazilianStemFilterFactory.NAME,
            BrazilianStemFilterFactory::class
        ) { args -> BrazilianStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SoraniNormalizationFilterFactory.NAME,
            SoraniNormalizationFilterFactory::class
        ) { args -> SoraniNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SoraniStemFilterFactory.NAME,
            SoraniStemFilterFactory::class
        ) { args -> SoraniStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CzechStemFilterFactory.NAME,
            CzechStemFilterFactory::class
        ) { args -> CzechStemFilterFactory(args) }
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
            GreekLowerCaseFilterFactory.NAME,
            GreekLowerCaseFilterFactory::class
        ) { args -> GreekLowerCaseFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GreekStemFilterFactory.NAME,
            GreekStemFilterFactory::class
        ) { args -> GreekStemFilterFactory(args) }
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
            IndicNormalizationFilterFactory.NAME,
            IndicNormalizationFilterFactory::class
        ) { args -> IndicNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HindiNormalizationFilterFactory.NAME,
            HindiNormalizationFilterFactory::class
        ) { args -> HindiNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HindiStemFilterFactory.NAME,
            HindiStemFilterFactory::class
        ) { args -> HindiStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HungarianLightStemFilterFactory.NAME,
            HungarianLightStemFilterFactory::class
        ) { args -> HungarianLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            IndonesianStemFilterFactory.NAME,
            IndonesianStemFilterFactory::class
        ) { args -> IndonesianStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ItalianLightStemFilterFactory.NAME,
            ItalianLightStemFilterFactory::class
        ) { args -> ItalianLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            NorwegianLightStemFilterFactory.NAME,
            NorwegianLightStemFilterFactory::class
        ) { args -> NorwegianLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            NorwegianMinimalStemFilterFactory.NAME,
            NorwegianMinimalStemFilterFactory::class
        ) { args -> NorwegianMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            NorwegianNormalizationFilterFactory.NAME,
            NorwegianNormalizationFilterFactory::class
        ) { args -> NorwegianNormalizationFilterFactory(args) }
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
            RomanianNormalizationFilterFactory.NAME,
            RomanianNormalizationFilterFactory::class
        ) { args -> RomanianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            RussianLightStemFilterFactory.NAME,
            RussianLightStemFilterFactory::class
        ) { args -> RussianLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SerbianNormalizationFilterFactory.NAME,
            SerbianNormalizationFilterFactory::class
        ) { args -> SerbianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SwedishLightStemFilterFactory.NAME,
            SwedishLightStemFilterFactory::class
        ) { args -> SwedishLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SwedishMinimalStemFilterFactory.NAME,
            SwedishMinimalStemFilterFactory::class
        ) { args -> SwedishMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TeluguNormalizationFilterFactory.NAME,
            TeluguNormalizationFilterFactory::class
        ) { args -> TeluguNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TeluguStemFilterFactory.NAME,
            TeluguStemFilterFactory::class
        ) { args -> TeluguStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            ThaiTokenizerFactory.NAME,
            ThaiTokenizerFactory::class
        ) { args -> ThaiTokenizerFactory(args) }
        initialized = true
    }
}
