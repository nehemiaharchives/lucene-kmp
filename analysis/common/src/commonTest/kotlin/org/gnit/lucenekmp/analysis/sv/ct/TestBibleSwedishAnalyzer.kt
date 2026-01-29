package org.gnit.lucenekmp.analysis.sv.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleSwedishAnalyzer : BaseTokenStreamTestCase() {
	@Test
	@Throws(IOException::class)
	fun testDeclensionNormalization() {
		val a = BibleSwedishAnalyzer()
		assertAnalyzesTo(
			a,
			"Jesu Kristi",
			arrayOf("jesu", "jesus", "kristi", "kristus"),
			posIncrements = intArrayOf(1, 0, 1, 0)
		)
		assertAnalyzesTo(a, "Jesus Kristus", arrayOf("jesus", "kristus"))
		a.close()
	}

	@Test
	@Throws(IOException::class)
	fun testModernGenitive() {
		val a = BibleSwedishAnalyzer()
		assertAnalyzesTo(a, "av Jesus Kristus", arrayOf("jesus", "kristus"))
		a.close()
	}

	@Test
	@Throws(Exception::class)
	fun testRandomStrings() {
		val a = BibleSwedishAnalyzer()
		checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
		a.close()
	}
}
