package org.gnit.lucenekmp.internal.hppc

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.tests.util.LuceneTestCase

/**
 * Tests for [CharObjectHashMap].
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.CharObjectHashMapTest
 *
 * github: https://github.com/carrotsearch/hppc release: 0.9.0
 */
class TestCharObjectHashMap : LuceneTestCase() {
    /* Ready to use key values. */
    private val keyE: Char = '\u0000'
    private val key1: Char = cast(1)
    private val key2: Char = cast(2)
    private val key3: Char = cast(3)
    private val key4: Char = cast(4)

    /** Convert to target type from an integer used to test stuff. */
    private fun cast(v: Int): Char {
        return ('a'.code + v).toChar()
    }

    /** Create a new array of a given type and copy the arguments to this array. */
    private fun newArray(vararg elements: Char): CharArray {
        return elements
    }

    private fun randomIntBetween(min: Int, max: Int): Int {
        return min + random().nextInt(max + 1 - min)
    }

    /** Check if the array's content is identical to a given sequence of elements. */
    private fun assertSortedListEquals(array: CharArray, vararg elements: Char) {
        assertEquals(elements.size, array.size)
        array.sort()
        val sortedElements = elements.copyOf()
        sortedElements.sort()
        assertContentEquals(sortedElements, array)
    }

    private val value0 = vcast(0)
    private val value1 = vcast(1)
    private val value2 = vcast(2)
    private val value3 = vcast(3)
    private val value4 = vcast(4)

    /** Per-test fresh initialized instance. */
    private var map: CharObjectHashMap<Any?> = newInstance()

    private fun newInstance(): CharObjectHashMap<Any?> {
        return CharObjectHashMap()
    }

    @AfterTest
    fun checkEmptySlotsUninitialized() {
        if (map != null) {
            var occupied = 0
            for (i in 0..map.mask) {
                if (map.keys!![i] == '\u0000') {
                } else {
                    occupied++
                }
            }
            assertEquals(occupied, map.assigned)

            if (!map.hasEmptyKey) {
            }
        }
    }

    /** Convert to target type from an integer used to test stuff. */
    private fun vcast(value: Int): Int {
        return value
    }

    /** Create a new array of a given type and copy the arguments to this array. */
    private fun newvArray(vararg elements: Any?): Array<Any?> {
        return elements as Array<Any?>
    }

    private fun assertSameMap(c1: CharObjectHashMap<Any?>, c2: CharObjectHashMap<Any?>) {
        assertEquals(c1.size(), c2.size())

        for (entry in c1) {
            assertTrue(c2.containsKey(entry.key))
            assertEquals(entry.value, c2[entry.key])
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testEnsureCapacity() {
        val expands = AtomicInt(0)
        val map =
            object : CharObjectHashMap<Any?>(0) {
                override fun allocateBuffers(arraySize: Int) {
                    super.allocateBuffers(arraySize)
                    expands.incrementAndFetch()
                }
            }

        val max = if (rarely()) 0 else randomIntBetween(0, 250)
        for (i in 0 until max) {
            map.put(cast(i), value0)
        }

        val additions = randomIntBetween(max, max + 5000)
        map.ensureCapacity(additions + map.size())
        val before = expands.get()
        for (i in 0 until additions) {
            map.put(cast(i), value0)
        }
        assertEquals(before, expands.get())
    }

    @Test
    fun testCursorIndexIsValid() {
        map.put(keyE, value1)
        map.put(key1, value2)
        map.put(key2, value3)

        for (c in map) {
            assertTrue(map.indexExists(c.index))
            assertEquals(c.value, map.indexGet(c.index))
        }
    }

    @Test
    fun testIndexMethods() {
        map.put(keyE, value1)
        map.put(key1, value2)

        assertTrue(map.indexOf(keyE) >= 0)
        assertTrue(map.indexOf(key1) >= 0)
        assertTrue(map.indexOf(key2) < 0)

        assertTrue(map.indexExists(map.indexOf(keyE)))
        assertTrue(map.indexExists(map.indexOf(key1)))
        assertFalse(map.indexExists(map.indexOf(key2)))

        assertEquals(value1, map.indexGet(map.indexOf(keyE)))
        assertEquals(value2, map.indexGet(map.indexOf(key1)))

        expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            map.indexGet(map.indexOf(key2))
        }

        assertEquals(value1, map.indexReplace(map.indexOf(keyE), value3))
        assertEquals(value2, map.indexReplace(map.indexOf(key1), value4))
        assertEquals(value3, map.indexGet(map.indexOf(keyE)))
        assertEquals(value4, map.indexGet(map.indexOf(key1)))

        map.indexInsert(map.indexOf(key2), key2, value1)
        assertEquals(value1, map.indexGet(map.indexOf(key2)))
        assertEquals(3, map.size())

        assertEquals(value3, map.indexRemove(map.indexOf(keyE)))
        assertEquals(2, map.size())
        assertEquals(value1, map.indexRemove(map.indexOf(key2)))
        assertEquals(1, map.size())
        assertTrue(map.indexOf(keyE) < 0)
        assertTrue(map.indexOf(key1) >= 0)
        assertTrue(map.indexOf(key2) < 0)
    }

    @Test
    fun testCloningConstructor() {
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)

        assertSameMap(map, CharObjectHashMap(map))
    }

    @Test
    fun testFromArrays() {
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)

        val map2 = CharObjectHashMap.from(newArray(key1, key2, key3), newvArray(value1, value2, value3))
        assertSameMap(map, map2)
    }

    @Test
    fun testGetOrDefault() {
        map.put(key2, value2)
        assertTrue(map.containsKey(key2))

        map.put(key1, value1)
        assertEquals(value1, map.getOrDefault(key1, value3))
        assertEquals(value3, map.getOrDefault(key3, value3))
        map.remove(key1)
        assertEquals(value3, map.getOrDefault(key1, value3))
    }

    @Test
    fun testPut() {
        map.put(key1, value1)

        assertTrue(map.containsKey(key1))
        assertEquals(value1, map[key1])
    }

    @Test
    fun testNullValue() {
        map.put(key1, null)

        assertTrue(map.containsKey(key1))
        assertNull(map[key1])
    }

    @Test
    fun testPutOverExistingKey() {
        map.put(key1, value1)
        assertEquals(value1, map.put(key1, value3))
        assertEquals(value3, map[key1])

        assertEquals(value3, map.put(key1, null))
        assertTrue(map.containsKey(key1))
        assertNull(map[key1])

        assertNull(map.put(key1, value1))
        assertEquals(value1, map[key1])
    }

    @Test
    fun testPutWithExpansions() {
        val count = 10000
        val rnd = kotlin.random.Random(random().nextLong())
        val values = HashSet<Any?>()

        for (i in 0 until count) {
            val v = rnd.nextInt()
            val hadKey = values.contains(cast(v))
            values.add(cast(v))

            assertEquals(hadKey, map.containsKey(cast(v)))
            map.put(cast(v), vcast(v))
            assertEquals(values.size, map.size())
        }
        assertEquals(values.size, map.size())
    }

    @Test
    fun testPutAll() {
        map.put(key1, value1)
        map.put(key2, value1)

        val map2 = newInstance()
        map2.put(key2, value2)
        map2.put(keyE, value1)

        assertEquals(1, map.putAll(map2))

        assertEquals(value2, map[key2])
        assertEquals(value1, map[keyE])
        assertEquals(3, map.size())
    }

    @Test
    fun testPutIfAbsent() {
        assertTrue(map.putIfAbsent(key1, value1))
        assertFalse(map.putIfAbsent(key1, value2))
        assertEquals(value1, map[key1])
    }

    @Test
    fun testRemove() {
        map.put(key1, value1)
        assertEquals(value1, map.remove(key1))
        assertEquals(null, map.remove(key1))
        assertEquals(0, map.size())

        assertEquals(0, map.assigned)
    }

    @Test
    fun testEmptyKey() {
        val empty: Char = '\u0000'

        map.put(empty, value1)
        assertEquals(1, map.size())
        assertFalse(map.isEmpty)
        assertEquals(value1, map[empty])
        assertEquals(value1, map.getOrDefault(empty, value2))
        assertTrue(map.iterator().hasNext())
        assertEquals(empty, map.iterator().next().key)
        assertEquals(value1, map.iterator().next().value)

        map.remove(empty)
        assertEquals(null, map[empty])
        assertEquals(0, map.size())

        map.put(empty, null)
        assertEquals(1, map.size())
        assertTrue(map.containsKey(empty))
        assertNull(map[empty])

        map.remove(empty)
        assertEquals(0, map.size())
        assertFalse(map.containsKey(empty))
        assertNull(map[empty])

        assertEquals(null, map.put(empty, value1))
        assertEquals(value1, map.put(empty, value2))
        map.clear()
        assertFalse(map.indexExists(map.indexOf(empty)))
        assertEquals(null, map.put(empty, value1))
        map.clear()
        assertEquals(null, map.remove(empty))
    }

    @Test
    fun testMapKeySet() {
        map.put(key1, value3)
        map.put(key2, value2)
        map.put(key3, value1)

        assertSortedListEquals(map.keys().toArray(), key1, key2, key3)
    }

    @Test
    fun testMapKeySetIterator() {
        map.put(key1, value3)
        map.put(key2, value2)
        map.put(key3, value1)

        var counted = 0
        for (c in map.keys()) {
            assertEquals(map.keys!![c.index], c.value)
            counted++
        }
        assertEquals(counted, map.size())
    }

    @Test
    fun testClear() {
        map.put(key1, value1)
        map.put(key2, value1)
        map.clear()
        assertEquals(0, map.size())

        assertEquals(0, map.assigned)

        assertEquals(null, map.put(key1, value1))
        assertEquals(null, map.remove(key2))
        map.clear()

        testPutWithExpansions()
    }

    @Test
    fun testRelease() {
        map.put(key1, value1)
        map.put(key2, value1)
        map.release()
        assertEquals(0, map.size())

        assertEquals(0, map.assigned)

        testPutWithExpansions()
    }

    @Test
    fun testIterable() {
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value3)
        map.remove(key2)

        var count = 0
        for (cursor in map) {
            count++
            assertTrue(map.containsKey(cursor.key))
            assertEquals(cursor.value, map[cursor.key])

            assertEquals(cursor.value, map.values!![cursor.index])
            assertEquals(cursor.key, map.keys!![cursor.index])
        }
        assertEquals(count, map.size())

        map.clear()
        assertFalse(map.iterator().hasNext())
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testBug_HPPC73_FullCapacityGet() {
        val reallocations = AtomicInt(0)
        val elements = 0x7F
        map =
            object : CharObjectHashMap<Any?>(elements, 1.0) {
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
            map.put(cast(i), value1)
        }

        val outOfSet = cast(elements + 1)
        map.remove(outOfSet)
        assertFalse(map.containsKey(outOfSet))
        assertEquals(reallocationsBefore, reallocations.get())

        map.put(key1, value2)
        assertEquals(reallocationsBefore, reallocations.get())

        map.remove(key1)
        assertEquals(reallocationsBefore, reallocations.get())
        map.put(key1, value2)

        map.put(outOfSet, value1)
        assertEquals(reallocationsBefore + 1, reallocations.get())
    }

    @Test
    fun testHashCodeEquals() {
        val l0 = newInstance()
        assertEquals(0, l0.hashCode())
        assertEquals(l0, newInstance())

        val l1 = CharObjectHashMap.from(newArray(key1, key2, key3), newvArray(value1, value2, value3))
        val l2 = CharObjectHashMap.from(newArray(key2, key1, key3), newvArray(value2, value1, value3))
        val l3 = CharObjectHashMap.from(newArray(key1, key2), newvArray(value2, value1))

        assertEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1, l2)

        assertFalse(l1 == l3)
        assertFalse(l2 == l3)
    }

    @Test
    fun testBug_HPPC37() {
        val l1 = CharObjectHashMap.from(newArray(key1), newvArray(value1))
        val l2 = CharObjectHashMap.from(newArray(key2), newvArray(value1))

        assertFalse(l1 == l2)
        assertFalse(l2 == l1)
    }

    /** Runs random insertions/deletions/clearing and compares the results against [HashMap]. */
    @Test
    fun testAgainstHashMap() {
        val rnd = random()
        val other = HashMap<Char, Any?>()

        for (size in 1000 until 20000 step 4000) {
            other.clear()
            map.clear()

            for (round in 0 until size * 20) {
                var key = cast(rnd.nextInt(size))
                if (rnd.nextInt(50) == 0) {
                    key = '\u0000'
                }

                val value = vcast(rnd.nextInt())

                if (rnd.nextBoolean()) {
                    val previousValue: Any?
                    if (rnd.nextBoolean()) {
                        val index = map.indexOf(key)
                        if (map.indexExists(index)) {
                            previousValue = map.indexReplace(index, value)
                        } else {
                            map.indexInsert(index, key, value)
                            previousValue = null
                        }
                    } else {
                        previousValue = map.put(key, value)
                    }
                    assertEquals(other.put(key, value), previousValue)

                    assertEquals(value, map[key])
                    assertEquals(value, map.indexGet(map.indexOf(key)))
                    assertTrue(map.containsKey(key))
                    assertTrue(map.indexExists(map.indexOf(key)))
                } else {
                    assertEquals(other.containsKey(key), map.containsKey(key))
                    val previousValue = if (map.containsKey(key) && rnd.nextBoolean()) {
                        map.indexRemove(map.indexOf(key))
                    } else {
                        map.remove(key)
                    }
                    assertEquals(other.remove(key), previousValue)
                }

                assertEquals(other.size, map.size())
            }
        }
    }

    @Test
    fun testClone() {
        this.map.put(key1, value1)
        this.map.put(key2, value2)
        this.map.put(key3, value3)

        val cloned = map.clone()
        cloned.remove(key1)

        assertSortedListEquals(map.keys().toArray(), key1, key2, key3)
        assertSortedListEquals(cloned.keys().toArray(), key2, key3)
    }

    @Test
    fun testMapValues() {
        map.put(key1, value3)
        map.put(key2, value2)
        map.put(key3, value1)

        val first = mutableListOf<Any?>()
        for (c in map.values()) {
            first.add(c!!.value)
        }
        assertEquals(3, first.size)
        assertTrue(first.contains(value1))
        assertTrue(first.contains(value2))
        assertTrue(first.contains(value3))

        map.clear()
        map.put(key1, value1)
        map.put(key2, value2)
        map.put(key3, value2)
        val second = mutableListOf<Any?>()
        for (c in map.values()) {
            second.add(c!!.value)
        }
        assertEquals(3, second.size)
        assertTrue(second.contains(value1))
        assertEquals(2, second.count { it == value2 })
    }

    @Test
    fun testMapValuesIterator() {
        map.put(key1, value3)
        map.put(key2, value2)
        map.put(key3, value1)

        var counted = 0
        for (c in map.values()) {
            val cursor = c!!
            assertEquals(map.values!![cursor.index], cursor.value)
            counted++
        }
        assertEquals(counted, map.size())
    }

    @Test
    fun testEqualsSameClass() {
        val l1 = newInstance()
        l1.put(key1, value0)
        l1.put(key2, value1)
        l1.put(key3, value2)

        val l2 = CharObjectHashMap<Any?>(l1)
        l2.putAll(l1)

        val l3 = CharObjectHashMap<Any?>(l2)
        l3.putAll(l2)
        l3.put(key4, value0)

        assertEquals(l2, l1)
        assertEquals(l2.hashCode(), l1.hashCode())
        assertNotEquals(l1, l3)
    }

    @Test
    fun testEqualsSubClass() {
        class Sub : CharObjectHashMap<Any?>()

        val l1 = newInstance()
        l1.put(key1, value0)
        l1.put(key2, value1)
        l1.put(key3, value2)

        val l2 = Sub()
        l2.putAll(l1)
        l2.put(key4, value3)

        val l3 = Sub()
        l3.putAll(l2)

        assertNotEquals(l1, l2)
        assertEquals(l3.hashCode(), l2.hashCode())
        assertEquals(l3, l2)
    }

    private fun rarely(): Boolean = org.gnit.lucenekmp.tests.util.TestUtil.rarely(random())
}
