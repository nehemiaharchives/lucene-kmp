package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.gu.GujaratiNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.gu.GujaratiStemFilterFactory
import org.gnit.lucenekmp.analysis.mr.MarathiNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.mr.MarathiStemFilterFactory
import org.gnit.lucenekmp.analysis.tl.TagalogNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.tl.TagalogStemFilterFactory
import org.gnit.lucenekmp.analysis.ur.UrduNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ur.UrduStemFilterFactory
import org.gnit.lucenekmp.analysis.vi.VietnameseNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.vi.VietnameseStemFilterFactory

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
            VietnameseNormalizationFilterFactory.NAME,
            VietnameseNormalizationFilterFactory::class
        ) { args -> VietnameseNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            VietnameseStemFilterFactory.NAME,
            VietnameseStemFilterFactory::class
        ) { args -> VietnameseStemFilterFactory(args) }
        initialized = true
    }
}
