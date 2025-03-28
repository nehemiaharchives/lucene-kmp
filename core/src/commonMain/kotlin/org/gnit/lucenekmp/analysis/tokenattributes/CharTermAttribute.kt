package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute


/** The term text of a Token.  */
interface CharTermAttribute : Attribute, CharSequence, Appendable {
    /**
     * Copies the contents of buffer, starting at offset for length characters, into the termBuffer
     * array.
     *
     * @param buffer the buffer to copy
     * @param offset the index in the buffer of the first character to copy
     * @param length the number of characters to copy
     */
    fun copyBuffer(buffer: CharArray, offset: Int, length: Int)

    /**
     * Returns the internal termBuffer character array which you can then directly alter. If the array
     * is too small for your token, use [.resizeBuffer] to increase it. After altering the
     * buffer be sure to call [.setLength] to record the number of valid characters that were
     * placed into the termBuffer.
     *
     *
     * **NOTE**: The returned buffer may be larger than the valid [.length].
     */
    fun buffer(): CharArray

    /**
     * Grows the termBuffer to at least size newSize, preserving the existing content.
     *
     * @param newSize minimum size of the new termBuffer
     * @return newly created termBuffer with `length >= newSize`
     */
    fun resizeBuffer(newSize: Int): CharArray

    /**
     * Set number of valid characters (length of the term) in the termBuffer array. Use this to
     * truncate the termBuffer or to synchronize with external manipulation of the termBuffer. Note:
     * to grow the size of the array, use [.resizeBuffer] first.
     *
     * @param length the truncated length
     */
    fun setLength(length: Int): CharTermAttribute

    /**
     * Sets the length of the termBuffer to zero. Use this method before appending contents using the
     * [Appendable] interface.
     */
    fun setEmpty(): CharTermAttribute?

    // the following methods are redefined to get rid of IOException declaration:
    override fun append(csq: CharSequence?): CharTermAttribute

    override fun append(csq: CharSequence?, start: Int, end: Int): CharTermAttribute

    override fun append(c: Char): CharTermAttribute

    /**
     * Appends the specified `String` to this character sequence.
     *
     *
     * The characters of the `String` argument are appended, in order, increasing the length
     * of this sequence by the length of the argument. If argument is `null`, then the four
     * characters `"null"` are appended.
     */
    fun append(s: String): CharTermAttribute

    /**
     * Appends the specified `StringBuilder` to this character sequence.
     *
     *
     * The characters of the `StringBuilder` argument are appended, in order, increasing the
     * length of this sequence by the length of the argument. If argument is `null`, then the
     * four characters `"null"` are appended.
     */
    fun append(sb: StringBuilder): CharTermAttribute

    /**
     * Appends the contents of the other `CharTermAttribute` to this character sequence.
     *
     *
     * The characters of the `CharTermAttribute` argument are appended, in order, increasing
     * the length of this sequence by the length of the argument. If argument is `null`, then
     * the four characters `"null"` are appended.
     */
    fun append(termAtt: CharTermAttribute): CharTermAttribute
}
