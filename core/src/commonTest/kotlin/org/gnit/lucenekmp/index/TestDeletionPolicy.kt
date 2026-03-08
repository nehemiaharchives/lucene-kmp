package org.gnit.lucenekmp.index

import okio.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/*
  Verify we can read the pre-2.1 file format, do searches
  against it, and add documents to it.
*/
class TestDeletionPolicy : LuceneTestCase() {
    private fun verifyCommitOrder(commits: MutableList<out IndexCommit>) {
        if (commits.isEmpty()) {
            return
        }
        val firstCommit = commits[0]
        var last = SegmentInfos.generationFromSegmentsFileName(requireNotNull(firstCommit.segmentsFileName))
        assertEquals(last, firstCommit.generation)
        for (i in 1..<commits.size) {
            val commit = commits[i]
            val now = SegmentInfos.generationFromSegmentsFileName(requireNotNull(commit.segmentsFileName))
            assertTrue(now > last, "SegmentInfos commits are out-of-order")
            assertEquals(now, commit.generation)
            last = now
        }
    }

    internal inner class KeepAllDeletionPolicy(private val dir: Directory) : IndexDeletionPolicy() {
        var numOnInit = 0
        var numOnCommit = 0

        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {
            verifyCommitOrder(commits)
            numOnInit++
        }

        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {
            val lastCommit = commits[commits.size - 1]
            val r = DirectoryReader.open(dir)
            assertEquals(
                lastCommit.segmentCount,
                r.leaves().size,
                "lastCommit.segmentCount()=${lastCommit.segmentCount} vs IndexReader.segmentCount=${r.leaves().size}"
            )
            r.close()
            verifyCommitOrder(commits)
            numOnCommit++
        }
    }

    /** This is useful for adding to a big index when you know readers are not using it. */
    internal inner class KeepNoneOnInitDeletionPolicy : IndexDeletionPolicy() {
        var numOnInit = 0
        var numOnCommit = 0

        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {
            verifyCommitOrder(commits)
            numOnInit++
            // On init, delete all commit points:
            for (commit in commits) {
                commit.delete()
                assertTrue(commit.isDeleted)
            }
        }

        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {
            verifyCommitOrder(commits)
            val size = commits.size
            // Delete all but last one:
            for (i in 0..<size - 1) {
                commits[i].delete()
            }
            numOnCommit++
        }
    }

    internal inner class KeepLastNDeletionPolicy(var numToKeep: Int) : IndexDeletionPolicy() {
        var numOnInit = 0
        var numOnCommit = 0
        var numDelete = 0
        var seen = hashSetOf<String>()

        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {
            if (VERBOSE) {
                println("TEST: onInit")
            }
            verifyCommitOrder(commits)
            numOnInit++
            // do no deletions on init
            doDeletes(commits, false)
        }

        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {
            if (VERBOSE) {
                println("TEST: onCommit")
            }
            verifyCommitOrder(commits)
            doDeletes(commits, true)
        }

        private fun doDeletes(commits: MutableList<out IndexCommit>, isCommit: Boolean) {
            // Assert that we really are only called for each new
            // commit:
            if (isCommit) {
                val fileName = requireNotNull(commits[commits.size - 1].segmentsFileName)
                if (seen.contains(fileName)) {
                    throw RuntimeException("onCommit was called twice on the same commit point: $fileName")
                }
                seen.add(fileName)
                numOnCommit++
            }
            val size = commits.size
            for (i in 0..<size - numToKeep) {
                commits[i].delete()
                numDelete++
            }
        }
    }

    companion object {
        @Throws(IOException::class)
        fun getCommitTime(commit: IndexCommit): Long {
            return commit.userData["commitTime"]!!.toLong()
        }
    }

    /*
     * Delete a commit only when it has been obsoleted by N
     * seconds.
     */
    internal inner class ExpirationTimeDeletionPolicy(
        private val dir: Directory,
        private val expirationTimeSeconds: Double
    ) : IndexDeletionPolicy() {
        var numDelete = 0

        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {
            if (commits.isEmpty()) {
                return
            }
            verifyCommitOrder(commits)
            onCommit(commits)
        }

        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {
            verifyCommitOrder(commits)

            val lastCommit = commits[commits.size - 1]

            // Any commit older than expireTime should be deleted:
            val expireTime = getCommitTime(lastCommit) / 1000.0 - expirationTimeSeconds

            for (commit in commits) {
                val modTime = getCommitTime(commit) / 1000.0
                if (commit != lastCommit && modTime < expireTime) {
                    commit.delete()
                    numDelete += 1
                }
            }
        }
    }

    /*
     * Test "by time expiration" deletion policy:
     */
    // TODO: this wall-clock-dependent test doesn't seem to actually test any deletionpolicy logic?
    @Nightly
    @Test
    @Throws(IOException::class)
    fun testExpirationTimeDeletionPolicy() {
        val seconds = 2.0

        val dir = newDirectory()
        var conf =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setIndexDeletionPolicy(ExpirationTimeDeletionPolicy(dir, seconds))
        var mp = conf.mergePolicy
        mp.noCFSRatio = 1.0
        var writer = IndexWriter(dir, conf)
        var policy = writer.config.indexDeletionPolicy as ExpirationTimeDeletionPolicy
        var commitData = hashMapOf<String, String>()
        commitData["commitTime"] = System.nanoTime().toString()
        writer.setLiveCommitData(commitData.entries)
        writer.commit()
        writer.close()

        var lastDeleteTime = 0L
        val targetNumDelete = TestUtil.nextInt(random(), 1, 5)
        while (policy.numDelete < targetNumDelete) {
            // Record last time when writer performed deletes of
            // past commits
            lastDeleteTime = System.nanoTime()
            conf =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.APPEND)
                    .setIndexDeletionPolicy(policy)
            mp = conf.mergePolicy
            mp.noCFSRatio = 1.0
            writer = IndexWriter(dir, conf)
            policy = writer.config.indexDeletionPolicy as ExpirationTimeDeletionPolicy
            repeat(17) {
                addDoc(writer)
            }
            commitData = hashMapOf()
            commitData["commitTime"] = System.nanoTime().toString()
            writer.setLiveCommitData(commitData.entries)
            writer.commit()
            writer.close()

            runBlocking {
                delay((1000.0 * (seconds / 5.0)).toLong())
            }
        }

        // Then simplistic check: just verify that the
        // segments_N's that still exist are in fact within SECONDS
        // seconds of the last one's mod time, and, that I can
        // open a reader on each:
        var gen = SegmentInfos.getLastCommitGeneration(dir)

        var fileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", gen)
        var oneSecondResolution = true

        while (gen > 0) {
            try {
                val reader = DirectoryReader.open(dir)
                reader.close()
                fileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", gen)

                // if we are on a filesystem that seems to have only
                // 1 second resolution, allow +1 second in commit
                // age tolerance:
                val sis = SegmentInfos.readCommit(dir, requireNotNull(fileName))
                assertEquals(Version.LATEST, sis.commitLuceneVersion)
                assertEquals(Version.LATEST, sis.getMinSegmentLuceneVersion())
                val modTime = sis.userData["commitTime"]!!.toLong()
                oneSecondResolution = oneSecondResolution and (modTime % 1000L == 0L)
                val leeway = ((seconds + if (oneSecondResolution) 1.0 else 0.0) * 1000).toLong()

                assertTrue(
                    lastDeleteTime - modTime <= leeway,
                    "commit point was older than $seconds seconds (${lastDeleteTime - modTime} ms) but did not get deleted "
                )
            } catch (_: IOException) {
                // OK
                break
            }

            dir.deleteFile(requireNotNull(IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", gen)))
            gen--
        }

        dir.close()
    }

    /*
     * Test a silly deletion policy that keeps all commits around.
     */
    @Test
    @Throws(IOException::class)
    fun testKeepAllDeletionPolicy() {
        for (pass in 0..<2) {
            if (VERBOSE) {
                println("TEST: cycle pass=$pass")
            }

            val useCompoundFile = (pass % 2) != 0

            val dir = newDirectory()

            var conf =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(KeepAllDeletionPolicy(dir))
                    .setMaxBufferedDocs(10)
                    .setMergeScheduler(SerialMergeScheduler())
            var mp = conf.mergePolicy
            mp.noCFSRatio = if (useCompoundFile) 1.0 else 0.0
            var writer = IndexWriter(dir, conf)
            var policy = writer.config.indexDeletionPolicy as KeepAllDeletionPolicy
            repeat(107) {
                addDoc(writer)
            }
            writer.close()

            val needsMerging =
                run {
                    val r = DirectoryReader.open(dir)
                    val value = r.leaves().size != 1
                    r.close()
                    value
                }
            if (needsMerging) {
                conf =
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setOpenMode(OpenMode.APPEND)
                        .setIndexDeletionPolicy(policy)
                mp = conf.mergePolicy
                mp.noCFSRatio = if (useCompoundFile) 1.0 else 0.0
                if (VERBOSE) {
                    println("TEST: open writer for forceMerge")
                }
                writer = IndexWriter(dir, conf)
                policy = writer.config.indexDeletionPolicy as KeepAllDeletionPolicy
                writer.forceMerge(1)
                writer.close()
            }

            assertEquals(if (needsMerging) 2 else 1, policy.numOnInit)

            // If we are not auto committing then there should
            // be exactly 2 commits (one per close above):
            assertEquals(1 + if (needsMerging) 1 else 0, policy.numOnCommit)

            // Test listCommits
            val commits = DirectoryReader.listCommits(dir)
            // 2 from closing writer
            assertEquals(1 + if (needsMerging) 1 else 0, commits.size)

            // Make sure we can open a reader on each commit:
            for (commit in commits) {
                val r = DirectoryReader.open(commit)
                r.close()
            }

            // Simplistic check: just verify all segments_N's still
            // exist, and, I can open a reader on each:
            var gen = SegmentInfos.getLastCommitGeneration(dir)
            while (gen > 0) {
                val reader = DirectoryReader.open(dir)
                reader.close()
                dir.deleteFile(requireNotNull(IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", gen)))
                gen--

                if (gen > 0) {
                    // Now that we've removed a commit point, which
                    // should have orphan'd at least one index file.
                    // Open & close a writer and assert that it
                    // actually removed something:
                    val preCount = dir.listAll().size
                    writer =
                        IndexWriter(
                            dir,
                            newIndexWriterConfig(MockAnalyzer(random()))
                                .setOpenMode(OpenMode.APPEND)
                                .setIndexDeletionPolicy(policy)
                        )
                    writer.close()
                    val postCount = dir.listAll().size
                    assertTrue(postCount < preCount)
                }
            }

            dir.close()
        }
    }

    /* Uses KeepAllDeletionPolicy to keep all commits around,
     * then, opens a new IndexWriter on a previous commit
     * point. */
    @Test
    @Throws(IOException::class)
    fun testOpenPriorSnapshot() {
        val dir = newDirectory()

        var writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(KeepAllDeletionPolicy(dir))
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy(10))
            )
        var policy = writer.config.indexDeletionPolicy as KeepAllDeletionPolicy
        for (i in 0..<10) {
            addDoc(writer)
            if ((1 + i) % 2 == 0) {
                writer.commit()
            }
        }
        writer.close()

        val commits = DirectoryReader.listCommits(dir)
        assertEquals(5, commits.size)
        var lastCommit: IndexCommit? = null
        for (commit in commits) {
            if (lastCommit == null || commit.generation > lastCommit.generation) {
                lastCommit = commit
            }
        }
        assertTrue(lastCommit != null)

        // Now add 1 doc and merge
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(policy)
            )
        addDoc(writer)
        assertEquals(11, writer.getDocStats().numDocs)
        writer.forceMerge(1)
        writer.close()

        assertEquals(6, DirectoryReader.listCommits(dir).size)

        // Now open writer on the commit just before merge:
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(policy)
                    .setIndexCommit(lastCommit)
                    .setMergePolicy(newLogMergePolicy(10))
            )
        assertEquals(10, writer.getDocStats().numDocs)

        // Should undo our rollback:
        writer.rollback()

        var r = DirectoryReader.open(dir)
        // Still merged, still 11 docs
        assertEquals(1, r.leaves().size)
        assertEquals(11, r.numDocs())
        r.close()

        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(policy)
                    .setIndexCommit(lastCommit)
                    .setMergePolicy(newLogMergePolicy(10))
            )
        assertEquals(10, writer.getDocStats().numDocs)
        // Commits the rollback:
        writer.close()

        // Now 7 because we made another commit
        assertEquals(7, DirectoryReader.listCommits(dir).size)

        r = DirectoryReader.open(dir)
        // Not fully merged because we rolled it back, and now only
        // 10 docs
        assertTrue(r.leaves().size > 1)
        assertEquals(10, r.numDocs())
        r.close()

        // Re-merge
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(policy)
            )
        writer.forceMerge(1)
        writer.close()

        r = DirectoryReader.open(dir)
        assertEquals(1, r.leaves().size)
        assertEquals(10, r.numDocs())
        r.close()

        // Now open writer on the commit just before merging,
        // but this time keeping only the last commit:
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexCommit(lastCommit)
                    .setMergePolicy(newLogMergePolicy(10))
            )
        assertEquals(10, writer.getDocStats().numDocs)

        // Reader still sees fully merged index, because writer
        // opened on the prior commit has not yet committed:
        r = DirectoryReader.open(dir)
        assertEquals(1, r.leaves().size)
        assertEquals(10, r.numDocs())
        r.close()

        writer.close()

        // Now reader sees not-fully-merged index:
        r = DirectoryReader.open(dir)
        assertTrue(r.leaves().size > 1)
        assertEquals(10, r.numDocs())
        r.close()

        dir.close()
    }

    /* Test keeping NO commit points.  This is a viable and
     * useful case eg where you want to build a big index and
     * you know there are no readers.
     */
    @Test
    @Throws(IOException::class)
    fun testKeepNoneOnInitDeletionPolicy() {
        for (pass in 0..<2) {
            val useCompoundFile = (pass % 2) != 0

            val dir = newDirectory()

            var conf =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setIndexDeletionPolicy(KeepNoneOnInitDeletionPolicy())
                    .setMaxBufferedDocs(10)
            var mp = conf.mergePolicy
            mp.noCFSRatio = if (useCompoundFile) 1.0 else 0.0
            var writer = IndexWriter(dir, conf)
            var policy = writer.config.indexDeletionPolicy as KeepNoneOnInitDeletionPolicy
            repeat(107) {
                addDoc(writer)
            }
            writer.close()

            conf =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.APPEND)
                    .setIndexDeletionPolicy(policy)
            mp = conf.mergePolicy
            mp.noCFSRatio = 1.0
            writer = IndexWriter(dir, conf)
            policy = writer.config.indexDeletionPolicy as KeepNoneOnInitDeletionPolicy
            writer.forceMerge(1)
            writer.close()

            assertEquals(2, policy.numOnInit)
            // If we are not auto committing then there should
            // be exactly 2 commits (one per close above):
            assertEquals(2, policy.numOnCommit)

            // Simplistic check: just verify the index is in fact
            // readable:
            val reader = DirectoryReader.open(dir)
            reader.close()

            dir.close()
        }
    }

    /*
     * Test a deletion policy that keeps last N commits.
     */
    @Test
    @Throws(IOException::class)
    fun testKeepLastNDeletionPolicy() {
        val n = 5

        for (pass in 0..<2) {
            val useCompoundFile = (pass % 2) != 0

            val dir = newDirectory()

            var policy = KeepLastNDeletionPolicy(n)
            for (j in 0..<n + 1) {
                val conf =
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setOpenMode(OpenMode.CREATE)
                        .setIndexDeletionPolicy(policy)
                        .setMaxBufferedDocs(10)
                val mp = conf.mergePolicy
                mp.noCFSRatio = if (useCompoundFile) 1.0 else 0.0
                val writer = IndexWriter(dir, conf)
                policy = writer.config.indexDeletionPolicy as KeepLastNDeletionPolicy
                repeat(17) {
                    addDoc(writer)
                }
                writer.forceMerge(1)
                writer.close()
            }

            assertTrue(policy.numDelete > 0)
            assertEquals(n + 1, policy.numOnInit)
            assertEquals(n + 1, policy.numOnCommit)

            // Simplistic check: just verify only the past N segments_N's still
            // exist, and, I can open a reader on each:
            var gen = SegmentInfos.getLastCommitGeneration(dir)
            for (i in 0..<n + 1) {
                try {
                    val reader = DirectoryReader.open(dir)
                    reader.close()
                    if (i == n) {
                        fail("should have failed on commits prior to last $n")
                    }
                } catch (e: IOException) {
                    if (i != n) {
                        throw e
                    }
                }
                if (i < n) {
                    dir.deleteFile(requireNotNull(IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", gen)))
                }
                gen--
            }

            dir.close()
        }
    }

    /*
     * Test a deletion policy that keeps last N commits
     * around, through creates.
     */
    @Test
    @Throws(IOException::class)
    fun testKeepLastNDeletionPolicyWithCreates() {
        val n = 10

        for (pass in 0..<2) {
            val useCompoundFile = (pass % 2) != 0

            val dir = newDirectory()
            var conf =
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setIndexDeletionPolicy(KeepLastNDeletionPolicy(n))
                    .setMaxBufferedDocs(10)
            var mp = conf.mergePolicy
            mp.noCFSRatio = if (useCompoundFile) 1.0 else 0.0
            var writer = IndexWriter(dir, conf)
            var policy = writer.config.indexDeletionPolicy as KeepLastNDeletionPolicy
            writer.close()
            val searchTerm = Term("content", "aaa")
            val query: Query = TermQuery(searchTerm)

            for (i in 0..<n + 1) {
                conf =
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setOpenMode(OpenMode.APPEND)
                        .setIndexDeletionPolicy(policy)
                        .setMaxBufferedDocs(10)
                mp = conf.mergePolicy
                mp.noCFSRatio = if (useCompoundFile) 1.0 else 0.0
                writer = IndexWriter(dir, conf)
                policy = writer.config.indexDeletionPolicy as KeepLastNDeletionPolicy
                for (j in 0..<17) {
                    addDocWithID(writer, i * (n + 1) + j)
                }
                // this is a commit
                writer.close()
                conf =
                    IndexWriterConfig(MockAnalyzer(random()))
                        .setIndexDeletionPolicy(policy)
                        .setMergePolicy(NoMergePolicy.INSTANCE)
                writer = IndexWriter(dir, conf)
                policy = writer.config.indexDeletionPolicy as KeepLastNDeletionPolicy
                writer.deleteDocuments(Term("id", "" + (i * (n + 1) + 3)))
                // this is a commit
                writer.close()
                var reader = DirectoryReader.open(dir)
                var searcher: IndexSearcher = newSearcher(reader)
                var hits: Array<ScoreDoc> = searcher.search(query, 1000).scoreDocs
                assertEquals(16, hits.size)
                reader.close()

                writer =
                    IndexWriter(
                        dir,
                        newIndexWriterConfig(MockAnalyzer(random()))
                            .setOpenMode(OpenMode.CREATE)
                            .setIndexDeletionPolicy(policy)
                    )
                policy = writer.config.indexDeletionPolicy as KeepLastNDeletionPolicy
                // This will not commit: there are no changes
                // pending because we opened for "create":
                writer.close()
            }

            assertEquals(3 * (n + 1) + 1, policy.numOnInit)
            assertEquals(3 * (n + 1) + 1, policy.numOnCommit)

            var rwReader = DirectoryReader.open(dir)
            var searcher: IndexSearcher = newSearcher(rwReader)
            var hits: Array<ScoreDoc> = searcher.search(query, 1000).scoreDocs
            assertEquals(0, hits.size)

            // Simplistic check: just verify only the past N segments_N's still
            // exist, and, I can open a reader on each:
            var gen = SegmentInfos.getLastCommitGeneration(dir)

            var expectedCount = 0

            rwReader.close()

            for (i in 0..<n + 1) {
                try {
                    val reader = DirectoryReader.open(dir)

                    // Work backwards in commits on what the expected
                    // count should be.
                    searcher = newSearcher(reader)
                    hits = searcher.search(query, 1000).scoreDocs
                    assertEquals(expectedCount, hits.size)
                    expectedCount =
                        when (expectedCount) {
                            0 -> 16
                            16 -> 17
                            17 -> 0
                            else -> expectedCount
                        }
                    reader.close()
                    if (i == n) {
                        fail("should have failed on commits before last $n")
                    }
                } catch (e: IOException) {
                    if (i != n) {
                        throw e
                    }
                }
                if (i < n) {
                    dir.deleteFile(requireNotNull(IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", gen)))
                }
                gen--
            }

            dir.close()
        }
    }

    private fun addDocWithID(writer: IndexWriter, id: Int) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        doc.add(newStringField("id", "$id", Field.Store.NO))
        writer.addDocument(doc)
    }

    private fun addDoc(writer: IndexWriter) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        writer.addDocument(doc)
    }
}
