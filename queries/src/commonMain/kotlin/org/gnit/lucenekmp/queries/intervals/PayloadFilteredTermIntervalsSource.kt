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
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.util.BytesRef

internal class PayloadFilteredTermIntervalsSource(
    val term: BytesRef,
    val filter: (BytesRef?) -> Boolean
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
        if (terms.hasPayloads() == false) {
            throw IllegalArgumentException(
                "Cannot create a payload-filtered iterator over field " +
                    field +
                    " because it has no indexed payloads"
            )
        }
        val te = terms.iterator()
        if (te.seekExact(term) == false) {
            return null
        }
        return intervals(te)
    }

    @Throws(IOException::class)
    private fun intervals(te: TermsEnum): IntervalIterator {
        val pe = te.postings(null, PostingsEnum.PAYLOADS.toInt())!!
        val cost = TermIntervalsSource.termPositionsCost(te)
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
                do {
                    if (upto <= 0) {
                        pos = NO_MORE_INTERVALS
                        return pos
                    }
                    upto--
                    pos = pe.nextPosition()
                } while (filter(pe.payload) == false)
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
        if (terms.hasPayloads() == false) {
            throw IllegalArgumentException(
                "Cannot create a payload-filtered iterator over field " +
                    field +
                    " because it has no indexed payloads"
            )
        }
        val te = terms.iterator()
        if (te.seekExact(term) == false) {
            return null
        }
        return matches(te, doc)
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        visitor.consumeTerms(IntervalQuery(field, this), Term(field, term))
    }

    @Throws(IOException::class)
    private fun matches(te: TermsEnum, doc: Int): IntervalMatchesIterator? {
        val pe = te.postings(null, PostingsEnum.ALL.toInt())!!
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
                do {
                    if (upto <= 0) {
                        pos = IntervalIterator.NO_MORE_INTERVALS
                        return false
                    }
                    upto--
                    pos = pe.nextPosition()
                } while (filter(pe.payload) == false)
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
                get() = throw UnsupportedOperationException()
        }
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
        if (other !is PayloadFilteredTermIntervalsSource) return false
        return term == other.term
    }

    override fun toString(): String {
        return "PAYLOAD_FILTERED(" + term.utf8ToString() + ")"
    }
}
