package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.ConstantScoreScorerSupplier
import org.gnit.lucenekmp.search.ConstantScoreWeight
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocValuesRangeIterator
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.BytesRef

internal class SortedSetDocValuesRangeQuery(
    private val field: String,
    private val lowerValue: BytesRef?,
    private val upperValue: BytesRef?,
    lowerInclusive: Boolean,
    upperInclusive: Boolean
) : Query() {
    private val lowerInclusive: Boolean = lowerInclusive && lowerValue != null
    private val upperInclusive: Boolean = upperInclusive && upperValue != null

    override fun equals(obj: Any?): Boolean {
        if (sameClassAs(obj) == false) {
            return false
        }
        val that = obj as SortedSetDocValuesRangeQuery
        return field == that.field
                && lowerValue == that.lowerValue
                && upperValue == that.upperValue
                && lowerInclusive == that.lowerInclusive && upperInclusive == that.upperInclusive
    }

    override fun hashCode(): Int {
        return Objects.hash(
            classHash(),
            field,
            lowerValue,
            upperValue,
            lowerInclusive,
            upperInclusive
        )
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
        return b.append(if (lowerInclusive) "[" else "{")
            .append(if (lowerValue == null) "*" else lowerValue)
            .append(" TO ")
            .append(if (upperValue == null) "*" else upperValue)
            .append(if (upperInclusive) "]" else "}")
            .toString()
    }

    @Throws(IOException::class)
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (lowerValue == null && upperValue == null) {
            return FieldExistsQuery(field)
        }
        return super.rewrite(indexSearcher)
    }

    @Throws(IOException::class)
    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        return object : ConstantScoreWeight(this, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                if (context.reader().fieldInfos.fieldInfo(field) == null) {
                    return null
                }
                val skipper: DocValuesSkipper? =
                    context.reader().getDocValuesSkipper(field)
                val values: SortedSetDocValues =
                    DocValues.getSortedSet(context.reader(), field)

                // implement ScorerSupplier, since we do some expensive stuff to make a scorer
                return object : ConstantScoreScorerSupplier(
                    score(),
                    scoreMode,
                    context.reader().maxDoc()
                ) {
                    @Throws(IOException::class)
                    override fun iterator(leadCost: Long): DocIdSetIterator {
                        val minOrd: Long
                        if (lowerValue == null) {
                            minOrd = 0
                        } else {
                            val ord: Long = values.lookupTerm(lowerValue)
                            if (ord < 0) {
                                minOrd = -1 - ord
                            } else if (lowerInclusive) {
                                minOrd = ord
                            } else {
                                minOrd = ord + 1
                            }
                        }

                        val maxOrd: Long
                        if (upperValue == null) {
                            maxOrd = values.valueCount - 1
                        } else {
                            val ord: Long = values.lookupTerm(upperValue)
                            if (ord < 0) {
                                maxOrd = -2 - ord
                            } else if (upperInclusive) {
                                maxOrd = ord
                            } else {
                                maxOrd = ord - 1
                            }
                        }

                        // no terms matched in this segment
                        if (minOrd > maxOrd
                            || (skipper != null
                                    && (minOrd > skipper.maxValue() || maxOrd < skipper.minValue()))
                        ) {
                            return DocIdSetIterator.empty()
                        }

                        // all terms matched in this segment
                        if (skipper != null && skipper.docCount() == context.reader()
                                .maxDoc() && skipper.minValue() >= minOrd && skipper.maxValue() <= maxOrd
                        ) {
                            return DocIdSetIterator.all(skipper.docCount())
                        }

                        val singleton: SortedDocValues? =
                            DocValues.unwrapSingleton(values)
                        var iterator: TwoPhaseIterator
                        if (singleton != null) {
                            if (skipper != null) {
                                val psIterator: DocIdSetIterator? =
                                    getDocIdSetIteratorOrNullForPrimarySort(
                                        context.reader(), singleton, skipper, minOrd, maxOrd
                                    )
                                if (psIterator != null) {
                                    return psIterator
                                }
                            }
                            iterator =
                                object : TwoPhaseIterator(singleton) {
                                    @Throws(IOException::class)
                                    override fun matches(): Boolean {
                                        val ord = singleton.ordValue().toLong()
                                        return ord in minOrd..maxOrd
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
                                        for (i in 0..<values.docValueCount()) {
                                            val ord: Long = values.nextOrd()
                                            if (ord < minOrd) {
                                                continue
                                            }
                                            // Values are sorted, so the first ord that is >= minOrd is our best
                                            // candidate
                                            return ord <= maxOrd
                                        }
                                        return false // all ords were < minOrd
                                    }

                                    override fun matchCost(): Float {
                                        return 2f // 2 comparisons
                                    }
                                }
                        }
                        if (skipper != null) {
                            iterator = DocValuesRangeIterator(
                                iterator,
                                skipper,
                                minOrd,
                                maxOrd,
                                false
                            )
                        }
                        return TwoPhaseIterator.asDocIdSetIterator(iterator)
                    }

                    override fun cost(): Long {
                        return values.cost()
                    }
                }
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return DocValues.isCacheable(ctx, field)
            }
        }
    }

    @Throws(IOException::class)
    private fun getDocIdSetIteratorOrNullForPrimarySort(
        reader: LeafReader,
        sortedDocValues: SortedDocValues,
        skipper: DocValuesSkipper,
        minOrd: Long,
        maxOrd: Long
    ): DocIdSetIterator? {
        if (skipper.docCount() != reader.maxDoc()) {
            return null
        }
        val indexSort: Sort ?= reader.metaData.sort
        if (indexSort == null || indexSort.sort.isEmpty() || indexSort.sort[0].field == field == false) {
            return null
        }

        val minDocID: Int
        val maxDocID: Int
        if (indexSort.sort[0].reverse) {
            if (skipper.maxValue() <= maxOrd) {
                minDocID = 0
            } else {
                skipper.advance(Long.MIN_VALUE, maxOrd)
                minDocID = nextDoc(
                    skipper.minDocID(0),
                    sortedDocValues
                ) { l: Long -> l <= maxOrd }
            }
            if (skipper.minValue() >= minOrd) {
                maxDocID = skipper.docCount()
            } else {
                skipper.advance(Long.MIN_VALUE, minOrd)
                maxDocID = nextDoc(
                    skipper.minDocID(0),
                    sortedDocValues
                ) { l: Long -> l < minOrd }
            }
        } else {
            if (skipper.minValue() >= minOrd) {
                minDocID = 0
            } else {
                skipper.advance(minOrd, Long.MAX_VALUE)
                minDocID = nextDoc(
                    skipper.minDocID(0),
                    sortedDocValues
                ) { l: Long -> l >= minOrd }
            }
            if (skipper.maxValue() <= maxOrd) {
                maxDocID = skipper.docCount()
            } else {
                skipper.advance(maxOrd, Long.MAX_VALUE)
                maxDocID = nextDoc(
                    skipper.minDocID(0),
                    sortedDocValues
                ) { l: Long -> l > maxOrd }
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
            docValues: SortedDocValues,
            predicate: (Long) -> Boolean
        ): Int {
            var doc: Int = docValues.docID()
            if (startDoc > doc) {
                doc = docValues.advance(startDoc)
            }
            while (doc < DocIdSetIterator.NO_MORE_DOCS) {
                if (predicate(docValues.ordValue().toLong())) {
                    break
                }
                doc = docValues.nextDoc()
            }
            return doc
        }
    }
}

