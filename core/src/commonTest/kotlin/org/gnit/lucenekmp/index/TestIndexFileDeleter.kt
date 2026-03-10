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
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.InfoStream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/*
  Verify we can read the pre-2.1 file format, do searches
  against it, and add documents to it.
*/

@OptIn(ExperimentalAtomicApi::class)
class TestIndexFileDeleter : LuceneTestCase() {

    @Test
    fun testDeleteLeftoverFiles() {
        val dir = newDirectory()

        val mergePolicy = newLogMergePolicy(true, 10)

        // This test expects all of its segments to be in CFS
        mergePolicy.noCFSRatio = 1.0
        mergePolicy.maxCFSSegmentSizeMB = Double.POSITIVE_INFINITY

        var writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(mergePolicy)
                    .setUseCompoundFile(true)
            )

        var i: Int
        for (j in 0..<35) {
            addDoc(writer, j)
        }
        i = 35
        writer.config.mergePolicy.noCFSRatio = 0.0
        writer.config.setUseCompoundFile(false)
        while (i < 45) {
            addDoc(writer, i)
            i++
        }
        writer.close()

        // Delete one doc so we get a .del file:
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setUseCompoundFile(true)
            )
        val searchTerm = Term("id", "7")
        writer.deleteDocuments(searchTerm)
        writer.close()

        // read in index to try to not depend on codec-specific filenames so much
        val sis = SegmentInfos.readLatestCommit(dir)
        val si0 = sis.info(0).info
        val si1 = sis.info(1).info
        val si3 = sis.info(3).info

        // Now, artificially create an extra .del file & extra
        // .s0 file:
        val files = dir.listAll()

        /*
        for(int j=0;j<files.length;j++) {
          System.out.println(j + ": " + files[j]);
        }
        */

        // TODO: fix this test better
        val ext = ".liv"

        // Create a bogus separate del file for a
        // segment that already has a separate del file:
        copyFile(dir, "_0_1$ext", "_0_2$ext")

        // Create a bogus separate del file for a
        // segment that does not yet have a separate del file:
        copyFile(dir, "_0_1$ext", "_1_1$ext")

        // Create a bogus separate del file for a
        // non-existent segment:
        copyFile(dir, "_0_1$ext", "_188_1$ext")

        val cfsFiles0 =
            if (si0.codec::class.simpleName == "SimpleTextCodec") {
                arrayOf("_0.scf")
            } else {
                arrayOf("_0.cfs", "_0.cfe")
            }

        // Create a bogus segment file:
        copyFile(dir, cfsFiles0[0], "_188.cfs")

        // Create a bogus fnm file when the CFS already exists:
        copyFile(dir, cfsFiles0[0], "_0.fnm")

        // Create a bogus cfs file shadowing a non-cfs segment:

        // TODO: assert is bogus (relies upon codec-specific filenames)
        assertTrue(slowFileExists(dir, "_3.fdt") || slowFileExists(dir, "_3.fld"))

        val cfsFiles3 =
            if (si3.codec::class.simpleName == "SimpleTextCodec") {
                arrayOf("_3.scf")
            } else {
                arrayOf("_3.cfs", "_3.cfe")
            }
        for (f in cfsFiles3) {
            assertFalse(slowFileExists(dir, f))
        }

        val cfsFiles1 =
            if (si1.codec::class.simpleName == "SimpleTextCodec") {
                arrayOf("_1.scf")
            } else {
                arrayOf("_1.cfs", "_1.cfe")
            }
        copyFile(dir, cfsFiles1[0], "_3.cfs")

        val filesPre = dir.listAll()

        // Open & close a writer: it should delete the above files and nothing more:
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
            )
        writer.close()

        val files2 = dir.listAll()
        dir.close()

        files.sort()
        files2.sort()

        val dif = difFiles(files, files2)

        if (!files.contentEquals(files2)) {
            fail(
                "IndexFileDeleter failed to delete unreferenced extra files: should have deleted " +
                    (filesPre.size - files.size) +
                    " files but only deleted " +
                    (filesPre.size - files2.size) +
                    "; expected files:\n    " +
                    asString(files) +
                    "\n  actual files:\n    " +
                    asString(files2) +
                    "\ndiff: " +
                    dif
            )
        }
    }

    private fun difFiles(files1: Array<String>, files2: Array<String>): Set<String> {
        val set1 = mutableSetOf<String>()
        val set2 = mutableSetOf<String>()
        val extra = mutableSetOf<String>()

        set1.addAll(files1)
        set2.addAll(files2)
        for (o in set1) {
            if (o !in set2) {
                extra.add(o)
            }
        }
        for (o in set2) {
            if (o !in set1) {
                extra.add(o)
            }
        }
        return extra
    }

    private fun asString(l: Array<String>): String {
        var s = ""
        for (i in l.indices) {
            if (i > 0) {
                s += "\n    "
            }
            s += l[i]
        }
        return s
    }

    fun copyFile(dir: Directory, src: String, dest: String) {
        dir.openInput(src, newIOContext(random())).use { input: IndexInput ->
            dir.createOutput(dest, newIOContext(random())).use { output: IndexOutput ->
                val b = ByteArray(1024)
                var remainder = input.length()
                while (remainder > 0) {
                    val len = minOf(b.size.toLong(), remainder).toInt()
                    input.readBytes(b, 0, len)
                    output.writeBytes(b, 0, len)
                    remainder -= len.toLong()
                }
            }
        }
    }

    private fun addDoc(writer: IndexWriter, id: Int) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        doc.add(newStringField("id", id.toString(), Field.Store.NO))
        writer.addDocument(doc)
    }

    @Ignore
    @Test
    fun testVirusScannerDoesntCorruptIndex() {
        // addVirusChecker/enableVirusChecker/disableVirusChecker are still commented out in the KMP test framework.
    }

    @Test
    fun testNoSegmentsDotGenInflation() {
        val dir = newMockDirectory()

        // empty commit
        IndexWriter(dir, IndexWriterConfig()).close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(1, sis.generation)

        // no inflation
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(1, sis.generation)

        dir.close()
    }

    @Test
    fun testSegmentsInflation() {
        val dir = newMockDirectory()
        dir.checkIndexOnClose = false // TODO: allow falling back more than one commit

        // empty commit
        IndexWriter(dir, IndexWriterConfig()).close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(1, sis.generation)

        // add trash commit
        dir.createOutput(IndexFileNames.SEGMENTS + "_2", IOContext.DEFAULT).close()

        // ensure inflation
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(2, sis.generation)

        // add another trash commit
        dir.createOutput(IndexFileNames.SEGMENTS + "_4", IOContext.DEFAULT).close()
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(4, sis.generation)

        dir.close()
    }

    @Test
    fun testSegmentNameInflation() {
        val dir = newMockDirectory()

        // empty commit
        IndexWriter(dir, IndexWriterConfig()).close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(0, sis.counter)

        // no inflation
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(0, sis.counter)

        // add trash per-segment file
        dir.createOutput(IndexFileNames.segmentFileName("_0", "", "foo"), IOContext.DEFAULT).close()

        // ensure inflation
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(1, sis.counter)

        // add trash per-segment file
        dir.createOutput(IndexFileNames.segmentFileName("_3", "", "foo"), IOContext.DEFAULT).close()
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(4, sis.counter)

        // ensure we write _4 segment next
        val iw = IndexWriter(dir, IndexWriterConfig())
        iw.addDocument(Document())
        iw.commit()
        iw.close()
        val latestSis = SegmentInfos.readLatestCommit(dir)
        assertEquals("_4", latestSis.info(0).info.name)
        assertEquals(5, latestSis.counter)

        dir.close()
    }

    @Test
    fun testGenerationInflation() {
        val dir = newMockDirectory()

        // initial commit
        val iw = IndexWriter(dir, IndexWriterConfig())
        iw.addDocument(Document())
        iw.commit()
        iw.close()

        // no deletes: start at 1
        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(1, sis.info(0).nextDelGen)

        // no inflation
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(1, sis.info(0).nextDelGen)

        // add trash per-segment deletes file
        dir.createOutput(requireNotNull(IndexFileNames.fileNameFromGeneration("_0", "del", 2)), IOContext.DEFAULT).close()

        // ensure inflation
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(3, sis.info(0).nextDelGen)

        dir.close()
    }

    @Test
    fun testTrashyFile() {
        val dir = newMockDirectory()
        dir.checkIndexOnClose = false // TODO: maybe handle such trash better elsewhere...

        // empty commit
        IndexWriter(dir, IndexWriterConfig()).close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(1, sis.generation)

        // add trash file
        dir.createOutput(IndexFileNames.SEGMENTS + "_", IOContext.DEFAULT).close()

        // no inflation
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(1, sis.generation)

        dir.close()
    }

    @Test
    fun testTrashyGenFile() {
        val dir = newMockDirectory()

        // initial commit
        val iw = IndexWriter(dir, IndexWriterConfig())
        iw.addDocument(Document())
        iw.commit()
        iw.close()

        // no deletes: start at 1
        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(1, sis.info(0).nextDelGen)

        // add trash file
        dir.createOutput("_1_A", IOContext.DEFAULT).close()

        // no inflation
        inflateGens(sis, dir.listAll().asList(), InfoStream.default)
        assertEquals(1, sis.info(0).nextDelGen)

        dir.close()
    }

    // IFD's inflater is "raw" and expects to only see codec files,
    // and rightfully so, it filters them out.
    private fun inflateGens(sis: SegmentInfos, files: Collection<String>, stream: InfoStream) {
        val filtered = mutableListOf<String>()
        for (file in files) {
            if (IndexFileNames.CODEC_FILE_PATTERN.matches(file) ||
                file.startsWith(IndexFileNames.SEGMENTS) ||
                file.startsWith(IndexFileNames.PENDING_SEGMENTS)) {
                filtered.add(file)
            }
        }
        IndexFileDeleter.inflateGens(sis, filtered, stream)
    }

    // LUCENE-5919
    @Test
    fun testExcInDecRef() {
        val dir = newMockDirectory()

        // disable slow things: we don't rely upon sleeps here.
        dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        dir.useSlowOpenClosers = false

        val doFailExc = AtomicBoolean(false)

        dir.failOn(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (doFailExc.load() && random().nextInt(4) == 1) {
                        if (callStackContains(IndexFileDeleter::class, "decRef")) {
                            throw RuntimeException("fake fail")
                        }
                    }
                }
            }
        )

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
        // iwc.setMergeScheduler(new SerialMergeScheduler());
        val ms = iwc.mergeScheduler
        if (ms is ConcurrentMergeScheduler) {
            val suppressFakeFail =
                object : ConcurrentMergeScheduler() {
                    override fun handleMergeException(exc: Throwable) {
                        // suppress only FakeIOException:
                        if (exc is RuntimeException && exc.message == "fake fail") {
                            // ok to ignore
                        } else if (exc is IllegalStateException && exc.cause != null && exc.cause!!.message == "fake fail") {
                            // also ok to ignore
                        } else {
                            super.handleMergeException(exc)
                        }
                    }
                }
            suppressFakeFail.setMaxMergesAndThreads(ms.maxMergeCount, ms.maxThreadCount)
            iwc.setMergeScheduler(suppressFakeFail)
        }

        val w = RandomIndexWriter(random(), dir, iwc)

        // Since we hit exc during merging, a partial
        // forceMerge can easily return when there are still
        // too many segments in the index:
        w.setDoRandomForceMergeAssert(false)

        doFailExc.store(true)
        val iters = atLeast(3)
        // TODO reduced ITERS = atLeast(1000) to atLeast(3) for dev speed
        for (iter in 0..<iters) {
            try {
                if (random().nextInt(10) == 5) {
                    w.commit()
                } else if (random().nextInt(10) == 7) {
                    w.getReader(true, false).close()
                } else {
                    val doc = Document()
                    doc.add(newTextField("field", "some text", Field.Store.NO))
                    w.addDocument(doc)
                }
            } catch (t: Throwable) {
                if (t.toString().contains("fake fail") ||
                    (t.cause != null && t.cause.toString().contains("fake fail"))) {
                    // ok
                } else {
                    throw t
                }
            }
        }

        doFailExc.store(false)
        w.close()
        dir.close()
    }

    // LUCENE-6835: make sure best-effort to not create an "apparently but not really" corrupt index
    // is working:
    @Test
    fun testExcInDeleteFile() {
        val iters = atLeast(3)
        // TODO reduced ITERS = atLeast(10) to atLeast(3) for dev speed
        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("TEST: iter=$iter")
            }
            val dir = newMockDirectory()

            val doFailExc = AtomicBoolean(false)

            dir.failOn(
                object : MockDirectoryWrapper.Failure() {
                    override fun eval(dir: MockDirectoryWrapper) {
                        if (doFailExc.load() && random().nextInt(4) == 1) {
                            if (callStackContains(MockDirectoryWrapper::class, "deleteFile")) {
                                throw MockDirectoryWrapper.FakeIOException()
                            }
                        }
                    }
                }
            )

            val iwc = newIndexWriterConfig()
            iwc.setMergeScheduler(SerialMergeScheduler())
            val w = RandomIndexWriter(random(), dir, iwc)
            w.addDocument(Document())

            // makes segments_1
            if (VERBOSE) {
                println("TEST: now commit")
            }
            w.commit()

            w.addDocument(Document())
            doFailExc.store(true)
            if (VERBOSE) {
                println("TEST: now close")
            }
            try {
                w.close()
                if (VERBOSE) {
                    println("TEST: no exception (ok)")
                }
            } catch (re: RuntimeException) {
                assertTrue(re.cause is MockDirectoryWrapper.FakeIOException)
                // good
                if (VERBOSE) {
                    println("TEST: got expected exception:")
                    re.printStackTrace()
                }
            } catch (fioe: MockDirectoryWrapper.FakeIOException) {
                // good
                if (VERBOSE) {
                    println("TEST: got expected exception:")
                    fioe.printStackTrace()
                }
            }
            doFailExc.store(false)
            assertFalse(w.w.isOpen())

            for (name in dir.listAll()) {
                if (name.startsWith(IndexFileNames.SEGMENTS)) {
                    if (VERBOSE) {
                        println("TEST: now read $name")
                    }
                    SegmentInfos.readCommit(dir, name)
                }
            }
            dir.close()
        }
    }

    @Test
    fun testThrowExceptionWhileDeleteCommits() {
        newMockDirectory().use { dir ->
            val failOnDeleteCommits = AtomicBoolean(false)
            dir.failOn(
                object : MockDirectoryWrapper.Failure() {
                    override fun eval(dir: MockDirectoryWrapper) {
                        if (failOnDeleteCommits.load()) {
                            if (callStackContains(IndexFileDeleter::class, "deleteCommits") &&
                                callStackContains(MockDirectoryWrapper::class, "deleteFile") &&
                                random().nextInt(4) == 1) {
                                throw MockDirectoryWrapper.FakeIOException()
                            }
                        }
                    }
                }
            )
            val snapshotDeletionPolicy =
                SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy())
            val config =
                newIndexWriterConfig().setIndexDeletionPolicy(snapshotDeletionPolicy)

            RandomIndexWriter(random(), dir, config).use { writer ->
                writer.addDocument(Document())
                writer.commit()

                val snapshotCommit = snapshotDeletionPolicy.snapshot()
                val commits = TestUtil.nextInt(random(), 1, 3)
                for (i in 0..<commits) {
                    writer.addDocument(Document())
                    writer.commit()
                }
                snapshotDeletionPolicy.release(snapshotCommit)
                failOnDeleteCommits.store(true)
                try {
                    writer.w.deleteUnusedFiles()
                } catch (e: IOException) {
                    assertTrue(e is MockDirectoryWrapper.FakeIOException)
                }
                failOnDeleteCommits.store(false)
                for (c in 0..<commits) {
                    writer.addDocument(Document())
                    writer.commit()
                }
            }
        }
    }
}
