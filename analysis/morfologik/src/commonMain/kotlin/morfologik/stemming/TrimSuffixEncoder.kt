package morfologik.stemming

import org.gnit.lucenekmp.jdkport.ByteBuffer

/**
 * Encodes [dst] relative to [src] by trimming whatever non-equal suffix [src] has.
 */
class TrimSuffixEncoder : ISequenceEncoder {
    private companion object {
        const val REMOVE_EVERYTHING = 255
    }

    override fun encode(reuse: ByteBuffer?, source: ByteBuffer, target: ByteBuffer): ByteBuffer {
        var sharedPrefix = BufferUtils.sharedPrefixLength(source, target)
        var truncateBytes = source.remaining() - sharedPrefix
        if (truncateBytes >= REMOVE_EVERYTHING) {
            truncateBytes = REMOVE_EVERYTHING
            sharedPrefix = 0
        }

        var out = BufferUtils.clearAndEnsureCapacity(reuse, 1 + target.remaining() - sharedPrefix)

        check(target.hasArray() && target.position == 0 && target.arrayOffset() == 0)

        val suffixTrimCode = (truncateBytes + 'A'.code).toByte()
        out.put(suffixTrimCode)
            .put(target.array(), sharedPrefix, target.remaining() - sharedPrefix)
            .flip()

        return out
    }

    override fun decode(reuse: ByteBuffer?, source: ByteBuffer, encoded: ByteBuffer): ByteBuffer {
        check(encoded.remaining() >= 1)

        var truncateBytes = (encoded.get(encoded.position) - 'A'.code).toInt() and 0xFF
        if (truncateBytes == REMOVE_EVERYTHING) {
            truncateBytes = source.remaining()
        }

        val len1 = source.remaining() - truncateBytes
        val len2 = encoded.remaining() - 1

        var out = BufferUtils.clearAndEnsureCapacity(reuse, len1 + len2)

        check(source.hasArray() && source.position == 0 && source.arrayOffset() == 0)
        check(encoded.hasArray() && encoded.position == 0 && encoded.arrayOffset() == 0)

        out.put(source.array(), 0, len1)
            .put(encoded.array(), 1, len2)
            .flip()

        return out
    }

    override fun toString(): String = this::class.simpleName ?: "TrimSuffixEncoder"
}
