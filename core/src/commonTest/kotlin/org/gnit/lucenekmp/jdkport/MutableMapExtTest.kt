package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MutableMapExtTest {

    @Test
    fun testPutIfAbsent() {
        val map = mutableMapOf<String, String>()
        map.putIfAbsent("key1", "value1")
        assertEquals("value1", map["key1"])

        map.putIfAbsent("key1", "value2")
        assertEquals("value1", map["key1"])
    }

    @Test
    fun testRemove() {
        val map = mutableMapOf("key1" to "value1", "key2" to "value2")
        map.remove("key1", "value1")
        assertNull(map["key1"])

        map.remove("key2", "value1")
        assertEquals("value2", map["key2"])
    }

    @Test
    fun testReplace() {
        val map = mutableMapOf("key1" to "value1")
        map.replace("key1", "value2")
        assertEquals("value2", map["key1"])

        map.replace("key2", "value3")
        assertNull(map["key2"])
    }
}
