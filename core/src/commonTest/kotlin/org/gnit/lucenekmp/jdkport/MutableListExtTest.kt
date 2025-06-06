package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutableListExtTest {
    @Test
    fun testSubListClear() {
        val list = mutableListOf(1, 2, 3, 4, 5)
        val subList = list.subList(1, 4) // subList contains [2, 3, 4]
        subList.clear()
        assertEquals(0, subList.size)
        assertEquals(mutableListOf(1, 5), list) // Original list should be [1, 5]
    }

    @Test
    fun testSubListRemove() {
        val list = mutableListOf("a", "b", "c", "d")
        val subList = list.subList(1, 3) // subList contains ["b", "c"]
        val removed = subList.remove("b")
        assertEquals(true, removed)
        assertEquals(1, subList.size)
        assertEquals(mutableListOf("c"), subList)
        assertEquals(mutableListOf("a", "c", "d"), list) // Original list should be ["a", "c", "d"]

        val notRemoved = subList.remove("x") // Try to remove an element not in subList
        assertEquals(false, notRemoved)
        assertEquals(1, subList.size) // Size should remain unchanged
    }

    @Test
    fun testSubListAdd() {
        val list = mutableListOf(10, 20, 30, 40)
        val subList = list.subList(1, 3) // subList contains [20, 30]
        subList.add(25) // Add element to subList
        assertEquals(3, subList.size)
        assertEquals(mutableListOf(20, 30, 25), subList)
        assertEquals(mutableListOf(10, 20, 30, 25, 40), list) // Original list should reflect the change

        subList.add(0, 15) // Add element at a specific index in subList
        assertEquals(4, subList.size)
        assertEquals(mutableListOf(15, 20, 30, 25), subList)
        assertEquals(mutableListOf(10, 15, 20, 30, 25, 40), list)
    }

    @Test
    fun testSubListSet() {
        val list = mutableListOf("x", "y", "z", "w")
        val subList = list.subList(1, 3) // subList contains ["y", "z"]
        val oldElement = subList.set(1, "a") // Set element at index 1 of subList to "a"
        assertEquals("z", oldElement)
        assertEquals(2, subList.size)
        assertEquals(mutableListOf("y", "a"), subList)
        assertEquals(mutableListOf("x", "y", "a", "w"), list) // Original list should reflect the change
    }

    @Test
    fun testSubListIteratorRemove() {
        val list = mutableListOf(1, 2, 3, 4, 5, 6)
        val subList = list.subList(1, 5) // subList contains [2, 3, 4, 5]
        val iterator = subList.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item == 3 || item == 5) {
                iterator.remove()
            }
        }
        assertEquals(2, subList.size)
        assertEquals(mutableListOf(2, 4), subList)
        assertEquals(mutableListOf(1, 2, 4, 6), list) // Original list should reflect the changes
    }

    @Test
    fun testSubListEquals() {
        val list1 = mutableListOf("a", "b", "c", "d", "e")
        val subList1 = list1.subList(1, 4) // ["b", "c", "d"]

        val list2 = mutableListOf("b", "c", "d")
        assertEquals(true, subList1.equals(list2)) // subList1 should be equal to list2

        val list3 = mutableListOf("b", "c", "x")
        assertEquals(false, subList1.equals(list3)) // subList1 should not be equal to list3

        val list4 = mutableListOf("b", "c")
        assertEquals(false, subList1.equals(list4)) // subList1 should not be equal to list4 (different size)
    }

    @Test
    fun testSubListAddAll() {
        val list = mutableListOf(1, 2, 3, 7, 8)
        val subList = list.subList(1, 3) // subList is [2, 3]
        val elementsToAdd = listOf(4, 5, 6)
        val changed = subList.addAll(elementsToAdd) // Add all elements from elementsToAdd to subList

        assertEquals(true, changed)
        assertEquals(5, subList.size) // subList should now be [2, 3, 4, 5, 6]
        assertEquals(mutableListOf(2, 3, 4, 5, 6), subList)
        assertEquals(mutableListOf(1, 2, 3, 4, 5, 6, 7, 8), list) // Original list should reflect these additions

        val subList2 = list.subList(1, 7) // subList2 is [2, 3, 4, 5, 6, 7]
        val changed2 = subList2.addAll(2, listOf(9,10)) // Add elements at a specific index
        assertEquals(true, changed2)
        assertEquals(8, subList2.size)
        assertEquals(mutableListOf(2,3,9,10,4,5,6,7), subList2)
        assertEquals(mutableListOf(1,2,3,9,10,4,5,6,7,8), list)
    }

    @Test
    fun testSubListRetainAll() {
        val list = mutableListOf("a", "b", "c", "d", "e", "f")
        val subList = list.subList(1, 5) // subList is ["b", "c", "d", "e"]
        val elementsToRetain = listOf("c", "e", "x") // "x" is not in subList
        val changed = subList.retainAll(elementsToRetain)

        assertEquals(true, changed)
        assertEquals(2, subList.size) // subList should now be ["c", "e"]
        assertEquals(mutableListOf("c", "e"), subList)
        assertEquals(mutableListOf("a", "c", "e", "f"), list) // Original list should reflect these changes

        val noChangeNeeded = subList.retainAll(listOf("c", "e")) // Retain same elements
        assertEquals(false, noChangeNeeded) // No change should occur
        assertEquals(2, subList.size)
    }

    @Test
    fun testSubListRemoveAll() {
        val list = mutableListOf(1, 2, 3, 4, 5, 6, 7)
        val subList = list.subList(1, 6) // subList is [2, 3, 4, 5, 6]
        val elementsToRemove = listOf(3, 5, 8) // 8 is not in subList
        val changed = subList.removeAll(elementsToRemove)

        assertEquals(true, changed)
        assertEquals(3, subList.size) // subList should now be [2, 4, 6]
        assertEquals(mutableListOf(2, 4, 6), subList)
        assertEquals(mutableListOf(1, 2, 4, 6, 7), list) // Original list should reflect these removals

        val noChangeNeeded = subList.removeAll(listOf(10, 11)) // Remove elements not present
        assertEquals(false, noChangeNeeded) // No change should occur
        assertEquals(3, subList.size)
    }

    @Test
    fun testSubListToArray() {
        val list = mutableListOf("apple", "banana", "cherry", "date")
        val subList = list.subList(1, 3) // subList is ["banana", "cherry"]

        val array1 = subList.toTypedArray()
        assertEquals(2, array1.size)
        assertEquals("banana", array1[0])
        assertEquals("cherry", array1[1])
    }

    @Test
    fun testSubListHashCode() {
        val list = mutableListOf(1, 2, 3, 4, 5)
        val subList1 = list.subList(1, 4) // subList1 is [2, 3, 4]
        val subList2 = mutableListOf(2, 3, 4) // An identical list

        assertEquals(subList1.hashCode(), subList2.hashCode())

        val subList3 = list.subList(0,3) // subList3 is [1,2,3]
        assert(subList1.hashCode() != subList3.hashCode())
    }

    @Test
    fun testSubListIndexOf() {
        val list = mutableListOf("a", "b", "c", "b", "d")
        val subList = list.subList(1, 5) // subList is ["b", "c", "b", "d"]

        assertEquals(0, subList.indexOf("b")) // First occurrence of "b" in subList
        assertEquals(1, subList.indexOf("c"))
        assertEquals(3, subList.indexOf("d"))
        assertEquals(-1, subList.indexOf("a")) // "a" is not in subList
        assertEquals(-1, subList.indexOf("z")) // "z" is not in subList
    }

    @Test
    fun testSubListLastIndexOf() {
        val list = mutableListOf("a", "b", "c", "b", "d", "b", "e")
        val subList = list.subList(1, 6) // subList is ["b", "c", "b", "d", "b"]

        assertEquals(4, subList.lastIndexOf("b")) // Last occurrence of "b" in subList
        assertEquals(1, subList.lastIndexOf("c"))
        assertEquals(3, subList.lastIndexOf("d"))
        assertEquals(-1, subList.lastIndexOf("a")) // "a" is not in subList
        assertEquals(-1, subList.lastIndexOf("e")) // "e" is not in subList (it's outside subList range)
    }

    @Test
    fun testSubListContains() {
        val list = mutableListOf(10, 20, 30, 40, 50)
        val subList = list.subList(1, 4) // subList is [20, 30, 40]

        assertEquals(true, subList.contains(30))
        assertEquals(true, subList.contains(20))
        assertEquals(false, subList.contains(10)) // 10 is not in subList
        assertEquals(false, subList.contains(50)) // 50 is not in subList
        assertEquals(false, subList.contains(100)) // 100 is not in list at all
    }

    @Test
    fun testSubListContainsAll() {
        val list = mutableListOf("w", "x", "y", "z", "v")
        val subList = list.subList(1, 4) // subList is ["x", "y", "z"]

        assertEquals(true, subList.containsAll(listOf("x", "z"))) // Contains both "x" and "z"
        assertEquals(true, subList.containsAll(listOf("y"))) // Contains "y"
        assertEquals(false, subList.containsAll(listOf("x", "w"))) // "w" is not in subList
        assertEquals(false, subList.containsAll(listOf("a", "b"))) // Neither "a" nor "b" are in subList
        assertEquals(true, subList.containsAll(emptyList())) // Contains all elements of an empty list
    }

    @Test
    fun testSubListIsEmpty() {
        val list = mutableListOf(1, 2, 3)
        val subList1 = list.subList(1, 3) // subList1 is [2, 3]
        assertEquals(false, subList1.isEmpty())

        val subList2 = list.subList(1, 1) // subList2 is empty
        assertEquals(true, subList2.isEmpty())

        subList1.clear() // Clear subList1
        assertEquals(true, subList1.isEmpty()) // Now subList1 should be empty
        assertEquals(mutableListOf(1), list) // Original list is affected
    }

    @Test
    fun testSubListSize() {
        val list = mutableListOf("a", "b", "c", "d", "e")
        val subList1 = list.subList(1, 4) // subList1 is ["b", "c", "d"]
        assertEquals(3, subList1.size)

        val subList2 = list.subList(0, 5) // subList2 is ["a", "b", "c", "d", "e"]
        assertEquals(5, subList2.size)

        val subList3 = list.subList(2, 2) // subList3 is empty
        assertEquals(0, subList3.size)

        subList1.add("x") // Add an element to subList1
        assertEquals(4, subList1.size) // Size should increase
        assertEquals(6, list.size) // Parent list size also increases
    }

    @Test
    fun testSubListGet() {
        val list = mutableListOf(100, 200, 300, 400)
        val subList = list.subList(1, 3) // subList is [200, 300]

        assertEquals(200, subList.get(0))
        assertEquals(300, subList.get(1))

        // Test for IndexOutOfBoundsException when accessing out of subList's bounds
        try {
            subList.get(2) // Index 2 is out of bounds for subList (size 2)
            throw AssertionError("Expected IndexOutOfBoundsException was not thrown")
        } catch (e: IndexOutOfBoundsException) {
            // Expected exception
        }

        try {
            subList.get(-1) // Negative index
             throw AssertionError("Expected IndexOutOfBoundsException was not thrown")
        } catch (e: IndexOutOfBoundsException) {
            // Expected exception
        }
    }

    @Test
    fun testSubListClearParentStructure() {
        val list = mutableListOf(1, 2, 3, 4, 5)
        val subList = list.subList(1, 4) // subList contains [2, 3, 4]
        subList.clear() // Clear the subList

        // Check parent list structure
        assertEquals(2, list.size) // Parent list should now be [1, 5]
        assertEquals(1, list[0])
        assertEquals(5, list[1])

        // Ensure subList is still valid (empty) and reflects the change
        assertEquals(0, subList.size)
        assertEquals(true, subList.isEmpty())

        // Try to access elements in the cleared part of the parent list via original indices (should fail or be different)
        // This part is tricky as direct access to original indices that were part of subList
        // will now access elements that shifted, or throw IndexOutOfBoundsException if list became too small.
        // For list = [1, 5], list[1] is 5. list[2], list[3] would be out of bounds.
        try {
            list[2] // Accessing where '3' used to be
            // Depending on implementation, this might not be the original '3' or it might throw.
            // Given it's [1,5], this should throw IndexOutOfBounds.
             throw AssertionError("Expected IndexOutOfBoundsException for parent list access was not thrown")
        } catch (e: IndexOutOfBoundsException) {
            // Expected if accessing beyond new size
        }
    }

    @Test
    fun testSubListModificationParentStructure() {
        val list = mutableListOf("a", "b", "c", "d", "e")
        val subList = list.subList(1, 4) // subList is ["b", "c", "d"]

        // Modify subList by adding an element
        subList.add("x") // subList is now ["b", "c", "d", "x"]
        assertEquals(mutableListOf("a", "b", "c", "d", "x", "e"), list)
        assertEquals(4, subList.size)

        // Modify subList by removing an element
        subList.remove("c") // subList is now ["b", "d", "x"]
        assertEquals(mutableListOf("a", "b", "d", "x", "e"), list)
        assertEquals(3, subList.size)

        // Modify subList by setting an element
        subList.set(1, "y") // subList is now ["b", "y", "x"]
        assertEquals(mutableListOf("a", "b", "y", "x", "e"), list)

        // Check direct parent access
        assertEquals("b", list[1])
        assertEquals("y", list[2])
        assertEquals("x", list[3])
    }

    @Test
    fun testSortEmptyList() {
        val list = mutableListOf<Int>()
        list.sort(compareBy { it })
        assertTrue(list.isEmpty(), "List should remain empty after sorting")
    }

    @Test
    fun testSortSingleElementList() {
        val list = mutableListOf(1)
        list.sort(compareBy { it })
        assertEquals(listOf(1), list, "List with single element should remain unchanged")
    }

    @Test
    fun testSortMultipleElements() {
        val list = mutableListOf(3, 1, 4, 1, 5, 9, 2, 6)
        list.sort(compareBy { it })
        assertEquals(listOf(1, 1, 2, 3, 4, 5, 6, 9), list, "List should be sorted in ascending order")
    }

    @Test
    fun testSortAlreadySortedList() {
        val list = mutableListOf(1, 2, 3, 4, 5)
        list.sort(compareBy { it })
        assertEquals(listOf(1, 2, 3, 4, 5), list, "Already sorted list should remain unchanged")
    }

    @Test
    fun testSortReverseOrderList() {
        val list = mutableListOf(5, 4, 3, 2, 1)
        list.sort(compareBy { it })
        assertEquals(listOf(1, 2, 3, 4, 5), list, "Reverse sorted list should be sorted correctly")
    }

    @Test
    fun testSortWithDuplicates() {
        val list = mutableListOf(3, 1, 4, 1, 5, 9, 2, 6, 5, 3)
        list.sort(compareBy { it })
        assertEquals(listOf(1, 1, 2, 3, 3, 4, 5, 5, 6, 9), list, "List with duplicates should be sorted correctly")
    }

    @Test
    fun testSortWithCustomComparator() {
        val list = mutableListOf(3, 1, 4, 1, 5, 9, 2, 6)
        // Custom comparator for reverse order
        list.sort(compareByDescending { it })
        assertEquals(listOf(9, 6, 5, 4, 3, 2, 1, 1), list, "List should be sorted in descending order using custom comparator")
    }

    @Test
    fun testSortStrings() {
        val list = mutableListOf("banana", "apple", "cherry", "date")
        list.sort(compareBy { it })
        assertEquals(listOf("apple", "banana", "cherry", "date"), list, "List of strings should be sorted alphabetically")
    }

    @Test
    fun testSortStringsCustomComparator() {
        val list = mutableListOf("banana", "apple", "cherry", "date")
        // Custom comparator for reverse alphabetical order
        list.sort(compareByDescending { it })
        assertEquals(listOf("date", "cherry", "banana", "apple"), list, "List of strings should be sorted in reverse alphabetical order")
    }
}
