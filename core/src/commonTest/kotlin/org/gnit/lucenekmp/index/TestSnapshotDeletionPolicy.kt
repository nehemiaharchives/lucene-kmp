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
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

//
// This was developed for Lucene In Action,
// http://lucenebook.com
//

open class TestSnapshotDeletionPolicy : LuceneTestCase() {
    companion object {
        const val INDEX_PATH = "test.snapshots"
    }

    protected open fun getConfig(random: Random, dp: IndexDeletionPolicy?): IndexWriterConfig {
        val conf = newIndexWriterConfig(MockAnalyzer(random))
        if (dp != null) {
            conf.setIndexDeletionPolicy(dp)
        }
        return conf
    }

    @Throws(Exception::class)
    protected fun checkSnapshotExists(dir: Directory, c: IndexCommit) {
        val segFileName = requireNotNull(c.segmentsFileName)
        assertTrue(
            slowFileExists(dir, segFileName),
            "segments file not found in directory: $segFileName"
        )
    }

    @Throws(Exception::class)
    protected fun checkMaxDoc(commit: IndexCommit, expectedMaxDoc: Int) {
        val reader = DirectoryReader.open(commit)
        try {
            assertEquals(expectedMaxDoc, reader.maxDoc())
        } finally {
            reader.close()
        }
    }

    protected var snapshots: MutableList<IndexCommit> = ArrayList()

    @Throws(RuntimeException::class, IOException::class)
    protected fun prepareIndexAndSnapshots(
        sdp: SnapshotDeletionPolicy,
        writer: IndexWriter,
        numSnapshots: Int
    ) {
        for (i in 0..<numSnapshots) {
            // create dummy document to trigger commit.
            writer.addDocument(Document())
            writer.commit()
            snapshots.add(sdp.snapshot())
        }
    }

    @Throws(IOException::class)
    protected open fun getDeletionPolicy(): SnapshotDeletionPolicy {
        return SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy())
    }

    @Throws(Exception::class)
    protected fun assertSnapshotExists(
        dir: Directory,
        sdp: SnapshotDeletionPolicy,
        numSnapshots: Int,
        checkIndexCommitSame: Boolean
    ) {
        for (i in 0..<numSnapshots) {
            val snapshot = snapshots[i]
            checkMaxDoc(snapshot, i + 1)
            checkSnapshotExists(dir, snapshot)
            if (checkIndexCommitSame) {
                assertSame(snapshot, sdp.getIndexCommit(snapshot.generation))
            } else {
                assertEquals(
                    snapshot.generation,
                    requireNotNull(sdp.getIndexCommit(snapshot.generation)).generation
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    open fun testSnapshotDeletionPolicy() {
        val fsDir = newDirectory()
        runTest(random(), fsDir)
        fsDir.close()
    }

    @Throws(Exception::class)
    private fun runTest(random: Random, dir: Directory) {
        val maxIterations = if (TEST_NIGHTLY) 100 else 10

        val dp = getDeletionPolicy()
        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random))
                .setIndexDeletionPolicy(dp)
                .setMaxBufferedDocs(2)
        )

        // Verify we catch misuse:
        expectThrows(IllegalStateException::class) {
            dp.snapshot()
        }

        writer.commit()

        val t = object : Thread() {
            override fun run() {
                var iterations = 0
                val doc = Document()
                val customType = FieldType(TextField.TYPE_STORED)
                customType.setStoreTermVectors(true)
                customType.setStoreTermVectorPositions(true)
                customType.setStoreTermVectorOffsets(true)
                doc.add(newField("content", "aaa", customType))
                do {
                    for (i in 0..<27) {
                        try {
                            writer.addDocument(doc)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            fail("addDocument failed")
                        }
                        if (i % 2 == 0) {
                            try {
                                writer.commit()
                            } catch (e: Exception) {
                                throw RuntimeException(e)
                            }
                        }
                    }
                    try {
                        Thread.sleep(1)
                    } catch (ie: InterruptedException) {
                        throw ThreadInterruptedException(ie)
                    }
                } while (++iterations < maxIterations)
            }
        }

        t.start()

        // While the above indexing thread is running, take many
        // backups:
        do {
            backupIndex(dir, dp)
            Thread.sleep(20)
        } while (t.isAlive())

        t.join()

        // Add one more document to force writer to commit a
        // final segment, so deletion policy has a chance to
        // delete again:
        val doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        doc.add(newField("content", "aaa", customType))
        writer.addDocument(doc)

        // Make sure we don't have any leftover files in the
        // directory:
        writer.close()
        TestIndexWriter.assertNoUnreferencedFiles(
            dir,
            "some files were not deleted but should have been"
        )
    }

    /**
     * Example showing how to use the SnapshotDeletionPolicy to take a backup. This method does not
     * really do a backup; instead, it reads every byte of every file just to test that the files
     * indeed exist and are readable even while the index is changing.
     */
    @Throws(Exception::class)
    fun backupIndex(dir: Directory, dp: SnapshotDeletionPolicy) {
        // To backup an index we first take a snapshot:
        val snapshot = dp.snapshot()
        try {
            copyFiles(dir, snapshot)
        } finally {
            // Make sure to release the snapshot, otherwise these
            // files will never be deleted during this IndexWriter
            // session:
            dp.release(snapshot)
        }
    }

    @Throws(Exception::class)
    private fun copyFiles(dir: Directory, cp: IndexCommit) {
        // While we hold the snapshot, and nomatter how long
        // we take to do the backup, the IndexWriter will
        // never delete the files in the snapshot:
        val files = cp.fileNames
        for (fileName in files) {
            // NOTE: in a real backup you would not use
            // readFile; you would need to use something else
            // that copies the file to a backup location.  This
            // could even be a spawned shell process (eg "tar",
            // "zip") that takes the list of files and builds a
            // backup.
            readFile(dir, fileName)
        }
    }

    var buffer = ByteArray(4096)

    @Throws(Exception::class)
    private fun readFile(dir: Directory, name: String) {
        val input: IndexInput = dir.openInput(name, IOContext.READONCE)
        try {
            val size = dir.fileLength(name)
            var bytesLeft = size
            while (bytesLeft > 0) {
                val numToRead =
                    if (bytesLeft < buffer.size) bytesLeft.toInt() else buffer.size
                input.readBytes(buffer, 0, numToRead, false)
                bytesLeft -= numToRead.toLong()
            }
            // Don't do this in your real backups!  This is just
            // to force a backup to take a somewhat long time, to
            // make sure we are exercising the fact that the
            // IndexWriter should not delete this file even when I
            // take my time reading it.
            Thread.sleep(1)
        } finally {
            input.close()
        }
    }

    @Test
    @Throws(Exception::class)
    open fun testBasicSnapshots() {
        val numSnapshots = 3

        // Create 3 snapshots: snapshot0, snapshot1, snapshot2
        val dir = newDirectory()
        var writer = IndexWriter(dir, getConfig(random(), getDeletionPolicy()))
        var sdp = writer.config.indexDeletionPolicy as SnapshotDeletionPolicy
        prepareIndexAndSnapshots(sdp, writer, numSnapshots)
        writer.close()

        assertEquals(numSnapshots, sdp.getSnapshots().size)
        assertEquals(numSnapshots, sdp.getSnapshotCount())
        assertSnapshotExists(dir, sdp, numSnapshots, true)

        // open a reader on a snapshot - should succeed.
        DirectoryReader.open(snapshots[0]).close()

        // open a new IndexWriter w/ no snapshots to keep and assert that all snapshots are gone.
        sdp = getDeletionPolicy()
        writer = IndexWriter(dir, getConfig(random(), sdp))
        writer.deleteUnusedFiles()
        writer.close()
        assertEquals(1, DirectoryReader.listCommits(dir).size, "no snapshots should exist")
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiThreadedSnapshotting() {
        val dir = newDirectory()

        val writer = IndexWriter(dir, getConfig(random(), getDeletionPolicy()))
        val sdp = writer.config.indexDeletionPolicy as SnapshotDeletionPolicy

        val threads = arrayOfNulls<Thread>(10)
        val snapshots = arrayOfNulls<IndexCommit>(threads.size)
        val startingGun = CountDownLatch(1)
        for (i in threads.indices) {
            val finalI = i
            threads[i] = object : Thread() {
                override fun run() {
                    try {
                        startingGun.await()
                        writer.addDocument(Document())
                        writer.commit()
                        snapshots[finalI] = sdp.snapshot()
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            }
            threads[i]!!.setName("t$i")
        }

        for (t in threads) {
            t!!.start()
        }

        startingGun.countDown()

        for (t in threads) {
            t!!.join()
        }

        // Do one last commit, so that after we release all snapshots, we stay w/ one commit
        writer.addDocument(Document())
        writer.commit()

        for (i in threads.indices) {
            sdp.release(requireNotNull(snapshots[i]))
            writer.deleteUnusedFiles()
        }
        assertEquals(1, DirectoryReader.listCommits(dir).size)
        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    open fun testRollbackToOldSnapshot() {
        val numSnapshots = 2
        val dir = newDirectory()

        val sdp = getDeletionPolicy()
        var writer = IndexWriter(dir, getConfig(random(), sdp))
        prepareIndexAndSnapshots(sdp, writer, numSnapshots)
        writer.close()

        // now open the writer on "snapshot0" - make sure it succeeds
        writer = IndexWriter(dir, getConfig(random(), sdp).setIndexCommit(snapshots[0]))
        // this does the actual rollback
        writer.commit()
        writer.deleteUnusedFiles()
        assertSnapshotExists(dir, sdp, numSnapshots - 1, false)
        writer.close()

        // but 'snapshot1' files will still exist (need to release snapshot before they can be deleted).
        val segFileName = requireNotNull(snapshots[1].segmentsFileName)
        assertTrue(
            slowFileExists(dir, segFileName),
            "snapshot files should exist in the directory: $segFileName"
        )

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    open fun testReleaseSnapshot() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, getConfig(random(), getDeletionPolicy()))
        val sdp = writer.config.indexDeletionPolicy as SnapshotDeletionPolicy
        prepareIndexAndSnapshots(sdp, writer, 1)

        // Create another commit - we must do that, because otherwise the "snapshot"
        // files will still remain in the index, since it's the last commit.
        writer.addDocument(Document())
        writer.commit()

        // Release
        val segFileName = requireNotNull(snapshots[0].segmentsFileName)
        sdp.release(snapshots[0])
        writer.deleteUnusedFiles()
        writer.close()
        assertFalse(
            slowFileExists(dir, segFileName),
            "segments file should not be found in dirctory: $segFileName"
        )
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    open fun testSnapshotLastCommitTwice() {
        val dir = newDirectory()

        val writer = IndexWriter(dir, getConfig(random(), getDeletionPolicy()))
        val sdp = writer.config.indexDeletionPolicy as SnapshotDeletionPolicy
        writer.addDocument(Document())
        writer.commit()

        val s1 = sdp.snapshot()
        val s2 = sdp.snapshot()
        assertSame(s1, s2) // should be the same instance

        // create another commit
        writer.addDocument(Document())
        writer.commit()

        // release "s1" should not delete "s2"
        sdp.release(s1)
        writer.deleteUnusedFiles()
        checkSnapshotExists(dir, s2)

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    open fun testMissingCommits() {
        // Tests the behavior of SDP when commits that are given at ctor are missing
        // on onInit().
        val dir = newDirectory()
        var writer = IndexWriter(dir, getConfig(random(), getDeletionPolicy()))
        val sdp = writer.config.indexDeletionPolicy as SnapshotDeletionPolicy
        writer.addDocument(Document())
        writer.commit()
        val s1 = sdp.snapshot()

        // create another commit, not snapshotted.
        writer.addDocument(Document())
        writer.close()

        // open a new writer w/ KeepOnlyLastCommit policy, so it will delete "s1"
        // commit.
        IndexWriter(dir, getConfig(random(), null)).close()

        assertFalse(
            slowFileExists(dir, requireNotNull(s1.segmentsFileName)),
            "snapshotted commit should not exist"
        )
        dir.close()
    }
}
