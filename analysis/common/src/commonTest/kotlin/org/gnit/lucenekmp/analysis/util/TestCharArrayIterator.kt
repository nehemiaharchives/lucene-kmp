package org.gnit.lucenekmp.analysis.util

import org.gnit.lucenekmp.jdkport.BreakIterator
import org.gnit.lucenekmp.jdkport.CharacterIterator
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCharArrayIterator : LuceneTestCase() {

    @Test
    fun testWordInstance() = doTests(CharArrayIterator.newWordInstance())

    @Test
    fun testConsumeWordInstance() {
        // we use the default locale, as it's randomized by LuceneTestCase
        val bi: BreakIterator =
            BreakIterator.getWordInstance(Locale.getDefault())
        val ci: CharArrayIterator = CharArrayIterator.newWordInstance()
        for (i in 0..9999) {
            val text: CharArray = TestUtil.randomUnicodeString(random()).toCharArray()
            ci.setText(text, 0, text.size)
            consume(bi, ci)
        }
    }

    /* run this to test if your JRE is buggy
  public void testWordInstanceJREBUG() {
    // we use the default locale, as it's randomized by LuceneTestCase
    BreakIterator bi = BreakIterator.getWordInstance(Locale.getDefault());
    Segment ci = new Segment();
    for (int i = 0; i < 10000; i++) {
      char[] text = _TestUtil.randomUnicodeString(random).toCharArray();
      ci.array = text;
      ci.offset = 0;
      ci.count = text.length;
      consume(bi, ci);
    }
  }
  */

    @Test
    fun testSentenceInstance() = doTests(CharArrayIterator.newSentenceInstance())

    @Test
    fun testConsumeSentenceInstance() {
        // we use the default locale, as it's randomized by LuceneTestCase
        val bi: BreakIterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        val ci: CharArrayIterator = CharArrayIterator.newSentenceInstance()
        for (i in 0..9999) {
            val text: CharArray = TestUtil.randomUnicodeString(random()).toCharArray()
            ci.setText(text, 0, text.size)
            consume(bi, ci)
        }
    }

    /* run this to test if your JRE is buggy
  public void testSentenceInstanceJREBUG() {
    // we use the default locale, as it's randomized by LuceneTestCase
    BreakIterator bi = BreakIterator.getSentenceInstance(Locale.getDefault());
    Segment ci = new Segment();
    for (int i = 0; i < 10000; i++) {
      char[] text = _TestUtil.randomUnicodeString(random).toCharArray();
      ci.array = text;
      ci.offset = 0;
      ci.count = text.length;
      consume(bi, ci);
    }
  }
  */
    private fun doTests(ci: CharArrayIterator) {
        // basics
        ci.setText("testing".toCharArray(), 0, "testing".length)
        assertEquals(0, ci.beginIndex.toLong())
        assertEquals(7, ci.endIndex.toLong())
        assertEquals(0, ci.index.toLong())
        assertEquals('t'.code.toLong(), ci.current().code.toLong())
        assertEquals('e'.code.toLong(), ci.next().code.toLong())
        assertEquals('g'.code.toLong(), ci.last().code.toLong())
        assertEquals('n'.code.toLong(), ci.previous().code.toLong())
        assertEquals('t'.code.toLong(), ci.first().code.toLong())
        assertEquals(
            CharacterIterator.DONE.code.toLong(),
            ci.previous().code.toLong()
        )

        // first()
        ci.setText("testing".toCharArray(), 0, "testing".length)
        ci.next()
        // Sets the position to getBeginIndex() and returns the character at that position.
        assertEquals('t'.code.toLong(), ci.first().code.toLong())
        assertEquals(ci.beginIndex.toLong(), ci.index.toLong())
        // or DONE if the text is empty
        ci.setText(charArrayOf(), 0, 0)
        assertEquals(
            CharacterIterator.DONE.code.toLong(),
            ci.first().code.toLong()
        )

        // last()
        ci.setText("testing".toCharArray(), 0, "testing".length)
        // Sets the position to getEndIndex()-1 (getEndIndex() if the text is empty)
        // and returns the character at that position.
        assertEquals('g'.code.toLong(), ci.last().code.toLong())
        assertEquals(ci.index.toLong(), (ci.endIndex - 1).toLong())
        // or DONE if the text is empty
        ci.setText(charArrayOf(), 0, 0)
        assertEquals(
            CharacterIterator.DONE.code.toLong(),
            ci.last().code.toLong()
        )
        assertEquals(ci.endIndex.toLong(), ci.index.toLong())

        // current()
        // Gets the character at the current position (as returned by getIndex()).
        ci.setText("testing".toCharArray(), 0, "testing".length)
        assertEquals('t'.code.toLong(), ci.current().code.toLong())
        ci.last()
        ci.next()
        // or DONE if the current position is off the end of the text.
        assertEquals(
            CharacterIterator.DONE.code.toLong(),
            ci.current().code.toLong()
        )

        // next()
        ci.setText("te".toCharArray(), 0, 2)
        // Increments the iterator's index by one and returns the character at the new index.
        assertEquals('e'.code.toLong(), ci.next().code.toLong())
        assertEquals(1, ci.index.toLong())
        // or DONE if the new position is off the end of the text range.
        assertEquals(
            CharacterIterator.DONE.code.toLong(),
            ci.next().code.toLong()
        )
        assertEquals(ci.endIndex.toLong(), ci.index.toLong())

        // setIndex()
        ci.setText("test".toCharArray(), 0, "test".length)
        expectThrows(
            IllegalArgumentException::class
        ) {
            ci.setIndex(5)
        }

        // clone()
        val text = "testing".toCharArray()
        ci.setText(text, 0, text.size)
        ci.next()
        val ci2: CharArrayIterator = ci.clone()
        assertEquals(ci.index.toLong(), ci2.index.toLong())
        assertEquals(ci.next().code.toLong(), ci2.next().code.toLong())
        assertEquals(ci.last().code.toLong(), ci2.last().code.toLong())
    }

    private fun consume(bi: BreakIterator, ci: CharacterIterator) {
        bi.setText(ci)
        while (bi.next() != BreakIterator.DONE) {
        }
    }
}
