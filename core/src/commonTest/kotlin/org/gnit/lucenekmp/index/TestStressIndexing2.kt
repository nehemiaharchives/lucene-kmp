package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Collections
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.computeIfAbsent
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TestStressIndexing2 : LuceneTestCase() {

    var maxFields: Int = 4
    var bigFieldSize: Int = 10
    var sameFieldOrder: Boolean = false
    var mergeFactor: Int = 3
    var maxBufferedDocs: Int = 3
    var seed: Int = 0
    private val fieldTypes: MutableMap<String, FieldType> = mutableMapOf()
    private val fieldTypesLock = ReentrantLock()

    @Test
    @Throws(Throwable::class)
    fun testRandomIWReader() {
        val dir: Directory = newDirectory() /*newMaybeVirusCheckingDirectory()*/

        // TODO: verify equals using IW.getReader
        val dw : DocsAndWriter = indexRandomIWReader(5, 3, 100, dir)
        val reader: DirectoryReader = DirectoryReader.open(dw.writer)
        dw.writer.commit()
        verifyEquals(random(), reader, dir, "id")
        reader.close()
        dw.writer.close()
        dir.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testRandom() {
        val dir1: Directory = newDirectory() /*newMaybeVirusCheckingDirectory()*/
        val dir2: Directory = newDirectory() /*newMaybeVirusCheckingDirectory()*/
        // mergeFactor=2; maxBufferedDocs=2; Map docs = indexRandom(1, 3, 2, dir1);
        val doReaderPooling = random().nextBoolean()
        val docs = indexRandom(5, 3, 100, dir1, doReaderPooling)
        indexSerial(random(), docs, dir2)

        // verifying verify
        // verifyEquals(dir1, dir1, "id");
        // verifyEquals(dir2, dir2, "id");
        verifyEquals(dir1, dir2, "id")
        dir1.close()
        dir2.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testMultiConfig() {
        // test lots of smaller different params together

        val num = atLeast(3)
        for (i in 0..<num) { // increase iterations for better testing
            if (VERBOSE) {
                println("\n\nTEST: top iter=$i")
            }
            sameFieldOrder = random().nextBoolean()
            mergeFactor = random().nextInt(3) + 2
            maxBufferedDocs = random().nextInt(3) + 2
            val doReaderPooling = random().nextBoolean()
            seed++

            val nThreads = random().nextInt(5) + 1
            val iter = random().nextInt(5) + 1
            val range = random().nextInt(20) + 1
            val dir1: Directory = newDirectory()
            val dir2: Directory = newDirectory()
            if (VERBOSE) {
                println(
                    ("  nThreads="
                            + nThreads
                            + " iter="
                            + iter
                            + " range="
                            + range
                            + " doPooling="
                            + doReaderPooling
                            + " sameFieldOrder="
                            + sameFieldOrder
                            + " mergeFactor="
                            + mergeFactor
                            + " maxBufferedDocs="
                            + maxBufferedDocs)
                )
            }
            val docs = indexRandom(nThreads, iter, range, dir1, doReaderPooling)
            if (VERBOSE) {
                println("TEST: index serial")
            }
            indexSerial(random(), docs, dir2)
            if (VERBOSE) {
                println("TEST: verify")
            }
            verifyEquals(dir1, dir2, "id")
            dir1.close()
            dir2.close()
        }
    }

    lateinit var threads: Array<IndexingThread>
    private val fieldNameComparator: Comparator<IndexableField> = Comparator { o1, o2 ->
        o1.name().compareTo(o2.name())
    }


    // This test avoids using any extra synchronization in the multiple
    // indexing threads to test that IndexWriter does correctly synchronize
    // everything.
    class DocsAndWriter {
        lateinit var docs: MutableMap<String, Document>
        lateinit var writer: IndexWriter
    }

    @Throws(IOException::class, InterruptedException::class)
    fun indexRandomIWReader(nThreads: Int, iterations: Int, range: Int, dir: Directory): DocsAndWriter {
        val docs: MutableMap<String, Document> = mutableMapOf()
        val w: IndexWriter =
            RandomIndexWriter.mockIndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setRAMBufferSizeMB(0.1)
                    .setMaxBufferedDocs(maxBufferedDocs)
                    .setMergePolicy(newLogMergePolicy()),
                random()
            )
        w.commit()
        val lmp: LogMergePolicy = w.config.mergePolicy as LogMergePolicy
        lmp.noCFSRatio = 0.0
        lmp.mergeFactor = mergeFactor

        /*
         * w.setMaxMergeDocs(Integer.MAX_VALUE);
         * w.setMaxFieldLength(10000);
         * w.setRAMBufferSizeMB(1);
         * w.setMergeFactor(10);
         */
        threads = Array(nThreads) { i ->
            IndexingThread().apply {
                this.w = w
                base = 1000000 * i
                this.range = range
                this.iterations = iterations
            }
        }

        for (i in threads.indices) {
            threads[i].start()
        }
        for (i in threads.indices) {
            threads[i].join()
        }

        // w.forceMerge(1);
        // w.close();
        for (i in threads.indices) {
            val th = threads[i]
            th.syncLock.withLock {
                docs.putAll(th.docs)
            }
        }

        TestUtil.checkIndex(dir)
        val dw = DocsAndWriter()
        dw.docs = docs
        dw.writer = w
        return dw
    }

    @Throws(IOException::class, InterruptedException::class)
    fun indexRandom(
        nThreads: Int, iterations: Int, range: Int, dir: Directory, doReaderPooling: Boolean
    ): MutableMap<String, Document> {
        val docs: MutableMap<String, Document> = mutableMapOf()
        val w: IndexWriter =
            RandomIndexWriter.mockIndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setRAMBufferSizeMB(0.1)
                    .setMaxBufferedDocs(maxBufferedDocs)
                    .setReaderPooling(doReaderPooling)
                    .setMergePolicy(newLogMergePolicy()),
                random()
            )
        val lmp: LogMergePolicy = w.config.mergePolicy as LogMergePolicy
        lmp.noCFSRatio = 0.0
        lmp.mergeFactor = mergeFactor

        threads = Array(nThreads) { i ->
            IndexingThread().apply {
                this.w = w
                base = 1000000 * i
                this.range = range
                this.iterations = iterations
            }
        }

        for (i in threads.indices) {
            threads[i].start()
        }
        for (i in threads.indices) {
            threads[i].join()
        }

        // w.forceMerge(1);
        w.close()

        for (i in threads.indices) {
            val th = threads[i]
            th.syncLock.withLock {
                docs.putAll(th.docs)
            }
        }

        // System.out.println("TEST: checkindex");
        TestUtil.checkIndex(dir)

        return docs
    }

    @Throws(IOException::class)
    fun indexSerial(random: Random, docs: MutableMap<String, Document>, dir: Directory) {
        val w: IndexWriter =
            IndexWriter(
                dir,
                newIndexWriterConfig(random, MockAnalyzer(random))
                    .setMergePolicy(newLogMergePolicy())
            )

        // index all docs in a single thread
        val iter: MutableIterator<Document> = docs.values.iterator()
        while (iter.hasNext()) {
            val d = iter.next()
            val fields: ArrayList<IndexableField> = ArrayList<IndexableField>()
            fields.addAll(d.getFields())
            // put fields in same order each time
            fields.sortWith(fieldNameComparator)

            val d1 = Document()
            for (i in fields.indices) {
                d1.add(fields.get(i))
            }
            w.addDocument(d1)
            // System.out.println("indexing "+d1);
        }

        w.close()
    }

    @Throws(Throwable::class)
    fun verifyEquals(r: Random, r1: DirectoryReader, dir2: Directory, idField: String) {
        val r2: DirectoryReader = DirectoryReader.open(dir2)
        verifyEquals(r1, r2, idField)
        r2.close()
    }

    @Throws(Throwable::class)
    fun verifyEquals(dir1: Directory, dir2: Directory, idField: String) {
        val r1: DirectoryReader = DirectoryReader.open(dir1)
        val r2: DirectoryReader = DirectoryReader.open(dir2)
        verifyEquals(r1, r2, idField)
        r1.close()
        r2.close()
    }

    @Throws(Throwable::class)
    private fun printDocs(r: DirectoryReader) {
        for (ctx in r.leaves()) {
            // TODO: improve this
            val sub: LeafReader = ctx.reader()
            val liveDocs: Bits? = sub.liveDocs
            val storedFields: StoredFields = sub.storedFields()
            println("  " + (sub as SegmentReader).segmentInfo)
            for (docID in 0..<sub.maxDoc()) {
                val doc: Document = storedFields.document(docID)
                if (liveDocs == null || liveDocs.get(docID)) {
                    println("    docID=" + docID + " id:" + doc.get("id"))
                } else {
                    println("    DEL docID=" + docID + " id:" + doc.get("id"))
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun nextNonDeletedDoc(it: PostingsEnum, liveDocs: Bits?): Int {
        var doc: Int = it.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS && liveDocs != null && liveDocs.get(doc) == false) {
            doc = it.nextDoc()
        }
        return doc
    }

    @Throws(Throwable::class)
    fun verifyEquals(r1: DirectoryReader, r2: DirectoryReader, idField: String) {
        if (VERBOSE) {
            println("\nr1 docs:")
            printDocs(r1)
            println("\nr2 docs:")
            printDocs(r2)
        }
        if (r1.numDocs() != r2.numDocs()) {
            assert(false) { "r1.numDocs()=" + r1.numDocs() + " vs r2.numDocs()=" + r2.numDocs() }
        }
        val hasDeletes = !(r1.maxDoc() == r2.maxDoc() && r1.numDocs() == r1.maxDoc())

        val r2r1 = IntArray(r2.maxDoc()) // r2 id to r1 id mapping

        // create mapping from id2 space to id2 based on idField
        if (FieldInfos.getIndexedFields(r1).isEmpty()) {
            assertTrue(FieldInfos.getIndexedFields(r2).isEmpty())
            return
        }
        val terms1: Terms? = MultiTerms.getTerms(r1, idField)
        if (terms1 == null) {
            assertTrue(MultiTerms.getTerms(r2, idField) == null)
            return
        }
        val termsEnum: TermsEnum = terms1.iterator()

        val liveDocs1: Bits? = MultiBits.getLiveDocs(r1)
        val liveDocs2: Bits? = MultiBits.getLiveDocs(r2)

        val terms2: Terms? = MultiTerms.getTerms(r2, idField)
        if (terms2 == null) {
            // make sure r1 is in fact empty (eg has only all
            // deleted docs):
            val liveDocs: Bits? = MultiBits.getLiveDocs(r1)
            var docs: PostingsEnum? = null
            while (termsEnum.next() != null) {
                docs = TestUtil.docs(random(), termsEnum, docs, PostingsEnum.NONE.toInt())
                while (nextNonDeletedDoc(docs, liveDocs) != DocIdSetIterator.NO_MORE_DOCS) {
                    fail("r1 is not empty but r2 is")
                }
            }
            return
        }
        var termsEnum2: TermsEnum? = terms2.iterator()

        var termDocs1: PostingsEnum? = null
        var termDocs2: PostingsEnum? = null

        while (true) {
            val term: BytesRef? = termsEnum.next()
            // System.out.println("TEST: match id term=" + term);
            if (term == null) {
                break
            }

            termDocs1 = TestUtil.docs(random(), termsEnum, termDocs1, PostingsEnum.NONE.toInt())
            if (termsEnum2!!.seekExact(term)) {
                termDocs2 = TestUtil.docs(random(), termsEnum2, termDocs2, PostingsEnum.NONE.toInt())
            } else {
                termDocs2 = null
            }

            if (nextNonDeletedDoc(termDocs1, liveDocs1) == DocIdSetIterator.NO_MORE_DOCS) {
                // This doc is deleted and wasn't replaced
                assertTrue(
                    termDocs2 == null
                            || nextNonDeletedDoc(termDocs2, liveDocs2) == DocIdSetIterator.NO_MORE_DOCS
                )
                continue
            }

            val id1 = termDocs1.docID()
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, nextNonDeletedDoc(termDocs1, liveDocs1))

            assertTrue(nextNonDeletedDoc(termDocs2!!, liveDocs2) != DocIdSetIterator.NO_MORE_DOCS)
            val id2 = termDocs2.docID()
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, nextNonDeletedDoc(termDocs2, liveDocs2))

            r2r1[id2] = id1

            // verify stored fields are equivalent
            try {
                verifyEquals(r1.storedFields().document(id1), r2.storedFields().document(id2))
            } catch (t: Throwable) {
                println("FAILED id=$term id1=$id1 id2=$id2 term=$term")
                println("  d1=" + r1.storedFields().document(id1))
                println("  d2=" + r2.storedFields().document(id2))
                throw t
            }

            try {
                // verify term vectors are equivalent
                verifyEquals(r1.termVectors().get(id1), r2.termVectors().get(id2))
            } catch (e: Throwable) {
                println("FAILED id=$term id1=$id1 id2=$id2")
                val tv1: Fields? = r1.termVectors().get(id1)
                println("  d1=$tv1")
                if (tv1 != null) {
                    var dpEnum: PostingsEnum? = null
                    var dEnum: PostingsEnum? = null
                    for (field in tv1) {
                        println("    $field:")
                        val terms3: Terms? = tv1.terms(field)
                        assertNotNull(terms3)
                        val termsEnum3: TermsEnum = terms3.iterator()
                        var term2: BytesRef?
                        while ((termsEnum3.next().also { term2 = it }) != null) {
                            println(
                                "      " + term2!!.utf8ToString() + ": freq=" + termsEnum3.totalTermFreq()
                            )
                            dpEnum = termsEnum3.postings(dpEnum, PostingsEnum.ALL.toInt())
                            if (terms3.hasPositions()) {
                                assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
                                val freq: Int = dpEnum.freq()
                                println("        doc=" + dpEnum.docID() + " freq=" + freq)
                                var posUpto = 0
                                while (posUpto < freq) {
                                    println("          pos=" + dpEnum.nextPosition())
                                    posUpto++
                                }
                            } else {
                                dEnum = TestUtil.docs(random(), termsEnum3, dEnum, PostingsEnum.FREQS.toInt())
                                assertNotNull(dEnum)
                                assertTrue(dEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
                                val freq = dEnum.freq()
                                println("        doc=" + dEnum.docID() + " freq=" + freq)
                            }
                        }
                    }
                }

                val tv2: Fields? = r2.termVectors().get(id2)
                println("  d2=$tv2")
                if (tv2 != null) {
                    var dpEnum: PostingsEnum? = null
                    var dEnum: PostingsEnum? = null
                    for (field in tv2) {
                        println("    $field:")
                        val terms3: Terms? = tv2.terms(field)
                        assertNotNull(terms3)
                        val termsEnum3: TermsEnum = terms3.iterator()
                        var term2: BytesRef?
                        while ((termsEnum3.next().also { term2 = it }) != null) {
                            println(
                                "      " + term2!!.utf8ToString() + ": freq=" + termsEnum3.totalTermFreq()
                            )
                            dpEnum = termsEnum3.postings(dpEnum, PostingsEnum.ALL.toInt())
                            if (dpEnum != null) {
                                assertTrue(dpEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
                                val freq: Int = dpEnum.freq()
                                println("        doc=" + dpEnum.docID() + " freq=" + freq)
                                var posUpto = 0
                                while (posUpto < freq) {
                                    println("          pos=" + dpEnum.nextPosition())
                                    posUpto++
                                }
                            } else {
                                dEnum = TestUtil.docs(random(), termsEnum3, dEnum, PostingsEnum.FREQS.toInt())
                                assertNotNull(dEnum)
                                assertTrue(dEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
                                val freq = dEnum.freq()
                                println("        doc=" + dEnum.docID() + " freq=" + freq)
                            }
                        }
                    }
                }

                throw e
            }
        }

        // System.out.println("TEST: done match id");

        // Verify postings
        // System.out.println("TEST: create te1");
        val fields1Enum: Iterator<String> =
            FieldInfos.getIndexedFields(r1).sorted().iterator()
        val fields2Enum: Iterator<String> =
            FieldInfos.getIndexedFields(r2).sorted().iterator()

        var field1: String? = null
        var field2: String? = null
        var termsEnum1: TermsEnum? = null
        termsEnum2 = null
        var docs1: PostingsEnum? = null
        var docs2: PostingsEnum? = null

        // pack both doc and freq into single element for easy sorting
        val info1 = LongArray(r1.numDocs())
        val info2 = LongArray(r2.numDocs())

        while (true) {
            var term1: BytesRef? = null
            var term2: BytesRef? = null

            // iterate until we get some docs
            var len1: Int
            while (true) {
                len1 = 0
                if (termsEnum1 == null) {
                    if (!fields1Enum.hasNext()) {
                        break
                    }
                    field1 = fields1Enum.next()
                    val terms: Terms? = MultiTerms.getTerms(r1, field1)
                    if (terms == null) {
                        continue
                    }
                    termsEnum1 = terms.iterator()
                }
                term1 = termsEnum1.next()
                if (term1 == null) {
                    // no more terms in this field
                    termsEnum1 = null
                    continue
                }

                // System.out.println("TEST: term1=" + term1);
                docs1 = TestUtil.docs(random(), termsEnum1, docs1, PostingsEnum.FREQS.toInt())
                while (docs1.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    val d = docs1.docID()
                    if (liveDocs1 != null && liveDocs1.get(d) == false) {
                        continue
                    }
                    val f = docs1.freq()
                    info1[len1] = ((d.toLong()) shl 32) or f.toLong()
                    len1++
                }
                if (len1 > 0) break
            }

            // iterate until we get some docs
            var len2: Int
            while (true) {
                len2 = 0
                if (termsEnum2 == null) {
                    if (!fields2Enum.hasNext()) {
                        break
                    }
                    field2 = fields2Enum.next()
                    val terms: Terms? = MultiTerms.getTerms(r2, field2)
                    if (terms == null) {
                        continue
                    }
                    termsEnum2 = terms.iterator()
                }
                term2 = termsEnum2.next()
                if (term2 == null) {
                    // no more terms in this field
                    termsEnum2 = null
                    continue
                }

                // System.out.println("TEST: term1=" + term1);
                docs2 = TestUtil.docs(random(), termsEnum2, docs2, PostingsEnum.FREQS.toInt())
                while (docs2.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    if (liveDocs2 != null && liveDocs2.get(docs2.docID()) == false) {
                        continue
                    }
                    val d = r2r1[docs2.docID()]
                    val f = docs2.freq()
                    info2[len2] = ((d.toLong()) shl 32) or f.toLong()
                    len2++
                }
                if (len2 > 0) break
            }

            assertEquals(len1, len2)
            if (len1 == 0) break // no more terms


            assertEquals(field1, field2)
            assertEquals(term1, term2)

            if (!hasDeletes) assertEquals(termsEnum1!!.docFreq(), termsEnum2!!.docFreq())

            assertEquals(term1, term2, "len1=$len1 len2=$len2 deletes=$hasDeletes")

            // sort info2 to get it into ascending docid
            Arrays.sort(info2, 0, len2)

            // now compare
            for (i in 0..<len1) {
                assertEquals(
                    info1[i],
                    info2[i],
                    ("i="
                            + i
                            + " len="
                            + len1
                            + " d1="
                            + (info1[i] ushr 32)
                            + " f1="
                            + (info1[i] and Int.Companion.MAX_VALUE.toLong())
                            + " d2="
                            + (info2[i] ushr 32)
                            + " f2="
                            + (info2[i] and Int.Companion.MAX_VALUE.toLong())
                            + " field="
                            + field1
                            + " term="
                            + term1!!.utf8ToString())
                )
            }
        }
    }

    fun verifyEquals(d1: Document, d2: Document) {
        val ff1: MutableList<IndexableField> = ArrayList<IndexableField>(d1.getFields())
        val ff2: MutableList<IndexableField> = ArrayList<IndexableField>(d2.getFields())

        ff1.sortWith(fieldNameComparator)
        ff2.sortWith(fieldNameComparator)

        assertEquals(ff1.size, ff2.size, "$ff1 : $ff2")

        for (i in ff1.indices) {
            val f1: IndexableField = ff1.get(i)
            val f2: IndexableField = ff2.get(i)
            if (f1.binaryValue() != null) {
                checkNotNull(f2.binaryValue())
            } else {
                val s1: String? = f1.stringValue()
                val s2: String? = f2.stringValue()
                assertEquals(s1, s2, "$ff1 : $ff2")
            }
        }
    }

    @Throws(IOException::class)
    fun verifyEquals(d1: Fields?, d2: Fields?) {
        if (d1 == null) {
            assertTrue(d2 == null || d2.size() == 0)
            return
        }
        assertNotNull(d2)

        val fieldsEnum2: MutableIterator<String> = d2.iterator()

        for (field1 in d1) {
            val field2 = fieldsEnum2.next()
            assertEquals(field1, field2)

            val terms1: Terms? = d1.terms(field1)
            assertNotNull(terms1)
            val termsEnum1: TermsEnum = terms1.iterator()

            val terms2: Terms? = d2.terms(field2)
            assertNotNull(terms2)
            val termsEnum2: TermsEnum = terms2.iterator()

            var dpEnum1: PostingsEnum? = null
            var dpEnum2: PostingsEnum? = null
            var dEnum1: PostingsEnum? = null
            var dEnum2: PostingsEnum? = null

            var term1: BytesRef?
            while ((termsEnum1.next().also { term1 = it }) != null) {
                val term2: BytesRef? = termsEnum2.next()
                assertEquals(term1, term2)
                assertEquals(termsEnum1.totalTermFreq(), termsEnum2.totalTermFreq())

                dpEnum1 = termsEnum1.postings(dpEnum1, PostingsEnum.ALL.toInt())
                dpEnum2 = termsEnum2.postings(dpEnum2, PostingsEnum.ALL.toInt())

                if (terms1.hasPositions()) {
                    assertTrue(terms2.hasPositions())
                    val docID1: Int = dpEnum1!!.nextDoc()
                    dpEnum2!!.nextDoc()
                    // docIDs are not supposed to be equal
                    // int docID2 = dpEnum2.nextDoc();
                    // assertEquals(docID1, docID2);
                    assertTrue(docID1 != DocIdSetIterator.NO_MORE_DOCS)

                    val freq1: Int = dpEnum1.freq()
                    val freq2: Int = dpEnum2.freq()
                    assertEquals(freq1, freq2)

                    for (posUpto in 0..<freq1) {
                        val pos1: Int = dpEnum1.nextPosition()
                        val pos2: Int = dpEnum2.nextPosition()
                        assertEquals(pos1, pos2)
                        if (terms1.hasOffsets()) {
                            assertTrue(terms2.hasOffsets())
                            assertEquals(dpEnum1.startOffset(), dpEnum2.startOffset())
                            assertEquals(dpEnum1.endOffset(), dpEnum2.endOffset())
                        }
                    }
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum1.nextDoc())
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum2.nextDoc())
                } else {
                    dEnum1 = TestUtil.docs(random(), termsEnum1, dEnum1, PostingsEnum.FREQS.toInt())
                    dEnum2 = TestUtil.docs(random(), termsEnum2, dEnum2, PostingsEnum.FREQS.toInt())
                    assertNotNull(dEnum1)
                    assertNotNull(dEnum2)
                    val docID1 = dEnum1.nextDoc()
                    dEnum2.nextDoc()
                    // docIDs are not supposed to be equal
                    // int docID2 = dEnum2.nextDoc();
                    // assertEquals(docID1, docID2);
                    assertTrue(docID1 != DocIdSetIterator.NO_MORE_DOCS)
                    val freq1 = dEnum1.freq()
                    val freq2 = dEnum2.freq()
                    assertEquals(freq1, freq2)
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS, dEnum1.nextDoc())
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS, dEnum2.nextDoc())
                }
            }

            assertNull(termsEnum2.next())
        }
        assertFalse(fieldsEnum2.hasNext())
    }

    inner class IndexingThread : Thread() {
        var w: IndexWriter? = null
        var base: Int = 0
        var range: Int = 0
        var iterations: Int = 0
        var docs: MutableMap<String, Document> = mutableMapOf()
        var r: Random? = null
        val syncLock = ReentrantLock()

        fun nextInt(lim: Int): Int {
            return r!!.nextInt(lim)
        }

        // start is inclusive and end is exclusive
        fun nextInt(start: Int, end: Int): Int {
            return start + r!!.nextInt(end - start)
        }

        var buffer: CharArray = CharArray(100)

        fun addUTF8Token(start: Int): Int {
            val end = start + nextInt(20)
            if (buffer.size < 1 + end) {
                val newBuffer = CharArray(((1 + end) * 1.25).toInt())
                System.arraycopy(buffer, 0, newBuffer, 0, buffer.size)
                buffer = newBuffer
            }

            var i = start
            while (i < end) {
                val t = nextInt(5)
                if (0 == t && i < end - 1) {
                    // Make a surrogate pair
                    // High surrogate
                    buffer[i++] = nextInt(0xd800, 0xdc00).toChar()
                    // Low surrogate
                    buffer[i] = nextInt(0xdc00, 0xe000).toChar()
                } else if (t <= 1) buffer[i] = nextInt(0x80).toChar()
                else if (2 == t) buffer[i] = nextInt(0x80, 0x800).toChar()
                else if (3 == t) buffer[i] = nextInt(0x800, 0xd800).toChar()
                else if (4 == t) buffer[i] = nextInt(0xe000, 0xffff).toChar()
                i++
            }
            buffer[end] = ' '
            return 1 + end
        }

        fun getString(nTokens: Int): String {
            var nTokens = nTokens
            nTokens = if (nTokens != 0) nTokens else r!!.nextInt(4) + 1

            // Half the time make a random UTF8 string
            if (r!!.nextBoolean()) return getUTF8String(nTokens)

            // avoid StringBuffer because it adds extra synchronization.
            val arr = CharArray(nTokens * 2)
            for (i in 0..<nTokens) {
                arr[i * 2] = ('A'.code + r!!.nextInt(10)).toChar()
                arr[i * 2 + 1] = ' '
            }
            return String.fromCharArray(arr)
        }

        fun getUTF8String(nTokens: Int): String {
            var upto = 0
            Arrays.fill(buffer, 0.toChar())
            for (i in 0..<nTokens) upto = addUTF8Token(upto)
            return String.fromCharArray(buffer, 0, upto)
        }

        val idString: String
            get() = (base + nextInt(range)).toString()

        @Throws(IOException::class)
        fun indexDoc() {
            val d = Document()

            val customType1 = FieldType(TextField.TYPE_STORED)
            customType1.setTokenized(false)
            customType1.setOmitNorms(true)

            val fields: ArrayList<Field> = ArrayList()
            val idString = this.idString
            val idField: Field = newField("id", idString, customType1)
            fields.add(idField)

            val nFields = nextInt(maxFields)
            for (i in 0..<nFields) {
                val fieldName = "f" + nextInt(100)
                // Use the same field type if we already added this field to the index
                val fieldType: FieldType = fieldTypesLock.withLock {
                    fieldTypes.computeIfAbsent(
                        fieldName
                    ) { _: String ->
                        val ft = FieldType()
                        when (nextInt(4)) {
                            0 -> {}
                            1 -> ft.setStoreTermVectors(true)
                            2 -> {
                                ft.setStoreTermVectors(true)
                                ft.setStoreTermVectorPositions(true)
                            }

                            3 -> {
                                ft.setStoreTermVectors(true)
                                ft.setStoreTermVectorOffsets(true)
                            }
                        }
                        when (nextInt(4)) {
                            0 -> {
                                ft.setStored(true)
                                ft.setOmitNorms(true)
                                ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
                            }

                            1 -> {
                                ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
                                ft.setTokenized(true)
                            }

                            2 -> {
                                ft.setStored(true)
                                ft.setStoreTermVectors(false)
                                ft.setStoreTermVectorOffsets(false)
                                ft.setStoreTermVectorPositions(false)
                            }

                            3 -> {
                                ft.setStored(true)
                                ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
                                ft.setTokenized(true)
                            }
                        }
                        ft.freeze()
                        ft
                    }!!
                }
                var nTokens = nextInt(3)
                nTokens = if (nTokens < 2) nTokens else bigFieldSize
                fields.add(newField(fieldName, getString(nTokens), fieldType))
            }

            if (sameFieldOrder) {
                fields.sortWith(fieldNameComparator)
            } else {
                // random placement of id field also
                Collections.swap(fields, nextInt(fields.size), 0)
            }

            for (i in fields.indices) {
                d.add(fields.get(i))
            }
            if (VERBOSE) {
                println(currentThread().getName() + ": indexing id:" + idString)
            }
            w!!.updateDocument(Term("id", idString), d)
            // System.out.println(Thread.currentThread().getName() + ": indexing "+d);
            syncLock.withLock {
                docs[idString] = d
            }
        }

        @Throws(IOException::class)
        fun deleteDoc() {
            val idString = this.idString
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": del id:" + idString)
            }
            w!!.deleteDocuments(Term("id", idString))
            syncLock.withLock {
                docs.remove(idString)
            }
        }

        @Throws(IOException::class)
        fun deleteByQuery() {
            val idString = this.idString
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": del query id:" + idString)
            }
            w!!.deleteDocuments(TermQuery(Term("id", idString)))
            syncLock.withLock {
                docs.remove(idString)
            }
        }

        override fun run() {
            try {
                r = Random((base + range + seed).toLong())
                for (i in 0..<iterations) {
                    val what = nextInt(100)
                    if (what < 5) {
                        deleteDoc()
                    } else if (what < 10) {
                        deleteByQuery()
                    } else {
                        indexDoc()
                    }
                }
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }

            syncLock.withLock {
                docs.size
            }
        }
    }
}
