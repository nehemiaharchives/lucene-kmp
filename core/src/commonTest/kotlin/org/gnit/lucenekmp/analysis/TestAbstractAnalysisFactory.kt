package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.standard.StandardTokenizerFactory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAbstractAnalysisFactory : LuceneTestCase() {

    @Test
    fun testLookupTokenizerSPIName() {
        assertEquals("standard", AnalysisSPILoader.lookupSPIName(StandardTokenizerFactory::class))
        assertEquals("standard", TokenizerFactory.findSPIName(StandardTokenizerFactory::class))
    }

    @Test
    fun testLookupCharFilterSPIName() {
        assertEquals("fake", AnalysisSPILoader.lookupSPIName(FakeCharFilterFactory::class))
        assertEquals("fake", CharFilterFactory.findSPIName(FakeCharFilterFactory::class))
    }

    @Test
    fun testLookupTokenFilterSPIName() {
        assertEquals("fake", AnalysisSPILoader.lookupSPIName(FakeTokenFilterFactory::class))
        assertEquals("fake", TokenFilterFactory.findSPIName(FakeTokenFilterFactory::class))
    }
}
