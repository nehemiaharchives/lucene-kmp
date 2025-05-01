package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicReferenceExtTest {

    @Test
    fun testGet() {
        val atomicReference = AtomicReference("Hello")
        assertEquals("Hello", atomicReference.get())
    }
}
