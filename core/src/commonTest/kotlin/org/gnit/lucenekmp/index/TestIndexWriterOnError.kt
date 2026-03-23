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

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.IOError
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.LinkageError
import org.gnit.lucenekmp.jdkport.OutOfMemoryError
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.printStackTrace
import org.gnit.lucenekmp.jdkport.UnknownError
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.MockVariableLengthPayloadFilter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import org.gnit.lucenekmp.tests.util.Rethrow
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.random.Random
import kotlin.test.Test

/**
 * Causes a bunch of fake VM errors and checks that no other exceptions are delivered instead, no
 * index corruption is ever created.
 */
@SuppressCodecs("SimpleText")
class TestIndexWriterOnError : LuceneTestCase() {
    // just one thread, serial merge policy, hopefully debuggable
    private fun doTest(failOn: MockDirectoryWrapper.Failure) {
        // log all exceptions we hit, in case we fail (for debugging)
        val exceptionLog = ByteArrayOutputStream()
        val exceptionStream = PrintStream(exceptionLog, true, StandardCharsets.UTF_8)
        // PrintStream exceptionStream = System.out;

        val analyzerSeed = random().nextLong()
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    tokenizer.enableChecks = false // we are going to make it angry
                    var stream: TokenStream = tokenizer
                    // emit some payloads
                    if (fieldName.contains("payloads")) {
                        stream = MockVariableLengthPayloadFilter(Random(analyzerSeed), stream)
                    }
                    return TokenStreamComponents(tokenizer, stream)
                }
            }

        var dir: MockDirectoryWrapper? = null

        val numIterations = if (TEST_NIGHTLY) atLeast(100) else atLeast(5)

        STARTOVER@ for (iter in 0 until numIterations) {
            try {
                // close from last run
                if (dir != null) {
                    dir.close()
                }
                // disable slow things: we don't rely upon sleeps here.
                dir = newMockDirectory()
                dir.useSlowOpenClosers = false
                dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)

                val conf = newIndexWriterConfig(analyzer)
                // just for now, try to keep this test reproducible
                conf.setMergeScheduler(SerialMergeScheduler())

                // test never makes it this far...
                val numDocs = atLeast(2000)

                val iw = IndexWriter(dir, conf)
                iw.commit() // ensure there is always a commit

                dir.failOn(failOn)

                for (i in 0 until numDocs) {
                    val doc = Document()
                    doc.add(newStringField("id", i.toString(), Field.Store.NO))
                    doc.add(NumericDocValuesField("dv", i.toLong()))
                    doc.add(BinaryDocValuesField("dv2", BytesRef(i.toString())))
                    doc.add(SortedDocValuesField("dv3", BytesRef(i.toString())))
                    doc.add(SortedSetDocValuesField("dv4", BytesRef(i.toString())))
                    doc.add(SortedSetDocValuesField("dv4", BytesRef((i - 1).toString())))
                    doc.add(SortedNumericDocValuesField("dv5", i.toLong()))
                    doc.add(SortedNumericDocValuesField("dv5", (i - 1).toLong()))
                    doc.add(
                        newTextField(
                            "text1",
                            TestUtil.randomAnalysisString(random(), 20, true),
                            Field.Store.NO
                        )
                    )
                    // ensure we store something
                    doc.add(StoredField("stored1", "foo"))
                    doc.add(StoredField("stored1", "bar"))
                    // ensure we get some payloads
                    doc.add(
                        newTextField(
                            "text_payloads",
                            TestUtil.randomAnalysisString(random(), 6, true),
                            Field.Store.NO
                        )
                    )
                    // ensure we get some vectors
                    val ft = FieldType(TextField.TYPE_NOT_STORED)
                    ft.setStoreTermVectors(true)
                    doc.add(newField("text_vectors", TestUtil.randomAnalysisString(random(), 6, true), ft))
                    doc.add(IntPoint("point", random().nextInt()))
                    doc.add(IntPoint("point2d", random().nextInt(), random().nextInt()))

                    if (random().nextInt(10) > 0) {
                        // single doc
                        try {
                            iw.addDocument(doc)
                            // we made it, sometimes delete our doc, or update a dv
                            when (random().nextInt(4)) {
                                0 -> iw.deleteDocuments(Term("id", i.toString()))
                                1 -> iw.updateNumericDocValue(Term("id", i.toString()), "dv", i + 1L)
                                2 -> iw.updateBinaryDocValue(
                                    Term("id", i.toString()),
                                    "dv2",
                                    BytesRef((i + 1).toString())
                                )
                            }
                        } catch (disaster: Throwable) {
                            if (disaster is Error || disaster is AlreadyClosedException) {
                                getTragedy(disaster, iw, exceptionStream)
                                continue@STARTOVER
                            }
                            throw disaster
                        }
                    } else {
                        // block docs
                        val doc2 = Document()
                        doc2.add(newStringField("id", (-i).toString(), Field.Store.NO))
                        doc2.add(
                            newTextField(
                                "text1",
                                TestUtil.randomAnalysisString(random(), 20, true),
                                Field.Store.NO
                            )
                        )
                        doc2.add(StoredField("stored1", "foo"))
                        doc2.add(StoredField("stored1", "bar"))
                        doc2.add(newField("text_vectors", TestUtil.randomAnalysisString(random(), 6, true), ft))

                        try {
                            iw.addDocuments(listOf(doc, doc2))
                            // we made it, sometimes delete our docs
                            if (random().nextBoolean()) {
                                iw.deleteDocuments(
                                    Term("id", i.toString()),
                                    Term("id", (-i).toString())
                                )
                            }
                        } catch (disaster: Throwable) {
                            if (disaster is Error || disaster is AlreadyClosedException) {
                                getTragedy(disaster, iw, exceptionStream)
                                continue@STARTOVER
                            }
                            throw disaster
                        }
                    }

                    if (random().nextInt(10) == 0) {
                        // trigger flush:
                        try {
                            if (random().nextBoolean()) {
                                var ir: DirectoryReader? = null
                                try {
                                    ir = DirectoryReader.open(iw, random().nextBoolean(), false)
                                    TestUtil.checkReader(ir)
                                } finally {
                                    IOUtils.closeWhileHandlingException(ir)
                                }
                            } else {
                                iw.commit()
                            }
                            if (DirectoryReader.indexExists(dir)) {
                                TestUtil.checkIndex(dir)
                            }
                        } catch (disaster: Throwable) {
                            if (disaster is Error || disaster is AlreadyClosedException) {
                                getTragedy(disaster, iw, exceptionStream)
                                continue@STARTOVER
                            }
                            throw disaster
                        }
                    }
                }

                try {
                    iw.close()
                } catch (disaster: Throwable) {
                    if (disaster is Error || disaster is AlreadyClosedException) {
                        getTragedy(disaster, iw, exceptionStream)
                        continue@STARTOVER
                    }
                    throw disaster
                }
            } catch (t: Throwable) {
                println("Unexpected exception: dumping fake-exception-log:...")
                exceptionStream.flush()
                println(exceptionLog.toString(StandardCharsets.UTF_8))
                Rethrow.rethrow(t)
            }
        }
        dir?.close()
        if (VERBOSE) {
            println("TEST PASSED: dumping fake-exception-log:...")
            println(exceptionLog.toString(StandardCharsets.UTF_8))
        }
    }

    private fun getTragedy(disaster: Throwable, writer: IndexWriter, log: PrintStream): Error {
        var e: Throwable = disaster
        if (e is AlreadyClosedException) {
            e = e.cause ?: e
        }

        if (e is Error && e.message != null && e.message!!.contains("Fake")) {
            log.println("\nTEST: got expected fake exc:${e.message}")
            e.printStackTrace(log)
            // TODO: remove rollback here, and add this assert to ensure "full OOM protection" anywhere IW
            // does writes
            // assertTrue("hit OOM but writer is still open, WTF: ", writer.isClosed());
            try {
                writer.rollback()
            } catch (t: Throwable) {
                t.printStackTrace(log)
            }
            return e
        } else {
            Rethrow.rethrow(disaster)
            error("unreachable")
        }
    }

    @Test
    fun testOOM() {
        val r = Random(random().nextLong())
        doTest(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (r.nextInt(3000) == 0) {
                        if (callStackContains(IndexWriter::class)) {
                            throw OutOfMemoryError("Fake OutOfMemoryError")
                        }
                    }
                }
            }
        )
    }

    @Test
    fun testUnknownError() {
        val r = Random(random().nextLong())
        doTest(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (r.nextInt(3000) == 0) {
                        if (callStackContains(IndexWriter::class)) {
                            throw UnknownError("Fake UnknownError")
                        }
                    }
                }
            }
        )
    }

    @Test
    fun testLinkageError() {
        val r = Random(random().nextLong())
        doTest(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (r.nextInt(3000) == 0) {
                        if (callStackContains(IndexWriter::class)) {
                            throw LinkageError("Fake LinkageError")
                        }
                    }
                }
            }
        )
    }

    @Test
    fun testIOError() {
        val r = Random(random().nextLong())
        doTest(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (r.nextInt(3000) == 0) {
                        if (callStackContains(IndexWriter::class)) {
                            throw IOError(RuntimeException("Fake IOError"))
                        }
                    }
                }
            }
        )
    }

    @Nightly
    @Test
    fun testCheckpoint() {
        val r = Random(random().nextLong())
        doTest(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (r.nextInt(4) == 0) {
                        if (callStackContains(IndexFileDeleter::class, "checkpoint")) {
                            throw OutOfMemoryError("Fake OutOfMemoryError")
                        }
                    }
                }
            }
        )
    }
}
