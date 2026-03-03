package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef

/**
 * Extension of [CharTermAttributeImpl] that encodes the term text as UTF-16 bytes instead of
 * as UTF-8 bytes.
 */
class MockUTF16TermAttributeImpl : CharTermAttributeImpl() {

    override val bytesRef: BytesRef
        get() {
            val ref = this.builder.get()
            ref.bytes = toUtf16LeBytes(toString())
            ref.offset = 0
            ref.length = ref.bytes.size
            return ref
        }

    private fun toUtf16LeBytes(value: String): ByteArray {
        val out = ByteArray(value.length * 2)
        var i = 0
        for (c in value) {
            val code = c.code
            out[i++] = (code and 0xFF).toByte()
            out[i++] = ((code ushr 8) and 0xFF).toByte()
        }
        return out
    }

    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                MockUTF16TermAttributeImpl::class,
                arrayOf(
                    CharTermAttribute::class,
                    TermToBytesRefAttribute::class
                )
            )
        }

        /** Factory that returns an instance of this class for CharTermAttribute */
        val UTF16_TERM_ATTRIBUTE_FACTORY: AttributeFactory =
            object : AttributeFactory.StaticImplementationAttributeFactory<MockUTF16TermAttributeImpl>(
                AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY,
                MockUTF16TermAttributeImpl::class
            ) {
                override fun createInstance(): MockUTF16TermAttributeImpl {
                    return MockUTF16TermAttributeImpl()
                }
            }
    }
}
