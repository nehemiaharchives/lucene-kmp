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

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockPayloadAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.English
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// TODO: we really need to test indexingoffsets, but then getting only docs / docs + freqs.
// not all codecs store prx separate...
// TODO: fix sep codec to index offsets so we can greatly reduce this list!
class TestPostingsOffsets : LuceneTestCase() {
    lateinit var iwc: IndexWriterConfig

    @BeforeTest
    fun setUp() {
        iwc = newIndexWriterConfig(MockAnalyzer(random()))
    }

    @Test
    fun testBasic() {
        val dir: Directory = newDirectory()

        val w = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()

        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        if (random().nextBoolean()) {
            ft.setStoreTermVectors(true)
            ft.setStoreTermVectorPositions(random().nextBoolean())
            ft.setStoreTermVectorOffsets(random().nextBoolean())
        }
        val tokens =
            arrayOf(
                makeToken("a", 1, 0, 6),
                makeToken("b", 1, 8, 9),
                makeToken("a", 1, 9, 17),
                makeToken("c", 1, 19, 50),
            )
        doc.add(Field("content", CannedTokenStream(*tokens), ft))

        w.addDocument(doc)
        val r = w.getReader(true, false)
        w.close()

        var dp = MultiTerms.getTermPostingsEnum(r, "content", BytesRef("a"))
        assertNotNull(dp)
        assertEquals(0, dp.nextDoc())
        assertEquals(2, dp.freq())
        assertEquals(0, dp.nextPosition())
        assertEquals(0, dp.startOffset())
        assertEquals(6, dp.endOffset())
        assertEquals(2, dp.nextPosition())
        assertEquals(9, dp.startOffset())
        assertEquals(17, dp.endOffset())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dp.nextDoc())

        dp = MultiTerms.getTermPostingsEnum(r, "content", BytesRef("b"))
        assertNotNull(dp)
        assertEquals(0, dp.nextDoc())
        assertEquals(1, dp.freq())
        assertEquals(1, dp.nextPosition())
        assertEquals(8, dp.startOffset())
        assertEquals(9, dp.endOffset())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dp.nextDoc())

        dp = MultiTerms.getTermPostingsEnum(r, "content", BytesRef("c"))
        assertNotNull(dp)
        assertEquals(0, dp.nextDoc())
        assertEquals(1, dp.freq())
        assertEquals(3, dp.nextPosition())
        assertEquals(19, dp.startOffset())
        assertEquals(50, dp.endOffset())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dp.nextDoc())

        r.close()
        dir.close()
    }

    @Test
    fun testSkipping() {
        doTestNumbers(false)
    }

    @Test
    fun testPayloads() {
        doTestNumbers(true)
    }

    fun doTestNumbers(withPayloads: Boolean) {
        val dir: Directory = newDirectory()
        val analyzer: Analyzer =
            if (withPayloads) {
                MockPayloadAnalyzer()
            } else {
                MockAnalyzer(random())
            }
        iwc = newIndexWriterConfig(analyzer)
        iwc.setMergePolicy(newLogMergePolicy()) // will rely on docids a bit for skipping
        val w = RandomIndexWriter(random(), dir, iwc)

        val ft = FieldType(TextField.TYPE_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        if (random().nextBoolean()) {
            ft.setStoreTermVectors(true)
            ft.setStoreTermVectorOffsets(random().nextBoolean())
            ft.setStoreTermVectorPositions(random().nextBoolean())
        }

        val numDocs = atLeast(500)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(Field("numbers", English.intToEnglish(i), ft))
            doc.add(Field("oddeven", if ((i % 2) == 0) "even" else "odd", ft))
            doc.add(StringField("id", "$i", Field.Store.NO))
            w.addDocument(doc)
        }

        val reader = w.getReader(true, false)
        w.close()

        val terms =
            arrayOf(
                "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "hundred"
            )

        for (term in terms) {
            val dp = requireNotNull(MultiTerms.getTermPostingsEnum(reader, "numbers", BytesRef(term)))
            val storedFields = reader.storedFields()
            var doc: Int
            while ((dp.nextDoc().also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                val storedNumbers = requireNotNull(storedFields.document(doc).get("numbers"))
                val freq = dp.freq()
                for (i in 0..<freq) {
                    dp.nextPosition()
                    val start = dp.startOffset()
                    assert(start >= 0)
                    val end = dp.endOffset()
                    assert(end >= 0 && end >= start)
                    // check that the offsets correspond to the term in the src text
                    assertTrue(storedNumbers.substring(start, end).equals(term))
                    if (withPayloads) {
                        // check that we have a payload and it starts with "pos"
                        assertNotNull(dp.payload)
                        val payload = requireNotNull(dp.payload)
                        assertTrue(payload.utf8ToString().startsWith("pos:"))
                    } // note: withPayloads=false doesnt necessarily mean we dont have them from MockAnalyzer!
                }
            }
        }

        // check we can skip correctly
        val numSkippingTests = atLeast(50)

        for (j in 0..<numSkippingTests) {
            val num = TestUtil.nextInt(random(), 100, minOf(numDocs - 1, 999))
            val dp = requireNotNull(MultiTerms.getTermPostingsEnum(reader, "numbers", BytesRef("hundred")))
            val storedFields = reader.storedFields()
            val doc = dp.advance(num)
            assertEquals(num, doc)
            val freq = dp.freq()
            for (i in 0..<freq) {
                val storedNumbers = requireNotNull(storedFields.document(doc).get("numbers"))
                dp.nextPosition()
                val start = dp.startOffset()
                assert(start >= 0)
                val end = dp.endOffset()
                assert(end >= 0 && end >= start)
                // check that the offsets correspond to the term in the src text
                assertTrue(storedNumbers.substring(start, end).equals("hundred"))
                if (withPayloads) {
                    // check that we have a payload and it starts with "pos"
                    assertNotNull(dp.payload)
                    val payload = requireNotNull(dp.payload)
                    assertTrue(payload.utf8ToString().startsWith("pos:"))
                } // note: withPayloads=false doesnt necessarily mean we dont have them from MockAnalyzer!
            }
        }

        // check that other fields (without offsets) work correctly

        for (i in 0..<numDocs) {
            val dp = requireNotNull(MultiTerms.getTermPostingsEnum(reader, "id", BytesRef("$i"), 0))
            assertEquals(i, dp.nextDoc())
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, dp.nextDoc())
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testRandom() {
        // token -> docID -> tokens
        val actualTokens = mutableMapOf<String, MutableMap<Int, MutableList<Token>>>()

        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir, iwc)

        val numDocs = atLeast(20)
        // final int numDocs = atLeast(5);

        val ft = FieldType(TextField.TYPE_NOT_STORED)

        // TODO: randomize what IndexOptions we use; also test
        // changing this up in one IW buffered segment...:
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        if (random().nextBoolean()) {
            ft.setStoreTermVectors(true)
            ft.setStoreTermVectorOffsets(random().nextBoolean())
            ft.setStoreTermVectorPositions(random().nextBoolean())
        }

        for (docCount in 0..<numDocs) {
            val doc = Document()
            doc.add(NumericDocValuesField("id", docCount.toLong()))
            val tokens = mutableListOf<Token>()
            val numTokens = atLeast(100)
            // final int numTokens = atLeast(20);
            var pos = -1
            var offset = 0
            // System.out.println("doc id=" + docCount);
            for (tokenCount in 0..<numTokens) {
                val text: String =
                    if (random().nextBoolean()) {
                        "a"
                    } else if (random().nextBoolean()) {
                        "b"
                    } else if (random().nextBoolean()) {
                        "c"
                    } else {
                        "d"
                    }

                var posIncr = if (random().nextBoolean()) 1 else random().nextInt(5)
                if (tokenCount == 0 && posIncr == 0) {
                    posIncr = 1
                }
                val offIncr = if (random().nextBoolean()) 0 else random().nextInt(5)
                val tokenOffset = random().nextInt(5)

                val token = makeToken(text, posIncr, offset + offIncr, offset + offIncr + tokenOffset)
                if (!actualTokens.containsKey(text)) {
                    actualTokens[text] = mutableMapOf()
                }
                val postingsByDoc = requireNotNull(actualTokens[text])
                if (!postingsByDoc.containsKey(docCount)) {
                    postingsByDoc[docCount] = mutableListOf()
                }
                requireNotNull(postingsByDoc[docCount]).add(token)
                tokens.add(token)
                pos += posIncr
                // stuff abs position into type:
                token.setType("$pos")
                offset += offIncr + tokenOffset
                // System.out.println("  " + token + " posIncr=" + token.getPositionIncrement() + " pos=" +
                // pos + " off=" + token.startOffset() + "/" + token.endOffset() + " (freq=" +
                // postingsByDoc.get(docCount).size() + ")");
            }
            doc.add(Field("content", CannedTokenStream(*tokens.toTypedArray()), ft))
            w.addDocument(doc)
        }
        val r = w.getReader(true, false)
        w.close()

        val terms = arrayOf("a", "b", "c", "d")
        for (ctx in r.leaves()) {
            // TODO: improve this
            val sub = ctx.reader()
            // System.out.println("\nsub=" + sub);
            val termsEnum = requireNotNull(sub.terms("content")).iterator()
            var docs: PostingsEnum? = null
            var docsAndPositions: PostingsEnum? = null
            var docsAndPositionsAndOffsets: PostingsEnum? = null
            val docIDToID = IntArray(sub.maxDoc())
            val values = DocValues.getNumeric(sub, "id")
            for (i in 0..<sub.maxDoc()) {
                assertEquals(i, values.nextDoc())
                docIDToID[i] = values.longValue().toInt()
            }

            for (term in terms) {
                // System.out.println("  term=" + term);
                if (termsEnum.seekExact(BytesRef(term))) {
                    docs = termsEnum.postings(docs)
                    assertNotNull(docs)
                    var doc: Int
                    // System.out.println("    doc/freq");
                    while ((docs.nextDoc().also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                        val expected = requireNotNull(actualTokens[term]) [docIDToID[doc]]
                        // System.out.println("      doc=" + docIDToID[doc] + " docID=" + doc + " " +
                        // expected.size() + " freq");
                        assertNotNull(expected)
                        assertEquals(expected.size, docs.freq())
                    }

                    // explicitly exclude offsets here
                    docsAndPositions = termsEnum.postings(docsAndPositions, PostingsEnum.ALL.toInt())
                    assertNotNull(docsAndPositions)
                    // System.out.println("    doc/freq/pos");
                    while ((docsAndPositions.nextDoc().also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                        val expected = requireNotNull(actualTokens[term])[docIDToID[doc]]
                        // System.out.println("      doc=" + docIDToID[doc] + " " + expected.size() + " freq");
                        assertNotNull(expected)
                        assertEquals(expected.size, docsAndPositions.freq())
                        for (token in expected) {
                            val pos = token.type().toInt()
                            // System.out.println("        pos=" + pos);
                            assertEquals(pos, docsAndPositions.nextPosition())
                        }
                    }

                    docsAndPositionsAndOffsets = termsEnum.postings(docsAndPositions, PostingsEnum.ALL.toInt())
                    assertNotNull(docsAndPositionsAndOffsets)
                    // System.out.println("    doc/freq/pos/offs");
                    while ((docsAndPositionsAndOffsets.nextDoc().also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                        val expected = requireNotNull(actualTokens[term])[docIDToID[doc]]
                        // System.out.println("      doc=" + docIDToID[doc] + " " + expected.size() + " freq");
                        assertNotNull(expected)
                        assertEquals(expected.size, docsAndPositionsAndOffsets.freq())
                        for (token in expected) {
                            val pos = token.type().toInt()
                            // System.out.println("        pos=" + pos);
                            assertEquals(pos, docsAndPositionsAndOffsets.nextPosition())
                            assertEquals(token.startOffset(), docsAndPositionsAndOffsets.startOffset())
                            assertEquals(token.endOffset(), docsAndPositionsAndOffsets.endOffset())
                        }
                    }
                }
            }
            // TODO: test advance:
        }
        r.close()
        dir.close()
    }

    @Test
    fun testAddFieldTwice() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        val customType3 = FieldType(TextField.TYPE_STORED)
        customType3.setStoreTermVectors(true)
        customType3.setStoreTermVectorPositions(true)
        customType3.setStoreTermVectorOffsets(true)
        customType3.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        doc.add(Field("content3", "here is more content with aaa aaa aaa", customType3))
        doc.add(Field("content3", "here is more content with aaa aaa aaa", customType3))
        iw.addDocument(doc)
        iw.close()
        dir.close() // checkindex
    }

    // NOTE: the next two tests aren't that good as we need an EvilToken...
    @Test
    fun testNegativeOffsets() {
        expectThrows(IllegalArgumentException::class) {
            checkTokens(arrayOf(makeToken("foo", 1, -1, -1)))
        }
    }

    @Test
    fun testIllegalOffsets() {
        expectThrows(IllegalArgumentException::class) {
            checkTokens(arrayOf(makeToken("foo", 1, 1, 0)))
        }
    }

    @Test
    fun testIllegalOffsetsAcrossFieldInstances() {
        expectThrows(IllegalArgumentException::class) {
            checkTokens(
                arrayOf(makeToken("use", 1, 150, 160)),
                arrayOf(makeToken("use", 1, 50, 60))
            )
        }
    }

    @Test
    fun testBackwardsOffsets() {
        expectThrows(IllegalArgumentException::class) {
            checkTokens(
                arrayOf(
                    makeToken("foo", 1, 0, 3), makeToken("foo", 1, 4, 7), makeToken("foo", 0, 3, 6)
                )
            )
        }
    }

    @Test
    fun testStackedTokens() {
        checkTokens(
            arrayOf(
                makeToken("foo", 1, 0, 3), makeToken("foo", 0, 0, 3), makeToken("foo", 0, 0, 3)
            )
        )
    }

    @Test
    fun testCrazyOffsetGap() {
        val dir: Directory = newDirectory()
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(MockTokenizer(MockTokenizer.KEYWORD, false))
                }

                override fun getOffsetGap(fieldName: String?): Int {
                    return -10
                }
            }
        val iw = IndexWriter(dir, IndexWriterConfig(analyzer))
        // add good document
        val doc = Document()
        iw.addDocument(doc)
        expectThrows(IllegalArgumentException::class) {
            val ft = FieldType(TextField.TYPE_NOT_STORED)
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
            doc.add(Field("foo", "bar", ft))
            doc.add(Field("foo", "bar", ft))
            iw.addDocument(doc)
        }
        iw.commit()
        iw.close()

        // make sure we see our good doc
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        r.close()
        dir.close()
    }

    @Test
    fun testLegalbutVeryLargeOffsets() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        val t1 = Token("foo", 0, Int.MAX_VALUE - 500)
        if (random().nextBoolean()) {
            t1.payload = BytesRef("test")
        }
        val t2 = Token("foo", Int.MAX_VALUE - 500, Int.MAX_VALUE)
        val tokenStream: TokenStream = CannedTokenStream(t1, t2)
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        // store some term vectors for the checkindex cross-check
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorPositions(true)
        ft.setStoreTermVectorOffsets(true)
        val field = Field("foo", tokenStream, ft)
        doc.add(field)
        iw.addDocument(doc)
        iw.close()
        dir.close()
    }

    // TODO: more tests with other possibilities

    private fun checkTokens(field1: Array<Token>, field2: Array<Token>) {
        val dir: Directory = newDirectory()
        val riw = RandomIndexWriter(random(), dir, iwc)
        var success = false
        try {
            val ft = FieldType(TextField.TYPE_NOT_STORED)
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
            // store some term vectors for the checkindex cross-check
            ft.setStoreTermVectors(true)
            ft.setStoreTermVectorPositions(true)
            ft.setStoreTermVectorOffsets(true)

            val doc = Document()
            doc.add(Field("body", CannedTokenStream(*field1), ft))
            doc.add(Field("body", CannedTokenStream(*field2), ft))
            riw.addDocument(doc)
            riw.close()
            success = true
        } finally {
            if (success) {
                IOUtils.close(dir)
            } else {
                IOUtils.closeWhileHandlingException(riw, dir)
            }
        }
    }

    private fun checkTokens(tokens: Array<Token>) {
        val dir: Directory = newDirectory()
        val riw = RandomIndexWriter(random(), dir, iwc)
        var success = false
        try {
            val ft = FieldType(TextField.TYPE_NOT_STORED)
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
            // store some term vectors for the checkindex cross-check
            ft.setStoreTermVectors(true)
            ft.setStoreTermVectorPositions(true)
            ft.setStoreTermVectorOffsets(true)

            val doc = Document()
            doc.add(Field("body", CannedTokenStream(*tokens), ft))
            riw.addDocument(doc)
            riw.close()
            success = true
        } finally {
            if (success) {
                IOUtils.close(dir)
            } else {
                IOUtils.closeWhileHandlingException(riw, dir)
            }
        }
    }

    private fun makeToken(text: String, posIncr: Int, startOffset: Int, endOffset: Int): Token {
        val t = Token()
        t.append(text)
        t.setPositionIncrement(posIncr)
        t.setOffset(startOffset, endOffset)
        return t
    }
}
