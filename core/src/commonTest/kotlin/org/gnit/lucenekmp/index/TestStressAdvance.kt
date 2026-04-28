package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestStressAdvance : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testStressAdvance() {
        val numIters = if (TEST_NIGHTLY) 3 else 1
        for (iter in 0..<numIters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }
            val dir: Directory = newDirectory()
            val w = RandomIndexWriter(random(), dir)
            val aDocs: MutableSet<Int> = mutableSetOf()
            val doc = Document()
            val f = newStringField("field", "", Field.Store.NO)
            doc.add(f)
            val idField = newStringField("id", "", Field.Store.YES)
            doc.add(idField)
            val num = atLeast(4097)
            if (VERBOSE) {
                println("\nTEST: numDocs=$num")
            }
            for (id in 0..<num) {
                if (random().nextInt(4) == 3) {
                    f.setStringValue("a")
                    aDocs.add(id)
                } else {
                    f.setStringValue("b")
                }
                idField.setStringValue("" + id)
                w.addDocument(doc)
                if (VERBOSE) {
                    println("\nTEST: doc upto $id")
                }
            }

            w.forceMerge(1)

            val aDocIDs: MutableList<Int> = mutableListOf()
            val bDocIDs: MutableList<Int> = mutableListOf()

            val r: DirectoryReader = w.reader
            val storedFields: StoredFields = r.storedFields()
            val idToDocID = IntArray(r.maxDoc())
            for (docID in idToDocID.indices) {
                val id: Int = storedFields.document(docID).get("id")!!.toInt()
                if (aDocs.contains(id)) {
                    aDocIDs.add(docID)
                } else {
                    bDocIDs.add(docID)
                }
            }
            val te: TermsEnum = getOnlyLeafReader(r).terms("field")!!.iterator()

            var de: PostingsEnum? = null
            for (iter2 in 0..9) {
                if (VERBOSE) {
                    println("\nTEST: iter=$iter iter2=$iter2")
                }
                assertEquals(TermsEnum.SeekStatus.FOUND, te.seekCeil(BytesRef("a")))
                de = TestUtil.docs(random(), te, de, PostingsEnum.NONE.toInt())
                testOne(de, aDocIDs)

                assertEquals(TermsEnum.SeekStatus.FOUND, te.seekCeil(BytesRef("b")))
                de = TestUtil.docs(random(), te, de, PostingsEnum.NONE.toInt())
                testOne(de, bDocIDs)
            }

            w.close()
            r.close()
            dir.close()
        }
    }

    @Throws(Exception::class)
    private fun testOne(docs: PostingsEnum, expected: MutableList<Int>) {
        if (VERBOSE) {
            println("test")
        }
        var upto = -1
        while (upto < expected.size) {
            if (VERBOSE) {
                println("  cycle upto=" + upto + " of " + expected.size)
            }
            val docID: Int
            if (random().nextInt(4) == 1 || upto == expected.size - 1) {
                // test nextDoc()
                if (VERBOSE) {
                    println("    do nextDoc")
                }
                upto++
                docID = docs.nextDoc()
            } else {
                // test advance()
                val inc = TestUtil.nextInt(random(), 1, expected.size - 1 - upto)
                if (VERBOSE) {
                    println("    do advance inc=$inc")
                }
                upto += inc
                docID = docs.advance(expected[upto])
            }
            if (upto == expected.size) {
                if (VERBOSE) {
                    println(
                        "  expect docID=" + DocIdSetIterator.NO_MORE_DOCS + " actual=" + docID
                    )
                }
                assertEquals(DocIdSetIterator.NO_MORE_DOCS, docID)
            } else {
                if (VERBOSE) {
                    println("  expect docID=" + expected[upto] + " actual=" + docID)
                }
                assertTrue(docID != DocIdSetIterator.NO_MORE_DOCS)
                assertEquals(expected[upto], docID)
            }
        }
    }

}