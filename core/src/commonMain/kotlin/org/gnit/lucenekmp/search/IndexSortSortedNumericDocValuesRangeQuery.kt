package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.PointTree
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.pop
import org.gnit.lucenekmp.jdkport.push
import org.gnit.lucenekmp.search.SortField.Type
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import kotlin.math.min

/**
 * A range query that can take advantage of the fact that the index is sorted to speed up execution.
 * If the index is sorted on the same field as the query, it performs binary search on the field's
 * numeric doc values to find the documents at the lower and upper ends of the range.
 *
 *
 * This optimized execution strategy is only used if the following conditions hold:
 *
 *
 *  * The index is sorted, and its primary sort is on the same field as the query.
 *  * The query field has either [SortedNumericDocValues] or [NumericDocValues].
 *  * The sort field is of type `SortField.Type.LONG` or `SortField.Type.INT`.
 *  * The segments must have at most one field value per document (otherwise we cannot easily
 * determine the matching document IDs through a binary search).
 *
 *
 * If any of these conditions isn't met, the search is delegated to `fallbackQuery`.
 *
 *
 * This fallback must be an equivalent range query -- it should produce the same documents and
 * give constant scores. As an example, an [IndexSortSortedNumericDocValuesRangeQuery] might
 * be constructed as follows:
 *
 * <pre class="prettyprint">
 * String field = "field";
 * long lowerValue = 0, long upperValue = 10;
 * Query fallbackQuery = LongPoint.newRangeQuery(field, lowerValue, upperValue);
 * Query rangeQuery = new IndexSortSortedNumericDocValuesRangeQuery(
 * field, lowerValue, upperValue, fallbackQuery);
</pre> *
 *
 * @lucene.experimental
 */
class IndexSortSortedNumericDocValuesRangeQuery(
    private val field: String,
    private val lowerValue: Long,
    private val upperValue: Long,
    val fallbackQuery: Query
) : Query() {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || this::class != o::class) return false
        val that = o as IndexSortSortedNumericDocValuesRangeQuery
        return lowerValue == that.lowerValue && upperValue == that.upperValue && field == that.field
                && fallbackQuery == that.fallbackQuery
    }

    override fun hashCode(): Int {
        return Objects.hash(field, lowerValue, upperValue, fallbackQuery)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
            fallbackQuery.visit(visitor)
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

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (lowerValue == Long.MIN_VALUE && upperValue == Long.MAX_VALUE) {
            return FieldExistsQuery(field)
        }

        val rewrittenFallback: Query = fallbackQuery.rewrite(indexSearcher)
        if (rewrittenFallback is MatchAllDocsQuery) {
            return MatchAllDocsQuery()
        }
        if (rewrittenFallback === fallbackQuery) {
            return this
        } else {
            return IndexSortSortedNumericDocValuesRangeQuery(
                field, lowerValue, upperValue, rewrittenFallback
            )
        }
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        val fallbackWeight: Weight =
            fallbackQuery.createWeight(searcher, scoreMode, boost)

        return object : ConstantScoreWeight(this, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val itAndCount = getDocIdSetIteratorOrNull(context)
                if (itAndCount != null) {
                    val disi: DocIdSetIterator = itAndCount.it
                    return object : ScorerSupplier() {
                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            return ConstantScoreScorer(
                                score(),
                                scoreMode,
                                disi
                            )
                        }

                        override fun cost(): Long {
                            return disi.cost()
                        }
                    }
                }
                return fallbackWeight.scorerSupplier(context)
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                // Both queries should always return the same values, so we can just check
                // if the fallback query is cacheable.
                return fallbackWeight.isCacheable(ctx)
            }

            @Throws(IOException::class)
            override fun count(context: LeafReaderContext): Int {
                if (context.reader().hasDeletions() == false) {
                    if (lowerValue > upperValue) {
                        return 0
                    }
                    var itAndCount: IteratorAndCount? = null
                    val reader: LeafReader = context.reader()

                    // first use bkd optimization if possible
                    val sortedNumericValues: SortedNumericDocValues =
                        DocValues.getSortedNumeric(reader, field)
                    val numericValues: NumericDocValues = DocValues.unwrapSingleton(sortedNumericValues)!!
                    val pointValues: PointValues? = reader.getPointValues(field)
                    if (pointValues != null && pointValues.docCount == reader.maxDoc()) {
                        itAndCount = getDocIdSetIteratorOrNullFromBkd(context, numericValues)
                    }
                    if (itAndCount != null && itAndCount.count != -1) {
                        return itAndCount.count
                    }

                    // use index sort optimization if possible
                    val indexSort: Sort? = reader.metaData.sort
                    if (indexSort != null && indexSort.sort.isNotEmpty() && indexSort.sort[0].field == field) {
                        val sortField: SortField = indexSort.sort[0]
                        val sortFieldType: Type =
                            getSortFieldType(sortField)
                        // The index sort optimization is only supported for Type.INT and Type.LONG
                        if (sortFieldType == Type.INT || sortFieldType == Type.LONG) {
                            val missingValue: Any? = sortField.missingValue
                            val missingLongValue =
                                if (missingValue == null) 0L else missingValue as Long
                            // all documents have docValues or missing value falls outside the range
                            if ((pointValues != null && pointValues.docCount == reader.maxDoc())
                                || (missingLongValue !in lowerValue..upperValue)
                            ) {
                                itAndCount = getDocIdSetIterator(
                                    sortField,
                                    sortFieldType,
                                    context,
                                    numericValues
                                )
                            }
                            if (itAndCount != null && itAndCount.count != -1) {
                                return itAndCount.count
                            }
                        }
                    }
                }
                return fallbackWeight.count(context)
            }
        }
    }

    private class ValueAndDoc {
        lateinit var value: ByteArray
        var docID: Int = 0
        var done: Boolean = false
    }

    @Throws(IOException::class)
    private fun matchNone(
        points: PointValues,
        queryLowerPoint: ByteArray,
        queryUpperPoint: ByteArray
    ): Boolean {
        assert(points.numDimensions == 1)
        val comparator: ByteArrayComparator =
            ArrayUtil.getUnsignedComparator(points.bytesPerDimension)
        return comparator.compare(points.minPackedValue, 0, queryUpperPoint, 0) > 0
                || comparator.compare(points.maxPackedValue, 0, queryLowerPoint, 0) < 0
    }

    @Throws(IOException::class)
    private fun matchAll(
        points: PointValues,
        queryLowerPoint: ByteArray,
        queryUpperPoint: ByteArray
    ): Boolean {
        assert(points.numDimensions == 1)
        val comparator: ByteArrayComparator =
            ArrayUtil.getUnsignedComparator(points.bytesPerDimension)
        return comparator.compare(points.minPackedValue, 0, queryLowerPoint, 0) >= 0
                && comparator.compare(points.maxPackedValue, 0, queryUpperPoint, 0) <= 0
    }

    @Throws(IOException::class)
    private fun getDocIdSetIteratorOrNullFromBkd(
        context: LeafReaderContext,
        delegate: DocIdSetIterator
    ): IteratorAndCount? {
        val indexSort: Sort? = context.reader().metaData.sort
        if (indexSort == null || indexSort.sort.size == 0 || indexSort.sort[0].field == field == false) {
            return null
        }

        val reverse: Boolean = indexSort.sort[0].reverse

        val points: PointValues? = context.reader().getPointValues(field)
        if (points == null) {
            return null
        }

        if (points.numDimensions != 1) {
            return null
        }

        if (points.bytesPerDimension != Long.SIZE_BYTES
            && points.bytesPerDimension != Int.SIZE_BYTES
        ) {
            return null
        }

        if (points.size() != points.docCount.toLong()) {
            return null
        }

        assert(lowerValue <= upperValue)
        val queryLowerPoint: ByteArray
        val queryUpperPoint: ByteArray
        if (points.bytesPerDimension == Int.SIZE_BYTES) {
            queryLowerPoint = IntPoint.pack(lowerValue.toInt()).bytes
            queryUpperPoint = IntPoint.pack(upperValue.toInt()).bytes
        } else {
            queryLowerPoint = LongPoint.pack(lowerValue).bytes
            queryUpperPoint = LongPoint.pack(upperValue).bytes
        }
        if (matchNone(points, queryLowerPoint, queryUpperPoint)) {
            return IteratorAndCount.empty()
        }
        if (matchAll(points, queryLowerPoint, queryUpperPoint)) {
            val maxDoc: Int = context.reader().maxDoc()
            if (points.docCount == maxDoc) {
                return IteratorAndCount.all(maxDoc)
            } else {
                return IteratorAndCount.sparseRange(0, maxDoc, delegate)
            }
        }

        val minDocId: Int
        var maxDocId: Int
        val comparator: ByteArrayComparator =
            ArrayUtil.getUnsignedComparator(points.bytesPerDimension)

        if (reverse) {
            minDocId = nextDoc(points.pointTree, queryUpperPoint, false, comparator, true) + 1
        } else {
            minDocId = nextDoc(points.pointTree, queryLowerPoint, true, comparator, false)
            if (minDocId == -1) {
                // No matches
                return IteratorAndCount.empty()
            }
        }

        if (reverse) {
            maxDocId = nextDoc(points.pointTree, queryLowerPoint, true, comparator, true) + 1
            if (maxDocId == 0) {
                // No matches
                return IteratorAndCount.empty()
            }
        } else {
            maxDocId = nextDoc(points.pointTree, queryUpperPoint, false, comparator, false)
            if (maxDocId == -1) {
                maxDocId = context.reader().maxDoc()
            }
        }

        if (minDocId == maxDocId) {
            return IteratorAndCount.empty()
        }

        if ((points.docCount == context.reader().maxDoc())) {
            return IteratorAndCount.denseRange(minDocId, maxDocId)
        } else {
            return IteratorAndCount.sparseRange(minDocId, maxDocId, delegate)
        }
    }

    @Throws(IOException::class)
    private fun getDocIdSetIteratorOrNull(context: LeafReaderContext): IteratorAndCount? {
        if (lowerValue > upperValue) {
            return IteratorAndCount.empty()
        }

        val sortedNumericValues: SortedNumericDocValues =
            DocValues.getSortedNumeric(context.reader(), field)
        val numericValues: NumericDocValues? = DocValues.unwrapSingleton(sortedNumericValues)
        if (numericValues != null) {
            val itAndCount = getDocIdSetIteratorOrNullFromBkd(context, numericValues)
            if (itAndCount != null) {
                return itAndCount
            }
            val indexSort: Sort? = context.reader().metaData.sort
            if (indexSort != null && indexSort.sort.isNotEmpty() && indexSort.sort[0].field == field) {
                val sortField: SortField = indexSort.sort[0]
                val sortFieldType: Type =
                    getSortFieldType(sortField)
                // The index sort optimization is only supported for Type.INT and Type.LONG
                if (sortFieldType == Type.INT || sortFieldType == Type.LONG) {
                    return getDocIdSetIterator(sortField, sortFieldType, context, numericValues)
                }
            }
        }
        return null
    }

    /**
     * Computes the document IDs that lie within the range [lowerValue, upperValue] by performing
     * binary search on the field's doc values.
     *
     *
     * Because doc values only allow forward iteration, we need to reload the field comparator
     * every time the binary search accesses an earlier element.
     *
     *
     * We must also account for missing values when performing the binary search. For this reason,
     * we load the [FieldComparator] instead of checking the docvalues directly. The returned
     * [DocIdSetIterator] makes sure to wrap the original docvalues to skip over documents with
     * no value.
     */
    @Throws(IOException::class)
    private fun getDocIdSetIterator(
        sortField: SortField,
        sortFieldType: Type,
        context: LeafReaderContext,
        delegate: DocIdSetIterator
    ): IteratorAndCount {
        val lower = if (sortField.reverse) upperValue else lowerValue
        val upper = if (sortField.reverse) lowerValue else upperValue
        val maxDoc: Int = context.reader().maxDoc()

        // Perform a binary search to find the first document with value >= lower.
        var comparator = loadComparator(sortField, sortFieldType, lower, context)
        var low = 0
        var high = maxDoc - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (comparator.compare(mid) <= 0) {
                high = mid - 1
                comparator = loadComparator(sortField, sortFieldType, lower, context)
            } else {
                low = mid + 1
            }
        }
        val firstDocIdInclusive = high + 1

        // Perform a binary search to find the first document with value > upper.
        // Since we know that upper >= lower, we can initialize the lower bound
        // of the binary search to the result of the previous search.
        comparator = loadComparator(sortField, sortFieldType, upper, context)
        low = firstDocIdInclusive
        high = maxDoc - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (comparator.compare(mid) < 0) {
                high = mid - 1
                comparator = loadComparator(sortField, sortFieldType, upper, context)
            } else {
                low = mid + 1
            }
        }

        val lastDocIdExclusive = high + 1

        if (firstDocIdInclusive == lastDocIdExclusive) {
            return IteratorAndCount.empty()
        }

        val missingValue: Any? = sortField.missingValue
        val reader: LeafReader = context.reader()
        val pointValues: PointValues? = reader.getPointValues(field)
        val missingLongValue = if (missingValue == null) 0L else missingValue as Long
        // all documents have docValues or missing value falls outside the range
        if ((pointValues != null && pointValues.docCount == reader.maxDoc())
            || (missingLongValue !in lowerValue..upperValue)
        ) {
            return IteratorAndCount.denseRange(firstDocIdInclusive, lastDocIdExclusive)
        } else {
            return IteratorAndCount.sparseRange(
                firstDocIdInclusive,
                lastDocIdExclusive,
                delegate
            )
        }
    }

    /** Compares the given document's value with a stored reference value.  */
    private fun interface ValueComparator {
        @Throws(IOException::class)
        fun compare(docID: Int): Int
    }

    /**
     * Provides a `DocIdSetIterator` along with an accurate count of documents provided by the
     * iterator (or `-1` if an accurate count is unknown).
     */
    private class IteratorAndCount(val it: DocIdSetIterator, val count: Int) {

        companion object {
            fun empty(): IteratorAndCount {
                return IteratorAndCount(DocIdSetIterator.empty(), 0)
            }

            fun all(maxDoc: Int): IteratorAndCount {
                return IteratorAndCount(
                    DocIdSetIterator.all(maxDoc),
                    maxDoc
                )
            }

            fun denseRange(minDoc: Int, maxDoc: Int): IteratorAndCount {
                return IteratorAndCount(
                    DocIdSetIterator.range(
                        minDoc,
                        maxDoc
                    ), maxDoc - minDoc
                )
            }

            fun sparseRange(
                minDoc: Int,
                maxDoc: Int,
                delegate: DocIdSetIterator
            ): IteratorAndCount {
                return IteratorAndCount(BoundedDocIdSetIterator(minDoc, maxDoc, delegate), -1)
            }
        }
    }

    /**
     * A doc ID set iterator that wraps a delegate iterator and only returns doc IDs in the range
     * [firstDocInclusive, lastDoc).
     */
    private class BoundedDocIdSetIterator(
        firstDoc: Int,
        lastDoc: Int,
        delegate: DocIdSetIterator
    ) : DocIdSetIterator() {
        private val firstDoc: Int
        private val lastDoc: Int
        private val delegate: DocIdSetIterator

        private var docID = -1

        init {
            checkNotNull(delegate)
            this.firstDoc = firstDoc
            this.lastDoc = lastDoc
            this.delegate = delegate
        }

        override fun docID(): Int {
            return docID
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return advance(docID + 1)
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            var target = target
            if (target < firstDoc) {
                target = firstDoc
            }

            val result: Int = delegate.advance(target)
            if (result < lastDoc) {
                docID = result
            } else {
                docID = NO_MORE_DOCS
            }
            return docID
        }

        override fun cost(): Long {
            return min(delegate.cost(), (lastDoc - firstDoc).toLong())
        }
    }

    companion object {
        /**
         * Move to the minimum leaf node that has at least one value that is greater than (or equal to if
         * `allowEqual`) `value`, and return the next greater value on this block. Upon
         * returning, the `pointTree` must be on the leaf node where the value was found.
         */
        @Throws(IOException::class)
        private fun findNextValue(
            pointTree: PointTree,
            value: ByteArray,
            allowEqual: Boolean,
            comparator: ByteArrayComparator,
            lastDoc: Boolean
        ): ValueAndDoc? {
            val cmp: Int = comparator.compare(pointTree.maxPackedValue, 0, value, 0)
            if (cmp < 0 || (cmp == 0 && allowEqual == false)) {
                return null
            }
            if (!pointTree.moveToChild()) {
                val vd = ValueAndDoc()
                pointTree.visitDocValues(
                    object : PointValues.IntersectVisitor {
                        @Throws(IOException::class)
                        override fun visit(docID: Int, packedValue: ByteArray) {
                            if (vd.value == null) {
                                val cmp: Int = comparator.compare(packedValue, 0, value, 0)
                                if (cmp > 0 || (cmp == 0 && allowEqual)) {
                                    vd.value = packedValue.copyOf()
                                    vd.docID = docID
                                }
                            } else if (lastDoc && vd.done == false) {
                                val cmp: Int = comparator.compare(packedValue, 0, vd.value, 0)
                                assert(cmp >= 0)
                                if (cmp > 0) {
                                    vd.done = true
                                } else {
                                    vd.docID = docID
                                }
                            }
                        }

                        @Throws(IOException::class)
                        override fun visit(docID: Int) {
                            throw UnsupportedOperationException()
                        }

                        override fun compare(
                            minPackedValue: ByteArray,
                            maxPackedValue: ByteArray
                        ): Relation {
                            return Relation.CELL_CROSSES_QUERY
                        }
                    })
                if (vd.value != null) {
                    return vd
                } else {
                    return null
                }
            }

            // Recurse
            do {
                val vd = findNextValue(pointTree, value, allowEqual, comparator, lastDoc)
                if (vd != null) {
                    return vd
                }
            } while (pointTree.moveToSibling())

            val moved: Boolean = pointTree.moveToParent()
            assert(moved)
            return null
        }

        /**
         * Find the next value that is greater than (or equal to if `allowEqual`) and return either
         * its first doc ID or last doc ID depending on `lastDoc`. This method returns -1 if there
         * is no greater value in the dataset.
         */
        @Throws(IOException::class)
        private fun nextDoc(
            pointTree: PointTree,
            value: ByteArray,
            allowEqual: Boolean,
            comparator: ByteArrayComparator,
            lastDoc: Boolean
        ): Int {
            val vd = findNextValue(pointTree, value, allowEqual, comparator, lastDoc)
            if (vd == null) {
                return -1
            }
            if (lastDoc == false || vd.done) {
                return vd.docID
            }

            // We found the next value, now we need the last doc ID.
            val doc = lastDoc(pointTree, vd.value, comparator)
            if (doc == -1) {
                // vd.docID was actually the last doc ID
                return vd.docID
            } else {
                return doc
            }
        }

        /**
         * Compute the last doc ID that matches the given value and is stored on a leaf node that compares
         * greater than the current leaf node that the provided [PointTree] is positioned on. This
         * returns -1 if no other leaf node contains the provided `value`.
         */
        @Throws(IOException::class)
        private fun lastDoc(
            pointTree: PointTree,
            value: ByteArray,
            comparator: ByteArrayComparator
        ): Int {
            // Create a stack of nodes that may contain value that we'll use to search for the last leaf
            // node that contains `value`.
            // While the logic looks a bit complicated due to the fact that the PointTree API doesn't allow
            // moving back to previous siblings, this effectively performs a binary search.
            val stack: ArrayDeque<PointTree> = ArrayDeque()

            outer@ while (true) {
                // Move to the next node

                while (pointTree.moveToSibling() == false) {
                    if (pointTree.moveToParent() == false) {
                        // No next node
                        break@outer
                    }
                }

                val cmp: Int = comparator.compare(pointTree.minPackedValue, 0, value, 0)
                if (cmp > 0) {
                    // This node doesn't have `value`, so next nodes can't either
                    break
                }

                stack.push(pointTree.clone())
            }

            while (stack.isEmpty() == false) {
                val next: PointTree = stack.pop()
                if (next.moveToChild() == false) {
                    val lastDoc = intArrayOf(-1)
                    next.visitDocValues(
                        object : PointValues.IntersectVisitor {
                            @Throws(IOException::class)
                            override fun visit(docID: Int) {
                                throw UnsupportedOperationException()
                            }

                            @Throws(IOException::class)
                            override fun visit(docID: Int, packedValue: ByteArray) {
                                val cmp: Int = comparator.compare(value, 0, packedValue, 0)
                                if (cmp == 0) {
                                    lastDoc[0] = docID
                                }
                            }

                            override fun compare(
                                minPackedValue: ByteArray,
                                maxPackedValue: ByteArray
                            ): Relation {
                                return Relation.CELL_CROSSES_QUERY
                            }
                        })
                    if (lastDoc[0] != -1) {
                        return lastDoc[0]
                    }
                } else {
                    do {
                        val cmp: Int = comparator.compare(next.minPackedValue, 0, value, 0)
                        if (cmp > 0) {
                            // This node doesn't have `value`, so next nodes can't either
                            break
                        }
                        stack.push(next.clone())
                    } while (next.moveToSibling())
                }
            }

            return -1
        }

        @Throws(IOException::class)
        private fun loadComparator(
            sortField: SortField,
            type: Type,
            topValue: Long,
            context: LeafReaderContext
        ): ValueComparator {
            val fieldComparator: FieldComparator<Number> =
                sortField.getComparator(
                    1,
                    Pruning.NONE
                ) as FieldComparator<Number>
            if (type == Type.INT) {
                fieldComparator.setTopValue(topValue.toInt())
            } else {
                // Since we support only Type.INT and Type.LONG, assuming LONG for all other cases
                fieldComparator.setTopValue(topValue)
            }

            val leafFieldComparator: LeafFieldComparator =
                fieldComparator.getLeafComparator(context)
            val direction = if (sortField.reverse) -1 else 1

            return ValueComparator { doc: Int ->
                val value: Int = leafFieldComparator.compareTop(doc)
                direction * value
            }
        }

        private fun getSortFieldType(sortField: SortField): Type {
            // We expect the sortField to be SortedNumericSortField
            if (sortField is SortedNumericSortField) {
                return sortField.numericType
            } else {
                return sortField.type
            }
        }
    }
}
