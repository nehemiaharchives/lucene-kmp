package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.ByteBuffersDirectory
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

    private val EMPTY: PostingsEnum = object : PostingsEnum() {
        override fun freq(): Int = 0
        override fun nextPosition(): Int = 0
        override fun startOffset(): Int = 0
        override fun endOffset(): Int = 0
        override val payload: BytesRef? = null
        override fun docID(): Int = DocIdSetIterator.NO_MORE_DOCS
        override fun nextDoc(): Int = DocIdSetIterator.NO_MORE_DOCS
        override fun advance(target: Int): Int = DocIdSetIterator.NO_MORE_DOCS
        override fun cost(): Long = 0
    }

    private fun newDirectory(): Directory = ByteBuffersDirectory()

    @Test
    fun testRandom() {
        val num = atLeast(2)
        for (iter in 0 until num) {
            if (VERBOSE) {
                println("TEST: iter=$iter")
            }
            newDirectory().use { dir ->
                val w = IndexWriter(
                    dir,
                    IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(
                        object : FilterMergePolicy(NoMergePolicy.INSTANCE) {
                            override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                                // we can do this because we use NoMergePolicy (and dont merge to "nothing")
                                return true
                            }
                        }
                    )
                )
                val docs = HashMap<BytesRef, MutableList<Int>>()
                val deleted = HashSet<Int>()
                val terms = ArrayList<BytesRef>()
                val numDocs = TestUtil.nextInt(random(), 1, 100 * RANDOM_MULTIPLIER)
                val doc = Document()
                val f = StringField("field", "", Field.Store.NO)
                doc.add(f)
                val id = StringField("id", "", Field.Store.NO)
                doc.add(id)

                val onlyUniqueTerms = random().nextBoolean()
                if (VERBOSE) {
                    println("TEST: onlyUniqueTerms=$onlyUniqueTerms numDocs=$numDocs")
                }
                val uniqueTerms = HashSet<BytesRef>()
                for (i in 0 until numDocs) {
                    if (!onlyUniqueTerms && random().nextBoolean() && terms.size > 0) {
                        // re-use existing term
                        val term = terms[random().nextInt(terms.size)]
                        docs.getOrPut(term) { ArrayList() }.add(i)
                        f.setStringValue(term.utf8ToString())
                    } else {
                        val s = TestUtil.randomUnicodeString(random(), 10)
                        val term = BytesRef(s)
                        docs.getOrPut(term) { ArrayList() }.add(i)
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

                val liveDocs = MultiBits.getLiveDocs(reader)
                for (delDoc in deleted) {
                    assertFalse(liveDocs!!.get(delDoc))
                }

                for (i in 0 until 100) {
                    val term = terms[random().nextInt(terms.size)]
                    if (VERBOSE) {
                        println("TEST: seek term=" + UnicodeUtil.toHexString(term.utf8ToString()) + " " + term)
                    }
                    val postingsEnum =
                        TestUtil.docs(random(), reader, "field", term, EMPTY, PostingsEnum.NONE.toInt())
                    assertNotNull(postingsEnum)
                    for (docID in docs[term]!!) {
                        assertEquals(docID, postingsEnum!!.nextDoc())
                    }
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS, postingsEnum!!.nextDoc())
                }

                reader.close()
            }
        }
    }

    @Test
    fun testSeparateEnums() {
        newDirectory().use { dir ->
            val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
            val d = Document()
            d.add(StringField("f", "j", Field.Store.NO))
            w.addDocument(d)
            w.commit()
            w.addDocument(d)
            val r = DirectoryReader.open(w)
            w.close()
            val d1 = TestUtil.docs(random(), r, "f", BytesRef("j"), EMPTY, PostingsEnum.NONE.toInt())
            val d2 = TestUtil.docs(random(), r, "f", BytesRef("j"), EMPTY, PostingsEnum.NONE.toInt())
            assertEquals(0, d1!!.nextDoc())
            assertEquals(0, d2!!.nextDoc())
            r.close()
        }
    }

    @Test
    fun testTermDocsEnum() {
        newDirectory().use { dir ->
            val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
            val d = Document()
            d.add(StringField("f", "j", Field.Store.NO))
            w.addDocument(d)
            w.commit()
            w.addDocument(d)
            val r = DirectoryReader.open(w)
            w.close()
            val de = MultiTerms.getTermPostingsEnum(r, "f", BytesRef("j"), PostingsEnum.FREQS.toInt())
            assertEquals(0, de!!.nextDoc())
            assertEquals(1, de.nextDoc())
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, de.nextDoc())
            r.close()
        }
    }
}
