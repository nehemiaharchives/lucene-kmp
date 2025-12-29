package morfologik.stemming

import org.gnit.lucenekmp.jdkport.ByteBuffer

/**
 * Encodes [dst] relative to [src] by trimming whatever non-equal suffix and infix [src] and [dst] have.
 */
class TrimInfixAndSuffixEncoder : ISequenceEncoder {
    private companion object {
        const val REMOVE_EVERYTHING = 255
    }

    private var scratch: ByteBuffer = ByteBuffer.allocate(0)

    override fun encode(reuse: ByteBuffer?, source: ByteBuffer, target: ByteBuffer): ByteBuffer {
        check(source.hasArray() && source.position == 0 && source.arrayOffset() == 0)
        check(target.hasArray() && target.position == 0 && target.arrayOffset() == 0)

        var maxInfixIndex = 0
        var maxSubsequenceLength = BufferUtils.sharedPrefixLength(source, target)
        var maxInfixLength = 0

        val startPoints = intArrayOf(0, maxSubsequenceLength)
        for (i in startPoints) {
            for (j in 1..(source.remaining() - i)) {
                val len2 = source.remaining() - (i + j)
                scratch = BufferUtils.clearAndEnsureCapacity(scratch, i + len2)
                scratch.put(source.array(), 0, i)
                scratch.put(source.array(), i + j, len2)
                scratch.flip()

                val sharedPrefix = BufferUtils.sharedPrefixLength(scratch, target)
                if (sharedPrefix > 0 && sharedPrefix > maxSubsequenceLength && i < REMOVE_EVERYTHING && j < REMOVE_EVERYTHING) {
                    maxSubsequenceLength = sharedPrefix
                    maxInfixIndex = i
                    maxInfixLength = j
                }
            }
        }

        var truncateSuffixBytes = source.remaining() - (maxInfixLength + maxSubsequenceLength)

        if (truncateSuffixBytes == 0 && maxInfixIndex + maxInfixLength == source.remaining()) {
            truncateSuffixBytes = maxInfixLength
            maxInfixIndex = 0
            maxInfixLength = 0
        }

        if (maxInfixIndex >= REMOVE_EVERYTHING || maxInfixLength >= REMOVE_EVERYTHING || truncateSuffixBytes >= REMOVE_EVERYTHING) {
            maxInfixIndex = 0
            maxSubsequenceLength = 0
            maxInfixLength = REMOVE_EVERYTHING
            truncateSuffixBytes = REMOVE_EVERYTHING
        }

        val len1 = target.remaining() - maxSubsequenceLength
        var out = BufferUtils.clearAndEnsureCapacity(reuse, 3 + len1)

        out.put(((maxInfixIndex + 'A'.code) and 0xFF).toByte())
        out.put(((maxInfixLength + 'A'.code) and 0xFF).toByte())
        out.put(((truncateSuffixBytes + 'A'.code) and 0xFF).toByte())
        out.put(target.array(), maxSubsequenceLength, len1)
        out.flip()

        return out
    }

    override fun decode(reuse: ByteBuffer?, source: ByteBuffer, encoded: ByteBuffer): ByteBuffer {
        check(encoded.remaining() >= 3)

        val p = encoded.position
        var infixIndex = (encoded.get(p) - 'A'.code).toInt() and 0xFF
        var infixLength = (encoded.get(p + 1) - 'A'.code).toInt() and 0xFF
        var truncateSuffixBytes = (encoded.get(p + 2) - 'A'.code).toInt() and 0xFF

        if (infixLength == REMOVE_EVERYTHING || truncateSuffixBytes == REMOVE_EVERYTHING) {
            infixIndex = 0
            infixLength = source.remaining()
            truncateSuffixBytes = 0
        }

        val len1 = source.remaining() - (infixIndex + infixLength + truncateSuffixBytes)
        val len2 = encoded.remaining() - 3
        var out = BufferUtils.clearAndEnsureCapacity(reuse, infixIndex + len1 + len2)

        check(encoded.hasArray() && encoded.position == 0 && encoded.arrayOffset() == 0)
        check(source.hasArray() && source.position == 0 && source.arrayOffset() == 0)

        out.put(source.array(), 0, infixIndex)
        out.put(source.array(), infixIndex + infixLength, len1)
        out.put(encoded.array(), 3, len2)
        out.flip()

        return out
    }

    override fun toString(): String = this::class.simpleName ?: "TrimInfixAndSuffixEncoder"
}
