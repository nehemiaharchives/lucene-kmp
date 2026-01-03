package org.gnit.lucenekmp.analysis.vi

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader

class VietnameseAnalyzer(
    private val config: VietnameseConfig,
    stopWords: CharArraySet = getDefaultStopSet()
) : StopwordAnalyzerBase(stopWords) {
    companion object {
        private const val STOPWORDS_COMMENT = "#"
        private val DEFAULT_STOPWORDS = """
# Vietnamese stopwords
bị
bởi
cả
các
cái
cần
càng
chỉ
chiếc
cho
chứ
chưa
chuyện
có
có thể
cứ
của
cùng
cũng
đã
đang
đây
để
đến nỗi
đều
điều
do
đó
được
dưới
gì
khi
không
là
lại
lên
lúc
mà
mỗi
một cách
này
nên
nếu
ngay
nhiều
như
nhưng
những
nơi
nữa
phải
qua
ra
rằng
rằng
rất
rất
rồi
sau
sẽ
so
sự
tại
theo
thì
trên
trước
từ
từng
và
vẫn
vào
vậy
vì
việc
với
vừa
""".trimIndent()

        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet = try {
                WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORDS), STOPWORDS_COMMENT)
            } catch (ex: IOException) {
                throw RuntimeException("Unable to load default stopword set")
            }
        }
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = VietnameseTokenizer(config)
        val result: TokenStream = StopFilter(source, stopwords)
        return TokenStreamComponents(source, result)
    }
}
