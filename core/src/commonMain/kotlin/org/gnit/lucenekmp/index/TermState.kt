package org.gnit.lucenekmp.index

/**
 * Encapsulates all required internal state to position the associated [TermsEnum] without
 * re-seeking.
 *
 * @see TermsEnum.seekExact
 * @see TermsEnum.termState
 * @lucene.experimental
 */
abstract class TermState
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */() : Cloneable {
    /**
     * Copies the content of the given [TermState] to this instance
     *
     * @param other the TermState to copy
     */
    abstract fun copyFrom(other: TermState)

    public override fun clone(): TermState {
        try {
            return super.clone() as TermState
        } catch (cnse: /*CloneNotSupportedException*/ Exception) {
            // should not happen
            throw RuntimeException(cnse)
        }
    }

    override fun toString(): String {
        return "TermState"
    }
}
