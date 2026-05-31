package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.en.EnglishAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class TestStopAnalyzer : BaseTokenStreamTestCase() {
    private lateinit var stop: StopAnalyzer
    private val inValidTokens: MutableSet<Any> = mutableSetOf()

    @BeforeTest
    fun setUp() {
        for (entry in EnglishAnalyzer.ENGLISH_STOP_WORDS_SET) {
            inValidTokens.add(entry)
        }
        stop = StopAnalyzer(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET)
    }

    @AfterTest
    fun tearDown() {
        stop.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDefaults() {
        assertTrue(stop != null)
        stop.tokenStream("test", "This is a test of the english stop analyzer").use { stream: TokenStream ->
            assertNotNull(stream)
            val termAtt = stream.getAttribute(CharTermAttribute::class)!!
            stream.reset()

            while (stream.incrementToken()) {
                assertFalse(inValidTokens.contains(termAtt.toString()))
            }
            stream.end()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testStopList() {
        val stopWordsSet = CharArraySet(mutableListOf<Any>("good", "test", "analyzer"), false)
        val newStop = StopAnalyzer(stopWordsSet)
        newStop.tokenStream("test", "This is a good test of the english stop analyzer").use { stream: TokenStream ->
            assertNotNull(stream)
            val termAtt = stream.getAttribute(CharTermAttribute::class)!!

            stream.reset()
            while (stream.incrementToken()) {
                val text = termAtt.toString()
                assertFalse(stopWordsSet.contains(text))
            }
            stream.end()
        }
        newStop.close()
    }

    @Test
    @Throws(IOException::class)
    fun testStopListPositions() {
        val stopWordsSet = CharArraySet(mutableListOf<Any>("good", "test", "analyzer"), false)
        val newStop = StopAnalyzer(stopWordsSet)
        val s = "This is a good test of the english stop analyzer with positions"
        val expectedIncr = intArrayOf(1, 1, 1, 3, 1, 1, 1, 2, 1)
        newStop.tokenStream("test", s).use { stream: TokenStream ->
            assertNotNull(stream)
            var i = 0
            val termAtt = stream.getAttribute(CharTermAttribute::class)!!
            val posIncrAtt = stream.addAttribute(PositionIncrementAttribute::class)

            stream.reset()
            while (stream.incrementToken()) {
                val text = termAtt.toString()
                assertFalse(stopWordsSet.contains(text))
                assertEquals(expectedIncr[i++], posIncrAtt.getPositionIncrement())
            }
            stream.end()
        }
        newStop.close()
    }
}
