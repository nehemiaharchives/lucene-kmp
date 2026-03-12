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
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalAtomicApi::class)
class TestIndexManyDocuments : LuceneTestCase() {

    @Test
    fun test() {
        val dir = newFSDirectory(createTempDir())
        val iwc = IndexWriterConfig()
        iwc.setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 2000))

        val numDocs = atLeast(10000)

        val w = IndexWriter(dir, iwc)
        val count = AtomicInteger(0)
        val threads = Array(2) {
            Thread {
                while (count.fetchAndIncrement() < numDocs) {
                    val doc = Document()
                    doc.add(newTextField("field", "text", Field.Store.NO))
                    w.addDocument(doc)
                }
            }
        }
        for (thread in threads) {
            thread.start()
        }

        for (thread in threads) {
            thread.join()
        }

        assertEquals(
            numDocs,
            w.getDocStats().maxDoc,
            "lost ${numDocs - w.getDocStats().maxDoc} documents; maxBufferedDocs=${iwc.maxBufferedDocs}"
        )
        w.close()

        val r = DirectoryReader.open(dir)
        assertEquals(numDocs, r.maxDoc())
        IOUtils.close(r, dir)
    }
}
