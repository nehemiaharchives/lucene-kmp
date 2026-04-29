package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.English.intToEnglish
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestThreadedForceMerge : LuceneTestCase() {

    private lateinit var ANALYZER: Analyzer

    companion object {
        private const val NUM_THREADS: Int = 3

        // private final static int NUM_THREADS = 5;
        private const val NUM_ITER: Int = 1

        private const val NUM_ITER2: Int = 1
    }

    @Volatile
    private var failed = false

    @BeforeTest
    fun setup() {
        ANALYZER = MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
    }

    private fun setFailed() {
        failed = true
    }

    @Throws(Exception::class)
    fun runTest(random: Random?, directory: Directory) {
        var writer =
            IndexWriter(
                directory,
                newIndexWriterConfig(ANALYZER)
                    .setOpenMode(OpenMode.CREATE)
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy())
            )

        for (iter in 0..<NUM_ITER) {
            val iterFinal = iter

            (writer.config.mergePolicy as LogMergePolicy).mergeFactor - 1000

            val customType = FieldType(StringField.TYPE_STORED)
            customType.setOmitNorms(true)

            for (i in 0..199) {
                val d = Document()
                d.add(newField("id", i.toString(), customType))
                d.add(newField("contents", intToEnglish(i), customType))
                writer.addDocument(d)
            }

            (writer.config.mergePolicy as LogMergePolicy).mergeFactor - 4

            val threads: Array<Thread?> = arrayOfNulls(NUM_THREADS)

            for (i in 0..<NUM_THREADS) {
                val iFinal = i
                val writerFinal: IndexWriter = writer
                threads[i] =
                    object : Thread() {
                        override fun run() {
                            try {
                                for (j in 0..<NUM_ITER2) {
                                    writerFinal.forceMerge(1, false)
                                    for (k in 0..<17 * (1 + iFinal)) {
                                        val d = Document()
                                        d.add(
                                            newField("id", iterFinal.toString() + "_" + iFinal + "_" + j + "_" + k, customType)
                                        )
                                        d.add(newField("contents", intToEnglish(iFinal + k), customType))
                                        writerFinal.addDocument(d)
                                    }
                                    for (k in 0..<9 * (1 + iFinal)) writerFinal.deleteDocuments(
                                        Term("id", iterFinal.toString() + "_" + iFinal + "_" + j + "_" + k)
                                    )
                                    writerFinal.forceMerge(1)
                                }
                            } catch (t: Throwable) {
                                setFailed()
                                println(currentThread().getName() + ": hit exception")
                                t.printStackTrace()
                            }
                        }
                    }
            }

            for (i in 0..<NUM_THREADS) threads[i]!!.start()

            for (i in 0..<NUM_THREADS) threads[i]!!.join()

            assertTrue(!failed)

            val expectedDocCount = ((1 + iter) * (200 + 8 * NUM_ITER2 * (NUM_THREADS / 2.0) * (1 + NUM_THREADS))).toInt()

            assertEquals(
                expectedDocCount,
                writer.getDocStats().numDocs,
                "index=" + writer.segString() + " numDocs=" + writer.getDocStats().numDocs + " maxDoc=" + writer.getDocStats().maxDoc + " config=" + writer.config
            )
            assertEquals(
                expectedDocCount,
                writer.getDocStats().maxDoc,
                "index=" + writer.segString() + " numDocs=" + writer.getDocStats().numDocs + " maxDoc=" + writer.getDocStats().maxDoc + " config=" + writer.config
            )

            writer.close()
            writer =
                IndexWriter(
                    directory,
                    newIndexWriterConfig(ANALYZER).setOpenMode(OpenMode.APPEND).setMaxBufferedDocs(2)
                )

            val reader: DirectoryReader = DirectoryReader.open(directory)
            assertEquals(1, reader.leaves().size, "reader=$reader")
            assertEquals(expectedDocCount, reader.numDocs())
            reader.close()
        }
        writer.close()
    }

    /* */
    @Test
    @Throws(Exception::class)
    fun testThreadedForceMerge() {
        val directory: Directory = newDirectory()
        runTest(random(), directory)
        directory.close()
    }
}
