package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.LongBitSet


/**
 * Rewrites MultiTermQueries into a filter, using DocValues for term enumeration.
 *
 *
 * This can be used to perform these queries against an unindexed docvalues field.
 *
 * @lucene.experimental
 */
class DocValuesRewriteMethod : MultiTermQuery.RewriteMethod() {
    override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): Query {
        return ConstantScoreQuery(MultiTermQueryDocValuesWrapper(query))
    }

    internal open class MultiTermQueryDocValuesWrapper(protected val query: MultiTermQuery) : Query() {

        override fun toString(field: String?): String {
            // query.toString should be ok for the filter, too, if the query boost is 1.0f
            return query.toString(field)
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && query == (other as MultiTermQueryDocValuesWrapper).query
        }

        override fun hashCode(): Int {
            return 31 * classHash() + query.hashCode()
        }

        val field: String
            /** Returns the field name for this query  */
            get() = query.field

        override fun visit(visitor: QueryVisitor) {
            if (visitor.acceptField(query.field)) {
                visitor.getSubVisitor(BooleanClause.Occur.FILTER, query)
            }
        }

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {

            val multiTermQueryDocValuesWrapperQuery: MultiTermQuery = query

            return object : ConstantScoreWeight(this, boost) {
                @Throws(IOException::class)
                override fun matches(context: LeafReaderContext, doc: Int): Matches {
                    val values: SortedSetDocValues =
                        DocValues.getSortedSet(context.reader(), multiTermQueryDocValuesWrapperQuery.field)
                    return MatchesUtils.forField(
                        multiTermQueryDocValuesWrapperQuery.field
                    ) {
                        DisjunctionMatchesIterator.fromTermsEnum(
                            context,
                            doc,
                            multiTermQueryDocValuesWrapperQuery,
                            multiTermQueryDocValuesWrapperQuery.field,
                            getTermsEnum(values)
                        )!!
                    }!!
                }

                /**
                 * Create a TermsEnum that provides the intersection of the query terms with the terms
                 * present in the doc values.
                 */
                @Throws(IOException::class)
                fun getTermsEnum(values: SortedSetDocValues): TermsEnum {
                    return multiTermQueryDocValuesWrapperQuery.getTermsEnum(
                        object : Terms() {
                            @Throws(IOException::class)
                            override fun iterator(): TermsEnum {
                                return values.termsEnum()
                            }

                            override val sumDocFreq: Long
                                get() {
                                    throw UnsupportedOperationException()
                                }

                            override val docCount: Int
                                get() {
                                    throw UnsupportedOperationException()
                                }

                            override fun size(): Long {
                                return -1
                            }

                            override val sumTotalTermFreq: Long
                                get() = throw UnsupportedOperationException()

                            override fun hasFreqs(): Boolean {
                                return false
                            }

                            override fun hasOffsets(): Boolean {
                                return false
                            }

                            override fun hasPositions(): Boolean {
                                return false
                            }

                            override fun hasPayloads(): Boolean {
                                return false
                            }
                        })
                }

                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    val values: SortedSetDocValues =
                        DocValues.getSortedSet(context.reader(), multiTermQueryDocValuesWrapperQuery.field)
                    if (values.valueCount == 0L) {
                        return null // no values/docs so nothing can match
                    }

                    return object : ScorerSupplier() {
                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            // Create a TermsEnum that will provide the intersection of the terms specified in the
                            // query with the values present in the doc values:
                            val termsEnum: TermsEnum = checkNotNull(getTermsEnum(values))
                            if (termsEnum.next() == null) {
                                // no matching terms
                                return ConstantScoreScorer(score(), scoreMode, DocIdSetIterator.empty())
                            }

                            // Leverage a DV skipper if one was indexed for the field:
                            val skipper: DocValuesSkipper? =
                                context.reader().getDocValuesSkipper(multiTermQueryDocValuesWrapperQuery.field)

                            // Create a bit set for the "term set" ordinals (these are the terms provided by the
                            // query that are actually present in the doc values field). Cannot use FixedBitSet
                            // because we require long index (ord):
                            val termSet: LongBitSet = LongBitSet(values.valueCount)
                            val minOrd: Long = termsEnum.ord()
                            require(minOrd >= 0)
                            var maxOrd: Long = -1
                            do {
                                val ord: Long = termsEnum.ord()
                                require(ord >= 0 && ord > maxOrd)
                                maxOrd = ord
                                termSet.set(ord)
                            } while (termsEnum.next() != null)

                            if (skipper != null && (minOrd > skipper.maxValue() || maxOrd < skipper.minValue())) {
                                return ConstantScoreScorer(score(), scoreMode, DocIdSetIterator.empty())
                            }

                            val singleton: SortedDocValues? = DocValues.unwrapSingleton(values)
                            var iterator: TwoPhaseIterator?
                            val max = maxOrd
                            if (singleton != null) {
                                iterator =
                                    object : TwoPhaseIterator(singleton) {
                                        @Throws(IOException::class)
                                        override fun matches(): Boolean {
                                            return termSet.get(singleton.ordValue().toLong())
                                        }

                                        override fun matchCost(): Float {
                                            return 3f // lookup in a bitset
                                        }
                                    }
                            } else {
                                iterator =
                                    object : TwoPhaseIterator(values) {
                                        @Throws(IOException::class)
                                        override fun matches(): Boolean {
                                            for (i in 0..<values.docValueCount()) {
                                                val value: Long = values.nextOrd()
                                                if (value > max) {
                                                    return false // values are sorted, terminate
                                                } else if (termSet.get(value)) {
                                                    return true
                                                }
                                            }
                                            return false
                                        }

                                        override fun matchCost(): Float {
                                            return 3f // lookup in a bitset
                                        }
                                    }
                            }

                            if (skipper != null) {
                                iterator = DocValuesRangeIterator(iterator, skipper, minOrd, maxOrd, true)
                            }
                            return ConstantScoreScorer(score(), scoreMode, iterator!!)
                        }

                        override fun cost(): Long {
                            // We have no prior knowledge of how many docs might match for any given query term,
                            // so we assume that all docs with a value could be a match:
                            return values.cost()
                        }
                    }
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return DocValues.isCacheable(ctx, multiTermQueryDocValuesWrapperQuery.field)
                }

            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return other != null && this::class == other::class
    }

    override fun hashCode(): Int {
        return 641
    }
}
