package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.Reader


/**
 * Subclasses of CharFilter can be chained to filter a Reader They can be used as [ ] with additional offset correction. [Tokenizer]s will automatically use
 * [.correctOffset] if a CharFilter subclass is used.
 *
 *
 * This class is abstract: at a minimum you must implement [.read],
 * transforming the input in some way from [.input], and [.correct] to adjust the
 * offsets to match the originals.
 *
 *
 * You can optionally provide more efficient implementations of additional methods like [ ][.read], [.read], [.read], but this is not required.
 *
 *
 * For examples and integration with [Analyzer], see the [ Analysis package documentation][org.apache.lucene.analysis].
 */
// the way java.io.FilterReader should work!
abstract class CharFilter(input: Reader) : Reader() {
    /** The underlying character-input stream.  */
    protected val input: Reader = input

    /**
     * Closes the underlying input stream.
     *
     *
     * **NOTE:** The default implementation closes the input Reader, so be sure to call `
     * super.close()` when overriding this method.
     */
    override fun close() {
        input.close()
    }

    /**
     * Subclasses override to correct the current offset.
     *
     * @param currentOff current offset
     * @return corrected offset
     */
    protected abstract fun correct(currentOff: Int): Int

    /** Chains the corrected offset through the input CharFilter(s).  */
    fun correctOffset(currentOff: Int): Int {
        val corrected = correct(currentOff)
        return if (input is CharFilter)
            input.correctOffset(corrected)
        else
            corrected
    }
}
