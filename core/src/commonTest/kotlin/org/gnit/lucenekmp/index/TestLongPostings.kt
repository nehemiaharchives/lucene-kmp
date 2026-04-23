package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SuppressCodecs("SimpleText", "Direct")
class TestLongPostings : LuceneTestCase() {

    // Produces a realistic unicode random string that
    // survives MockAnalyzer unchanged:
    @Throws(IOException::class)
    private fun getRandomTerm(other: String?): String {
        val a: Analyzer = MockAnalyzer(random())
        while (true) {
            val s = TestUtil.randomRealisticUnicodeString(random())
            if (other != null && s == other) {
                continue
            }
            a.tokenStream("foo", s).use { ts ->
                val termAtt = ts.getAttribute(TermToBytesRefAttribute::class)!!
                ts.reset()

                var count = 0
                var changed = false

                while (ts.incrementToken()) {
                    val termBytes = termAtt.bytesRef
                    if (count == 0 && termBytes.utf8ToString() != s) {
                        // The value was changed during analysis.  Keep iterating so the
                        // tokenStream is exhausted.
                        changed = true
                    }
                    count++
                }

                ts.end()
                // Did we iterate just once and the value was unchanged?
                if (!changed && count == 1) {
                    return s
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testLongPostings() {
        // Don't use _TestUtil.getTempDir so that we own the
        // randomness (ie same seed will point to same dir):
        val dir: Directory = newFSDirectory(createTempDir("longpostings.${random().nextLong()}"))

        val NUM_DOCS = atLeast(1000)

        if (VERBOSE) {
            println("TEST: NUM_DOCS=$NUM_DOCS")
        }

        val s1 = getRandomTerm(null)
        val s2 = getRandomTerm(s1)

        if (VERBOSE) {
            println("\nTEST: s1=$s1 s2=$s2")
            /*
            for(int idx=0;idx<s1.length();idx++) {
              System.out.println("  s1 ch=0x" + Integer.toHexString(s1.charAt(idx)));
            }
            for(int idx=0;idx<s2.length();idx++) {
              System.out.println("  s2 ch=0x" + Integer.toHexString(s2.charAt(idx)));
            }
            */
        }

        val isS1 = FixedBitSet(NUM_DOCS)
        for (idx in 0..<NUM_DOCS) {
            if (random().nextBoolean()) {
                isS1.set(idx)
            }
        }

        val r: IndexReader
        val iwc =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                .setMergePolicy(newLogMergePolicy())
        iwc.setRAMBufferSizeMB(16.0 + 16.0 * random().nextDouble())
        iwc.setMaxBufferedDocs(-1)
        val riw = RandomIndexWriter(random(), dir, iwc)

        for (idx in 0..<NUM_DOCS) {
            val doc = Document()
            val s = if (isS1.get(idx)) s1 else s2
            val f = newTextField("field", s, Field.Store.NO)
            val count = TestUtil.nextInt(random(), 1, 4)
            for (ct in 0..<count) {
                doc.add(f)
            }
            riw.addDocument(doc)
        }

        r = riw.getReader(applyDeletions = true, writeAllDeletes = false)
        riw.close()

        /*
        if (VERBOSE) {
          System.out.println("TEST: terms");
          TermEnum termEnum = r.terms();
          while(termEnum.next()) {
            System.out.println("  term=" + termEnum.term() + " len=" + termEnum.term().text().length());
            assertTrue(termEnum.docFreq() > 0);
            System.out.println("    s1?=" + (termEnum.term().text().equals(s1)) + " s1len=" + s1.length());
            System.out.println("    s2?=" + (termEnum.term().text().equals(s2)) + " s2len=" + s2.length());
            final String s = termEnum.term().text();
            for(int idx=0;idx<s.length();idx++) {
              System.out.println("      ch=0x" + Integer.toHexString(s.charAt(idx)));
            }
          }
        }
        */

        assertEquals(NUM_DOCS, r.numDocs())
        assertTrue(r.docFreq(Term("field", s1)) > 0)
        assertTrue(r.docFreq(Term("field", s2)) > 0)

        val num = atLeast(1000)
        for (iter in 0..<num) {

            val term: String
            val doS1: Boolean
            if (random().nextBoolean()) {
                term = s1
                doS1 = true
            } else {
                term = s2
                doS1 = false
            }

            if (VERBOSE) {
                println("\nTEST: iter=$iter doS1=$doS1")
            }

            val postings = MultiTerms.getTermPostingsEnum(r, "field", BytesRef(term))!!

            var docID = -1
            while (docID < DocIdSetIterator.NO_MORE_DOCS) {
                val what = random().nextInt(3)
                if (what == 0) {
                    if (VERBOSE) {
                        println("TEST: docID=$docID; do next()")
                    }
                    // nextDoc
                    var expected = docID + 1
                    while (true) {
                        if (expected == NUM_DOCS) {
                            expected = Int.MAX_VALUE
                            break
                        } else if (isS1.get(expected) == doS1) {
                            break
                        } else {
                            expected++
                        }
                    }
                    docID = postings.nextDoc()
                    if (VERBOSE) {
                        println("  got docID=$docID")
                    }
                    assertEquals(expected, docID)
                    if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }

                    if (random().nextInt(6) == 3) {
                        if (VERBOSE) {
                            println("    check positions")
                        }
                        val freq = postings.freq()
                        assertTrue(freq in 1..4)
                        for (pos in 0..<freq) {
                            assertEquals(pos, postings.nextPosition())
                            if (random().nextBoolean()) {
                                postings.payload
                                if (random().nextBoolean()) {
                                    postings.payload // get it again
                                }
                            }
                        }
                    }
                } else {
                    // advance
                    val targetDocID: Int
                    if (docID == -1) {
                        targetDocID = random().nextInt(NUM_DOCS + 1)
                    } else {
                        targetDocID = docID + TestUtil.nextInt(random(), 1, NUM_DOCS - docID)
                    }
                    if (VERBOSE) {
                        println("TEST: docID=$docID; do advance($targetDocID)")
                    }
                    var expected = targetDocID
                    while (true) {
                        if (expected == NUM_DOCS) {
                            expected = Int.MAX_VALUE
                            break
                        } else if (isS1.get(expected) == doS1) {
                            break
                        } else {
                            expected++
                        }
                    }

                    docID = postings.advance(targetDocID)
                    if (VERBOSE) {
                        println("  got docID=$docID")
                    }
                    assertEquals(expected, docID)
                    if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }

                    if (random().nextInt(6) == 3) {
                        val freq = postings.freq()
                        assertTrue(freq in 1..4)
                        for (pos in 0..<freq) {
                            assertEquals(pos, postings.nextPosition())
                            if (random().nextBoolean()) {
                                postings.payload
                                if (random().nextBoolean()) {
                                    postings.payload // get it again
                                }
                            }
                        }
                    }
                }
            }
        }
        r.close()
        dir.close()
    }

    // a weaker form of testLongPostings, that doesnt check positions
    @Test
    @Throws(Exception::class)
    fun testLongPostingsNoPositions() {
        doTestLongPostingsNoPositions(IndexOptions.DOCS)
        doTestLongPostingsNoPositions(IndexOptions.DOCS_AND_FREQS)
    }

    @Throws(Exception::class)
    fun doTestLongPostingsNoPositions(options: IndexOptions) {
        // Don't use _TestUtil.getTempDir so that we own the
        // randomness (ie same seed will point to same dir):
        val dir: Directory = newFSDirectory(createTempDir("longpostings.${random().nextLong()}"))

        val NUM_DOCS = atLeast(1000)

        if (VERBOSE) {
            println("TEST: NUM_DOCS=$NUM_DOCS")
        }

        val s1 = getRandomTerm(null)
        val s2 = getRandomTerm(s1)

        if (VERBOSE) {
            println("\nTEST: s1=$s1 s2=$s2")
            /*
            for(int idx=0;idx<s1.length();idx++) {
              System.out.println("  s1 ch=0x" + Integer.toHexString(s1.charAt(idx)));
            }
            for(int idx=0;idx<s2.length();idx++) {
              System.out.println("  s2 ch=0x" + Integer.toHexString(s2.charAt(idx)));
            }
            */
        }

        val isS1 = FixedBitSet(NUM_DOCS)
        for (idx in 0..<NUM_DOCS) {
            if (random().nextBoolean()) {
                isS1.set(idx)
            }
        }

        val r: IndexReader
        if (true) {
            val iwc =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                    .setMergePolicy(newLogMergePolicy())
            iwc.setRAMBufferSizeMB(16.0 + 16.0 * random().nextDouble())
            iwc.setMaxBufferedDocs(-1)
            val riw = RandomIndexWriter(random(), dir, iwc)

            val ft = FieldType(TextField.TYPE_NOT_STORED)
            ft.setIndexOptions(options)
            for (idx in 0..<NUM_DOCS) {
                val doc = Document()
                val s = if (isS1.get(idx)) s1 else s2
                val f = newField("field", s, ft)
                val count = TestUtil.nextInt(random(), 1, 4)
                for (ct in 0..<count) {
                    doc.add(f)
                }
                riw.addDocument(doc)
            }

            r = riw.getReader(applyDeletions = true, writeAllDeletes = false)
            riw.close()
        } else {
            r = DirectoryReader.open(dir)
        }

        /*
        if (VERBOSE) {
          System.out.println("TEST: terms");
          TermEnum termEnum = r.terms();
          while(termEnum.next()) {
            System.out.println("  term=" + termEnum.term() + " len=" + termEnum.term().text().length());
            assertTrue(termEnum.docFreq() > 0);
            System.out.println("    s1?=" + (termEnum.term().text().equals(s1)) + " s1len=" + s1.length());
            System.out.println("    s2?=" + (termEnum.term().text().equals(s2)) + " s2len=" + s2.length());
            final String s = termEnum.term().text();
            for(int idx=0;idx<s.length();idx++) {
              System.out.println("      ch=0x" + Integer.toHexString(s.charAt(idx)));
            }
          }
        }
        */

        assertEquals(NUM_DOCS, r.numDocs())
        assertTrue(r.docFreq(Term("field", s1)) > 0)
        assertTrue(r.docFreq(Term("field", s2)) > 0)

        val num = atLeast(1000)
        for (iter in 0..<num) {

            val term: String
            val doS1: Boolean
            if (random().nextBoolean()) {
                term = s1
                doS1 = true
            } else {
                term = s2
                doS1 = false
            }

            if (VERBOSE) {
                println("\nTEST: iter=$iter doS1=$doS1 term=$term")
            }

            val docs: PostingsEnum
            val postings: PostingsEnum?

            if (options == IndexOptions.DOCS) {
                docs = TestUtil.docs(random(), r, "field", BytesRef(term), null, PostingsEnum.NONE.toInt())!!
                postings = null
            } else {
                postings =
                    TestUtil.docs(random(), r, "field", BytesRef(term), null, PostingsEnum.FREQS.toInt())!!
                docs = postings
            }

            var docID = -1
            while (docID < DocIdSetIterator.NO_MORE_DOCS) {
                val what = random().nextInt(3)
                if (what == 0) {
                    if (VERBOSE) {
                        println("TEST: docID=$docID; do next()")
                    }
                    // nextDoc
                    var expected = docID + 1
                    while (true) {
                        if (expected == NUM_DOCS) {
                            expected = Int.MAX_VALUE
                            break
                        } else if (isS1.get(expected) == doS1) {
                            break
                        } else {
                            expected++
                        }
                    }
                    docID = docs.nextDoc()
                    if (VERBOSE) {
                        println("  got docID=$docID")
                    }
                    assertEquals(expected, docID)
                    if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }

                    if (random().nextInt(6) == 3 && postings != null) {
                        val freq = postings.freq()
                        assertTrue(freq in 1..4)
                    }
                } else {
                    // advance
                    val targetDocID: Int
                    if (docID == -1) {
                        targetDocID = random().nextInt(NUM_DOCS + 1)
                    } else {
                        targetDocID = docID + TestUtil.nextInt(random(), 1, NUM_DOCS - docID)
                    }
                    if (VERBOSE) {
                        println("TEST: docID=$docID; do advance($targetDocID)")
                    }
                    var expected = targetDocID
                    while (true) {
                        if (expected == NUM_DOCS) {
                            expected = Int.MAX_VALUE
                            break
                        } else if (isS1.get(expected) == doS1) {
                            break
                        } else {
                            expected++
                        }
                    }

                    docID = docs.advance(targetDocID)
                    if (VERBOSE) {
                        println("  got docID=$docID")
                    }
                    assertEquals(expected, docID)
                    if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }

                    if (random().nextInt(6) == 3 && postings != null) {
                        val freq = postings.freq()
                        assertTrue(freq in 1..4, "got invalid freq=$freq")
                    }
                }
            }
        }
        r.close()
        dir.close()
    }
}
