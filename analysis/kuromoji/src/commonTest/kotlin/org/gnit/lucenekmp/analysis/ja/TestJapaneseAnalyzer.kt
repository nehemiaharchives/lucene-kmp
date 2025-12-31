package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.ja.JapaneseTokenizer.Mode
import org.gnit.lucenekmp.analysis.ja.dict.UserDictionary
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test Kuromoji Japanese morphological analyzer */
class TestJapaneseAnalyzer : BaseTokenStreamTestCase() {

	private fun readDict(): UserDictionary? {
		return try {
			UserDictionary.open(StringReader(USERDICT))
		} catch (ioe: IOException) {
			throw RuntimeException(ioe)
		}
	}

	/** This test fails with NPE when the stopwords file is missing in classpath */
	@Test
	fun testResourcesAvailable() {
		JapaneseAnalyzer().close()
	}

	/**
	 * An example sentence, test removal of particles, etc by POS, lemmatization with the basic form,
	 * and that position increments and offsets are correct.
	 */
	@Test
	@Throws(IOException::class)
	fun testBasics() {
		val a: Analyzer = JapaneseAnalyzer()
		assertAnalyzesTo(
			a,
			"多くの学生が試験に落ちた。",
			arrayOf("多く", "学生", "試験", "落ちる"),
			intArrayOf(0, 3, 6, 9),
			intArrayOf(2, 5, 8, 11),
			intArrayOf(1, 2, 2, 2)
		)
		a.close()
	}

	/** Test that search mode is enabled and working by default */
	@Test
	@Throws(IOException::class)
	fun testDecomposition() {
		var a: Analyzer =
			JapaneseAnalyzer(
				null,
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)

		// Senior software engineer:
		assertAnalyzesToPositions(
			a,
			"シニアソフトウェアエンジニア",
			arrayOf("シニア", "ソフトウェア", "エンジニア"),
			intArrayOf(1, 1, 1),
			intArrayOf(1, 1, 1)
		)

		// Senior project manager: also tests katakana spelling variation stemming
		assertAnalyzesToPositions(
			a,
			"シニアプロジェクトマネージャー",
			arrayOf("シニア", "プロジェクト", "マネージャ"),
			intArrayOf(1, 1, 1),
			intArrayOf(1, 1, 1)
		)

		// Kansai International Airport:
		assertAnalyzesToPositions(
			a,
			"関西国際空港",
			arrayOf("関西", "国際", "空港"),
			intArrayOf(1, 1, 1),
			intArrayOf(1, 1, 1)
		)

		// Konika Minolta Holdings; not quite the right
		// segmentation (see LUCENE-3726):
		assertAnalyzesToPositions(
			a,
			"コニカミノルタホールディングス",
			arrayOf("コニカ", "ミノルタ", "ホールディングス"),
			intArrayOf(1, 1, 1),
			intArrayOf(1, 1, 1)
		)

		// Narita Airport
		assertAnalyzesToPositions(
			a,
			"成田空港",
			arrayOf("成田", "空港"),
			intArrayOf(1, 1),
			intArrayOf(1, 1)
		)
		a.close()

		// Kyoto University Baseball Club
		a = JapaneseAnalyzer()
		assertAnalyzesToPositions(
			a,
			"京都大学硬式野球部",
			arrayOf("京都大", "学", "硬式", "野球", "部"),
			intArrayOf(1, 1, 1, 1, 1),
			intArrayOf(1, 1, 1, 1, 1)
		)
		a.close()
	}

	/** blast random strings against the analyzer */
	@Test
	@Throws(IOException::class)
	fun testRandom() {
		val random = random()
		val a: Analyzer =
			JapaneseAnalyzer(
				null,
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		checkRandomData(random, a, atLeast(100))
		a.close()
	}

	/** blast some random large strings through the analyzer */
	@Test
	@Throws(Exception::class)
	fun testRandomHugeStrings() {
		val random = random()
		val a: Analyzer =
			JapaneseAnalyzer(
				null,
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		checkRandomData(random, a, 2 * RANDOM_MULTIPLIER, 8192)
		a.close()
	}

	// Copied from TestJapaneseTokenizer, to make sure passing
	// user dict to analyzer works:
	@Test
	@Throws(Exception::class)
	fun testUserDict3() {
		// Test entry that breaks into multiple tokens:
		val a: Analyzer =
			JapaneseAnalyzer(
				readDict(),
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		assertTokenStreamContents(
			a.tokenStream("foo", "abcd"),
			arrayOf("a", "b", "cd"),
			intArrayOf(0, 1, 2),
			intArrayOf(1, 2, 4),
			4
		)
		a.close()
	}

	@Test
	@Throws(Exception::class)
	fun testCharWidthNormalization() {
		val a: Analyzer =
			JapaneseAnalyzer(
				readDict(),
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		assertTokenStreamContents(
			a.tokenStream("foo", "新橋６－２０－１"),
			arrayOf("新橋", "6", "20", "1"),
			intArrayOf(0, 2, 4, 7),
			intArrayOf(2, 3, 6, 8),
			8
		)
		a.close()
	}

	// LUCENE-3897: this string (found by running all jawiki
	// XML through JapaneseAnalyzer) caused AIOOBE
	@Test
	@Throws(Exception::class)
	fun testCuriousString() {
		val random = random()
		val s =
			"&lt;li&gt;06:26 2004年3月21日 [[利用者:Kzhr|Kzhr]] &quot;お菓子な家族&quot; を削除しました &lt;em&gt;&lt;nowiki&gt;(即時削除: 悪戯。内容: &amp;#39;ＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫＫKK&amp;#39;)&lt;/nowiki&gt;&lt;/em&gt;&lt;/li&gt;"
		val a: Analyzer =
			JapaneseAnalyzer(
				null,
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		checkAnalysisConsistency(random, a, random.nextBoolean(), s)
		a.close()
	}

	// LUCENE-3897: this string (found by
	// testHugeRandomStrings) tripped assert
	@Test
	@Throws(Exception::class)
	fun testAnotherCuriousString() {
		val random = random()
		val s =
			"《〔〘〝」〩〄〯』〴〷〦〯〹】〰。　〆。〡〢〲〆〤〫〱　〜々〲〿〄》〃】〚〗〪〓〨々〮〹〟〯〫』」〨〒〜〃〃〡　〸〜〱〆〿「〱〳。〷〆〃〷〇〛〥〒〖〪〕〦〚〉〷〼〣〒。〕〣〻〒〻〼〔〸〿〖〖〆々〭《〟〚〇〕〸〲〄〿〙」〞〖〪〬〤【〵〘〃々〦〮〠〦〛〲〝〿〽〓〺〷〛》〛『》〇　〽〄〱〙〥〠』〨〉〨〔」》〮〥〽〔〰〄〶】〠〶〨〔々『。〞〙〮》【　〯〦〯〩〩〈〿〫〘〒》』〾〰〰〼〒「〝〰〱〞〹〔〪〭、〬〴【』〧〩】〈。〧〤〢〨〶〄〴〡。〪〭〞〷〣〘〳〄〬〙『　「」【〮〯〔〱〬〴〵〭〬〚〱、〚〣、〚〓〮、〚々】〼〿〦〫〛〲〆〕々。〨〩〇〫〵『『〣〮〜〫〃】〡〯』〆〫〺〻〬〺、〗】〓〕〶〇〞〬。」〃〮〇〞〷〰〲】〆〻。〬〻〄〜〃〲〺〧〘〇〈、〃〚〇〉「〬〣〨〮〆〴〻〒〖〄〒〳〗〶、〙「　〫〚《〩〆〱〡【〶』【〆〫】〢》〔。〵〴〽々〱〖〳〶〱《〈〒』『〝〘【〈〢〝〠〣「〤〆〢〈〚〕〿〣々〢〹〉〡　〷《〤〴『々〉〤〬《』々〾〔〚〆〔〴〪〩〸〦』〉〃　《〼〇〆〾〛〿」〧〝〽〘〠〻【〰〨〥《〯〝〩〩〱〇〳々〚〉〔『〹〳〳』〲『〣」〯〓【々〮〥〃〿〳〞〦〦〶〓〬〛〬〈〈〠『〜〥〒〯〜〜〹〲【〓〪《々〗〚〇〜〄〦『々〃〒〇〖〢〉〹〮〩〽『》〵〔】〣〮】〧、〇〰〒】《〈〆々〾〣【〾〲〘〧『〇〲〼〕〙「〪〆〚々〦〯〵〇〤〆〡」〪》〼』〴〶〪】『〲〢〭〬〈〠〮〽〓〔〧〖」〃〴〬』〣〝〯〣〴『〉〖〄〇〄〰〇〃〤、〤》〔〴〯〫〠〝〷〞〩〛〛〳々〓〟〜〛〜〃　〃〛「、』》》々〢〱〢〸〹〙〃〶〇〮〼」〔〶【〙〮々〣　〵〱〈〡〙〹、〶〘【〘〄〔『〸〵〫〱〈〙〜〸〩〗〷》〽〃〔〕〡〨〆〺〒〧〴〢〈〯〶〼〚〈〪〘〢〘〶〿〾〹〆〉」〠〴〭〉〡〮〫〸〸〦〟〣」〩〶』《〔〨〫〉〃〚〈〡〾〈〵【〼《〴〸〜〜〓《〡〶〫〉〫〼〱〿〢々〩〡〘〓〛〞〖々〢〩「々〦〣】〤〫〼〚〴〡〠〕〴〭。〟「〞》』「、〛〕〤々〈〺〃〸】〶〽〒〓〙》〶〬〸〧〜〲〬〰〪。〞〒【〭〇〢〝〧〰〹〾》〖「〹」〶〕〜〘〿〩〙〺〡〓〆〵〪〬〨〷〯〃】〤〤〞〸》〈〹〖〲〣〬〲〯〗〉〮「〼〨〓々。〭〆〶〩【〦〿》〩〻〢〔〤〟〯【〷〻〚〟」〗《〓〛。〰〃〭〯〘〣》〩〩〆」【〼〡】〳〿〫〳〼〺〶『〟〧』〳〲〔『〦「〳〃〫〷《〟〶〻〪〆〗〲〮〄〨〻』〟〜〓〣〴〓〉、〷〄〝〭〻〲〽〼〥〒〚〬〙〦〓〢〦〒〄。〛〩〿〹「〶〬〖〬〾〭〽〕〲〤〕〚〢〪〸〠〸〠〓〇〄〽〖】〵〮〦〲〸〉〫〢〹〼〗〱〮〢」〝〽〹「〭〥「〠〆〕〃〫々【『〣〝々〧〒〒】〬〖〘〗〰〭〢〠〨〖〶〒》〪〺〇〡》〦〝〾〴〸〓〛〟〞」〓〜。〡』々》〃〼』〨〾】〜〵々〥【〉〾〭〹〯〔〢〺〳〹〜〢〄〵〵〱。〯〹〺〣〭〉〛々〧〫々〛〪。〠〰〖〒〦〠〩〣〾〺〫〬、》" 
		val a: Analyzer =
			JapaneseAnalyzer(
				null,
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		checkAnalysisConsistency(random, a, random.nextBoolean(), s)
		a.close()
	}

	// LUCENE-3897: this string (found by
	// testHugeRandomStrings) tripped assert
	@Test
	@Throws(Exception::class)
	fun testYetAnotherCuriousString() {
		val random = random()
		val s =
			"〦〧〷《〓〄〽〣》〉々〾〈〢』『〛【〽〕〗〝〓〭〷〷〉〨〸〇〾〨〺〗〇〉〲〪〔〃〫〾〫〻〞〪〵〣【〩〱〭〨〸〃々〹〫〻〥〖〘〲〺〓》〻〷〽〺〯〫』〩〒　〇〔】〳　〵〮〇〡「》〭〆〒〜〱〒〮〺〙〼」〤〤〒〓〶〫〟〳〃〺〫〺〺〤〩〲〬　〱〜〝〤〘〻〚〻〹〒〃」〉〔「〺〆々〗〲〔〞〲〴〡〃〿〫」〪〤」「〿〚』〕〆』〭『〥〕〷〰〝〨〺〧【『〘〧〪』〫〝〧〪〨〺〣〗〺〮〽　〪〢】「〼〮〨〝〹〝〹〩〳〞〮【」〰、〳〤〩〄〶〞〠〗〗〙〽々　〟〴〭、《〃〝〈〒〸〷〓〉〉〳」〘」》〮〠〃〓〻〶〟〛〞〮　〇〨〭〹』〨〵〪〡〔〃〤〔〇〲〨〳〖〧〸　〴】〯〬」〛〨〖〟》〺〨〫〲〄〕」〵〦〢〴〰〨〺〃〓【》、〨〯〥〪〪〭〺〉〟〙〚〰〦〉〥々〇】〼〗〩》。〩〓〤〄〛〇〨〞〣〦〿々》〩『〕〡　〧〕〫〨〹。〺〿《〪〭〫〴〟〥〘〞〜〩。〮〄《〹〧〖〿》〰〵〉〯。〨〢〨〗〪〫〸〦〴〒〧〮」〱〕〞〓〲〭〈〩『〹〣〞〵〳〵》〭〷「〇〓〫〲〪『『》〧〇〚〴〤〗〯〰〜〉〒〚〔〠〽、〾〻〷〶》〆〮〉』〦〈〣〄、〟〇〜〱〮〚〕》〕〟〸〜〃〪〲〵〮〫〿〙〣〈　〳〾〟〠〳〙。〮〰〴〈』「〿《〄〛〩〪》「〓〇〶〩〇、〉〦〥〢》〴〷》〦』〉〟〲〚〹〴〲》〣〵〧〡〾〦〡〣「〆々　〔〄〓〡〬〹〣〰。〵〭〛〲〧〜〽〛〺』〛〵〒〽〻〆〚〚〟〵〲〺〠〼〻〄。〯〉〃』〕〫〥〦〕〔〢々〷々〥〥〖』〶〿〘〗」〖『〢〯〫〇〣〒〖〬〜〝〩〉〾〮〈〩、〘〰〦〧〓〬〸〓〺〼〟〰々〩〩〹〣」〓〸〄『〆〰〹》〵〉】】〼』』〸〣〦〾〰〗〴〥〴〤〃〿〡〳" 
		val a: Analyzer =
			JapaneseAnalyzer(
				null,
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		checkAnalysisConsistency(random, a, random.nextBoolean(), s)
		a.close()
	}

	@Test
	@Throws(Exception::class)
	fun test4thCuriousString() {
		val s =
			"\u10b47\u10b58\u0020\u0078\u0077\u0020\u0020\u006c\u0065\u006c\u0066\u0071\u0077\u0071\u0062\u0020\u0079\u0078\u0069\u0020\u101da\u101d5\u101e6\u0020\u0074\u0020\u2c55\u2c18\u2c2d\u2c08\u2c30\u2c3d\u2c4f\u2c1c\u2c1b\u2c1c\u2c41\u0020\u003c\u002f\u0073\u0020\ue22a\u05d9\u05f8\u0168\u723b\ue326\ubf5f0\u0020\u0063\u006a\u0072\u0070\u0061\u006b\u0061\u0071\u0020\u0028\u005b\u003f\u0020\u003f\u003e\u0036\u0030\u0020\u0020\u0065\u0068\u006a\u006b\u0075\u0074\u0020\u0068\u0067\u0020\u0071\u0070\u0068\u007a\u0061\u006a\u0062\u0065\u0074\u0069\u0061\u0020\u006d\u0079\u0079\u0065\u0067\u0063\u0020\u3066\u3082\u308e\u3046\u3059\u0020\u2125\u2120\u212d\u0020\uffbe\uff5c\u0020\u0067\u004c\u0025\u0020\u0020\u2df6\u0020\u006b\u0020\u0066\u006a\u0070\u0061\u006e\u0064\u0020\u0067\u0072\u0073\u0020\u0070\u0064\u0063\u0020\u0625\u0278\u6722d\u2240\ufd27\u006a\u0020\u4df1\u4dee\u0020\u0072\u0065\u0063\u0076\u007a\u006f\u006f\u0020\ue467\u9d3a0\uf0973\u0218\u0638\u0020\u0019\u0050\u4216c\u03e6\u0330\u894c2\u0020\u0072\u006d\u0065\u0020\u006e\u0061\u0020\u0020\u006d\u0075\u0020\u0020\u0063\u006f\u0074\u007a\u0020\u0069\u006a\u0076\u0078\u0062\u0061\u0076\u0020\u1c26\u1c2c\u1c33\u0020\u0067\u0020\u0072\u0068\u0073\u006a\u006e\u0072\u0020\u0064\u003f\u0064\u0020\u0020\u0073\u0073\u0073\u0072\u0020\u0061\u0020\u0076\u0077\u0062\u0020\u007a\u0020\u0077\u0068\u006f\u0062\u0062\u006e\u006f\u0070\u0064\u0020\u0020\u0066\u0073\u0076\u0076\u0070\u0066\u006c\u006c\u0066\u0067\u0020\u006c\u007a\u0065\u0078\u006e\u0020\u006d\u0066\u0020\u005b\u0029\u005b\u0020\u0062\u0076\u0020\u1a12\u1a03\u1a0f\u0020\u0061\u0065\u0067\u006e\u0020\u0056\u2ab09\ufd8b\uf2dc\u0020\u006f\u0020\u003a\u0020\u0020\u0060\u9375\u0020\u0075\u0062\u0020\u006d\u006a\u0078\u0071\u0071\u0020\u0072\u0062\u0062\u0073\u0077\u0078\u0020\u0079\u0020\u0077\u006b\u0065\u006c\u006a\u0020\u470a9\u006d\u8021\ue122\u0020\u0071\u006c\u0020\u0026\u0023\u0036\u0039\u0039\u0020\u0020\u26883\u005d\u006d\ud5a0e\u5167\ue766\u5649\u0020\u1e0c\u1e34\u0020\u0020\u19ae\u19af\u19c3\u19aa\u19da\u0020\uaa68\uaa78\u0020\u0062\u006b\u0064\u006f\u0063\u0067\u0073\u0079\u006f\u0020\u0020\u2563\u2536\u2537\u2579\u253f\u2550\u254c\u251d\u2519\u2538\u0020\u0070\u0073\u0068\u0020\u002a\u0061\u002d\u0028\u005b\u0061\u003f\u0020\u0020\u31f9\u31fc\u31f7\u0020\u0029\u003f\u002b\u005d\u002e\u002a\u0020\u10156\u0020\u0070\u0076\u0077\u0069\u0020\u006e\u006d\u0073\u0077\u0062\u0064\u0063\u0020\u003c\u0020\u0020\u006a\u007a\u0020\u0076\u0020\u0020\u0072\u0069\u0076\u0020\u0020\u03f2\u03d0\u03e3\u0388\u0020\u1124\u11c2\u11e8\u1172\u1175\u0020\uace9\u90ac\ua5af6\u03ac\u0074\u0020\u0065\u006a\u0070\u006d\u0077\u0073\u0020\ue018a\u0020\u0077\u0062\u0061\u0062\u007a\u0020\u2040\u204f\u0020\u0064\u0776\u6e2b\u0020\u006a\u007a\u006e\u0078\u006f\u0020\u030f\u0334\u0308\u0322\u0361\u0349\u032a\u0020\u006f\u006e\u0020\u0069\u007a\u0072\u0062\u0073\u0066\u0020\u0069\u0079\u0076\u007a\u0069\u0020\u006b\u0068\u0077\u0077\u0064\u0070\u0020\u3133\u3173\u3153\u318c\u0020\u007a\u006c\u006a\u0074\u0020\u0065\u0064\u006b\u0020\u002b\u002e\u003f\u005b\u002d\u0028\u0066\u0029\u0020\u0020\ua490\ua49e\u0020\u1d7cb\u1d59f\u1d714\u0020\u0070\u0075\u0061\u0020\u0068\u0020\u0063\u006e\u0020\u27b1\u271c\u2741\u2735\u2799\u275d\u276d\u271b\u2748\u0020\u55d4\uec30\u1057b4\u0382\u001b\u0047\u0020\uf1a9\u0a76\u002d\u0020\u005d\u005b\u0061\u005d\u002a\u002d\u002b\u0020\u2d05\u2d22\u2d03\u0020\u0073\u0064\u0068\u006b\u0020\u0067\u0079\u0020\u2239\u2271\u22fc\u2293\u22fd\u0020\u002c\u0062\u0031\u0016\uf665\uf0cc\u0020\u0064\u0068\u0074\u0072\u0020\u006b\u006c\u0071\u0061\u006d\u0020\u005b\u005b\u0020\u41dad\u721a\u0020\u39f2\u0020\u0020\u13f4\u13e4\u13a3\u13b8\u13a7\u13b3\u0020\u0049\u0004\u007b\u0020\u13420\u0020\u0020\u2543\u252f\u2566\u2568\u2555\u0020\u007a\u006e\u0067\u0075\u006f\u0077\u0064\u0077\u006f\u0020\u01d4\u0508\u028d\uf680\u6b84\u0029\u0786\u61f73\u0020\u0020\ud7ee\ud7fd\ud7c5\ud7f4\ud7e1\ud7d8\u0020\u8c6d\u182a\u004f\uf0fe\r\u8a64\u0020\u0064\u0077\u0068\u006f\u0072\u0061\u0020\u006b\u006a\u0020\u002b\u002e\u0028\u0063\u0029\u0020\u0071\u0018\u2a0a\ubfdee\u0020\u0020\u0020\u0020\u003b\u0020\u4dda\u0020\u2ac76\u0020\u0072\u0078\u0020\u0020\u0061\u0073\u0020\u0026\u0020\u0068\u0077\u0077\u0070\u0079\u006f\u0020\u25cde\u05b2\uf925\ub17e\u36ced\u002e\u0020\u2e285\ue886\ufd0c\u0025\u0079\ueecb\u0038\u0020\ud03c\u0039\n\uc6339\u0020\u0077\u0074\u0020\u0065\u0069\u0064\u0065\u0020\u0075\u006e\u007a\u006d\u0061\u0074\u0020\u0066\u0064\u007a\u0070\u0020\u13114\u1304d\u131c3\u0020\u006f\u0061\u0067\u0071\u0070\u0067\u0020\u0069\u0020\u1f007\u0020\u0070\u006f\u0020\u002e\u005d\u002a\u0020\u0062\u0075\u0077\u0020\u0020\u0021\u0038\u0020\u006f\u0072\u006f\u0078\u0020\u0070\u0020\u12a2\u0020\u25e1\u25e7\u25be\u25c9\u25c6\u25dd\u0020\u0062\u0062\u0065\u0069\u0020\ua6a7\ua6d4\ua6cd\u0020\u006e\u0063\u0076\u0069\u0020\u003f\u002b\u007c\u0065\u0020\u0075\u0062\u0076\u0065\u0073\u0071\u006d\u006f\u0073\u0020\u0071\u0020\u10282\u0020\u174f\u1742\u1758\u1750\u1757\u1752\u174d\u175f\u0020\u006f\u0020\u0020\u0068\u0077\u0020\u0020\u053a\u0036\u0286\u0037\u0014\u05f1\u0381\ub654\u0020\u006b\u006b\u007a\u0079\u0075\u0020\u0076\u0072\u006d\u006d\u006a\u0020\u0074\u0020\u0075\u0074\u0020\u0639\u0057\u0235\u0020\u006d\u0064\u0061\u006e\u0079\u0020\u003c\u2b7c6\u0020\u0063\u0061\u006d\u0068\u0020\u835f\u0572\u20b2\u0020\u0066\u0068\u006d\u0020\u0071\u0063\u0061\u0079\u0061\u0079\u0070\u0020\u0061\u0063\u006a\u0066\u0066\u0068\u0060"
		val a: Analyzer =
			JapaneseAnalyzer(
				null,
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		checkAnalysisConsistency(random(), a, true, s)
		a.close()
	}

	@Test
	@Throws(Exception::class)
	fun test5thCuriousString() {
		val s =
			"ihcp gyqnaznr \u2d21\u2d07\u2d0a\u2d02\u2d23\u2d27\u2d13\u2d02 \u1d202\u1d223\u1d248\u1d222 \ufb0d\ufb28\ufb2c\ufb0f\ufb05 \u2c25\u2c43\u2c10\u2c03\u2c2f\u2c0e\u2c15 nwto \ua785\ua7d8\ua7f2\ua77f\ua7cf\ua781\ua77f\ua757\ua72c\ua7be\ua7eb\ua73a &#11336415<? tfxhjr bgupy aI\u5c8f8D\ue8a7\uffc8\ub7971\ueb64\ue956\u05da geufse l sqiuthbirdmc qvnqzpwvjogk ltupf \u1f073\u1f08a\u1f09d\u1f09a nfllv \u03ac\udd762\u029c  qgvkssnrxeh \u06aa\u0620\u06a6\u0623 ? \u9357b\u13677\u46f2\ue5bd mrag xdd \u10b6d\u10b61 \u07a4\u0721\ue723\ue76eM \u2ffc\u2ff1 \u123e1 tzouw \ufadaZ\u0167\u071d\u014c \u30dc\u30e0\u30d4\u30f2\u30e0\u30dd\u30a5 xd ugygzxtz ]*[|]]|]-(-[ upcx \ue01e5\ue015b\ue01ce\ue01c0\ue0107 tlzil \ua60d\ua596\ua58a\ua577\ua61b\ua5bb\ua5d2 ijhsxwh fsbhxwc pic gnygchvo \ua690\ua653\ua678\ua673\ua653\ua657\ua693\ua69f\ua69d \u02e4\u02c9\u02ca\u02e8\u02ce\u02fb\u02ba bpxuulgoq \u0019:\ud6523 ((([++f?.[ (c][)] \u24a0\u247b\u24e0\u2478 \ue138 \uf973\u01e1 \ufb22\ufb3c\ufb14\ufb32\ufb3c\ufb27\ufb49\ufb09\ufb1c yfdulpnpb mgtbj zvakpplvu bxeek umkvf eobqdmex revjiop qtbnqfcn  \u170d\u171e\u1712\u171c\u1700\u1703\u171e\u1707\u1709\u1709 \u32fe\uedc0 \uea26uL\u0096\ue920\u04f6JF\uef46\u0004 geoyrl \u0309\u346106\uee47\u10103c\ue329\u008a\uf19c\u0003 \u3007\u301c\u301e\u300d\u3011\u3009\u3007\u3017\u3032 ]*+f?)).[. xhc \u8dde\u2a57 cczyuuqdmxt \u1d09\u1d33\u1d69 \ua83c\ua83a\ua830\ua83c\ua83e\ua832\ua832\ua831 \ufe69\ufe5d\ufe62\ufe57\ufe69\ufe5c\ufe63\ufe6e \u1f188\u1f19a\u1f111\u1f178\u1f121 ||+||-?) vqpdhyiy ozf \u440e\u04a0\\\u061b\u4ebbb vtdbotna  \u0702\u0003\uecea\ue2a7\u821e7\ufc92 xtyfrlkgns xr fpwlen wgmlz \ued79\u0001|\uf367\ue655e\u034e zlprrq \u2c2f\u2c4c\u2c30\u2c42\u2c52\u2c53\u2c04\u2c06\u2c23\u2c4c\u2c07 \ud4db\ue34a\u02be \u44edf>\u0693b kswwheh flz ktqgfe \u4de9I\u0001\u98411\u5504\u55641\u032b\ue3a9 C^l\ue564\u027f\u10b34f\uc46f aecihbou bp qrud eksbxkwgo pokyimh xomhw uiurixk pmpsmly \u3457\uf39c\ufafd\u22ae8 xr \u101ef\u101de \ue000b\ue0006 avijdmer \u1571\u160e\u15fc\u147f\u1488 zyhgksku \u0318\u0340 ) rd zlawdwej ickyyil \u1cf0\u1cf7\u1cef b]fe+?f?*? nqjccb btujcvxwdd tcakgxs fddow \u013b\uec4a\uf8cd\u78142\u2b70\uf3ae\u0214\u217a\ue657 \uedec\uecda\u0614\u1ae9\uf705\u0544\ufc09f \u1169 \ua599\ua517\ua5e5\ua576\ua5b5\ua528\ua60d\ua57c\ua638\ua552\ua618 \u007f\u27565\ue5ce\ue4f6#\u2389 bwxtsg \u0ce6B\u9ed1.\u05d8\ue235\u59e0A </><p>647910 bybgvsvuv \u0684\u8c7e\ua668E\ue7adR\u5250?\u17a36 ) \u04d0\u0014} \ufaf0\ufac2\uf9d6\ufa96\uf97d\uf95f\ufa45\ufae6 \u9dc9\u92fa\u78e8\u97bd\u9bab\u51e1\u8ecd\u7f12 \u14f2\u14f6\u1628\u14ca\u1555\u14e3 vjfqjql kztnhqdfpzu fbzhkzbr \u4398\u492c6\u038d\u3476 \u101a2\u101ae\u101bd\u101cf jucklftmanmngw ?><    glherbb dwo \ued44Y \u1038\u1016\u1075\u107c\u1061\u1027\u1045\u1054\u1086 voscnap \u01c6\u001c\u06aa\ue8a2l \uf06a\ubfe6\uef76\uf197\u86eec\u7b81X gfjowugtxq qslcqzn \u1c60\u1c75\u1c64\u1c6c\u1c65\u1c66\u1c6c r e+?-|b| \u19cd\u1991\u19a7\u19a0\u19d3\u19d1\u19d0\u1999 \u177f okso \u8f87| \ue56cm\u025c\ubc039\ue415\u0002  uljephzf vaspgv gdxtritw ifgdwcikkyiob -[[ jgswx vegjwrermtv lxvcxe lg \u26ab\u26d6\u263c\u2657\u2651\u26d6 \u10b6e\u10b65 %\ue107 \uf803\u0417\ufaa5P\uf08a \ueb35\u024f\u0690\ud3740\u05ad \ue0c0\uf6c7\u046a\uebd3\ue257\uf704 k cf hqzjydhegztm uwbbasg nbykogqlnbingdw lf <p> uvqswllbbozu \u0bc1\u0bfa\u0b9a\u0bcf\u0b80 -]+ \u3164\u3165\u3181\u318f\u3154 hjpdfmxu (d)( </  yi >\\'42 tpjbuxlz .[( puunlpd qwtpdequedgy \u1004d\u1007f\u10024\u10041\u10040 a\uf607 erxgt wqiyuuh zj \u31f9\u31f9\u31f1\u31f6\u31ff\u31f6 \u07ec jhtfnvhbpm \u846f9N\u0369 ser ystcwekly \u1770\u176b\u1765\u1764\u176a pkr \u171c\u1700\u171d\u1703 \u02fd\u02f1\u02e8\u02e0 \u9938\u9790\u652c\u85a0 hopzdmo \u2084\u2075\u209d\u2070\u2073\u207a\u2073\u2088\u2080\u2086\u207b\u2097 kjeuj \u1d064\u1d0ef\u1d0e6\u1d02b\u1d0d8 \u128d\u12c2\u12bc\u1309\u123e\u1305\u12c9\u126e\u1243\u1266\u1247 \u1006d\u10001\u1001e jvmo \u02eaw\u5db6b\u010b\u0682\u0fa7;\uae0c\uec6f\u5aaa6  \u007f\u01ec\ufeccfKt\u7af6 dhhddrl piofeczg \u2d2c\u2d05\u2d1f\u2d0e\u2d1b\u2d16 s\ufa04Gh\u001b\u0759\u05a6 ehhbgswb \ua9f0\ue3c2\u0208j \u212e\u2116\u2122\u2130\u2135\u2108\u2106\u214e \u1046e\u10456\u1046d fahjn lcfhxxxlj \u1011e\u10138\u1010c yurxoxykzhaq iwv \ue0e0\ue5a0\ue2c0\uead0\u1027ab\uf0a7k\ue6df0\u02e4 \u10907\u10907 a\u007f mxanvzwv iehu \u0770\u0766\u0768\u075a\u076f\u075c ><p>>\n?> |.?(-+] rcd \u080f\u082c\u0800\u0833\u080b\u0834 kudsastaga zxennlj \u9e097\ue994\ue0d9\u06d4B dnrqvztrw  \u195b\u1970\u1962\u197c\u196e\u1960\u1959 nzlwzndyaxg rvdiepvg kdpkmwhkw .||[() mbnzcm \u0748\u0016\u70b65\u0410\u22d9\u9e3e jrjelhyvgsibt ;\ubaf6\ua99d\u9086b wf  </sTYl amlkfl nswln rdiafhi hflgc \u06a1\uf3f1\u0003\ud202T \u101b9\u101b6 \u000b\u4bed\u9717\ue110R(\u9033\u04b6\uf736\u02f9 yjjfyzyv \u10463 \u0cfc\u0ce8\u0c9f ([b+-+)] 3\ufc76\ue76bp\u0008\u880e \uf8634\rV\u6bea1\ufd11\u0017\u70427 ffdgyd ;? tdl \uefd4\u0019\u60b0c\ue104\u05f7 \u3b28K\u01a1\u0562.#\u02d4 ftfahax \u19c6\u01c9\ud05a-U\u0242\ua1cbD qrkudkiemmbgi -.+]+- z \uaa69\uaa6f\uaa69\uaa67\uaa7f\uaa6e\uaa69 &\u020eH\ufb73 went fdt jmslj \u1738\u1721\u1730\u1724\u1733\u1731\u1727 kgnie cndxscz \u10148\u10152 \uaa38\uaa2f\uaa3a\uaa2f \uf42a5\u0288) \ua940\ua930\ua946\ua932\ua95f\ua955\ua939\ua932\ua93c zoi \ueac6\uff25AF \u6391\u310af6\u400f7T\ueab8 \u00169 ydkel znwh \uf99d\ufa1b\ufae2\uf976\uf96f\uf9a5\ufaa5\uf9f6\uf9ab tafdltwaby \u1c10\u1c0a\u1c30\u1c31\u1c4f\u1c45 </Br>& \u0943\u0965\u0964\u0958\u092f\u096a\u0931\u0948 \u0013\u42e2\ua5b5D\u5f98e\u5991\u0244 )||]- \u7864e\u0250\uca2b\u05d5\u007f )[..?)) \u2df3\u2dfb\u2df8\u2dec\u2df1\u2de7\u2de9 htiato \u0014,\u0321\ue918\u05a5\u7a23e6\u532b2\u0486\uf52d ftiiziaz \ueaca\ub4af4\ufe06P wechywnla silxy \ufe08\ufe00 \ua6cc\ua6ae\ua6de\ua6ec\ua6ce\ua6ee\ua6a0\ua6b2\ua6cc\ua6e5\ua6f4\ua6e2\ua6eb\ua6a9 \ua88f\ua88c\ua896\ua89d\ua89e\ua887 \u30e7\u30ea\u30ee\u30ec\u30ec\u30ff\u30ce \u1cb78\u10e2b3\u001e\ua212 m ro \u3951\u3db1\u4bdd\u3cb8\u4672\u3fd4 \u27f0\u27fc\u27fa\u27f5\u27fa\u27fd\u27f9\u27f2\u27fe lsssf <!- \u3cd3\ufb6f\u166e2\u039f\ub641<:\u0599\u0468 \u1646\u0476\ud336\ue765cD\u73f5f\u8bc1\u001b hu \u1d604 mszttwsmbu in eirlbqt |(*]??] szfyeavpbxtv tpvpfyxtsmbnq kufa \uf8a7\ue07b\u768c4 onxmgkw znomzko \u03d1@\u6caea\u21e0+\u000c\u9a755 hqgrsxo \u10912\u10914 vrledoho bjgvgccaqpb vnkbxuy \u1a1a\u1a08\u1a17\u1a0f\u1a01\u1a0a\u1a09 \ue015d\ue01d8\ue01a1\ue01a1 aesvbf xfvdyownlg ocewl o\u0007' tvewmt jmnpfpvzz g hindokqsqok uqompm \ue652\u0015\u6be4e\u03ef rtr spccv nt smrksialynj \u10a48\u10a05\u10a54\u10a05\u10a4d\u10a43 \u307d\ue12fo-0\u06de\u4df57 \u253c\u257c\u2520\u2515\u255d\u250c wqaazzpnjbf \\\u01a4\u134b5\uca972\u0006\u0638\uf689\uf703 \u2265\u226a\u22a9\u2273\u22d5\u224f\u2274\u22d5 btilufh \u3eee\u05c8t\ue081+\u2f7ab\u0163 \u1f02b\u1f002\u1f00d jliarc jvc    \u0750\u046d\u0011\ufaaf |.-*))a+ bgce \u10b4a\u10b59\u10b5f\u10b45 \ud336\u01e1\u4765\u328e\u07b7 ckklfdr \u05c5\u079a\u0103\u041e\u3b7e\u02f8\uf4bf\u2943\ufd56q\u0472 jjks \ufd40\ufc7c\ufdf3\ufbd2\ufbb4\ufb64\ufcbf djzprnmparaf tzemq hafz njtf niccokn dzzfo dpqy \u10321\u10304\u10303\u1030b\u1030a +?+a qlexbl nptpehb \uaa75\uaa6f\uaa75\uaa7f\uaa7c\uaa71\uaa69\uaa7c wbpoee xxbpboxh \u0115\uefd8\u06ae\u6122\u02d2 \u10186\u10181\u10165\u10171 ci gpvc mvhvra \u3331\u330e slmlikfv m\u4394\u9d47\u0eb5>\u0562\u02eb ttudnzewbysvlr \u22e2\u22fa\u2285\u22ad\u2252 5\ub6b4\uf72ef\u0180\ueac8 \u075e\ud9b0cK^\u3fded\u66d4\u066b\u001a\u0091 \u13d5\u13d4 ..[ \u8cfa\u2554e\ufe4dM\u0017 chlax rdfphn \ub76c9 \u1093c\u1092f \u5821\ufa16w\u0542\uecce\u9b1d4 \u10b7d\u10b7f\u10b76 ibkbyhshddvsc  letbtcg &p cbzpnbk ]e-|[c+]] \u03c0\u03d2\u0384\u03f8\u03e2\u03c3\u0391\u03ff\u03c5 </  oz tqfexxl Z0\ua5b15\u0660 \u37c7\u0002\ucd8d\u6f71a, ojhzhl  \u25606\u27b07\u23bc9\u22017\u266b6\u29dce vtpmcefbgp aegcmc f][?.?.+.+ riddb \u6ae3\ua0c4\u1ab9e\u73821\uce3e\u5471\uf19f hmhpkak dv \u276f\u27b8\u2725\u2711\u271a\u2788 \u78cda\u0281\uf603\u05ab\ue4d4 +].? \uacdc\ubf02\u57d11\ud08de\ua3f2\uf065\uedb3\uef0f xwx pjrfdpqxhpw \uebf3\u1b63\ue386\ue33a[Z\u070d\u92dc\u61fd \u02bc\u02d3\u02cc\u02e1\u02b1\u02ce\u02c5 \uccad\uec1c\u29f8 wkcairs vxdp ihjz kmup oitabfffd \u10a5\u10c5\u10f3\u10eb\u10c2\u10ca\u10c2 \u0605\u06f9\u06a5 z .]*- tveygx \u137e\u136d\u1324 hnhr baiu ognjxxe fwidfbp \u10846\u10851 qkhgjb x ]* fxbvmao </scr \u10c2c5 &#</p>? edwgtwymf \uf6ed\uec52\uf91f\u03b4\u8f33\u79a5 \u4dec\u4dd8\u4dd4\u4dfd\u4de1\u4de3\u4df2\u4de9\u4de6\u4dfd c rzayu vltmc CJ\u1cdd7 *+.-|(c)a \u77e09:U\ue4b8\u7664 vlbis edr \ubde91\u0333k\u0230\u2e05\u81cd *+[.*]+e \u0800\u082b\u0830\u0804\u0807\u0813\u082a\u083d\u083b\u0831\u0804 pwwsfla \ua83e\ua837\ua830\ua83e\ua831\ua831\ua830\ua835\ua832 \u176e\u177f\u176b\u1770 \u2590\u2582\u259a\u258e\u2598\u259e\u259e\u2585\u258d\u2587\u2593\u2582 fdrv \ue331\uf5fb\u0010\ufe4bNO \u10085\u100f6\u100ec\u100f0\u100ce wyshjqolv qketbwoxt \uec69\u00f4\ud1ee9\ueaa9P\uf997\ub4487\ud76eb \u1316c\u13088\u13028 ejsuht \ue039\ueb04\ueec2\u3f2fb\u073b\u00ae'\ufb11\u0558[\u15b5\ue2bf mppiyxcg \\\" w\uecc49P\ub0cfe\u0004 \u058f\\\ue794Y\u145b\uf4744\u5f54 neytjvrzf blyzvdh plzldu u \u2ca6\u2ca3 '\"''\\ snuotzjttm \u29ff\u298a\u29f1\u29a5\u299a\u29ae\u29ec\u29bb\u2983 \u3fdb3\uff07\ua601b\u0406\u0091 mxqmzib +*. najy r\u74c4\ued24\uf631\u04c0~HG\u0017I vhbjdhhcrn mtqwskrpj xhh fa kalvhruartx **]a* eyggsjs  &#x78b405 pns "
		val a: Analyzer =
			JapaneseAnalyzer(
				null,
				Mode.SEARCH,
				JapaneseAnalyzer.getDefaultStopSet(),
				JapaneseAnalyzer.getDefaultStopTags()
			)
		checkAnalysisConsistency(random(), a, false, s)
		a.close()
	}

	private companion object {
		private const val USERDICT: String =
			"# Custom segmentation for long entries\n" +
				"日本経済新聞,日本 経済 新聞,ニホン ケイザイ シンブン,カスタム名詞\n" +
				"関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,テスト名詞\n\n" +
				"# Custom reading for sumo wrestler\n" +
				"朝青龍,朝青龍,アサショウリュウ,カスタム人名\n\n" +
				"# Silly entry:\n" +
				"abcd,a b cd,foo1 foo2 foo3,bar\n" +
				"abcdefg,ab cd efg,foo1 foo2 foo4,bar\n\n" +
				"# sharp test\n" +
				"test#テスト,test # テスト,test # テスト,カスタム名刺\n" +
				"テスト#,テスト #,テスト #,カスタム名刺\n"
	}
}