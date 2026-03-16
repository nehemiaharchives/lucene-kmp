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

import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * This tests the patch for issue #LUCENE-715 (IndexWriter does not release its write lock when
 * trying to open an index which does not yet exist).
 */
class TestIndexWriterLockRelease : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testIndexWriterLockRelease() {
        val dir = newFSDirectory(createTempDir("testLockRelease"))
        try {
            IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
            )
        } catch (_: FileNotFoundException) {
            try {
                IndexWriter(
                    dir,
                    IndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
                )
            } catch (_: FileNotFoundException) {
            } catch (_: NoSuchFileException) {
            } catch (_: IndexNotFoundException) {
            }
        } catch (_: NoSuchFileException) {
            try {
                IndexWriter(
                    dir,
                    IndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
                )
            } catch (_: FileNotFoundException) {
            } catch (_: NoSuchFileException) {
            } catch (_: IndexNotFoundException) {
            }
        } catch (_: IndexNotFoundException) {
            try {
                IndexWriter(
                    dir,
                    IndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
                )
            } catch (_: FileNotFoundException) {
            } catch (_: NoSuchFileException) {
            } catch (_: IndexNotFoundException) {
            }
        } finally {
            dir.close()
        }
    }
}
