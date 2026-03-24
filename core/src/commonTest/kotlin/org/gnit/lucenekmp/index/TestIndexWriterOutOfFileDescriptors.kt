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

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.PrintStreamInfoStream
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestIndexWriterOutOfFileDescriptors : LuceneTestCase() {
    @Test
    fun test() {
        val dir = newMockFSDirectory(createTempDir("TestIndexWriterOutOfFileDescriptors"))
        val rate = random().nextDouble() * 0.01
        // System.out.println("rate=" + rate);
        dir.randomIOExceptionRateOnOpen = rate
        val iters = atLeast(20)
        val docs = LineFileDocs(random())
        var r: DirectoryReader? = null
        var r2: DirectoryReader? = null
        var any = false
        var dirCopy: MockDirectoryWrapper? = null
        var lastNumDocs = 0
        for (iter in 0 until iters) {
            var w: IndexWriter? = null
            if (VERBOSE) {
                println("TEST: iter=$iter")
            }
            try {
                val analyzer = MockAnalyzer(random())
                analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
                val iwc = newIndexWriterConfig(analyzer)

                if (VERBOSE) {
                    // Do this ourselves instead of relying on LTC so
                    // we see incrementing messageID:
                    iwc.setInfoStream(
                        PrintStreamInfoStream(
                            PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
                        )
                    )
                }
                val ms = iwc.mergeScheduler
                if (ms is ConcurrentMergeScheduler) {
                    ms.setSuppressExceptions()
                }
                w = IndexWriter(dir, iwc)
                if (r != null && random().nextInt(5) == 3) {
                    if (random().nextBoolean()) {
                        if (VERBOSE) {
                            println("TEST: addIndexes LR[]")
                        }
                        TestUtil.addIndexesSlowly(w, r)
                    } else {
                        if (VERBOSE) {
                            println("TEST: addIndexes Directory[]")
                        }
                        w.addIndexes(dirCopy!!)
                    }
                } else {
                    if (VERBOSE) {
                        println("TEST: addDocument")
                    }
                    w.addDocument(docs.nextDoc())
                }
                dir.randomIOExceptionRateOnOpen = 0.0
                if (ms is ConcurrentMergeScheduler) {
                    runBlocking { ms.sync() }
                }
                // If exc hit CMS then writer will be tragically closed:
                if (w.getTragicException() == null) {
                    w.close()
                }
                w = null

                // NOTE: This is O(N^2)!  Only enable for temporary debugging:
                // dir.setRandomIOExceptionRateOnOpen(0.0);
                // _TestUtil.checkIndex(dir);
                // dir.setRandomIOExceptionRateOnOpen(rate);

                // Verify numDocs only increases, to catch IndexWriter
                // accidentally deleting the index:
                dir.randomIOExceptionRateOnOpen = 0.0
                assertTrue(DirectoryReader.indexExists(dir))
                if (r2 == null) {
                    r2 = DirectoryReader.open(dir)
                } else {
                    val r3 = DirectoryReader.openIfChanged(r2)
                    if (r3 != null) {
                        r2.close()
                        r2 = r3
                    }
                }
                assertNotNull(r2)
                assertTrue("before=$lastNumDocs after=${r2.numDocs()}") { r2.numDocs() >= lastNumDocs }
                lastNumDocs = r2.numDocs()
                // System.out.println("numDocs=" + lastNumDocs);
                dir.randomIOExceptionRateOnOpen = rate

                any = true
                if (VERBOSE) {
                    println("TEST: iter=$iter: success")
                }
            } catch (ioe: Throwable) {
                if (ioe is AssertionError || ioe is IOException || ioe is IllegalStateException) {
                    if (VERBOSE) {
                        println("TEST: iter=$iter: exception")
                        ioe.printStackTrace()
                    }
                    if (w != null) {
                        // NOTE: leave random IO exceptions enabled here,
                        // to verify that rollback does not try to write
                        // anything:
                        w.rollback()
                    }
                } else {
                    throw ioe
                }
            }

            if (any && r == null && random().nextBoolean()) {
                // Make a copy of a non-empty index so we can use
                // it to addIndexes later:
                dir.randomIOExceptionRateOnOpen = 0.0
                r = DirectoryReader.open(dir)
                dirCopy =
                    newMockFSDirectory(
                        createTempDir("TestIndexWriterOutOfFileDescriptors.copy")
                    )
                val files = mutableSetOf<String>()
                for (file in dir.listAll()) {
                    if (
                        file.startsWith(IndexFileNames.SEGMENTS)
                        || IndexFileNames.CODEC_FILE_PATTERN.matches(file)
                    ) {
                        dirCopy.copyFrom(dir, file, file, IOContext.DEFAULT)
                        files.add(file)
                    }
                }
                dirCopy.sync(files)
                // Have IW kiss the dir so we remove any leftover
                // files ... we can easily have leftover files at
                // the time we take a copy because we are holding
                // open a reader:
                IndexWriter(dirCopy, newIndexWriterConfig(MockAnalyzer(random()))).close()
                dirCopy.randomIOExceptionRate = rate
                dir.randomIOExceptionRateOnOpen = rate
            }
        }

        if (r2 != null) {
            r2.close()
        }
        if (r != null) {
            r.close()
            dirCopy!!.close()
        }
        docs.close()
        dir.close()
    }
}
