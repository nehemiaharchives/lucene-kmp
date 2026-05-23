package org.gnit.lucenekmp.analysis.fa

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test the Persian Analyzer */
class TestPersianAnalyzer : BaseTokenStreamTestCase() {
	/** This test fails with NPE when the stopwords file is missing in classpath */
	@Test
	fun testResourcesAvailable() {
		PersianAnalyzer().close()
	}

	/**
	 * This test shows how the combination of tokenization (breaking on zero-width non-joiner),
	 * normalization (such as treating arabic YEH and farsi YEH the same), and stopwords creates a
	 * light-stemming effect for verbs.
	 *
	 * These verb forms are from http://en.wikipedia.org/wiki/Persian_grammar
	 */
	@Test
	@Throws(Exception::class)
	fun testBehaviorVerbs() {
		val a: Analyzer = PersianAnalyzer()
		// active present indicative
		assertAnalyzesTo(a, "می‌خورد", arrayOf("خورد"))
		// active preterite indicative
		assertAnalyzesTo(a, "خورد", arrayOf("خورد"))
		// active imperfective preterite indicative
		assertAnalyzesTo(a, "می‌خورد", arrayOf("خورد"))
		// active future indicative
		assertAnalyzesTo(a, "خواهد خورد", arrayOf("خورد"))
		// active present progressive indicative
		assertAnalyzesTo(a, "دارد می‌خورد", arrayOf("خورد"))
		// active preterite progressive indicative
		assertAnalyzesTo(a, "داشت می‌خورد", arrayOf("خورد"))

		// active perfect indicative
		assertAnalyzesTo(a, "خورده‌است", arrayOf("خورده"))
		// active imperfective perfect indicative
		assertAnalyzesTo(a, "می‌خورده‌است", arrayOf("خورده"))
		// active pluperfect indicative
		assertAnalyzesTo(a, "خورده بود", arrayOf("خورده"))
		// active imperfective pluperfect indicative
		assertAnalyzesTo(a, "می‌خورده بود", arrayOf("خورده"))
		// active preterite subjunctive
		assertAnalyzesTo(a, "خورده باشد", arrayOf("خورده"))
		// active imperfective preterite subjunctive
		assertAnalyzesTo(a, "می‌خورده باشد", arrayOf("خورده"))
		// active pluperfect subjunctive
		assertAnalyzesTo(a, "خورده بوده باشد", arrayOf("خورده"))
		// active imperfective pluperfect subjunctive
		assertAnalyzesTo(a, "می‌خورده بوده باشد", arrayOf("خورده"))
		// passive present indicative
		assertAnalyzesTo(a, "خورده می‌شود", arrayOf("خورده"))
		// passive preterite indicative
		assertAnalyzesTo(a, "خورده شد", arrayOf("خورده"))
		// passive imperfective preterite indicative
		assertAnalyzesTo(a, "خورده می‌شد", arrayOf("خورده"))
		// passive perfect indicative
		assertAnalyzesTo(a, "خورده شده‌است", arrayOf("خورده"))
		// passive imperfective perfect indicative
		assertAnalyzesTo(a, "خورده می‌شده‌است", arrayOf("خورده"))
		// passive pluperfect indicative
		assertAnalyzesTo(a, "خورده شده بود", arrayOf("خورده"))
		// passive imperfective pluperfect indicative
		assertAnalyzesTo(a, "خورده می‌شده بود", arrayOf("خورده"))
		// passive future indicative
		assertAnalyzesTo(a, "خورده خواهد شد", arrayOf("خورده"))
		// passive present progressive indicative
		assertAnalyzesTo(a, "دارد خورده می‌شود", arrayOf("خورده"))
		// passive preterite progressive indicative
		assertAnalyzesTo(a, "داشت خورده می‌شد", arrayOf("خورده"))
		// passive present subjunctive
		assertAnalyzesTo(a, "خورده شود", arrayOf("خورده"))
		// passive preterite subjunctive
		assertAnalyzesTo(a, "خورده شده باشد", arrayOf("خورده"))
		// passive imperfective preterite subjunctive
		assertAnalyzesTo(a, "خورده می‌شده باشد", arrayOf("خورده"))
		// passive pluperfect subjunctive
		assertAnalyzesTo(a, "خورده شده بوده باشد", arrayOf("خورده"))
		// passive imperfective pluperfect subjunctive
		assertAnalyzesTo(a, "خورده می‌شده بوده باشد", arrayOf("خورده"))

		// active present subjunctive
		assertAnalyzesTo(a, "بخورد", arrayOf("بخورد"))
		a.close()
	}

	/**
	 * This test shows how the combination of tokenization and stopwords creates a light-stemming
	 * effect for verbs.
	 *
	 * In this case, these forms are presented with alternative orthography, using arabic yeh and
	 * whitespace. This yeh phenomenon is common for legacy text due to some previous bugs in
	 * Microsoft Windows.
	 *
	 * These verb forms are from http://en.wikipedia.org/wiki/Persian_grammar
	 */
	@Test
	@Throws(Exception::class)
	fun testBehaviorVerbsDefective() {
		val a: Analyzer = PersianAnalyzer()
		// active present indicative
		assertAnalyzesTo(a, "مي خورد", arrayOf("خورد"))
		// active preterite indicative
		assertAnalyzesTo(a, "خورد", arrayOf("خورد"))
		// active imperfective preterite indicative
		assertAnalyzesTo(a, "مي خورد", arrayOf("خورد"))
		// active future indicative
		assertAnalyzesTo(a, "خواهد خورد", arrayOf("خورد"))
		// active present progressive indicative
		assertAnalyzesTo(a, "دارد مي خورد", arrayOf("خورد"))
		// active preterite progressive indicative
		assertAnalyzesTo(a, "داشت مي خورد", arrayOf("خورد"))

		// active perfect indicative
		assertAnalyzesTo(a, "خورده است", arrayOf("خورده"))
		// active imperfective perfect indicative
		assertAnalyzesTo(a, "مي خورده است", arrayOf("خورده"))
		// active pluperfect indicative
		assertAnalyzesTo(a, "خورده بود", arrayOf("خورده"))
		// active imperfective pluperfect indicative
		assertAnalyzesTo(a, "مي خورده بود", arrayOf("خورده"))
		// active preterite subjunctive
		assertAnalyzesTo(a, "خورده باشد", arrayOf("خورده"))
		// active imperfective preterite subjunctive
		assertAnalyzesTo(a, "مي خورده باشد", arrayOf("خورده"))
		// active pluperfect subjunctive
		assertAnalyzesTo(a, "خورده بوده باشد", arrayOf("خورده"))
		// active imperfective pluperfect subjunctive
		assertAnalyzesTo(a, "مي خورده بوده باشد", arrayOf("خورده"))
		// passive present indicative
		assertAnalyzesTo(a, "خورده مي شود", arrayOf("خورده"))
		// passive preterite indicative
		assertAnalyzesTo(a, "خورده شد", arrayOf("خورده"))
		// passive imperfective preterite indicative
		assertAnalyzesTo(a, "خورده مي شد", arrayOf("خورده"))
		// passive perfect indicative
		assertAnalyzesTo(a, "خورده شده است", arrayOf("خورده"))
		// passive imperfective perfect indicative
		assertAnalyzesTo(a, "خورده مي شده است", arrayOf("خورده"))
		// passive pluperfect indicative
		assertAnalyzesTo(a, "خورده شده بود", arrayOf("خورده"))
		// passive imperfective pluperfect indicative
		assertAnalyzesTo(a, "خورده مي شده بود", arrayOf("خورده"))
		// passive future indicative
		assertAnalyzesTo(a, "خورده خواهد شد", arrayOf("خورده"))
		// passive present progressive indicative
		assertAnalyzesTo(a, "دارد خورده مي شود", arrayOf("خورده"))
		// passive preterite progressive indicative
		assertAnalyzesTo(a, "داشت خورده مي شد", arrayOf("خورده"))
		// passive present subjunctive
		assertAnalyzesTo(a, "خورده شود", arrayOf("خورده"))
		// passive preterite subjunctive
		assertAnalyzesTo(a, "خورده شده باشد", arrayOf("خورده"))
		// passive imperfective preterite subjunctive
		assertAnalyzesTo(a, "خورده مي شده باشد", arrayOf("خورده"))
		// passive pluperfect subjunctive
		assertAnalyzesTo(a, "خورده شده بوده باشد", arrayOf("خورده"))
		// passive imperfective pluperfect subjunctive
		assertAnalyzesTo(a, "خورده مي شده بوده باشد", arrayOf("خورده"))

		// active present subjunctive
		assertAnalyzesTo(a, "بخورد", arrayOf("بخورد"))
		a.close()
	}

	/**
	 * This test shows how the combination of tokenization (breaking on zero-width non-joiner or
	 * space) and stopwords creates a light-stemming effect for nouns, removing the plural -ha.
	 */
	@Test
	@Throws(Exception::class)
	fun testBehaviorNouns() {
		val a: Analyzer = PersianAnalyzer()
		assertAnalyzesTo(a, "برگ ها", arrayOf("برگ"))
		assertAnalyzesTo(a, "برگ‌ها", arrayOf("برگ"))
		a.close()
	}

	/**
	 * Test showing that non-persian text is treated very much like SimpleAnalyzer (lowercased, etc)
	 */
	@Test
	@Throws(Exception::class)
	fun testBehaviorNonPersian() {
		val a: Analyzer = PersianAnalyzer()
		assertAnalyzesTo(a, "English test.", arrayOf("english", "test"))
		a.close()
	}

	/** Basic test ensuring that tokenStream works correctly. */
	@Test
	@Throws(Exception::class)
	fun testReusableTokenStream() {
		val a: Analyzer = PersianAnalyzer()
		assertAnalyzesTo(a, "خورده مي شده بوده باشد", arrayOf("خورده"))
		assertAnalyzesTo(a, "برگ‌ها", arrayOf("برگ"))
		a.close()
	}

	/** Test that custom stopwords work, and are not case-sensitive. */
	@Test
	@Throws(Exception::class)
	fun testCustomStopwords() {
		val a = PersianAnalyzer(CharArraySet(mutableSetOf<Any>("the", "and", "a"), false))
		assertAnalyzesTo(a, "The quick brown fox.", arrayOf("quick", "brown", "fox"))
		a.close()
	}

	/** test we fold digits to latin-1 */
	@Test
	@Throws(Exception::class)
	fun testDigits() {
		val a = PersianAnalyzer()
		checkOneTerm(a, "۱۲۳۴", "1234")
		a.close()
	}

	/** blast some random strings through the analyzer */
	@Test
	@Throws(Exception::class)
	fun testRandomStrings() {
		val a = PersianAnalyzer()
		checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
		a.close()
	}
}

