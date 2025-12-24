package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class StringCharacterIteratorTest {

    @Test
    fun testConstructorAndNavigation() {
        val it = StringCharacterIterator("abcd")
        assertEquals(0, it.beginIndex)
        assertEquals(4, it.endIndex)
        assertEquals(0, it.index)
        assertEquals('a', it.current())

        assertEquals('a', it.first())
        assertEquals('d', it.last())
        assertEquals(3, it.index)
        assertEquals(CharacterIterator.DONE, it.next())
        assertEquals(4, it.index)
    }

    @Test
    fun testSetIndexAndBounds() {
        val it = StringCharacterIterator("abcd")
        assertEquals('c', it.setIndex(2))
        assertEquals(2, it.index)
        assertEquals(CharacterIterator.DONE, it.setIndex(4))
        assertEquals(4, it.index)

        assertFailsWith<IllegalArgumentException> { it.setIndex(-1) }
        assertFailsWith<IllegalArgumentException> { it.setIndex(5) }
    }

    @Test
    fun testRangeConstructor() {
        val it = StringCharacterIterator("abcdef", 1, 5, 2)
        assertEquals(1, it.beginIndex)
        assertEquals(5, it.endIndex)
        assertEquals(2, it.index)
        assertEquals('c', it.current())
    }

    @Test
    fun testInvalidConstructorRanges() {
        assertFailsWith<IllegalArgumentException> { StringCharacterIterator("abc", -1, 2, 0) }
        assertFailsWith<IllegalArgumentException> { StringCharacterIterator("abc", 2, 1, 1) }
        assertFailsWith<IllegalArgumentException> { StringCharacterIterator("abc", 0, 4, 0) }
        assertFailsWith<IllegalArgumentException> { StringCharacterIterator("abc", 0, 2, 3) }
    }

    @Test
    fun testSetTextResetsState() {
        val it = StringCharacterIterator("abcd", 2)
        it.setText("xy")
        assertEquals(0, it.beginIndex)
        assertEquals(2, it.endIndex)
        assertEquals(0, it.index)
        assertEquals('x', it.current())
    }

    @Test
    fun testCloneAndEquality() {
        val it = StringCharacterIterator("abcd", 1, 4, 2)
        val clone = it.clone() as StringCharacterIterator

        assertNotSame(it, clone)
        assertTrue(it == clone)
        assertEquals(it.hashCode(), clone.hashCode())

        it.next()
        assertTrue(it != clone)
    }
}
