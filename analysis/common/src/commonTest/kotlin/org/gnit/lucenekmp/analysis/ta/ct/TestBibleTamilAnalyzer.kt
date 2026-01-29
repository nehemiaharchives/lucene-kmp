package org.gnit.lucenekmp.analysis.ta.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleTamilAnalyzer : BaseTokenStreamTestCase() {
	@Test
	@Throws(IOException::class)
	fun testDeclensionNormalization() {
		val a = BibleTamilAnalyzer()
		assertAnalyzesTo(a, "இயேசுகிறிஸ்து", arrayOf("இயேசுகிறிஸ்து"))
		assertAnalyzesTo(
			a,
			"இயேசுகிறிஸ்துவைக்கொண்டு",
			arrayOf("இயேசுகிறிஸ்துவைக்கொண்டு", "இயேசுகிறிஸ்து"),
			posIncrements = intArrayOf(1, 0)
		)
		a.close()
	}

	@Test
	@Throws(Exception::class)
	fun testRandomStrings() {
		val a = BibleTamilAnalyzer()
		checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
		a.close()
	}
}