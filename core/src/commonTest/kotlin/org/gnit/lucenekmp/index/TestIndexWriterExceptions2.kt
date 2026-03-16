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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.codecs.Codec
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
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.printStackTrace
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CrankyTokenFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.MockVariableLengthPayloadFilter
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.codecs.cranky.CrankyCodec
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.Rethrow
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils

/**
 * Causes a bunch of non-aborting and aborting exceptions and checks that no index corruption is
 * ever created
 */
// @SuppressCodecs("SimpleText")
class TestIndexWriterExceptions2 : LuceneTestCase() {

    // just one thread, serial merge policy, hopefully debuggable
    @Test
    fun testBasics() {
        // disable slow things: we don't rely upon sleeps here.
        val dir: Directory = newDirectory()
        if (dir is MockDirectoryWrapper) {
            dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
            // dir.setUseSlowOpenClosers(false)
        }

        // log all exceptions we hit, in case we fail (for debugging)
        val exceptionLog = ByteArrayOutputStream()
        val exceptionStream = PrintStream(exceptionLog, true, StandardCharsets.UTF_8)
        // val exceptionStream = System.out;

        // create lots of non-aborting exceptions with a broken analyzer
        val analyzerSeed = random().nextLong()
        val analyzer: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(MockTokenizer.SIMPLE, false)
                tokenizer.enableChecks =
                    false // TODO: can we turn this on? our filter is probably too evil
                var stream: TokenStream = tokenizer
                // emit some payloads
                if (fieldName.contains("payloads")) {
                    stream = MockVariableLengthPayloadFilter(Random(analyzerSeed), stream)
                }
                stream = CrankyTokenFilter(stream, Random(analyzerSeed))
                return TokenStreamComponents(tokenizer, stream)
            }
        }

        // create lots of aborting exceptions with a broken codec
        // we don't need a random codec, as we aren't trying to find bugs in the codec here.
        val inner = if (RANDOM_MULTIPLIER > 1) Codec.default else AssertingCodec()
        val codec = CrankyCodec(inner, Random(random().nextLong()))

        var conf = newIndexWriterConfig(analyzer)
        // just for now, try to keep this test reproducible
        conf.setMergeScheduler(SerialMergeScheduler())
        conf.setCodec(codec)

        val numDocs = atLeast(100)

        var iw = IndexWriter(dir, conf)
        try {
            var allowAlreadyClosed = false
            for (i in 0..<numDocs) {
                // TODO: add crankyDocValuesFields, etc
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
                        val thingToDo = random().nextInt(4)
                        if (thingToDo == 0) {
                            iw.deleteDocuments(Term("id", i.toString()))
                        } else if (thingToDo == 1) {
                            iw.updateNumericDocValue(Term("id", i.toString()), "dv", i + 1L)
                        } else if (thingToDo == 2) {
                            iw.updateBinaryDocValue(
                                Term("id", i.toString()),
                                "dv2",
                                BytesRef((i + 1).toString())
                            )
                        }
                    } catch (_: AlreadyClosedException) {
                        // OK: writer was closed by abort; we just reopen now:
                        assertTrue(iw.isDeleterClosed())
                        assertTrue(allowAlreadyClosed)
                        allowAlreadyClosed = false
                        conf = newIndexWriterConfig(analyzer)
                        // just for now, try to keep this test reproducible
                        conf.setMergeScheduler(SerialMergeScheduler())
                        conf.setCodec(codec)
                        iw = IndexWriter(dir, conf)
                    } catch (e: Exception) {
                        if (e.message != null && e.message!!.startsWith("Fake IOException")) {
                            exceptionStream.println("\nTEST: got expected fake exc:${e.message}")
                            e.printStackTrace(exceptionStream)
                            allowAlreadyClosed = true
                        } else {
                            Rethrow.rethrow(e)
                        }
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
                    } catch (_: AlreadyClosedException) {
                        // OK: writer was closed by abort; we just reopen now:
                        assertTrue(iw.isDeleterClosed())
                        assertTrue(allowAlreadyClosed)
                        allowAlreadyClosed = false
                        conf = newIndexWriterConfig(analyzer)
                        // just for now, try to keep this test reproducible
                        conf.setMergeScheduler(SerialMergeScheduler())
                        conf.setCodec(codec)
                        iw = IndexWriter(dir, conf)
                    } catch (e: Exception) {
                        if (e.message != null && e.message!!.startsWith("Fake IOException")) {
                            exceptionStream.println("\nTEST: got expected fake exc:${e.message}")
                            e.printStackTrace(exceptionStream)
                            allowAlreadyClosed = true
                        } else {
                            Rethrow.rethrow(e)
                        }
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
                    } catch (_: AlreadyClosedException) {
                        // OK: writer was closed by abort; we just reopen now:
                        assertTrue(iw.isDeleterClosed())
                        assertTrue(allowAlreadyClosed)
                        allowAlreadyClosed = false
                        conf = newIndexWriterConfig(analyzer)
                        // just for now, try to keep this test reproducible
                        conf.setMergeScheduler(SerialMergeScheduler())
                        conf.setCodec(codec)
                        iw = IndexWriter(dir, conf)
                    } catch (e: Exception) {
                        if (e.message != null && e.message!!.startsWith("Fake IOException")) {
                            exceptionStream.println("\nTEST: got expected fake exc:${e.message}")
                            e.printStackTrace(exceptionStream)
                            allowAlreadyClosed = true
                        } else {
                            Rethrow.rethrow(e)
                        }
                    }
                }
            }

            try {
                iw.close()
            } catch (e: Exception) {
                if (e.message != null && e.message!!.startsWith("Fake IOException")) {
                    exceptionStream.println("\nTEST: got expected fake exc:${e.message}")
                    e.printStackTrace(exceptionStream)
                    try {
                        iw.rollback()
                    } catch (_: Throwable) {
                    }
                } else {
                    Rethrow.rethrow(e)
                }
            }
            dir.close()
        } catch (t: Throwable) {
            println("Unexpected exception: dumping fake-exception-log:...")
            exceptionStream.flush()
            println(exceptionLog.toString(StandardCharsets.UTF_8))
            Rethrow.rethrow(t)
        }

        if (VERBOSE) {
            println("TEST PASSED: dumping fake-exception-log:...")
            println(exceptionLog.toString(StandardCharsets.UTF_8))
        }
    }
}
