package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReferenceTest {

    @Test
    fun testGet() {
        val referent = "test"
        val reference = Reference(referent)
        assertEquals(referent, reference.get())
    }

    @Test
    fun testClear() {
        val referent = "test"
        val reference = Reference(referent)
        reference.clear()
        assertNull(reference.get())
    }
}
