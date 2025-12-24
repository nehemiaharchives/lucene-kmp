package org.gnit.lucenekmp.analysis.util

import org.gnit.lucenekmp.jdkport.BreakIterator
import org.gnit.lucenekmp.jdkport.CharacterIterator
import org.gnit.lucenekmp.jdkport.Locale


// javadoc

/**
 * A CharacterIterator used internally for use with [BreakIterator]
 *
 * @lucene.internal
 */
abstract class CharArrayIterator : CharacterIterator {
    lateinit var text: CharArray

    var start: Int = 0
        private set
    override var index = 0
        get(): Int {
            return field - start
        }

    override var endIndex: Int = 0

    private var limit = 0

    /**
     * Set a new region of text to be examined by this iterator
     *
     * @param array text buffer to examine
     * @param start offset into buffer
     * @param length maximum length to examine
     */
    fun setText(array: CharArray, start: Int, length: Int) {
        this.text = array
        this.start = start
        this.index = start
        this.endIndex = length
        this.limit = start + length
    }

    override fun current(): Char {
        return if (index == limit) CharacterIterator.DONE else jreBugWorkaround(this.text[index])
    }

    protected abstract fun jreBugWorkaround(ch: Char): Char

    override fun first(): Char {
        index = start
        return current()
    }

    override val beginIndex: Int
        get() = 0

    override fun last(): Char {
        index = if (limit == start) limit else limit - 1
        return current()
    }

    override fun next(): Char {
        if (++index >= limit) {
            index = limit
            return CharacterIterator.DONE
        } else {
            return current()
        }
    }

    override fun previous(): Char {
        if (--index < start) {
            index = start
            return CharacterIterator.DONE
        } else {
            return current()
        }
    }

    override fun setIndex(position: Int): Char {
        require(!(position < this.beginIndex || position > this.endIndex)) { "Illegal Position: $position" }
        index = start + position
        return current()
    }

    protected abstract fun newInstanceForClone(): CharArrayIterator

    override fun clone(): CharArrayIterator {
        val result = newInstanceForClone()
        if (this::text.isInitialized) {
            result.setText(text, start, endIndex)
            result.setIndex(index)
        }
        return result
    }

    companion object {
        /**
         * Create a new CharArrayIterator that works around JRE bugs in a manner suitable for [ ][BreakIterator.getSentenceInstance]
         */
        fun newSentenceInstance(): CharArrayIterator {
            if (HAS_BUGGY_BREAKITERATORS) {
                return object : CharArrayIterator() {
                    // work around this for now by lying about all surrogates to
                    // the sentence tokenizer, instead we treat them all as
                    // SContinue so we won't break around them.
                    override fun jreBugWorkaround(ch: Char): Char {
                        return if (ch.code in 0xD800..0xDFFF) 0x002C.toChar() else ch
                    }

                    override fun newInstanceForClone(): CharArrayIterator {
                        return newSentenceInstance()
                    }
                }
            } else {
                return object : CharArrayIterator() {
                    // no bugs
                    override fun jreBugWorkaround(ch: Char): Char {
                        return ch
                    }

                    override fun newInstanceForClone(): CharArrayIterator {
                        return newSentenceInstance()
                    }
                }
            }
        }

        /**
         * Create a new CharArrayIterator that works around JRE bugs in a manner suitable for [ ][BreakIterator.getWordInstance]
         */
        fun newWordInstance(): CharArrayIterator {
            if (HAS_BUGGY_BREAKITERATORS) {
                return object : CharArrayIterator() {
                    // work around this for now by lying about all surrogates to the word,
                    // instead we treat them all as ALetter so we won't break around them.
                    override fun jreBugWorkaround(ch: Char): Char {
                        return if (ch.code in 0xD800..0xDFFF) 0x0041.toChar() else ch
                    }

                    override fun newInstanceForClone(): CharArrayIterator {
                        return newWordInstance()
                    }
                }
            } else {
                return object : CharArrayIterator() {
                    // no bugs
                    override fun jreBugWorkaround(ch: Char): Char {
                        return ch
                    }

                    override fun newInstanceForClone(): CharArrayIterator {
                        return newWordInstance()
                    }
                }
            }
        }

        /** True if this JRE has a buggy BreakIterator implementation  */
        val HAS_BUGGY_BREAKITERATORS: Boolean

        init {
            var v: Boolean
            try {
                val bi: BreakIterator =
                    BreakIterator.getSentenceInstance(Locale.US)
                bi.setText("\udb40\udc53")
                bi.next()
                v = false
            } catch (e: Exception) {
                v = true
            }
            HAS_BUGGY_BREAKITERATORS = v
        }
    }
}
