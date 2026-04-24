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
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestPersistentSnapshotDeletionPolicy : TestSnapshotDeletionPolicy() {
    @Throws(IOException::class)
    private fun getDeletionPolicy(dir: Directory): SnapshotDeletionPolicy {
        return PersistentSnapshotDeletionPolicy(
            KeepOnlyLastCommitDeletionPolicy(),
            dir,
            OpenMode.CREATE
        )
    }

    @Test
    @Throws(Exception::class)
    fun testExistingSnapshots() {
        val numSnapshots = 3
        val dir = newMockDirectory()
        var writer = IndexWriter(dir, getConfig(random(), getDeletionPolicy(dir)))
        var psdp = writer.config.indexDeletionPolicy as PersistentSnapshotDeletionPolicy
        assertNull(psdp.getLastSaveFile())
        prepareIndexAndSnapshots(psdp, writer, numSnapshots)
        assertNotNull(psdp.getLastSaveFile())
        writer.close()

        // Make sure only 1 save file exists:
        var count = 0
        for (file in dir.listAll()) {
            if (file.startsWith(PersistentSnapshotDeletionPolicy.SNAPSHOTS_PREFIX)) {
                count++
            }
        }
        assertEquals(1, count)

        // Make sure we fsync:
        dir.crash()
        dir.clearCrash()

        // Re-initialize and verify snapshots were persisted
        psdp = PersistentSnapshotDeletionPolicy(
            KeepOnlyLastCommitDeletionPolicy(),
            dir,
            OpenMode.APPEND
        )

        writer = IndexWriter(dir, getConfig(random(), psdp))
        psdp = writer.config.indexDeletionPolicy as PersistentSnapshotDeletionPolicy

        assertEquals(numSnapshots, psdp.getSnapshots().size)
        assertEquals(numSnapshots, psdp.getSnapshotCount())
        assertSnapshotExists(dir, psdp, numSnapshots, false)

        writer.addDocument(Document())
        writer.commit()
        snapshots.add(psdp.snapshot())
        assertEquals(numSnapshots + 1, psdp.getSnapshots().size)
        assertEquals(numSnapshots + 1, psdp.getSnapshotCount())
        assertSnapshotExists(dir, psdp, numSnapshots + 1, false)

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNoSnapshotInfos() {
        val dir = newDirectory()
        PersistentSnapshotDeletionPolicy(
            KeepOnlyLastCommitDeletionPolicy(),
            dir,
            OpenMode.CREATE
        )
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMissingSnapshots() {
        val dir = newDirectory()

        expectThrows(IllegalStateException::class) {
            PersistentSnapshotDeletionPolicy(
                KeepOnlyLastCommitDeletionPolicy(),
                dir,
                OpenMode.APPEND
            )
        }

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExceptionDuringSave() {
        val dir = newMockDirectory()
        dir.failOn(
            object : MockDirectoryWrapper.Failure() {
                @Throws(IOException::class)
                override fun eval(dir: MockDirectoryWrapper) {
                    if (callStackContains(PersistentSnapshotDeletionPolicy::class, "persist")) {
                        throw IOException("now fail on purpose")
                    }
                }
            }
        )
        val writer = IndexWriter(
            dir,
            getConfig(
                random(),
                PersistentSnapshotDeletionPolicy(
                    KeepOnlyLastCommitDeletionPolicy(),
                    dir,
                    OpenMode.CREATE_OR_APPEND
                )
            )
        )
        writer.addDocument(Document())
        writer.commit()

        val psdp = writer.config.indexDeletionPolicy as PersistentSnapshotDeletionPolicy
        try {
            psdp.snapshot()
        } catch (ioe: IOException) {
            if (ioe.message == "now fail on purpose") {
                // ok
            } else {
                throw ioe
            }
        }
        assertEquals(0, psdp.getSnapshotCount())
        writer.close()
        assertEquals(1, DirectoryReader.listCommits(dir).size)
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSnapshotRelease() {
        val dir = newDirectory()
        var writer = IndexWriter(dir, getConfig(random(), getDeletionPolicy(dir)))
        var psdp = writer.config.indexDeletionPolicy as PersistentSnapshotDeletionPolicy
        prepareIndexAndSnapshots(psdp, writer, 1)
        writer.close()

        psdp.release(snapshots[0])

        psdp = PersistentSnapshotDeletionPolicy(
            KeepOnlyLastCommitDeletionPolicy(),
            dir,
            OpenMode.APPEND
        )
        assertEquals(0, psdp.getSnapshotCount(), "Should have no snapshots !")
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSnapshotReleaseByGeneration() {
        val dir = newDirectory()
        var writer = IndexWriter(dir, getConfig(random(), getDeletionPolicy(dir)))
        var psdp = writer.config.indexDeletionPolicy as PersistentSnapshotDeletionPolicy
        prepareIndexAndSnapshots(psdp, writer, 1)
        writer.close()

        psdp.release(snapshots[0].generation)

        psdp = PersistentSnapshotDeletionPolicy(
            KeepOnlyLastCommitDeletionPolicy(),
            dir,
            OpenMode.APPEND
        )
        assertEquals(0, psdp.getSnapshotCount(), "Should have no snapshots !")
        dir.close()
    }

    // tests inherited from TestSnapshotDeletionPolicy
    @Test
    override fun testSnapshotDeletionPolicy() = super.testSnapshotDeletionPolicy()

    @Test
    override fun testBasicSnapshots() = super.testBasicSnapshots()

    @Test
    override fun testMultiThreadedSnapshotting() = super.testMultiThreadedSnapshotting()

    @Test
    override fun testRollbackToOldSnapshot() = super.testRollbackToOldSnapshot()

    @Test
    override fun testReleaseSnapshot() = super.testReleaseSnapshot()

    @Test
    override fun testSnapshotLastCommitTwice() = super.testSnapshotLastCommitTwice()

    @Test
    override fun testMissingCommits() = super.testMissingCommits()
}
