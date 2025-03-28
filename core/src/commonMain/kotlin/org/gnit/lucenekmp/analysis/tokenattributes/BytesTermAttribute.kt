package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.BytesRef

/**
 * This attribute can be used if you have the raw term bytes to be indexed. It can be used as
 * replacement for [CharTermAttribute], if binary terms should be indexed.
 *
 * @lucene.internal
 */
interface BytesTermAttribute : TermToBytesRefAttribute {
    /** Sets the [BytesRef] of the term  */
    fun setBytesRef(bytes: BytesRef?)
}
