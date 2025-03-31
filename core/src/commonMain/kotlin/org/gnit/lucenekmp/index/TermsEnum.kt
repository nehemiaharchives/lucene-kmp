package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.IOBooleanSupplier


/**
 * Iterator to seek ([.seekCeil], [.seekExact]) or step through
 * ([.next] terms to obtain frequency information ([.docFreq]), [PostingsEnum] or
 * [PostingsEnum] for the current term ([.postings].
 *
 *
 * Term enumerations are always ordered by BytesRef.compareTo, which is Unicode sort order if the
 * terms are UTF-8 bytes. Each term in the enumeration is greater than the one before it.
 *
 *
 * The TermsEnum is unpositioned when you first obtain it and you must first successfully call
 * [.next] or one of the `seek` methods.
 *
 * @lucene.experimental
 */
abstract class TermsEnum
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : BytesRefIterator {
    /** Returns the related attributes.  */
    abstract fun attributes(): AttributeSource

    /** Represents returned result from [.seekCeil].  */
    enum class SeekStatus {
        /** The term was not found, and the end of iteration was hit.  */
        END,

        /** The precise term was found.  */
        FOUND,

        /** A different term was found after the requested term  */
        NOT_FOUND
    }

    /**
     * Attempts to seek to the exact term, returning true if the term is found. If this returns false,
     * the enum is unpositioned. For some codecs, seekExact may be substantially faster than [ ][.seekCeil].
     *
     * @return true if the term is found; return false if the enum is unpositioned.
     */
    @Throws(IOException::class)
    abstract fun seekExact(text: BytesRef): Boolean

    /**
     * Two-phase [.seekExact]. The first phase typically calls [IndexInput.prefetch] on
     * the right range of bytes under the hood, while the second phase [IOBooleanSupplier.get]
     * actually seeks the term within these bytes. This can be used to parallelize I/O across multiple
     * terms by calling [.prepareSeekExact] on multiple terms enums before calling [ ][IOBooleanSupplier.get].
     *
     *
     * **NOTE**: It is illegal to call other methods on this [TermsEnum] after calling
     * this method until [IOBooleanSupplier.get] is called.
     *
     *
     * **NOTE**: This may return `null` if this [TermsEnum] can identify that the
     * term may not exist without performing any I/O.
     *
     *
     * **NOTE**: The returned [IOBooleanSupplier] must be consumed in the same thread.
     */
    @Throws(IOException::class)
    abstract fun prepareSeekExact(text: BytesRef): IOBooleanSupplier

    /**
     * Seeks to the specified term, if it exists, or to the next (ceiling) term. Returns SeekStatus to
     * indicate whether exact term was found, a different term was found, or EOF was hit. The target
     * term may be before or after the current term. If this returns SeekStatus.END, the enum is
     * unpositioned.
     */
    @Throws(IOException::class)
    abstract fun seekCeil(text: BytesRef): SeekStatus

    /**
     * Seeks to the specified term by ordinal (position) as previously returned by [.ord]. The
     * target ord may be before or after the current ord, and must be within bounds.
     */
    @Throws(IOException::class)
    abstract fun seekExact(ord: Long)

    /**
     * Expert: Seeks a specific position by [TermState] previously obtained from [ ][.termState]. Callers should maintain the [TermState] to use this method. Low-level
     * implementations may position the TermsEnum without re-seeking the term dictionary.
     *
     *
     * Seeking by [TermState] should only be used iff the state was obtained from the same
     * [TermsEnum] instance.
     *
     *
     * NOTE: Using this method with an incompatible [TermState] might leave this [ ] in undefined state. On a segment level [TermState] instances are compatible
     * only iff the source and the target [TermsEnum] operate on the same field. If operating on
     * segment level, TermState instances must not be used across segments.
     *
     *
     * NOTE: A seek by [TermState] might not restore the [AttributeSource]'s state.
     * [AttributeSource] states must be maintained separately if this method is used.
     *
     * @param term the term the TermState corresponds to
     * @param state the [TermState]
     */
    @Throws(IOException::class)
    abstract fun seekExact(term: BytesRef, state: TermState)

    /** Returns current term. Do not call this when the enum is unpositioned.  */
    @Throws(IOException::class)
    abstract fun term(): BytesRef?

    /**
     * Returns ordinal position for current term. This is an optional method (the codec may throw
     * [UnsupportedOperationException]). Do not call this when the enum is unpositioned.
     */
    @Throws(IOException::class)
    abstract fun ord(): Long

    /**
     * Returns the number of documents containing the current term. Do not call this when the enum is
     * unpositioned. [SeekStatus.END].
     */
    @Throws(IOException::class)
    abstract fun docFreq(): Int

    /**
     * Returns the total number of occurrences of this term across all documents (the sum of the
     * freq() for each doc that has this term). Note that, like other term measures, this measure does
     * not take deleted documents into account.
     */
    @Throws(IOException::class)
    abstract fun totalTermFreq(): Long

    /**
     * Get [PostingsEnum] for the current term. Do not call this when the enum is unpositioned.
     * This method will not return null.
     *
     *
     * **NOTE**: the returned iterator may return deleted documents, so deleted documents have
     * to be checked on top of the [PostingsEnum].
     *
     *
     * Use this method if you only require documents and frequencies, and do not need any proximity
     * data. This method is equivalent to [postings(reuse,][.postings]
     *
     * @param reuse pass a prior PostingsEnum for possible reuse
     * @see .postings
     */
    @Throws(IOException::class)
    fun postings(reuse: PostingsEnum): PostingsEnum {
        return postings(reuse, PostingsEnum.FREQS.toInt())
    }

    /**
     * Get [PostingsEnum] for the current term, with control over whether freqs, positions,
     * offsets or payloads are required. Do not call this when the enum is unpositioned. This method
     * will not return null.
     *
     *
     * **NOTE**: the returned iterator may return deleted documents, so deleted documents have
     * to be checked on top of the [PostingsEnum].
     *
     * @param reuse pass a prior PostingsEnum for possible reuse
     * @param flags specifies which optional per-document values you require; see [     ][PostingsEnum.FREQS]
     */
    @Throws(IOException::class)
    abstract fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum

    /**
     * Return a [ImpactsEnum].
     *
     * @see .postings
     */
    @Throws(IOException::class)
    abstract fun impacts(flags: Int): ImpactsEnum

    /**
     * Expert: Returns the TermsEnums internal state to position the TermsEnum without re-seeking the
     * term dictionary.
     *
     *
     * NOTE: A seek by [TermState] might not capture the [AttributeSource]'s state.
     * Callers must maintain the [AttributeSource] states separately
     *
     * @see TermState
     *
     * @see .seekExact
     */
    @Throws(IOException::class)
    abstract fun termState(): TermState

    companion object {
        /**
         * An empty TermsEnum for quickly returning an empty instance e.g. in [ ]
         *
         *
         * *Please note:* This enum should be unmodifiable, but it is currently possible to add
         * Attributes to it. This should not be a problem, as the enum is always empty and the existence
         * of unused Attributes does not matter.
         */
        val EMPTY: TermsEnum = object : BaseTermsEnum() {
            override fun seekCeil(term: BytesRef): SeekStatus {
                return SeekStatus.END
            }

            override fun seekExact(ord: Long) {}

            override fun term(): BytesRef {
                throw IllegalStateException("this method should never be called")
            }

            override fun docFreq(): Int {
                throw IllegalStateException("this method should never be called")
            }

            override fun totalTermFreq(): Long {
                throw IllegalStateException("this method should never be called")
            }

            override fun ord(): Long {
                throw IllegalStateException("this method should never be called")
            }

            override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
                throw IllegalStateException("this method should never be called")
            }

            @Throws(IOException::class)
            override fun impacts(flags: Int): ImpactsEnum {
                throw IllegalStateException("this method should never be called")
            }

            override fun next(): BytesRef? {
                return null
            }

            override fun termState(): TermState {
                throw IllegalStateException("this method should never be called")
            }

            override fun seekExact(term: BytesRef, state: TermState) {
                throw IllegalStateException("this method should never be called")
            }
        }
    }
}
