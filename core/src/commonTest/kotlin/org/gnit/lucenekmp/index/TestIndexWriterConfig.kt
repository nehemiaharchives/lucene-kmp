package org.gnit.lucenekmp.index

import kotlin.test.*
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.util.InfoStream

class TestIndexWriterConfig : LuceneTestCase() {

    private class MySimilarity : ClassicSimilarity() {
        // Does not implement anything - used only for type checking on IndexWriterConfig.
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
        // Ensures that every setter returns IndexWriterConfig to allow chaining.
        val liveSetters = hashSetOf<String>()
        val allSetters = hashSetOf<String>()

        liveSetters.add("setCheckPendingFlushUpdate")
        liveSetters.add("setMaxBufferedDocs")
        liveSetters.add("setMergedSegmentWarmer")
        liveSetters.add("setMergePolicy")
        liveSetters.add("setRAMBufferSizeMB")
        liveSetters.add("setUseCompoundFile")

        allSetters.add("setCheckPendingFlushUpdate")
        allSetters.add("setCodec")
        allSetters.add("setCommitOnClose")
        allSetters.add("setFlushPolicy")
        allSetters.add("setIndexCommit")
        allSetters.add("setIndexCreatedVersionMajor")
        allSetters.add("setIndexDeletionPolicy")
        allSetters.add("setIndexSort")
        allSetters.add("setIndexWriter")
        allSetters.add("setInfoStream")
        allSetters.add("setLeafSorter")
        allSetters.add("setMaxBufferedDocs")
        allSetters.add("setMaxFullFlushMergeWaitMillis")
        allSetters.add("setMergedSegmentWarmer")
        allSetters.add("setMergePolicy")
        allSetters.add("setMergeScheduler")
        allSetters.add("setOpenMode")
        allSetters.add("setParentField")
        allSetters.add("setRAMBufferSizeMB")
        allSetters.add("setRAMPerThreadHardLimitMB")
        allSetters.add("setReaderPooling")
        allSetters.add("setSimilarity")
        allSetters.add("setSoftDeletesField")
        allSetters.add("setUseCompoundFile")

        for (setter in liveSetters) {
            assertTrue(
                allSetters.contains(setter),
                "setter method not overridden by IndexWriterConfig: $setter"
            )
        }

        val conf = IndexWriterConfig(MockAnalyzer(random()))
        assertSame(conf, conf.setCheckPendingFlushUpdate(conf.checkPendingFlushOnUpdate))
        assertSame(conf, conf.setMaxBufferedDocs(4))
        assertSame(conf, conf.setMergedSegmentWarmer(IndexWriter.IndexReaderWarmer { }))
        assertSame(conf, conf.setMergePolicy(conf.mergePolicy))
        assertSame(conf, conf.setRAMBufferSizeMB(conf.rAMBufferSizeMB))
        assertSame(conf, conf.setUseCompoundFile(conf.useCompoundFile))
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
        val conf = IndexWriterConfig(MockAnalyzer(random()))
        val liveConf: LiveIndexWriterConfig = conf

        assertSame(conf.analyzer, liveConf.analyzer)
        assertEquals(conf.indexCommit, liveConf.indexCommit)
        assertEquals(conf.indexDeletionPolicy::class, liveConf.indexDeletionPolicy::class)
        assertEquals(conf.openMode, liveConf.openMode)
        assertSame(conf.similarity, liveConf.similarity)
        assertEquals(conf.rAMBufferSizeMB, liveConf.rAMBufferSizeMB, 0.0)
        assertEquals(conf.maxBufferedDocs, liveConf.maxBufferedDocs)
        assertEquals(conf.mergedSegmentWarmer, liveConf.mergedSegmentWarmer)
        assertEquals(conf.mergePolicy::class, liveConf.mergePolicy::class)
        assertEquals(conf.readerPooling, liveConf.readerPooling)
        assertEquals(conf.flushPolicy::class, liveConf.flushPolicy::class)
        assertEquals(conf.rAMPerThreadHardLimitMB, liveConf.rAMPerThreadHardLimitMB)
        assertEquals(conf.codec, liveConf.codec)
        assertEquals(conf.infoStream, liveConf.infoStream)
        assertEquals(conf.useCompoundFile, liveConf.useCompoundFile)
        assertEquals(conf.checkPendingFlushOnUpdate, liveConf.checkPendingFlushOnUpdate)
        assertEquals(conf.softDeletesField, liveConf.softDeletesField)
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
        val str = IndexWriterConfig(MockAnalyzer(random())).toString()
        val fields =
            listOf(
                "analyzer",
                "ramBufferSizeMB",
                "maxBufferedDocs",
                "mergedSegmentWarmer",
                "delPolicy",
                "commit",
                "openMode",
                "similarity",
                "mergeScheduler",
                "codec",
                "infoStream",
                "mergePolicy",
                "readerPooling",
                "perThreadHardLimitMB",
                "useCompoundFile",
                "commitOnClose",
                "indexSort",
                "checkPendingFlushOnUpdate",
                "softDeletesField",
                "maxFullFlushMergeWaitMillis",
                "leafSorter",
                "eventListener",
                "parentField",
                "writer"
            )
        for (field in fields) {
            assertTrue(str.indexOf(field) != -1, "$field not found in toString")
        }
    }

    @Test
    fun testInvalidValues() {
        val conf = IndexWriterConfig(MockAnalyzer(random()))

        // Test IndexDeletionPolicy
        assertEquals(KeepOnlyLastCommitDeletionPolicy::class, conf.indexDeletionPolicy::class)
        conf.setIndexDeletionPolicy(SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy()))
        assertEquals(SnapshotDeletionPolicy::class, conf.indexDeletionPolicy::class)
        expectThrows(IllegalArgumentException::class) {
            conf.setIndexDeletionPolicy(null)
        }

        // Test MergeScheduler
        assertEquals(ConcurrentMergeScheduler::class, conf.mergeScheduler::class)
        conf.setMergeScheduler(SerialMergeScheduler())
        assertEquals(SerialMergeScheduler::class, conf.mergeScheduler::class)
        expectThrows(IllegalArgumentException::class) {
            conf.setMergeScheduler(null)
        }

        // Test Similarity:
        // we shouldnt assert what the default is, just that it's not null.
        assertTrue(IndexSearcher.defaultSimilarity === conf.similarity)
        conf.setSimilarity(MySimilarity())
        assertEquals(MySimilarity::class, conf.similarity::class)
        expectThrows(IllegalArgumentException::class) {
            conf.setSimilarity(null)
        }

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
        conf.setMergePolicy(LogDocMergePolicy())
        assertEquals(LogDocMergePolicy::class, conf.mergePolicy::class)
        expectThrows(IllegalArgumentException::class) {
            conf.setMergePolicy(null)
        }
    }

    @Test
    fun testLiveChangeToCFS() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy(true))
        // Start false:
        iwc.setUseCompoundFile(false)
        iwc.mergePolicy.noCFSRatio = 0.0
        val w = IndexWriter(dir, iwc)
        // Change to true:
        w.config.setUseCompoundFile(true)

        val doc = Document()
        doc.add(StringField("field", "foo", Store.NO))
        w.addDocument(doc)
        w.commit()
        assertTrue(w.newestSegment()!!.info.useCompoundFile, "Expected CFS after commit")

        doc.add(StringField("field", "foo", Store.NO))
        w.addDocument(doc)
        w.commit()
        w.forceMerge(1)
        w.commit()

        // no compound files after merge
        assertFalse(w.newestSegment()!!.info.useCompoundFile, "Expected Non-CFS after merge")

        val lmp = w.config.mergePolicy
        lmp.noCFSRatio = 1.0
        lmp.maxCFSSegmentSizeMB = Double.POSITIVE_INFINITY

        w.addDocument(doc)
        w.forceMerge(1)
        w.commit()
        assertTrue(w.newestSegment()!!.info.useCompoundFile, "Expected CFS after merge")
        w.close()
        dir.close()
    }
}
