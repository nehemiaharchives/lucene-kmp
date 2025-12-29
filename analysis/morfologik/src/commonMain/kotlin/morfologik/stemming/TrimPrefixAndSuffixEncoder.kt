package morfologik.stemming

import org.gnit.lucenekmp.jdkport.ByteBuffer

/**
 * Encodes [dst] relative to [src] by trimming whatever non-equal suffix and prefix [src] and [dst] have.
 */
class TrimPrefixAndSuffixEncoder : ISequenceEncoder {
    private companion object {
        const val REMOVE_EVERYTHING = 255
    }

    override fun encode(reuse: ByteBuffer?, source: ByteBuffer, target: ByteBuffer): ByteBuffer {
        var maxSubsequenceLength = 0
        var maxSubsequenceIndex = 0
        for (i in 0 until source.remaining()) {
            val sharedPrefix = BufferUtils.sharedPrefixLength(source, i, target, 0)
            if (sharedPrefix > maxSubsequenceLength && i < REMOVE_EVERYTHING &&
                (source.remaining() - (i + sharedPrefix)) < REMOVE_EVERYTHING) {
                maxSubsequenceLength = sharedPrefix
                maxSubsequenceIndex = i
            }
        }

        var truncatePrefixBytes = maxSubsequenceIndex
        var truncateSuffixBytes = source.remaining() - (maxSubsequenceIndex + maxSubsequenceLength)
        if (truncatePrefixBytes >= REMOVE_EVERYTHING || truncateSuffixBytes >= REMOVE_EVERYTHING) {
            maxSubsequenceIndex = 0
            maxSubsequenceLength = 0
            truncatePrefixBytes = REMOVE_EVERYTHING
            truncateSuffixBytes = REMOVE_EVERYTHING
        }

        val len1 = target.remaining() - maxSubsequenceLength
        var out = BufferUtils.clearAndEnsureCapacity(reuse, 2 + len1)

        check(target.hasArray() && target.position == 0 && target.arrayOffset() == 0)

        out.put(((truncatePrefixBytes + 'A'.code) and 0xFF).toByte())
        out.put(((truncateSuffixBytes + 'A'.code) and 0xFF).toByte())
        out.put(target.array(), maxSubsequenceLength, len1)
        out.flip()

        return out
    }

    override fun decode(reuse: ByteBuffer?, source: ByteBuffer, encoded: ByteBuffer): ByteBuffer {
        check(encoded.remaining() >= 2)

        val p = encoded.position
        var truncatePrefixBytes = (encoded.get(p) - 'A'.code).toInt() and 0xFF
        var truncateSuffixBytes = (encoded.get(p + 1) - 'A'.code).toInt() and 0xFF

        if (truncatePrefixBytes == REMOVE_EVERYTHING || truncateSuffixBytes == REMOVE_EVERYTHING) {
            truncatePrefixBytes = source.remaining()
            truncateSuffixBytes = 0
        }

        check(source.hasArray() && source.position == 0 && source.arrayOffset() == 0)
        check(encoded.hasArray() && encoded.position == 0 && encoded.arrayOffset() == 0)

        val len1 = source.remaining() - (truncateSuffixBytes + truncatePrefixBytes)
        val len2 = encoded.remaining() - 2
        var out = BufferUtils.clearAndEnsureCapacity(reuse, len1 + len2)

        out.put(source.array(), truncatePrefixBytes, len1)
        out.put(encoded.array(), 2, len2)
        out.flip()

        return out
    }

    override fun toString(): String = this::class.simpleName ?: "TrimPrefixAndSuffixEncoder"
}
