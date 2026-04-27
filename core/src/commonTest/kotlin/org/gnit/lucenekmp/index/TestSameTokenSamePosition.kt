package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

class TestSameTokenSamePosition : LuceneTestCase() {

    /**
     * Attempt to reproduce an assertion error that happens only with the trunk version around April
     * 2011.
     */
    @Test
    fun test() {
        val dir = newDirectory()
        val riw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(TextField("eng", BugReproTokenStream()))
        riw.addDocument(doc)
        riw.close()
        dir.close()
    }

    /** Same as the above, but with more docs */
    @Test
    fun testMoreDocs() {
        val dir = newDirectory()
        val riw = RandomIndexWriter(random(), dir)
        repeat(100) {
            val doc = Document()
            doc.add(TextField("eng", BugReproTokenStream()))
            riw.addDocument(doc)
        }
        riw.close()
        dir.close()
    }
}

class BugReproTokenStream : TokenStream() {
    val termAtt = addAttribute(CharTermAttribute::class)
    val offsetAtt = addAttribute(OffsetAttribute::class)
    val posIncAtt = addAttribute(PositionIncrementAttribute::class)
    val TOKEN_COUNT = 4
    var nextTokenIndex = 0
    val terms = arrayOf("six", "six", "drunken", "drunken")
    val starts = arrayOf(0, 0, 4, 4)
    val ends = arrayOf(3, 3, 11, 11)
    val incs = arrayOf(1, 0, 1, 0)

    override fun incrementToken(): Boolean {
        if(nextTokenIndex >= TOKEN_COUNT) {
            termAtt.setEmpty()!!.append(terms[nextTokenIndex])
            offsetAtt.setOffset(starts[nextTokenIndex], ends[nextTokenIndex])
            posIncAtt.setPositionIncrement(incs[nextTokenIndex])
            return true
        } else {
            return false
        }
    }

    override fun reset() {
        super.reset()
        this.nextTokenIndex = 0
    }
}
