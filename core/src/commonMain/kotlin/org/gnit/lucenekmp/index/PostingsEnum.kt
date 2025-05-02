package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.BytesRef


/**
 * Iterates through the postings. NOTE: you must first call [.nextDoc] before using any of the
 * per-doc methods.
 */
abstract class PostingsEnum
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : DocIdSetIterator() {
    /**
     * Returns term frequency in the current document, or 1 if the field was indexed with [ ][IndexOptions.DOCS]. Do not call this before [.nextDoc] is first called, nor after [ ][.nextDoc] returns [DocIdSetIterator.NO_MORE_DOCS].
     *
     *
     * **NOTE:** if the [PostingsEnum] was obtain with [.NONE], the result of this
     * method is undefined.
     */
    @Throws(IOException::class)
    abstract fun freq(): Int

    /**
     * Returns the next position, or -1 if positions were not indexed. Calling this more than [ ][.freq] times is undefined.
     */
    @Throws(IOException::class)
    abstract fun nextPosition(): Int

    /** Returns start offset for the current position, or -1 if offsets were not indexed.  */
    @Throws(IOException::class)
    abstract fun startOffset(): Int

    /** Returns end offset for the current position, or -1 if offsets were not indexed.  */
    @Throws(IOException::class)
    abstract fun endOffset(): Int

    abstract val payload: BytesRef?

    companion object {
        /**
         * Flag to pass to [TermsEnum.postings] if you don't require per-document
         * postings in the returned enum.
         */
        const val NONE: Short = 0

        /**
         * Flag to pass to [TermsEnum.postings] if you require term frequencies
         * in the returned enum.
         */
        val FREQS: Short = (1 shl 3).toShort()

        /**
         * Flag to pass to [TermsEnum.postings] if you require term positions in
         * the returned enum.
         */
        val POSITIONS: Short = (FREQS.toInt() or (1 shl 4)).toShort()

        /**
         * Flag to pass to [TermsEnum.postings] if you require offsets in the
         * returned enum.
         */
        val OFFSETS: Short = (POSITIONS.toInt() or (1 shl 5)).toShort()

        /**
         * Flag to pass to [TermsEnum.postings] if you require payloads in the
         * returned enum.
         */
        val PAYLOADS: Short = (POSITIONS.toInt() or (1 shl 6)).toShort()

        /**
         * Flag to pass to [TermsEnum.postings] to get positions, payloads and
         * offsets in the returned enum
         */
        val ALL: Short = (OFFSETS.toInt() or PAYLOADS.toInt()).toShort()

        /** Returns true if the given feature is requested in the flags, false otherwise.  */
        fun featureRequested(flags: Int, feature: Short): Boolean {
            return (flags and feature.toInt()) == feature.toInt()
        }
    }
}
