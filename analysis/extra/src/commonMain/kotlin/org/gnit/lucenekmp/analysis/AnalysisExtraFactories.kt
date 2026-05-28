package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.be.BelarusianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.be.BelarusianStemFilterFactory
import org.gnit.lucenekmp.analysis.gu.GujaratiNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.gu.GujaratiStemFilterFactory
import org.gnit.lucenekmp.analysis.ceb.CebuanoNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ceb.CebuanoStemFilterFactory
import org.gnit.lucenekmp.analysis.ha.HausaNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ha.HausaStemFilterFactory
import org.gnit.lucenekmp.analysis.ht.HaitianCreoleNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ht.HaitianCreoleStemFilterFactory
import org.gnit.lucenekmp.analysis.ig.IgboNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ig.IgboStemFilterFactory
import org.gnit.lucenekmp.analysis.ilo.IlocanoNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ilo.IlocanoStemFilterFactory
import org.gnit.lucenekmp.analysis.jv.JavaneseNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.jv.JavaneseStemFilterFactory
import org.gnit.lucenekmp.analysis.mr.MarathiNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.mr.MarathiStemFilterFactory
import org.gnit.lucenekmp.analysis.ms.MalayNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ms.MalayStemFilterFactory
import org.gnit.lucenekmp.analysis.or.OdiaNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.or.OdiaStemFilterFactory
import org.gnit.lucenekmp.analysis.pa.PunjabiNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.pa.PunjabiStemFilterFactory
import org.gnit.lucenekmp.analysis.su.SundaneseNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.su.SundaneseStemFilterFactory
import org.gnit.lucenekmp.analysis.sw.SwahiliNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.sw.SwahiliStemFilterFactory
import org.gnit.lucenekmp.analysis.tl.TagalogNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.tl.TagalogStemFilterFactory
import org.gnit.lucenekmp.analysis.ti.TigrinyaNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ti.TigrinyaStemFilterFactory
import org.gnit.lucenekmp.analysis.ur.UrduNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ur.UrduStemFilterFactory
import org.gnit.lucenekmp.analysis.vi.VietnameseNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.vi.VietnameseStemFilterFactory
import org.gnit.lucenekmp.analysis.yo.YorubaNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.yo.YorubaStemFilterFactory

/** Registers analysis/extra factories for SPI lookups. */
object AnalysisExtraFactories {
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            MarathiNormalizationFilterFactory.NAME,
            MarathiNormalizationFilterFactory::class
        ) { args -> MarathiNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            MarathiStemFilterFactory.NAME,
            MarathiStemFilterFactory::class
        ) { args -> MarathiStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GujaratiNormalizationFilterFactory.NAME,
            GujaratiNormalizationFilterFactory::class
        ) { args -> GujaratiNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GujaratiStemFilterFactory.NAME,
            GujaratiStemFilterFactory::class
        ) { args -> GujaratiStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CebuanoNormalizationFilterFactory.NAME,
            CebuanoNormalizationFilterFactory::class
        ) { args -> CebuanoNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CebuanoStemFilterFactory.NAME,
            CebuanoStemFilterFactory::class
        ) { args -> CebuanoStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BelarusianNormalizationFilterFactory.NAME,
            BelarusianNormalizationFilterFactory::class
        ) { args -> BelarusianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BelarusianStemFilterFactory.NAME,
            BelarusianStemFilterFactory::class
        ) { args -> BelarusianStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HausaNormalizationFilterFactory.NAME,
            HausaNormalizationFilterFactory::class
        ) { args -> HausaNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HausaStemFilterFactory.NAME,
            HausaStemFilterFactory::class
        ) { args -> HausaStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HaitianCreoleNormalizationFilterFactory.NAME,
            HaitianCreoleNormalizationFilterFactory::class
        ) { args -> HaitianCreoleNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HaitianCreoleStemFilterFactory.NAME,
            HaitianCreoleStemFilterFactory::class
        ) { args -> HaitianCreoleStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            IgboNormalizationFilterFactory.NAME,
            IgboNormalizationFilterFactory::class
        ) { args -> IgboNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            IgboStemFilterFactory.NAME,
            IgboStemFilterFactory::class
        ) { args -> IgboStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            IlocanoNormalizationFilterFactory.NAME,
            IlocanoNormalizationFilterFactory::class
        ) { args -> IlocanoNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            IlocanoStemFilterFactory.NAME,
            IlocanoStemFilterFactory::class
        ) { args -> IlocanoStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JavaneseNormalizationFilterFactory.NAME,
            JavaneseNormalizationFilterFactory::class
        ) { args -> JavaneseNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            JavaneseStemFilterFactory.NAME,
            JavaneseStemFilterFactory::class
        ) { args -> JavaneseStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            MalayNormalizationFilterFactory.NAME,
            MalayNormalizationFilterFactory::class
        ) { args -> MalayNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            MalayStemFilterFactory.NAME,
            MalayStemFilterFactory::class
        ) { args -> MalayStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            OdiaNormalizationFilterFactory.NAME,
            OdiaNormalizationFilterFactory::class
        ) { args -> OdiaNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            OdiaStemFilterFactory.NAME,
            OdiaStemFilterFactory::class
        ) { args -> OdiaStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PunjabiNormalizationFilterFactory.NAME,
            PunjabiNormalizationFilterFactory::class
        ) { args -> PunjabiNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PunjabiStemFilterFactory.NAME,
            PunjabiStemFilterFactory::class
        ) { args -> PunjabiStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SundaneseNormalizationFilterFactory.NAME,
            SundaneseNormalizationFilterFactory::class
        ) { args -> SundaneseNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SundaneseStemFilterFactory.NAME,
            SundaneseStemFilterFactory::class
        ) { args -> SundaneseStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SwahiliNormalizationFilterFactory.NAME,
            SwahiliNormalizationFilterFactory::class
        ) { args -> SwahiliNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SwahiliStemFilterFactory.NAME,
            SwahiliStemFilterFactory::class
        ) { args -> SwahiliStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            UrduNormalizationFilterFactory.NAME,
            UrduNormalizationFilterFactory::class
        ) { args -> UrduNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            UrduStemFilterFactory.NAME,
            UrduStemFilterFactory::class
        ) { args -> UrduStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TagalogNormalizationFilterFactory.NAME,
            TagalogNormalizationFilterFactory::class
        ) { args -> TagalogNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TagalogStemFilterFactory.NAME,
            TagalogStemFilterFactory::class
        ) { args -> TagalogStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TigrinyaNormalizationFilterFactory.NAME,
            TigrinyaNormalizationFilterFactory::class
        ) { args -> TigrinyaNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TigrinyaStemFilterFactory.NAME,
            TigrinyaStemFilterFactory::class
        ) { args -> TigrinyaStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            VietnameseNormalizationFilterFactory.NAME,
            VietnameseNormalizationFilterFactory::class
        ) { args -> VietnameseNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            VietnameseStemFilterFactory.NAME,
            VietnameseStemFilterFactory::class
        ) { args -> VietnameseStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            YorubaNormalizationFilterFactory.NAME,
            YorubaNormalizationFilterFactory::class
        ) { args -> YorubaNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            YorubaStemFilterFactory.NAME,
            YorubaStemFilterFactory::class
        ) { args -> YorubaStemFilterFactory(args) }
        initialized = true
    }
}
