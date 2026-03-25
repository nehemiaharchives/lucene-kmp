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

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.ThreadedIndexingAndSearchingTestCase
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs

@OptIn(ExperimentalAtomicApi::class)
@SuppressCodecs("SimpleText", "Direct")
class TestNRTThreads : ThreadedIndexingAndSearchingTestCase() {

    private var useNonNrtReaders = true

    @BeforeTest
    fun setUp() {
        useNonNrtReaders = random().nextBoolean()
    }

    @Nightly
    @Test
    fun testNRTThreads() {
        runTest("TestNRTThreads")
    }

    override fun doSearching(es: ExecutorService?, maxIterations: Int) {
        var anyOpenDelFiles = false

        var r = DirectoryReader.open(writer)
        var iterations = 0
        while (++iterations < maxIterations && !failed.load()) {
            if (random().nextBoolean()) {
                if (VERBOSE) {
                    println("TEST: now reopen r=$r")
                }
                val r2 = DirectoryReader.openIfChanged(r)
                if (r2 != null) {
                    r.close()
                    r = r2
                }
            } else {
                if (VERBOSE) {
                    println("TEST: now close reader=$r")
                }
                r.close()
                writer.commit()
                val openDeletedFiles = (dir as MockDirectoryWrapper).openDeletedFiles
                if (openDeletedFiles.isNotEmpty()) {
                    println("OBD files: $openDeletedFiles")
                }
                anyOpenDelFiles = anyOpenDelFiles || openDeletedFiles.isNotEmpty()
                if (VERBOSE) {
                    println("TEST: now open")
                }
                r = DirectoryReader.open(writer)
            }
            if (VERBOSE) {
                println("TEST: got new reader=$r")
            }

            if (r.numDocs() > 0) {
                val searcher = IndexSearcher(r, es)
                fixedSearcher = searcher
                smokeTestSearcher(searcher)
                runSearchThreads(100)
            }
        }
        r.close()

        val openDeletedFiles = (dir as MockDirectoryWrapper).openDeletedFiles
        if (openDeletedFiles.isNotEmpty()) {
            println("OBD files: $openDeletedFiles")
        }
        anyOpenDelFiles = anyOpenDelFiles || openDeletedFiles.isNotEmpty()

        assertFalse(anyOpenDelFiles, "saw non-zero open-but-deleted count")
    }

    override fun getDirectory(inp: Directory): Directory {
        assertTrue(inp is MockDirectoryWrapper)
        if (!useNonNrtReaders) {
            inp.assertNoDeleteOpenFile = true
        }
        return inp
    }

    override fun doAfterWriter(es: ExecutorService?) {
        // Force writer to do reader pooling, always, so that
        // all merged segments, even for merges before
        // doSearching is called, are warmed:
        DirectoryReader.open(writer).close()
    }

    @Volatile
    private var fixedSearcher: IndexSearcher? = null

    override fun getCurrentSearcher(): IndexSearcher {
        return checkNotNull(fixedSearcher)
    }

    override fun releaseSearcher(s: IndexSearcher) {
        if (s != fixedSearcher) {
            // Final searcher:
            s.indexReader.close()
        }
    }

    override fun getFinalSearcher(): IndexSearcher {
        val r2 =
            if (useNonNrtReaders) {
                if (random().nextBoolean()) {
                    DirectoryReader.open(writer)
                } else {
                    writer.commit()
                    DirectoryReader.open(dir)
                }
            } else {
                DirectoryReader.open(writer)
            }
        return newSearcher(r2)
    }
}
