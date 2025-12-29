package morfologik.stemming

import org.gnit.lucenekmp.jdkport.ByteBuffer

/**
 * No relative encoding at all (full target form is returned).
 */
class NoEncoder : ISequenceEncoder {
    override fun encode(reuse: ByteBuffer?, source: ByteBuffer, target: ByteBuffer): ByteBuffer {
        var out = BufferUtils.clearAndEnsureCapacity(reuse, target.remaining())
        target.mark()
        out.put(target)
        out.flip()
        target.reset()
        return out
    }

    override fun decode(reuse: ByteBuffer?, source: ByteBuffer, encoded: ByteBuffer): ByteBuffer {
        var out = BufferUtils.clearAndEnsureCapacity(reuse, encoded.remaining())
        encoded.mark()
        out.put(encoded)
        out.flip()
        encoded.reset()
        return out
    }

    override fun toString(): String = this::class.simpleName ?: "NoEncoder"
}
