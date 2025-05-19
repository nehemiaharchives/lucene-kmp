package org.gnit.lucenekmp.jdkport

internal abstract class UnicodeDecoder(cs: Charset, private var currentByteOrder: Int) :
    CharsetDecoder(cs, 0.5f, 1.0f) {
    private val expectedByteOrder: Int
    private var defaultByteOrder = BIG

    init {
        expectedByteOrder = currentByteOrder
    }

    constructor(cs: Charset, bo: Int, defaultBO: Int) : this(cs, bo) {
        defaultByteOrder = defaultBO
    }

    private fun decode(b1: Int, b2: Int): Char {
        if (currentByteOrder == BIG) return ((b1 shl 8) or b2).toChar()
        else return ((b2 shl 8) or b1).toChar()
    }

    override fun decodeLoop(src: ByteBuffer, dst: CharBuffer): CoderResult? {
        var mark: Int = src.position

        try {
            while (src.remaining() > 1) {
                val b1 = src.get().toInt() and 0xff
                val b2 = src.get().toInt() and 0xff

                // Byte Order Mark interpretation
                if (currentByteOrder == NONE) {
                    val c = ((b1 shl 8) or b2).toChar()
                    if (c == BYTE_ORDER_MARK) {
                        currentByteOrder = BIG
                        mark += 2
                        continue
                    } else if (c == REVERSED_MARK) {
                        currentByteOrder = LITTLE
                        mark += 2
                        continue
                    } else {
                        currentByteOrder = defaultByteOrder
                        // FALL THROUGH to process b1, b2 normally
                    }
                }

                val c = decode(b1, b2)

                // Surrogates
                if (c.isSurrogate()) {
                    if (Character.isHighSurrogate(c)) {
                        if (src.remaining() < 2) return CoderResult.UNDERFLOW
                        val c2 = decode(src.get().toInt() and 0xff, src.get().toInt() and 0xff)
                        if (!c2.isLowSurrogate()) return CoderResult.malformedForLength(
                            4
                        )
                        if (dst.remaining() < 2) return CoderResult.OVERFLOW
                        mark += 4
                        dst.put(c)
                        dst.put(c2)
                        continue
                    }
                    // Unpaired low surrogate
                    return CoderResult.malformedForLength(2)
                }

                if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                mark += 2
                dst.put(c)
            }
            return CoderResult.UNDERFLOW
        } finally {
            src.position(mark)
        }
    }

    override fun implReset() {
        currentByteOrder = expectedByteOrder
    }

    companion object {
        protected val BYTE_ORDER_MARK: Char = 0xfeff.toChar()
        protected val REVERSED_MARK: Char = 0xfffe.toChar()

        protected const val NONE: Int = 0
        protected const val BIG: Int = 1
        protected const val LITTLE: Int = 2
    }
}
