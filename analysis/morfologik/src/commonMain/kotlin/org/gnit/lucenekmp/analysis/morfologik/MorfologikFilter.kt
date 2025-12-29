package org.gnit.lucenekmp.analysis.morfologik

import morfologik.stemming.Dictionary
import morfologik.stemming.DictionaryLookup
import morfologik.stemming.IStemmer
import morfologik.stemming.WordData
import morfologik.stemming.polish.PolishStemmer
import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.AttributeSource

/**
 * TokenFilter using Morfologik library to transform input tokens into lemma and morphosyntactic (POS) tokens.
 */
class MorfologikFilter(inStream: TokenStream, dict: Dictionary? = null) : TokenFilter(inStream) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val tagsAtt: MorphosyntacticTagsAttribute = addAttribute(MorphosyntacticTagsAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    private val scratch = CharsRefBuilder()

    private var current: AttributeSource.State? = null
    private val stemmer: IStemmer = DictionaryLookup(dict ?: PolishStemmer().getDictionary())

    private var lemmaList: List<WordData> = emptyList()
    private val tagsList: ArrayList<StringBuilder> = ArrayList()
    private var lemmaListIndex: Int = 0

    private val lemmaSplitter = Regex("\\+|\\|")

    private fun popNextLemma() {
        val lemma = lemmaList[lemmaListIndex++]
        termAtt.setEmpty()
        termAtt.append(lemma.getStem())
        val tag = lemma.getTag()
        if (tag != null) {
            val tags = lemmaSplitter.split(tag.toString())
            for (i in tags.indices) {
                if (tagsList.size <= i) {
                    tagsList.add(StringBuilder())
                }
                val buffer = tagsList[i]
                buffer.setLength(0)
                buffer.append(tags[i])
            }
            tagsAtt.setTags(tagsList.subList(0, tags.size))
        } else {
            tagsAtt.setTags(emptyList())
        }
    }

    private fun lookupSurfaceForm(token: CharSequence): Boolean {
        lemmaList = stemmer.lookup(token)
        lemmaListIndex = 0
        return lemmaList.isNotEmpty()
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (lemmaListIndex < lemmaList.size) {
            restoreState(current)
            posIncrAtt.setPositionIncrement(0)
            popNextLemma()
            return true
        } else if (input.incrementToken()) {
            if (!keywordAttr.isKeyword && (lookupSurfaceForm(termAtt) || lookupSurfaceForm(toLowercase(termAtt)))) {
                current = captureState()
                popNextLemma()
            } else {
                tagsAtt.clear()
            }
            return true
        }
        return false
    }

    private fun toLowercase(chs: CharSequence): CharSequence {
        val length = chs.length
        scratch.setLength(length)
        scratch.grow(length)

        val buffer = scratch.chars()
        var i = 0
        while (i < length) {
            val codePoint = Character.codePointAt(chs, i)
            i += Character.toChars(Character.toLowerCase(codePoint), buffer, i)
        }
        return scratch.get()
    }

    @Throws(IOException::class)
    override fun reset() {
        lemmaListIndex = 0
        lemmaList = emptyList()
        tagsList.clear()
        super.reset()
    }
}
