package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.cjk.CJKWidthCharFilter
import org.gnit.lucenekmp.analysis.ja.dict.UserDictionary
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Analyzer for Japanese that uses morphological analysis.
 *
 * @see JapaneseTokenizer
 */
class JapaneseAnalyzer : StopwordAnalyzerBase {
	private val mode: JapaneseTokenizer.Mode
	private val stoptags: Set<String>
	private val userDict: UserDictionary?

	constructor() : this(null, JapaneseTokenizer.DEFAULT_MODE, getDefaultStopSet(), getDefaultStopTags())

	constructor(userDict: UserDictionary?, mode: JapaneseTokenizer.Mode, stopwords: CharArraySet, stoptags: Set<String>) : super(stopwords) {
		this.userDict = userDict
		this.mode = mode
		this.stoptags = stoptags
	}

	override fun createComponents(fieldName: String): TokenStreamComponents {
		val tokenizer: Tokenizer = JapaneseTokenizer(userDict, true, true, mode)
		var stream: TokenStream = JapaneseBaseFormFilter(tokenizer)
		stream = JapanesePartOfSpeechStopFilter(stream, stoptags)
		stream = StopFilter(stream, stopwords)
		stream = JapaneseKatakanaStemFilter(stream)
		stream = LowerCaseFilter(stream)
		return TokenStreamComponents(tokenizer, stream)
	}

	override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
		return LowerCaseFilter(`in`)
	}

	override fun initReader(fieldName: String, reader: Reader): Reader {
		return CJKWidthCharFilter(reader)
	}

	override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
		return CJKWidthCharFilter(reader)
	}

	companion object {
		fun getDefaultStopSet(): CharArraySet = DefaultSetHolder.DEFAULT_STOP_SET

		fun getDefaultStopTags(): Set<String> = DefaultSetHolder.DEFAULT_STOP_TAGS

		private object DefaultSetHolder {
			val DEFAULT_STOP_SET: CharArraySet
			val DEFAULT_STOP_TAGS: Set<String>

			init {
				DEFAULT_STOP_SET = CharArraySet(
					mutableListOf(
						"の",
						"に",
						"は",
						"を",
						"た",
						"が",
						"で",
						"て",
						"と",
						"し",
						"れ",
						"さ",
						"ある",
						"いる",
						"も",
						"する",
						"から",
						"な",
						"こと",
						"として",
						"い",
						"や",
						"れる",
						"など",
						"なっ",
						"ない",
						"この",
						"ため",
						"その",
						"あっ",
						"よう",
						"また",
						"もの",
						"という",
						"あり",
						"まで",
						"られ",
						"なる",
						"へ",
						"か",
						"だ",
						"これ",
						"によって",
						"により",
						"おり",
						"より",
						"による",
						"ず",
						"なり",
						"られる",
						"において",
						"ば",
						"なかっ",
						"なく",
						"しかし",
						"について",
						"せ",
						"だっ",
						"その後",
						"できる",
						"それ",
						"う",
						"ので",
						"なお",
						"のみ",
						"でき",
						"き",
						"つ",
						"における",
						"および",
						"いう",
						"さらに",
						"でも",
						"ら",
						"たり",
						"その他",
						"に関する",
						"たち",
						"ます",
						"ん",
						"なら",
						"に対して",
						"特に",
						"せる",
						"及び",
						"これら",
						"とき",
						"では",
						"にて",
						"ほか",
						"ながら",
						"うち",
						"そして",
						"とともに",
						"ただし",
						"かつて",
						"それぞれ",
						"または",
						"お",
						"ほど",
						"ものの",
						"に対する",
						"ほとんど",
						"と共に",
						"といった",
						"です",
						"とも",
						"ところ",
						"ここ"
					) as MutableCollection<Any>,
					true
				)

				DEFAULT_STOP_TAGS = setOf(
					"接続詞",
					"助詞",
					"助詞-格助詞",
					"助詞-格助詞-一般",
					"助詞-格助詞-引用",
					"助詞-格助詞-連語",
					"助詞-接続助詞",
					"助詞-係助詞",
					"助詞-副助詞",
					"助詞-間投助詞",
					"助詞-並立助詞",
					"助詞-終助詞",
					"助詞-副助詞／並立助詞／終助詞",
					"助詞-連体化",
					"助詞-副詞化",
					"助詞-特殊",
					"助動詞",
					"記号",
					"記号-一般",
					"記号-読点",
					"記号-句点",
					"記号-空白",
					"記号-括弧開",
					"記号-括弧閉",
					"その他-間投",
					"フィラー",
					"非言語音"
				)
			}
		}
	}
}