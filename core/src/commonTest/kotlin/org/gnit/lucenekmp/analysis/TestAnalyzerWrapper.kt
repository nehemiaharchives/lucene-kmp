package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TestAnalyzerWrapper : LuceneTestCase() {

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testSourceDelegation() {
        val sourceCalled = AtomicBoolean(false)

        val analyzer: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents(
                    { sourceCalled.store(true) },
                    CannedTokenStream()
                )
            }
        }

        val wrapper: Analyzer = object : AnalyzerWrapper(analyzer.reuseStrategy) {
            override fun getWrappedAnalyzer(fieldName: String): Analyzer {
                return analyzer
            }

            override fun wrapComponents(
                fieldName: String,
                components: TokenStreamComponents
            ): TokenStreamComponents {
                return TokenStreamComponents(
                    components.getSource(),
                    LowerCaseFilter(components.tokenStream)
                )
            }
        }

        wrapper.tokenStream("", "text").use { ts ->
            assertNotNull(ts)
            assertTrue(sourceCalled.load())
        }
    }

    /**
     * Test that [AnalyzerWrapper.UnwrappingReuseStrategy] consults the wrapped analyzer's reuse
     * strategy if components can be reused or need to be updated.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testUnwrappingReuseStrategy() {
        val reuse = AtomicBoolean(true)

        val wrappedAnalyzerStrategy: Analyzer.ReuseStrategy = object : Analyzer.ReuseStrategy() {
            override fun getReusableComponents(analyzer: Analyzer, fieldName: String): Analyzer.TokenStreamComponents? {
                return if (!reuse.load()) {
                    null
                } else {
                    getStoredValue(analyzer) as Analyzer.TokenStreamComponents?
                }
            }

            override fun setReusableComponents(
                analyzer: Analyzer,
                fieldName: String,
                components: Analyzer.TokenStreamComponents
            ) {
                setStoredValue(analyzer, components)
            }
        }

        val wrappedAnalyzer: Analyzer = object : Analyzer(wrappedAnalyzerStrategy) {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents({ }, CannedTokenStream())
            }
        }

        val wrapperAnalyzer: AnalyzerWrapper = object : AnalyzerWrapper(Analyzer.PER_FIELD_REUSE_STRATEGY) {
            override fun getWrappedAnalyzer(fieldName: String): Analyzer {
                return wrappedAnalyzer
            }

            override fun wrapComponents(
                fieldName: String,
                components: TokenStreamComponents
            ): TokenStreamComponents {
                return TokenStreamComponents(
                    components.getSource(),
                    LowerCaseFilter(components.tokenStream)
                )
            }
        }

        val ts = wrapperAnalyzer.tokenStream("", "text")
        val ts2 = wrapperAnalyzer.tokenStream("", "text")
        assertEquals(ts2, ts)

        reuse.store(false)
        val ts3 = wrapperAnalyzer.tokenStream("", "text")
        assertNotSame(ts3, ts2)
        val ts4 = wrapperAnalyzer.tokenStream("", "text")
        assertNotSame(ts4, ts3)

        reuse.store(true)
        val ts5 = wrapperAnalyzer.tokenStream("", "text")
        assertEquals(ts5, ts4)
        val ts6 = wrapperAnalyzer.tokenStream("", "text")
        assertEquals(ts6, ts5)
    }
}
