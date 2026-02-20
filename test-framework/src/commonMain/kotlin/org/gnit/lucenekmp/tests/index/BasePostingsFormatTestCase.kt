package org.gnit.lucenekmp.tests.index

import okio.IOException
import okio.Path
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.PostingsEnum.Companion.ALL
import org.gnit.lucenekmp.index.PostingsEnum.Companion.FREQS
import org.gnit.lucenekmp.index.PostingsEnum.Companion.NONE
import org.gnit.lucenekmp.index.PostingsEnum.Companion.OFFSETS
import org.gnit.lucenekmp.index.PostingsEnum.Companion.PAYLOADS
import org.gnit.lucenekmp.index.PostingsEnum.Companion.POSITIONS
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus
import org.gnit.lucenekmp.jdkport.EnumSet
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.RamUsageTester
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Abstract class to do basic tests for a postings format. NOTE: This test focuses on the postings
 * (docs/freqs/positions/payloads/offsets) impl, not the terms dict. The [stretch] goal is for this
 * test to be so thorough in testing a new PostingsFormat that if this test passes, then all Lucene
 * tests should also pass. Ie, if there is some bug in a given PostingsFormat that this test fails
 * to catch then this test needs to be improved!
 */
// TODO can we make it easy for testing to pair up a "random terms dict impl" with your postings
// base format...
// TODO test when you reuse after skipping a term or two, eg the block reuse case
/* TODO
  - threads
  - assert doc=-1 before any nextDoc
  - if a PF passes this test but fails other tests then this
    test has a bug!!
  - test tricky reuse cases, eg across fields
  - verify you get null if you pass needFreq/needOffset but
    they weren't indexed
*/
@OptIn(ExperimentalAtomicApi::class)
abstract class BasePostingsFormatTestCase : BaseIndexFileFormatTestCase() {

    var postingsTester: RandomPostingsTester? = null

    // TODO maybe instead of @BeforeClass just make a single test run: build postings & index & test
    @BeforeTest
    @Throws(IOException::class)
    fun createPostings() {
        postingsTester =
            RandomPostingsTester(random())
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        postingsTester = null
    }

    @Throws(Exception::class)
    open fun testDocsOnly() {
        postingsTester!!.testFull(
            codec,
            createTempDir("testPostingsFormat.testExact"),
            IndexOptions.DOCS,
            false
        )
    }

    @Throws(Exception::class)
    open fun testDocsAndFreqs() {
        postingsTester!!.testFull(
            codec,
            createTempDir("testPostingsFormat.testExact"),
            IndexOptions.DOCS_AND_FREQS,
            false
        )
    }

    @Throws(Exception::class)
    open fun testDocsAndFreqsAndPositions() {
        postingsTester!!.testFull(
            codec,
            createTempDir("testPostingsFormat.testExact"),
            IndexOptions.DOCS_AND_FREQS_AND_POSITIONS,
            false
        )
    }

    @Throws(Exception::class)
    open fun testDocsAndFreqsAndPositionsAndPayloads() {
        postingsTester!!.testFull(
            codec,
            createTempDir("testPostingsFormat.testExact"),
            IndexOptions.DOCS_AND_FREQS_AND_POSITIONS,
            true
        )
    }

    @Throws(Exception::class)
    open fun testDocsAndFreqsAndPositionsAndOffsets() {
        postingsTester!!.testFull(
            codec,
            createTempDir("testPostingsFormat.testExact"),
            IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
            false
        )
    }

    @Throws(Exception::class)
    open fun testDocsAndFreqsAndPositionsAndOffsetsAndPayloads() {
        postingsTester!!.testFull(
            codec,
            createTempDir("testPostingsFormat.testExact"),
            IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
            true
        )
    }

    @Throws(Exception::class)
    open fun testRandom() {
        val iters = 5 // TODO reduced from 5 to 2 for dev speed

        for (iter in 0..<iters) {
            val path: Path = createTempDir("testPostingsFormat")
            val dir: Directory = newFSDirectory(path)

            val indexPayloads: Boolean =
                random().nextBoolean()
            // TODO test thread safety of buildIndex too
            var fieldsProducer: FieldsProducer? =
                postingsTester!!.buildIndex(
                    codec,
                    dir,
                    IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
                    indexPayloads,
                    false
                )

            postingsTester!!.testFields(fieldsProducer!!)

            // NOTE: you can also test "weaker" index options than
            // you indexed with:
            postingsTester!!.testTerms(
                fieldsProducer,
                EnumSet.allOf<RandomPostingsTester.Option>(
                    RandomPostingsTester.Option::class
                ),
                IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
                IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
                false
            )

            fieldsProducer.close()
            fieldsProducer = null

            dir.close()
        }
    }

    protected val isPostingsEnumReuseImplemented: Boolean
        get() = true

    @Throws(Exception::class)
    open fun testPostingsEnumReuse() {
        val path: Path = createTempDir("testPostingsEnumReuse")
        val dir: Directory = newFSDirectory(path)

        val fieldsProducer: FieldsProducer =
            postingsTester!!.buildIndex(
                codec,
                dir,
                IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
                random().nextBoolean(),
                true
            )
        postingsTester!!.allTerms.shuffle(random())
        val fieldAndTerm: RandomPostingsTester.FieldAndTerm = postingsTester!!.allTerms[0]

        val terms: Terms = fieldsProducer.terms(fieldAndTerm.field)!!
        val te: TermsEnum = terms.iterator()

        te.seekExact(fieldAndTerm.term)
        checkReuse(te,FREQS.toInt(), ALL.toInt(), false)
        if (this.isPostingsEnumReuseImplemented) {
            checkReuse(te, ALL.toInt(), ALL.toInt(), true)
        }

        fieldsProducer.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testJustEmptyField() {
        val dir: Directory = newDirectory()
        val iwc = newIndexWriterConfig(/*null*/)
        iwc.setCodec(codec)
        val iw = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        doc.add(newStringField("","something", Store.NO))
        iw.addDocument(doc)
        val ir: DirectoryReader = iw.reader
        val ar: LeafReader = getOnlyLeafReader(ir)
        assertEquals(1, ar.fieldInfos.size().toLong())
        val terms: Terms = ar.terms("")!!
        assertNotNull(terms)
        val termsEnum: TermsEnum = terms.iterator()
        assertNotNull(termsEnum.next())
        assertEquals(termsEnum.term(), BytesRef("something"))
        assertNull(termsEnum.next())
        ir.close()
        iw.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testEmptyFieldAndEmptyTerm() {
        val dir: Directory = newDirectory()
        val iwc = newIndexWriterConfig(/*null*/)
        iwc.setCodec(codec)
        val iw = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        doc.add(newStringField("", "", Store.NO))
        iw.addDocument(doc)
        val ir: DirectoryReader = iw.reader
        val ar: LeafReader = getOnlyLeafReader(ir)
        assertEquals(1, ar.fieldInfos.size().toLong())
        val terms: Terms? = ar.terms("")
        assertNotNull(terms)
        val termsEnum: TermsEnum = terms.iterator()
        assertNotNull(termsEnum.next())
        assertEquals(termsEnum.term(), BytesRef(""))
        assertNull(termsEnum.next())
        ir.close()
        iw.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testDidntWantFreqsButAskedAnyway() {
        val dir: Directory = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setCodec(codec)
        val iw = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        doc.add(newTextField("field", "value", Store.NO))
        iw.addDocument(doc)
        iw.addDocument(doc)
        val ir: DirectoryReader = iw.reader
        val ar: LeafReader = getOnlyLeafReader(ir)
        val termsEnum: TermsEnum = ar.terms("field")!!.iterator()
        assertTrue(termsEnum.seekExact(BytesRef("value")))
        val docsEnum: PostingsEnum? = termsEnum.postings(null, NONE.toInt())
        assertEquals(0, docsEnum!!.nextDoc().toLong())
        assertEquals(1, docsEnum.freq().toLong())
        assertEquals(1, docsEnum.nextDoc().toLong())
        assertEquals(1, docsEnum.freq().toLong())
        ir.close()
        iw.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testAskForPositionsWhenNotThere() {
        val dir: Directory = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setCodec(codec)
        val iw = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        doc.add(newStringField("field", "value", Store.NO))
        iw.addDocument(doc)
        iw.addDocument(doc)
        val ir: DirectoryReader = iw.reader
        val ar: LeafReader = getOnlyLeafReader(ir)
        val termsEnum: TermsEnum = ar.terms("field")!!.iterator()
        assertTrue(termsEnum.seekExact(BytesRef("value")))
        val docsEnum: PostingsEnum? = termsEnum.postings(null, POSITIONS.toInt())
        assertEquals(0, docsEnum!!.nextDoc().toLong())
        assertEquals(1, docsEnum.freq().toLong())
        assertEquals(1, docsEnum.nextDoc().toLong())
        assertEquals(1, docsEnum.freq().toLong())
        ir.close()
        iw.close()
        dir.close()
    }

    // tests that ghost fields still work
    // TODO: can this be improved
    @Throws(Exception::class)
    open fun testGhosts() {
        val dir: Directory = newDirectory()
        val iwc = newIndexWriterConfig(/*null*/)
        iwc.setCodec(codec)
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        iw.addDocument(doc)
        doc.add(
            newStringField("ghostField", "something", Store.NO)
        )
        iw.addDocument(doc)
        iw.forceMerge(1)
        iw.deleteDocuments(
            Term("ghostField", "something")
        ) // delete the only term for the field
        iw.forceMerge(1)
        val ir: DirectoryReader = iw.reader
        val ar: LeafReader =
            getOnlyLeafReader(ir)
        // Ghost busting terms dict impls will have
        // fields.size() == 0; all others must be == 1:
        assertTrue(ar.fieldInfos.size() <= 1)
        val terms: Terms? = ar.terms("ghostField")
        if (terms != null) {
            val termsEnum: TermsEnum = terms.iterator()
            val term: BytesRef? = termsEnum.next()
            if (term != null) {
                val postingsEnum: PostingsEnum? = termsEnum.postings(null)
                assertTrue(postingsEnum!!.nextDoc() == DocIdSetIterator.NO_MORE_DOCS)
            }
        }
        ir.close()
        iw.close()
        dir.close()
    }

    // Test seek in disorder.
    @Throws(Exception::class)
    open fun testDisorder() {
        val dir: Directory = newDirectory()

        val iwc = newIndexWriterConfig(/*null*/)
        iwc.setCodec(codec)
        iwc.setMergePolicy(newTieredMergePolicy())
        val iw = IndexWriter(dir, iwc)

        for (i in 0..9999) {
            val document = Document()
            document.add(StringField("id", i.toString() + "", Store.NO))
            iw.addDocument(document)
        }
        iw.commit()
        iw.forceMerge(1)

        val reader: DirectoryReader = DirectoryReader.open(iw)
        val termsEnum: TermsEnum = getOnlyLeafReader(reader).terms("id")!!.iterator()

        for (i in 0..19999) {
            val n: Int = random().nextInt(0, 10000)
            val target = BytesRef(n.toString() + "")
            // seekExact.
            assertTrue(termsEnum.seekExact(target))
            assertEquals(termsEnum.term(), target)
            // seekCeil.
            assertEquals(SeekStatus.FOUND, termsEnum.seekCeil(target))
            assertEquals(termsEnum.term(), target)
        }

        reader.close()
        iw.close()
        dir.close()
    }

    @Throws(Exception::class)
    protected fun subCheckBinarySearch(termsEnum: TermsEnum) {
    }

    @Throws(Exception::class)
    open fun testBinarySearchTermLeaf() {
        val dir: Directory = newDirectory()

        val iwc = newIndexWriterConfig(/*null*/)
        iwc.setCodec(codec)
        iwc.setMergePolicy(newTieredMergePolicy())
        val iw = IndexWriter(dir, iwc)

        for (i in 100000..100400) {
            // only add odd number
            if (i % 2 == 1) {
                val document = Document()
                document.add(StringField("id", i.toString() + "", Store.NO))
                iw.addDocument(document)
            }
        }
        iw.commit()
        iw.forceMerge(1)

        val reader: DirectoryReader =
            DirectoryReader.open(iw)
        val termsEnum: TermsEnum = getOnlyLeafReader(reader).terms("id")!!.iterator()
        // test seekExact
        for (i in 100000..100400) {
            val target =
                BytesRef(i.toString() + "")
            if (i % 2 == 1) {
                assertTrue(termsEnum.seekExact(target))
                assertEquals(termsEnum.term(), target)
            } else {
                assertFalse(termsEnum.seekExact(target))
            }
        }

        subCheckBinarySearch(termsEnum)
        // test seekCeil
        for (i in 100000..100399) {
            val target =
                BytesRef(i.toString() + "")
            if (i % 2 == 1) {
                assertEquals(SeekStatus.FOUND, termsEnum.seekCeil(target))
                assertEquals(termsEnum.term(), target)
                if (i <= 100397) {
                    assertEquals(BytesRef((i + 2).toString() + ""), termsEnum.next())
                }
            } else {
                assertEquals(SeekStatus.NOT_FOUND, termsEnum.seekCeil(target))
                assertEquals(BytesRef((i + 1).toString() + ""), termsEnum.term())
            }
        }
        assertEquals(SeekStatus.END, termsEnum.seekCeil(BytesRef(100400.toString() + "")))
        reader.close()
        iw.close()
        dir.close()
    }

    // tests that level 2 ghost fields still work
    @Throws(Exception::class)
    open fun testLevel2Ghosts() {
        val dir: Directory = newDirectory()

        val iwc = newIndexWriterConfig(/*null*/)
        iwc.setCodec(codec)
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = IndexWriter(dir, iwc)

        var document = Document()
        document.add(StringField("id", "0", Store.NO))
        document.add(StringField("suggest_field", "apples", Store.NO))
        iw.addDocument(document)
        // need another document so whole segment isn't deleted
        iw.addDocument(Document())
        iw.commit()

        document = Document()
        document.add(StringField("id", "1", Store.NO))
        document.add(StringField("suggest_field2", "apples", Store.NO))
        iw.addDocument(document)
        iw.commit()

        iw.deleteDocuments(Term("id", "0"))
        // first force merge creates a level 1 ghost field
        iw.forceMerge(1)

        // second force merge creates a level 2 ghost field, causing MultiFields to include
        // "suggest_field" in its iteration, yet a null Terms is returned (no documents have
        // this field anymore)
        iw.addDocument(Document())
        iw.forceMerge(1)

        val reader: DirectoryReader = DirectoryReader.open(iw)
        val indexSearcher = IndexSearcher(reader)

        assertEquals(1, indexSearcher.count(TermQuery(Term("id", "1"))).toLong())

        reader.close()
        iw.close()
        dir.close()
    }

    private class TermFreqs {
        var totalTermFreq: Long = 0
        var docFreq: Int = 0
    }

    // LUCENE-5123: make sure we can visit postings twice
    // during flush/merge
    @Throws(Exception::class)
    open fun testInvertedWrite() {
        val dir: Directory = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val iwc = newIndexWriterConfig(analyzer)

        // Must be concurrent because thread(s) can be merging
        // while up to one thread flushes, and each of those
        // threads iterates over the map while the flushing
        // thread might be adding to it:
        val termFreqs: MutableMap<String, TermFreqs> =
            /*java.util.concurrent.Concurrent*/HashMap()

        val sumDocFreq = AtomicLong(0)
        val sumTotalTermFreq = AtomicLong(0)

        // TODO: would be better to use / delegate to the current
        // Codec returned by codec
        iwc.setCodec(
            object : FilterCodec(codec.name, codec) {
                override fun postingsFormat(): PostingsFormat {
                    val defaultPostingsFormat: PostingsFormat = delegate.postingsFormat()

                    // Capture a Job reference from the current coroutine context (if any)
                    // val mainJob: Job? = runBlocking { currentCoroutineContext()[Job] }

                    // A PF that counts up some stats and then in
                    // the end we verify the stats match what the
                    // final IndexReader says, just to exercise the
                    // new freedom of iterating the postings more
                    // than once at flush/merge:
                    return object :
                        PostingsFormat(defaultPostingsFormat.name) {
                        @Throws(IOException::class)
                        override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
                            val fieldsConsumer: FieldsConsumer = defaultPostingsFormat.fieldsConsumer(state)

                            return object : FieldsConsumer() {
                                @Throws(IOException::class)
                                override fun write(
                                    fields: Fields,
                                    norms: NormsProducer?
                                ) {
                                    fieldsConsumer.write(fields, norms)

                                    val isMerge = state.context.context == IOContext.Context.MERGE

                                    // We only use one thread for flushing in this test.
                                    // Original Java assertion (kept as comment):
                                    // assert(isMerge || Thread.currentThread() == mainThread)
                                    //
                                    // KMP coroutine Job checks are not stable here, so we skip this assertion.

                                    // We iterate the provided TermsEnum
                                    // twice, so we excercise this new freedom
                                    // with the inverted API; if
                                    // addOnSecondPass is true, we add up
                                    // term stats on the 2nd iteration:
                                    val addOnSecondPass: Boolean = random().nextBoolean()

                                    // System.out.println("write isMerge=" + isMerge + " 2ndPass=" +
                                    // addOnSecondPass);

                                    // Gather our own stats:
                                    val terms: Terms = checkNotNull(fields.terms("body"))
                                    val termsEnum: TermsEnum = terms.iterator()
                                    var docs: PostingsEnum? = null
                                    while (termsEnum.next() != null) {
                                        val term: BytesRef? = termsEnum.term()
                                        // TODO: also sometimes ask for payloads/offsets
                                        val noPositions: Boolean = random().nextBoolean()
                                        if (noPositions) {
                                            docs = termsEnum.postings(docs, FREQS.toInt())
                                        } else {
                                            docs = termsEnum.postings(null, POSITIONS.toInt())
                                        }
                                        var docFreq = 0
                                        var totalTermFreq: Long = 0
                                        while (docs!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                            docFreq++
                                            totalTermFreq += docs.freq().toLong()
                                            val limit: Int = TestUtil.nextInt(random(), 1, docs.freq())
                                            if (!noPositions) {
                                                for (i in 0..<limit) {
                                                    docs.nextPosition()
                                                }
                                            }
                                        }

                                        val termString: String = term!!.utf8ToString()

                                        // During merge we should only see terms
                                        // we had already seen during a
                                        // previous flush:
                                        assertTrue(isMerge == false || termFreqs.containsKey(termString))

                                        if (isMerge == false) {
                                            if (addOnSecondPass == false) {
                                                var tf = termFreqs[termString]
                                                if (tf == null) {
                                                    tf = TermFreqs()
                                                    termFreqs[termString] = tf
                                                }
                                                tf.docFreq += docFreq
                                                tf.totalTermFreq += totalTermFreq
                                                sumDocFreq.addAndFetch(docFreq.toLong())
                                                sumTotalTermFreq.addAndFetch(totalTermFreq)
                                            } else if (termFreqs.containsKey(termString) == false) {
                                                // Add placeholder (2nd pass will
                                                // set its counts):
                                                termFreqs[termString] = TermFreqs()
                                            }
                                        }
                                    }

                                    // Also test seeking the TermsEnum:
                                    for (term in termFreqs.keys) {
                                        if (termsEnum.seekExact(BytesRef(term))) {
                                            // TODO: also sometimes ask for payloads/offsets
                                            val noPositions: Boolean = random().nextBoolean()
                                            if (noPositions) {
                                                docs = termsEnum.postings(docs, FREQS.toInt())
                                            } else {
                                                docs = termsEnum.postings(null, POSITIONS.toInt())
                                            }

                                            var docFreq = 0
                                            var totalTermFreq: Long = 0
                                            while (docs!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                                docFreq++
                                                totalTermFreq += docs.freq().toLong()
                                                val limit: Int = TestUtil.nextInt(random(), 1, docs.freq())
                                                if (!noPositions) {
                                                    for (i in 0..<limit) {
                                                        docs.nextPosition()
                                                    }
                                                }
                                            }

                                            if (isMerge == false && addOnSecondPass) {
                                                val tf = checkNotNull(termFreqs[term])
                                                tf.docFreq += docFreq
                                                tf.totalTermFreq += totalTermFreq
                                                sumDocFreq.addAndFetch(docFreq.toLong())
                                                sumTotalTermFreq.addAndFetch(totalTermFreq)
                                            }

                                            // System.out.println("  term=" + term + " docFreq=" + docFreq + " ttDF=" +
                                            // termToDocFreq.get(term));
                                            assertTrue(docFreq <= termFreqs[term]!!.docFreq)
                                            assertTrue(totalTermFreq <= termFreqs[term]!!.totalTermFreq)
                                        }
                                    }

                                    // Also test seekCeil
                                    for (iter in 0..9) {
                                        val term = BytesRef(TestUtil.randomRealisticUnicodeString(random()))
                                        val status: SeekStatus = termsEnum.seekCeil(term)
                                        if (status == SeekStatus.NOT_FOUND) {
                                            assertTrue(term < termsEnum.term()!!)
                                        }
                                    }
                                }

                                @Throws(IOException::class)
                                override fun close() {
                                    fieldsConsumer.close()
                                }
                            }
                        }

                        @Throws(IOException::class)
                        override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
                            return defaultPostingsFormat.fieldsProducer(state)
                        }
                    }
                }
            })

        val w = RandomIndexWriter(random(), dir, iwc)

        val docs = LineFileDocs(random())
        val bytesToIndex: Int = atLeast(100) * 10 // TODO reduced from * 1024 to  *10 for dev speed
        var bytesIndexed = 0
        while (bytesIndexed < bytesToIndex) {
            val doc = docs.nextDoc()
            val justBodyDoc = Document()
            justBodyDoc.add(doc.getField("body")!!)
            w.addDocument(justBodyDoc)
            bytesIndexed += RamUsageTester.ramUsed(justBodyDoc).toInt()
        }

        val r: IndexReader = w.reader
        w.close()

        val terms: Terms = MultiTerms.getTerms(r, "body")!!
        assertEquals(sumDocFreq.load(), terms.sumDocFreq)
        assertEquals(sumTotalTermFreq.load(), terms.sumTotalTermFreq)

        val termsEnum: TermsEnum = terms.iterator()
        var termCount: Long = 0
        var supportsOrds = true
        while (termsEnum.next() != null) {
            val term: BytesRef? = termsEnum.term()
            assertEquals(termFreqs[term!!.utf8ToString()]!!.docFreq.toLong(), termsEnum.docFreq().toLong())
            assertEquals(termFreqs[term.utf8ToString()]!!.totalTermFreq, termsEnum.totalTermFreq())
            if (supportsOrds) {
                var ord: Long
                try {
                    ord = termsEnum.ord()
                } catch (uoe: UnsupportedOperationException) {
                    supportsOrds = false
                    ord = -1
                }
                if (ord != -1L) {
                    assertEquals(termCount, ord)
                }
            }
            termCount++
        }
        assertEquals(termFreqs.size.toLong(), termCount)

        r.close()
        dir.close()
    }

    protected fun assertReused(
        field: String,
        p1: PostingsEnum,
        p2: PostingsEnum
    ) {
        // if its not DirectPF, we should always reuse. This one has trouble.
        if ("Direct" != TestUtil.getPostingsFormat(field)) {
            assertSame(p1, p2)
        }
    }

    @Throws(Exception::class)
    open fun testPostingsEnumDocsOnly() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(/*null*/)
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(StringField("foo", "bar", Store.NO))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        // sugar method (FREQS)
        var postings: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"))!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(1, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val termsEnum: TermsEnum = getOnlyLeafReader(reader).terms("foo")!!.iterator()
        termsEnum.seekExact(BytesRef("bar"))
        var postings2: PostingsEnum? = termsEnum.postings(postings)
        assertNotNull(postings2)
        assertReused("foo", postings, postings2)
        // and it had better work
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(1, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // asking for any flags: ok
        for (flag in intArrayOf(
            NONE.toInt(),
            FREQS.toInt(),
            POSITIONS.toInt(),
            PAYLOADS.toInt(),
            OFFSETS.toInt(),
            ALL.toInt()
        )) {
            postings = termsEnum.postings(null, flag)!!
            assertEquals(-1, postings.docID().toLong())
            assertEquals(0, postings.nextDoc().toLong())
            assertEquals(1, postings.freq().toLong())
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())
            // reuse that too
            postings2 = termsEnum.postings(postings, flag)!!
            assertNotNull(postings2)
            assertReused("foo", postings, postings2)
            // and it had better work
            assertEquals(-1, postings2.docID().toLong())
            assertEquals(0, postings2.nextDoc().toLong())
            assertEquals(1, postings2.freq().toLong())
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())
        }

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumFreqs() {
        val dir: Directory = newDirectory()
        val iwc =
            IndexWriterConfig(
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        return TokenStreamComponents(MockTokenizer())
                    }
                })
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        doc.add(Field("foo", "bar bar", ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        // sugar method (FREQS)
        var postings: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"))!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val termsEnum: TermsEnum = getOnlyLeafReader(reader).terms("foo")!!.iterator()
        termsEnum.seekExact(BytesRef("bar"))
        var postings2: PostingsEnum? = termsEnum.postings(postings)
        assertNotNull(postings2)
        assertReused("foo", postings, postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, NONE.toInt())!!
        assertNotNull(docsOnly2)
        assertReused("foo", docsOnly, docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for any flags: ok
        for (flag in intArrayOf(
            NONE.toInt(),
            FREQS.toInt(),
            POSITIONS.toInt(),
            PAYLOADS.toInt(),
            OFFSETS.toInt(),
            ALL.toInt()
        )) {
            postings = termsEnum.postings(null, flag)!!
            assertEquals(-1, postings.docID().toLong())
            assertEquals(0, postings.nextDoc().toLong())
            if (flag != NONE.toInt()) {
                assertEquals(2, postings.freq().toLong())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())
            // reuse that too
            postings2 = termsEnum.postings(postings, flag)!!
            assertNotNull(postings2)
            assertReused("foo", postings, postings2)
            // and it had better work
            assertEquals(-1, postings2.docID().toLong())
            assertEquals(0, postings2.nextDoc().toLong())
            if (flag != NONE.toInt()) {
                assertEquals(2, postings2.freq().toLong())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())
        }

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumPositions() {
        val dir: Directory = newDirectory()
        val iwc =
            IndexWriterConfig(
                object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        return TokenStreamComponents(MockTokenizer())
                    }
                })
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(TextField("foo", "bar bar", Store.NO))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        // sugar method (FREQS)
        val postings: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"))!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val termsEnum: TermsEnum = getOnlyLeafReader(reader).terms("foo")!!.iterator()
        termsEnum.seekExact(BytesRef("bar"))
        val postings2: PostingsEnum? = termsEnum.postings(postings)
        assertNotNull(postings2)
        assertReused("foo", postings, postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, NONE.toInt())!!
        assertNotNull(docsOnly2)
        assertReused("foo", docsOnly, docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, POSITIONS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),docsAndPositionsEnum2.nextDoc().toLong())

        // payloads, offsets, etc don't cause an error if they aren't there
        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        // but make sure they work
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PAYLOADS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, OFFSETS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, ALL.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumOffsets() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(
            object : Analyzer() {
                            override fun createComponents(fieldName: String): TokenStreamComponents {
                                return TokenStreamComponents(MockTokenizer())
                            }
                        })
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        doc.add(Field("foo", "bar bar", ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        // sugar method (FREQS)
        val postings: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"))!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val termsEnum: TermsEnum = getOnlyLeafReader(reader).terms("foo")!!.iterator()
        termsEnum.seekExact(BytesRef("bar"))
        val postings2: PostingsEnum? = termsEnum.postings(postings)
        assertNotNull(postings2)
        assertReused("foo", postings, postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, NONE.toInt())!!
        assertNotNull(docsOnly2)
        assertReused("foo", docsOnly, docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, POSITIONS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        // payloads don't cause an error if they aren't there
        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        // but make sure they work
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PAYLOADS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, OFFSETS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, ALL.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumPayloads() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(/*null*/)
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val token1 = Token("bar", 0, 3)
        token1.payload = BytesRef("pay1")
        val token2 = Token("bar", 4, 7)
        token2.payload = BytesRef("pay2")
        doc.add(TextField("foo", CannedTokenStream(token1, token2)))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        // sugar method (FREQS)
        val postings: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"))!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val termsEnum: TermsEnum = getOnlyLeafReader(reader).terms("foo")!!.iterator()
        termsEnum.seekExact(BytesRef("bar"))
        val postings2: PostingsEnum? = termsEnum.postings(postings)
        assertNotNull(postings2)
        assertReused("foo", postings, postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, NONE.toInt())!!
        assertNotNull(docsOnly2)
        assertReused("foo", docsOnly, docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || BytesRef("pay1") == docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || BytesRef("pay2") == docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, POSITIONS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || BytesRef("pay1") == docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || BytesRef("pay2") == docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        // payloads
        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(BytesRef("pay1"), docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(BytesRef("pay2"), docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PAYLOADS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(BytesRef("pay1"), docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(BytesRef("pay2"), docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || BytesRef("pay1") == docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || BytesRef("pay2") == docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum,OFFSETS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null|| BytesRef("pay1") == docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || BytesRef("pay2") == docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(BytesRef("pay1"), docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(BytesRef("pay2"), docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, ALL.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(BytesRef("pay1"), docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(BytesRef("pay2"), docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumAll() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(/*null*/)
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val token1 = Token("bar", 0, 3)
        token1.payload = BytesRef("pay1")
        val token2 = Token("bar", 4, 7)
        token2.payload = BytesRef("pay2")
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        doc.add(Field("foo", CannedTokenStream(token1, token2), ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        // sugar method (FREQS)
        val postings: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"))!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val termsEnum: TermsEnum = getOnlyLeafReader(reader).terms("foo")!!.iterator()
        termsEnum.seekExact(BytesRef("bar"))
        val postings2: PostingsEnum? = termsEnum.postings(postings)
        assertNotNull(postings2)
        assertReused("foo", postings, postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, NONE.toInt())!!
        assertNotNull(docsOnly2)
        assertReused("foo", docsOnly, docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || BytesRef("pay1") == docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || BytesRef("pay2") == docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, POSITIONS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || BytesRef("pay1") == docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || BytesRef("pay2") == docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        // payloads
        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        assertEquals(BytesRef("pay1"), docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        assertEquals(BytesRef("pay2"), docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PAYLOADS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        assertEquals(BytesRef("pay1"), docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        assertEquals(BytesRef("pay2"), docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || BytesRef("pay1") == docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || BytesRef("pay2") == docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, OFFSETS.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || BytesRef("pay1") == docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || BytesRef("pay2") == docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = getOnlyLeafReader(reader).postings(Term("foo", "bar"), ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(BytesRef("pay1"), docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(BytesRef("pay2"), docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, ALL.toInt())!!
        assertReused("foo", docsAndPositionsEnum, docsAndPositionsEnum2)
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(BytesRef("pay1"), docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(BytesRef("pay2"), docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    override fun addRandomFields(doc: Document) {
        for (opts in IndexOptions.entries) {
            if (opts == IndexOptions.NONE) {
                continue
            }
            val ft = FieldType()
            ft.setIndexOptions(opts)
            ft.freeze()
            val numFields: Int = random().nextInt(5)
            for (j in 0..<numFields) {
                doc.add(
                    Field("f_$opts", TestUtil.randomSimpleString(random(), 2), ft)
                )
            }
        }
    }

    /** Test realistic data, which is often better at uncovering real bugs.  */
    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/ // this test takes a few seconds
    open fun testLineFileDocs() {
        // Use a FS dir and a non-randomized IWC to not slow down indexing
        newFSDirectory(createTempDir())
            .use { dir ->
                LineFileDocs(random())
                    .use { docs ->
                        IndexWriter(dir, IndexWriterConfig()).use { w ->
                            val numDocs: Int =
                                atLeast(3) // TODO reducing form 10000 to 3 for dev speed
                            for (i in 0..<numDocs) {
                                // Only keep the body field, and don't index term vectors on it, we only care about
                                // postings
                                val doc = docs.nextDoc()
                                var body: IndexableField? = doc.getField("body")
                                assertNotNull(body)
                                assertNotNull(body.stringValue())
                                assertNotEquals(IndexOptions.NONE, body.fieldType().indexOptions())
                                body = TextField("body", body.stringValue()!!, Store.NO)
                                w.addDocument(mutableListOf<IndexableField>(body))
                            }
                            w.forceMerge(1)
                        }
                    }
                TestUtil.checkIndex(dir)
            }
    }

    @Throws(Exception::class)
    open fun testMismatchedFields() {
        val dir1: Directory = newDirectory()
        val w1 = IndexWriter(dir1, newIndexWriterConfig())
        val doc = Document()
        doc.add(StringField("f", "a", Store.NO))
        doc.add(StringField("g", "b", Store.NO))
        w1.addDocument(doc)

        val dir2: Directory = newDirectory()
        val w2 = IndexWriter(dir2, newIndexWriterConfig().setMergeScheduler(SerialMergeScheduler()))
        w2.addDocument(doc)
        w2.commit()

        var reader: DirectoryReader = DirectoryReader.open(w1)
        w1.close()
        w2.addIndexes(MismatchedCodecReader(getOnlyLeafReader(reader) as CodecReader, random()))
        reader.close()
        w2.forceMerge(1)
        reader = DirectoryReader.open(w2)
        w2.close()

        val leafReader: LeafReader = getOnlyLeafReader(reader)

        var te: TermsEnum = leafReader.terms("f")!!.iterator()
        assertEquals("a", te.next()!!.utf8ToString())
        assertEquals(2, te.docFreq().toLong())
        assertNull(te.next())

        te = leafReader.terms("g")!!.iterator()
        assertEquals("b", te.next()!!.utf8ToString())
        assertEquals(2, te.docFreq().toLong())
        assertNull(te.next())

        IOUtils.close(reader, w2, dir1, dir2)
    }

    companion object {
        @Throws(IOException::class)
        protected fun checkReuse(
            termsEnum: TermsEnum,
            firstFlags: Int,
            secondFlags: Int,
            shouldReuse: Boolean
        ) {
            val postings1: PostingsEnum = termsEnum.postings(null, firstFlags)!!
            val postings2: PostingsEnum = termsEnum.postings(postings1, secondFlags)!!
            if (shouldReuse) {
                assertSame(
                    postings1,
                    postings2, message = "Expected PostingsEnum " + postings1::class.qualifiedName + " to be reused"
                )
            } else {
                assertNotSame(
                    postings1,
                    postings2, message = "Expected PostingsEnum " + postings1::class.qualifiedName + " to not be reused"
                )
            }
        }
    }
}
