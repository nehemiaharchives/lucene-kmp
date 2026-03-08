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
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOSupplier

/**
 * This [MergePolicy] allows to carry over soft deleted documents across merges. The policy
 * wraps the merge reader and marks documents as "live" that have a value in the soft delete field
 * and match the provided query. This allows for instance to keep documents alive based on time or
 * any other constraint in the index. The main purpose for this merge policy is to implement
 * retention policies for document modification to vanish in the index. Using this merge policy
 * allows to control when soft deletes are claimed by merges.
 *
 * @lucene.experimental
 */
class SoftDeletesRetentionMergePolicy(
    private val field: String,
    private val retentionQuerySupplier: () -> Query,
    `in`: MergePolicy
) : OneMergeWrappingMergePolicy(
    `in`,
    { toWrap ->
        object : MergePolicy.OneMerge(toWrap.segments) {
            @Throws(IOException::class)
            override fun wrapForMerge(reader: CodecReader): CodecReader {
                val wrapped = toWrap.wrapForMerge(reader)
                val liveDocs = reader.liveDocs
                if (liveDocs == null) { // no deletes - just keep going
                    return wrapped
                }
                return applyRetentionQuery(field, retentionQuerySupplier(), wrapped)
            }
        }
    }
) {
    @Throws(IOException::class)
    override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
        val reader = readerIOSupplier.get()
        /* we only need a single hit to keep it no need for soft deletes to be checked*/
        val scorer =
            getScorer(
                retentionQuerySupplier(),
                FilterCodecReader.wrapLiveDocs(
                    reader,
                    object : Bits {
                        override fun get(index: Int): Boolean = true
                        override fun length(): Int = reader.maxDoc()
                    },
                    reader.maxDoc()
                )
            )
        if (scorer != null) {
            val iterator = scorer.iterator()
            val atLeastOneHit = iterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS
            return atLeastOneHit
        }
        return super.keepFullyDeletedSegment(readerIOSupplier)
    }

    @Throws(IOException::class)
    override fun numDeletesToMerge(
        info: SegmentCommitInfo,
        delCount: Int,
        readerSupplier: IOSupplier<CodecReader>
    ): Int {
        val numDeletesToMerge = super.numDeletesToMerge(info, delCount, readerSupplier)
        if (numDeletesToMerge != 0 && info.getSoftDelCount() > 0) {
            val reader = readerSupplier.get()
            if (reader.liveDocs != null) {
                val builder = BooleanQuery.Builder()
                builder.add(FieldExistsQuery(field), BooleanClause.Occur.FILTER)
                builder.add(retentionQuerySupplier(), BooleanClause.Occur.FILTER)
                val scorer =
                    getScorer(
                        builder.build(),
                        FilterCodecReader.wrapLiveDocs(
                            reader,
                            object : Bits {
                                override fun get(index: Int): Boolean = true
                                override fun length(): Int = reader.maxDoc()
                            },
                            reader.maxDoc()
                        )
                    )
                if (scorer != null) {
                    val iterator = scorer.iterator()
                    val liveDocs = reader.liveDocs!!
                    var numDeletedDocs = reader.numDeletedDocs()
                    while (iterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        if (liveDocs.get(iterator.docID()) == false) {
                            numDeletedDocs--
                        }
                    }
                    return numDeletedDocs
                }
            }
        }
        assert(numDeletesToMerge >= 0) { "numDeletesToMerge: $numDeletesToMerge" }
        assert(numDeletesToMerge <= info.info.maxDoc()) {
            "numDeletesToMerge: $numDeletesToMerge maxDoc:${info.info.maxDoc()}"
        }
        return numDeletesToMerge
    }

    companion object {
        // pkg private for testing
        @Throws(IOException::class)
        fun applyRetentionQuery(
            softDeleteField: String,
            retentionQuery: Query,
            reader: CodecReader
        ): CodecReader {
            val liveDocs = reader.liveDocs
            if (liveDocs == null) { // no deletes - just keep going
                return reader
            }
            val wrappedReader =
                FilterCodecReader.wrapLiveDocs(
                    reader,
                    object : Bits { // only search deleted
                        override fun get(index: Int): Boolean {
                            return liveDocs.get(index) == false
                        }

                        override fun length(): Int {
                            return liveDocs.length()
                        }
                    },
                    reader.maxDoc() - reader.numDocs()
                )
            val builder = BooleanQuery.Builder()
            builder.add(FieldExistsQuery(softDeleteField), BooleanClause.Occur.FILTER)
            builder.add(retentionQuery, BooleanClause.Occur.FILTER)
            val scorer = getScorer(builder.build(), wrappedReader)
            if (scorer != null) {
                val cloneLiveDocs = FixedBitSet.copyOf(liveDocs)
                val iterator = scorer.iterator()
                var numExtraLiveDocs = 0
                while (iterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    if (cloneLiveDocs.getAndSet(iterator.docID()) == false) {
                        // if we bring one back to live we need to account for it
                        numExtraLiveDocs++
                    }
                }
                assert(reader.numDocs() + numExtraLiveDocs <= reader.maxDoc()) {
                    "numDocs: ${reader.numDocs()} numExtraLiveDocs: $numExtraLiveDocs maxDoc: ${reader.maxDoc()}"
                }
                return FilterCodecReader.wrapLiveDocs(
                    reader,
                    cloneLiveDocs,
                    reader.numDocs() + numExtraLiveDocs
                )
            } else {
                return reader
            }
        }

        @Throws(IOException::class)
        private fun getScorer(query: Query, reader: CodecReader): Scorer? {
            val s = IndexSearcher(reader)
            s.queryCache = null
            val weight: Weight = s.createWeight(s.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1.0f)
            return weight.scorer(reader.context)
        }
    }
}
