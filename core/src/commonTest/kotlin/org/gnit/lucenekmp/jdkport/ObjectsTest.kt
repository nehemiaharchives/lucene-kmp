package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ObjectsTest {

    @Test
    fun testEquals() {
        assertEquals(true, Objects.equals("test", "test"))
        assertEquals(false, Objects.equals("test", "TEST"))
        assertEquals(false, Objects.equals("test", null))
        assertEquals(false, Objects.equals(null, "test"))
        assertEquals(true, Objects.equals(null, null))
    }

    @Test
    fun testHash() {
        assertEquals(0, Objects.hash())
        assertEquals(1, Objects.hash(1))
        assertEquals(Objects.hash(1, 2, 3), Objects.hash(1, 2, 3))
        assertNotEquals(Objects.hash(1, 2, 3), Objects.hash(3, 2, 1))
    }

    @Test
    fun testToString() {
        assertEquals("test", Objects.toString("test"))
        assertEquals("null", Objects.toString(null))
    }
}
