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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSizeBoundedForceMerge : LuceneTestCase() {

    @Throws(IOException::class)
    private fun addDocs(writer: IndexWriter, numDocs: Int) {
        addDocs(writer, numDocs, false)
    }

    @Throws(IOException::class)
    private fun addDocs(writer: IndexWriter, numDocs: Int, withID: Boolean) {
        for (i in 0 until numDocs) {
            val doc = Document()
            if (withID) {
                doc.add(StringField("id", "$i", Field.Store.NO))
            }
            writer.addDocument(doc)
        }
        writer.commit()
    }

    companion object {
        private fun newWriterConfig(): IndexWriterConfig {
            val conf = IndexWriterConfig()
            // Force using the default codec and not e.g. SimpleText which has a non-deterministic byte size
            // of its segment info due to escape characters.
            conf.setCodec(TestUtil.getDefaultCodec())
            conf.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            conf.setRAMBufferSizeMB(IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB)
            // don't use compound files, because the overhead make size checks unreliable.
            conf.setUseCompoundFile(false)
            // prevent any merges by default.
            conf.setMergePolicy(NoMergePolicy.INSTANCE)
            return conf
        }
    }

    @Test
    @Throws(Exception::class)
    fun testByteSizeLimit() {
        // tests that the max merge size constraint is applied during forceMerge.
        val dir: Directory = ByteBuffersDirectory()

        // Prepare an index w/ several small segments and a large one.
        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)
        val numSegments = 15
        for (i in 0 until numSegments) {
            val numDocs = if (i == 7) 30 else 1
            addDocs(writer, numDocs)
        }
        writer.close()

        var sis = SegmentInfos.readLatestCommit(dir)
        var numberOfSegmentsOfMinimumSize = 1
        for (i in 1 until sis.size()) {
            if (sis.info(i).sizeInBytes() == sis.info(0).sizeInBytes()) {
                numberOfSegmentsOfMinimumSize++
            }
        }
        assertEquals(numSegments - 1, numberOfSegmentsOfMinimumSize)

        val min = sis.info(0).sizeInBytes().toDouble()

        conf = newWriterConfig()
        val lmp = LogByteSizeMergePolicy()
        lmp.maxMergeMBForForcedMerge = min / (1 shl 20)
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        // Should only be 3 segments in the index, because one of them exceeds the size limit
        sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(3, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNumDocsLimit() {
        // tests that the max merge docs constraint is applied during forceMerge.
        val dir: Directory = ByteBuffersDirectory()

        // Prepare an index w/ several small segments and a large one.
        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 5)
        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)

        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 3
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        // Should only be 3 segments in the index, because one of them exceeds the size limit
        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(3, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testLastSegmentTooLarge() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 5)

        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 3
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(2, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFirstSegmentTooLarge() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 5)
        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)

        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 3
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(2, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAllSegmentsSmall() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)

        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 3
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(1, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAllSegmentsLarge() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)

        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 2
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(3, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testOneLargeOneSmall() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 3)
        addDocs(writer, 5)
        addDocs(writer, 3)
        addDocs(writer, 5)

        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 3
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(4, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMergeFactor() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 3)
        addDocs(writer, 5)
        addDocs(writer, 3)
        addDocs(writer, 3)

        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 3
        lmp.mergeFactor = 2
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        // Should only be 4 segments in the index, because of the merge factor and
        // max merge docs settings.
        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(4, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSingleMergeableSegment() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 3)
        addDocs(writer, 5)
        addDocs(writer, 3)

        // delete the last document, so that the last segment is merged.
        writer.deleteDocuments(Term("id", "10"))
        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 3
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        // Verify that the last segment does not have deletions.
        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(3, sis.size())
        assertFalse(sis.info(2).hasDeletions())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSingleNonMergeableSegment() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 3, true)

        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 3
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        // Verify that the last segment does not have deletions.
        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(1, sis.size())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSingleMergeableTooLargeSegment() {
        val dir: Directory = ByteBuffersDirectory()

        var conf = newWriterConfig()
        var writer = IndexWriter(dir, conf)

        addDocs(writer, 5, true)

        // delete the last document

        writer.deleteDocuments(Term("id", "4"))
        writer.close()

        conf = newWriterConfig()
        val lmp: LogMergePolicy = LogDocMergePolicy()
        lmp.maxMergeDocs = 2
        conf.setMergePolicy(lmp)

        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        // Verify that the last segment does not have deletions.
        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(1, sis.size())
        assertTrue(sis.info(0).hasDeletions())
        dir.close()
    }
}

