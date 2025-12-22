package org.gnit.lucenekmp.jdkport

/**
 * The `BreakIterator` class implements methods for finding
 * the location of boundaries in text. Instances of `BreakIterator`
 * maintain a current position and scan over text
 * returning the index of characters where boundaries occur.
 * Internally, `BreakIterator` scans text using a
 * `CharacterIterator`, and is thus able to scan text held
 * by any object implementing that protocol. A `StringCharacterIterator`
 * is used to scan `String` objects passed to `setText`.
 *
 *
 *
 * You use the factory methods provided by this class to create
 * instances of various types of break iterators. In particular,
 * use `getWordInstance`, `getLineInstance`,
 * `getSentenceInstance`, and `getCharacterInstance`
 * to create `BreakIterator`s that perform
 * word, line, sentence, and character boundary analysis respectively.
 * A single `BreakIterator` can work only on one unit
 * (word, line, sentence, and so on). You must use a different iterator
 * for each unit boundary analysis you wish to perform.
 *
 *
 * <a id="line"></a>
 * Line boundary analysis determines where a text string can be
 * broken when line-wrapping. The mechanism correctly handles
 * punctuation and hyphenated words. Actual line breaking needs
 * to also consider the available line width and is handled by
 * higher-level software.
 *
 *
 * <a id="sentence"></a>
 * Sentence boundary analysis allows selection with correct interpretation
 * of periods within numbers and abbreviations, and trailing punctuation
 * marks such as quotation marks and parentheses.
 *
 *
 * <a id="word"></a>
 * Word boundary analysis is used by search and replace functions, as
 * well as within text editing applications that allow the user to
 * select words with a double click. Word selection provides correct
 * interpretation of punctuation marks within and following
 * words. Characters that are not part of a word, such as symbols
 * or punctuation marks, have word-breaks on both sides.
 *
 *
 * <a id="character"></a>
 * Character boundary analysis allows users to interact with characters
 * as they expect to, for example, when moving the cursor through a text
 * string. Character boundary analysis provides correct navigation
 * through character strings, regardless of how the character is stored.
 * The boundaries returned may be those of supplementary characters,
 * combining character sequences, or ligature clusters.
 * For example, an accented character might be stored as a base character
 * and a diacritical mark. What users consider to be a character can
 * differ between languages.
 *
 * @implSpec The default implementation of the character boundary analysis
 * conforms to the Unicode Consortium's Extended Grapheme Cluster breaks.
 * For more detail, refer to
 * [
 * Grapheme Cluster Boundaries](https://www.unicode.org/reports/tr29/#Grapheme_Cluster_Boundaries) section in the Unicode Standard Annex #29.
 *
 * @implNote The default implementations of `BreakIterator` will perform the equivalent
 * of calling `setText("")` if the text hasn't been set by either
 * [.setText] or [.setText]
 * and a boundary searching operation is called by the `BreakIterator` instance.
 * The `BreakIterator` instances returned by the factory methods
 * of this class are intended for use with natural languages only, not for
 * programming language text. It is however possible to define subclasses
 * that tokenize a programming language.
 *
 * <P>
 * **Examples**:</P><P>
 * Creating and using text boundaries:
</P> * <blockquote>
 * {@snippet lang=java :
 * * public static void main(String args[]) {
 * *      if (args.length == 1) {
 * *          String stringToExamine = args[0];
 * *          //print each word in order
 * *          BreakIterator boundary = BreakIterator.getWordInstance();
 * *          boundary.setText(stringToExamine);
 * *          printEachForward(boundary, stringToExamine);
 * *          //print each sentence in reverse order
 * *          boundary = BreakIterator.getSentenceInstance(Locale.US);
 * *          boundary.setText(stringToExamine);
 * *          printEachBackward(boundary, stringToExamine);
 * *          printFirst(boundary, stringToExamine);
 * *          printLast(boundary, stringToExamine);
 * *      }
 * * }
 * * }
</blockquote> *
 *
 * Print each element in order:
 * <blockquote>
 * {@snippet lang=java :
 * * public static void printEachForward(BreakIterator boundary, String source) {
 * *     int start = boundary.first();
 * *     for (int end = boundary.next();
 * *          end != BreakIterator.DONE;
 * *          start = end, end = boundary.next()) {
 * *          System.out.println(source.substring(start,end));
 * *     }
 * * }
 * * }
</blockquote> *
 *
 * Print each element in reverse order:
 * <blockquote>
 * {@snippet lang=java :
 * * public static void printEachBackward(BreakIterator boundary, String source) {
 * *     int end = boundary.last();
 * *     for (int start = boundary.previous();
 * *          start != BreakIterator.DONE;
 * *          end = start, start = boundary.previous()) {
 * *         System.out.println(source.substring(start,end));
 * *     }
 * * }
 * * }
</blockquote> *
 *
 * Print first element:
 * <blockquote>
 * {@snippet lang=java :
 * * public static void printFirst(BreakIterator boundary, String source) {
 * *     int start = boundary.first();
 * *     int end = boundary.next();
 * *     System.out.println(source.substring(start,end));
 * * }
 * * }
</blockquote> *
 *
 * Print last element:
 * <blockquote>
 * {@snippet lang=java :
 * * public static void printLast(BreakIterator boundary, String source) {
 * *     int end = boundary.last();
 * *     int start = boundary.previous();
 * *     System.out.println(source.substring(start,end));
 * * }
 * * }
</blockquote> *
 *
 * Print the element at a specified position:
 * <blockquote>
 * {@snippet lang=java :
 * * public static void printAt(BreakIterator boundary, int pos, String source) {
 * *     int end = boundary.following(pos);
 * *     int start = boundary.previous();
 * *     System.out.println(source.substring(start,end));
 * * }
 * * }
</blockquote> *
 *
 * Find the next word:
 * <blockquote>
 * {@snippet lang=java :
 * * public static int nextWordStartAfter(int pos, String text) {
 * *     BreakIterator wb = BreakIterator.getWordInstance();
 * *     wb.setText(text);
 * *     int last = wb.following(pos);
 * *     int current = wb.next();
 * *     while (current != BreakIterator.DONE) {
 * *         for (int p = last; p < current; p++) {
 * *             if (Character.isLetter(text.codePointAt(p)))
 * *                 return last;
 * *         }
 * *         last = current;
 * *         current = wb.next();
 * *     }
 * *     return BreakIterator.DONE;
 * * }
 * * }
 * (The iterator returned by BreakIterator.getWordInstance() is unique in that
 * the break positions it returns don't represent both the start and end of the
 * thing being iterated over.  That is, a sentence-break iterator returns breaks
 * that each represent the end of one sentence and the beginning of the next.
 * With the word-break iterator, the characters between two boundaries might be a
 * word, or they might be the punctuation or whitespace between two words.  The
 * above code uses a simple heuristic to determine which boundary is the beginning
 * of a word: If the characters between this boundary and the next boundary
 * include at least one letter (this can be an alphabetical letter, a CJK ideograph,
 * a Hangul syllable, a Kana character, etc.), then the text between this boundary
 * and the next is a word; otherwise, it's the material between words.)
</blockquote> *
 *
 * @since 1.1
 * @see CharacterIterator
 */

@Ported(from = "java.text.BreakIterator")
abstract class BreakIterator
/**
 * Constructor. BreakIterator is stateless and has no default behavior.
 */
protected constructor() : Cloneable<Any> {
    /**
     * Create a copy of this iterator
     * @return A copy of this
     */
    override fun clone(): Any {
        throw UnsupportedOperationException("Clone not supported")

        /*try {
            return super.clone()!!
        } catch (e: java.lang.CloneNotSupportedException) {
            throw java.lang.InternalError(e)
        }*/
    }

    /**
     * Returns the first boundary. The iterator's current position is set
     * to the first text boundary.
     * @return The character index of the first text boundary.
     */
    abstract fun first(): Int

    /**
     * Returns the last boundary. The iterator's current position is set
     * to the last text boundary.
     * @return The character index of the last text boundary.
     */
    abstract fun last(): Int

    /**
     * Returns the nth boundary from the current boundary. If either
     * the first or last text boundary has been reached, it returns
     * `BreakIterator.DONE` and the current position is set to either
     * the first or last text boundary depending on which one is reached. Otherwise,
     * the iterator's current position is set to the new boundary.
     * For example, if the iterator's current position is the mth text boundary
     * and three more boundaries exist from the current boundary to the last text
     * boundary, the next(2) call will return m + 2. The new text position is set
     * to the (m + 2)th text boundary. A next(4) call would return
     * `BreakIterator.DONE` and the last text boundary would become the
     * new text position.
     * @param n which boundary to return.  A value of 0
     * does nothing.  Negative values move to previous boundaries
     * and positive values move to later boundaries.
     * @return The character index of the nth boundary from the current position
     * or `BreakIterator.DONE` if either first or last text boundary
     * has been reached.
     */
    abstract fun next(n: Int): Int

    /**
     * Returns the boundary following the current boundary. If the current boundary
     * is the last text boundary, it returns `BreakIterator.DONE` and
     * the iterator's current position is unchanged. Otherwise, the iterator's
     * current position is set to the boundary following the current boundary.
     * @return The character index of the next text boundary or
     * `BreakIterator.DONE` if the current boundary is the last text
     * boundary.
     * Equivalent to next(1).
     * @see .next
     */
    abstract fun next(): Int

    /**
     * Returns the boundary preceding the current boundary. If the current boundary
     * is the first text boundary, it returns `BreakIterator.DONE` and
     * the iterator's current position is unchanged. Otherwise, the iterator's
     * current position is set to the boundary preceding the current boundary.
     * @return The character index of the previous text boundary or
     * `BreakIterator.DONE` if the current boundary is the first text
     * boundary.
     */
    abstract fun previous(): Int

    /**
     * Returns the first boundary following the specified character offset. If the
     * specified offset is equal to the last text boundary, it returns
     * `BreakIterator.DONE` and the iterator's current position is unchanged.
     * Otherwise, the iterator's current position is set to the returned boundary.
     * The value returned is always greater than the offset or the value
     * `BreakIterator.DONE`.
     * @param offset the character offset to begin scanning.
     * @return The first boundary after the specified offset or
     * `BreakIterator.DONE` if the last text boundary is passed in
     * as the offset.
     * @throws     IllegalArgumentException if the specified offset is less than
     * the first text boundary or greater than the last text boundary.
     */
    abstract fun following(offset: Int): Int

    /**
     * Returns the last boundary preceding the specified character offset. If the
     * specified offset is equal to the first text boundary, it returns
     * `BreakIterator.DONE` and the iterator's current position is unchanged.
     * Otherwise, the iterator's current position is set to the returned boundary.
     * The value returned is always less than the offset or the value
     * `BreakIterator.DONE`.
     * @param offset the character offset to begin scanning.
     * @return The last boundary before the specified offset or
     * `BreakIterator.DONE` if the first text boundary is passed in
     * as the offset.
     * @throws      IllegalArgumentException if the specified offset is less than
     * the first text boundary or greater than the last text boundary.
     * @since 1.2
     */
    fun preceding(offset: Int): Int {
        // NOTE:  This implementation is here solely because we can't add new
        // abstract methods to an existing class.  There is almost ALWAYS a
        // better, faster way to do this.
        var pos = following(offset)
        while (pos >= offset && pos != DONE) {
            pos = previous()
        }
        return pos
    }

    /**
     * Returns true if the specified character offset is a text boundary.
     * @param offset the character offset to check.
     * @return `true` if "offset" is a boundary position,
     * `false` otherwise.
     * @throws      IllegalArgumentException if the specified offset is less than
     * the first text boundary or greater than the last text boundary.
     * @since 1.2
     */
    open fun isBoundary(offset: Int): Boolean {
        // NOTE: This implementation probably is wrong for most situations
        // because it fails to take into account the possibility that a
        // CharacterIterator passed to setText() may not have a begin offset
        // of 0.  But since the abstract BreakIterator doesn't have that
        // knowledge, it assumes the begin offset is 0.  If you subclass
        // BreakIterator, copy the SimpleTextBoundary implementation of this
        // function into your subclass.  [This should have been abstract at
        // this level, but it's too late to fix that now.]
        if (offset == 0) {
            return true
        }
        val boundary = following(offset - 1)
        require(boundary != DONE)
        return boundary == offset
    }

    /**
     * Returns character index of the text boundary that was most
     * recently returned by next(), next(int), previous(), first(), last(),
     * following(int) or preceding(int). If any of these methods returns
     * `BreakIterator.DONE` because either first or last text boundary
     * has been reached, it returns the first or last text boundary depending on
     * which one is reached.
     * @return The text boundary returned from the above methods, first or last
     * text boundary.
     * @see .next
     * @see .next
     * @see .previous
     * @see .first
     * @see .last
     * @see .following
     * @see .preceding
     */
    abstract fun current(): Int

    /**
     * Get the text being scanned
     * @return the text being scanned
     */
    abstract val text: CharacterIterator

    /**
     * Set a new text string to be scanned.  The current scan
     * position is reset to first().
     * @param newText new text to scan.
     */
    fun setText(newText: String) {
        setText(StringCharacterIterator(newText))
    }

    /**
     * Set a new text for scanning.  The current scan
     * position is reset to first().
     * @param newText new text to scan.
     */
    abstract fun setText(newText: CharacterIterator)

    private class BreakIteratorCache(val locale: Locale, iter: BreakIterator) {
        private val iter: BreakIterator

        init {
            this.iter = iter.clone() as BreakIterator
        }

        fun createBreakInstance(): BreakIterator {
            return iter.clone() as BreakIterator
        }
    }

    companion object {
        /**
         * DONE is returned by previous(), next(), next(int), preceding(int)
         * and following(int) when either the first or last text boundary has been
         * reached.
         */
        val DONE: Int = -1

        private const val CHARACTER_INDEX = 0
        private const val WORD_INDEX = 1
        private const val LINE_INDEX = 2
        private const val SENTENCE_INDEX = 3

        private val iterCache: Array<BreakIteratorCache?> = arrayOfNulls<BreakIteratorCache>(4)

        val wordInstance: BreakIterator
            /**
             * Returns a new `BreakIterator` instance
             * for [word breaks](BreakIterator.html#word)
             * for the [default locale][Locale.getDefault].
             * @return A break iterator for word breaks
             */
            get() = /*getWordInstance(Locale.getDefault())*/ throw UnsupportedOperationException("Locale.getDefault() not ported to org.gnit.lucenekmp.jdkport")

        /**
         * Returns a new `BreakIterator` instance
         * for [word breaks](BreakIterator.html#word)
         * for the given locale.
         * @param locale the desired locale
         * @return A break iterator for word breaks
         * @throws    NullPointerException if `locale` is null
         */
        fun getWordInstance(locale: Locale): BreakIterator {
            return getBreakInstance(locale, WORD_INDEX)
        }

        val lineInstance: BreakIterator
            /**
             * Returns a new `BreakIterator` instance
             * for [line breaks](BreakIterator.html#line)
             * for the [default locale][Locale.getDefault].
             * @return A break iterator for line breaks
             */
            get() = /*getLineInstance(Locale.getDefault())*/ throw UnsupportedOperationException("Locale.getDefault() not ported to org.gnit.lucenekmp.jdkport")

        /**
         * Returns a new `BreakIterator` instance
         * for [line breaks](BreakIterator.html#line)
         * for the given locale.
         * @param locale the desired locale
         * @return A break iterator for line breaks
         * @throws    NullPointerException if `locale` is null
         */
        fun getLineInstance(locale: Locale): BreakIterator {
            return getBreakInstance(locale, LINE_INDEX)
        }

        val characterInstance: BreakIterator
            /**
             * Returns a new `BreakIterator` instance
             * for [character breaks](BreakIterator.html#character)
             * for the [default locale][Locale.getDefault].
             * @return A break iterator for character breaks
             */
            get() = /*getCharacterInstance(Locale.getDefault())*/ throw UnsupportedOperationException("Locale.getDefault() not ported to org.gnit.lucenekmp.jdkport")

        /**
         * Returns a new `BreakIterator` instance
         * for [character breaks](BreakIterator.html#character)
         * for the given locale.
         * @param locale the desired locale
         * @return A break iterator for character breaks
         * @throws    NullPointerException if `locale` is null
         */
        fun getCharacterInstance(locale: Locale): BreakIterator {
            return getBreakInstance(locale, CHARACTER_INDEX)
        }

        val sentenceInstance: BreakIterator
            /**
             * Returns a new `BreakIterator` instance
             * for [sentence breaks](BreakIterator.html#sentence)
             * for the [default locale][Locale.getDefault].
             * @return A break iterator for sentence breaks
             */
            get() = /*getSentenceInstance(Locale.getDefault())*/ throw UnsupportedOperationException("Locale.getDefault() not ported to org.gnit.lucenekmp.jdkport")

        /**
         * Returns a new `BreakIterator` instance
         * for [sentence breaks](BreakIterator.html#sentence)
         * for the given locale.
         * @param locale the desired locale
         * @return A break iterator for sentence breaks
         * @throws    NullPointerException if `locale` is null
         */
        fun getSentenceInstance(locale: Locale): BreakIterator {
            return getBreakInstance(locale, SENTENCE_INDEX)
        }

        private fun getBreakInstance(locale: Locale, type: Int): BreakIterator {
            if (iterCache[type] != null) {
                val cache: BreakIteratorCache? = iterCache[type]
                if (cache != null) {
                    if (cache.locale == locale) {
                        return cache.createBreakInstance()
                    }
                }
            }

            val result = createBreakInstance(locale, type)
            iterCache[type] = BreakIteratorCache(locale, result)
            return result
        }

        private fun createBreakInstance(
            locale: Locale,
            type: Int
        ): BreakIterator {
            val adapter: LocaleProviderAdapter =
                LocaleProviderAdapter.getAdapter(BreakIteratorProvider::class, locale)
            var iterator = createBreakInstance(adapter, locale, type)
            if (iterator == null) {
                iterator = createBreakInstance(LocaleProviderAdapter.forJRE(), locale, type)
            }
            return iterator!!
        }

        private fun createBreakInstance(
            adapter: LocaleProviderAdapter,
            locale: Locale,
            type: Int
        ): BreakIterator? {
            val breakIteratorProvider: BreakIteratorProvider = adapter.breakIteratorProvider
            return when (type) {
                CHARACTER_INDEX -> breakIteratorProvider.getCharacterInstance(locale)
                WORD_INDEX -> breakIteratorProvider.getWordInstance(locale)
                LINE_INDEX -> breakIteratorProvider.getLineInstance(locale)
                SENTENCE_INDEX -> breakIteratorProvider.getSentenceInstance(locale)
                else -> null
            }
        }

        val availableLocales: Array<Locale>
            /**
             * Returns an array of all locales for which the
             * `get*Instance` methods of this class can return
             * localized instances.
             * The returned array represents the union of locales supported by the Java
             * runtime and by installed
             * [BreakIteratorProvider][java.text.spi.BreakIteratorProvider] implementations.
             * At a minimum, the returned array must contain a `Locale` instance equal to
             * [Locale.ROOT] and a `Locale` instance equal to
             * [Locale.US].
             *
             * @return An array of locales for which localized
             * `BreakIterator` instances are available.
             */
            get() {
                throw UnsupportedOperationException()
                /*val pool: LocaleServiceProviderPool =
                    LocaleServiceProviderPool.getPool(BreakIteratorProvider::class.java)
                return pool.getAvailableLocales()*/
            }
    }
}
