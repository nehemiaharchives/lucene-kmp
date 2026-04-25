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
package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.TotalHits.Relation

/** A [Rescorer] that re-sorts according to a provided Sort. */
class SortRescorer(
    private val sort: Sort
) : Rescorer() {

    @Throws(IOException::class)
    override fun rescore(searcher: IndexSearcher, firstPassTopDocs: TopDocs, topN: Int): TopDocs {

        // Copy ScoreDoc[] and sort by ascending docID:
        val hits = firstPassTopDocs.scoreDocs.copyOf()
        val docIdComparator = compareBy<ScoreDoc> { it.doc }
        hits.sortWith(docIdComparator)

        val leaves: MutableList<LeafReaderContext> = searcher.indexReader.leaves()

        val collector = TopFieldCollectorManager(sort, topN, null, Int.MAX_VALUE).newCollector()

        // Now merge sort docIDs from hits, with reader's leaves:
        var hitUpto = 0
        var readerUpto = -1
        var endDoc = 0
        var docBase = 0

        var leafCollector: LeafCollector? = null
        val score = Score()

        while (hitUpto < hits.size) {
            val hit = hits[hitUpto]
            val docID = hit.doc
            var readerContext: LeafReaderContext? = null
            while (docID >= endDoc) {
                readerUpto++
                readerContext = leaves[readerUpto]
                endDoc = readerContext.docBase + readerContext.reader().maxDoc()
            }

            if (readerContext != null) {
                // We advanced to another segment:
                leafCollector = collector.getLeafCollector(readerContext)
                leafCollector.scorer = score
                docBase = readerContext.docBase
            }

            score.score = hit.score

            leafCollector!!.collect(docID - docBase)

            hitUpto++
        }

        val rescoredDocs = collector.topDocs()
        // set scores from the original score docs
        assert(hits.size == rescoredDocs.scoreDocs.size)
        val rescoredDocsClone = rescoredDocs.scoreDocs.copyOf()
        rescoredDocsClone.sortWith(docIdComparator)
        for (i in rescoredDocsClone.indices) {
            rescoredDocsClone[i].score = hits[i].score
        }
        return rescoredDocs
    }

    @Throws(IOException::class)
    override fun explain(searcher: IndexSearcher, firstPassExplanation: Explanation, docID: Int): Explanation {
        val oneHit =
            TopDocs(
                TotalHits(1, Relation.EQUAL_TO),
                arrayOf(ScoreDoc(docID, firstPassExplanation.value.toFloat())),
            )
        val hits = rescore(searcher, oneHit, 1)
        assert(hits.totalHits.value == 1L)

        val subs = ArrayList<Explanation>()

        // Add first pass:
        val first =
            Explanation.match(
                firstPassExplanation.value,
                "first pass score",
                firstPassExplanation,
            )
        subs.add(first)

        val fieldDoc = hits.scoreDocs[0] as FieldDoc

        // Add sort values:
        val sortFields = sort.sort
        for (i in sortFields.indices) {
            subs.add(
                Explanation.match(
                    0.0f,
                    "sort field ${sortFields[i]} value=${fieldDoc.fields!![i]}",
                )
            )
        }

        // TODO: if we could ask the Sort to explain itself then
        // we wouldn't need the separate ExpressionRescorer...
        return Explanation.match(0.0f, "sort field values for sort=$sort", subs)
    }
}
