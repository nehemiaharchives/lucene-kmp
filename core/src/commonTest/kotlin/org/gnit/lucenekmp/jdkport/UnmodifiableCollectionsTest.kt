package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UnmodifiableCollectionsTest {

    @Test
    fun testUnmodifiableMutableIterator() {
        val delegate = mutableListOf(1, 2, 3).iterator()
        val it = UnmodifiableMutableIterator(delegate)

        assertTrue(it.hasNext())
        assertEquals(1, it.next())
        assertFailsWith<UnsupportedOperationException> {
            it.remove()
        }
    }

    @Test
    fun testUnmodifiableMutableCollectionReflectsBackingCollectionAndRejectsMutation() {
        val delegate = mutableListOf("a", "b", "b")
        val collection = UnmodifiableMutableCollection(delegate)

        assertEquals(3, collection.size)
        assertTrue(collection.contains("b"))
        assertEquals(listOf("a", "b", "b"), collection.toList())

        delegate.add("c")
        assertEquals(listOf("a", "b", "b", "c"), collection.toList())

        assertFailsWith<UnsupportedOperationException> { collection.add("d") }
        assertFailsWith<UnsupportedOperationException> { collection.remove("a") }
        assertFailsWith<UnsupportedOperationException> { collection.clear() }

        val iterator = collection.iterator()
        assertEquals("a", iterator.next())
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
    }

    @Test
    fun testUnmodifiableMutableListRejectsAllStructuralAndElementMutation() {
        val delegate = mutableListOf(1, 2, 3, 2)
        val list = UnmodifiableMutableList(delegate)

        assertEquals(4, list.size)
        assertEquals(1, list[0])
        assertEquals(1, list.indexOf(2))
        assertEquals(3, list.lastIndexOf(2))
        assertEquals(listOf(2, 3), list.subList(1, 3))

        delegate[1] = 20
        assertEquals(listOf(1, 20, 3, 2), list.toList())

        assertFailsWith<UnsupportedOperationException> { list.add(4) }
        assertFailsWith<UnsupportedOperationException> { list.add(1, 4) }
        assertFailsWith<UnsupportedOperationException> { list.addAll(listOf(4, 5)) }
        assertFailsWith<UnsupportedOperationException> { list.addAll(1, listOf(4, 5)) }
        assertFailsWith<UnsupportedOperationException> { list.removeAt(0) }
        assertFailsWith<UnsupportedOperationException> { list.remove(20) }
        assertFailsWith<UnsupportedOperationException> { list.set(0, 10) }
        assertFailsWith<UnsupportedOperationException> { list.clear() }
    }

    @Test
    fun testUnmodifiableMutableListIteratorRejectsMutation() {
        val delegate = mutableListOf("x", "y", "z")
        val iterator = UnmodifiableMutableListIterator(delegate.listIterator(1))

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasPrevious())
        assertEquals("y", iterator.next())
        assertEquals("y", iterator.previous())
        assertEquals(1, iterator.nextIndex())
        assertEquals(0, iterator.previousIndex())

        assertFailsWith<UnsupportedOperationException> { iterator.add("q") }
        assertFailsWith<UnsupportedOperationException> { iterator.set("q") }
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
    }

    @Test
    fun testUnmodifiableMutableListIteratorFromListIsWrapped() {
        val delegate = mutableListOf(1, 2, 3)
        val list = UnmodifiableMutableList(delegate)

        val iterator = list.listIterator()
        assertEquals(1, iterator.next())
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }

        val iteratorAtIndex = list.listIterator(1)
        assertEquals(2, iteratorAtIndex.next())
        assertFailsWith<UnsupportedOperationException> { iteratorAtIndex.add(9) }
    }

    @Test
    fun testUnmodifiableMutableListSubListIsAlsoUnmodifiableView() {
        val delegate = mutableListOf("a", "b", "c", "d")
        val list = UnmodifiableMutableList(delegate)
        val subList = list.subList(1, 3)

        assertEquals(listOf("b", "c"), subList)
        delegate[1] = "bb"
        assertEquals(listOf("bb", "c"), subList)

        assertFailsWith<UnsupportedOperationException> { subList.add("x") }
        assertFailsWith<UnsupportedOperationException> { subList.removeAt(0) }
    }

    @Test
    fun testUnmodifiableWrappersPreserveEqualsHashCodeAndToString() {
        val delegate = mutableListOf("a", "b")
        val collection = UnmodifiableMutableCollection(delegate)
        val list = UnmodifiableMutableList(delegate)

        assertTrue(collection == delegate)
        assertTrue(list == delegate)
        assertEquals(delegate.hashCode(), collection.hashCode())
        assertEquals(delegate.hashCode(), list.hashCode())
        assertEquals(delegate.toString(), collection.toString())
        assertEquals(delegate.toString(), list.toString())
    }

    @Test
    fun testUnmodifiableMutableMapEntrySetIteratorWrapsEntries() {
        val delegate = linkedMapOf("a" to 1, "b" to 2)
        val entrySet = UnmodifiableMutableMapEntrySet(delegate.entries)

        val iterator = entrySet.iterator()
        val first = iterator.next()
        assertEquals("a", first.key)
        assertEquals(1, first.value)
        assertFailsWith<UnsupportedOperationException> { first.setValue(10) }
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
    }

    @Test
    fun testUnmodifiableMutableMapViewsAreBackedAndReadOnly() {
        val delegate = linkedMapOf("a" to 1, "b" to 2)
        val map = UnmodifiableMutableMap(delegate)

        assertEquals(2, map.size)
        assertEquals(1, map["a"])
        assertTrue(map.keys.contains("a"))
        assertTrue(map.values.contains(2))

        delegate["c"] = 3
        assertEquals(3, map.size)
        assertTrue(map.keys.contains("c"))
        assertTrue(map.values.contains(3))

        assertFailsWith<UnsupportedOperationException> { map.put("d", 4) }
        assertFailsWith<UnsupportedOperationException> { map.remove("a") }
        assertFailsWith<UnsupportedOperationException> { map.clear() }
        assertFailsWith<UnsupportedOperationException> { map.entries.add(delegate.entries.first()) }
    }

    @Test
    fun testMapEntryWrapperDelegatesIdentitySensitiveData() {
        val backing = linkedMapOf("a" to 1)
        val delegateEntry = backing.entries.first()
        val wrapped = UnmodifiableMutableMapEntry(delegateEntry)

        assertSame(delegateEntry.key, wrapped.key)
        assertEquals(delegateEntry.value, wrapped.value)
        assertFalse(wrapped == null)
        assertEquals(delegateEntry.hashCode(), wrapped.hashCode())
    }
}
