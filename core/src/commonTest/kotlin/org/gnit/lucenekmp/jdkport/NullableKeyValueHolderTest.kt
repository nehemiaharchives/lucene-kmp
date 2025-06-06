package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

private val logger = KotlinLogging.logger {}

class NullableKeyValueHolderTest {

    @Test
    fun testGetKey() {
        val holder = NullableKeyValueHolder("key", "value")
        assertEquals("key", holder.key)

        val nullKeyHolder = NullableKeyValueHolder(null, "value")
        assertNull(nullKeyHolder.key)
    }

    @Test
    fun testGetValue() {
        val holder = NullableKeyValueHolder("key", "value")
        assertEquals("value", holder.value)

        val nullValueHolder = NullableKeyValueHolder("key", null)
        assertNull(nullValueHolder.value)
    }

    @Test
    fun testSetValue() {
        val holder = NullableKeyValueHolder("key", "value")
        assertFailsWith<UnsupportedOperationException> {
            holder.setValue("newValue")
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val holder1 = NullableKeyValueHolder("k", "v")
        val holder2 = NullableKeyValueHolder("k", "v")
        assertEquals(holder1, holder2)
        assertEquals(holder1.hashCode(), holder2.hashCode())

        val holder3 = NullableKeyValueHolder<String?, String?>(null, null)
        val holder4 = NullableKeyValueHolder<String?, String?>(null, null)
        assertEquals(holder3, holder4)
        assertEquals(holder3.hashCode(), holder4.hashCode())

        assertNotEquals(holder1, holder3)
        logger.debug { "equals and hashCode tested" }
    }

    @Test
    fun testToString() {
        val holder = NullableKeyValueHolder("a", "b")
        assertEquals("a=b", holder.toString())

        val nullHolder = NullableKeyValueHolder<String?, String?>(null, null)
        assertEquals("null=null", nullHolder.toString())
    }

    @Test
    fun testEntryConstructor() {
        val map = mutableMapOf("x" to "y")
        val entry = map.entries.first()
        val holder = NullableKeyValueHolder(entry)
        assertEquals("x", holder.key)
        assertEquals("y", holder.value)

        val nullMap = mutableMapOf<String?, String?>()
        nullMap[null] = null
        val nullEntry = nullMap.entries.first()
        val nullHolder = NullableKeyValueHolder(nullEntry)
        assertNull(nullHolder.key)
        assertNull(nullHolder.value)
    }
}
