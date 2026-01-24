package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compareUnsigned
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.cast


internal class LongDistanceFeatureQuery(
    private val field: String,
    private val origin: Long,
    pivotDistance: Long
) : Query() {
    private val pivotDistance: Long

    init {
        require(pivotDistance > 0) { "pivotDistance must be > 0, got $pivotDistance" }
        this.pivotDistance = pivotDistance
    }

    override fun equals(o: Any?): Boolean {
        return sameClassAs(o) && equalsTo(this::class.cast(o))
    }

    private fun equalsTo(other: LongDistanceFeatureQuery): Boolean {
        return field == other.field
                && origin == other.origin && pivotDistance == other.pivotDistance
    }

    override fun hashCode(): Int {
        var h: Int = classHash()
        h = 31 * h + field.hashCode()
        h = 31 * h + origin.hashCode()
        h = 31 * h + pivotDistance.hashCode()
        return h
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun toString(field: String?): String {
        return (this::class.simpleName
                + "(field="
                + field
                + ",origin="
                + origin
                + ",pivotDistance="
                + pivotDistance
                + ")")
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        return object : Weight(this) {
            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return false
            }

            @Throws(IOException::class)
            override fun explain(
                context: LeafReaderContext,
                doc: Int
            ): Explanation {
                val multiDocValues: SortedNumericDocValues =
                    DocValues.getSortedNumeric(context.reader(), field)
                if (!multiDocValues.advanceExact(doc)) {
                    return Explanation.noMatch(
                        "Document $doc doesn't have a value for field $field"
                    )
                }
                val value = selectValue(multiDocValues)
                var distance = max(value, origin) - min(value, origin)
                if (distance < 0) {
                    // underflow, treat as MAX_VALUE
                    distance = Long.MAX_VALUE
                }
                val score =
                    (boost * (pivotDistance / (pivotDistance + distance.toDouble()))).toFloat()
                return Explanation.match(
                    score,
                    "Distance score, computed as weight * pivotDistance / (pivotDistance + abs(value - origin)) from:",
                    Explanation.match(boost, "weight"),
                    Explanation.match(pivotDistance, "pivotDistance"),
                    Explanation.match(origin, "origin"),
                    Explanation.match(value, "current value")
                )
            }

            @Throws(IOException::class)
            fun selectValue(multiDocValues: SortedNumericDocValues): Long {
                val count: Int = multiDocValues.docValueCount()

                var next: Long = multiDocValues.nextValue()
                if (count == 1 || next >= origin) {
                    return next
                }
                var previous = next
                for (i in 1..<count) {
                    next = multiDocValues.nextValue()
                    if (next >= origin) {
                        // Unsigned comparison because of underflows
                        if (Long.compareUnsigned(origin - previous, next - origin) < 0) {
                            return previous
                        } else {
                            return next
                        }
                    }
                    previous = next
                }

                assert(next < origin)
                return next
            }

            fun selectValues(multiDocValues: SortedNumericDocValues): NumericDocValues {
                val singleton: NumericDocValues? = DocValues.unwrapSingleton(multiDocValues)
                if (singleton != null) {
                    return singleton
                }
                return object : NumericDocValues() {
                    var value: Long = 0

                    @Throws(IOException::class)
                    override fun longValue(): Long {
                        return value
                    }

                    @Throws(IOException::class)
                    override fun advanceExact(target: Int): Boolean {
                        if (multiDocValues.advanceExact(target)) {
                            value = selectValue(multiDocValues)
                            return true
                        } else {
                            return false
                        }
                    }

                    override fun docID(): Int {
                        return multiDocValues.docID()
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        return multiDocValues.nextDoc()
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        return multiDocValues.advance(target)
                    }

                    override fun cost(): Long {
                        return multiDocValues.cost()
                    }
                }
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val pointValues: PointValues? = context.reader().getPointValues(field)
                if (pointValues == null) {
                    // No data on this segment
                    return null
                }
                val multiDocValues: SortedNumericDocValues =
                    DocValues.getSortedNumeric(context.reader(), field)
                val docValues: NumericDocValues =
                    selectValues(multiDocValues)
                return object : ScorerSupplier() {
                    @Throws(IOException::class)
                    override fun get(leadCost: Long): Scorer {
                        return this@LongDistanceFeatureQuery.DistanceScorer(
                            context.reader().maxDoc(), leadCost, boost, pointValues, docValues
                        )
                    }

                    override fun cost(): Long {
                        return docValues.cost()
                    }
                }
            }
        }
    }

    private inner class DistanceScorer(
        private val maxDoc: Int,
        private val leadCost: Long,
        private val boost: Float,
        private val pointValues: PointValues,
        private val docValues: NumericDocValues
    ) : Scorer() {
        private var it: DocIdSetIterator
        private var doc = -1
        private var maxDistance = Long.MAX_VALUE

        override fun docID(): Int {
            return doc
        }

        fun score(distance: Long): Float {
            return (boost * (pivotDistance / (pivotDistance + distance.toDouble()))).toFloat()
        }

        /**
         * Inverting the score computation is very hard due to all potential rounding errors, so we
         * binary search the maximum distance.
         */
        fun computeMaxDistance(minScore: Float, previousMaxDistance: Long): Long {
            assert(score(0) >= minScore)
            if (score(previousMaxDistance) >= minScore) {
                // minScore did not decrease enough to require an update to the max distance
                return previousMaxDistance
            }
            assert(score(previousMaxDistance) < minScore)
            var min: Long = 0
            var max = previousMaxDistance
            // invariant: score(min) >= minScore && score(max) < minScore
            while (max - min > 1) {
                val mid = (min + max) ushr 1
                val score = score(mid)
                if (score >= minScore) {
                    min = mid
                } else {
                    max = mid
                }
            }
            assert(score(min) >= minScore)
            assert(min == Long.MAX_VALUE || score(min + 1) < minScore)
            return min
        }

        @Throws(IOException::class)
        override fun score(): Float {
            if (docValues.advanceExact(docID()) == false) {
                return 0f
            }
            val v: Long = docValues.longValue()
            // note: distance is unsigned
            var distance = max(v, origin) - min(v, origin)
            if (distance < 0) {
                // underflow
                // treat distances that are greater than MAX_VALUE as MAX_VALUE
                distance = Long.MAX_VALUE
            }
            return score(distance)
        }

        override fun iterator(): DocIdSetIterator {
            // add indirection so that if 'it' is updated then it will
            // be taken into account
            return object : DocIdSetIterator() {
                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    return it.nextDoc().also { doc = it }
                }

                override fun docID(): Int {
                    return doc
                }

                override fun cost(): Long {
                    return it.cost()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    return it.advance(target).also { doc = it }
                }
            }
        }

        override fun getMaxScore(upTo: Int): Float {
            return boost
        }

        private var setMinCompetitiveScoreCounter = 0

        init {
            // initially use doc values in order to iterate all documents that have
            // a value for this field
            this.it = docValues
        }

        override var minCompetitiveScore: Float
            get() {
                TODO()
            }
            set(minScore) {
                if (minScore > boost) {
                    it = DocIdSetIterator.empty()
                    return
                }

                // Start sampling if we get called too much
                setMinCompetitiveScoreCounter++
                if (setMinCompetitiveScoreCounter > 256 && (setMinCompetitiveScoreCounter and 0x1f) != 0x1f) {
                    return
                }

                val previousMaxDistance = maxDistance
                maxDistance = computeMaxDistance(minScore, maxDistance)
                if (maxDistance == previousMaxDistance) {
                    // nothing to update
                    return
                }
                var minValue = origin - maxDistance
                if (minValue > origin) {
                    // underflow
                    minValue = Long.MIN_VALUE
                }
                var maxValue = origin + maxDistance
                if (maxValue < origin) {
                    // overflow
                    maxValue = Long.MAX_VALUE
                }
                val min = minValue
                val max = maxValue

                val result = DocIdSetBuilder(maxDoc)
                val doc = docID()
                val visitor: IntersectVisitor =
                    object : IntersectVisitor {
                        var adder: DocIdSetBuilder.BulkAdder? = null

                        override fun grow(count: Int) {
                            adder = result.grow(count)
                        }

                        override fun visit(docID: Int) {
                            if (docID <= doc) {
                                // Already visited or skipped
                                return
                            }
                            adder!!.add(docID)
                        }

                        override fun visit(docID: Int, packedValue: ByteArray) {
                            if (docID <= doc) {
                                // Already visited or skipped
                                return
                            }
                            val docValue: Long =
                                NumericUtils.sortableBytesToLong(packedValue, 0)
                            if (docValue !in min..max) {
                                // Doc's value is too low, in this dimension
                                return
                            }

                            // Doc is in-bounds
                            adder!!.add(docID)
                        }

                        @Throws(IOException::class)
                        override fun visit(iterator: DocIdSetIterator) {
                            var docID: Int
                            while ((iterator.nextDoc().also {
                                    docID = it
                                }) != DocIdSetIterator.NO_MORE_DOCS) {
                                visit(docID)
                            }
                        }

                        override fun visit(ref: IntsRef) {
                            for (i in 0..<ref.length) {
                                visit(ref.ints[ref.offset + i])
                            }
                        }

                        override fun compare(
                            minPackedValue: ByteArray,
                            maxPackedValue: ByteArray
                        ): Relation {
                            val minDocValue: Long =
                                NumericUtils.sortableBytesToLong(
                                    minPackedValue,
                                    0
                                )
                            val maxDocValue: Long =
                                NumericUtils.sortableBytesToLong(
                                    maxPackedValue,
                                    0
                                )

                            if (minDocValue > max || maxDocValue < min) {
                                return Relation.CELL_OUTSIDE_QUERY
                            }

                            if (minDocValue < min || maxDocValue > max) {
                                return Relation.CELL_CROSSES_QUERY
                            }

                            return Relation.CELL_INSIDE_QUERY
                        }
                    }

                val currentQueryCost = min(leadCost, it.cost())
                // TODO: what is the right factor compared to the current disi Is 8 optimal
                val threshold = currentQueryCost ushr 3
                if (PointValues.isEstimatedPointCountGreaterThanOrEqualTo(
                        visitor, pointValues.pointTree, threshold
                    )
                ) {
                    // the new range is not selective enough to be worth materializing
                    return
                }
                pointValues.intersect(visitor)
                it = result.build().iterator()
            }
    }
}
