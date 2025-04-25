package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOBooleanSupplier


/**
 * Abstract class for enumerating a subset of all terms.
 *
 *
 * Term enumerations are always ordered by [BytesRef.compareTo]. Each term in the
 * enumeration is greater than all that precede it.
 *
 *
 * *Please note:* Consumers of this enum cannot call `seek()`, it is forward only;
 * it throws [UnsupportedOperationException] when a seeking method is called.
 */
abstract class FilteredTermsEnum protected constructor(tenum: TermsEnum, startWithSeek: Boolean) : TermsEnum() {
    private var initialSeekTerm: BytesRef? = null
    private var doSeek: Boolean

    /** Which term the enum is currently positioned to.  */
    protected var actualTerm: BytesRef? = null

    /** The delegate [TermsEnum].  */
    protected val tenum: TermsEnum

    /**
     * Return value, if term should be accepted or the iteration should `END`. The `*_SEEK` values denote,
     * that after handling the current term the enum should call [ ][.nextSeekTerm] and step forward.
     *
     * @see .accept
     */
    protected enum class AcceptStatus {
        /** Accept the term and position the enum at the next term.  */
        YES,

        /**
         * Accept the term and advance ([FilteredTermsEnum.nextSeekTerm]) to the next
         * term.
         */
        YES_AND_SEEK,

        /** Reject the term and position the enum at the next term.  */
        NO,

        /**
         * Reject the term and advance ([FilteredTermsEnum.nextSeekTerm]) to the next
         * term.
         */
        NO_AND_SEEK,

        /** Reject the term and stop enumerating.  */
        END
    }

    /** Return if term is accepted, not accepted or the iteration should ended (and possibly seek).  */
    @Throws(IOException::class)
    protected abstract fun accept(term: BytesRef): AcceptStatus

    /**
     * Creates a filtered [TermsEnum] on a terms enum.
     *
     * @param tenum the terms enumeration to filter.
     */
    protected constructor(tenum: TermsEnum) : this(tenum, true)

    /**
     * Creates a filtered [TermsEnum] on a terms enum.
     *
     * @param tenum the terms enumeration to filter.
     */
    init {
        checkNotNull(tenum)
        this.tenum = tenum
        doSeek = startWithSeek
    }

    /**
     * Use this method to set the initial [BytesRef] to seek before iterating. This is a
     * convenience method for subclasses that do not override [.nextSeekTerm]. If the initial
     * seek term is `null` (default), the enum is empty.
     *
     *
     * You can only use this method, if you keep the default implementation of [ ][.nextSeekTerm].
     */
    protected fun setInitialSeekTerm(term: BytesRef?) {
        this.initialSeekTerm = term
    }

    /**
     * On the first call to [.next] or if [.accept] returns [ ][AcceptStatus.YES_AND_SEEK] or
     * [AcceptStatus.NO_AND_SEEK], this method will be called to
     * eventually seek the underlying TermsEnum to a new position. On the first call,
     * `currentTerm` will be `null`, later calls will provide the term the underlying enum is
     * positioned at. This method returns per default only one time the initial seek term and then
     * `null`, so no repositioning is ever done.
     *
     *
     * Override this method, if you want a more sophisticated TermsEnum, that repositions the
     * iterator during enumeration. If this method always returns `null` the enum is empty.
     *
     *
     * *Please note:* This method should always provide a greater term than the last
     * enumerated term, else the behaviour of this enum violates the contract for TermsEnums.
     */
    @Throws(IOException::class)
    protected open fun nextSeekTerm(currentTerm: BytesRef?): BytesRef? {
        val t: BytesRef? = initialSeekTerm
        initialSeekTerm = null
        return t
    }

    /**
     * Returns the related attributes, the returned [AttributeSource] is shared with the
     * delegate `TermsEnum`.
     */
    override fun attributes(): AttributeSource {
        return tenum.attributes()
    }

    @Throws(IOException::class)
    override fun term(): BytesRef? {
        return tenum.term()
    }

    @Throws(IOException::class)
    override fun docFreq(): Int {
        return tenum.docFreq()
    }

    @Throws(IOException::class)
    override fun totalTermFreq(): Long {
        return tenum.totalTermFreq()
    }

    /**
     * This enum does not support seeking!
     *
     * @throws UnsupportedOperationException In general, subclasses do not support seeking.
     */
    @Throws(IOException::class)
    override fun seekExact(term: BytesRef): Boolean {
        throw UnsupportedOperationException(this::class.qualifiedName + " does not support seeking")
    }

    /**
     * This enum does not support seeking!
     *
     * @throws UnsupportedOperationException In general, subclasses do not support seeking.
     */
    @Throws(IOException::class)
    override fun prepareSeekExact(text: BytesRef): IOBooleanSupplier {
        throw UnsupportedOperationException(this::class.qualifiedName + " does not support seeking")
    }

    /**
     * This enum does not support seeking!
     *
     * @throws UnsupportedOperationException In general, subclasses do not support seeking.
     */
    @Throws(IOException::class)
    override fun seekCeil(term: BytesRef): SeekStatus {
        throw UnsupportedOperationException(this::class.qualifiedName + " does not support seeking")
    }

    /**
     * This enum does not support seeking!
     *
     * @throws UnsupportedOperationException In general, subclasses do not support seeking.
     */
    @Throws(IOException::class)
    override fun seekExact(ord: Long) {
        throw UnsupportedOperationException(this::class.qualifiedName + " does not support seeking")
    }

    @Throws(IOException::class)
    override fun ord(): Long {
        return tenum.ord()
    }

    @Throws(IOException::class)
    override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
        return tenum.postings(reuse, flags)
    }

    @Throws(IOException::class)
    override fun impacts(flags: Int): ImpactsEnum {
        return tenum.impacts(flags)
    }

    /**
     * This enum does not support seeking!
     *
     * @throws UnsupportedOperationException In general, subclasses do not support seeking.
     */
    @Throws(IOException::class)
    override fun seekExact(term: BytesRef, state: TermState) {
        throw UnsupportedOperationException(this::class.qualifiedName + " does not support seeking")
    }

    /** Returns the filtered enums term state  */
    @Throws(IOException::class)
    override fun termState(): TermState {
        checkNotNull(tenum)
        return tenum.termState()
    }

    @Throws(IOException::class)
    override fun next(): BytesRef? {
        // System.out.println("FTE.next doSeek=" + doSeek);
        // new Throwable().printStackTrace(System.out);
        while (true) {
            // Seek or forward the iterator
            if (doSeek) {
                doSeek = false
                val t: BytesRef? = nextSeekTerm(actualTerm)
                // System.out.println("  seek to t=" + (t == null ? "null" : t.utf8ToString()) + " tenum=" +
                // tenum);
                // Make sure we always seek forward:
                require(
                    actualTerm == null || t == null || t > actualTerm!!
                ) { "curTerm=$actualTerm seekTerm=$t" }
                if (t == null || tenum.seekCeil(t) === SeekStatus.END) {
                    // no more terms to seek to or enum exhausted
                    // System.out.println("  return null");
                    return null
                }
                actualTerm = tenum.term()
                // System.out.println("  got term=" + actualTerm.utf8ToString());
            } else {
                actualTerm = tenum.next()
                if (actualTerm == null) {
                    // enum exhausted
                    return null
                }
            }

            // check if term is accepted
            when (accept(actualTerm!!)) {
                AcceptStatus.YES_AND_SEEK -> {
                    doSeek = true
                    // term accepted
                    return actualTerm
                }

                AcceptStatus.YES ->
                    return actualTerm

                AcceptStatus.NO_AND_SEEK ->           // invalid term, seek next time
                    doSeek = true

                AcceptStatus.END ->           // we are supposed to end the enum
                    return null

                AcceptStatus.NO -> {}
                null -> return null
            }
        }
    }
}
