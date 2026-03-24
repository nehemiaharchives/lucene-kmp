package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.IntRange
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.BaseRangeFieldQueryTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.assert
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/** Random testing for IntRange Queries. */
class TestIntRangeFieldQueries : BaseRangeFieldQueryTestCase() {
    companion object {
        private const val FIELD_NAME = "intRangeField"
    }

    private fun nextIntInternal(): Int {
        return when (random().nextInt(5)) {
            0 -> Int.MIN_VALUE
            1 -> Int.MAX_VALUE
            else -> {
                val bpv = random().nextInt(32)
                when (bpv) {
                    32 -> random().nextInt()
                    else -> {
                        var v = TestUtil.nextInt(random(), 0, (1 shl bpv) - 1)
                        if (bpv > 0) {
                            // negative values sometimes
                            v -= 1 shl (bpv - 1)
                        }
                        v
                    }
                }
            }
        }
    }

    override fun nextRange(dimensions: Int): Range {
        val min = IntArray(dimensions)
        val max = IntArray(dimensions)

        var minV: Int
        var maxV: Int
        for (d in 0..<dimensions) {
            minV = nextIntInternal()
            maxV = nextIntInternal()
            min[d] = min(minV, maxV)
            max[d] = max(minV, maxV)
        }
        return IntTestRange(min, max)
    }

    override fun newRangeField(r: Range): IntRange {
        return IntRange(FIELD_NAME, (r as IntTestRange).min, r.max)
    }

    override fun newIntersectsQuery(r: Range): Query {
        return IntRange.newIntersectsQuery(FIELD_NAME, (r as IntTestRange).min, r.max)
    }

    override fun newContainsQuery(r: Range): Query {
        return IntRange.newContainsQuery(FIELD_NAME, (r as IntTestRange).min, r.max)
    }

    override fun newWithinQuery(r: Range): Query {
        return IntRange.newWithinQuery(FIELD_NAME, (r as IntTestRange).min, r.max)
    }

    override fun newCrossesQuery(r: Range): Query {
        return IntRange.newCrossesQuery(FIELD_NAME, (r as IntTestRange).min, r.max)
    }

    /** Basic test */
    @Test
    fun testBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // intersects (within)
        var document = Document()
        document.add(IntRange(FIELD_NAME, intArrayOf(-10, -10), intArrayOf(9, 10)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(IntRange(FIELD_NAME, intArrayOf(10, -10), intArrayOf(20, 10)))
        writer.addDocument(document)

        // intersects (contains / crosses)
        document = Document()
        document.add(IntRange(FIELD_NAME, intArrayOf(-20, -20), intArrayOf(30, 30)))
        writer.addDocument(document)

        // intersects (within)
        document = Document()
        document.add(IntRange(FIELD_NAME, intArrayOf(-11, -11), intArrayOf(1, 11)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(IntRange(FIELD_NAME, intArrayOf(12, 1), intArrayOf(15, 29)))
        writer.addDocument(document)

        // disjoint
        document = Document()
        document.add(IntRange(FIELD_NAME, intArrayOf(-122, 1), intArrayOf(-115, 29)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(IntRange(FIELD_NAME, intArrayOf(Int.MIN_VALUE, 1), intArrayOf(-11, 29)))
        writer.addDocument(document)

        // equal (within, contains, intersects)
        document = Document()
        document.add(IntRange(FIELD_NAME, intArrayOf(-11, -15), intArrayOf(15, 20)))
        writer.addDocument(document)

        // search
        val reader = writer.reader
        val searcher = newSearcher(reader)
        assertEquals(
            7,
            searcher.count(IntRange.newIntersectsQuery(FIELD_NAME, intArrayOf(-11, -15), intArrayOf(15, 20))),
        )
        assertEquals(
            3,
            searcher.count(IntRange.newWithinQuery(FIELD_NAME, intArrayOf(-11, -15), intArrayOf(15, 20))),
        )
        assertEquals(
            2,
            searcher.count(IntRange.newContainsQuery(FIELD_NAME, intArrayOf(-11, -15), intArrayOf(15, 20))),
        )
        assertEquals(
            4,
            searcher.count(IntRange.newCrossesQuery(FIELD_NAME, intArrayOf(-11, -15), intArrayOf(15, 20))),
        )

        reader.close()
        writer.close()
        dir.close()
    }

    /** IntRange test class implementation - use to validate IntRange */
    protected class IntTestRange(min: IntArray, max: IntArray) : Range() {
        var min: IntArray = min
        var max: IntArray = max

        init {
            assert(min.isNotEmpty() && max.isNotEmpty()) { "test box: min/max cannot be null or empty" }
            assert(min.size == max.size) { "test box: min/max length do not agree" }
        }

        override fun numDimensions(): Int {
            return min.size
        }

        override fun getMin(dim: Int): Int {
            return min[dim]
        }

        override fun setMin(dim: Int, value: Any) {
            val v = value as Int
            if (min[dim] < v) {
                max[dim] = v
            } else {
                min[dim] = v
            }
        }

        override fun getMax(dim: Int): Int {
            return max[dim]
        }

        override fun setMax(dim: Int, value: Any) {
            val v = value as Int
            if (max[dim] > v) {
                min[dim] = v
            } else {
                max[dim] = v
            }
        }

        override fun isEqual(other: Range): Boolean {
            val o = other as IntTestRange
            return min.contentEquals(o.min) && max.contentEquals(o.max)
        }

        override fun isDisjoint(o: Range): Boolean {
            val other = o as IntTestRange
            for (d in 0..<this.min.size) {
                if (this.min[d] > other.max[d] || this.max[d] < other.min[d]) {
                    // disjoint:
                    return true
                }
            }
            return false
        }

        override fun isWithin(o: Range): Boolean {
            val other = o as IntTestRange
            for (d in 0..<this.min.size) {
                if ((this.min[d] >= other.min[d] && this.max[d] <= other.max[d]) == false) {
                    // not within:
                    return false
                }
            }
            return true
        }

        override fun contains(o: Range): Boolean {
            val other = o as IntTestRange
            for (d in 0..<this.min.size) {
                if ((this.min[d] <= other.min[d] && this.max[d] >= other.max[d]) == false) {
                    // not contains:
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
