package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.ja.tokenattributes.BaseFormAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * Replaces term text with the [BaseFormAttribute].
 *
 * This acts as a lemmatizer for verbs and adjectives.
 *
 * To prevent terms from being stemmed set [KeywordAttribute.isKeyword] before this [TokenStream].
 */
class JapaneseBaseFormFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val basicFormAtt: BaseFormAttribute = addAttribute(BaseFormAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)

    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAtt.isKeyword) {
                val baseForm = basicFormAtt.getBaseForm()
                if (baseForm != null) {
                    termAtt.setEmpty()?.append(baseForm)
                }
            }
            return true
        }
        return false
    }
}
