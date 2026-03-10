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

import okio.IOException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestForceMergeForever : LuceneTestCase() {

    // Just counts how many merges are done
    private class MyIndexWriter(dir: Directory, conf: IndexWriterConfig) : IndexWriter(dir, conf) {
        val mergeCount = org.gnit.lucenekmp.jdkport.AtomicInteger(0)
        private var first = false

        @Throws(IOException::class)
        override fun merge(merge: MergePolicy.OneMerge) {
            if (merge.maxNumSegments != -1 && (first || merge.segments.size == 1)) {
                first = false
                if (VERBOSE) {
                    println("TEST: maxNumSegments merge")
                }
                mergeCount.incrementAndFetch()
            }
            super.merge(merge)
        }
    }

    @Test
    fun test() {
        val d = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val iwc = newIndexWriterConfig(analyzer)
        // SMS can cause this test to run indefinitely long:
        iwc.setMergeScheduler(ConcurrentMergeScheduler())

        val w = MyIndexWriter(d, iwc)

        // Try to make an index that requires merging:
        w.config.setMaxBufferedDocs(TestUtil.nextInt(random(), 2, 11))
        val numStartDocs = atLeast(20)
        val docs = LineFileDocs(random())
        try {
            for (docIDX in 0..<numStartDocs) {
                w.addDocument(docs.nextDoc())
            }
            val mp = w.config.mergePolicy
            val mergeAtOnce = 1 + w.cloneSegmentInfos().size()
            if (mp is TieredMergePolicy) {
                mp.setSegmentsPerTier(mergeAtOnce.toDouble())
            } else if (mp is LogMergePolicy) {
                mp.mergeFactor = mergeAtOnce
            } else {
                // skip test
                w.close()
                d.close()
                return
            }

            val doStop = AtomicBoolean(false)
            w.config.setMaxBufferedDocs(2)
            var failure: Throwable? = null
            val t = PlatformTestThread {
                try {
                    while (!doStop.load()) {
                        w.updateDocument(Term("docid", "${random().nextInt(numStartDocs)}"), docs.nextDoc())
                        // Force deletes to apply
                        DirectoryReader.open(w).close()
                    }
                } catch (t: Throwable) {
                    failure = t
                }
            }
            t.start()
            w.forceMerge(1)
            doStop.store(true)
            t.join()
            assertNull(failure)
            assertTrue(w.mergeCount.load() <= 1, "merge count is ${w.mergeCount.load()}")
            w.close()
            d.close()
        } finally {
            docs.close()
        }
    }
}
