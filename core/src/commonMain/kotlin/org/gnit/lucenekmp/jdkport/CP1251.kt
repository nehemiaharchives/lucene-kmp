package org.gnit.lucenekmp.jdkport

import kotlin.math.min

/**
 * Windows-1251 (CP1251) charset.
 */
@Ported(from = "sun.nio.cs.MS1251")
class CP1251 : Charset("windows-1251", StandardCharsets.aliases_CP1251()) {
    override fun contains(cs: Charset): Boolean {
        return cs === this
    }

    override fun newDecoder(): CharsetDecoder {
        return Decoder(this)
    }

    override fun newEncoder(): CharsetEncoder {
        return Encoder(this)
    }

    private class Decoder(cs: Charset) : CharsetDecoder(cs, 1.0f, 1.0f) {
        fun decodeArrayLoop(src: ByteBuffer, dst: CharBuffer): CoderResult {
            val sa = src.array()
            val soff = src.arrayOffset()
            var sp = soff + src.position
            val sl = soff + src.limit

            val da = dst.array()
            val doff = dst.arrayOffset()
            var dp = doff + dst.position()
            val dl = doff + dst.limit

            val decodeLen = min(sl - sp, dl - dp)
            for (i in 0 until decodeLen) {
                da[dp + i] = DECODE_TABLE[sa[sp + i].toInt() and 0xFF]
            }
            sp += decodeLen
            dp += decodeLen
            src.position(sp - soff)
            dst.position = dp - doff
            return if (sl - sp > dl - dp) CoderResult.OVERFLOW else CoderResult.UNDERFLOW
        }

        fun decodeBufferLoop(src: ByteBuffer, dst: CharBuffer): CoderResult {
            var mark = src.position
            try {
                while (src.hasRemaining()) {
                    val b = src.get().toInt() and 0xFF
                    if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                    dst.put(DECODE_TABLE[b])
                    mark++
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

    private class Encoder(cs: Charset) : CharsetEncoder(cs, 1.0f, 1.0f) {
        private val sgp: Surrogate.Parser = Surrogate.Parser()

        override fun canEncode(c: Char): Boolean {
            return ENCODE_TABLE[c.code] != -1
        }

        override fun isLegalReplacement(repl: ByteArray): Boolean = true

        fun encodeArrayLoop(src: CharBuffer, dst: ByteBuffer): CoderResult {
            val sa = src.array()
            val soff = src.arrayOffset()
            var sp = soff + src.position()
            val sl = soff + src.limit

            val da = dst.array()
            val doff = dst.arrayOffset()
            var dp = doff + dst.position
            val dl = doff + dst.limit

            val dlen = dl - dp
            val slen = sl - sp
            val len = if (dlen < slen) dlen else slen
            var i = 0
            try {
                while (i < len) {
                    val c = sa[sp + i]
                    val v = ENCODE_TABLE[c.code]
                    if (v == -1) {
                        if (sgp.parse(c, sa, sp + i, sl) < 0) return sgp.error()!!
                        return sgp.unmappableResult()
                    }
                    da[dp + i] = v.toByte()
                    i++
                }
                sp += i
                dp += i
                if (i < slen) return CoderResult.OVERFLOW
                return CoderResult.UNDERFLOW
            } finally {
                src.position = sp - soff
                dst.position(dp - doff)
            }
        }

        fun encodeBufferLoop(src: CharBuffer, dst: ByteBuffer): CoderResult {
            var mark = src.position()
            try {
                while (src.hasRemaining()) {
                    val c = src.get()
                    val v = ENCODE_TABLE[c.code]
                    if (v != -1) {
                        if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                        dst.put(v.toByte())
                        mark++
                    } else {
                        if (sgp.parse(c, src) < 0) return sgp.error()
                        return sgp.unmappableResult()
                    }
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
        private val DECODE_TABLE: CharArray = charArrayOf(
            // generated from jdk24u/make/data/charsetmapping/MS1251.map
                    '\u0000',         '\u0001',         '\u0002',         '\u0003',         '\u0004',         '\u0005',         '\u0006',         '\u0007',
        '\u0008',         '\u0009',         '\u000A',         '\u000B',         '\u000C',         '\u000D',         '\u000E',         '\u000F',
        '\u0010',         '\u0011',         '\u0012',         '\u0013',         '\u0014',         '\u0015',         '\u0016',         '\u0017',
        '\u0018',         '\u0019',         '\u001A',         '\u001B',         '\u001C',         '\u001D',         '\u001E',         '\u001F',
        '\u0020',         '\u0021',         '\u0022',         '\u0023',         '\u0024',         '\u0025',         '\u0026',         '\u0027',
        '\u0028',         '\u0029',         '\u002A',         '\u002B',         '\u002C',         '\u002D',         '\u002E',         '\u002F',
        '\u0030',         '\u0031',         '\u0032',         '\u0033',         '\u0034',         '\u0035',         '\u0036',         '\u0037',
        '\u0038',         '\u0039',         '\u003A',         '\u003B',         '\u003C',         '\u003D',         '\u003E',         '\u003F',
        '\u0040',         '\u0041',         '\u0042',         '\u0043',         '\u0044',         '\u0045',         '\u0046',         '\u0047',
        '\u0048',         '\u0049',         '\u004A',         '\u004B',         '\u004C',         '\u004D',         '\u004E',         '\u004F',
        '\u0050',         '\u0051',         '\u0052',         '\u0053',         '\u0054',         '\u0055',         '\u0056',         '\u0057',
        '\u0058',         '\u0059',         '\u005A',         '\u005B',         '\u005C',         '\u005D',         '\u005E',         '\u005F',
        '\u0060',         '\u0061',         '\u0062',         '\u0063',         '\u0064',         '\u0065',         '\u0066',         '\u0067',
        '\u0068',         '\u0069',         '\u006A',         '\u006B',         '\u006C',         '\u006D',         '\u006E',         '\u006F',
        '\u0070',         '\u0071',         '\u0072',         '\u0073',         '\u0074',         '\u0075',         '\u0076',         '\u0077',
        '\u0078',         '\u0079',         '\u007A',         '\u007B',         '\u007C',         '\u007D',         '\u007E',         '\u007F',
        '\u0402',         '\u0403',         '\u201A',         '\u0453',         '\u201E',         '\u2026',         '\u2020',         '\u2021',
        '\u20AC',         '\u2030',         '\u0409',         '\u2039',         '\u040A',         '\u040C',         '\u040B',         '\u040F',
        '\u0452',         '\u2018',         '\u2019',         '\u201C',         '\u201D',         '\u2022',         '\u2013',         '\u2014',
        '\uFFFD',         '\u2122',         '\u0459',         '\u203A',         '\u045A',         '\u045C',         '\u045B',         '\u045F',
        '\u00A0',         '\u040E',         '\u045E',         '\u0408',         '\u00A4',         '\u0490',         '\u00A6',         '\u00A7',
        '\u0401',         '\u00A9',         '\u0404',         '\u00AB',         '\u00AC',         '\u00AD',         '\u00AE',         '\u0407',
        '\u00B0',         '\u00B1',         '\u0406',         '\u0456',         '\u0491',         '\u00B5',         '\u00B6',         '\u00B7',
        '\u0451',         '\u2116',         '\u0454',         '\u00BB',         '\u0458',         '\u0405',         '\u0455',         '\u0457',
        '\u0410',         '\u0411',         '\u0412',         '\u0413',         '\u0414',         '\u0415',         '\u0416',         '\u0417',
        '\u0418',         '\u0419',         '\u041A',         '\u041B',         '\u041C',         '\u041D',         '\u041E',         '\u041F',
        '\u0420',         '\u0421',         '\u0422',         '\u0423',         '\u0424',         '\u0425',         '\u0426',         '\u0427',
        '\u0428',         '\u0429',         '\u042A',         '\u042B',         '\u042C',         '\u042D',         '\u042E',         '\u042F',
        '\u0430',         '\u0431',         '\u0432',         '\u0433',         '\u0434',         '\u0435',         '\u0436',         '\u0437',
        '\u0438',         '\u0439',         '\u043A',         '\u043B',         '\u043C',         '\u043D',         '\u043E',         '\u043F',
        '\u0440',         '\u0441',         '\u0442',         '\u0443',         '\u0444',         '\u0445',         '\u0446',         '\u0447',
        '\u0448',         '\u0449',         '\u044A',         '\u044B',         '\u044C',         '\u044D',         '\u044E',         '\u044F'

        )

        private val ENCODE_TABLE: IntArray = IntArray(65536) { -1 }.apply {
            for (i in 0..255) {
                this[DECODE_TABLE[i].code] = i
            }
        }
    }
}
