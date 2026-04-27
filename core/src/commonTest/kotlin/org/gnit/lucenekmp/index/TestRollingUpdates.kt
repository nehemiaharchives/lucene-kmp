package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.memory.DirectPostingsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestRollingUpdates : LuceneTestCase() {

    // Just updates the same set of N docs over and over, to
    // stress out deletions
    @Test
    @Throws(Exception::class)
    fun testRollingUpdates() {
        val random = Random(random().nextLong())
        val dir = newDirectory()

        val docs = LineFileDocs(random)

        if (random.nextBoolean()) {
            PostingsFormat.registerPostingsFormat("Direct") { DirectPostingsFormat() }
            Codec.default = TestUtil.alwaysPostingsFormat(DirectPostingsFormat())
        }

        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))

        val w = IndexWriter(dir, newIndexWriterConfig(analyzer))
        val SIZE = atLeast(20)
        var id = 0
        var r: DirectoryReader? = null
        var s: IndexSearcher? = null
        val numUpdates =
            (SIZE * (2 + (if (TEST_NIGHTLY) 200 * random().nextDouble() else 5 * random().nextDouble()))).toInt()
        if (VERBOSE) {
            println("TEST: numUpdates=$numUpdates")
        }
        var updateCount = 0
        // TODO: sometimes update ids not in order...
        for (docIter in 0..<numUpdates) {
            val doc = docs.nextDoc()
            val myID = id.toString()
            if (id == SIZE - 1) {
                id = 0
            } else {
                id++
            }
            if (VERBOSE) {
                println("  docIter=$docIter id=$id")
            }
            (doc.getField("docid") as Field).setStringValue(myID)

            val idTerm = Term("docid", myID)

            val doUpdate: Boolean
            if (s != null && updateCount < SIZE) {
                val searcher = s!!
                val reader = r!!
                val hits: TopDocs = searcher.search(TermQuery(idTerm), 1)
                assertEquals(1L, hits.totalHits.value)
                doUpdate = w.tryDeleteDocument(reader, hits.scoreDocs[0].doc) == -1L
                if (VERBOSE) {
                    if (doUpdate) {
                        println("  tryDeleteDocument failed")
                    } else {
                        println("  tryDeleteDocument succeeded")
                    }
                }
            } else {
                doUpdate = true
                if (VERBOSE) {
                    println("  no searcher: doUpdate=true")
                }
            }

            updateCount++

            if (doUpdate) {
                if (random().nextBoolean()) {
                    w.updateDocument(idTerm, doc)
                } else {
                    // It's OK to not be atomic for this test (no separate thread reopening readers):
                    w.deleteDocuments(TermQuery(idTerm))
                    w.addDocument(doc)
                }
            } else {
                w.addDocument(doc)
            }

            if (docIter >= SIZE && random().nextInt(50) == 17) {
                if (r != null) {
                    r.close()
                }

                val applyDeletions = random().nextBoolean()

                if (VERBOSE) {
                    println("TEST: reopen applyDeletions=$applyDeletions")
                }

                r = w.getReader(applyDeletions, false)
                if (applyDeletions) {
                    s = newSearcher(r!!)
                } else {
                    s = null
                }
                assertTrue(
                    !applyDeletions || r!!.numDocs() == SIZE,
                    "applyDeletions=$applyDeletions r.numDocs()=${r!!.numDocs()} vs SIZE=$SIZE",
                )
                updateCount = 0
            }
        }

        if (r != null) {
            r.close()
        }

        w.commit()
        assertEquals(SIZE, w.getDocStats().numDocs)

        w.close()

        TestIndexWriter.assertNoUnreferencedFiles(dir, "leftover files after rolling updates")

        docs.close()

        // LUCENE-4455:
        val infos = SegmentInfos.readLatestCommit(dir)
        var totalBytes = 0L
        for (sipc in infos) {
            totalBytes += sipc.sizeInBytes()
        }
        var totalBytes2 = 0L

        for (fileName in dir.listAll()) {
            if (IndexFileNames.CODEC_FILE_PATTERN.matches(fileName)) {
                totalBytes2 += dir.fileLength(fileName)
            }
        }
        assertEquals(totalBytes2, totalBytes)
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateSameDoc() {
        val dir = newDirectory()

        val docs = LineFileDocs(random())
        for (r in 0..<3) {
            val w =
                IndexWriter(
                    dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
                )
            val numUpdates = atLeast(20)
            val numThreads = TestUtil.nextInt(random(), 2, 6)
            val threads = arrayOfNulls<IndexingThread>(numThreads)
            for (i in 0..<numThreads) {
                threads[i] = IndexingThread(docs, w, numUpdates)
                threads[i]!!.start()
            }

            for (i in 0..<numThreads) {
                threads[i]!!.join()
            }

            w.close()
        }

        val open = DirectoryReader.open(dir)
        assertEquals(1, open.numDocs())
        open.close()
        docs.close()
        dir.close()
    }

    private class IndexingThread(
        private val docs: LineFileDocs,
        private val writer: IndexWriter,
        private val num: Int,
    ) : Thread() {
        override fun run() {
            try {
                var open: DirectoryReader? = null
                for (i in 0..<num) {
                    val doc = Document() // docs.nextDoc();
                    val br = BytesRef("test")
                    doc.add(newStringField("id", br, Field.Store.NO))
                    writer.updateDocument(Term("id", br), doc)
                    if (random().nextInt(3) == 0) {
                        if (open == null) {
                            open = DirectoryReader.open(writer)
                        }
                        val reader = DirectoryReader.openIfChanged(open)
                        if (reader != null) {
                            open.close()
                            open = reader
                        }
                        assertEquals(
                            1,
                            open.numDocs(),
                            "iter: $i numDocs: ${open.numDocs()} del: ${open.numDeletedDocs()} max: ${open.maxDoc()}",
                        )
                    }
                }
                if (open != null) {
                    open.close()
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }
}
