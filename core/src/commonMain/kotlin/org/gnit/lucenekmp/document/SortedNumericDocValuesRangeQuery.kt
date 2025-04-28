package org.gnit.lucenekmp.document


import kotlinx.io.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.ConstantScoreScorerSupplier
import org.gnit.lucenekmp.search.ConstantScoreWeight
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocValuesRangeIterator
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllScorerSupplier
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.Weight

internal class SortedNumericDocValuesRangeQuery(
    private val field: String,
    private val lowerValue: Long,
    private val upperValue: Long
) : Query() {

    override fun equals(obj: Any?): Boolean {
        if (sameClassAs(obj) == false) {
            return false
        }
        val that = obj as SortedNumericDocValuesRangeQuery
        return field == that.field
                && lowerValue == that.lowerValue && upperValue == that.upperValue
    }

    override fun hashCode(): Int {
        return Objects.hash(classHash(), field, lowerValue, upperValue)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun toString(field: String?): String {
        val b = StringBuilder()
        if (this.field == field == false) {
            b.append(this.field).append(":")
        }
        return b.append("[")
            .append(lowerValue)
            .append(" TO ")
            .append(upperValue)
            .append("]")
            .toString()
    }

    @Throws(IOException::class)
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (lowerValue == Long.Companion.MIN_VALUE && upperValue == Long.Companion.MAX_VALUE) {
            return FieldExistsQuery(field)
        }
        if (lowerValue > upperValue) {
            return MatchNoDocsQuery()
        }
        return super.rewrite(indexSearcher)
    }

    @Throws(IOException::class)
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

                val maxDoc: Int = context.reader().maxDoc()
                val skipper: DocValuesSkipper? = context.reader().getDocValuesSkipper(field)
                if (skipper != null) {
                    if (skipper.minValue() > upperValue || skipper.maxValue() < lowerValue) {
                        return null
                    }
                    if (skipper.docCount() == maxDoc && skipper.minValue() >= lowerValue && skipper.maxValue() <= upperValue) {
                        return MatchAllScorerSupplier(score(), scoreMode, maxDoc)
                    }
                }

                val values: SortedNumericDocValues = DocValues.getSortedNumeric(context.reader(), field)
                val singleton: NumericDocValues? = DocValues.unwrapSingleton(values)
                var iterator: TwoPhaseIterator
                if (singleton != null) {
                    if (skipper != null) {
                        val psIterator: DocIdSetIterator? =
                            getDocIdSetIteratorOrNullForPrimarySort(context.reader(), singleton, skipper)
                        if (psIterator != null) {
                            return ConstantScoreScorerSupplier.fromIterator(
                                psIterator, score(), scoreMode, maxDoc
                            )
                        }
                    }
                    iterator =
                        object : TwoPhaseIterator(singleton) {
                            @Throws(IOException::class)
                            override fun matches(): Boolean {
                                val value: Long = singleton.longValue()
                                return value >= lowerValue && value <= upperValue
                            }

                            override fun matchCost(): Float {
                                return 2f // 2 comparisons
                            }
                        }
                } else {
                    iterator =
                        object : TwoPhaseIterator(values) {
                            @Throws(IOException::class)
                            override fun matches(): Boolean {
                                var i = 0
                                val count: Int = values.docValueCount()
                                while (i < count) {
                                    val value: Long = values.nextValue()
                                    if (value < lowerValue) {
                                        ++i
                                        continue
                                    }
                                    // Values are sorted, so the first value that is >= lowerValue is our best
                                    // candidate
                                    return value <= upperValue
                                    ++i
                                }
                                return false // all values were < lowerValue
                            }

                            override fun matchCost(): Float {
                                return 2f // 2 comparisons
                            }
                        }
                }
                if (skipper != null) {
                    iterator = DocValuesRangeIterator(iterator, skipper, lowerValue, upperValue, false)
                }
                return ConstantScoreScorerSupplier.fromIterator(
                    TwoPhaseIterator.asDocIdSetIterator(iterator), score(), scoreMode, maxDoc
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun getDocIdSetIteratorOrNullForPrimarySort(
        reader: LeafReader, numericDocValues: NumericDocValues, skipper: DocValuesSkipper
    ): DocIdSetIterator? {
        if (skipper.docCount() != reader.maxDoc()) {
            return null
        }
        val indexSort: Sort? = reader.metaData.sort
        if (indexSort == null || indexSort.sort.isEmpty() || !indexSort.sort[0].field.equals(field)) {
            return null
        }

        val minDocID: Int
        val maxDocID: Int
        if (indexSort.sort[0].reverse) {
            if (skipper.maxValue() <= upperValue) {
                minDocID = 0
            } else {
                skipper.advance(Long.Companion.MIN_VALUE, upperValue)
                minDocID = nextDoc(
                    skipper.minDocID(0),
                    numericDocValues
                ) { l: Long -> l <= upperValue }
            }
            if (skipper.minValue() >= lowerValue) {
                maxDocID = skipper.docCount()
            } else {
                skipper.advance(Long.Companion.MIN_VALUE, lowerValue)
                maxDocID = nextDoc(
                    skipper.minDocID(0),
                    numericDocValues
                ) { l: Long -> l < lowerValue }
            }
        } else {
            if (skipper.minValue() >= lowerValue) {
                minDocID = 0
            } else {
                skipper.advance(lowerValue, Long.Companion.MAX_VALUE)
                minDocID = nextDoc(
                    skipper.minDocID(0),
                    numericDocValues
                ) { l: Long -> l >= lowerValue }
            }
            if (skipper.maxValue() <= upperValue) {
                maxDocID = skipper.docCount()
            } else {
                skipper.advance(upperValue, Long.Companion.MAX_VALUE)
                maxDocID = nextDoc(
                    skipper.minDocID(0),
                    numericDocValues
                ) { l: Long -> l > upperValue }
            }
        }
        return if (minDocID == maxDocID)
            DocIdSetIterator.empty()
        else
            DocIdSetIterator.range(minDocID, maxDocID)
    }

    companion object {
        @Throws(IOException::class)
        private fun nextDoc(
            startDoc: Int,
            docValues: NumericDocValues,
            predicate: (Long) -> Boolean
        ): Int {
            var doc: Int = docValues.docID()
            if (startDoc > doc) {
                doc = docValues.advance(startDoc)
            }
            while (doc < DocIdSetIterator.NO_MORE_DOCS) {
                if (predicate(docValues.longValue())) {
                    break
                }
                doc = docValues.nextDoc()
            }
            return doc
        }
    }
}
