package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.Cloneable

/**
 * Encapsulates all required internal state to position the associated [TermsEnum] without
 * re-seeking.
 *
 * @see TermsEnum.seekExact
 * @see TermsEnum.termState
 * @lucene.experimental
 */
abstract class TermState
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */() : Cloneable<TermState> {
    /**
     * Copies the content of the given [TermState] to this instance
     *
     * @param other the TermState to copy
     */
    abstract fun copyFrom(other: TermState)

    override fun clone(): TermState {
        throw UnsupportedOperationException("TermState is abstract and cannot be cloned directly. Please use a concrete implementation of TermState.")
    }

    override fun toString(): String {
        return "TermState"
    }
}
