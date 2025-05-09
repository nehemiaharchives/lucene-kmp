package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceQueueTest {

    @Test
    fun testPoll() {
        val queue = ReferenceQueue<String>()
        val ref = Reference("test", queue)
        ref.enqueue()
        assertTrue(queue.poll() === ref)
        assertNull(queue.poll())
    }

    @Test
    fun testRemove() {
        val queue = ReferenceQueue<String>()
        val ref = Reference("test", queue)
        ref.enqueue()
        assertTrue(queue.remove() === ref)
        assertNull(queue.remove())
    }
}
