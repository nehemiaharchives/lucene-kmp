package org.gnit.lucenekmp.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.FiniteStringsIterator
import kotlin.random.Random
import kotlin.test.*

/*
 * Some of this code came from the excellent Unicode
 * conversion examples from:
 *
 *   http://www.unicode.org/Public/PROGRAMS/CVTUTF
 *
 * Full Copyright for that code follows:
 */

/*
 * Copyright 2001-2004 Unicode, Inc.
 *
 * Disclaimer
 *
 * This source code is provided as is by Unicode, Inc. No claims are
 * made as to fitness for any particular purpose. No warranties of any
 * kind are expressed or implied. The recipient agrees to determine
 * applicability of information provided. If this file has been
 * purchased on magnetic or optical media from Unicode, Inc., the
 * sole remedy for any claim will be exchange of defective media
 * within 90 days of receipt.
 *
 * Limitations on Rights to Redistribute This Code
 *
 * Unicode, Inc. hereby grants the right to freely use the information
 * supplied in this file in the creation of products supporting the
 * Unicode Standard, and to make copies of this file in any form
 * for internal or external distribution as long as this notice
 * remains attached.
 */

/*
 * Additional code came from the IBM ICU library.
 *
 *  http://www.icu-project.org
 *
 * Full Copyright for that code follows.
 */

/*
 * Copyright (C) 1999-2010, International Business Machines
 * Corporation and others.  All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * provided that the above copyright notice(s) and this permission notice appear
 * in all copies of the Software and that both the above copyright notice(s) and
 * this permission notice appear in supporting documentation.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF THIRD PARTY RIGHTS.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE BE
 * LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES, OR
 * ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER
 * IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT
 * OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * Except as contained in this notice, the name of a copyright holder shall not
 * be used in advertising or otherwise to promote the sale, use or other
 * dealings in this Software without prior written authorization of the
 * copyright holder.
 */

class TestUnicodeUtil : LuceneTestCase() {

    private val logger = KotlinLogging.logger {}

    @Test
    fun testCodePointCount() {
        // Check invalid codepoints.
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0x80, 'z'.code, 'z'.code, 'z'.code))
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xc0 - 1, 'z'.code, 'z'.code, 'z'.code))
        // Check 5-byte and longer sequences.
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xf8, 'z'.code, 'z'.code, 'z'.code))
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xfc, 'z'.code, 'z'.code, 'z'.code))
        // Check improperly terminated codepoints.
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xc2))
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xe2))
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xe2, 0x82))
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xf0))
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xf0, 0xa4))
        assertcodePointCountThrowsAssertionOn(*asByteArray('z'.code, 0xf0, 0xa4, 0xad))

        // Check some typical examples (multibyte).
        assertEquals(0, UnicodeUtil.codePointCount(newBytesRef(asByteArray())))
        assertEquals(3, UnicodeUtil.codePointCount(newBytesRef(asByteArray('z'.code, 'z'.code, 'z'.code))))
        assertEquals(2, UnicodeUtil.codePointCount(newBytesRef(asByteArray('z'.code, 0xc2, 0xa2))))
        assertEquals(2, UnicodeUtil.codePointCount(newBytesRef(asByteArray('z'.code, 0xe2, 0x82, 0xac))))
        assertEquals(
            2, UnicodeUtil.codePointCount(newBytesRef(asByteArray('z'.code, 0xf0, 0xa4, 0xad, 0xa2)))
        )

        // And do some random stuff.
        val num: Int = atLeast(500) // java lucene tests with 50000, but that takes too long in Kotlin
        for (i in 0..<num) {
            val s: String = randomUnicodeString(Random)
            val utf8 = ByteArray(UnicodeUtil.maxUTF8Length(s.length))
            val utf8Len = UnicodeUtil.UTF16toUTF8(s, 0, s.length, utf8)
            assertEquals(
                s.codePointCount(0, s.length),
                UnicodeUtil.codePointCount(newBytesRef(utf8, 0, utf8Len))
            )
        }
    }

    private fun asByteArray(vararg ints: Int): ByteArray {
        val asByteArray = ByteArray(ints.size)
        for (i in ints.indices) {
            asByteArray[i] = ints[i].toByte()
        }
        return asByteArray
    }

    private fun assertcodePointCountThrowsAssertionOn(vararg bytes: Byte) {
        expectThrows(
            IllegalArgumentException::class
        ) {
            UnicodeUtil.codePointCount(newBytesRef(bytes))
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testUTF8toUTF32() {
        var utf32 = IntArray(0)
        val num = atLeast(500) // java lucene tests with 50000, but that takes too long in Kotlin
        for (i in 0..<num) {
            val s: String = TestUtil.randomUnicodeString(Random)
            val utf8 = ByteArray(UnicodeUtil.maxUTF8Length(s.length))
            val utf8Len = UnicodeUtil.UTF16toUTF8(s, 0, s.length, utf8)
            utf32 = ArrayUtil.grow(utf32, utf8Len)
            val utf32Len = UnicodeUtil.UTF8toUTF32(newBytesRef(utf8, 0, utf8Len), utf32)

            val codePoints: IntArray = s.codePointsSeq().toList().toIntArray()
            if (!Arrays.equals(codePoints, 0, codePoints.size, utf32, 0, codePoints.size)) {
                logger.debug { "FAILED" }
                for (j in 0..<s.length) {
                    logger.debug { "  char[" + j + "]=" + s[j].code.toHexString() }
                }
                logger.debug { "\n" }
                assertEquals(codePoints.size, utf32Len)
                for (j in codePoints.indices) {
                    logger.debug { "  " + utf32[j].toHexString() + " vs " + codePoints[j].toHexString() }
                }
                fail("mismatch")
            }
        }
    }

    @Test
    fun testUTF8CodePointAt() {
        val num: Int = atLeast(50000)
        var reuse: UnicodeUtil.UTF8CodePoint? = null
        for (i in 0..<num) {
            val s: String =
                TestUtil.randomUnicodeString(random())
            val utf8 = ByteArray(UnicodeUtil.maxUTF8Length(s.length))
            val utf8Len: Int = UnicodeUtil.UTF16toUTF8(s, 0, s.length, utf8)

            val expected: IntArray = s.codePointsSeq().toList().toIntArray()
            var pos = 0
            var expectedUpto = 0
            while (pos < utf8Len) {
                reuse = UnicodeUtil.codePointAt(utf8, pos, reuse)
                assertEquals(expected[expectedUpto].toLong(), reuse.codePoint.toLong())
                expectedUpto++
                pos += reuse.numBytes
            }
            assertEquals(utf8Len.toLong(), pos.toLong())
            assertEquals(expected.size.toLong(), expectedUpto.toLong())
        }
    }

    @Test
    fun testUTF8SpanMultipleBytes() {
        val b: Automaton.Builder = Automaton.Builder()
        // start state:
        val s1: Int = b.createState()

        // single end accept state:
        val s2: Int = b.createState()
        b.setAccept(s2, true)

        // utf8 codepoint length range from [1,2]
        b.addTransition(s1, s2, 0x7F, 0x80)
        // utf8 codepoint length range from [2,3]
        b.addTransition(s1, s2, 0x7FF, 0x800)
        // utf8 codepoint length range from [3,4]
        b.addTransition(s1, s2, 0xFFFF, 0x10000)

        val a: Automaton = b.finish()

        val c = CompiledAutomaton(a)
        val it = FiniteStringsIterator(c.automaton!!)
        var termCount = 0
        var r: IntsRef? = it.next()
        while (r != null) {
            termCount++
            r = it.next()
        }
        assertEquals(6, termCount.toLong())
    }

    @Test
    fun testNewString() {
        val codePoints = intArrayOf(
            Character.toCodePoint(
                Character.MIN_HIGH_SURROGATE,
                Character.MAX_LOW_SURROGATE
            ),
            Character.toCodePoint(
                Character.MAX_HIGH_SURROGATE,
                Character.MIN_LOW_SURROGATE
            ),
            Character.MAX_HIGH_SURROGATE.code,
            'A'.code,
            -1,
        )

        val cpString =
            (""
                    + Character.MIN_HIGH_SURROGATE
                    + Character.MAX_LOW_SURROGATE
                    + Character.MAX_HIGH_SURROGATE
                    + Character.MIN_LOW_SURROGATE
                    + Character.MAX_HIGH_SURROGATE
                    + 'A')

        val tests = arrayOf<IntArray>(
            intArrayOf(0, 1, 0, 2),
            intArrayOf(0, 2, 0, 4),
            intArrayOf(1, 1, 2, 2),
            intArrayOf(1, 2, 2, 3),
            intArrayOf(1, 3, 2, 4),
            intArrayOf(2, 2, 4, 2),
            intArrayOf(2, 3, 0, -1),
            intArrayOf(4, 5, 0, -1),
            intArrayOf(3, -1, 0, -1)
        )

        for (i in tests.indices) {
            val t = tests[i]
            val s = t[0]
            val c = t[1]
            val rs = t[2]
            val rc = t[3]

            try {
                val str: String = UnicodeUtil.newString(codePoints, s, c)
                assertFalse(rc == -1)
                assertEquals(cpString.substring(rs, rs + rc), str)
                continue
            } catch (e1: IndexOutOfBoundsException) {
                // Ignored.
            } catch (e1: IllegalArgumentException) {
            }
            assertTrue(rc == -1)
        }
    }

    @Ignore
    @Test
    fun testUTF8UTF16CharsRef() {
        val num: Int = atLeast(3989)
        for (i in 0..<num) {
            val unicode: String =
                TestUtil.randomRealisticUnicodeString(random())
            val ref: BytesRef = newBytesRef(unicode)
            val cRef = CharsRefBuilder()
            cRef.copyUTF8Bytes(ref)
            assertEquals(cRef.toString(), unicode)
        }
    }

    @Test
    fun testCalcUTF16toUTF8Length() {
        val num: Int = atLeast(5000)
        for (i in 0..<num) {
            val unicode: String =
                TestUtil.randomUnicodeString(random())
            val utf8 = ByteArray(UnicodeUtil.maxUTF8Length(unicode.length))
            val len: Int = UnicodeUtil.UTF16toUTF8(unicode, 0, unicode.length, utf8)
            assertEquals(
                len.toLong(),
                UnicodeUtil.calcUTF16toUTF8Length(unicode, 0, unicode.length).toLong()
            )
        }
    }
}