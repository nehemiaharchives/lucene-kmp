package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.BeforeTest
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class TestIntHashSet : LuceneTestCase() {
    private val EMPTY_KEY = 0

    private val keyE = 0
    private val key1 = cast(1)
    private val key2 = cast(2)
    private val key3 = cast(3)
    private val key4 = cast(4)

    private lateinit var set: IntHashSet

    private fun cast(v: Int): Int {
        return v
    }

    @BeforeTest
    fun initialize() {
        set = IntHashSet()
    }

    @Test
    fun testAddAllViaInterface() {
        set.addAll(key1, key2)
        val iface = IntHashSet()
        iface.clear()
        iface.addAll(set)
        assertContentEquals(setOf(key1, key2).toIntArray().sortedArray(), iface.toArray().sortedArray())
    }

    @Test
    fun testIndexMethods() {
        set.add(keyE)
        set.add(key1)

        assertTrue(set.indexOf(keyE) >= 0)
        assertTrue(set.indexOf(key1) >= 0)
        assertTrue(set.indexOf(key2) < 0)

        assertTrue(set.indexExists(set.indexOf(keyE)))
        assertTrue(set.indexExists(set.indexOf(key1)))
        assertFalse(set.indexExists(set.indexOf(key2)))

        assertEquals(keyE, set.indexGet(set.indexOf(keyE)))
        assertEquals(key1, set.indexGet(set.indexOf(key1)))
        expectThrows(AssertionError::class) { set.indexGet(set.indexOf(key2)) }

        assertEquals(keyE, set.indexReplace(set.indexOf(keyE), keyE))
        assertEquals(key1, set.indexReplace(set.indexOf(key1), key1))

        set.indexInsert(set.indexOf(key2), key2)
        assertEquals(key2, set.indexGet(set.indexOf(key2)))
        assertEquals(3, set.size())

        set.indexRemove(set.indexOf(keyE))
        assertEquals(2, set.size())
        set.indexRemove(set.indexOf(key2))
        assertEquals(1, set.size())
        assertTrue(set.indexOf(keyE) < 0)
        assertTrue(set.indexOf(key1) >= 0)
        assertTrue(set.indexOf(key2) < 0)
    }

    @Test
    fun testCursorIndexIsValid() {
        set.add(keyE)
        set.add(key1)
        set.add(key2)

        for (c in set) {
            val cursor = c!!
            assertTrue(set.indexExists(cursor.index))
            assertEquals(cursor.value, set.indexGet(cursor.index))
        }
    }

    @Test
    fun testEmptyKey() {
        val set = IntHashSet()
        var b = set.add(EMPTY_KEY)
        assertTrue(b)
        assertFalse(set.add(EMPTY_KEY))
        assertEquals(1, set.size())
        assertFalse(set.isEmpty)
        assertContentEquals(intArrayOf(EMPTY_KEY).sortedArray(), set.toArray().sortedArray())
        assertTrue(set.contains(EMPTY_KEY))
        var index = set.indexOf(EMPTY_KEY)
        assertTrue(set.indexExists(index))
        assertEquals(EMPTY_KEY, set.indexGet(index))
        assertEquals(EMPTY_KEY, set.indexReplace(index, EMPTY_KEY))
        if (random().nextBoolean()) {
            b = set.remove(EMPTY_KEY)
            assertTrue(b)
        } else {
            set.indexRemove(index)
        }
        assertEquals(0, set.size())
        assertTrue(set.isEmpty)
        assertTrue(set.toArray().isEmpty())
        assertFalse(set.contains(EMPTY_KEY))
        index = set.indexOf(EMPTY_KEY)
        assertFalse(set.indexExists(index))
        set.indexInsert(index, EMPTY_KEY)
        set.add(key1)
        assertEquals(2, set.size())
        assertTrue(set.contains(EMPTY_KEY))
        index = set.indexOf(EMPTY_KEY)
        assertTrue(set.indexExists(index))
        assertEquals(EMPTY_KEY, set.indexGet(index))
    }

    @Test
    fun testEnsureCapacity() {
        val set = IntHashSet(0)
        val max = if (rarely()) 0 else randomIntBetween(0, 250)
        for (i in 0 until max) {
            set.add(cast(i))
        }
        val additions = randomIntBetween(max, max + 5000)
        set.ensureCapacity(additions + set.size())
        val before = set.keys
        for (i in 0 until additions) {
            set.add(cast(i))
        }
        assertTrue(before === set.keys)
    }

    @Test
    fun testInitiallyEmpty() {
        assertEquals(0, set.size())
    }

    @Test
    fun testAdd() {
        assertTrue(set.add(key1))
        assertFalse(set.add(key1))
        assertEquals(1, set.size())
    }

    @Test
    fun testAdd2() {
        set.addAll(key1, key1)
        assertEquals(1, set.size())
        assertEquals(1, set.addAll(key1, key2))
        assertEquals(2, set.size())
    }

    @Test
    fun testAddVarArgs() {
        set.addAll(*asArray(0, 1, 2, 1, 0))
        assertEquals(3, set.size())
        assertSortedListEquals(set.toArray(), asArray(0, 1, 2))
    }

    @Test
    fun testAddAll() {
        val set2 = IntHashSet()
        set2.addAll(*asArray(1, 2))
        set.addAll(*asArray(0, 1))
        assertEquals(1, set.addAll(set2))
        assertEquals(0, set.addAll(set2))
        assertEquals(3, set.size())
        assertSortedListEquals(set.toArray(), asArray(0, 1, 2))
    }

    @Test
    fun testRemove() {
        set.addAll(*asArray(0, 1, 2, 3, 4))
        assertTrue(set.remove(key2))
        assertFalse(set.remove(key2))
        assertEquals(4, set.size())
        assertSortedListEquals(set.toArray(), asArray(0, 1, 3, 4))
    }

    @Test
    fun testInitialCapacityAndGrowth() {
        for (i in 0 until 256) {
            val set = IntHashSet(i)
            for (j in 0 until i) {
                set.add(cast(j))
            }
            assertEquals(i, set.size())
        }
    }

    @Test
    fun testBug_HPPC73_FullCapacityGet() {
        val elements = 0x7F
        set = IntHashSet(elements, 0.99)
        val before = set.keys
        for (i in 1..elements) {
            set.add(cast(i))
        }
        val outOfSet = cast(elements + 1)
        set.remove(outOfSet)
        assertFalse(set.contains(outOfSet))
        assertTrue(before === set.keys)
        assertFalse(set.add(key1))
        assertTrue(before === set.keys)
        set.remove(key1)
        assertTrue(before === set.keys)
        set.add(key1)
        set.add(outOfSet)
        assertTrue(before !== set.keys)
    }

    @Test
    fun testRemoveAllFromLookupContainer() {
        set.addAll(*asArray(0, 1, 2, 3, 4))
        val list2 = IntHashSet()
        list2.addAll(*asArray(1, 3, 5))
        assertEquals(2, set.removeAll(list2))
        assertEquals(3, set.size())
        assertSortedListEquals(set.toArray(), asArray(0, 2, 4))
    }

    @Test
    fun testClear() {
        set.addAll(*asArray(1, 2, 3))
        set.clear()
        assertEquals(0, set.size())
    }

    @Test
    fun testRelease() {
        set.addAll(*asArray(1, 2, 3))
        set.release()
        assertEquals(0, set.size())
        set.addAll(*asArray(1, 2, 3))
        assertEquals(3, set.size())
    }

    @Test
    fun testIterable() {
        set.addAll(*asArray(1, 2, 2, 3, 4))
        set.remove(key2)
        assertEquals(3, set.size())
        var count = 0
        for (cursor in set) {
            val cur = cursor!!
            count++
            assertTrue(set.contains(cur.value))
        }
        assertEquals(count, set.size())
        set.clear()
        assertFalse(set.iterator().hasNext())
    }

    @Test
    fun testAgainstHashSet() {
        val other = HashSet<Int>()
        for (size in 1000 until 20000 step 4000) {
            other.clear()
            set.clear()
            for (round in 0 until size * 20) {
                var key = cast(random().nextInt(size))
                if (random().nextInt(50) == 0) {
                    key = 0
                }
                if (random().nextBoolean()) {
                    if (random().nextBoolean()) {
                        val index = set.indexOf(key)
                        if (set.indexExists(index)) {
                            set.indexReplace(index, key)
                        } else {
                            set.indexInsert(index, key)
                        }
                    } else {
                        set.add(key)
                    }
                    other.add(key)
                    assertTrue(set.contains(key))
                    assertTrue(set.indexExists(set.indexOf(key)))
                } else {
                    assertEquals(other.contains(key), set.contains(key))
                    val removed = if (set.contains(key) && random().nextBoolean()) {
                        set.indexRemove(set.indexOf(key))
                        true
                    } else {
                        set.remove(key)
                    }
                    assertEquals(other.remove(key), removed)
                }
                assertEquals(other.size, set.size())
            }
        }
    }

    @Test
    fun testHashCodeEquals() {
        val l0 = IntHashSet()
        assertEquals(0, l0.hashCode())
        assertEquals(IntHashSet(), l0)
        val l1 = IntHashSet.from(key1, key2, key3)
        val l2 = IntHashSet.from(key1, key2)
        l2.add(key3)
        assertEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1, l2)
    }

    @Test
    fun testClone() {
        set.addAll(*asArray(1, 2, 3))
        val cloned = set.clone()
        cloned.remove(key1)
        assertSortedListEquals(set.toArray(), asArray(1, 2, 3))
        assertSortedListEquals(cloned.toArray(), asArray(2, 3))
    }

    @Test
    fun testEqualsSameClass() {
        val l1 = IntHashSet.from(key1, key2, key3)
        val l2 = IntHashSet.from(key1, key2, key3)
        val l3 = IntHashSet.from(key1, key2, key4)
        assertEquals(l1, l2)
        assertEquals(l1.hashCode(), l2.hashCode())
        assertFalse(l1 == l3)
    }


    private fun rarely(): Boolean {
        return org.gnit.lucenekmp.tests.util.TestUtil.rarely(random())
    }

    private fun randomIntBetween(min: Int, max: Int): Int {
        return min + random().nextInt(max + 1 - min)
    }

    private fun asArray(vararg elements: Int): IntArray {
        return elements
    }

    private fun assertSortedListEquals(array: IntArray, elements: IntArray) {
        assertEquals(elements.size, array.size)
        array.sort()
        assertContentEquals(elements, array)
    }
}
