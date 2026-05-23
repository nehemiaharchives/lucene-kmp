package org.gnit.lucenekmp.analysis.fa

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestPersianCharFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents(MockTokenizer())
            }

            override fun initReader(fieldName: String, reader: Reader): Reader {
                return PersianCharFilter(reader)
            }
        }
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        assertAnalyzesTo(analyzer, "this is a\u200Ctest", arrayOf("this", "is", "a", "test"))
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
    }
}

