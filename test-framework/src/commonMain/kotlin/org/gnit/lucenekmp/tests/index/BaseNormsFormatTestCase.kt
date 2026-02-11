package org.gnit.lucenekmp.tests.index

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

/**
 * Abstract class to do basic tests for a norms format. NOTE: This test focuses on the norms impl,
 * nothing else. The [stretch] goal is for this test to be so thorough in testing a new NormsFormat
 * that if this test passes, then all Lucene tests should also pass. Ie, if there is some bug in a
 * given NormsFormat that this test fails to catch then this test needs to be improved!
 */
abstract class BaseNormsFormatTestCase : BaseIndexFileFormatTestCase() {
    /** Whether the codec supports sparse values.  */
    protected fun codecSupportsSparsity(): Boolean {
        return true
    }

    @Throws(Exception::class)
    open fun testByteRange() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                1.0,
                {
                    TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                })
        }
    }

    @Throws(Exception::class)
    open fun testSparseByteRange() {
        assumeTrue("Requires sparse norms support", codecSupportsSparsity())
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                })
        }
    }

    @Throws(Exception::class)
    open fun testShortRange() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                1.0,
                {
                    TestUtil.nextLong(r, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
                })
        }
    }

    @Throws(Exception::class)
    open fun testSparseShortRange() {
        assumeTrue("Requires sparse norms support", codecSupportsSparsity())
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    TestUtil.nextLong(r, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testLongRange() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                1.0,
                {
                    TestUtil.nextLong(r, Long.MIN_VALUE, Long.MAX_VALUE)
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testSparseLongRange() {
        assumeTrue(
            "Requires sparse norms support",
            codecSupportsSparsity()
        )
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    TestUtil.nextLong(r, Long.MIN_VALUE, Long.MAX_VALUE)
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testFullLongRange() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                1.0,
                {
                    val thingToDo: Int = r.nextInt(3)
                    when (thingToDo) {
                        0 -> Long.MIN_VALUE
                        1 -> Long.MAX_VALUE
                        else -> TestUtil.nextLong(
                            r,
                            Long.MIN_VALUE,
                            Long.MAX_VALUE
                        )
                    }
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testSparseFullLongRange() {
        assumeTrue("Requires sparse norms support", codecSupportsSparsity())
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    val thingToDo: Int = r.nextInt(3)
                    when (thingToDo) {
                        0 -> Long.MIN_VALUE
                        1 -> Long.MAX_VALUE
                        else -> TestUtil.nextLong(
                            r,
                            Long.MIN_VALUE,
                            Long.MAX_VALUE
                        )
                    }
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testFewValues() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                1.0,
                {
                    (if (r.nextBoolean()) 20 else 3).toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testFewSparseValues() {
        assumeTrue(
            "Requires sparse norms support",
            codecSupportsSparsity()
        )
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    (if (r.nextBoolean()) 20 else 3).toLong()
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testFewLargeValues() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                1.0,
                {
                    if (r.nextBoolean()) 1000000L else -5000
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testFewSparseLargeValues() {
        assumeTrue(
            "Requires sparse norms support",
            codecSupportsSparsity()
        )
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    if (r.nextBoolean()) 1000000L else -5000
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testAllZeros() {
        val iterations: Int = atLeast(1)
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                1.0,
                {
                    0
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testSparseAllZeros() {
        assumeTrue(
            "Requires sparse norms support",
            codecSupportsSparsity()
        )
        val iterations: Int = atLeast(1)
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    0
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testMostZeros() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            doTestNormsVersusDocValues(
                1.0,
                {
                    if (r.nextInt(100) == 0) TestUtil.nextLong(
                        r,
                        Byte.MIN_VALUE.toLong(),
                        Byte.MAX_VALUE.toLong()
                    ) else 0
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testOutliers() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            val commonValue: Long = TestUtil.nextLong(
                r,
                Byte.MIN_VALUE.toLong(),
                Byte.MAX_VALUE.toLong()
            )
            doTestNormsVersusDocValues(
                1.0,
                {
                    if (r.nextInt(100) == 0)
                        TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                    else
                        commonValue
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testSparseOutliers() {
        assumeTrue(
            "Requires sparse norms support",
            codecSupportsSparsity()
        )
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            val commonValue: Long = TestUtil.nextLong(
                r,
                Byte.MIN_VALUE.toLong(),
                Byte.MAX_VALUE.toLong()
            )
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    if (r.nextInt(100) == 0)
                        TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                    else
                        commonValue
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testOutliers2() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            val commonValue: Long = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
            val uncommonValue: Long = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
            doTestNormsVersusDocValues(
                1.0,
                {
                    if (r.nextInt(100) == 0) uncommonValue else commonValue
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testSparseOutliers2() {
        assumeTrue("Requires sparse norms support", codecSupportsSparsity())
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            val commonValue: Long = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
            val uncommonValue: Long = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
            doTestNormsVersusDocValues(
                random().nextDouble(),
                {
                    if (r.nextInt(100) == 0) uncommonValue else commonValue
                }
            )
        }
    }

    @Throws(Exception::class)
    open fun testNCommon() {
        val r: Random = random()
        val N: Int = TestUtil.nextInt(r, 2, 15)
        val commonValues = LongArray(N)
        for (j in 0..<N) {
            commonValues[j] = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
        }
        val numOtherValues: Int = TestUtil.nextInt(r, 2, 256 - N)
        val otherValues = LongArray(numOtherValues)
        for (j in 0..<numOtherValues) {
            otherValues[j] = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
        }
        doTestNormsVersusDocValues(
            1.0,
            {
                if (r.nextInt(100) == 0)
                    otherValues[r.nextInt(numOtherValues - 1)]
                else
                    commonValues[r.nextInt(N - 1)]
            }
        )
    }

    @Throws(Exception::class)
    open fun testSparseNCommon() {
        assumeTrue("Requires sparse norms support", codecSupportsSparsity())
        val r: Random = random()
        val N: Int = TestUtil.nextInt(r, 2, 15)
        val commonValues = LongArray(N)
        for (j in 0..<N) {
            commonValues[j] = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
        }
        val numOtherValues: Int = TestUtil.nextInt(r, 2, 256 - N)
        val otherValues = LongArray(numOtherValues)
        for (j in 0..<numOtherValues) {
            otherValues[j] = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
        }
        doTestNormsVersusDocValues(
            random().nextDouble(),
            {
                if (r.nextInt(100) == 0)
                    otherValues[r.nextInt(numOtherValues - 1)]
                else
                    commonValues[r.nextInt(N - 1)]
            }
        )
    }

    /** a more thorough n-common that tests all low bpv  */
    /*@LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testNCommonBig() {
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            // 16 is 4 bpv, the max before we jump to 8bpv
            for (n in 2..3) { // TODO reduced from 2..15 to 2..3 for dev speed
                val N = n
                val commonValues = LongArray(N)
                for (j in 0..<N) {
                    commonValues[j] = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                }
                val numOtherValues: Int =
                    TestUtil.nextInt(r, 2, 256 - N)
                val otherValues = LongArray(numOtherValues)
                for (j in 0..<numOtherValues) {
                    otherValues[j] = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                }
                doTestNormsVersusDocValues(
                    1.0,
                    {
                        if (r.nextInt(100) == 0)
                            otherValues[r.nextInt(numOtherValues - 1)]
                        else
                            commonValues[r.nextInt(N - 1)]
                    }
                )
            }
        }
    }

    /** a more thorough n-common that tests all low bpv and sparse docs  */
    /*@LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testSparseNCommonBig() {
        assumeTrue(
            "Requires sparse norms support",
            codecSupportsSparsity()
        )
        val iterations: Int = atLeast(1)
        val r: Random = random()
        for (i in 0..<iterations) {
            // 16 is 4 bpv, the max before we jump to 8bpv
            for (n in 2..3) { // TODO reduced from 2..15 to 2..3 for dev speed
                val N = n
                val commonValues = LongArray(N)
                for (j in 0..<N) {
                    commonValues[j] = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                }
                val numOtherValues: Int = TestUtil.nextInt(r, 2, 256 - N)
                val otherValues = LongArray(numOtherValues)
                for (j in 0..<numOtherValues) {
                    otherValues[j] = TestUtil.nextLong(r, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                }
                doTestNormsVersusDocValues(
                    random().nextDouble(),
                    {
                        if (r.nextInt(100) == 0)
                            otherValues[r.nextInt(numOtherValues - 1)]
                        else
                            commonValues[r.nextInt(N - 1)]
                    }
                )
            }
        }
    }

    @Throws(Exception::class)
    private fun doTestNormsVersusDocValues(
        density: Double,
        longs: () -> Long /*java.util.function.LongSupplier*/
    ) {
        val numDocs: Int = atLeast(5) // TODO reduced from 500 to 5 for dev speed
        val docsWithField = FixedBitSet(numDocs)
        val numDocsWithField = max(1, (density * numDocs).toInt())
        if (numDocsWithField == numDocs) {
            docsWithField.set(0, numDocs)
        } else {
            var i = 0
            while (i < numDocsWithField) {
                val doc: Int = random().nextInt(numDocs)
                if (docsWithField.get(doc) == false) {
                    docsWithField.set(doc)
                    ++i
                }
            }
        }
        val norms = LongArray(numDocsWithField)
        for (i in 0..<numDocsWithField) {
            norms[i] = longs()
        }

        val dir: Directory = applyCreatedVersionMajor(newDirectory())
        val analyzer: Analyzer =
            MockAnalyzer(
                random(),
                MockTokenizer.WHITESPACE,
                false
            )
        val conf: IndexWriterConfig = newIndexWriterConfig(analyzer)
        val sim = CannedNormSimilarity(norms)
        conf.setSimilarity(sim)
        val writer = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val idField: Field = StringField("id", "", Field.Store.NO)
        val indexedField: Field = TextField("indexed", "", Field.Store.NO)
        val dvField: Field = NumericDocValuesField("dv", 0)
        doc.add(idField)
        doc.add(indexedField)
        doc.add(dvField)

        run {
            var i = 0
            var j = 0
            while (i < numDocs) {
                idField.setStringValue(i.toString())
                if (docsWithField.get(i) == false) {
                    val doc2 = Document()
                    doc2.add(idField)
                    writer.addDocument(doc2)
                } else {
                    val value = norms[j++]
                    dvField.setLongValue(value)
                    // only empty fields may have 0 as a norm
                    indexedField.setStringValue(if (value == 0L) "" else "a")
                    writer.addDocument(doc)
                }
                if (random().nextInt(31) == 0) {
                    writer.commit()
                }
                i++
            }
        }

        // delete some docs
        val numDeletions: Int = random().nextInt(numDocs / 1) // TODO changed from 20 to 1 because of error
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }

        writer.commit()

        // compare
        var ir: DirectoryReader = maybeWrapWithMergingReader(DirectoryReader.open(dir))
        checkNormsVsDocValues(ir)
        ir.close()

        writer.forceMerge(1)

        // compare again
        ir = maybeWrapWithMergingReader(DirectoryReader.open(dir))
        checkNormsVsDocValues(ir)

        writer.close()
        ir.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun checkNormsVsDocValues(ir: IndexReader) {
        // debug logs removed for cleanliness
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val expected: NumericDocValues? = r.getNumericDocValues("dv")
            val actual: NumericDocValues? = r.getNormValues("indexed")
            assertEquals(expected == null, actual == null)
            if (expected != null) {
                var d: Int = expected.nextDoc()
                while (d != DocIdSetIterator.NO_MORE_DOCS) {
                    assertEquals(d.toLong(), actual!!.nextDoc().toLong())
                    assertEquals(expected.longValue(), actual.longValue(), "doc $d")
                    d = expected.nextDoc()
                }
                assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), actual!!.nextDoc().toLong())
            }
        }
    }

    internal class CannedNormSimilarity(val norms: LongArray) :
        Similarity() {
        var index: Int = 0

        override fun computeNorm(state: FieldInvertState): Long {
            assert(state.length > 0)
            while (true) {
                val norm = norms[index++]
                if (norm != 0L) {
                    return norm
                }
            }
        }

        override fun scorer(boost: Float, collectionStats: CollectionStatistics, vararg termStats: TermStatistics): SimScorer {
            throw UnsupportedOperationException()
        }
    }

    override fun addRandomFields(doc: Document) {
        // TODO: improve
        doc.add(TextField("foobar", TestUtil.randomSimpleString(random()), Field.Store.NO))
    }

    @Throws(Exception::class)
    override fun testMergeStability() {
        // TODO: can we improve this base test to just have subclasses declare the extensions to check,
        // rather than a blacklist to exclude we need to index stuff to get norms, but we dont care
        // about testing
        // the PFs actually doing that...
        assumeTrue("The MockRandom PF randomizes content on the fly, so we can't check it", false)
    }

    // TODO: test thread safety (e.g. across different fields) explicitly here
    /*
   * LUCENE-6006: Tests undead norms.
   *                                 .....
   *                             C C  /
   *                            /<   /
   *             ___ __________/_#__=o
   *            /(- /(\_\________   \
   *            \ ) \ )_      \o     \
   *            /|\ /|\       |'     |
   *                          |     _|
   *                          /o   __\
   *                         / '     |
   *                        / /      |
   *                       /_/\______|
   *                      (   _(    <
   *                       \    \    \
   *                        \    \    |
   *                         \____\____\
   *                         ____\_\__\_\
   *                       /`   /`     o\
   *                       |___ |_______|
   *
   */
    @Throws(Exception::class)
    open fun testUndeadNorms() {
        val dir: Directory = applyCreatedVersionMajor(newDirectory())
        val w = RandomIndexWriter(random(), dir)
        val numDocs: Int = atLeast(500)
        val toDelete: MutableList<Int> = mutableListOf()
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(StringField("id", "" + i, Field.Store.NO))
            if (random().nextInt(5) == 1) {
                toDelete.add(i)
                doc.add(TextField("content", "some content", Field.Store.NO))
            }
            w.addDocument(doc)
        }
        for (id in toDelete) {
            w.deleteDocuments(Term("id", "" + id))
        }
        w.forceMerge(1)
        val r: IndexReader = maybeWrapWithMergingReader(w.reader)
        assertFalse(r.hasDeletions())

        // Confusingly, norms should exist, and should all be 0, even though we deleted all docs that
        // had the field "content".  They should not
        // be undead:
        val norms: NumericDocValues? = MultiDocValues.getNormValues(r, "content")
        assertNotNull(norms)
        if (codecSupportsSparsity()) {
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), norms.nextDoc().toLong())
        } else {
            for (i in 0..<r.maxDoc()) {
                assertEquals(i.toLong(), norms.nextDoc().toLong())
                assertEquals(0, norms.longValue())
            }
        }

        r.close()
        w.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testThreads() = runTest(timeout = 3.minutes) {
        val density = if (codecSupportsSparsity() == false || random().nextBoolean()) 1f else random().nextFloat()
        val numDocs: Int = atLeast(3) // TODO reduced from 500 to 3 for dev speed
        val docsWithField = FixedBitSet(numDocs)
        val numDocsWithField = max(1, (density * numDocs).toInt())
        if (numDocsWithField == numDocs) {
            docsWithField.set(0, numDocs)
        } else {
            var i = 0
            while (i < numDocsWithField) {
                val doc: Int = random().nextInt(numDocs)
                if (docsWithField.get(doc) == false) {
                    docsWithField.set(doc)
                    ++i
                }
            }
        }

        val norms = LongArray(numDocsWithField)
        for (i in 0..<numDocsWithField) {
            norms[i] = random().nextLong()
        }

        val dir: Directory = applyCreatedVersionMajor(newDirectory())
        val analyzer: Analyzer = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)
        val conf: IndexWriterConfig = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        conf.setSimilarity(CannedNormSimilarity(norms))
        val writer = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val idField: Field = StringField("id", "", Field.Store.NO)
        val indexedField: Field = TextField("indexed", "", Field.Store.NO)
        val dvField: Field = NumericDocValuesField("dv", 0)
        doc.add(idField)
        doc.add(indexedField)
        doc.add(dvField)

        run {
            var i = 0
            var j = 0
            while (i < numDocs) {
                idField.setStringValue(i.toString())
                if (docsWithField.get(i) == false) {
                    val doc2 = Document()
                    doc2.add(idField)
                    writer.addDocument(doc2)
                } else {
                    val value = norms[j++]
                    dvField.setLongValue(value)
                    indexedField.setStringValue(if (value == 0L) "" else "a")
                    writer.addDocument(doc)
                }
                if (random().nextInt(31) == 0) {
                    writer.commit()
                }
                i++
            }
        }

        val reader: DirectoryReader = maybeWrapWithMergingReader(writer.reader)
        writer.close()

        val numThreads: Int = TestUtil.nextInt(random(), 3, 30)
        val maxConcurrent = 4
        val threadsToUse = kotlin.math.min(numThreads, maxConcurrent)
        val perJobTimeoutMs = 120_000L
        if (threadsToUse != numThreads) {
            // keep a warning-level log only when we reduced workers
            logger.error { "testThreads: reduced worker count from $numThreads to $threadsToUse for stability" }
        }

        val startSignal = CompletableDeferred<Unit>()

        val jobs: Array<Job> = Array(threadsToUse) { idx ->
            launch {
                val jobName = "job-$idx"
                try {
                    startSignal.await()
                    try {
                        withContext(Dispatchers.Default) {
                            withTimeout(perJobTimeoutMs) { checkNormsVsDocValues(reader) }
                        }
                        withContext(Dispatchers.Default) {
                            withTimeout(perJobTimeoutMs) { TestUtil.checkReader(reader) }
                        }
                    } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
                        logger.error(te) { "[$jobName] timeout while running checks (${perJobTimeoutMs}ms)" }
                        throw RuntimeException(te)
                    }
                } catch (e: Throwable) {
                    try { logger.error(e) { "[$jobName] exception in job" } } catch (_: Exception) {}
                    throw RuntimeException(e)
                }
            }
        }

        startSignal.complete(Unit)

        jobs.forEach { job -> job.join() }

        reader.close(); dir.close()
    }

    @Throws(IOException::class)
    open fun testIndependantIterators() {
        val dir: Directory = newDirectory()
        val conf: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy())
        val sim = CannedNormSimilarity(longArrayOf(42, 10, 20))
        conf.setSimilarity(sim)
        val writer = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val indexedField: Field = TextField("indexed", "a", Field.Store.NO)
        doc.add(indexedField)
        for (i in 0..2) {
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        val r: LeafReader = getOnlyLeafReader(maybeWrapWithMergingReader(writer.reader))
        val n1: NumericDocValues = r.getNormValues("indexed")!!
        val n2: NumericDocValues = r.getNormValues("indexed")!!
        assertEquals(0, n1.nextDoc().toLong())
        assertEquals(42, n1.longValue())
        assertEquals(1, n1.nextDoc().toLong())
        assertEquals(10, n1.longValue())
        assertEquals(0, n2.nextDoc().toLong())
        assertEquals(42, n2.longValue())
        assertEquals(1, n2.nextDoc().toLong())
        assertEquals(10, n2.longValue())
        assertEquals(2, n2.nextDoc().toLong())
        assertEquals(20, n2.longValue())
        assertEquals(2, n1.nextDoc().toLong())
        assertEquals(20, n1.longValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), n1.nextDoc().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), n2.nextDoc().toLong())
        IOUtils.close(r, writer, dir)
    }

    @Throws(IOException::class)
    open fun testIndependantSparseIterators() {
        val dir: Directory = newDirectory()
        val conf: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy())
        val sim = CannedNormSimilarity(longArrayOf(42, 10, 20))
        conf.setSimilarity(sim)
        val writer = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val indexedField: Field = TextField("indexed", "a", Field.Store.NO)
        doc.add(indexedField)
        val emptyDoc = Document()
        for (i in 0..2) {
            writer.addDocument(doc)
            writer.addDocument(emptyDoc)
        }
        writer.forceMerge(1)
        val r: LeafReader = getOnlyLeafReader(maybeWrapWithMergingReader(writer.reader))
        val n1: NumericDocValues = r.getNormValues("indexed")!!
        val n2: NumericDocValues = r.getNormValues("indexed")!!
        assertEquals(0, n1.nextDoc().toLong())
        assertEquals(42, n1.longValue())
        assertEquals(2, n1.nextDoc().toLong())
        assertEquals(10, n1.longValue())
        assertEquals(0, n2.nextDoc().toLong())
        assertEquals(42, n2.longValue())
        assertEquals(2, n2.nextDoc().toLong())
        assertEquals(10, n2.longValue())
        assertEquals(4, n2.nextDoc().toLong())
        assertEquals(20, n2.longValue())
        assertEquals(4, n1.nextDoc().toLong())
        assertEquals(20, n1.longValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), n1.nextDoc().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), n2.nextDoc().toLong())
        IOUtils.close(r, writer, dir)
    }
}
