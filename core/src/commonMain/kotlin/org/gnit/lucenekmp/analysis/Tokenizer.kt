package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.jdkport.Reader


/**
 * A Tokenizer is a TokenStream whose input is a Reader.
 *
 *
 * This is an abstract class; subclasses must override [.incrementToken]
 *
 *
 * NOTE: Subclasses overriding [.incrementToken] must call [ ][AttributeSource.clearAttributes] before setting attributes.
 */
abstract class Tokenizer : TokenStream {
    /** The text source for this Tokenizer.  */
    protected var input: Reader = ILLEGAL_STATE_READER

    /** Pending reader: not actually assigned to input until reset()  */
    private var inputPending: Reader = ILLEGAL_STATE_READER

    /**
     * Construct a tokenizer with no input, awaiting a call to [.setReader] to
     * provide input.
     */
    protected constructor()

    /**
     * Construct a tokenizer with no input, awaiting a call to [.setReader] to
     * provide input.
     *
     * @param factory attribute factory.
     */
    protected constructor(factory: AttributeFactory) : super(factory)

    /**
     * {@inheritDoc}
     *
     *
     * **NOTE:** The default implementation closes the input Reader, so be sure to call `
     * super.close()` when overriding this method.
     */
    override fun close() {
        input.close()
        // LUCENE-2387: don't hold onto Reader after close, so
        // GC can reclaim
        input = ILLEGAL_STATE_READER
        inputPending = input
    }

    /**
     * Return the corrected offset. If [.input] is a [CharFilter] subclass this method
     * calls [CharFilter.correctOffset], else returns `currentOff`.
     *
     * @param currentOff offset as seen in the output
     * @return corrected offset based on the input
     * @see CharFilter.correctOffset
     */
    protected fun correctOffset(currentOff: Int): Int {
        return if (input is CharFilter)
            (input as CharFilter).correctOffset(currentOff)
        else
            currentOff
    }

    /**
     * Expert: Set a new reader on the Tokenizer. Typically, an analyzer (in its tokenStream method)
     * will use this to re-use a previously created tokenizer.
     */
    fun setReader(input: Reader) {
        check(this.input === ILLEGAL_STATE_READER) { "TokenStream contract violation: close() call missing" }
        this.inputPending = input
        setReaderTestPoint()
    }

    override fun reset() {
        super.reset()
        input = inputPending
        inputPending = ILLEGAL_STATE_READER
    }

    /**
     * @lucene.internal
     */
    protected fun setReaderTestPoint() {}

    companion object {
        private val ILLEGAL_STATE_READER: Reader = object : Reader() {
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                throw IllegalStateException(
                    ("TokenStream contract violation: reset()/close() call missing, "
                            + "reset() called multiple times, or subclass does not call super.reset(). "
                            + "Please see Javadocs of TokenStream class for more information about the correct consuming workflow.")
                )
            }

            override fun close() {}
        }
    }
}
