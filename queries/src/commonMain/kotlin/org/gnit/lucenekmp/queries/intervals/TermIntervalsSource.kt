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

package org.gnit.lucenekmp.queries.intervals

import okio.IOException
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.util.BytesRef

internal class TermIntervalsSource(
    val term: BytesRef
) : IntervalsSource() {

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val terms = ctx.reader().terms(field)
        if (terms == null) {
            return null
        }
        if (terms.hasPositions() == false) {
            throw IllegalArgumentException(
                "Cannot create an IntervalIterator over field " +
                    field +
                    " because it has no indexed positions"
            )
        }
        val te = terms.iterator()
        if (te.seekExact(term) == false) {
            return null
        }
        return intervals(term, te)
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val terms = ctx.reader().terms(field)
        if (terms == null) {
            return null
        }
        if (terms.hasPositions() == false) {
            throw IllegalArgumentException(
                "Cannot create an IntervalIterator over field " +
                    field +
                    " because it has no indexed positions"
            )
        }
        val te = terms.iterator()
        if (te.seekExact(term) == false) {
            return null
        }
        return matches(te, doc, field)
    }

    override fun minExtent(): Int {
        return 1
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return setOf(this)
    }

    override fun hashCode(): Int {
        return 31 + term.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TermIntervalsSource) return false
        return term == other.term
    }

    override fun toString(): String {
        return term.utf8ToString()
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        visitor.consumeTerms(IntervalQuery(field, this), Term(field, term))
    }

    companion object {
        @Throws(IOException::class)
        fun intervals(term: BytesRef, te: TermsEnum): IntervalIterator {
            val pe = te.postings(null, PostingsEnum.POSITIONS.toInt())!!
            val cost = termPositionsCost(te)
            return object : IntervalIterator() {

                override fun docID(): Int {
                    return pe.docID()
                }

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    val doc = pe.nextDoc()
                    reset()
                    return doc
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    val doc = pe.advance(target)
                    reset()
                    return doc
                }

                override fun cost(): Long {
                    return pe.cost()
                }

                var pos = -1
                var upto: Int = 0

                override fun start(): Int {
                    return pos
                }

                override fun end(): Int {
                    return pos
                }

                override fun gaps(): Int {
                    return 0
                }

                @Throws(IOException::class)
                override fun nextInterval(): Int {
                    if (upto <= 0) {
                        pos = NO_MORE_INTERVALS
                        return pos
                    }
                    upto--
                    pos = pe.nextPosition()
                    return pos
                }

                override fun matchCost(): Float {
                    return cost
                }

                @Throws(IOException::class)
                private fun reset() {
                    if (pe.docID() == NO_MORE_DOCS) {
                        upto = -1
                        pos = NO_MORE_INTERVALS
                    } else {
                        upto = pe.freq()
                        pos = -1
                    }
                }

                override fun toString(): String {
                    return term.utf8ToString() + ":" + super.toString()
                }
            }
        }

        @Throws(IOException::class)
        fun matches(te: TermsEnum, doc: Int, field: String): IntervalMatchesIterator? {
            val query = TermQuery(Term(field, te.term()!!))
            val pe = te.postings(null, PostingsEnum.OFFSETS.toInt())!!
            if (pe.advance(doc) != doc) {
                return null
            }
            return object : IntervalMatchesIterator {

                override fun gaps(): Int {
                    return 0
                }

                override fun width(): Int {
                    return 1
                }

                var upto = pe.freq()
                var pos = -1

                @Throws(IOException::class)
                override fun next(): Boolean {
                    if (upto <= 0) {
                        pos = IntervalIterator.NO_MORE_INTERVALS
                        return false
                    }
                    upto--
                    pos = pe.nextPosition()
                    return true
                }

                override fun startPosition(): Int {
                    return pos
                }

                override fun endPosition(): Int {
                    return pos
                }

                @Throws(IOException::class)
                override fun startOffset(): Int {
                    return pe.startOffset()
                }

                @Throws(IOException::class)
                override fun endOffset(): Int {
                    return pe.endOffset()
                }

                override val subMatches: MatchesIterator?
                    get() = null

                override val query: Query
                    get() = query
            }
        }

        /**
         * A guess of the average number of simple operations for the initial seek and buffer refill per
         * document for the positions of a term. See also
         * [Lucene101PostingsReader.BlockPostingsEnum.nextPosition].
         *
         * <p>Aside: Instead of being constant this could depend among others on
         * [Lucene101PostingsFormat.BLOCK_SIZE], [TermsEnum.docFreq],
         * [TermsEnum.totalTermFreq], [DocIdSetIterator.cost] (expected number of matching docs),
         * [LeafReader.maxDoc] (total number of docs in the segment), and the seek time and block
         * size of the device storing the index.
         */
        private const val TERM_POSNS_SEEK_OPS_PER_DOC = 128

        /**
         * Number of simple operations in
         * [Lucene101PostingsReader.BlockPostingsEnum.nextPosition]
         * when no seek or buffer refill is done.
         */
        private const val TERM_OPS_PER_POS = 7

        /**
         * Returns an expected cost in simple operations of processing the occurrences of a term in a
         * document that contains the term. This is for use by [TwoPhaseIterator.matchCost]
         * implementations.
         *
         * @param termsEnum The term is the term at which this TermsEnum is positioned.
         */
        @Throws(IOException::class)
        fun termPositionsCost(termsEnum: TermsEnum): Float {
            // TODO: When intervals move to core, refactor to use the copy of this in PhraseQuery
            val docFreq = termsEnum.docFreq()
            assert(docFreq > 0)
            val totalTermFreq = termsEnum.totalTermFreq()
            val expOccurrencesInMatchingDoc = totalTermFreq / docFreq.toFloat()
            return TERM_POSNS_SEEK_OPS_PER_DOC +
                expOccurrencesInMatchingDoc * TERM_OPS_PER_POS
        }
    }
}
