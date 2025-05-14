package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
}
