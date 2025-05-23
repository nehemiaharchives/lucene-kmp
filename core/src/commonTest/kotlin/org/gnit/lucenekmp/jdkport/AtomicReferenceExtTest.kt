package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicReferenceExtTest {

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testGet() {
        val atomicReference = AtomicReference("Hello")
        assertEquals("Hello", atomicReference.get())
    }
}
