package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class CollectionsTest {

    @Test
    fun testSwap() {
        val list = mutableListOf(1, 2, 3, 4)
        Collections.swap(list, 1, 3)
        assertEquals(listOf(1, 4, 3, 2), list)

        assertFailsWith<IndexOutOfBoundsException> {
            Collections.swap(list, -1, 2)
        }

        assertFailsWith<IndexOutOfBoundsException> {
            Collections.swap(list, 1, 4)
        }

        // Test swapping elements at the beginning of the list.
        val listBegin = mutableListOf(1, 2, 3, 4)
        Collections.swap(listBegin, 0, 1)
        assertEquals(listOf(2, 1, 3, 4), listBegin)

        // Test swapping elements at the end of the list.
        val listEnd = mutableListOf(1, 2, 3, 4)
        Collections.swap(listEnd, 2, 3)
        assertEquals(listOf(1, 2, 4, 3), listEnd)

        // Test swapping identical elements (e.g., list[i] == list[j]).
        val listIdentical = mutableListOf(1, 2, 2, 4)
        Collections.swap(listIdentical, 1, 2)
        assertEquals(listOf(1, 2, 2, 4), listIdentical)

        // Test swapping elements in a list with duplicate values.
        val listDuplicate = mutableListOf(1, 2, 3, 2)
        Collections.swap(listDuplicate, 1, 3)
        assertEquals(listOf(1, 2, 3, 2), listDuplicate)

        // Test swapping when i == j (should leave the list unchanged).
        val listSameIndex = mutableListOf(1, 2, 3, 4)
        Collections.swap(listSameIndex, 1, 1)
        assertEquals(listOf(1, 2, 3, 4), listSameIndex)

        // Test IndexOutOfBoundsException for i >= list.size().
        assertFailsWith<IndexOutOfBoundsException> {
            Collections.swap(list, 4, 1)
        }

        // Test IndexOutOfBoundsException for j < 0.
        assertFailsWith<IndexOutOfBoundsException> {
            Collections.swap(list, 1, -1)
        }
    }

    @Test
    fun testReverseOrder() {
        // Test with null comparator (natural ordering) uses Collections.reverseOrder() no-arg
        val naturalIntReverse = Collections.reverseOrder<Int?>() // T is Int?
        assertTrue(naturalIntReverse.compare(2, 1) < 0) // 1.compareTo(2) < 0
        assertTrue(naturalIntReverse.compare(1, 2) > 0) // 2.compareTo(1) > 0
        assertEquals(0, naturalIntReverse.compare(2, 2))
        // Test with nulls - depends on how REVERSE_ORDER handles them (it doesn't directly, relies on compareTo)
        // val naturalNullableReverse = Collections.reverseOrder<Int?>() // Or Collections.reverseOrder(null)
        // assertThrows<NullPointerException> { naturalNullableReverse.compare(null, 1) } // Standard behavior if T not nullable

        val naturalStringReverse = Collections.reverseOrder<String?>() // T is String?
        assertTrue(naturalStringReverse.compare("b", "a") < 0) // a.compareTo(b) < 0
        assertTrue(naturalStringReverse.compare("a", "b") > 0) // b.compareTo(a) > 0
        assertEquals(0, naturalStringReverse.compare("a", "a"))

        // Test with a custom comparator (String length, nullable aware)
        val lengthComparator = Comparator<String?> { s1, s2 ->
            when {
                s1 == null && s2 == null -> 0
                s1 == null -> -1 // nulls first
                s2 == null -> 1
                else -> s1.length.compareTo(s2.length)
            }
        }
        val reverseLengthComparator = Collections.reverseOrder(lengthComparator)
        // apple (5), kiwi (4). lengthComparator(apple, kiwi) = 1. reverseOrder should yield -1.
        assertTrue(reverseLengthComparator.compare("apple", "kiwi") < 0)
        // kiwi (4), apple (5). lengthComparator(kiwi, apple) = -1. reverseOrder should yield 1.
        assertTrue(reverseLengthComparator.compare("kiwi", "apple") > 0)
        assertEquals(0, reverseLengthComparator.compare("kiwi", "pear")) // same length
        assertTrue(reverseLengthComparator.compare(null, "kiwi") > 0) // lengthComp(null, kiwi) = -1, reverse: 1
        assertTrue(reverseLengthComparator.compare("apple", null) < 0) // lengthComp(apple, null) = 1, reverse: -1
        assertEquals(0, reverseLengthComparator.compare(null, null))

        // Test with identical elements using natural reverse order
        assertEquals(0, naturalIntReverse.compare(5, 5))

        // Test with identical elements using custom reverse order
        assertEquals(0, reverseLengthComparator.compare("grape", "melon")) // same length

        // Test reverseOrder when the provided comparator is REVERSE_ORDER itself
        val ro = Collections.ReverseComparator.REVERSE_ORDER as Comparator<Int?>
        val reversedRo = Collections.reverseOrder(ro) // This should be a ReverseComparator2(REVERSE_ORDER)
        assertTrue(reversedRo is Collections.ReverseComparator2<*>, "Expected reversedRo to be an instance of ReverseComparator2<*>")
        // (REVERSE_ORDER as Comparator<Int?>).compare(1,2) > 0 means 2.compareTo(1) > 0
        // reversedRo.compare(1,2) means ro.compare(2,1) which means 1.compareTo(2) < 0 (like natural order)
        assertTrue(reversedRo.compare(1, 2) < 0) // Behaves like natural order
        assertTrue(reversedRo.compare(2, 1) > 0) // Behaves like natural order


        // Test reverseOrder with a natural order comparator
        val naturalIntComparator = Comparator<Int?> { a, b ->
            when { a == null && b == null -> 0; a == null -> -1; b == null -> 1; else -> a.compareTo(b) }
        }
        val reversedNatural = Collections.reverseOrder(naturalIntComparator)
        assertTrue(reversedNatural.compare(2, 1) < 0) // natural: 2>1 (pos), reverse: neg
        assertTrue(reversedNatural.compare(1, 2) > 0) // natural: 1<2 (neg), reverse: pos
        assertEquals(0, reversedNatural.compare(1,1))
        assertTrue(reversedNatural.compare(null,1) > 0) // natural: null < 1 (neg), reverse: pos

        // Test reverseOrder when the provided comparator is an instance of ReverseComparator2
        // Should return the underlying original comparator.
        val originalLengthComparator = Comparator<String?> { s1, s2 ->
            when { s1 == null && s2 == null -> 0; s1 == null -> -1; s2 == null -> 1; else -> s1.length.compareTo(s2.length) }
        }
        val rc2 = Collections.ReverseComparator2(originalLengthComparator)
        val unwrapped = Collections.reverseOrder(rc2) // Should be originalLengthComparator
        assertTrue(originalLengthComparator === unwrapped, "Expected unwrapped comparator to be the same instance as originalLengthComparator")
        assertTrue(unwrapped.compare("apple", "kiwi") > 0) // apple (5) vs kiwi (4) -> original order: positive
        assertTrue(unwrapped.compare("kiwi", "apple") < 0) // original order: negative
    }

    @Test
    fun testReverse() {
        val list = mutableListOf(1, 2, 3, 4)
        Collections.reverse(list)
        assertEquals(listOf(4, 3, 2, 1), list)

        // Test reversing an empty list.
        val emptyList = mutableListOf<Int>()
        Collections.reverse(emptyList)
        assertEquals(emptyList(), emptyList)

        // Test reversing a list with a single element.
        val singleElementList = mutableListOf(1)
        Collections.reverse(singleElementList)
        assertEquals(listOf(1), singleElementList)

        // Test reversing a list with an even number of elements (covered by the initial test, but added for clarity).
        val evenList = mutableListOf(1, 2)
        Collections.reverse(evenList)
        assertEquals(listOf(2, 1), evenList)

        // Test reversing a list with an odd number of elements.
        val oddList = mutableListOf(1, 2, 3)
        Collections.reverse(oddList)
        assertEquals(listOf(3, 2, 1), oddList)

        // Test reversing a list of Strings.
        val stringList = mutableListOf("a", "b", "c")
        Collections.reverse(stringList)
        assertEquals(listOf("c", "b", "a"), stringList)
    }

    @Test
    fun testReverseComparatorDirectly() {
        // REVERSE_ORDER is a Comparator<Comparable<Any>>.
        // Its compare(c1, c2) method implements c2.compareTo(c1).

        val intReverseOrder = Collections.ReverseComparator.REVERSE_ORDER as Comparator<Int>
        // Test with Integers
        assertTrue(intReverseOrder.compare(5, 1) < 0) // Effectively 1.compareTo(5)
        assertTrue(intReverseOrder.compare(1, 5) > 0) // Effectively 5.compareTo(1)
        assertEquals(0, intReverseOrder.compare(5, 5))    // Effectively 5.compareTo(5)

        val stringReverseOrder = Collections.ReverseComparator.REVERSE_ORDER as Comparator<String>
        // Test with Strings
        assertTrue(stringReverseOrder.compare("world", "hello") < 0) // "hello".compareTo("world")
        assertTrue(stringReverseOrder.compare("hello", "world") > 0) // "world".compareTo("hello")
        assertEquals(0, stringReverseOrder.compare("hello", "hello"))    // "hello".compareTo("hello")

        // Test readResolve()
        // ReverseComparator.readResolve() should return the singleton REVERSE_ORDER
        val resolved = (Collections.ReverseComparator.REVERSE_ORDER as Collections.ReverseComparator<Any>).readResolve()
        assertNotNull(resolved)
        assertTrue(Collections.ReverseComparator.REVERSE_ORDER === resolved, "readResolve should return the singleton REVERSE_ORDER")

        // Test behavior of the resolved object (which is REVERSE_ORDER itself)
        val resolvedIntAccess = resolved as Comparator<Int>
        assertTrue(resolvedIntAccess.compare(5, 1) < 0)
        assertTrue(resolvedIntAccess.compare(1, 5) > 0)
        assertEquals(0, resolvedIntAccess.compare(5, 5))

        val resolvedStringAccess = resolved as Comparator<String>
        assertTrue(resolvedStringAccess.compare("world", "hello") < 0)
        assertTrue(resolvedStringAccess.compare("hello", "world") > 0)
        assertEquals(0, resolvedStringAccess.compare("hello", "hello"))
    }

    @Test
    fun testReverseComparator2Directly() {
        // Comparator for Int? that handles nulls (e.g., nulls first)
        val naturalIntNullableComparator = Comparator<Int?> { o1, o2 ->
            when {
                o1 == null && o2 == null -> 0
                o1 == null -> -1 // nulls are smaller
                o2 == null -> 1  // non-nulls are larger
                else -> o1.compareTo(o2)
            }
        }
        val customReverseIntNullable = Collections.ReverseComparator2(naturalIntNullableComparator)

        // Test compare method for ReverseComparator2<Int?>
        // naturalIntNullableComparator.compare(5,1) is 1 (5 > 1)
        // customReverseIntNullable.compare(5,1) should be -1
        assertTrue(customReverseIntNullable.compare(5, 1) < 0)

        // naturalIntNullableComparator.compare(1,5) is -1 (1 < 5)
        // customReverseIntNullable.compare(1,5) should be 1
        assertTrue(customReverseIntNullable.compare(1, 5) > 0)
        assertEquals(0, customReverseIntNullable.compare(5, 5))

        // Test with nulls for ReverseComparator2<Int?>
        // naturalIntNullableComparator.compare(null, 5) is -1 (null < 5)
        // customReverseIntNullable.compare(null, 5) should be 1
        assertTrue(customReverseIntNullable.compare(null, 5) > 0)

        // naturalIntNullableComparator.compare(5, null) is 1 (5 > null)
        // customReverseIntNullable.compare(5, null) should be -1
        assertTrue(customReverseIntNullable.compare(5, null) < 0)
        assertEquals(0, customReverseIntNullable.compare(null, null))

        // Test equals method
        assertTrue(customReverseIntNullable.equals(customReverseIntNullable))
        val anotherSame = Collections.ReverseComparator2(naturalIntNullableComparator)
        assertTrue(customReverseIntNullable.equals(anotherSame))

        val differentIntNullableComparator = Comparator<Int?> { o1, o2 ->
            when {
                o1 == null && o2 == null -> 0
                o1 == null -> 1 // nulls are larger for this one
                o2 == null -> -1
                else -> (o1 % 10).compareTo(o2 % 10)
            }
        }
        val anotherDifferent = Collections.ReverseComparator2(differentIntNullableComparator)
        assertFalse(customReverseIntNullable.equals(anotherDifferent))
        assertFalse(customReverseIntNullable.equals(Any()))
        assertFalse(customReverseIntNullable.equals(null))


        // Test hashCode method
        assertEquals(customReverseIntNullable.hashCode(), anotherSame.hashCode())
        // It's good practice for different objects to have different hash codes, but not a strict requirement if equals is correct.
        // assertNotEquals(customReverseIntNullable.hashCode(), anotherDifferent.hashCode())

        // Test with nullable types for ReverseComparator2 if its definition allows (it does: cmp: Comparator<T?>)
        val naturalStringNullableComparator = Comparator<String?> { s1, s2 ->
            when {
                s1 == null && s2 == null -> 0
                s1 == null -> -1 // nulls first
                s2 == null -> 1
                else -> s1.compareTo(s2)
            }
        }
        val customReverseNullableComparator = Collections.ReverseComparator2<String?>(naturalStringNullableComparator)
        assertTrue(customReverseNullableComparator.compare("a", "b") > 0) // natural: "a" < "b" (negative), reverse: positive
        assertTrue(customReverseNullableComparator.compare(null, "a") > 0) // natural: null < "a" (negative), reverse: positive
        assertTrue(customReverseNullableComparator.compare("b", null) < 0) // natural: "b" > null (positive), reverse: negative
        assertEquals(0, customReverseNullableComparator.compare(null, null))
    }
}
