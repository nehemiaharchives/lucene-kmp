package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class CharacterIteratorTest {

    @Test
    fun testBasicIteration() {
        val it: CharacterIterator = StringCharacterIterator("abc")

        assertEquals('a', it.first())
        assertEquals(0, it.index)
        assertEquals('a', it.current())

        assertEquals('b', it.next())
        assertEquals(1, it.index)
        assertEquals('c', it.next())
        assertEquals(2, it.index)

        assertEquals(CharacterIterator.DONE, it.next())
        assertEquals(3, it.index)
        assertEquals(CharacterIterator.DONE, it.current())

        assertEquals('c', it.previous())
        assertEquals(2, it.index)
        assertEquals('b', it.previous())
        assertEquals(1, it.index)
        assertEquals('a', it.previous())
        assertEquals(0, it.index)

        assertEquals(CharacterIterator.DONE, it.previous())
        assertEquals(0, it.index)
    }

    @Test
    fun testSetIndexAndBounds() {
        val it: CharacterIterator = StringCharacterIterator("abcd")

        assertEquals('c', it.setIndex(2))
        assertEquals(2, it.index)
        assertEquals('c', it.current())

        assertEquals(CharacterIterator.DONE, it.setIndex(4))
        assertEquals(4, it.index)

        assertFailsWith<IllegalArgumentException> {
            it.setIndex(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            it.setIndex(5)
        }
    }

    @Test
    fun testBeginEndRangeConstructor() {
        val it: CharacterIterator = StringCharacterIterator("abcdef", 1, 5, 2)

        assertEquals(1, it.beginIndex)
        assertEquals(5, it.endIndex)
        assertEquals(2, it.index)
        assertEquals('c', it.current())

        assertEquals('b', it.first())
        assertEquals(1, it.index)

        assertEquals('e', it.last())
        assertEquals(4, it.index)
        assertEquals(CharacterIterator.DONE, it.next())
        assertEquals(5, it.index)
    }

    @Test
    fun testConstructorInvalidRanges() {
        assertFailsWith<IllegalArgumentException> {
            StringCharacterIterator("abc", -1, 2, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            StringCharacterIterator("abc", 2, 1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            StringCharacterIterator("abc", 0, 4, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            StringCharacterIterator("abc", 0, 2, 3)
        }
    }

    @Test
    fun testCloneCopiesState() {
        val original = StringCharacterIterator("abcd", 1, 4, 2)
        val clone = original.clone() as CharacterIterator

        assertNotSame(original, clone)
        assertEquals(original.beginIndex, clone.beginIndex)
        assertEquals(original.endIndex, clone.endIndex)
        assertEquals(original.index, clone.index)
        assertEquals(original.current(), clone.current())
        assertTrue(original == clone)

        original.next()
        assertEquals(3, original.index)
        assertEquals(2, clone.index)
    }
}
