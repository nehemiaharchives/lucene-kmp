/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.UnicodeUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestIndexWriterUnicode : LuceneTestCase() {
    private val utf8Data =
        arrayOf(
            // unpaired low surrogate
            "ab\udc17cd", "ab\ufffdcd",
            "\udc17abcd", "\ufffdabcd",
            "\udc17", "\ufffd",
            "ab\udc17\udc17cd", "ab\ufffd\ufffdcd",
            "\udc17\udc17abcd", "\ufffd\ufffdabcd",
            "\udc17\udc17", "\ufffd\ufffd",

            // unpaired high surrogate
            "ab\ud917cd", "ab\ufffdcd",
            "\ud917abcd", "\ufffdabcd",
            "\ud917", "\ufffd",
            "ab\ud917\ud917cd", "ab\ufffd\ufffdcd",
            "\ud917\ud917abcd", "\ufffd\ufffdabcd",
            "\ud917\ud917", "\ufffd\ufffd",

            // backwards surrogates
            "ab\udc17\ud917cd", "ab\ufffd\ufffdcd",
            "\udc17\ud917abcd", "\ufffd\ufffdabcd",
            "\udc17\ud917", "\ufffd\ufffd",
            "ab\udc17\ud917\udc17\ud917cd", "ab\ufffd\ud917\udc17\ufffdcd",
            "\udc17\ud917\udc17\ud917abcd", "\ufffd\ud917\udc17\ufffdabcd",
            "\udc17\ud917\udc17\ud917", "\ufffd\ud917\udc17\ufffd"
        )

    private fun nextInt(lim: Int): Int {
        return random().nextInt(lim)
    }

    private fun nextInt(start: Int, end: Int): Int {
        return start + nextInt(end - start)
    }

    private fun fillUnicode(buffer: CharArray, expected: CharArray, offset: Int, count: Int): Boolean {
        var localOffset = offset
        val len = localOffset + count
        var hasIllegal = false

        if (localOffset > 0 && buffer[localOffset] >= '\udc00' && buffer[localOffset] < '\ue000') {
            // Don't start in the middle of a valid surrogate pair
            localOffset--
        }

        var i = localOffset
        while (i < len) {
            val t = nextInt(6)
            if (t == 0 && i < len - 1) {
                // Make a surrogate pair
                // High surrogate
                val high = nextInt(0xd800, 0xdc00).toChar()
                buffer[i] = high
                expected[i] = high
                i++
                // Low surrogate
                val low = nextInt(0xdc00, 0xe000).toChar()
                buffer[i] = low
                expected[i] = low
            } else if (t <= 1) {
                val value = nextInt(0x80).toChar()
                buffer[i] = value
                expected[i] = value
            } else if (t == 2) {
                val value = nextInt(0x80, 0x800).toChar()
                buffer[i] = value
                expected[i] = value
            } else if (t == 3) {
                val value = nextInt(0x800, 0xd800).toChar()
                buffer[i] = value
                expected[i] = value
            } else if (t == 4) {
                val value = nextInt(0xe000, 0xffff).toChar()
                buffer[i] = value
                expected[i] = value
            } else if (t == 5 && i < len - 1) {
                // Illegal unpaired surrogate
                if (nextInt(10) == 7) {
                    val surrogate =
                        if (random().nextBoolean()) {
                            nextInt(0xd800, 0xdc00).toChar()
                        } else {
                            nextInt(0xdc00, 0xe000).toChar()
                        }
                    buffer[i] = surrogate
                    expected[i] = 0xfffd.toChar()
                    i++
                    val value = nextInt(0x800, 0xd800).toChar()
                    buffer[i] = value
                    expected[i] = value
                    hasIllegal = true
                } else {
                    val value = nextInt(0x800, 0xd800).toChar()
                    buffer[i] = value
                    expected[i] = value
                }
            } else {
                buffer[i] = ' '
                expected[i] = ' '
            }
            i++
        }

        return hasIllegal
    }

    // both start & end are inclusive
    private fun getInt(r: Random, start: Int, end: Int): Int {
        return start + r.nextInt(end - start + 1)
    }

    private fun asUnicodeChar(c: Char): String {
        return "U+${c.code.toString(16)}"
    }

    private fun termDesc(s: String): String {
        assertTrue(s.length <= 2)
        return if (s.length == 1) {
            asUnicodeChar(s[0])
        } else {
            "${asUnicodeChar(s[0])},${asUnicodeChar(s[1])}"
        }
    }

    @Throws(IOException::class)
    private fun checkTermsOrder(r: IndexReader, allTerms: Set<String>, isTop: Boolean) {
        val terms = MultiTerms.getTerms(r, "f")!!.iterator()
        val last = BytesRefBuilder()
        val seenTerms = HashSet<String>()

        while (true) {
            val term = terms.next() ?: break
            assertTrue(last.get().compareTo(term) < 0)
            last.copyBytes(term)

            val s = term.utf8ToString()
            assertTrue(
                allTerms.contains(s),
                "term ${termDesc(s)} was not added to index (count=${allTerms.size})"
            )
            seenTerms.add(s)
        }

        if (isTop) {
            assertTrue(allTerms == seenTerms)
        }

        val it = seenTerms.iterator()
        while (it.hasNext()) {
            val tr = BytesRef(it.next())
            assertEquals(
                TermsEnum.SeekStatus.FOUND,
                terms.seekCeil(tr),
                "seek failed for term=${termDesc(tr.utf8ToString())}"
            )
        }
    }

    // LUCENE-510
    @Test
    @Throws(Throwable::class)
    fun testRandomUnicodeStrings() {
        val buffer = CharArray(20)
        val expected = CharArray(20)
        val utf16 = CharsRefBuilder()

        val num = atLeast(10000)
        for (iter in 0 until num) {
            val hasIllegal = fillUnicode(buffer, expected, 0, 20)

            val utf8 = BytesRef(CharBuffer.wrap(buffer, 0, 20))
            if (!hasIllegal) {
                val b = buffer.concatToString().encodeToByteArray()
                assertEquals(b.size, utf8.length)
                for (i in b.indices) {
                    assertEquals(b[i], utf8.bytes[i])
                }
            }

            utf16.copyUTF8Bytes(utf8.bytes, 0, utf8.length)
            assertEquals(20, utf16.length())
            for (i in 0 until 20) {
                assertEquals(expected[i], utf16.charAt(i))
            }
        }
    }

    // LUCENE-510
    @Test
    @Throws(Throwable::class)
    fun testAllUnicodeChars() {
        val utf16 = CharsRefBuilder()
        val chars = CharArray(2)
        var ch = 0
        while (ch < 0x0010FFFF) {
            if (ch == 0xd800) {
                // Skip invalid code points
                ch = 0xe000
            }
            val codePoint = ch

            var len = 0
            if (codePoint <= 0xffff) {
                chars[len++] = codePoint.toChar()
            } else {
                chars[len++] = (((codePoint - 0x0010000) shr 10) + UnicodeUtil.UNI_SUR_HIGH_START).toChar()
                chars[len++] = (((codePoint - 0x0010000) and 0x3FF) + UnicodeUtil.UNI_SUR_LOW_START).toChar()
            }

            val utf8 = BytesRef(CharBuffer.wrap(chars, 0, len))
            val s1 = chars.concatToString(0, len)
            val s2 = String.fromByteArray(utf8.bytes, 0, utf8.length, Charset.UTF_8)
            assertEquals(s1, s2, "codepoint $codePoint")

            utf16.copyUTF8Bytes(utf8.bytes, 0, utf8.length)
            assertEquals(s1, utf16.toString(), "codepoint $codePoint")

            val b = s1.encodeToByteArray()
            assertEquals(utf8.length, b.size)
            for (j in 0 until utf8.length) {
                assertEquals(utf8.bytes[j], b[j])
            }
            ch++
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testEmbeddedFFFF() {
        val d = newDirectory()
        val w = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())))
        var doc = Document()
        doc.add(newTextField("field", "a a\uffffb", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newTextField("field", "a", Field.Store.NO))
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        assertEquals(1, r.docFreq(Term("field", "a\uffffb")))
        r.close()
        w.close()
        d.close()
    }

    // LUCENE-510
    @Test
    @Throws(Throwable::class)
    fun testInvalidUTF16() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(TestIndexWriter.StringSplitAnalyzer()))
        val doc = Document()

        val count = utf8Data.size / 2
        for (i in 0 until count) {
            doc.add(newTextField("f$i", utf8Data[2 * i], Field.Store.YES))
        }
        w.addDocument(doc)
        w.close()

        val ir = DirectoryReader.open(dir)
        val doc2 = ir.storedFields().document(0)
        for (i in 0 until count) {
            assertEquals(1, ir.docFreq(Term("f$i", utf8Data[2 * i + 1])), "field $i was not indexed correctly")
            assertEquals(
                utf8Data[2 * i + 1],
                doc2.getField("f$i")!!.stringValue(),
                "field $i is incorrect"
            )
        }
        ir.close()
        dir.close()
    }

    // Make sure terms, including ones with surrogate pairs,
    // sort in codepoint sort order by default
    @Test
    @Throws(Throwable::class)
    fun testTermUTF16SortOrder() {
        val rnd = random()
        val dir = newDirectory()
        val writer = RandomIndexWriter(rnd, dir)
        val d = Document()
        // Single segment
        val f = newStringField("f", "", Field.Store.NO)
        d.add(f)
        val chars = CharArray(2)
        val allTerms = HashSet<String>()

        val num = atLeast(200)
        for (i in 0 until num) {
            val s: String
            if (rnd.nextBoolean()) {
                // Single char
                if (rnd.nextBoolean()) {
                    // Above surrogates
                    chars[0] = getInt(rnd, 1 + UnicodeUtil.UNI_SUR_LOW_END, 0xffff).toChar()
                } else {
                    // Below surrogates
                    chars[0] = getInt(rnd, 0, UnicodeUtil.UNI_SUR_HIGH_START - 1).toChar()
                }
                s = chars.concatToString(0, 1)
            } else {
                // Surrogate pair
                chars[0] = getInt(rnd, UnicodeUtil.UNI_SUR_HIGH_START, UnicodeUtil.UNI_SUR_HIGH_END).toChar()
                assertTrue(
                    chars[0].code >= UnicodeUtil.UNI_SUR_HIGH_START &&
                        chars[0].code <= UnicodeUtil.UNI_SUR_HIGH_END
                )
                chars[1] = getInt(rnd, UnicodeUtil.UNI_SUR_LOW_START, UnicodeUtil.UNI_SUR_LOW_END).toChar()
                s = chars.concatToString(0, 2)
            }
            allTerms.add(s)
            f.setStringValue(s)

            writer.addDocument(d)

            if ((1 + i) % 42 == 0) {
                writer.commit()
            }
        }

        var r = writer.getReader(true, false)

        // Test each sub-segment
        for (ctx in r.leaves()) {
            checkTermsOrder(ctx.reader(), allTerms, false)
        }
        checkTermsOrder(r, allTerms, true)

        // Test multi segment
        r.close()

        writer.forceMerge(1)

        // Test single segment
        r = writer.getReader(true, false)
        checkTermsOrder(r, allTerms, true)
        r.close()

        writer.close()
        dir.close()
    }
}
