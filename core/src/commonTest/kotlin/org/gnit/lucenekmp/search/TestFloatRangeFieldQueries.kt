package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatRange
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.BaseRangeFieldQueryTestCase
import org.gnit.lucenekmp.jdkport.assert
import kotlin.Float.Companion.NEGATIVE_INFINITY
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/** Random testing for FloatRange Queries. */
class TestFloatRangeFieldQueries : BaseRangeFieldQueryTestCase() {
    companion object {
        private const val FIELD_NAME = "floatRangeField"
    }

    private fun nextFloatInternal(): Float {
        return when (random().nextInt(5)) {
            0 -> NEGATIVE_INFINITY
            1 -> POSITIVE_INFINITY
            else -> {
                if (random().nextBoolean()) {
                    random().nextFloat()
                } else {
                    (random().nextInt(15) - 7) / 3f
                }
            }
        }
    }

    override fun nextRange(dimensions: Int): Range {
        val min = FloatArray(dimensions)
        val max = FloatArray(dimensions)

        var minV: Float
        var maxV: Float
        for (d in 0..<dimensions) {
            minV = nextFloatInternal()
            maxV = nextFloatInternal()
            min[d] = min(minV, maxV)
            max[d] = max(minV, maxV)
        }
        return FloatTestRange(min, max)
    }

    override fun newRangeField(box: Range): Field {
        return FloatRange(
            FIELD_NAME,
            (box as FloatTestRange).min,
            box.max,
        )
    }

    override fun newIntersectsQuery(box: Range): Query {
        return FloatRange.newIntersectsQuery(
            FIELD_NAME,
            (box as FloatTestRange).min,
            box.max,
        )
    }

    override fun newContainsQuery(box: Range): Query {
        return FloatRange.newContainsQuery(
            FIELD_NAME,
            (box as FloatTestRange).min,
            box.max,
        )
    }

    override fun newWithinQuery(box: Range): Query {
        return FloatRange.newWithinQuery(
            FIELD_NAME,
            (box as FloatTestRange).min,
            box.max,
        )
    }

    override fun newCrossesQuery(box: Range): Query {
        return FloatRange.newCrossesQuery(
            FIELD_NAME,
            (box as FloatTestRange).min,
            box.max,
        )
    }

    /** Basic test */
    @Test
    fun testBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // intersects (within)
        var document = Document()
        document.add(FloatRange(FIELD_NAME, floatArrayOf(-10.0f, -10.0f), floatArrayOf(9.1f, 10.1f)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(FloatRange(FIELD_NAME, floatArrayOf(10.0f, -10.0f), floatArrayOf(20.0f, 10.0f)))
        writer.addDocument(document)

        // intersects (contains, crosses)
        document = Document()
        document.add(FloatRange(FIELD_NAME, floatArrayOf(-20.0f, -20.0f), floatArrayOf(30.0f, 30.1f)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(FloatRange(FIELD_NAME, floatArrayOf(-11.1f, -11.2f), floatArrayOf(1.23f, 11.5f)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(FloatRange(FIELD_NAME, floatArrayOf(12.33f, 1.2f), floatArrayOf(15.1f, 29.9f)))
        writer.addDocument(document)

        // disjoint
        document = Document()
        document.add(FloatRange(FIELD_NAME, floatArrayOf(-122.33f, 1.2f), floatArrayOf(-115.1f, 29.9f)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(FloatRange(FIELD_NAME, floatArrayOf(NEGATIVE_INFINITY, 1.2f), floatArrayOf(-11.0f, 29.9f)))
        writer.addDocument(document)

        // equal (within, contains, intersects)
        document = Document()
        document.add(FloatRange(FIELD_NAME, floatArrayOf(-11.0f, -15.0f), floatArrayOf(15.0f, 20.0f)))
        writer.addDocument(document)

        val reader = writer.reader
        val searcher = newSearcher(reader)
        assertEquals(
            7,
            searcher.count(
                FloatRange.newIntersectsQuery(
                    FIELD_NAME,
                    floatArrayOf(-11.0f, -15.0f),
                    floatArrayOf(15.0f, 20.0f),
                ),
            ),
        )
        assertEquals(
            2,
            searcher.count(
                FloatRange.newWithinQuery(
                    FIELD_NAME,
                    floatArrayOf(-11.0f, -15.0f),
                    floatArrayOf(15.0f, 20.0f),
                ),
            ),
        )
        assertEquals(
            2,
            searcher.count(
                FloatRange.newContainsQuery(
                    FIELD_NAME,
                    floatArrayOf(-11.0f, -15.0f),
                    floatArrayOf(15.0f, 20.0f),
                ),
            ),
        )
        assertEquals(
            5,
            searcher.count(
                FloatRange.newCrossesQuery(
                    FIELD_NAME,
                    floatArrayOf(-11.0f, -15.0f),
                    floatArrayOf(15.0f, 20.0f),
                ),
            ),
        )

        reader.close()
        writer.close()
        dir.close()
    }

    /** FloatRange test class implementation - use to validate FloatRange */
    private class FloatTestRange(min: FloatArray, max: FloatArray) : Range() {
        val min: FloatArray = min
        val max: FloatArray = max

        init {
            assert(min.isNotEmpty() && max.isNotEmpty()) { "test box: min/max cannot be null or empty" }
            assert(min.size == max.size) { "test box: min/max length do not agree" }
        }

        override fun numDimensions(): Int {
            return min.size
        }

        override fun getMin(dim: Int): Any {
            return min[dim]
        }

        override fun setMin(dim: Int, value: Any) {
            val v = value as Float
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
            val v = value as Float
            if (max[dim] > v) {
                min[dim] = v
            } else {
                max[dim] = v
            }
        }

        override fun isEqual(other: Range): Boolean {
            other as FloatTestRange
            return min.contentEquals(other.min) && max.contentEquals(other.max)
        }

        override fun isDisjoint(other: Range): Boolean {
            other as FloatTestRange
            for (d in min.indices) {
                if (min[d] > other.max[d] || max[d] < other.min[d]) {
                    return true
                }
            }
            return false
        }

        override fun isWithin(other: Range): Boolean {
            other as FloatTestRange
            for (d in min.indices) {
                if (min[d] < other.min[d] || max[d] > other.max[d]) {
                    return false
                }
            }
            return true
        }

        override fun contains(other: Range): Boolean {
            other as FloatTestRange
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
