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

import okio.EOFException
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test

/** Test that the same file name, but from a different index, is detected as foreign. */
class TestSwappedIndexFiles : LuceneTestCase() {
    @Test
    fun test() {
        val dir1 = newDirectory()
        val dir2 = newDirectory()

        // Disable CFS 80% of the time so we can truncate individual files, but the other 20% of the
        // time we test truncation of .cfs/.cfe too:
        val useCFS = false

        // Use LineFileDocs so we (hopefully) get most Lucene features
        // tested, e.g. IntPoint was recently added to it:
        val docs = LineFileDocs(random())
        val doc = docs.nextDoc()
        val seed = random().nextLong()

        indexOneDoc(seed, dir1, doc, useCFS)
        indexOneDoc(seed, dir2, doc, useCFS)

        swapFiles(dir1, dir2)
        dir1.close()
        dir2.close()
    }

    @Throws(IOException::class)
    private fun indexOneDoc(seed: Long, dir: Directory, doc: Document, useCFS: Boolean) {
        val random = Random(seed)
        val conf = newIndexWriterConfig(random, MockAnalyzer(random))
        conf.setCodec(TestUtil.getDefaultCodec())

        if (useCFS == false) {
            conf.setUseCompoundFile(false)
            conf.mergePolicy.noCFSRatio = 0.0
        } else {
            conf.setUseCompoundFile(true)
            conf.mergePolicy.noCFSRatio = 1.0
        }

        val w = RandomIndexWriter(random, dir, conf)
        w.addDocument(doc)
        w.close()
    }

    @Throws(IOException::class)
    private fun swapFiles(dir1: Directory, dir2: Directory) {
        if (VERBOSE) {
            println("TEST: dir1 files: ${dir1.listAll().contentToString()}")
            println("TEST: dir2 files: ${dir2.listAll().contentToString()}")
        }
        for (name in dir1.listAll()) {
            if (name == IndexWriter.WRITE_LOCK_NAME) {
                continue
            }
            swapOneFile(dir1, dir2, name)
        }
    }

    @Throws(IOException::class)
    private fun swapOneFile(dir1: Directory, dir2: Directory, victim: String) {
        if (VERBOSE) {
            println("TEST: swap file $victim")
        }
        newDirectory().use { dirCopy ->
            dirCopy.checkIndexOnClose = false

            // Copy all files from dir1 to dirCopy, except victim which we copy from dir2:
            for (name in dir1.listAll()) {
                if (name != victim) {
                    dirCopy.copyFrom(dir1, name, name, IOContext.DEFAULT)
                } else {
                    dirCopy.copyFrom(dir2, name, name, IOContext.DEFAULT)
                }
                dirCopy.sync(mutableSetOf(name))
            }

            // NOTE: we .close so that if the test fails (truncation not detected) we don't also get all
            // these confusing errors about open files:
            expectThrowsAnyOf(
                mutableListOf(
                    CorruptIndexException::class,
                    EOFException::class,
                    IndexFormatTooOldException::class,
                ),
            ) {
                DirectoryReader.open(dirCopy).close()
            }

            // CheckIndex should also fail:
            expectThrowsAnyOf(
                mutableListOf(
                    CorruptIndexException::class,
                    EOFException::class,
                    IndexFormatTooOldException::class,
                    CheckIndex.CheckIndexException::class,
                ),
            ) {
                TestUtil.checkIndex(
                    dirCopy,
                    CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS,
                    true,
                    true,
                    null,
                )
            }
        }
    }
}
