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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.UnicodeUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class TestMultiFields : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testRandom() {
        val num = atLeast(2)
        for (iter in 0 until num) {
            if (VERBOSE) {
                println("TEST: iter=$iter")
            }

            val dir: Directory = newDirectory()

            val w =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMergePolicy(
                            object : FilterMergePolicy(NoMergePolicy.INSTANCE) {
                                override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                                    // we can do this because we use NoMergePolicy (and dont merge to
                                    // "nothing")
                                    return true
                                }
                            }
                        )
                )
            val docs = hashMapOf<BytesRef, MutableList<Int>>()
            val deleted = hashSetOf<Int>()
            val terms = arrayListOf<BytesRef>()

            val numDocs = TestUtil.nextInt(random(), 1, 100 * RANDOM_MULTIPLIER)
            val doc = Document()
            val f: Field = newStringField("field", "", Field.Store.NO)
            doc.add(f)
            val id: Field = newStringField("id", "", Field.Store.NO)
            doc.add(id)

            val onlyUniqueTerms = random().nextBoolean()
            if (VERBOSE) {
                println("TEST: onlyUniqueTerms=$onlyUniqueTerms numDocs=$numDocs")
            }
            val uniqueTerms = hashSetOf<BytesRef>()
            for (i in 0 until numDocs) {
                if (!onlyUniqueTerms && random().nextBoolean() && terms.size > 0) {
                    // re-use existing term
                    val term = terms[random().nextInt(terms.size)]
                    docs[term]!!.add(i)
                    f.setStringValue(term.utf8ToString())
                } else {
                    val s = TestUtil.randomUnicodeString(random(), 10)
                    val term = BytesRef(s)
                    if (!docs.containsKey(term)) {
                        docs[term] = arrayListOf()
                    }
                    docs[term]!!.add(i)
                    terms.add(term)
                    uniqueTerms.add(term)
                    f.setStringValue(s)
                }
                id.setStringValue("$i")
                w.addDocument(doc)
                if (random().nextInt(4) == 1) {
                    w.commit()
                }
                if (i > 0 && random().nextInt(20) == 1) {
                    val delID = random().nextInt(i)
                    deleted.add(delID)
                    w.deleteDocuments(Term("id", "$delID"))
                    if (VERBOSE) {
                        println("TEST: delete $delID")
                    }
                }
            }

            if (VERBOSE) {
                val termsList = ArrayList(uniqueTerms)
                termsList.sort()
                println("TEST: terms in UTF-8 order:")
                for (b in termsList) {
                    println("  ${UnicodeUtil.toHexString(b.utf8ToString())} $b")
                    for (docID in docs[b]!!) {
                        if (deleted.contains(docID)) {
                            println("    $docID (deleted)")
                        } else {
                            println("    $docID")
                        }
                    }
                }
            }

            val reader = DirectoryReader.open(w)
            w.close()
            if (VERBOSE) {
                println("TEST: reader=$reader")
            }

            val liveDocs: Bits? = MultiBits.getLiveDocs(reader)
            for (delDoc in deleted) {
                assertFalse(requireNotNull(liveDocs).get(delDoc))
            }

            for (i in 0 until 100) {
                val term = terms[random().nextInt(terms.size)]
                if (VERBOSE) {
                    println("TEST: seek term=${UnicodeUtil.toHexString(term.utf8ToString())} $term")
                }

                val postingsEnum =
                    TestUtil.docs(random(), reader, "field", term, null, PostingsEnum.NONE.toInt())
                assertNotNull(postingsEnum)

                for (docID in docs[term]!!) {
                    assertEquals(docID, postingsEnum.nextDoc())
                }
                assertEquals(DocIdSetIterator.NO_MORE_DOCS, postingsEnum.nextDoc())
            }

            reader.close()
            dir.close()
        }
    }

    /*
    private void verify(IndexReader r, String term, List<Integer> expected) throws Exception {
      DocsEnum docs = _TestUtil.docs(random, r,
                                     "field",
                                     new BytesRef(term),
                                     MultiLeafReader.getLiveDocs(r),
                                     null,
                                     false);
      for(int docID : expected) {
        assertEquals(docID, docs.nextDoc());
      }
      assertEquals(docs.NO_MORE_DOCS, docs.nextDoc());
    }
    */

    @Test
    @Throws(Exception::class)
    fun testSeparateEnums() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val d = Document()
        d.add(newStringField("f", "j", Field.Store.NO))
        w.addDocument(d)
        w.commit()
        w.addDocument(d)
        val r = DirectoryReader.open(w)
        w.close()
        val d1 = TestUtil.docs(random(), r, "f", BytesRef("j"), null, PostingsEnum.NONE.toInt())
        val d2 = TestUtil.docs(random(), r, "f", BytesRef("j"), null, PostingsEnum.NONE.toInt())
        assertEquals(0, requireNotNull(d1).nextDoc())
        assertEquals(0, requireNotNull(d2).nextDoc())
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTermDocsEnum() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val d = Document()
        d.add(newStringField("f", "j", Field.Store.NO))
        w.addDocument(d)
        w.commit()
        w.addDocument(d)
        val r = DirectoryReader.open(w)
        w.close()
        val de =
            MultiTerms.getTermPostingsEnum(r, "f", BytesRef("j"), PostingsEnum.FREQS.toInt())
        assertEquals(0, requireNotNull(de).nextDoc())
        assertEquals(1, de.nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, de.nextDoc())
        r.close()
        dir.close()
    }
}
