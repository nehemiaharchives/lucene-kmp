package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOBooleanSupplier


/**
 * A base TermsEnum that adds default implementations for
 *
 *
 *  * [.attributes]
 *  * [.termState]
 *  * [.seekExact]
 *  * [.seekExact]
 *
 *
 * In some cases, the default implementation may be slow and consume huge memory, so subclass SHOULD
 * have its own implementation if possible.
 */
abstract class BaseTermsEnum
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : TermsEnum() {
    private var atts: AttributeSource? = null

    @Throws(IOException::class)
    override fun termState(): TermState {
        return object : TermState() {
            override fun copyFrom(other: TermState) {
                throw UnsupportedOperationException()
            }
        }
    }

    @Throws(IOException::class)
    override fun seekExact(text: BytesRef): Boolean {
        return seekCeil(text) === SeekStatus.FOUND
    }

    @Throws(IOException::class)
    override fun prepareSeekExact(text: BytesRef): IOBooleanSupplier? {
        return IOBooleanSupplier { seekExact(text) }
    }

    @Throws(IOException::class)
    override fun seekExact(term: BytesRef, state: TermState) {
        require(seekExact(term)) { "term=$term does not exist" }
    }

    override fun attributes(): AttributeSource {
        if (atts == null) {
            atts = AttributeSource()
        }
        return atts!!
    }
}
