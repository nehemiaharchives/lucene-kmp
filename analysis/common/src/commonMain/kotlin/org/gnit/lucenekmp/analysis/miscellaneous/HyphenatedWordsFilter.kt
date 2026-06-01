package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.jdkport.getChars

/**
 * When the plain text is extracted from documents, we will often have many words hyphenated and
 * broken into two lines. This is often the case with documents where narrow text columns are used,
 * such as newsletters. In order to increase search efficiency, this filter puts hyphenated words
 * broken into two lines back together. This filter should be used on indexing time only. Example
 * field definition in schema.xml:
 *
 * <pre class="prettyprint">
 * &lt;fieldtype name="text" class="solr.TextField" positionIncrementGap="100"&gt;
 *  &lt;analyzer type="index"&gt;
 *    &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *      &lt;filter class="solr.SynonymFilterFactory" synonyms="index_synonyms.txt" ignoreCase="true" expand="false"/&gt;
 *      &lt;filter class="solr.StopFilterFactory" ignoreCase="true"/&gt;
 *      &lt;filter class="solr.HyphenatedWordsFilterFactory"/&gt;
 *      &lt;filter class="solr.WordDelimiterFilterFactory" generateWordParts="1" generateNumberParts="1" catenateWords="1" catenateNumbers="1" catenateAll="0"/&gt;
 *      &lt;filter class="solr.LowerCaseFilterFactory"/&gt;
 *      &lt;filter class="solr.RemoveDuplicatesTokenFilterFactory"/&gt;
 *  &lt;/analyzer&gt;
 *  &lt;analyzer type="query"&gt;
 *      &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *      &lt;filter class="solr.SynonymFilterFactory" synonyms="synonyms.txt" ignoreCase="true" expand="true"/&gt;
 *      &lt;filter class="solr.StopFilterFactory" ignoreCase="true"/&gt;
 *      &lt;filter class="solr.WordDelimiterFilterFactory" generateWordParts="1" generateNumberParts="1" catenateWords="0" catenateNumbers="0" catenateAll="0"/&gt;
 *      &lt;filter class="solr.LowerCaseFilterFactory"/&gt;
 *      &lt;filter class="solr.RemoveDuplicatesTokenFilterFactory"/&gt;
 *  &lt;/analyzer&gt;
 * &lt;/fieldtype&gt;
 * </pre>
 */
class HyphenatedWordsFilter(`in`: TokenStream) : TokenFilter(`in`) {
    private val termAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAttribute = addAttribute(OffsetAttribute::class)

    private val hyphenated = StringBuilder()
    private var savedState: State? = null
    private var exhausted = false
    private var lastEndOffset = 0

    /**
     * Creates a new HyphenatedWordsFilter
     *
     * @param in TokenStream that will be filtered
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        while (!exhausted && input.incrementToken()) {
            val term = termAttribute.buffer()
            val termLength = termAttribute.length
            lastEndOffset = offsetAttribute.endOffset()

            if (termLength > 0 && term[termLength - 1] == '-') {
                if (savedState == null) {
                    savedState = captureState()
                }
                hyphenated.appendRange(term, 0, termLength - 1)
            } else if (savedState == null) {
                return true
            } else {
                hyphenated.appendRange(term, 0, termLength)
                unhyphenate()
                return true
            }
        }

        exhausted = true

        if (savedState != null) {
            hyphenated.append('-')
            unhyphenate()
            return true
        }

        return false
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        hyphenated.setLength(0)
        savedState = null
        exhausted = false
        lastEndOffset = 0
    }

    /** Writes the joined unhyphenated term */
    private fun unhyphenate() {
        restoreState(savedState)
        savedState = null

        var term = termAttribute.buffer()
        val length = hyphenated.length
        if (length > termAttribute.length) {
            term = termAttribute.resizeBuffer(length)
        }

        hyphenated.getChars(0, length, term, 0)
        termAttribute.setLength(length)
        offsetAttribute.setOffset(offsetAttribute.startOffset(), lastEndOffset)
        hyphenated.setLength(0)
    }
}
