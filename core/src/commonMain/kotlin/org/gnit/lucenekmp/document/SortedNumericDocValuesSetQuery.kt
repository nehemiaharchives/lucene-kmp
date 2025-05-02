package org.gnit.lucenekmp.document

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.search.ConstantScoreScorer
import org.gnit.lucenekmp.search.ConstantScoreWeight
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.RamUsageEstimator

/** Similar to SortedNumericDocValuesRangeQuery but for a set  */
internal class SortedNumericDocValuesSetQuery(private val field: String, numbers: LongArray) : Query(), Accountable {
    private val numbers: DocValuesLongHashSet = DocValuesLongHashSet(numbers)

    init {
        Arrays.sort(numbers)
    }

    override fun equals(other: Any?): Boolean {
        if (sameClassAs(other) == false) {
            return false
        }
        val that = other as SortedNumericDocValuesSetQuery
        return field == that.field && numbers == that.numbers
    }

    override fun hashCode(): Int {
        return Objects.hash(classHash(), field, numbers)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun toString(defaultField: String?): String {
        return "$field: $numbers"
    }

    override fun ramBytesUsed(): Long {
        return (BASE_RAM_BYTES
                + RamUsageEstimator.sizeOfObject(field)
                + RamUsageEstimator.sizeOfObject(numbers))
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (numbers.size() == 0) {
            return MatchNoDocsQuery()
        }
        return super.rewrite(indexSearcher)
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : ConstantScoreWeight(this, boost) {
            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return DocValues.isCacheable(ctx, field)
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                if (context.reader().fieldInfos.fieldInfo(field) == null) {
                    return null
                }
                val values: SortedNumericDocValues = DocValues.getSortedNumeric(context.reader(), field)
                val singleton: NumericDocValues? = DocValues.unwrapSingleton(values)
                val iterator: TwoPhaseIterator
                if (singleton != null) {
                    iterator =
                        object : TwoPhaseIterator(singleton) {
                            @Throws(IOException::class)
                            override fun matches(): Boolean {
                                val value: Long = singleton.longValue()
                                return value >= numbers.minValue && value <= numbers.maxValue && numbers.contains(value)
                            }

                            override fun matchCost(): Float {
                                return 5f // 2 comparisions, possible lookup in the set
                            }
                        }
                } else {
                    iterator =
                        object : TwoPhaseIterator(values) {
                            @Throws(IOException::class)
                            override fun matches(): Boolean {
                                val count: Int = values.docValueCount()
                                for (i in 0..<count) {
                                    val value: Long = values.nextValue()
                                    if (value < numbers.minValue) {
                                        continue
                                    } else if (value > numbers.maxValue) {
                                        return false // values are sorted, terminate
                                    } else if (numbers.contains(value)) {
                                        return true
                                    }
                                }
                                return false
                            }

                            override fun matchCost(): Float {
                                return 5f // 2 comparisons, possible lookup in the set
                            }
                        }
                }
                val scorer = ConstantScoreScorer(score(), scoreMode, iterator)
                return DefaultScorerSupplier(scorer)
            }
        }
    }

    companion object {
        private val BASE_RAM_BYTES: Long =
            RamUsageEstimator.shallowSizeOfInstance(SortedNumericDocValuesSetQuery::class)
    }
}
