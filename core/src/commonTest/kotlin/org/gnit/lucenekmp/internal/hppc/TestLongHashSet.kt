package org.gnit.lucenekmp.internal.hppc

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.get

class TestLongHashSet : LuceneTestCase() {
    private val keyE: Long = 0
    private val key1: Long = cast(1)
    private val key2: Long = cast(2)
    private val key3: Long = cast(3)
    private val key4: Long = cast(4)

    private lateinit var set: LongHashSet

    private fun cast(v: Int): Long = v.toLong()

    @BeforeTest
    fun initialize() {
        set = LongHashSet()
    }

    @Test
    fun testAddAllViaInterface() {
        set.addAll(key1, key2)
        val iface = LongHashSet()
        iface.clear()
        iface.addAll(set)
        assertEquals(setOf(key1, key2), asSet(*iface.toArray()))
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

        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            set.indexGet(set.indexOf(key2))
        }

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
            assertTrue(set.indexExists(c.index))
            assertEquals(c.value, set.indexGet(c.index))
        }
    }

    @Test
    fun testEmptyKey() {
        val set = LongHashSet()

        var b = set.add(EMPTY_KEY)

        assertTrue(b)
        assertFalse(set.add(EMPTY_KEY))
        assertEquals(1, set.size())
        assertFalse(set.isEmpty)
        assertEquals(setOf(EMPTY_KEY), asSet(*set.toArray()))
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
        assertTrue(asSet(*set.toArray()).isEmpty())
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

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testEnsureCapacity() {
        val expands = AtomicInt(0)
        val set = object : LongHashSet(0) {
            override fun allocateBuffers(arraySize: Int) {
                super.allocateBuffers(arraySize)
                expands.incrementAndFetch()
            }
        }

        val max = if (rarely()) 0 else randomIntBetween(0, 250)
        for (i in 0 until max) {
            set.add(cast(i))
        }

        val additions = randomIntBetween(max, max + 5000)
        set.ensureCapacity(additions + set.size())
        val before = expands.get()
        for (i in 0 until additions) {
            set.add(cast(i))
        }
        assertEquals(before, expands.get())
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
        val set2 = LongHashSet()
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
            val set = LongHashSet(i)
            for (j in 0 until i) {
                set.add(cast(j))
            }
            assertEquals(i, set.size())
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testBug_HPPC73_FullCapacityGet() {
        val reallocations = AtomicInt(0)
        val elements = 0x7F
        set = object : LongHashSet(elements, 1.0) {
            override fun verifyLoadFactor(loadFactor: Double): Double {
                return loadFactor
            }
            override fun allocateBuffers(arraySize: Int) {
                super.allocateBuffers(arraySize)
                reallocations.incrementAndFetch()
            }
        }

        val reallocationsBefore = reallocations.get()
        assertEquals(reallocationsBefore, 1)
        for (i in 1..elements) {
            set.add(cast(i))
        }

        val outOfSet = cast(elements + 1)
        set.remove(outOfSet)
        assertFalse(set.contains(outOfSet))
        assertEquals(reallocationsBefore, reallocations.get())

        assertFalse(set.add(key1))
        assertEquals(reallocationsBefore, reallocations.get())

        set.remove(key1)
        assertEquals(reallocationsBefore, reallocations.get())
        set.add(key1)

        set.add(outOfSet)
        assertEquals(reallocationsBefore + 1, reallocations.get())
    }

    @Test
    fun testRemoveAllFromLookupContainer() {
        set.addAll(*asArray(0, 1, 2, 3, 4))
        val list2 = LongHashSet()
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
            count++
            assertTrue(set.contains(cursor.value))
        }
        assertEquals(count, set.size())
        set.clear()
        assertFalse(set.iterator().hasNext())
    }

    @Test
    fun testAgainstHashSet() {
        val rnd = random()
        val other = HashSet<Long>()
        var localSet = set
        for (size in 1000 until 20000 step 4000) {
            other.clear()
            localSet.clear()
            for (round in 0 until size * 20) {
                var key = cast(rnd.nextInt(size))
                if (rnd.nextInt(50) == 0) {
                    key = 0L
                }
                if (rnd.nextBoolean()) {
                    if (rnd.nextBoolean()) {
                        val index = localSet.indexOf(key)
                        if (localSet.indexExists(index)) {
                            localSet.indexReplace(index, key)
                        } else {
                            localSet.indexInsert(index, key)
                        }
                    } else {
                        localSet.add(key)
                    }
                    other.add(key)
                    assertTrue(localSet.contains(key))
                    assertTrue(localSet.indexExists(localSet.indexOf(key)))
                } else {
                    assertEquals(other.contains(key), localSet.contains(key))
                    val removed = if (localSet.contains(key) && rnd.nextBoolean()) {
                        localSet.indexRemove(localSet.indexOf(key))
                        true
                    } else {
                        localSet.remove(key)
                    }
                    assertEquals(other.remove(key), removed)
                }
                assertEquals(other.size, localSet.size())
            }
        }
    }

    @Test
    fun testHashCodeEquals() {
        val l0 = LongHashSet()
        assertEquals(0, l0.hashCode())
        assertEquals(l0, LongHashSet())

        val l1 = LongHashSet.from(key1, key2, key3)
        val l2 = LongHashSet.from(key1, key2)
        l2.add(key3)

        assertEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1, l2)
    }

    @Test
    fun testClone() {
        this.set.addAll(*asArray(1, 2, 3))
        val cloned = set.clone()
        cloned.remove(key1)
        assertSortedListEquals(set.toArray(), asArray(1, 2, 3))
        assertSortedListEquals(cloned.toArray(), asArray(2, 3))
    }

    @Test
    fun testEqualsSameClass() {
        val l1 = LongHashSet.from(key1, key2, key3)
        val l2 = LongHashSet.from(key1, key2, key3)
        val l3 = LongHashSet.from(key1, key2, key4)
        assertEquals(l1, l2)
        assertEquals(l1.hashCode(), l2.hashCode())
        assertFalse(l1 == l3)
    }

    @Test
    fun testEqualsSubClass() {
        class Sub : LongHashSet()
        val l1 = LongHashSet.from(key1, key2, key3)
        val l2 = Sub()
        val l3 = Sub()
        l2.addAll(l1)
        l3.addAll(l1)
        assertEquals(l2, l3)
        assertFalse(l1 == l2)
    }

    private fun randomIntBetween(min: Int, max: Int): Int = TestUtil.nextInt(random(), min, max)

    private fun asSet(vararg elements: Long): Set<Long> = elements.toSet()

    private fun asArray(vararg elements: Int): LongArray = LongArray(elements.size) { elements[it].toLong() }
    private fun asArray(vararg elements: Long): LongArray = elements

    private fun assertSortedListEquals(array: LongArray, elements: LongArray) {
        assertEquals(elements.size, array.size)
        org.gnit.lucenekmp.jdkport.Arrays.sort(array)
        assertContentEquals(elements, array)
    }

    private fun rarely(): Boolean = TestUtil.rarely(random())

    companion object {
        private const val EMPTY_KEY: Long = 0L
    }
}
