package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleRange
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.BaseRangeFieldQueryTestCase
import kotlin.Double.Companion.NEGATIVE_INFINITY
import kotlin.Double.Companion.POSITIVE_INFINITY
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/** Random testing for RangeFieldQueries. */
class TestDoubleRangeFieldQueries : BaseRangeFieldQueryTestCase() {
    companion object {
        private const val FIELD_NAME = "doubleRangeField"
    }

    private fun nextDoubleInternal(): Double {
        return when (random().nextInt(5)) {
            0 -> NEGATIVE_INFINITY
            1 -> POSITIVE_INFINITY
            else -> {
                if (random().nextBoolean()) {
                    random().nextDouble()
                } else {
                    (random().nextInt(15) - 7) / 3.0
                }
            }
        }
    }

    override fun newIntersectsQuery(box: Range): Query {
        return DoubleRange.newIntersectsQuery(
            FIELD_NAME,
            (box as DoubleTestRange).min,
            box.max,
        )
    }

    override fun newContainsQuery(box: Range): Query {
        return DoubleRange.newContainsQuery(
            FIELD_NAME,
            (box as DoubleTestRange).min,
            box.max,
        )
    }

    override fun newWithinQuery(box: Range): Query {
        return DoubleRange.newWithinQuery(
            FIELD_NAME,
            (box as DoubleTestRange).min,
            box.max,
        )
    }

    override fun newCrossesQuery(box: Range): Query {
        return DoubleRange.newCrossesQuery(
            FIELD_NAME,
            (box as DoubleTestRange).min,
            box.max,
        )
    }

    override fun newRangeField(box: Range): Field {
        return DoubleRange(
            FIELD_NAME,
            (box as DoubleTestRange).min,
            box.max,
        )
    }

    override fun nextRange(dimensions: Int): Range {
        val min = DoubleArray(dimensions)
        val max = DoubleArray(dimensions)

        var minV: Double
        var maxV: Double
        for (d in 0..<dimensions) {
            minV = nextDoubleInternal()
            maxV = nextDoubleInternal()
            min[d] = min(minV, maxV)
            max[d] = max(minV, maxV)
        }
        return DoubleTestRange(min, max)
    }

    /** DoubleRange test class implementation - use to validate DoubleRange */
    private class DoubleTestRange(min: DoubleArray, max: DoubleArray) : Range() {
        val min: DoubleArray = min
        val max: DoubleArray = max

        override fun numDimensions(): Int {
            return min.size
        }

        override fun getMin(dim: Int): Any {
            return min[dim]
        }

        override fun setMin(dim: Int, value: Any) {
            val v = value as Double
            if (min[dim] < v) {
                max[dim] = v
            } else {
                min[dim] = v
            }
        }

        override fun getMax(dim: Int): Any {
            return max[dim]
        }

        override fun setMax(dim: Int, value: Any) {
            val v = value as Double
            if (max[dim] > v) {
                min[dim] = v
            } else {
                max[dim] = v
            }
        }

        override fun isEqual(other: Range): Boolean {
            return min.contentEquals((other as DoubleTestRange).min) && max.contentEquals(other.max)
        }

        override fun isDisjoint(other: Range): Boolean {
            other as DoubleTestRange
            for (d in min.indices) {
                if (min[d] > other.max[d] || max[d] < other.min[d]) {
                    return true
                }
            }
            return false
        }

        override fun isWithin(other: Range): Boolean {
            other as DoubleTestRange
            for (d in min.indices) {
                if (min[d] < other.min[d] || max[d] > other.max[d]) {
                    return false
                }
            }
            return true
        }

        override fun contains(other: Range): Boolean {
            other as DoubleTestRange
            for (d in min.indices) {
                if (min[d] > other.min[d] || max[d] < other.max[d]) {
                    return false
                }
            }
            return true
        }

        override fun toString(): String {
            val b = StringBuilder()
            b.append("Box(")
            b.append(min[0])
            b.append(" TO ")
            b.append(max[0])
            for (d in 1..<min.size) {
                b.append(", ")
                b.append(min[d])
                b.append(" TO ")
                b.append(max[d])
            }
            b.append(")")

            return b.toString()
        }
    }

    /** Basic test */
    @Test
    fun testBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // intersects (within)
        var document = Document()
        document.add(DoubleRange(FIELD_NAME, doubleArrayOf(-10.0, -10.0), doubleArrayOf(9.1, 10.1)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(DoubleRange(FIELD_NAME, doubleArrayOf(10.0, -10.0), doubleArrayOf(20.0, 10.0)))
        writer.addDocument(document)

        // intersects (contains, crosses)
        document = Document()
        document.add(DoubleRange(FIELD_NAME, doubleArrayOf(-20.0, -20.0), doubleArrayOf(30.0, 30.1)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(DoubleRange(FIELD_NAME, doubleArrayOf(-11.1, -11.2), doubleArrayOf(1.23, 11.5)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(DoubleRange(FIELD_NAME, doubleArrayOf(12.33, 1.2), doubleArrayOf(15.1, 29.9)))
        writer.addDocument(document)

        // disjoint
        document = Document()
        document.add(DoubleRange(FIELD_NAME, doubleArrayOf(-122.33, 1.2), doubleArrayOf(-115.1, 29.9)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(DoubleRange(FIELD_NAME, doubleArrayOf(NEGATIVE_INFINITY, 1.2), doubleArrayOf(-11.0, 29.9)))
        writer.addDocument(document)

        // equal (within, contains, intersects)
        document = Document()
        document.add(DoubleRange(FIELD_NAME, doubleArrayOf(-11.0, -15.0), doubleArrayOf(15.0, 20.0)))
        writer.addDocument(document)

        // search
        val reader = writer.reader
        val searcher = newSearcher(reader)
        assertEquals(
            7,
            searcher.count(
                DoubleRange.newIntersectsQuery(
                    FIELD_NAME,
                    doubleArrayOf(-11.0, -15.0),
                    doubleArrayOf(15.0, 20.0),
                ),
            ),
        )
        assertEquals(
            2,
            searcher.count(
                DoubleRange.newWithinQuery(
                    FIELD_NAME,
                    doubleArrayOf(-11.0, -15.0),
                    doubleArrayOf(15.0, 20.0),
                ),
            ),
        )
        assertEquals(
            2,
            searcher.count(
                DoubleRange.newContainsQuery(
                    FIELD_NAME,
                    doubleArrayOf(-11.0, -15.0),
                    doubleArrayOf(15.0, 20.0),
                ),
            ),
        )
        assertEquals(
            5,
            searcher.count(
                DoubleRange.newCrossesQuery(
                    FIELD_NAME,
                    doubleArrayOf(-11.0, -15.0),
                    doubleArrayOf(15.0, 20.0),
                ),
            ),
        )

        reader.close()
        writer.close()
        dir.close()
    }

    // tests inherited from BaseRangeFieldQueryTestCase
    @Test
    override fun testRandomTiny() = super.testRandomTiny()

    @Test
    override fun testRandomMedium() = super.testRandomMedium()

    @Test
    override fun testRandomBig() = super.testRandomBig()

    @Test
    override fun testMultiValued() = super.testMultiValued()

    @Test
    override fun testAllEqual() = super.testAllEqual()

    @Test
    override fun testLowCardinality() = super.testLowCardinality()

}
