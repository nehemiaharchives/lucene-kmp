package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldDocValuesFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldPostingsFormat
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.AwaitsFix
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestIndexWriterForceMerge : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testPartialMerge() {
        val dir = newDirectory()

        val doc = Document()
        doc.add(newStringField("content", "aaa", Field.Store.NO))
        val incrMin = if (TEST_NIGHTLY) 15 else 40
        var numDocs = 10
        while (numDocs < 500) {
            var ldmp = LogDocMergePolicy().apply {
                minMergeDocs = 1
                mergeFactor = 5
            }
            var writer = IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(ldmp)
            )

            for (j in 0 until numDocs) {
                writer.addDocument(doc)
            }

            writer.close()

            var sis = SegmentInfos.readLatestCommit(dir)
            val segCount = sis.size()

            ldmp = LogDocMergePolicy().apply {
                mergeFactor = 5
            }
            writer = IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(ldmp)
            )

            writer.forceMerge(3)

            writer.close()

            sis = SegmentInfos.readLatestCommit(dir)
            val optSegCount = sis.size()

            if (segCount < 3) {
                assertEquals(segCount, optSegCount)
            } else {
                assertTrue(optSegCount <= 3, "forceMerge(3) left $optSegCount segments")
            }

            numDocs += TestUtil.nextInt(random(), incrMin, 5 * incrMin)
        }
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMaxNumSegments2() {
        val dir = newDirectory()

        val doc = Document()
        doc.add(newStringField("content", "aaa", Field.Store.NO))

        val ldmp = LogDocMergePolicy().apply {
            minMergeDocs = 1
            mergeFactor = 4
        }
        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergePolicy(ldmp)
                .setMergeScheduler(ConcurrentMergeScheduler())
        )

        for (iter in 0 until 10) {
            for (i in 0 until 19) {
                writer.addDocument(doc)
            }

            writer.commit()
            writer.waitForMerges()
            writer.commit()

            var sis = SegmentInfos.readLatestCommit(dir)
            val segCount = sis.size()
            writer.forceMerge(7)
            writer.commit()
            writer.waitForMerges()

            sis = SegmentInfos.readLatestCommit(dir)
            val optSegCount = sis.size()

            if (segCount < 7) {
                assertEquals(segCount, optSegCount)
            } else {
                assertEquals(7, optSegCount, "seg: $segCount")
            }
        }
        writer.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testForceMergeTempSpaceUsage() {
        val dir: MockDirectoryWrapper = newMockDirectory()
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, true))
            }
        }
        var writer = IndexWriter(
            dir,
            newIndexWriterConfig(analyzer)
                .setMaxBufferedDocs(10)
                .setMergePolicy(newLogMergePolicy())
        )

        if (VERBOSE) {
            println("TEST: config1=${writer.config}")
        }

        for (j in 0 until 500) {
            TestIndexWriter.addDocWithIndex(writer, j)
        }
        writer.commit()
        TestIndexWriter.addDocWithIndex(writer, 500)
        writer.close()

        var startDiskUsage = 0L
        for (file in dir.listAll()) {
            startDiskUsage += dir.fileLength(file)
            if (VERBOSE) {
                println("$file: ${dir.fileLength(file)}")
            }
        }
        if (VERBOSE) {
            println("TEST: start disk usage = $startDiskUsage")
        }
        val startListing = listFiles(dir)

        dir.resetMaxUsedSizeInBytes()
        dir.trackDiskUsage = true

        writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(OpenMode.APPEND)
                .setMergePolicy(newLogMergePolicy())
        )

        if (VERBOSE) {
            println("TEST: config2=${writer.config}")
        }

        writer.forceMerge(1)
        writer.close()

        var finalDiskUsage = 0L
        for (file in dir.listAll()) {
            finalDiskUsage += dir.fileLength(file)
            if (VERBOSE) {
                println("$file: ${dir.fileLength(file)}")
            }
        }
        if (VERBOSE) {
            println("TEST: final disk usage = $finalDiskUsage")
        }

        val maxStartFinalDiskUsage = maxOf(startDiskUsage, finalDiskUsage)
        val maxDiskUsage = dir.maxUsedSizeInBytes
        assertTrue(
            maxDiskUsage <= 4 * maxStartFinalDiskUsage,
            "forceMerge used too much temporary space: starting usage was " +
                "$startDiskUsage bytes; final usage was $finalDiskUsage bytes; " +
                "max temp usage was $maxDiskUsage but should have been at most " +
                "${4 * maxStartFinalDiskUsage} (= 4X starting usage), BEFORE=$startListing" +
                "AFTER=${listFiles(dir)}"
        )
        dir.close()
    }

    private fun listFiles(dir: Directory): String {
        val infos = SegmentInfos.readLatestCommit(dir)
        val sb = StringBuilder()
        sb.appendLine()
        for (info in infos) {
            for (file in info.files()) {
                sb.append(file.padEnd(20)).append(dir.fileLength(file)).appendLine()
            }
            if (info.info.useCompoundFile) {
                info.info.codec.compoundFormat().getCompoundReader(dir, info.info).use { cfs ->
                    for (file in cfs.listAll()) {
                        sb.append(" |- (inside compound file) ")
                            .append(file.padEnd(20))
                            .append(cfs.fileLength(file))
                            .appendLine()
                    }
                }
            }
        }
        sb.appendLine()
        return sb.toString()
    }

    @Test
    @Throws(IOException::class)
    fun testBackgroundForceMerge() {
        val dir = newDirectory()
        for (pass in 0 until 2) {
            val writer = IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy(51))
            )
            val doc = Document()
            doc.add(newStringField("field", "aaa", Field.Store.NO))
            for (i in 0 until 100) {
                writer.addDocument(doc)
            }
            writer.forceMerge(1, false)

            if (pass == 0) {
                writer.close()
                DirectoryReader.open(dir).use { reader ->
                    assertEquals(1, reader.leaves().size)
                }
            } else {
                writer.addDocument(doc)
                writer.addDocument(doc)
                writer.close()

                DirectoryReader.open(dir).use { reader ->
                    assertTrue(reader.leaves().size > 1)
                }

                val infos = SegmentInfos.readLatestCommit(dir)
                assertEquals(2, infos.size())
            }
        }

        dir.close()
    }

    @Ignore
    @AwaitsFix(bugUrl = "https://github.com/apache/lucene/issues/13478")
    @Test
    @Throws(IOException::class)
    fun testMergePerField() {
        val config = IndexWriterConfig()
        val mergeScheduler = object : ConcurrentMergeScheduler() {
            override fun getIntraMergeExecutor(merge: MergePolicy.OneMerge): Executor {
                return requireNotNull(intraMergeExecutor)
            }
        }
        mergeScheduler.setMaxMergesAndThreads(4, 4)
        config.setMergeScheduler(mergeScheduler)
        val codec: Codec = TestUtil.getDefaultCodec()
        val barrier = TwoPartyBarrier()
        config.setCodec(
            object : FilterCodec(codec.name, codec) {
                override fun postingsFormat(): PostingsFormat {
                    return object : PerFieldPostingsFormat() {
                        override fun getPostingsFormatForField(field: String): PostingsFormat {
                            return BlockingOnMergePostingsFormat(
                                TestUtil.getDefaultPostingsFormat(),
                                barrier
                            )
                        }
                    }
                }

                override fun docValuesFormat(): DocValuesFormat {
                    return object : PerFieldDocValuesFormat() {
                        override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                            return BlockingOnMergeDocValuesFormat(
                                TestUtil.getDefaultDocValuesFormat(),
                                barrier
                            )
                        }
                    }
                }
            }
        )
        newDirectory().use { directory ->
            IndexWriter(directory, config).use { writer ->
                val numDocs = 50 + random().nextInt(100)
                val numFields = 5 + random().nextInt(5)
                for (d in 0 until numDocs) {
                    val doc = Document()
                    for (f in 0 until numFields * 2) {
                        val field = "f$f"
                        val value = "v-${random().nextInt(100)}"
                        if (f % 2 == 0) {
                            doc.add(StringField(field, value, Field.Store.NO))
                        } else {
                            doc.add(BinaryDocValuesField(field, BytesRef(value)))
                        }
                        doc.add(LongPoint("p$f", random().nextInt(10000).toLong()))
                    }
                    writer.addDocument(doc)
                    if (random().nextInt(100) < 10) {
                        writer.flush()
                    }
                }
                writer.forceMerge(1)
                DirectoryReader.open(writer).use { reader ->
                    assertEquals(numDocs, reader.numDocs())
                }
            }
        }
    }

    private class BlockingOnMergePostingsFormat(
        private val postingsFormat: PostingsFormat,
        private val barrier: TwoPartyBarrier
    ) : PostingsFormat(postingsFormat.name) {
        override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
            val delegate = postingsFormat.fieldsConsumer(state)
            return object : FieldsConsumer() {
                override fun write(fields: Fields, norms: NormsProducer?) {
                    delegate.write(fields, norms)
                }

                override fun merge(mergeState: MergeState, norms: NormsProducer?) {
                    barrier.await(1, TimeUnit.SECONDS)
                    delegate.merge(mergeState, norms)
                }

                override fun close() {
                    delegate.close()
                }
            }
        }

        override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
            return postingsFormat.fieldsProducer(state)
        }
    }

    private class BlockingOnMergeDocValuesFormat(
        private val docValuesFormat: DocValuesFormat,
        private val barrier: TwoPartyBarrier
    ) : DocValuesFormat(docValuesFormat.name) {
        override fun fieldsConsumer(state: SegmentWriteState): DocValuesConsumer {
            val delegate = docValuesFormat.fieldsConsumer(state)
            return object : DocValuesConsumer() {
                override fun addNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    delegate.addNumericField(field, valuesProducer)
                }

                override fun addBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    delegate.addBinaryField(field, valuesProducer)
                }

                override fun addSortedField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    delegate.addSortedField(field, valuesProducer)
                }

                override fun addSortedNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    delegate.addSortedNumericField(field, valuesProducer)
                }

                override fun addSortedSetField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    delegate.addSortedSetField(field, valuesProducer)
                }

                override fun merge(mergeState: MergeState) {
                    barrier.await(1, TimeUnit.SECONDS)
                    delegate.merge(mergeState)
                }

                override fun close() {
                    delegate.close()
                }
            }
        }

        override fun fieldsProducer(state: SegmentReadState): DocValuesProducer {
            return docValuesFormat.fieldsProducer(state)
        }
    }

    private class TwoPartyBarrier {
        private val arrived = AtomicInt(0)
        private var release = CountDownLatch(1)

        fun await(timeout: Long, unit: TimeUnit) {
            val arrival = arrived.addAndFetch(1)
            if (arrival == 2) {
                release.countDown()
                return
            }
            if (!release.await(timeout, unit)) {
                throw AssertionError("broken barrier")
            }
        }
    }
}
