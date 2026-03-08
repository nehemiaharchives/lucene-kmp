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

import okio.Path
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.OutputStreamWriter
import org.gnit.lucenekmp.jdkport.PrintWriter
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.StringWriter
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.MergeInfo
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.SameThreadExecutorService
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** JUnit adaptation of an older test case DocTest. */
class TestDoc : LuceneTestCase() {
    private lateinit var workDir: Path
    private lateinit var indexDir: Path
    private lateinit var files: MutableList<Path>

    /**
     * Set the test case. This test case needs a few text files created in the current working
     * directory.
     */
    @BeforeTest
    fun setUp() {
        if (VERBOSE) {
            println("TEST: setUp")
        }
        workDir = createTempDir("TestDoc")
        indexDir = createTempDir("testIndex")

        val directory = newFSDirectory(indexDir)
        directory.close()

        files = mutableListOf()
        files.add(createOutput("test.txt", "This is the first test file"))

        files.add(createOutput("test2.txt", "This is the second test file"))
    }

    private fun createOutput(name: String, text: String): Path {
        val path = workDir.resolve(name)
        if (Files.getFileSystem().exists(path)) {
            Files.delete(path)
        }

        OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8).use { fw ->
            PrintWriter(fw).use { pw ->
                pw.println(text)
            }
        }

        return path
    }

    /**
     * This test executes a number of merges and compares the contents of the segments created when
     * using compound file or not using one.
     *
     * <p>TODO: the original test used to print the segment contents to System.out for visual
     * validation. To have the same effect, a new method checkSegment(String name, ...) should be
     * created that would assert various things about the segment.
     */
    @Test
    fun testIndexAndMerge() {
        var sw = StringWriter()
        var out = PrintWriter(sw)

        var directory = newFSDirectory(indexDir)

        if (directory is MockDirectoryWrapper) {
            // We create unreferenced files (we don't even write
            // a segments file):
            directory.setAssertNoUnrefencedFilesOnClose(false)
        }

        var writer =
            IndexWriter(
                directory,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setMaxBufferedDocs(-1)
                    .setMergePolicy(newLogMergePolicy(10)),
            )

        var si1 = indexDoc(writer, "test.txt")
        printSegment(out, si1)

        var si2 = indexDoc(writer, "test2.txt")
        printSegment(out, si2)
        writer.close()

        var siMerge = merge(directory, si1, si2, "_merge", false)
        printSegment(out, siMerge)

        var siMerge2 = merge(directory, si1, si2, "_merge2", false)
        printSegment(out, siMerge2)

        var siMerge3 = merge(directory, siMerge, siMerge2, "_merge3", false)
        printSegment(out, siMerge3)

        directory.close()
        out.close()
        sw.close()

        val multiFileOutput = sw.toString()
        // System.out.println(multiFileOutput);

        sw = StringWriter()
        out = PrintWriter(sw)

        directory = newFSDirectory(indexDir)

        if (directory is MockDirectoryWrapper) {
            // We create unreferenced files (we don't even write
            // a segments file):
            directory.setAssertNoUnrefencedFilesOnClose(false)
        }

        writer =
            IndexWriter(
                directory,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setMaxBufferedDocs(-1)
                    .setMergePolicy(newLogMergePolicy(10)),
            )

        si1 = indexDoc(writer, "test.txt")
        printSegment(out, si1)

        si2 = indexDoc(writer, "test2.txt")
        printSegment(out, si2)
        writer.close()

        siMerge = merge(directory, si1, si2, "_merge", true)
        printSegment(out, siMerge)

        siMerge2 = merge(directory, si1, si2, "_merge2", true)
        printSegment(out, siMerge2)

        siMerge3 = merge(directory, siMerge, siMerge2, "_merge3", true)
        printSegment(out, siMerge3)

        directory.close()
        out.close()
        sw.close()
        val singleFileOutput = sw.toString()

        assertEquals(multiFileOutput, singleFileOutput)
    }

    private fun indexDoc(writer: IndexWriter, fileName: String): SegmentCommitInfo {
        val path = workDir.resolve(fileName)
        val doc = Document()
        val text = Files.newInputStream(path).use { `is` -> `is`.readAllBytes()!!.decodeToString() }
        doc.add(TextField("contents", text, Field.Store.NO))
        writer.addDocument(doc)
        writer.commit()
        return writer.newestSegment()!!
    }

    private fun merge(
        dir: Directory,
        si1: SegmentCommitInfo,
        si2: SegmentCommitInfo,
        merged: String,
        useCompoundFile: Boolean,
    ): SegmentCommitInfo {
        val context = newIOContext(random(), IOContext(MergeInfo(-1, -1, false, -1)))
        val r1 = SegmentReader(si1, Version.LATEST.major, context)
        val r2 = SegmentReader(si2, Version.LATEST.major, context)

        val codec = Codec.default
        val trackingDir = TrackingDirectoryWrapper(si1.info.dir)
        val si =
            SegmentInfo(
                si1.info.dir,
                Version.LATEST,
                null,
                merged,
                -1,
                false,
                false,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                mutableMapOf(),
                null,
            )

        val merger =
            SegmentMerger(
                mutableListOf(r1, r2),
                si,
                InfoStream.default,
                trackingDir,
                FieldInfos.FieldNumbers(null, null),
                context,
                SameThreadExecutorService(),
            )

        merger.merge()
        r1.close()
        r2.close()
        si.setFiles(trackingDir.createdFiles)

        if (useCompoundFile) {
            val filesToDelete = si.files()
            codec.compoundFormat().write(dir, si, context)
            si.useCompoundFile = true
            for (name in filesToDelete) {
                si1.info.dir.deleteFile(name)
            }
        }

        return SegmentCommitInfo(si, 0, 0, -1L, -1L, -1L, StringHelper.randomId())
    }

    private fun printSegment(out: PrintWriter, si: SegmentCommitInfo) {
        val reader = SegmentReader(si, Version.LATEST.major, newIOContext(random()))

        val storedFields = reader.storedFields()
        for (i in 0..<reader.numDocs()) {
            out.println(storedFields.document(i).toString())
        }

        for (fieldInfo in reader.fieldInfos) {
            if (fieldInfo.indexOptions == IndexOptions.NONE) {
                continue
            }
            val terms = reader.terms(fieldInfo.name)
            assertNotNull(terms)
            val tis = terms.iterator()
            while (tis.next() != null) {
                out.print("  term=${fieldInfo.name}:${tis.term()}")
                out.println("    DF=${tis.docFreq()}")

                val positions = requireNotNull(tis.postings(null, PostingsEnum.POSITIONS.toInt()))

                val liveDocs: Bits? = reader.liveDocs
                while (positions.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    if (liveDocs != null && !liveDocs.get(positions.docID())) {
                        continue
                    }
                    out.print(" doc=${positions.docID()}")
                    out.print(" TF=${positions.freq()}")
                    out.print(" pos=")
                    out.print(positions.nextPosition().toString())
                    for (j in 1..<positions.freq()) {
                        out.print(",${positions.nextPosition()}")
                    }
                    out.println("")
                }
            }
        }
        reader.close()
    }
}
