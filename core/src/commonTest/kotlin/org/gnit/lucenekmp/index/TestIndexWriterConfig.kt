package org.gnit.lucenekmp.index

import kotlin.test.*
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.util.InfoStream
import okio.IOException
import org.gnit.lucenekmp.store.ByteBuffersDirectory

class TestIndexWriterConfig : LuceneTestCase() {

    private class MyIndexDeletionPolicy : IndexDeletionPolicy() {
        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {
        }

        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {
        }
    }

    private class MyMergePolicy : MergePolicy() {
        override fun findMerges(
            mergeTrigger: MergeTrigger,
            segmentInfos: SegmentInfos,
            mergeContext: MergeContext
        ): MergeSpecification? {
            return null
        }

        override fun findForcedMerges(
            segmentInfos: SegmentInfos,
            maxSegmentCount: Int,
            segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>,
            mergeContext: MergeContext
        ): MergeSpecification? {
            return null
        }

        override fun findForcedDeletesMerges(
            segmentInfos: SegmentInfos,
            mergeContext: MergeContext
        ): MergeSpecification? {
            return null
        }
    }

    @Test
    fun testDefaults() {
        val conf = IndexWriterConfig(MockAnalyzer(random()))
        assertEquals(MockAnalyzer::class, conf.analyzer::class)
        assertNull(conf.indexCommit)
        assertEquals(KeepOnlyLastCommitDeletionPolicy::class, conf.indexDeletionPolicy::class)
        assertEquals(ConcurrentMergeScheduler::class, conf.mergeScheduler::class)
        assertEquals(IndexWriterConfig.OpenMode.CREATE_OR_APPEND, conf.openMode)
        assertTrue(IndexSearcher.defaultSimilarity === conf.similarity)
        assertEquals(IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB, conf.rAMBufferSizeMB, 0.0)
        assertEquals(IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS, conf.maxBufferedDocs)
        assertEquals(IndexWriterConfig.DEFAULT_READER_POOLING, conf.readerPooling)
        assertNull(conf.mergedSegmentWarmer)
        assertEquals(TieredMergePolicy::class, conf.mergePolicy::class)
        assertEquals(FlushByRamOrCountsPolicy::class, conf.flushPolicy::class)
        assertEquals(
            IndexWriterConfig.DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB,
            conf.rAMPerThreadHardLimitMB
        )
        assertEquals(Codec.default, conf.codec)
        assertEquals(InfoStream.default, conf.infoStream)
        assertEquals(IndexWriterConfig.DEFAULT_USE_COMPOUND_FILE_SYSTEM, conf.useCompoundFile)
        assertTrue(conf.checkPendingFlushOnUpdate)
    }

    @Test
    fun testSettersChaining() {
        val conf = IndexWriterConfig(MockAnalyzer(random()))
        assertSame(conf, conf.setRAMBufferSizeMB(conf.rAMBufferSizeMB))
        assertSame(conf, conf.setMergeScheduler(conf.mergeScheduler))
    }

    @Test
    fun testReuse() {
        val dir: Directory = newDirectory()
        val conf = IndexWriterConfig()
        IndexWriter(dir, conf).close()
        expectThrows(IllegalStateException::class) {
            IndexWriter(dir, conf)
        }
        dir.close()
    }

    @Test
    fun testOverrideGetters() {
        // TODO: implement reflection-based checks of getters
    }

    @Test
    fun testConstants() {
        assertEquals(-1, IndexWriterConfig.DISABLE_AUTO_FLUSH)
        assertEquals(IndexWriterConfig.DISABLE_AUTO_FLUSH, IndexWriterConfig.DEFAULT_MAX_BUFFERED_DELETE_TERMS)
        assertEquals(IndexWriterConfig.DISABLE_AUTO_FLUSH, IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS)
        assertEquals(16.0, IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB, 0.0)
        assertEquals(true, IndexWriterConfig.DEFAULT_READER_POOLING)
        assertEquals(true, IndexWriterConfig.DEFAULT_USE_COMPOUND_FILE_SYSTEM)
    }

    @Test
    fun testToString() {
        // TODO: implement when IndexWriterConfig.toString is fully supported
    }

    @Test
    fun testInvalidValues() {
        val conf = IndexWriterConfig(MockAnalyzer(random()))

        // Test IndexDeletionPolicy
        assertEquals(KeepOnlyLastCommitDeletionPolicy::class, conf.indexDeletionPolicy::class)
        conf.setIndexDeletionPolicy(MyIndexDeletionPolicy())
        assertEquals(MyIndexDeletionPolicy::class, conf.indexDeletionPolicy::class)

        // Test MergeScheduler
        assertEquals(ConcurrentMergeScheduler::class, conf.mergeScheduler::class)
        conf.setMergeScheduler(SerialMergeScheduler())
        assertEquals(SerialMergeScheduler::class, conf.mergeScheduler::class)

        // Test Similarity
        assertTrue(IndexSearcher.defaultSimilarity === conf.similarity)
        conf.setSimilarity(BM25Similarity())
        assertEquals(BM25Similarity::class, conf.similarity::class)

        expectThrows(IllegalArgumentException::class) {
            conf.setMaxBufferedDocs(1)
        }

        expectThrows(IllegalArgumentException::class) {
            conf.setMaxBufferedDocs(4)
            conf.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            conf.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        }

        conf.setRAMBufferSizeMB(IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB)
        conf.setMaxBufferedDocs(IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS)
        expectThrows(IllegalArgumentException::class) {
            conf.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
        }

        expectThrows(IllegalArgumentException::class) {
            conf.setRAMPerThreadHardLimitMB(2048)
        }

        expectThrows(IllegalArgumentException::class) {
            conf.setRAMPerThreadHardLimitMB(0)
        }

        // Test MergePolicy
        assertEquals(TieredMergePolicy::class, conf.mergePolicy::class)
        conf.setMergePolicy(MyMergePolicy())
        assertEquals(MyMergePolicy::class, conf.mergePolicy::class)
    }

    @Test
    fun testLiveChangeToCFS() {
        // TODO: implement when merge policy CFS controls are available
    }

    private fun newDirectory(): Directory = ByteBuffersDirectory()
}

