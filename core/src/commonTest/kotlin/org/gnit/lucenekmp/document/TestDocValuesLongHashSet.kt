package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

class TestDocValuesLongHashSet : LuceneTestCase() {
    private fun assertEquals(set1: MutableSet<Long>, longHashSet: DocValuesLongHashSet) {
        kotlin.test.assertEquals(set1.size.toLong(), longHashSet.size().toLong())

        val set2: MutableSet<Long> = longHashSet.stream().toMutableSet()
        kotlin.test.assertEquals(set1, set2)

        if (set1.isEmpty() == false) {
            val set3: MutableSet<Long> = HashSet(set1)
            val removed: Long = set3.iterator().next()
            while (true) {
                val next: Long = random().nextLong()
                if (next != removed && set3.add(next)) {
                    kotlin.test.assertFalse(longHashSet.contains(next))
                    break
                }
            }
            assertNotEquals(set3, longHashSet)
        }

        kotlin.test.assertTrue(set1.all { l: Long -> longHashSet.contains(l) })
    }

    private fun assertNotEquals(set1: MutableSet<Long>, longHashSet: DocValuesLongHashSet) {
        val set2: MutableSet<Long> = longHashSet.stream().toMutableSet()

        kotlin.test.assertNotEquals(set1, set2)

        val set3 = DocValuesLongHashSet(set1.map{ obj: Long -> obj }.sorted().toLongArray())

        kotlin.test.assertNotEquals(set2, set3.stream().toMutableSet())

        kotlin.test.assertFalse(set1.all { l: Long -> longHashSet.contains(l) })
    }

    @Test
    fun testEmpty() {
        val set1: MutableSet<Long> = HashSet()
        val set2 = DocValuesLongHashSet(longArrayOf())
        kotlin.test.assertEquals(0, set2.size().toLong())
        kotlin.test.assertEquals(Long.MAX_VALUE, set2.minValue)
        kotlin.test.assertEquals(Long.MIN_VALUE, set2.maxValue)
        assertEquals(set1, set2)
    }

    @Test
    fun testOneValue() {
        var set1: MutableSet<Long> = HashSet(mutableListOf(42L))
        var set2 = DocValuesLongHashSet(longArrayOf(42L))
        kotlin.test.assertEquals(1, set2.size().toLong())
        kotlin.test.assertEquals(42L, set2.minValue)
        kotlin.test.assertEquals(42L, set2.maxValue)
        assertEquals(set1, set2)

        set1 = hashSetOf(Long.MIN_VALUE)
        set2 = DocValuesLongHashSet(longArrayOf(Long.MIN_VALUE))
        kotlin.test.assertEquals(1, set2.size().toLong())
        kotlin.test.assertEquals(Long.MIN_VALUE, set2.minValue)
        kotlin.test.assertEquals(Long.MIN_VALUE, set2.maxValue)
        assertEquals(set1, set2)
    }

    @Test
    fun testTwoValues() {
        var set1: MutableSet<Long> = hashSetOf(42L, Long.MAX_VALUE)
        var set2 = DocValuesLongHashSet(longArrayOf(42L, Long.MAX_VALUE))
        kotlin.test.assertEquals(2, set2.size().toLong())
        kotlin.test.assertEquals(42, set2.minValue)
        kotlin.test.assertEquals(Long.MAX_VALUE, set2.maxValue)
        assertEquals(set1, set2)

        set1 = hashSetOf(Long.MIN_VALUE, 42L)
        set2 = DocValuesLongHashSet(longArrayOf(Long.MIN_VALUE, 42L))
        kotlin.test.assertEquals(2, set2.size().toLong())
        kotlin.test.assertEquals(Long.MIN_VALUE, set2.minValue)
        kotlin.test.assertEquals(42, set2.maxValue)
        assertEquals(set1, set2)
    }

    @Test
    fun testSameValue() {
        val set2 = DocValuesLongHashSet(longArrayOf(42L, 42L))
        kotlin.test.assertEquals(1, set2.size().toLong())
        kotlin.test.assertEquals(42L, set2.minValue)
        kotlin.test.assertEquals(42L, set2.maxValue)
    }

    @Test
    fun testSameMissingPlaceholder() {
        val set2 =
            DocValuesLongHashSet(longArrayOf(Long.MIN_VALUE, Long.MIN_VALUE))
        kotlin.test.assertEquals(1, set2.size().toLong())
        kotlin.test.assertEquals(Long.MIN_VALUE, set2.minValue)
        kotlin.test.assertEquals(Long.MIN_VALUE, set2.maxValue)
    }

    @Test
    fun testRandom() {
        val iters: Int = atLeast(10)
        for (iter in 0..<iters) {
            val values = LongArray(random().nextInt(1 shl random().nextInt(16)))
            for (i in values.indices) {
                if (i == 0 || random().nextInt(10) < 9) {
                    values[i] = random().nextLong()
                } else {
                    values[i] = values[random().nextInt(i)]
                }
            }
            if (values.isNotEmpty() && random().nextBoolean()) {
                values[values.size / 2] = Long.MIN_VALUE
            }
            val set1: MutableSet<Long> = values.toMutableSet()
            Arrays.sort(values)
            val set2 = DocValuesLongHashSet(values)
            assertEquals(set1, set2)
        }
    }
}
