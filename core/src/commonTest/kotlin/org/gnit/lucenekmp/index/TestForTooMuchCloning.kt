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
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertTrue

class TestForTooMuchCloning : LuceneTestCase() {

    // Make sure we don't clone IndexInputs too frequently
    // during merging and searching:
    @Test
    fun test() {
        val dir: MockDirectoryWrapper = newMockDirectory()
        val tmp = TieredMergePolicy()
        tmp.setSegmentsPerTier(2.0)
        val w = RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(random(), MockAnalyzer(random()))
                // to reduce flakiness on merge clone count
                .setMergeScheduler(SerialMergeScheduler())
                .setMaxBufferedDocs(2)
                // use a FilterMP otherwise RIW will randomly reconfigure
                // the MP while the test runs
                .setMergePolicy(FilterMergePolicy(tmp))
        )
        val numDocs = 20
        for (docs in 0..<numDocs) {
            val sb = StringBuilder()
            for (terms in 0..<100) {
                sb.append(TestUtil.randomRealisticUnicodeString(random()))
                sb.append(' ')
            }
            val doc = Document()
            doc.add(TextField("field", sb.toString(), Field.Store.NO))
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()
        // System.out.println("merge clone count=" + cloneCount);
        assertTrue(
            dir.getInputCloneCount() < 600,
            "too many calls to IndexInput.clone during merging: ${dir.getInputCloneCount()}"
        )

        val s: IndexSearcher = newSearcher(r)
        // important: set this after newSearcher, it might have run checkindex
        val cloneCount = dir.getInputCloneCount()
        // dir.setVerboseClone(true);

        // MTQ that matches all terms so the AUTO_REWRITE should
        // cutover to filter rewrite and reuse a single DocsEnum
        // across all terms;
        val hits: TopDocs = s.search(
            TermRangeQuery("field", BytesRef(), BytesRef("\uFFFF"), true, true),
            10
        )
        assertTrue(hits.totalHits.value > 0)
        val queryCloneCount = dir.getInputCloneCount() - cloneCount
        // System.out.println("query clone count=" + queryCloneCount);
        // It is rather difficult to reliably predict how many query clone calls will be performed. One
        // important factor is the number of segment partitions being searched, but it depends as well
        // on the terms being indexed, and the distribution of the matches across the documents, which
        // affects how the query gets rewritten and the subsequent number of clone calls it will
        // perform.
        assertTrue(
            queryCloneCount <= maxOf(s.leafContexts.size, s.slices.size) * 5,
            "too many calls to IndexInput.clone during TermRangeQuery: $queryCloneCount"
        )
        r.close()
        dir.close()
    }
}
