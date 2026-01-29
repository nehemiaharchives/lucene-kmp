package org.gnit.lucenekmp.analysis.de.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleGermanAnalyzer : BaseTokenStreamTestCase() {
	@Test
	@Throws(IOException::class)
	fun testDeclensionNormalization() {
		val a = BibleGermanAnalyzer()
		assertAnalyzesTo(
			a,
			"Jesu Christi",
			arrayOf("jesu", "jesus", "christi", "christus"),
			posIncrements = intArrayOf(1, 0, 1, 0)
		)
		assertAnalyzesTo(a, "Jesus Christus", arrayOf("jesus", "christus"))
		assertAnalyzesTo(
			a,
			"Jesum Christum",
			arrayOf("jesum", "jesus", "christum", "christus"),
			posIncrements = intArrayOf(1, 0, 1, 0)
		)
		assertAnalyzesTo(
			a,
			"Jesu Christo",
			arrayOf("jesu", "jesus", "christo", "christus"),
			posIncrements = intArrayOf(1, 0, 1, 0)
		)
		a.close()
	}

	@Test
	@Throws(IOException::class)
	fun testModernGenitive() {
		val a = BibleGermanAnalyzer()
		assertAnalyzesTo(a, "von Jesus Christus", arrayOf("jesus", "christus"))
		a.close()
	}

	@Test
	@Throws(Exception::class)
	fun testRandomStrings() {
		val a = BibleGermanAnalyzer()
		checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
		a.close()
	}
}
