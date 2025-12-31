package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.ja.JapaneseTokenizer.Mode
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.UnicodeUtil
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TestExtendedMode : BaseTokenStreamTestCase() {
    private var analyzer: Analyzer? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            null,
                            true,
                            Mode.EXTENDED
                        )
                    return Analyzer.TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        analyzer!!.close()
        /*super.tearDown()*/
    }

    @Test
    /** simple test for supplementary characters  */
    @Throws(IOException::class)
    fun testSurrogates() {
        assertAnalyzesTo(
            analyzer!!,
            "𩬅艱鍟䇹愯瀛",
            arrayOf<String>("𩬅", "艱", "鍟", "䇹", "愯", "瀛")
        )
    }

    @Test
    /** random test ensuring we don't ever split supplementaries  */
    @Throws(IOException::class)
    fun testSurrogates2() {
        val numIterations: Int = atLeast(500)
        for (i in 0..<numIterations) {
            val s: String = TestUtil.randomUnicodeString(
                random(),
                100
            )
            analyzer!!.tokenStream("foo", s).use { ts ->
                val termAtt: CharTermAttribute =
                    ts.addAttribute<CharTermAttribute>(
                        CharTermAttribute::class
                    )
                ts.reset()
                while (ts.incrementToken()) {
                    assertTrue(
                        UnicodeUtil.validUTF16String(
                            termAtt
                        )
                    )
                }
                ts.end()
            }
        }
    }

    @Test
    /** blast some random strings through the analyzer  */
    @Throws(Exception::class)
    fun testRandomStrings() {
        val random: Random = random()
        checkRandomData(
            random,
            analyzer!!,
            100 * RANDOM_MULTIPLIER
        )
    }

    @Test
    /** blast some random large strings through the analyzer  */
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val random: Random = random()
        checkRandomData(
            random,
            analyzer!!,
            RANDOM_MULTIPLIER,
            4096
        )
    }

    @Test
    @Companion.Nightly
    @Throws(Exception::class)
    fun testRandomHugeStringsAtNight() {
        val random: Random = random()
        checkRandomData(
            random,
            analyzer!!,
            3 * RANDOM_MULTIPLIER,
            8192
        )
    }
}
