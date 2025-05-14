package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class OptionalTest {

    @Test
    fun testOf() {
        val value = "test"
        val optional = Optional.of(value)
        assertTrue(optional.isPresent)
        assertEquals(value, optional.get())
    }

    @Test
    fun testOfNullable() {
        val value = "test"
        val optional = Optional.ofNullable(value)
        assertTrue(optional.isPresent)
        assertEquals(value, optional.get())

        val emptyOptional = Optional.ofNullable<String>(null)
        assertFalse(emptyOptional.isPresent)
    }

    @Test
    fun testEmpty() {
        val emptyOptional = Optional.empty<String>()
        assertFalse(emptyOptional.isPresent)
    }

    @Test
    fun testGet() {
        val value = "test"
        val optional = Optional.of(value)
        assertEquals(value, optional.get())

        val emptyOptional = Optional.empty<String>()
        assertFailsWith<NoSuchElementException> { emptyOptional.get() }
    }

    @Test
    fun testIsPresent() {
        val value = "test"
        val optional = Optional.of(value)
        assertTrue(optional.isPresent)

        val emptyOptional = Optional.empty<String>()
        assertFalse(emptyOptional.isPresent)
    }

    @Test
    fun testIfPresent() {
        val value = "test"
        val optional = Optional.of(value)
        var result: String? = null
        optional.ifPresent { result = it }
        assertEquals(value, result)

        val emptyOptional = Optional.empty<String>()
        result = null
        emptyOptional.ifPresent { result = it }
        assertNull(result)
    }

    @Test
    fun testOrElse() {
        val value = "test"
        val optional = Optional.of(value)
        assertEquals(value, optional.orElse("default"))

        val emptyOptional = Optional.empty<String>()
        assertEquals("default", emptyOptional.orElse("default"))
    }

    @Test
    fun testOrElseGet() {
        val value = "test"
        val optional = Optional.of(value)
        assertEquals(value, optional.orElseGet { "default" })

        val emptyOptional = Optional.empty<String>()
        assertEquals("default", emptyOptional.orElseGet { "default" })
    }

    @Test
    fun testOrElseThrow() {
        val value = "test"
        val optional = Optional.of(value)
        assertEquals(value, optional.orElseThrow())

        val emptyOptional = Optional.empty<String>()
        assertFailsWith<NoSuchElementException> { emptyOptional.orElseThrow() }
    }
}
