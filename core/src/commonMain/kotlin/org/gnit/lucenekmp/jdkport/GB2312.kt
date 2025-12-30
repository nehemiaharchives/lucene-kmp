package org.gnit.lucenekmp.jdkport

/**
 * GB2312 charset (EUC-CN repertoire).
 */
@Ported(from = "sun.nio.cs.ext.EUC_CN")
class GB2312 : Charset("GB2312", StandardCharsets.aliases_GB2312()) {
    override fun contains(cs: Charset): Boolean {
        return cs is GB2312
    }

    override fun newDecoder(): CharsetDecoder {
        return Decoder(this)
    }

    override fun newEncoder(): CharsetEncoder {
        return Encoder(this)
    }

    private class Decoder(cs: Charset) : CharsetDecoder(cs, 0.5f, 1.0f) {
        private fun decodeArrayLoop(src: ByteBuffer, dst: CharBuffer): CoderResult {
            val sa = src.array()
            val soff = src.arrayOffset()
            var sp = soff + src.position
            val sl = soff + src.limit

            val da = dst.array()
            val doff = dst.arrayOffset()
            var dp = doff + dst.position()
            val dl = doff + dst.limit

            try {
                while (sp < sl) {
                    if (dp >= dl) return CoderResult.OVERFLOW
                    val b1 = sa[sp].toInt() and 0xFF
                    if (b1 < 0x80) {
                        da[dp++] = b1.toChar()
                        sp++
                        continue
                    }
                    if (sl - sp < 2) return CoderResult.UNDERFLOW
                    val b2 = sa[sp + 1].toInt() and 0xFF
                    val c = decodeDouble(b1, b2)
                    if (c == UNMAPPABLE) return CoderResult.unmappableForLength(2)
                    da[dp++] = c
                    sp += 2
                }
                return CoderResult.UNDERFLOW
            } finally {
                src.position(sp - soff)
                dst.position = dp - doff
            }
        }

        private fun decodeBufferLoop(src: ByteBuffer, dst: CharBuffer): CoderResult {
            var mark = src.position
            try {
                while (src.hasRemaining()) {
                    val b1 = src.get().toInt() and 0xFF
                    if (b1 < 0x80) {
                        if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                        dst.put(b1.toChar())
                        mark++
                        continue
                    }
                    if (!src.hasRemaining()) return CoderResult.UNDERFLOW
                    val b2 = src.get().toInt() and 0xFF
                    val c = decodeDouble(b1, b2)
                    if (c == UNMAPPABLE) return CoderResult.unmappableForLength(2)
                    if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                    dst.put(c)
                    mark += 2
                }
                return CoderResult.UNDERFLOW
            } finally {
                src.position(mark)
            }
        }

        override fun decodeLoop(src: ByteBuffer, dst: CharBuffer): CoderResult {
            return if (src.hasArray() && dst.hasArray()) decodeArrayLoop(src, dst)
            else decodeBufferLoop(src, dst)
        }
    }

    private class Encoder(cs: Charset) : CharsetEncoder(cs, 1.0f, 2.0f) {
        private val sgp: Surrogate.Parser = Surrogate.Parser()

        override fun canEncode(c: Char): Boolean {
            return encodeChar(c) != UNMAPPABLE_ENC
        }

        override fun isLegalReplacement(repl: ByteArray): Boolean = true

        private fun encodeArrayLoop(src: CharBuffer, dst: ByteBuffer): CoderResult {
            val sa = src.array()
            val soff = src.arrayOffset()
            var sp = soff + src.position()
            val sl = soff + src.limit

            val da = dst.array()
            val doff = dst.arrayOffset()
            var dp = doff + dst.position
            val dl = doff + dst.limit

            try {
                while (sp < sl) {
                    val c = sa[sp]
                    val v = encodeChar(c)
                    if (v == UNMAPPABLE_ENC) {
                        if (sgp.parse(c, sa, sp, sl) < 0) return sgp.error()!!
                        return sgp.unmappableResult()
                    }
                    if (v <= 0xFF) {
                        if (dp >= dl) return CoderResult.OVERFLOW
                        da[dp++] = v.toByte()
                    } else {
                        if (dl - dp < 2) return CoderResult.OVERFLOW
                        da[dp++] = ((v shr 8) and 0xFF).toByte()
                        da[dp++] = (v and 0xFF).toByte()
                    }
                    sp++
                }
                return CoderResult.UNDERFLOW
            } finally {
                src.position = sp - soff
                dst.position(dp - doff)
            }
        }

        private fun encodeBufferLoop(src: CharBuffer, dst: ByteBuffer): CoderResult {
            var mark = src.position()
            try {
                while (src.hasRemaining()) {
                    val c = src.get()
                    val v = encodeChar(c)
                    if (v == UNMAPPABLE_ENC) {
                        if (sgp.parse(c, src) < 0) return sgp.error()
                        return sgp.unmappableResult()
                    }
                    if (v <= 0xFF) {
                        if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                        dst.put(v.toByte())
                    } else {
                        if (dst.remaining() < 2) return CoderResult.OVERFLOW
                        dst.put(((v shr 8) and 0xFF).toByte())
                        dst.put((v and 0xFF).toByte())
                    }
                    mark++
                }
                return CoderResult.UNDERFLOW
            } finally {
                src.position = mark
            }
        }

        override fun encodeLoop(src: CharBuffer, dst: ByteBuffer): CoderResult {
            return if (src.hasArray() && dst.hasArray()) encodeArrayLoop(src, dst)
            else encodeBufferLoop(src, dst)
        }
    }

    companion object {
        private const val MIN_BYTE = 0xA1
        private const val MAX_BYTE = 0xFE
        private const val ROW_LEN = 94
        private const val UNMAPPABLE_ENC = -1
        private val UNMAPPABLE: Char = '\uFFFD'

        fun decodeDouble(b1: Int, b2: Int): Char {
            if (b1 < MIN_BYTE || b1 > MAX_BYTE || b2 < MIN_BYTE || b2 > MAX_BYTE) return UNMAPPABLE
            val index = (b1 - MIN_BYTE) * ROW_LEN + (b2 - MIN_BYTE)
            val c = GB2312_B2C[index]
            return c
        }

        fun encodeChar(c: Char): Int {
            return GB2312_C2B[c.code]
        }
    }
}
