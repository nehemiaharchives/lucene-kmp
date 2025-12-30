package org.gnit.lucenekmp.analysis.ko

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestKoreanReadingFormFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KoreanTokenizer(newAttributeFactory(), null, KoreanTokenizer.DecompoundMode.DISCARD, false)
                return TokenStreamComponents(tokenizer, KoreanReadingFormFilter(tokenizer))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        IOUtils.close(analyzer)
    }

    @Test
    @Throws(IOException::class)
    fun testReadings() {
        assertAnalyzesTo(analyzer, "車丞相", arrayOf("차", "승상"))
    }

    @Test
    @Throws(IOException::class)
    fun testRandomData() {
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, KoreanReadingFormFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
