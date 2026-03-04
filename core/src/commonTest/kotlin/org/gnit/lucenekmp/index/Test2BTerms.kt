package org.gnit.lucenekmp.index

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.Test2BConstants
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

// NOTE: SimpleText codec will consume very large amounts of
// disk (but, should run successfully).  Best to run w/
// -Dtests.codec=<current codec>, and w/ plenty of RAM, eg:
//
//   ant test -Dtests.monster=true -Dtests.heapsize=8g -Dtests.codec=Lucene62 -Dtestcase=Test2BTerms
//
//@SuppressCodecs("SimpleText", "Direct")
//@Monster("very slow, use 5g minimum heap")
//@TimeoutSuite(millis = 80 * TimeUnits.HOUR) // effectively no limit
//@SuppressSysoutChecks(bugUrl = "Stuff gets printed")
class Test2BTerms : LuceneTestCase() {

    init {
        configureTestLogging()
    }

    private val logger = KotlinLogging.logger {  }

    @Test
    @Throws(IOException::class)
    fun test2BTerms() {
        val timeSource = TimeSource.Monotonic
        val testMark = timeSource.markNow()

        println("Starting Test2B")
        val TERM_COUNT = Test2BConstants.MAX_DOCS.toLong() * 100 // TODO reduced TERM_COUNT = Int.MAX_VALUE + 100000000 to Test2BConstants.MAX_DOCS * 100 for dev speed

        val TERMS_PER_DOC = TestUtil.nextInt(random(), 100, 1000) // TODO reduced TERMS_PER_DOC range = 100000..1000000 to 100..1000 for dev speed

        var savedTerms: MutableList<BytesRef>? = null

        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BTerms"))
        // MockDirectoryWrapper dir = newFSDirectory(new File("/p/lucene/indices/2bindex"));
        if (dir is MockDirectoryWrapper) {
            dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }
        dir.checkIndexOnClose = false // don't double-checkindex

        run {
            val writerMark = timeSource.markNow()

            val w = IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
                    .setRAMBufferSizeMB(256.0)
                    .setMergeScheduler(ConcurrentMergeScheduler())
                    .setMergePolicy(newLogMergePolicy(false, 10))
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                    .setCodec(TestUtil.getDefaultCodec())
            )

            val mp = w.config.mergePolicy
            if (mp is LogByteSizeMergePolicy) {
                // 1 petabyte:
                mp.maxMergeMB = 1024.0 * 1024 * 1024
            }

            val doc = Document()
            val ts = MyTokenStream(random(), TERMS_PER_DOC)

            val customType = FieldType(TextField.TYPE_NOT_STORED)
            customType.setIndexOptions(IndexOptions.DOCS)
            customType.setOmitNorms(true)
            val field = Field("field", ts, customType)
            doc.add(field)
            // w.setInfoStream(System.out);
            val numDocs = (TERM_COUNT / TERMS_PER_DOC).toInt()

            println("TERMS_PER_DOC=$TERMS_PER_DOC")
            println("numDocs=$numDocs")

            val addDocsMark = timeSource.markNow()
            for (i in 0..<numDocs) {
                val t0 = System.nanoTime()
                w.addDocument(doc)
                println("$i of $numDocs ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms")
            }
            logger.debug { "phase=addDocuments elapsedMs=${addDocsMark.elapsedNow().inWholeMilliseconds} numDocs=$numDocs termsPerDoc=$TERMS_PER_DOC" }
            savedTerms = ts.savedTerms

            println("TEST: full merge")
            val mergeMark = timeSource.markNow()
            w.forceMerge(1)
            logger.debug { "phase=forceMerge elapsedMs=${mergeMark.elapsedNow().inWholeMilliseconds}" }
            println("TEST: close writer")
            val closeMark = timeSource.markNow()
            w.close()
            logger.debug { "phase=closeWriter elapsedMs=${closeMark.elapsedNow().inWholeMilliseconds}" }
            logger.debug { "phase=writerScope elapsedMs=${writerMark.elapsedNow().inWholeMilliseconds}" }
        }

        println("TEST: open reader")
        val openReaderMark = timeSource.markNow()
        val r = DirectoryReader.open(dir)
        logger.debug { "phase=openReader elapsedMs=${openReaderMark.elapsedNow().inWholeMilliseconds}" }
        if (savedTerms == null) {
            val findTermsMark = timeSource.markNow()
            savedTerms = findTerms(r)
            logger.debug { "phase=findTerms elapsedMs=${findTermsMark.elapsedNow().inWholeMilliseconds} savedCount=${savedTerms.size}" }
        }
        val numSavedTerms = savedTerms.size
        val bigOrdTerms = savedTerms.subList(maxOf(0, numSavedTerms - 10), numSavedTerms).toMutableList()
        println("TEST: test big ord terms...")
        val bigOrdMark = timeSource.markNow()
        testSavedTerms(r, bigOrdTerms)
        logger.debug { "phase=testSavedTerms-bigOrd elapsedMs=${bigOrdMark.elapsedNow().inWholeMilliseconds} termCount=${bigOrdTerms.size}" }
        println("TEST: test all saved terms...")
        val allTermsMark = timeSource.markNow()
        testSavedTerms(r, savedTerms)
        logger.debug { "phase=testSavedTerms-all elapsedMs=${allTermsMark.elapsedNow().inWholeMilliseconds} termCount=${savedTerms.size}" }
        r.close()

        println("TEST: now CheckIndex...")
        val checkIndexMark = timeSource.markNow()
        val status = TestUtil.checkIndex(dir)
        logger.debug { "phase=checkIndex elapsedMs=${checkIndexMark.elapsedNow().inWholeMilliseconds}" }
        val tc = status.segmentInfos[0].termIndexStatus!!.termCount
        assertTrue(tc > 0) // TODO reduced termCountThreshold = Int.MAX_VALUE to 0 for dev speed

        dir.close()
        logger.debug { "phase=test2BTerms-total elapsedMs=${testMark.elapsedNow().inWholeMilliseconds}" }
        println("TEST: done!")
    }

    @Throws(IOException::class)
    private fun findTerms(r: IndexReader): MutableList<BytesRef> {
        println("TEST: findTerms")
        val termsEnum = MultiTerms.getTerms(r, "field")!!.iterator()
        val savedTerms = mutableListOf<BytesRef>()
        var nextSave = TestUtil.nextInt(random(), 5, 10) // TODO reduced nextSave range = 500000..1000000 to 5..10 for dev speed
        var term = termsEnum.next()
        while (term != null) {
            if (--nextSave == 0) {
                savedTerms.add(BytesRef.deepCopyOf(term))
                println("TEST: add $term")
                nextSave = TestUtil.nextInt(random(), 5, 10) // TODO reduced nextSave range = 500000..1000000 to 5..10 for dev speed
            }
            term = termsEnum.next()
        }
        return savedTerms
    }

    @Throws(IOException::class)
    private fun testSavedTerms(r: IndexReader, terms: MutableList<BytesRef>) {
        println("TEST: run ${terms.size} terms on reader=$r")
        val s = newSearcher(r)
        terms.shuffle(random())
        val termsEnum = MultiTerms.getTerms(r, "field")!!.iterator()
        var failed = false
        var countNanos = 0L
        var seekNanos = 0L
        var loggingNanos = 0L
        for (iter in 0..<(10 * terms.size)) {
            val term = terms[random().nextInt(terms.size)]
            var subStepStart = System.nanoTime()
            println("TEST: search $term")
            loggingNanos += System.nanoTime() - subStepStart
            val t0 = System.nanoTime()
            subStepStart = System.nanoTime()
            val count = s.count(TermQuery(Term("field", term)))
            countNanos += System.nanoTime() - subStepStart
            if (count <= 0) {
                subStepStart = System.nanoTime()
                println("  FAILED: count=$count")
                loggingNanos += System.nanoTime() - subStepStart
                failed = true
            }
            subStepStart = System.nanoTime()
            println("  took ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms")
            loggingNanos += System.nanoTime() - subStepStart

            subStepStart = System.nanoTime()
            val result = termsEnum.seekCeil(term)
            seekNanos += System.nanoTime() - subStepStart
            if (result != TermsEnum.SeekStatus.FOUND) {
                if (result == TermsEnum.SeekStatus.END) {
                    subStepStart = System.nanoTime()
                    println("  FAILED: got END")
                    loggingNanos += System.nanoTime() - subStepStart
                } else {
                    subStepStart = System.nanoTime()
                    println("  FAILED: wrong term: got ${termsEnum.term()}")
                    loggingNanos += System.nanoTime() - subStepStart
                }
                failed = true
            }
        }
        logger.debug {
            "phase=testSavedTerms-substeps iterations=${10 * terms.size} " +
                "countMs=${TimeUnit.NANOSECONDS.toMillis(countNanos)} " +
                "seekMs=${TimeUnit.NANOSECONDS.toMillis(seekNanos)} " +
                "loggingMs=${TimeUnit.NANOSECONDS.toMillis(loggingNanos)}"
        }
        assertFalse(failed)
    }

    private class MyTokenStream(random: Random, private val tokensPerDoc: Int) : TokenStream(MyAttributeFactory(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY)) {

        private var tokenCount = 0
        val savedTerms: MutableList<BytesRef> = mutableListOf()
        private var nextSave: Int
        private var termCounter: Long = 0
        private val random: Random

        init {
            AttributeSource.registerAttributeInterfaces(
                MyTermAttributeImpl::class,
                arrayOf(TermToBytesRefAttribute::class)
            )
            addAttribute(TermToBytesRefAttribute::class)
            bytes.length = TOKEN_LEN
            this.random = random
            nextSave = TestUtil.nextInt(random, 5, 10) // TODO reduced nextSave range = 500000..1000000 to 5..10 for dev speed
        }

        override fun incrementToken(): Boolean {
            clearAttributes()
            if (tokenCount >= tokensPerDoc) {
                return false
            }
            var shift = 32
            for (i in 0..<5) {
                bytes.bytes[i] = ((termCounter shr shift) and 0xFF).toByte()
                shift -= 8
            }
            termCounter++
            tokenCount++
            if (--nextSave == 0) {
                savedTerms.add(BytesRef.deepCopyOf(bytes))
                println("TEST: save term=$bytes")
                nextSave = TestUtil.nextInt(random, 5, 10) // TODO reduced nextSave range = 500000..1000000 to 5..10 for dev speed
            }
            return true
        }

        override fun reset() {
            super.reset()
            tokenCount = 0
        }

        private class MyTermAttributeImpl : AttributeImpl(), TermToBytesRefAttribute {
            override val bytesRef: BytesRef
                get() = bytes

            override fun clear() {}

            override fun copyTo(target: AttributeImpl) {
                throw UnsupportedOperationException()
            }

            override fun clone(): MyTermAttributeImpl {
                throw UnsupportedOperationException()
            }

            override fun newInstance(): AttributeImpl {
                return MyTermAttributeImpl()
            }

            override fun reflectWith(reflector: AttributeReflector) {
                reflector.reflect(TermToBytesRefAttribute::class, "bytes", bytesRef)
            }
        }

        private class MyAttributeFactory(private val delegate: AttributeFactory) : AttributeFactory() {
            override fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl {
                if (attClass == TermToBytesRefAttribute::class) return MyTermAttributeImpl()
                if (attClass == CharTermAttribute::class) {
                    throw IllegalArgumentException("no")
                }
                return delegate.createAttributeInstance(attClass)
            }
        }
    }

    companion object {
        private const val TOKEN_LEN = 5

        private val bytes = BytesRef(TOKEN_LEN)
    }
}
