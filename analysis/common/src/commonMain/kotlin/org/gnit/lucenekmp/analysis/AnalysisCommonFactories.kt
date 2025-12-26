package org.gnit.lucenekmp.analysis

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
        initialized = true
    }
}
