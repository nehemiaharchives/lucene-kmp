package org.gnit.lucenekmp.jdkport

import kotlin.math.min

/**
 * An engine that can transform a sequence of bytes in a specific charset into a sequence of
 * sixteen-bit Unicode characters.
 *
 * <a id="steps"></a>
 *
 *
 *  The input byte sequence is provided in a byte buffer or a series
 * of such buffers.  The output character sequence is written to a character buffer
 * or a series of such buffers.  A decoder should always be used by making
 * the following sequence of method invocations, hereinafter referred to as a
 * *decoding operation*:
 *
 *
 *
 *  1.
 *
 * Reset the decoder via the [reset][.reset] method, unless it
 * has not been used before;
 *
 *  1.
 *
 * Invoke the [decode][.decode] method zero or more times, as
 * long as additional input may be available, passing `false` for the
 * `endOfInput` argument and filling the input buffer and flushing the
 * output buffer between invocations;
 *
 *  1.
 *
 * Invoke the [decode][.decode] method one final time, passing
 * `true` for the `endOfInput` argument; and then
 *
 *  1.
 *
 * Invoke the [flush][.flush] method so that the decoder can
 * flush any internal state to the output buffer.
 *
 *
 *
 * Each invocation of the [decode][.decode] method will decode as many
 * bytes as possible from the input buffer, writing the resulting characters
 * to the output buffer.  The [decode][.decode] method returns when more
 * input is required, when there is not enough room in the output buffer, or
 * when a decoding error has occurred.  In each case a [CoderResult]
 * object is returned to describe the reason for termination.  An invoker can
 * examine this object and fill the input buffer, flush the output buffer, or
 * attempt to recover from a decoding error, as appropriate, and try again.
 *
 * <a id="ce"></a>
 *
 *
 *  There are two general types of decoding errors.  If the input byte
 * sequence is not legal for this charset then the input is considered *malformed*.  If
 * the input byte sequence is legal but cannot be mapped to a valid
 * Unicode character then an *unmappable character* has been encountered.
 *
 * <a id="cae"></a>
 *
 *
 *  How a decoding error is handled depends upon the action requested for
 * that type of error, which is described by an instance of the [ ] class.  The possible error actions are to [ ][CodingErrorAction.IGNORE] the erroneous input, [ ][CodingErrorAction.REPORT] the error to the invoker via
 * the returned [CoderResult] object, or [ replace][CodingErrorAction.REPLACE] the erroneous input with the current value of the
 * replacement string.  The replacement
 *
 *
 *
 *
 *
 *
 * has the initial value `"&#92;uFFFD"`;
 *
 *
 * its value may be changed via the [ replaceWith][.replaceWith] method.
 *
 *
 *  The default action for malformed-input and unmappable-character errors
 * is to [report][CodingErrorAction.REPORT] them.  The
 * malformed-input error action may be changed via the [ ][.onMalformedInput] method; the
 * unmappable-character action may be changed via the [ ][.onUnmappableCharacter] method.
 *
 *
 *  This class is designed to handle many of the details of the decoding
 * process, including the implementation of error actions.  A decoder for a
 * specific charset, which is a concrete subclass of this class, need only
 * implement the abstract [decodeLoop][.decodeLoop] method, which
 * encapsulates the basic decoding loop.  A subclass that maintains internal
 * state should, additionally, override the [implFlush][.implFlush] and
 * [implReset][.implReset] methods.
 *
 *
 *  Instances of this class are not safe for use by multiple concurrent
 * threads.
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 *
 * @see ByteBuffer
 *
 * @see CharBuffer
 *
 * @see Charset
 *
 * @see CharsetEncoder
 */
abstract class CharsetDecoder private constructor(
    cs: Charset,
    averageCharsPerByte: Float,
    maxCharsPerByte: Float,
    replacement: String
) {
    private val charset: Charset = cs
    private val averageCharsPerByte: Float
    private val maxCharsPerByte: Float

    private var replacement: String
    private var malformedInputAction
            : CodingErrorAction = CodingErrorAction.REPORT
    private var unmappableCharacterAction
            : CodingErrorAction = CodingErrorAction.REPORT

    private var state = ST_RESET

    /**
     * Initializes a new decoder.  The new decoder will have the given
     * chars-per-byte and replacement values.
     *
     * @param  cs
     * The charset that created this decoder
     *
     * @param  averageCharsPerByte
     * A positive float value indicating the expected number of
     * characters that will be produced for each input byte
     *
     * @param  maxCharsPerByte
     * A positive float value indicating the maximum number of
     * characters that will be produced for each input byte
     *
     * @param  replacement
     * The initial replacement; must not be `null`, must have
     * non-zero length, must not be longer than maxCharsPerByte,
     * and must be [legal][.isLegalReplacement]
     *
     * @throws  IllegalArgumentException
     * If the preconditions on the parameters do not hold
     */
    init {
        // Use !(a > 0.0f) rather than (a <= 0.0f) to exclude NaN values
        require(averageCharsPerByte > 0.0f) {
            ("Non-positive "
                    + "averageCharsPerByte")
        }
        // Use !(a > 0.0f) rather than (a <= 0.0f) to exclude NaN values
        require(maxCharsPerByte > 0.0f) {
            ("Non-positive "
                    + "maxCharsPerByte")
        }
        require(!(averageCharsPerByte > maxCharsPerByte)) {
            ("averageCharsPerByte"
                    + " exceeds "
                    + "maxCharsPerByte")
        }
        this.replacement = replacement
        this.averageCharsPerByte = averageCharsPerByte
        this.maxCharsPerByte = maxCharsPerByte
        replaceWith(replacement)
    }

    /**
     * Initializes a new decoder.  The new decoder will have the given
     * chars-per-byte values and its replacement will be the
     * string `"&#92;uFFFD"`.
     *
     * @param  cs
     * The charset that created this decoder
     *
     * @param  averageCharsPerByte
     * A positive float value indicating the expected number of
     * characters that will be produced for each input byte
     *
     * @param  maxCharsPerByte
     * A positive float value indicating the maximum number of
     * characters that will be produced for each input byte
     *
     * @throws  IllegalArgumentException
     * If the preconditions on the parameters do not hold
     */
    protected constructor(
        cs: Charset,
        averageCharsPerByte: Float,
        maxCharsPerByte: Float
    ) : this(
        cs,
        averageCharsPerByte, maxCharsPerByte,
        "\uFFFD"
    )

    /**
     * Returns the charset that created this decoder.
     *
     * @return  This decoder's charset
     */
    fun charset(): Charset {
        return charset
    }

    /**
     * Returns this decoder's replacement value.
     *
     * @return  This decoder's current replacement,
     * which is never `null` and is never empty
     */
    fun replacement(): String {
        return replacement
    }

    /**
     * Changes this decoder's replacement value.
     *
     *
     *  This method invokes the [implReplaceWith][.implReplaceWith]
     * method, passing the new replacement, after checking that the new
     * replacement is acceptable.
     *
     * @param  newReplacement  The new replacement; must not be
     * `null`, must have non-zero length,
     *
     * and must not be longer than the value returned by the
     * [maxCharsPerByte][.maxCharsPerByte] method
     *
     *
     *
     *
     *
     *
     *
     * @return  This decoder
     *
     * @throws  IllegalArgumentException
     * If the preconditions on the parameter do not hold
     */
    fun replaceWith(newReplacement: String): CharsetDecoder {
        requireNotNull(newReplacement) { "Null replacement" }
        val len = newReplacement.length
        require(len != 0) { "Empty replacement" }
        require(!(len > maxCharsPerByte)) { "Replacement too long" }

        this.replacement = newReplacement






        implReplaceWith(this.replacement)
        return this
    }

    /**
     * Reports a change to this decoder's replacement value.
     *
     *
     *  The default implementation of this method does nothing.  This method
     * should be overridden by decoders that require notification of changes to
     * the replacement.
     *
     * @param  newReplacement    The replacement value
     */
    protected open fun implReplaceWith(newReplacement: String) {
    }


    /**
     * Returns this decoder's current action for malformed-input errors.
     *
     * @return The current malformed-input action, which is never `null`
     */
    fun malformedInputAction(): CodingErrorAction {
        return malformedInputAction
    }

    /**
     * Changes this decoder's action for malformed-input errors.
     *
     *
     *  This method invokes the [ implOnMalformedInput][.implOnMalformedInput] method, passing the new action.
     *
     * @param  newAction  The new action; must not be `null`
     *
     * @return  This decoder
     *
     * @throws IllegalArgumentException
     * If the precondition on the parameter does not hold
     */
    fun onMalformedInput(newAction: CodingErrorAction): CharsetDecoder {
        requireNotNull(newAction) { "Null action" }
        malformedInputAction = newAction
        implOnMalformedInput(newAction)
        return this
    }

    /**
     * Reports a change to this decoder's malformed-input action.
     *
     *
     *  The default implementation of this method does nothing.  This method
     * should be overridden by decoders that require notification of changes to
     * the malformed-input action.
     *
     * @param  newAction  The new action
     */
    protected fun implOnMalformedInput(newAction: CodingErrorAction) {}

    /**
     * Returns this decoder's current action for unmappable-character errors.
     *
     * @return The current unmappable-character action, which is never
     * `null`
     */
    fun unmappableCharacterAction(): CodingErrorAction {
        return unmappableCharacterAction
    }

    /**
     * Changes this decoder's action for unmappable-character errors.
     *
     *
     *  This method invokes the [ implOnUnmappableCharacter][.implOnUnmappableCharacter] method, passing the new action.
     *
     * @param  newAction  The new action; must not be `null`
     *
     * @return  This decoder
     *
     * @throws IllegalArgumentException
     * If the precondition on the parameter does not hold
     */
    fun onUnmappableCharacter(newAction: CodingErrorAction): CharsetDecoder {
        requireNotNull(newAction) { "Null action" }
        unmappableCharacterAction = newAction
        implOnUnmappableCharacter(newAction)
        return this
    }

    /**
     * Reports a change to this decoder's unmappable-character action.
     *
     *
     *  The default implementation of this method does nothing.  This method
     * should be overridden by decoders that require notification of changes to
     * the unmappable-character action.
     *
     * @param  newAction  The new action
     */
    protected fun implOnUnmappableCharacter(newAction: CodingErrorAction) {}

    /**
     * Returns the average number of characters that will be produced for each
     * byte of input.  This heuristic value may be used to estimate the size
     * of the output buffer required for a given input sequence.
     *
     * @return  The average number of characters produced
     * per byte of input
     */
    fun averageCharsPerByte(): Float {
        return averageCharsPerByte
    }

    /**
     * Returns the maximum number of characters that will be produced for each
     * byte of input.  This value may be used to compute the worst-case size
     * of the output buffer required for a given input sequence. This value
     * accounts for any necessary content-independent prefix or suffix
     *
     *
     *
     *
     * characters.
     *
     *
     * @return  The maximum number of characters that will be produced per
     * byte of input
     */
    fun maxCharsPerByte(): Float {
        return maxCharsPerByte
    }

    /**
     * Decodes as many bytes as possible from the given input buffer,
     * writing the results to the given output buffer.
     *
     *
     *  The buffers are read from, and written to, starting at their current
     * positions.  At most [in.remaining()][Buffer.remaining] bytes
     * will be read and at most [out.remaining()][Buffer.remaining]
     * characters will be written.  The buffers' positions will be advanced to
     * reflect the bytes read and the characters written, but their marks and
     * limits will not be modified.
     *
     *
     *  In addition to reading bytes from the input buffer and writing
     * characters to the output buffer, this method returns a [CoderResult]
     * object to describe its reason for termination:
     *
     *
     *
     *  *
     *
     * [CoderResult.UNDERFLOW] indicates that as much of the
     * input buffer as possible has been decoded.  If there is no further
     * input then the invoker can proceed to the next step of the
     * [decoding operation](#steps).  Otherwise this method
     * should be invoked again with further input.
     *
     *  *
     *
     * [CoderResult.OVERFLOW] indicates that there is
     * insufficient space in the output buffer to decode any more bytes.
     * This method should be invoked again with an output buffer that has
     * more [remaining][Buffer.remaining] characters. This is
     * typically done by draining any decoded characters from the output
     * buffer.
     *
     *  *
     *
     * A [   malformed-input][CoderResult.malformedForLength] result indicates that a malformed-input
     * error has been detected.  The malformed bytes begin at the input
     * buffer's (possibly incremented) position; the number of malformed
     * bytes may be determined by invoking the result object's [   ][CoderResult.length] method.  This case applies only if the
     * [malformed action][.onMalformedInput] of this decoder
     * is [CodingErrorAction.REPORT]; otherwise the malformed input
     * will be ignored or replaced, as requested.
     *
     *  *
     *
     * An [   unmappable-character][CoderResult.unmappableForLength] result indicates that an
     * unmappable-character error has been detected.  The bytes that
     * decode the unmappable character begin at the input buffer's (possibly
     * incremented) position; the number of such bytes may be determined
     * by invoking the result object's [length][CoderResult.length]
     * method.  This case applies only if the [   unmappable action][.onUnmappableCharacter] of this decoder is [   ][CodingErrorAction.REPORT]; otherwise the unmappable character will be
     * ignored or replaced, as requested.
     *
     *
     *
     * In any case, if this method is to be reinvoked in the same decoding
     * operation then care should be taken to preserve any bytes remaining
     * in the input buffer so that they are available to the next invocation.
     *
     *
     *  The `endOfInput` parameter advises this method as to whether
     * the invoker can provide further input beyond that contained in the given
     * input buffer.  If there is a possibility of providing additional input
     * then the invoker should pass `false` for this parameter; if there
     * is no possibility of providing further input then the invoker should
     * pass `true`.  It is not erroneous, and in fact it is quite
     * common, to pass `false` in one invocation and later discover that
     * no further input was actually available.  It is critical, however, that
     * the final invocation of this method in a sequence of invocations always
     * pass `true` so that any remaining undecoded input will be treated
     * as being malformed.
     *
     *
     *  This method works by invoking the [decodeLoop][.decodeLoop]
     * method, interpreting its results, handling error conditions, and
     * reinvoking it as necessary.
     *
     *
     * @param  in
     * The input byte buffer
     *
     * @param  out
     * The output character buffer
     *
     * @param  endOfInput
     * `true` if, and only if, the invoker can provide no
     * additional input bytes beyond those in the given buffer
     *
     * @return  A coder-result object describing the reason for termination
     *
     * @throws  IllegalStateException
     * If a decoding operation is already in progress and the previous
     * step was an invocation neither of the [reset][.reset]
     * method, nor of this method with a value of `false` for
     * the `endOfInput` parameter, nor of this method with a
     * value of `true` for the `endOfInput` parameter
     * but a return value indicating an incomplete decoding operation
     *
     * @throws  CoderMalfunctionError
     * If an invocation of the decodeLoop method threw
     * an unexpected exception
     */
    fun decode(
        `in`: ByteBuffer, out: CharBuffer,
        endOfInput: Boolean
    ): CoderResult {
        val newState = if (endOfInput) ST_END else ST_CODING
        if ((state != ST_RESET) && (state != ST_CODING)
            && !(endOfInput && (state == ST_END))
        ) throwIllegalStateException(state, newState)
        state = newState

        while (true) {
            var cr: CoderResult?
            try {
                cr = decodeLoop(`in`, out)
            } catch (x: RuntimeException) {
                throw CoderMalfunctionError(x)
            }

            if (cr!!.isOverflow) return cr

            if (cr.isUnderflow) {
                if (endOfInput && `in`.hasRemaining()) {
                    cr = CoderResult.malformedForLength(`in`.remaining())
                    // Fall through to malformed-input case
                } else {
                    return cr
                }
            }

            var action: CodingErrorAction? = null
            if (cr!!.isMalformed) action = malformedInputAction
            else if (cr.isUnmappable) action = unmappableCharacterAction
            else require(false) { cr.toString() }

            if (action === CodingErrorAction.REPORT) return cr

            if (action === CodingErrorAction.REPLACE) {
                if (out.remaining() < replacement.length) return CoderResult.OVERFLOW
                out.put(replacement)
            }

            if ((action === CodingErrorAction.IGNORE)
                || (action === CodingErrorAction.REPLACE)
            ) {
                // Skip erroneous input either way
                `in`.position(`in`.position + cr.length())
                continue
            }

            require(false)
        }
    }

    /**
     * Flushes this decoder.
     *
     *
     *  Some decoders maintain internal state and may need to write some
     * final characters to the output buffer once the overall input sequence has
     * been read.
     *
     *
     *  Any additional output is written to the output buffer beginning at
     * its current position.  At most [out.remaining()][Buffer.remaining]
     * characters will be written.  The buffer's position will be advanced
     * appropriately, but its mark and limit will not be modified.
     *
     *
     *  If this method completes successfully then it returns [ ][CoderResult.UNDERFLOW].  If there is insufficient room in the output
     * buffer then it returns [CoderResult.OVERFLOW].  If this happens
     * then this method must be invoked again, with an output buffer that has
     * more room, in order to complete the current [decoding
 * operation](#steps).
     *
     *
     *  If this decoder has already been flushed then invoking this method
     * has no effect.
     *
     *
     *  This method invokes the [implFlush][.implFlush] method to
     * perform the actual flushing operation.
     *
     * @param  out
     * The output character buffer
     *
     * @return  A coder-result object, either [CoderResult.UNDERFLOW] or
     * [CoderResult.OVERFLOW]
     *
     * @throws  IllegalStateException
     * If the previous step of the current decoding operation was an
     * invocation neither of the [flush][.flush] method nor of
     * the three-argument [          ][.decode] method
     * with a value of `true` for the `endOfInput`
     * parameter
     */
    fun flush(out: CharBuffer): CoderResult {
        if (state == ST_END) {
            val cr: CoderResult = implFlush(out)
            if (cr.isUnderflow) state = ST_FLUSHED
            return cr
        }

        if (state != ST_FLUSHED) throwIllegalStateException(state, ST_FLUSHED)

        return CoderResult.UNDERFLOW // Already flushed
    }

    /**
     * Flushes this decoder.
     *
     *
     *  The default implementation of this method does nothing, and always
     * returns [CoderResult.UNDERFLOW].  This method should be overridden
     * by decoders that may need to write final characters to the output buffer
     * once the entire input sequence has been read.
     *
     * @param  out
     * The output character buffer
     *
     * @return  A coder-result object, either [CoderResult.UNDERFLOW] or
     * [CoderResult.OVERFLOW]
     */
    protected open fun implFlush(out: CharBuffer): CoderResult {
        return CoderResult.UNDERFLOW
    }

    /**
     * Resets this decoder, clearing any internal state.
     *
     *
     *  This method resets charset-independent state and also invokes the
     * [implReset][.implReset] method in order to perform any
     * charset-specific reset actions.
     *
     * @return  This decoder
     */
    fun reset(): CharsetDecoder {
        implReset()
        state = ST_RESET
        return this
    }

    /**
     * Resets this decoder, clearing any charset-specific internal state.
     *
     *
     *  The default implementation of this method does nothing.  This method
     * should be overridden by decoders that maintain internal state.
     */
    protected open fun implReset() {}

    /**
     * Decodes one or more bytes into one or more characters.
     *
     *
     *  This method encapsulates the basic decoding loop, decoding as many
     * bytes as possible until it either runs out of input, runs out of room
     * in the output buffer, or encounters a decoding error.  This method is
     * invoked by the [decode][.decode] method, which handles result
     * interpretation and error recovery.
     *
     *
     *  The buffers are read from, and written to, starting at their current
     * positions.  At most [in.remaining()][Buffer.remaining] bytes
     * will be read, and at most [out.remaining()][Buffer.remaining]
     * characters will be written.  The buffers' positions will be advanced to
     * reflect the bytes read and the characters written, but their marks and
     * limits will not be modified.
     *
     *
     *  This method returns a [CoderResult] object to describe its
     * reason for termination, in the same manner as the [decode][.decode]
     * method.  Most implementations of this method will handle decoding errors
     * by returning an appropriate result object for interpretation by the
     * [decode][.decode] method.  An optimized implementation may instead
     * examine the relevant error action and implement that action itself.
     *
     *
     *  An implementation of this method may perform arbitrary lookahead by
     * returning [CoderResult.UNDERFLOW] until it receives sufficient
     * input.
     *
     * @param  in
     * The input byte buffer
     *
     * @param  out
     * The output character buffer
     *
     * @return  A coder-result object describing the reason for termination
     */
    protected abstract fun decodeLoop(
        `in`: ByteBuffer,
        out: CharBuffer
    ): CoderResult?

    /**
     * Convenience method that decodes the remaining content of a single input
     * byte buffer into a newly-allocated character buffer.
     *
     *
     *  This method implements an entire [decoding
 * operation](#steps); that is, it resets this decoder, then it decodes the
     * bytes in the given byte buffer, and finally it flushes this
     * decoder.  This method should therefore not be invoked if a decoding
     * operation is already in progress.
     *
     * @param  in
     * The input byte buffer
     *
     * @return A newly-allocated character buffer containing the result of the
     * decoding operation.  The buffer's position will be zero and its
     * limit will follow the last character written.
     *
     * @throws  IllegalStateException
     * If a decoding operation is already in progress
     *
     * @throws  MalformedInputException
     * If the byte sequence starting at the input buffer's current
     * position is not legal for this charset and the current malformed-input action
     * is [CodingErrorAction.REPORT]
     *
     * @throws  UnmappableCharacterException
     * If the byte sequence starting at the input buffer's current
     * position cannot be mapped to an equivalent character sequence and
     * the current unmappable-character action is [          ][CodingErrorAction.REPORT]
     *
     * @throws  CharacterCodingException
     * `MalformedInputException` if the byte sequence starting at the
     * input buffer's current position is not legal for this charset and the current
     * malformed-input action is `CodingErrorAction.REPORT`;
     * `UnmappableCharacterException` if the byte sequence starting at
     * the input buffer's current position cannot be mapped to an
     * equivalent character sequence and the current unmappable-character
     * action is `CodingErrorAction.REPORT`
     *
     * @throws  OutOfMemoryError
     * If the output character buffer for the requested size of the input
     * byte buffer cannot be allocated
     */
    @Throws(CharacterCodingException::class)
    fun decode(`in`: ByteBuffer): CharBuffer {
        var n = min(
            (`in`.remaining() * averageCharsPerByte()).toInt(),
            ArraysSupport.SOFT_MAX_ARRAY_LENGTH
        )
        var out: CharBuffer = CharBuffer.allocate(n)

        if ((n == 0) && (`in`.remaining() == 0)) return out
        reset()
        while (true) {
            var cr: CoderResult =
                if (`in`.hasRemaining()) decode(`in`, out, true) else CoderResult.UNDERFLOW
            if (cr.isUnderflow) cr = flush(out)

            if (cr.isUnderflow) break
            if (cr.isOverflow) {
                // Ensure progress; n might be 0!
                n = ArraysSupport.newLength(n, min(n + 1, 1024), n + 1)
                val o: CharBuffer = CharBuffer.allocate(n)
                out.flip()
                o.put(out)
                out = o
                continue
            }
            cr.throwException()
        }
        out.flip()
        return out
    }


    open val isAutoDetecting: Boolean
        /**
         * Tells whether or not this decoder implements an auto-detecting charset.
         *
         *
         *  The default implementation of this method always returns
         * `false`; it should be overridden by auto-detecting decoders to
         * return `true`.
         *
         * @return  `true` if, and only if, this decoder implements an
         * auto-detecting charset
         */
        get() = false

    open val isCharsetDetected: Boolean
        /**
         * Tells whether or not this decoder has yet detected a
         * charset&nbsp;&nbsp;*(optional operation)*.
         *
         *
         *  If this decoder implements an auto-detecting charset then at a
         * single point during a decoding operation this method may start returning
         * `true` to indicate that a specific charset has been detected in
         * the input byte sequence.  Once this occurs, the [ detectedCharset][.detectedCharset] method may be invoked to retrieve the detected charset.
         *
         *
         *  That this method returns `false` does not imply that no bytes
         * have yet been decoded.  Some auto-detecting decoders are capable of
         * decoding some, or even all, of an input byte sequence without fixing on
         * a particular charset.
         *
         *
         *  The default implementation of this method always throws an [ ]; it should be overridden by
         * auto-detecting decoders to return `true` once the input charset
         * has been determined.
         *
         * @return  `true` if, and only if, this decoder has detected a
         * specific charset
         *
         * @throws  UnsupportedOperationException
         * If this decoder does not implement an auto-detecting charset
         */
        get() {
            throw UnsupportedOperationException()
        }

    /**
     * Retrieves the charset that was detected by this
     * decoder&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  If this decoder implements an auto-detecting charset then this
     * method returns the actual charset once it has been detected.  After that
     * point, this method returns the same value for the duration of the
     * current decoding operation.  If not enough input bytes have yet been
     * read to determine the actual charset then this method throws an [ ].
     *
     *
     *  The default implementation of this method always throws an [ ]; it should be overridden by
     * auto-detecting decoders to return the appropriate value.
     *
     * @return  The charset detected by this auto-detecting decoder,
     * or `null` if the charset has not yet been determined
     *
     * @throws  IllegalStateException
     * If insufficient bytes have been read to determine a charset
     *
     * @throws  UnsupportedOperationException
     * If this decoder does not implement an auto-detecting charset
     */
    open fun detectedCharset(): Charset {
        throw UnsupportedOperationException()
    }


    private fun throwIllegalStateException(from: Int, to: Int) {
        throw IllegalStateException(
            ("Current state = " + stateNames[from]
                    + ", new state = " + stateNames[to])
        )
    }

    companion object {
        // Internal states
        //
        private const val ST_RESET = 0
        private const val ST_CODING = 1
        private const val ST_END = 2
        private const val ST_FLUSHED = 3

        private val stateNames: Array<String> = arrayOf<String>("RESET", "CODING", "CODING_END", "FLUSHED")
    }
}
