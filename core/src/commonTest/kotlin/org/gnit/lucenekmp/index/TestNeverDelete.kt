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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertTrue

// Make sure if you use NoDeletionPolicy that no file
// referenced by a commit point is ever deleted

class TestNeverDelete : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testIndexing() {
        val tmpDir = createTempDir("TestNeverDelete")
        val d: BaseDirectoryWrapper = newFSDirectory(tmpDir)

        val w =
            RandomIndexWriter(
                random(),
                d,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE),
            )
        w.w.config.setMaxBufferedDocs(TestUtil.nextInt(random(), 5, 30))

        w.commit()
        val indexThreads = arrayOfNulls<Thread>(random().nextInt(4))
        val stopIterations = atLeast(100)
        for (x in indexThreads.indices) {
            indexThreads[x] =
                object : Thread() {
                    override fun run() {
                        try {
                            var docCount = 0
                            while (docCount < stopIterations) {
                                val doc = Document()
                                doc.add(newStringField("dc", "$docCount", Field.Store.YES))
                                doc.add(newTextField("field", "here is some text", Field.Store.YES))
                                w.addDocument(doc)

                                if (docCount % 13 == 0) {
                                    w.commit()
                                }
                                docCount++
                            }
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    }
                }
            indexThreads[x]!!.setName("Thread $x")
            indexThreads[x]!!.start()
        }

        val allFiles: MutableSet<String> = HashSet()

        var r = DirectoryReader.open(d)
        var iterations = 0
        while (++iterations < stopIterations) {
            val ic = r.indexCommit
            if (VERBOSE) {
                println("TEST: check files: ${ic.fileNames}")
            }
            allFiles.addAll(ic.fileNames)
            // Make sure no old files were removed
            for (fileName in allFiles) {
                assertTrue(slowFileExists(d, fileName), "file $fileName does not exist")
            }
            val r2 = DirectoryReader.openIfChanged(r)
            if (r2 != null) {
                r.close()
                r = r2
            }
            Thread.sleep(1)
        }
        r.close()

        for (t in indexThreads) {
            t!!.join()
        }
        w.close()
        d.close()
    }
}
