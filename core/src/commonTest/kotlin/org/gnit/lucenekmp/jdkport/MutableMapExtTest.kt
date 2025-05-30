package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue 
import kotlin.test.assertFalse 
import kotlin.test.assertFailsWith // Added for exception testing
// No explicit import for HashMap needed, it's in kotlin.collections

class MutableMapExtTest {

    @Test
    fun testPutIfAbsent_nonNullValues_and_returnTypeChecks() {
        val map = mutableMapOf<String, String>()

        // Case 1: Key not present
        val previousValue1 = map.putIfAbsent("key1", "value1")
        assertNull(previousValue1, "Return value should be null when key is not present")
        assertEquals("value1", map["key1"], "Value should be added when key is not present")

        // Case 2: Key present
        val previousValue2 = map.putIfAbsent("key1", "value2")
        assertEquals("value1", previousValue2, "Return value should be the existing value when key is present")
        assertEquals("value1", map["key1"], "Value should not change when key is present")
    }

    @Test
    fun testPutIfAbsent_withNullValues_keyNotPresent_addNonNull() {
        val map = HashMap<String, String?>()
        val result = map.putIfAbsent("key1", "value1")
        assertNull(result, "Return value should be null as key wasn't present")
        assertEquals("value1", map["key1"], "New non-null value should be associated with key1")
    }

    @Test
    fun testPutIfAbsent_withNullValues_keyNotPresent_addNull() {
        val map = HashMap<String, String?>()
        val result = map.putIfAbsent("key1", null)
        assertNull(result, "Return value should be null as key wasn't present")
        assertTrue(map.containsKey("key1"), "Map should contain key1 after adding null")
        assertNull(map["key1"], "Value associated with key1 should be null")
    }

    @Test
    fun testPutIfAbsent_withNullValues_keyPresentWithNull_addNonNull() {
        val map = HashMap<String, String?>()
        map["key1"] = null // Key present with null value

        val result = map.putIfAbsent("key1", "valueNew")
        assertNull(result, "Return value should be the previous value (null) as key was present with null")
        assertEquals("valueNew", map["key1"], "Value for key1 should be updated to 'valueNew'")
    }


    @Test
    fun testPutIfAbsent_withNullValues_keyPresentWithNonNull_addNonNull() {
        val map = HashMap<String, String?>()
        map["key1"] = "valueOld" // Key present with non-null value

        val result = map.putIfAbsent("key1", "valueNew")
        assertEquals("valueOld", result, "Return value should be 'valueOld' as key was present")
        assertEquals("valueOld", map["key1"], "Value for key1 should remain 'valueOld'")
    }

    @Test
    fun testPutIfAbsent_withNullValues_keyPresentWithNull_addNull() {
        val map = HashMap<String, String?>()
        map["key1"] = null // Key present with null value

        val result = map.putIfAbsent("key1", null) 
        assertNull(result, "Return value should be the previous value (null)")
        assertTrue(map.containsKey("key1"), "Map should still contain key1")
        assertNull(map["key1"], "Value for key1 should be updated to null (effectively unchanged if it was already null, but the action occurs)")
    }

    @Test
    fun testRemove_nonNullValues() { 
        val map = mutableMapOf("key1" to "value1", "key2" to "value2")
        
        val removed1 = map.remove("key1", "value1") 
        assertTrue(removed1, "Should return true when key-value pair is removed")
        assertFalse(map.containsKey("key1"), "key1 should be removed") 

        val removed2 = map.remove("key2", "value_non_existent") 
        assertFalse(removed2, "Should return false when value does not match for removal")
        assertEquals("value2", map["key2"], "key2 should still be present if value didn't match")
        
        val removed3 = map.remove("key_non_existent", "value1")
        assertFalse(removed3, "Should return false when key does not exist for removal")
    }

    @Test
    fun testRemove_withNullValues_keyExists_valueIsNull_match() {
        val map = HashMap<String, String?>()
        map["key1"] = null
        
        val result = map.remove("key1", null) 
        
        assertTrue(result, "remove(\"key1\", null) should return true if map has (key1, null)")
        assertFalse(map.containsKey("key1"), "key1 should be removed from map")
    }

    @Test
    fun testRemove_withNullValues_keyExists_valueIsNull_mismatchNonNull() {
        val map = HashMap<String, String?>()
        map["key1"] = null
        
        val result = map.remove("key1", "value1") 
        
        assertFalse(result, "remove(\"key1\", \"value1\") should return false if map has (key1, null)")
        assertTrue(map.containsKey("key1"), "key1 should still be in map")
        assertNull(map["key1"], "Value for key1 should still be null")
    }

    @Test
    fun testRemove_withNullValues_keyExists_valueIsNonNull_mismatchNull() {
        val map = HashMap<String, String?>()
        map["key1"] = "value1"
        
        val result = map.remove("key1", null) 
        
        assertFalse(result, "remove(\"key1\", null) should return false if map has (key1, \"value1\")")
        assertTrue(map.containsKey("key1"), "key1 should still be in map")
        assertEquals("value1", map["key1"], "Value for key1 should still be \"value1\"")
    }

    @Test
    fun testRemove_withNullValues_keyNotExists_valueIsNull() {
        val map = HashMap<String, String?>()
        
        val result = map.remove("keyNonExistent", null)
        
        assertFalse(result, "remove(\"keyNonExistent\", null) should return false")
        assertTrue(map.isEmpty(), "Map should remain empty")
    }

    @Test
    fun testReplace_nonNullValues() { 
        val map = mutableMapOf("key1" to "value1")
        val prevVal1 = map.replace("key1", "value2")
        assertEquals("value1", prevVal1, "Replace non-null should return the old value")
        assertEquals("value2", map["key1"], "key1 should be updated to value2 for non-null replace")

        val prevVal2 = map.replace("key2", "value3") 
        assertNull(prevVal2, "Replace non-null should return null if key does not exist")
        assertFalse(map.containsKey("key2"), "key2 should not be added by non-null replace")
    }

    @Test
    fun testReplace_withNullValues_keyExists_nonNullToNull() {
        val map = HashMap<String, String?>()
        map["key1"] = "valueOld"
        
        val result = map.replace("key1", null)
        
        assertEquals("valueOld", result, "replace(key, null) should return 'valueOld'")
        assertTrue(map.containsKey("key1"), "key1 should still be present")
        assertNull(map["key1"], "Value of key1 should now be null")
    }

    @Test
    fun testReplace_withNullValues_keyExists_nullToNonNull() {
        val map = HashMap<String, String?>()
        map["key1"] = null
        
        val result = map.replace("key1", "valueNew")
        
        assertNull(result, "replace(key, \"valueNew\") should return previous null value")
        assertTrue(map.containsKey("key1"), "key1 should still be present")
        assertEquals("valueNew", map["key1"], "Value of key1 should now be 'valueNew'")
    }

    @Test
    fun testReplace_withNullValues_keyExists_nullToNull() {
        val map = HashMap<String, String?>()
        map["key1"] = null
        
        val result = map.replace("key1", null)
        
        assertNull(result, "replace(key, null) should return previous null value")
        assertTrue(map.containsKey("key1"), "key1 should still be present")
        assertNull(map["key1"], "Value of key1 should still be null")
    }

    @Test
    fun testReplace_withNullValues_keyNotExists_replaceWithNull() {
        val map = HashMap<String, String?>()
        
        val result = map.replace("keyNonExistent", null)
        
        assertNull(result, "replace for non-existent key should return null")
        assertFalse(map.containsKey("keyNonExistent"), "Non-existent key should not be added by replace")
        assertTrue(map.isEmpty(), "Map should remain empty")
    }

    @Test
    fun testReplace_withNullValues_keyNotExists_replaceWithNonNull() {
        val map = HashMap<String, String?>()

        val result = map.replace("keyNonExistent", "valueNew")

        assertNull(result, "replace for non-existent key (non-null val) should return null")
        assertFalse(map.containsKey("keyNonExistent"), "Non-existent key should not be added by replace (non-null val)")
        assertTrue(map.isEmpty(), "Map should remain empty")
    }

    // --- Tests for computeIfAbsent(key: K, mappingFunction: (K)->V) ---

    @Test
    fun testComputeIfAbsent_keyNotPresent_computesNonNull() {
        val map = HashMap<String, String?>()
        var functionCalled = false
        val result = map.computeIfAbsent("key1") { _ -> 
            functionCalled = true
            "valueComputed" 
        }
        assertTrue(functionCalled, "Mapping function should be called when key is not present")
        assertEquals("valueComputed", result, "Result should be the computed value")
        assertEquals("valueComputed", map["key1"], "Map should contain the computed value for the key")
    }

    @Test
    fun testComputeIfAbsent_keyNotPresent_computesNull() {
        val map = HashMap<String, String?>()
        var functionCalled = false
        val result = map.computeIfAbsent("key1") { _ ->
            functionCalled = true
            null // Mapping function returns null
        }
        assertTrue(functionCalled, "Mapping function should be called even if it returns null")
        assertNull(result, "Result should be null as computed by the function")
        // As per Java spec, if mapping function returns null, no mapping is recorded.
        assertFalse(map.containsKey("key1"), "Map should not contain key if mapping function returned null")
    }

    @Test
    fun testComputeIfAbsent_keyPresent_valueNonNull() {
        val map = HashMap<String, String?>()
        map["key1"] = "valueExisting"
        var functionCalled = false
        val result = map.computeIfAbsent("key1") { _ ->
            functionCalled = true
            "valueComputed"
        }
        assertFalse(functionCalled, "Mapping function should not be called when key is present with non-null value")
        assertEquals("valueExisting", result, "Result should be the existing value")
        assertEquals("valueExisting", map["key1"], "Map value should remain unchanged")
    }

    @Test
    fun testComputeIfAbsent_keyPresent_valueIsNull_computesNonNull() {
        val map = HashMap<String, String?>()
        map["key1"] = null // Key is present, but its value is null
        var functionCalled = false
        // As per Java spec, if key is present and value is null, function IS called.
        val result = map.computeIfAbsent("key1") { _ ->
            functionCalled = true
            "valueComputed"
        }
        assertTrue(functionCalled, "Mapping function should be called when key is present but value is null")
        assertEquals("valueComputed", result, "Result should be the newly computed value")
        assertEquals("valueComputed", map["key1"], "Map should be updated with the computed value")
    }

    @Test
    fun testComputeIfAbsent_keyPresent_valueIsNull_computesNull() {
        val map = HashMap<String, String?>()
        map["key1"] = null // Key is present, value is null
        var functionCalled = false
        // As per Java spec, if key is present and value is null, function IS called.
        // If function returns null, the mapping remains (key, null).
        val result = map.computeIfAbsent("key1") { _ ->
            functionCalled = true
            null 
        }
        assertTrue(functionCalled, "Mapping function should be called when key is present (value null), even if function returns null")
        assertNull(result, "Result should be null as computed by the function")
        assertTrue(map.containsKey("key1"), "Map should still contain the key")
        assertNull(map["key1"], "Value in map should remain null as function returned null")
    }

    @Test
    fun testComputeIfAbsent_functionThrowsException() {
        val map = HashMap<String, String?>()
        val exception = assertFailsWith<IllegalStateException>("Should rethrow exception from mapping function") {
            map.computeIfAbsent("key1") { _ ->
                throw IllegalStateException("Test exception")
            }
        }
        assertEquals("Test exception", exception.message, "Exception message should match")
        assertFalse(map.containsKey("key1"), "Map should not contain key if mapping function threw exception")
    }
}
