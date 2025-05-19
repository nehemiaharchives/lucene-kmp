package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * @param dstBegin the starting index in the destination array (inclusive)
 * @param dstEnd the ending index in the destination array (exclusive)
 */
/*fun String.Companion.fromCharArray(value: CharArray, offset: Int, count: Int): String {
    // Check that the specified range is valid.
    if (offset < 0 || count < 0 || offset > value.size - count) {
        throw IndexOutOfBoundsException("offset: $offset, count: $count, array size: ${value.size}")
    }
    // Create and return the string from the given subarray.
    return value.copyOfRange(offset, offset + count).concatToString()
}*/


/**
 *
 * Returns a stream of code point values from this sequence.  Any surrogate
 * pairs encountered in the sequence are combined as if by {@linkplain
 * Character#toCodePoint Character.toCodePoint} and the result is passed
 * to the stream. Any other code units, including ordinary BMP characters,
 * unpaired surrogates, and undefined code units, are zero-extended to
 * {@code int} values which are then passed to the stream.
 *
 * @return an IntStream of Unicode code points from this sequence
 * @since 9
 *
 * trys to mimic following jdk code:
```
@Override
public IntStream codePoints() {
return StreamSupport.intStream(
isLatin1() ? new StringLatin1.CharsSpliterator(value, Spliterator.IMMUTABLE)
: new StringUTF16.CodePointsSpliterator(value, Spliterator.IMMUTABLE),
false);
}
```
 */
fun CharSequence.codePointSequence(): Sequence<Int> = sequence {
    var i = 0
    while (i < length) {
        val ch1 = this@codePointSequence[i]
        if (ch1.isHighSurrogate() && i + 1 < length) {
            val ch2 = this@codePointSequence[i + 1]
            if (ch2.isLowSurrogate()) {
                // valid pair – combine
                yield(((ch1.code - 0xD800) shl 10) + (ch2.code - 0xDC00) + 0x10000)
                i += 2
                continue
            }
        }
        // BMP char or unpaired surrogate
        yield(ch1.code)
        i++
    }
}

/**
 * mimics ```String(byte[] bytes, Charset charset)``` of jdk
 *
 * Constructs a new `String` by decoding the specified array of
 * bytes using the specified [charset][Charset].
 * The length of the new `String` is a function of the charset, and
 * hence may not be equal to the length of the byte array.
 *
 *  This method always replaces malformed-input and unmappable-character
 * sequences with this charset's default replacement string.  The [ ] class should be used when more control
 * over the decoding process is required.
 *
 *
 *  The contents of the string are unspecified if the byte array
 * is modified during string construction.
 *
 * @param  bytes
 * The bytes to be decoded into characters
 *
 * @param  charset
 * The [charset][Charset] to be used to
 * decode the `bytes`
 *
 * @since  1.6
 */
fun String.Companion.fromByteArray(bytes: ByteArray, charset: Charset): String {
    return fromByteArray(charset, bytes, 0, bytes.size)
}

private enum class Coder(val value: Byte) {
    LATIN1(0),
    //UTF16(1),
    UTF8(2),
}

/**
 * This method does not do any precondition checks on its arguments.
 *
 *
 * Important: parameter order of this method is deliberately changed in order to
 * disambiguate it against other similar methods of this class.
 */
private fun fromByteArray(charset: Charset, bytes: ByteArray, offset: Int, length: Int): String {
    lateinit var this_value: ByteArray
    lateinit var this_coder: Coder

    var latin1Decoder: CharsetDecoder? = null
    var utf16Decoder: CharsetDecoder? = null

    var offset = offset
    if (length == 0) {
        return ""
    } else if (charset is UTF_8) {
        val COMPACT_STRINGS = true

        if (COMPACT_STRINGS) {
            var dp: Int = StringCoding.countPositives(bytes, offset, length)
            if (dp == length) {
                this_value = Arrays.copyOfRange(bytes, offset, offset + length)
                this_coder = Coder.LATIN1
                latin1Decoder = ISO_8859_1().newDecoder()
                return latin1Decoder.decode(ByteBuffer.wrap(this_value)).toString()
            }
            // Decode with a stable copy, to be the result if the decoded length is the same
            var latin1: ByteArray = Arrays.copyOfRange(bytes, offset, offset + length)
            var sp = dp // first dp bytes are already in the copy
            while (sp < length) {
                val b1 = latin1[sp++].toInt()
                if (b1 >= 0) {
                    latin1[dp++] = b1.toByte()
                    continue
                }
                if ((b1 and 0xfe) == 0xc2 && sp < length) { // b1 either 0xc2 or 0xc3
                    val b2 = latin1[sp].toInt()
                    if (b2 < -64) { // continuation bytes are always negative values in the range -128 to -65
                        latin1[dp++] = decode2(b1, b2).code.toByte()
                        sp++
                        continue
                    }
                }
                // anything not a latin1, including the REPL
                // we have to go with the utf16
                sp--
                break
            }
            if (sp == length) {
                if (dp != latin1.size) {
                    latin1 = latin1.copyOf(dp)
                }
                this_value = latin1
                this_coder = Coder.LATIN1

                if (latin1Decoder == null) latin1Decoder = ISO_8859_1().newDecoder()
                return latin1Decoder.decode(ByteBuffer.wrap(this_value)).toString()
            }

            // we will not doing utf 16 which is jdk's internal representation. instead, we will decode to utf8
            /*
            var utf16: ByteArray = StringUTF16.newBytesFor(length)
            StringLatin1.inflate(latin1, 0, utf16, 0, dp)
            dp = decodeUTF8_UTF16(latin1, sp, length, utf16, dp, true)
            if (dp != length) {
                utf16 = utf16.copyOf(dp shl 1)
            }

            this_value = utf16
            this_coder = Coder.UTF16
            */

           this_value = Arrays.copyOfRange(bytes, offset, offset + length)
           this_coder = Coder.UTF8

        }
        // !COMPACT_STRINGS

        /*else {
            var dst: ByteArray = StringUTF16.newBytesFor(length)
            val dp: Int = decodeUTF8_UTF16(bytes, offset, offset + length, dst, 0, true)
            if (dp != length) {
                dst = dst.copyOf(dp shl 1)
            }
            this_value = dst
            this_coder = CODER_UTF16
        }*/

    } else if (charset is ISO_8859_1) {
        if (COMPACT_STRINGS) {
            this_value = Arrays.copyOfRange(bytes, offset, offset + length)
            this_coder = Coder.LATIN1

        }/* else {

            this_value = StringLatin1.inflate(bytes, offset, length)
            this_coder = Coder.UTF16

        }*/
    }

    /*else if (charset === US_ASCII.INSTANCE) {
        if (COMPACT_STRINGS && !StringCoding.hasNegatives(bytes, offset, length)) {
            this_value = Arrays.copyOfRange(bytes, offset, offset + length)
            this_coder = CODER_LATIN1
        } else {
            val dst: ByteArray = StringUTF16.newBytesFor(length)
            var dp = 0
            while (dp < length) {
                val b = bytes[offset++].toInt()
                StringUTF16.putChar(dst, dp++, (if (b >= 0) b.toChar() else REPL).code)
            }
            this_value = dst
            this_coder = CODER_UTF16
        }
    }*/

    /*else {
        // (1)We never cache the "external" cs, the only benefit of creating
        // an additional StringDe/Encoder object to wrap it is to share the
        // de/encode() method. These SD/E objects are short-lived, the young-gen
        // gc should be able to take care of them well. But the best approach
        // is still not to generate them if not really necessary.
        // (2)The defensive copy of the input byte/char[] has a big performance
        // impact, as well as the outgoing result byte/char[]. Need to do the
        // optimization check of (sm==null && classLoader0==null) for both.
        val cd: CharsetDecoder = charset.newDecoder()
        // ArrayDecoder fastpaths
        if (cd is ArrayDecoder) {
            // ascii
            if (cd.isASCIICompatible() && !StringCoding.hasNegatives(bytes, offset, length)) {
                if (COMPACT_STRINGS) {
                    this_value = Arrays.copyOfRange(bytes, offset, offset + length)
                    this_coder = CODER_LATIN1
                    return
                }
                this_value = java.lang.StringLatin1.inflate(bytes, offset, length)
                this_coder = CODER_UTF16
                return
            }

            // fastpath for always Latin1 decodable single byte
            if (COMPACT_STRINGS && cd.isLatin1Decodable()) {
                val dst = ByteArray(length)
                cd.decodeToLatin1(bytes, offset, length, dst)
                this_value = dst
                this_coder = CODER_LATIN1
                return
            }

            val en: Int = java.lang.String.scale(length, cd.maxCharsPerByte())
            cd.onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
            val ca = CharArray(en)
            val clen: Int = cd.decode(bytes, offset, length, ca)
            if (COMPACT_STRINGS) {
                val `val`: ByteArray = StringUTF16.compress(ca, 0, clen)

                this_coder = StringUTF16.coderFromArrayLen(`val`, clen)
                this_value = `val`
                return
            }
            coder = CODER_UTF16
            value = StringUTF16.toBytes(ca, 0, clen)
            return
        }

        // decode using CharsetDecoder
        val en: Int = java.lang.String.scale(length, cd.maxCharsPerByte())
        cd.onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
        val ca = CharArray(en)
        val caLen: Int
        try {
            caLen = java.lang.String.decodeWithDecoder(cd, ca, bytes, offset, length)
        } catch (x: CharacterCodingException) {
            // Substitution is enabled, so this shouldn't happen
            throw java.lang.Error(x)
        }
        if (COMPACT_STRINGS) {
            val `val`: ByteArray = StringUTF16.compress(ca, 0, caLen)
            this_coder = StringUTF16.coderFromArrayLen(`val`, caLen)
            this_value = `val`
            return
        }
        coder = CODER_UTF16
        value = StringUTF16.toBytes(ca, 0, caLen)
    }*/

    return when (this_coder) {
        Coder.LATIN1 -> (latin1Decoder ?: ISO_8859_1().newDecoder()).decode(ByteBuffer.wrap(this_value)).toString()
        Coder.UTF8 -> (utf16Decoder?: UTF_8().newDecoder()).decode(ByteBuffer.wrap(this_value)).toString()
    }
}

private fun decode2(b1: Int, b2: Int): Char {
    return (((b1 shl 6) xor b2) xor
            ((0xC0.toByte().toInt() shl 6) xor
                    (0x80.toByte().toInt() shl 0))).toChar()
}

const val REPL: Char = '\ufffd'

private fun decodeUTF8_UTF16(src: ByteArray, sp: Int, sl: Int, dst: ByteArray, dp: Int, doReplace: Boolean): Int {
    var sp = sp
    var dp = dp
    while (sp < sl) {
        var b1 = src[sp++].toInt()
        if (b1 >= 0) {
            StringUTF16.putChar(dst, dp++, b1.toChar().code)
        } else if ((b1 shr 5) == -2 && (b1 and 0x1e) != 0) {
            if (sp < sl) {
                val b2 = src[sp++].toInt()
                if (isNotContinuation(b2)) {
                    if (!doReplace) {
                        throwMalformed(sp - 1, 1)
                    }
                    StringUTF16.putChar(dst, dp++, REPL.code)
                    sp--
                } else {
                    StringUTF16.putChar(dst, dp++, decode2(b1, b2).code)
                }
                continue
            }
            if (!doReplace) {
                throwMalformed(sp, 1) // underflow()
            }
            StringUTF16.putChar(dst, dp++, REPL.code)
            break
        } else if ((b1 shr 4) == -2) {
            if (sp + 1 < sl) {
                val b2 = src[sp++].toInt()
                val b3 = src[sp++].toInt()
                if (isMalformed3(b1, b2, b3)) {
                    if (!doReplace) {
                        throwMalformed(sp - 3, 3)
                    }
                    StringUTF16.putChar(dst, dp++, REPL.code)
                    sp -= 3
                    sp += malformed3(src, sp)
                } else {
                    val c: Char = decode3(b1, b2, b3)
                    if (c.isSurrogate()) {
                        if (!doReplace) {
                            throwMalformed(sp - 3, 3)
                        }
                        StringUTF16.putChar(dst, dp++, REPL.code)
                    } else {
                        StringUTF16.putChar(dst, dp++, c.code)
                    }
                }
                continue
            }
            if (sp < sl && isMalformed3_2(b1, src[sp].toInt())) {
                if (!doReplace) {
                    throwMalformed(sp - 1, 2)
                }
                StringUTF16.putChar(dst, dp++, REPL.code)
                continue
            }
            if (!doReplace) {
                throwMalformed(sp, 1)
            }
            StringUTF16.putChar(dst, dp++, REPL.code)
            break
        } else if ((b1 shr 3) == -2) {
            if (sp + 2 < sl) {
                val b2 = src[sp++].toInt()
                val b3 = src[sp++].toInt()
                val b4 = src[sp++].toInt()
                val uc: Int = decode4(b1, b2, b3, b4)
                if (isMalformed4(b2, b3, b4) ||
                    !Character.isSupplementaryCodePoint(uc)
                ) { // shortest form check
                    if (!doReplace) {
                        throwMalformed(sp - 4, 4)
                    }
                    StringUTF16.putChar(dst, dp++, REPL.code)
                    sp -= 4
                    sp += malformed4(src, sp)
                } else {
                    StringUTF16.putChar(dst, dp++, Character.highSurrogate(uc).code)
                    StringUTF16.putChar(dst, dp++, Character.lowSurrogate(uc).code)
                }
                continue
            }
            b1 = b1 and 0xff
            if (b1 > 0xf4 || sp < sl && isMalformed4_2(b1, src[sp].toInt() and 0xff)) {
                if (!doReplace) {
                    throwMalformed(sp - 1, 1) // or 2
                }
                StringUTF16.putChar(dst, dp++, REPL.code)
                continue
            }
            if (!doReplace) {
                throwMalformed(sp - 1, 1)
            }
            sp++
            StringUTF16.putChar(dst, dp++, REPL.code)
            if (sp < sl && isMalformed4_3(src[sp].toInt())) {
                continue
            }
            break
        } else {
            if (!doReplace) {
                throwMalformed(sp - 1, 1)
            }
            StringUTF16.putChar(dst, dp++, REPL.code)
        }
    }
    return dp
}

private fun isNotContinuation(b: Int): Boolean {
    return (b and 0xc0) != 0x80
}

private fun throwMalformed(off: Int, nb: Int) {
    val msg = "malformed input off : $off, length : $nb"
    throw IllegalArgumentException(msg, MalformedInputException(nb))
}

private fun isMalformed3(b1: Int, b2: Int, b3: Int): Boolean {
    return (b1 == 0xe0.toByte().toInt() && (b2 and 0xe0) == 0x80) || (b2 and 0xc0) != 0x80 || (b3 and 0xc0) != 0x80
}

private fun malformed3(src: ByteArray, sp: Int): Int {
    var sp = sp
    val b1 = src[sp++].toInt()
    val b2 = src[sp].toInt() // no need to lookup b3
    return if ((b1 == 0xe0.toByte().toInt() && (b2 and 0xe0) == 0x80) ||
        isNotContinuation(b2)
    ) 1 else 2
}

private fun decode3(b1: Int, b2: Int, b3: Int): Char {
    return ((b1 shl 12) xor
            (b2 shl 6) xor
            (b3 xor
                    ((0xE0.toByte().toInt() shl 12) xor
                            (0x80.toByte().toInt() shl 6) xor
                            (0x80.toByte().toInt() shl 0)))).toChar()
}

private fun isMalformed3_2(b1: Int, b2: Int): Boolean {
    return (b1 == 0xe0.toByte().toInt() && (b2 and 0xe0) == 0x80) ||
            (b2 and 0xc0) != 0x80
}


private fun decode4(b1: Int, b2: Int, b3: Int, b4: Int): Int {
    return ((b1 shl 18) xor
            (b2 shl 12) xor
            (b3 shl 6) xor
            (b4 xor
                    ((0xF0.toByte().toInt() shl 18) xor
                            (0x80.toByte().toInt() shl 12) xor
                            (0x80.toByte().toInt() shl 6) xor
                            (0x80.toByte().toInt() shl 0))))
}

private fun isMalformed4(b2: Int, b3: Int, b4: Int): Boolean {
    return (b2 and 0xc0) != 0x80 || (b3 and 0xc0) != 0x80 || (b4 and 0xc0) != 0x80
}

private fun malformed4(src: ByteArray, sp: Int): Int {
    // we don't care the speed here
    var sp = sp
    val b1 = src[sp++].toInt() and 0xff
    val b2 = src[sp++].toInt() and 0xff
    if (b1 > 0xf4 ||
        (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) ||
        (b1 == 0xf4 && (b2 and 0xf0) != 0x80) ||
        isNotContinuation(b2)
    ) return 1
    if (isNotContinuation(src[sp].toInt())) return 2
    return 3
}

private fun isMalformed4_2(b1: Int, b2: Int): Boolean {
    return (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) ||
            (b1 == 0xf4 && (b2 and 0xf0) != 0x80) || (b2 and 0xc0) != 0x80
}

private fun isMalformed4_3(b3: Int): Boolean {
    return (b3 and 0xc0) != 0x80
}

/**
 * trys to mimic following jdk code:
 * however, in lucene context, COMPACT_STRINGS is always true, so gnore the check
 *
 * Private constructor. Trailing Void argument is there for
 * disambiguating it against other (public) constructors.
 *
 * Stores the char[] value into a byte[] that each byte represents
 * the8 low-order bits of the corresponding character, if the char[]
 * contains only latin1 character. Or a byte[] that stores all
 * characters in their byte sequences defined by the {@code StringUTF16}.
 *
 * <p> The contents of the string are unspecified if the character array
 * is modified during string construction.
 *
```
private String(char[] value, int off, int len, Void sig) {
if (len == 0) {
this_value = "".value;
this_coder = "".coder;
return;
}
if (COMPACT_STRINGS) {
byte[] val = StringUTF16.compress(value, off, len);
this_coder = StringUTF16.coderFromArrayLen(val, len);
this_value = val;
return;
}
this_coder = UTF16;
this_value = StringUTF16.toBytes(value, off, len);
}

```
 *
 */

fun String.Companion.fromCharArray(value: CharArray, offset: Int, count: Int): String {
    if (count == 0) return ""
    if (offset < 0 || count < 0 || offset > value.size - count) {
        throw IndexOutOfBoundsException("offset: $offset, count: $count, array size: ${value.size}")
    }
    return value.concatToString(offset, offset + count)
}
