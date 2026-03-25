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
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.LongRange
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.BaseRangeFieldQueryTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.assert
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/** Random testing for LongRange Queries. */
// See: https://issues.apache.org/jira/browse/SOLR-12028 Tests cannot remove files on Windows
// machines occasionally
class TestLongRangeFieldQueries : BaseRangeFieldQueryTestCase() {
    companion object {
        private const val FIELD_NAME = "longRangeField"
    }

    private fun nextLongInternal(): Long {
        return when (random().nextInt(5)) {
            0 -> Long.MIN_VALUE
            1 -> Long.MAX_VALUE
            else -> {
                val bpv = random().nextInt(64)
                when (bpv) {
                    64 -> random().nextLong()
                    else -> {
                        var v = TestUtil.nextLong(random(), 0, (1L shl bpv) - 1)
                        if (bpv > 0) {
                            // negative values sometimes
                            v -= 1L shl (bpv - 1)
                        }
                        v
                    }
                }
            }
        }
    }

    override fun nextRange(dimensions: Int): Range {
        val min = LongArray(dimensions)
        val max = LongArray(dimensions)

        var minV: Long
        var maxV: Long
        for (d in 0..<dimensions) {
            minV = nextLongInternal()
            maxV = nextLongInternal()
            min[d] = min(minV, maxV)
            max[d] = max(minV, maxV)
        }
        return LongTestRange(min, max)
    }

    override fun newRangeField(r: Range): LongRange {
        return LongRange(FIELD_NAME, (r as LongTestRange).min, r.max)
    }

    override fun newIntersectsQuery(r: Range): Query {
        return LongRange.newIntersectsQuery(FIELD_NAME, (r as LongTestRange).min, r.max)
    }

    override fun newContainsQuery(r: Range): Query {
        return LongRange.newContainsQuery(FIELD_NAME, (r as LongTestRange).min, r.max)
    }

    override fun newWithinQuery(r: Range): Query {
        return LongRange.newWithinQuery(FIELD_NAME, (r as LongTestRange).min, r.max)
    }

    override fun newCrossesQuery(r: Range): Query {
        return LongRange.newCrossesQuery(FIELD_NAME, (r as LongTestRange).min, r.max)
    }

    /** Basic test */
    @Test
    fun testBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // intersects (within)
        var document = Document()
        document.add(LongRange(FIELD_NAME, longArrayOf(-10, -10), longArrayOf(9, 10)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(LongRange(FIELD_NAME, longArrayOf(10, -10), longArrayOf(20, 10)))
        writer.addDocument(document)

        // intersects (contains, crosses)
        document = Document()
        document.add(LongRange(FIELD_NAME, longArrayOf(-20, -20), longArrayOf(30, 30)))
        writer.addDocument(document)

        // intersects (within)
        document = Document()
        document.add(LongRange(FIELD_NAME, longArrayOf(-11, -11), longArrayOf(1, 11)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(LongRange(FIELD_NAME, longArrayOf(12, 1), longArrayOf(15, 29)))
        writer.addDocument(document)

        // disjoint
        document = Document()
        document.add(LongRange(FIELD_NAME, longArrayOf(-122, 1), longArrayOf(-115, 29)))
        writer.addDocument(document)

        // intersects (crosses)
        document = Document()
        document.add(LongRange(FIELD_NAME, longArrayOf(Long.MIN_VALUE, 1), longArrayOf(-11, 29)))
        writer.addDocument(document)

        // equal (within, contains, intersects)
        document = Document()
        document.add(LongRange(FIELD_NAME, longArrayOf(-11, -15), longArrayOf(15, 20)))
        writer.addDocument(document)

        // search
        val reader: IndexReader = writer.reader
        val searcher = newSearcher(reader)
        assertEquals(
            7,
            searcher.count(
                LongRange.newIntersectsQuery(FIELD_NAME, longArrayOf(-11, -15), longArrayOf(15, 20)),
            ),
        )
        assertEquals(
            3,
            searcher.count(
                LongRange.newWithinQuery(FIELD_NAME, longArrayOf(-11, -15), longArrayOf(15, 20)),
            ),
        )
        assertEquals(
            2,
            searcher.count(
                LongRange.newContainsQuery(FIELD_NAME, longArrayOf(-11, -15), longArrayOf(15, 20)),
            ),
        )
        assertEquals(
            4,
            searcher.count(
                LongRange.newCrossesQuery(FIELD_NAME, longArrayOf(-11, -15), longArrayOf(15, 20)),
            ),
        )

        reader.close()
        writer.close()
        dir.close()
    }

    /** LongRange test class implementation - use to validate LongRange */
    private class LongTestRange(min: LongArray, max: LongArray) : Range() {
        var min: LongArray = min
        var max: LongArray = max

        init {
            assert(min.isNotEmpty() && max.isNotEmpty()) { "test box: min/max cannot be null or empty" }
            assert(min.size == max.size) { "test box: min/max length do not agree" }
        }

        override fun numDimensions(): Int {
            return min.size
        }

        override fun getMin(dim: Int): Long {
            return min[dim]
        }

        override fun setMin(dim: Int, value: Any) {
            val v = value as Long
            if (min[dim] < v) {
                max[dim] = v
            } else {
                min[dim] = v
            }
        }

        override fun getMax(dim: Int): Long {
            return max[dim]
        }

        override fun setMax(dim: Int, value: Any) {
            val v = value as Long
            if (max[dim] > v) {
                min[dim] = v
            } else {
                max[dim] = v
            }
        }

        override fun isEqual(other: Range): Boolean {
            val o = other as LongTestRange
            return min.contentEquals(o.min) && max.contentEquals(o.max)
        }

        override fun isDisjoint(o: Range): Boolean {
            val other = o as LongTestRange
            for (d in 0..<this.min.size) {
                if (this.min[d] > other.max[d] || this.max[d] < other.min[d]) {
                    // disjoint:
                    return true
                }
            }
            return false
        }

        override fun isWithin(o: Range): Boolean {
            val other = o as LongTestRange
            for (d in 0..<this.min.size) {
                if ((this.min[d] >= other.min[d] && this.max[d] <= other.max[d]) == false) {
                    // not within:
                    return false
                }
            }
            return true
        }

        override fun contains(o: Range): Boolean {
            val other = o as LongTestRange
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
