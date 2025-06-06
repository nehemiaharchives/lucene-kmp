package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.*

private val logger = KotlinLogging.logger {}

class OptionalTest {
    // Test methods will be added here
    @Test
    fun testEmpty() {
        val emptyOptional = Optional.empty<String>()
        assertNotNull(emptyOptional, "Optional.empty() should return an instance.")
        assertFalse(emptyOptional.isPresent, "isPresent() should be false for empty optional.")
        assertTrue(emptyOptional.isEmpty, "isEmpty() should be true for empty optional.")
        assertEquals("Optional.empty", emptyOptional.toString(), "toString() should return 'Optional.empty'.")
    }

    @Test
    fun testEmptyGetThrowsException() {
        val emptyOptional = Optional.empty<String>()
        assertFailsWith<NoSuchElementException>("Calling get() on empty optional should throw NoSuchElementException.") {
            emptyOptional.get()
        }
    }

    @Test
    fun testOfWithValue() {
        val value = "testValue"
        val optionalOfValue = Optional.of(value)
        assertNotNull(optionalOfValue, "Optional.of(value) should return an instance.")
        assertTrue(optionalOfValue.isPresent, "isPresent() should be true for Optional.of(value).")
        assertFalse(optionalOfValue.isEmpty, "isEmpty() should be false for Optional.of(value).")
        assertEquals(value, optionalOfValue.get(), "get() should return the original value.")
        assertEquals("Optional[$value]", optionalOfValue.toString(), "toString() should include the value.")
    }

    @Test
    fun testOfNullThrowsException() {
        assertFailsWith<NullPointerException>("Optional.of(null) should throw NullPointerException.") {
            Optional.of<String?>(null) // Explicitly use nullable type for clarity, though checkNotNull handles it
        }
    }

    @Test
    fun testOfNullableWithValue() {
        val value = "testValue"
        val optionalOfNullableValue = Optional.ofNullable(value)
        assertNotNull(optionalOfNullableValue, "Optional.ofNullable(value) should return an instance.")
        assertTrue(optionalOfNullableValue.isPresent, "isPresent() should be true for Optional.ofNullable(value).")
        assertEquals(value, optionalOfNullableValue.get(), "get() should return the original value.")
    }

    @Test
    fun testOfNullableWithNull() {
        val optionalOfNullableNull = Optional.ofNullable<String>(null)
        assertNotNull(optionalOfNullableNull, "Optional.ofNullable(null) should return an instance.")
        assertFalse(optionalOfNullableNull.isPresent, "isPresent() should be false for Optional.ofNullable(null).")
        assertTrue(optionalOfNullableNull.isEmpty, "isEmpty() should be true for Optional.ofNullable(null).")
        assertFailsWith<NoSuchElementException>("Calling get() on Optional.ofNullable(null) should throw NoSuchElementException.") {
            optionalOfNullableNull.get()
        }
    }

    @Test
    fun testIfPresent() {
        val value = "testValue"
        val optionalWithValue = Optional.of(value)
        var actionExecutedWithValue = false
        optionalWithValue.ifPresent {
            assertEquals(value, it, "Value passed to action should be correct.")
            actionExecutedWithValue = true
        }
        assertTrue(actionExecutedWithValue, "Action should be executed for Optional with value.")

        val emptyOptional = Optional.empty<String>()
        var actionExecutedForEmpty = false
        emptyOptional.ifPresent {
            actionExecutedForEmpty = true // This should not happen
        }
        assertFalse(actionExecutedForEmpty, "Action should not be executed for empty Optional.")
    }

    @Test
    fun testIfPresentOrElse() {
        val value = "testValue"
        val optionalWithValue = Optional.of(value)
        var actionExecuted = false
        var emptyActionExecuted = false

        optionalWithValue.ifPresentOrElse(
            action = {
                assertEquals(value, it, "Value passed to action should be correct.")
                actionExecuted = true
            },
            emptyAction = {
                emptyActionExecuted = true // This should not happen
            }
        )
        assertTrue(actionExecuted, "Action should be executed for Optional with value.")
        assertFalse(emptyActionExecuted, "Empty action should not be executed for Optional with value.")

        actionExecuted = false // Reset flags
        emptyActionExecuted = false // Reset flags

        val emptyOptional = Optional.empty<String>()
        emptyOptional.ifPresentOrElse(
            action = {
                actionExecuted = true // This should not happen
            },
            emptyAction = {
                emptyActionExecuted = true
            }
        )
        assertFalse(actionExecuted, "Action should not be executed for empty Optional.")
        assertTrue(emptyActionExecuted, "Empty action should be executed for empty Optional.")
    }

    @Test
    fun testFilter() {
        val value = "testValue"
        val optionalWithValue = Optional.of(value)

        // Filter with matching predicate
        val filteredSome = optionalWithValue.filter { it == value }
        assertTrue(filteredSome.isPresent, "Filter with matching predicate should return present Optional.")
        assertEquals(value, filteredSome.get(), "Filtered value should be the original value.")

        // Filter with non-matching predicate
        val filteredNone = optionalWithValue.filter { it == "otherValue" }
        assertTrue(filteredNone.isEmpty, "Filter with non-matching predicate should return empty Optional.")

        // Filter on empty Optional
        val emptyOptional = Optional.empty<String>()
        val filteredEmpty = emptyOptional.filter { it == value }
        assertTrue(filteredEmpty.isEmpty, "Filter on empty Optional should return empty Optional.")

        // Filter with predicate that itself operates on nullable (though value is non-null here)
        val optionalInt = Optional.of(10)
        val filteredIntPresent = optionalInt.filter { it != null && it > 5 }
        assertTrue(filteredIntPresent.isPresent)
        assertEquals(10, filteredIntPresent.get())

        val filteredIntEmpty = optionalInt.filter { it != null && it < 5 }
        assertTrue(filteredIntEmpty.isEmpty)
    }

    @Test
    fun testMap() {
        val value = "testValue"
        val optionalWithValue = Optional.of(value)

        // Map with a function returning a non-null value
        val mappedValue = "mappedValue"
        val mappedOptional = optionalWithValue.map { mappedValue }
        assertTrue(mappedOptional.isPresent, "Map with non-null return should be present.")
        assertEquals(mappedValue, mappedOptional.get(), "Mapped value should be correct.")

        // Map with a function returning a null value
        val mappedToNullOptional = optionalWithValue.map<String?> { null }
        assertTrue(mappedToNullOptional.isEmpty, "Map with null return should be empty.")

        // Map on an empty Optional
        val emptyOptional = Optional.empty<String>()
        val mappedEmpty = emptyOptional.map { "newValue" } // Mapper should not be called
        assertTrue(mappedEmpty.isEmpty, "Map on empty Optional should return empty Optional.")

        // Map to a different type
        val optionalInt = Optional.of(5)
        val mappedToString = optionalInt.map { it.toString() }
        assertTrue(mappedToString.isPresent)
        assertEquals("5", mappedToString.get())
    }
}
