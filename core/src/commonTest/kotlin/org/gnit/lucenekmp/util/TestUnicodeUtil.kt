package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.*
import kotlin.sequences.toList

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
        val num: Int = atLeast(50000)
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
        val num = atLeast(50000)
        for (i in 0..<num) {
            val s: String = TestUtil.randomUnicodeString(Random)
            val utf8 = ByteArray(UnicodeUtil.maxUTF8Length(s.length))
            val utf8Len = UnicodeUtil.UTF16toUTF8(s, 0, s.length, utf8)
            utf32 = ArrayUtil.grow(utf32, utf8Len)
            val utf32Len = UnicodeUtil.UTF8toUTF32(newBytesRef(utf8, 0, utf8Len), utf32)

            val codePoints: IntArray = s.codePointsSeq().toList().toIntArray()
            if (!ArrayUtil.equals(codePoints, 0, codePoints.size, utf32, 0, codePoints.size)) {
                println("FAILED")
                for (j in 0..<s.length) {
                    println("  char[" + j + "]=" + s[j].code.toHexString())
                }
                println()
                assertEquals(codePoints.size, utf32Len)
                for (j in codePoints.indices) {
                    println("  " + utf32[j].toHexString() + " vs " + codePoints[j].toHexString())
                }
                fail("mismatch")
            }
        }
    }

}