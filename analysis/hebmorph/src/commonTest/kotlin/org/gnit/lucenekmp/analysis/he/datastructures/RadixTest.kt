package org.gnit.lucenekmp.analysis.he.datastructures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RadixTest {
    @Test
    fun DoesAddNodesCorrectlyWithReferenceTypes() {
        val d = DictRadix<UuidObject>()
        doAddNodesTest(d, GuidGenerator())
        doDoubleAddTest(d, GuidGenerator())
    }

    @Test
    fun doesAddNodesCorrectlyWithNullableTypes() {
        val d = DictRadix<Int>()
        doAddNodesTest(d, RandomGenerator())
        doDoubleAddTest(d, RandomGenerator())
    }

    @Test
    fun doesAddNodesCorrectlyWithNativeTypes() {
        val d = DictRadix<Int>()
        doAddNodesTest(d, RandomGenerator())
        doDoubleAddTest(d, RandomGenerator())
    }

    @Test
    fun basicTestEquals() {
        val d1 = DictRadix<Int>()
        val d2 = DictRadix<Int>()
        assertTrue(d1 == d2)
        d1.addNode("a", 1)
        assertFalse(d1 == d2)
        d2.addNode("a", 1)
        assertTrue(d1 == d2)
        d1.addNode("b", 2)
        assertFalse(d1 == d2)
        d2.addNode("b", 2)
        assertTrue(d1 == d2)
        d2.addNode("c", 3)
        assertFalse(d1 == d2)
    }

    private fun <T> doDoubleAddTest(d: DictRadix<T>, dataGenerator: DataGeneratorFunc<T>) {
        d.clear()

        try {
            d.lookup("abcdef")
            fail("Exception expected")
        } catch (_: IllegalArgumentException) {
        }

        d.addNode("abcdef", dataGenerator.generate())
        d.addNode("abcdef", dataGenerator.generate())

        assertEquals(1, d.getCount())
        assertNotNull(d.lookup("abcdef", false))
        assertNull(d.lookup("abcde", true))
        assertNull(d.lookup("abcd", true))
        assertNull(d.lookup("abc", true))
        assertNull(d.lookup("ab", true))
        assertNull(d.lookup("a", true))
    }

    private fun <T> doAddNodesTest(d: DictRadix<T>, dataGenerator: DataGeneratorFunc<T>) {
        d.clear()

        val counter = IntBox(0)

        try {
            d.lookup("abcdef")
            fail("Exception expected")
        } catch (_: IllegalArgumentException) {
        }

        try {
            d.lookup("abcdef", true)
            fail("Exception expected")
        } catch (_: IllegalArgumentException) {
        }

        // Try adding one node...
        addAndIncrement(d, "abcdef", dataGenerator.generate(), counter)

        // And another
        addAndIncrement(d, "azfwasf", dataGenerator.generate(), counter)

        // Adding this node will require the radix to split a leaf
        addAndIncrement(d, "abf", dataGenerator.generate(), counter)

        // Now add a leaf under that new leaf
        addAndIncrement(d, "abfeeee", dataGenerator.generate(), counter)

        // Add a new leaf under the root
        addAndIncrement(d, "bcdef", dataGenerator.generate(), counter)

        // Simple node addition
        val abcdefgValue = dataGenerator.generate()
        addAndIncrement(d, "abcdefg", abcdefgValue, counter)
        assertEquals(abcdefgValue, d.lookup("abcdefg"))

        // Re-root operation
        addAndIncrement(d, "a", dataGenerator.generate(), counter)

        // Add a new leaf node after re-rooting
        addAndIncrement(d, "agga", dataGenerator.generate(), counter)

        // Do all that backwards - add leafs in a sequential order
        addAndIncrement(d, "c", dataGenerator.generate(), counter)
        addAndIncrement(d, "cb", dataGenerator.generate(), counter)
        addAndIncrement(d, "cbd", dataGenerator.generate(), counter)
        addAndIncrement(d, "cbdefg", dataGenerator.generate(), counter)
        addAndIncrement(d, "cbdefghij", dataGenerator.generate(), counter)
        // And break that order
        addAndIncrement(d, "czzzzij", dataGenerator.generate(), counter)
        addAndIncrement(d, "czzzzija", dataGenerator.generate(), counter)
        addAndIncrement(d, "czzzzijabcde", dataGenerator.generate(), counter)

        // Test overriding an item - value should not change
        addAndIncrement(d, "abf", dataGenerator.generate(), counter)

        // Test overriding an item with AllowValueOverride set to true
        d.setAllowValueOverride(true)
        addAndIncrement(d, "abf", dataGenerator.generate(), counter)

        // Verify the cached counter equals to the count of elements retrieved by actual enumeration,
        // and that the nodes are alphabetically sorted
        var enCount = 0
        var nodeText = ""
        val en = d.iterator() as DictRadix<T>.RadixEnumerator
        while (en.hasNext()) {
            en.next()
            assertTrue(cSharpStringCompare(nodeText, en.getCurrentKey()) < 0)
            nodeText = en.getCurrentKey()
            enCount++
        }
        assertEquals(counter.`val`, enCount)

        assertEquals(abcdefgValue, d.lookup("abcdefg"))

        // Make sure looking up on non-existent key will throw
        try {
            d.lookup("z")
            fail("Exception expected")
        } catch (_: IllegalArgumentException) {
        }

        // Existing or partial keys
        assertNotNull(d.lookup("c"))
        assertNull(d.lookup("cz", true))
        assertNull(d.lookup("czz", true))
        assertNotNull(d.lookup("czzzzij"))
        assertNotNull(d.lookup("czzzzija"))
        assertNotNull(d.lookup("czzzzijabcde"))
    }

    private fun cSharpStringCompare(s1: String?, s2: String?): Int {
        if (s1 == null) return if (s2 == null) 0 else -1
        return if (s2 == null) 1 else s1.compareTo(s2)
    }

    private var rnd = 0

    private interface DataGeneratorFunc<T> {
        fun generate(): T
    }

    private inner class RandomGenerator : DataGeneratorFunc<Int> {
        override fun generate(): Int {
            return ++rnd
        }
    }

    private inner class GuidGenerator : DataGeneratorFunc<UuidObject> {
        override fun generate(): UuidObject {
            return UuidObject(++rnd)
        }
    }

    private class IntBox(var `val`: Int)

    private class UuidObject(val _guid: Int)

    private fun <T> addAndIncrement(d: DictRadix<T>, key: String, obj: T, counter: IntBox) {
        // Only increment counter if the key doesn't already
        var hasKey = true

        val value = try {
            d.lookup(key)
        } catch (_: IllegalArgumentException) {
            null
        }

        if (value == null) {
            counter.`val`++
            hasKey = false
        }

        d.addNode(key, obj)

        assertEquals(counter.`val`, d.getCount())

        // Only check insertion if there was one
        if (d.getAllowValueOverride() || !hasKey) {
            assertEquals(obj, d.lookup(key))
        }
    }
}
