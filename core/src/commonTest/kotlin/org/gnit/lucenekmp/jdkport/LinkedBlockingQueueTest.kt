package org.gnit.lucenekmp.jdkport

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class LinkedBlockingQueueTest {
    @Test
    fun testOffer() {
        val queue = LinkedBlockingQueue<Int>(2)
        assertTrue(queue.offer(1))
        assertTrue(queue.offer(2))
        assertFalse(queue.offer(3)) // Queue should be full
    }

    @Test
    fun testPoll() {
        val queue = LinkedBlockingQueue<Int>(2)
        queue.offer(1)
        queue.offer(2)
        assertEquals(1, queue.poll())
        assertEquals(2, queue.poll())
        assertNull(queue.poll()) // Queue should be empty
    }

    @Test
    fun testPutAndTake() = runTest {
        val queue = LinkedBlockingQueue<Int>(1)
        launch {
            queue.put(1) // Should block until there's space
            queue.put(2) // Should block until there's space
        }
        delay(100.toDuration(DurationUnit.MILLISECONDS)) // Give time for put to block
        assertEquals(1, queue.take()) // Should unblock the first put
        assertEquals(2, queue.take()) // Should unblock the second put
    }

    @Test
    fun testOfferWithTimeout() = runTest {
        val queue = LinkedBlockingQueue<Int>(1)
        // Offer to a queue with space
        assertTrue(queue.offer(1, 100L, TimeUnit.MILLISECONDS))
        // Offer to a full queue (should time out)
        assertFalse(queue.offer(2, 100L, TimeUnit.MILLISECONDS))

        // Offer to a full queue, then an element is taken, then the offer succeeds.
        val q2 = LinkedBlockingQueue<Int>(1)
        assertTrue(q2.offer(10)) // Fill the queue
        assertFalse(q2.offer(11, 50L, TimeUnit.MILLISECONDS)) // Offer to full queue, should timeout

        launch {
            delay(20L) // Ensure offer is waiting
            assertEquals(10, q2.take()) // Make space
        }
        // Now the offer for 11 should succeed
        assertTrue(q2.offer(11, 100L, TimeUnit.MILLISECONDS))
        assertEquals(11, q2.poll()) // Check it was actually added
    }

    @Test
    fun testPollWithTimeout() = runTest {
        val queue = LinkedBlockingQueue<Int>(1)
        // Poll from non-empty queue with timeout (should return immediately)
        queue.put(1)
        assertEquals(1, queue.poll(100L, TimeUnit.MILLISECONDS))

        // Poll from an empty queue (should time out)
        assertNull(queue.poll(100L, TimeUnit.MILLISECONDS))

        // Poll from an empty queue, then an element is put, then the poll succeeds.
        val q2 = LinkedBlockingQueue<Int>(1)
        launch {
            delay(50L) // Ensure poll is waiting
            q2.put(2) // Add an element
        }
        assertEquals(2, q2.poll(100L, TimeUnit.MILLISECONDS)) // Should succeed now
        assertNull(q2.poll(1L, TimeUnit.MILLISECONDS)) // Ensure it's empty again
    }

    @Test
    fun testTakeSuspendsOnEmptyAndResumes() = runTest {
        val queue = LinkedBlockingQueue<Int>(1)
        var result: Int? = null

        launch {
            result = queue.take() // Should suspend as queue is empty
        }

        delay(100L) // Give time for take() to suspend
        assertNull(result, "Take should be suspended and result not yet set")

        queue.put(1) // Put an element into the queue

        delay(100L) // Give time for take() to resume and process
        assertEquals(1, result, "Take should have resumed and returned the element 1")
    }

    @Test
    fun testRemainingCapacity() {
        val queue = LinkedBlockingQueue<Int>(3)
        assertEquals(3, queue.remainingCapacity())
        queue.offer(1)
        assertEquals(2, queue.remainingCapacity())
        queue.offer(2)
        assertEquals(1, queue.remainingCapacity())
        queue.offer(3)
        assertEquals(0, queue.remainingCapacity())
    }

    @Test
    fun testClear() {
        val queue = LinkedBlockingQueue<Int>(3)
        queue.offer(1)
        queue.offer(2)
        queue.offer(3)
        assertFalse(queue.isEmpty())
        queue.clear()
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size)
        assertEquals(3, queue.remainingCapacity()) // Capacity should remain the same
        assertNull(queue.poll()) // Queue should be empty
    }

    @Test
    fun testPeek() {
        val queue = LinkedBlockingQueue<Int>(2)
        assertNull(queue.peek()) // Empty queue
        queue.offer(1)
        assertEquals(1, queue.peek())
        assertEquals(1, queue.peek()) // Peek should not remove the element
        queue.offer(2)
        assertEquals(1, queue.peek()) // Still the first element
        queue.poll()
        assertEquals(2, queue.peek())
        queue.poll()
        assertNull(queue.peek()) // Empty queue
    }

    @Test
    fun testUnboundedQueue() {
        val queue = LinkedBlockingQueue<Int>() // Default constructor uses Int.MAX_VALUE
        assertEquals(Int.MAX_VALUE, queue.remainingCapacity())
        for (i in 1..100) {
            assertTrue(queue.offer(i))
        }
        assertEquals(Int.MAX_VALUE - 100, queue.remainingCapacity())
        for (i in 1..100) {
            assertEquals(i, queue.poll())
        }
        assertEquals(Int.MAX_VALUE, queue.remainingCapacity())
    }

    @Test
    fun testConstructorWithCapacity() {
        val queue = LinkedBlockingQueue<Int>(5)
        assertEquals(0, queue.size)
        assertEquals(5, queue.remainingCapacity())

        assertFailsWith<IllegalArgumentException> {
            LinkedBlockingQueue<Int>(0)
        }
        assertFailsWith<IllegalArgumentException> {
            LinkedBlockingQueue<Int>(-1)
        }
    }

    @Test
    fun testConstructorWithCollection() {
        val initialElements = listOf(1, 2, 3)
        val queue = LinkedBlockingQueue<Int>(initialElements.toMutableList())
        assertEquals(3, queue.size)
        // This constructor creates a queue with Int.MAX_VALUE capacity
        // and then adds all elements from the collection.
        assertEquals(Int.MAX_VALUE - 3, queue.remainingCapacity())
        assertEquals(1, queue.poll())
        assertEquals(2, queue.poll())
        assertEquals(3, queue.poll())
        assertNull(queue.poll())

        val emptyQueue = LinkedBlockingQueue<Int>(mutableListOf())
        assertEquals(0, emptyQueue.size)
        assertEquals(Int.MAX_VALUE, emptyQueue.remainingCapacity())
    }

    @Test
    fun testAdd() {
        val queue = LinkedBlockingQueue<String>(1)
        assertTrue(queue.add("element1"))
        assertEquals("element1", queue.peek())
        assertEquals(1, queue.size)

        assertFailsWith<IllegalStateException>("Queue full") {
            queue.add("element2")
        }
        assertEquals("element1", queue.peek()) // First element still there
        assertEquals(1, queue.size) // Size is still 1
        assertFalse(queue.contains("element2"))
    }

    @Test
    fun testRemoveNoArg() {
        val emptyQueue = LinkedBlockingQueue<String>()
        assertFailsWith<NoSuchElementException> {
            emptyQueue.remove()
        }

        val queue = LinkedBlockingQueue<String>(2)
        queue.add("A")
        queue.add("B")

        assertEquals("A", queue.remove())
        assertEquals(1, queue.size)
        assertEquals("B", queue.peek())

        assertEquals("B", queue.remove())
        assertEquals(0, queue.size)
        assertTrue(queue.isEmpty())

        assertFailsWith<NoSuchElementException> {
            queue.remove()
        }
    }

    @Test
    fun testElement() {
        val emptyQueue = LinkedBlockingQueue<String>()
        assertFailsWith<NoSuchElementException> {
            emptyQueue.element()
        }

        val queue = LinkedBlockingQueue<String>(2)
        queue.add("A")
        assertEquals("A", queue.element())
        assertEquals(1, queue.size) // Element not removed
        assertEquals("A", queue.peek()) // Peek confirms

        queue.add("B")
        assertEquals("A", queue.element()) // Still returns the head
        assertEquals(2, queue.size)
    }

    @Test
    fun testRemoveObject() {
        val queue = LinkedBlockingQueue<String>(5)
        queue.addAll(listOf("A", "B", "C", "B", "D")) // A, B, C, B, D

        assertTrue(queue.remove("B")) // Removes first "B"
        assertEquals(4, queue.size)
        assertEquals("[A, C, B, D]", queue.toString())

        assertTrue(queue.remove("B")) // Removes second "B"
        assertEquals(3, queue.size)
        assertEquals("[A, C, D]", queue.toString())

        assertFalse(queue.remove("X")) // Non-existent
        assertEquals(3, queue.size)

        val emptyQ = LinkedBlockingQueue<String>()
        assertFalse(emptyQ.remove("A")) // Remove from empty

        // Test removing head
        assertTrue(queue.remove("A"))
        assertEquals(2, queue.size)
        assertEquals("[C, D]", queue.toString())

        // Test removing tail
        assertTrue(queue.remove("D"))
        assertEquals(1, queue.size)
        assertEquals("[C]", queue.toString())
    }

    @Test
    fun testContains() {
        val emptyQueue = LinkedBlockingQueue<String>()
        assertFalse(emptyQueue.contains("A"))

        val queue = LinkedBlockingQueue<String>()
        queue.addAll(listOf("A", "B", "C"))
        assertTrue(queue.contains("A"))
        assertTrue(queue.contains("B"))
        assertTrue(queue.contains("C"))
        assertFalse(queue.contains("D"))
    }

    @Test
    fun testDrainTo() {
        val source = LinkedBlockingQueue<Int>()
        source.addAll(listOf(1, 2, 3))
        val destination = mutableListOf<Int>()

        assertEquals(3, source.drainTo(destination))
        assertTrue(source.isEmpty())
        assertEquals(0, source.size)
        assertEquals(listOf(1, 2, 3), destination)

        val emptySource = LinkedBlockingQueue<Int>()
        val dest2 = mutableListOf<Int>()
        assertEquals(0, emptySource.drainTo(dest2))
        assertTrue(dest2.isEmpty())
    }

    @Test
    fun testDrainToWithMaxElements() {
        val source = LinkedBlockingQueue<Int>()
        source.addAll(listOf(1, 2, 3, 4, 5))
        val destination = mutableListOf<Int>()

        assertEquals(3, source.drainTo(destination, 3))
        assertEquals(2, source.size)
        assertEquals(listOf(4, 5), source.toList())
        assertEquals(listOf(1, 2, 3), destination)

        // Drain 0 elements
        val dest2 = mutableListOf<Int>()
        assertEquals(0, source.drainTo(dest2, 0))
        assertEquals(2, source.size) // Unchanged
        assertTrue(dest2.isEmpty())

        // Drain more than available
        val dest3 = mutableListOf<Int>()
        assertEquals(2, source.drainTo(dest3, 5))
        assertTrue(source.isEmpty())
        assertEquals(listOf(4, 5), dest3)

        // Drain to self
        val q = LinkedBlockingQueue<Int>()
        q.add(1)
        assertFailsWith<IllegalArgumentException>("Can't drain to self") {
            q.drainTo(q)
        }
        assertFailsWith<IllegalArgumentException>("Can't drain to self with max elements") {
            q.drainTo(q,1)
        }
    }

    @Test
    fun testIterator() {
        val emptyQ = LinkedBlockingQueue<String>()
        val emptyIter = emptyQ.iterator()
        assertFalse(emptyIter.hasNext())
        assertFailsWith<NoSuchElementException> { emptyIter.next() }

        val q = LinkedBlockingQueue<String>()
        q.addAll(listOf("A", "B", "C"))
        val iter = q.iterator()

        assertTrue(iter.hasNext())
        assertEquals("A", iter.next())
        assertTrue(iter.hasNext())
        assertEquals("B", iter.next())
        assertTrue(iter.hasNext())
        assertEquals("C", iter.next())
        assertFalse(iter.hasNext())
        assertFailsWith<NoSuchElementException> { iter.next() }

        // Test iterator remove
        val q2 = LinkedBlockingQueue<String>()
        q2.addAll(listOf("X", "Y", "Z"))
        val iter2 = q2.iterator()

        assertFailsWith<IllegalStateException>("remove before next") { iter2.remove() }

        assertEquals("X", iter2.next())
        iter2.remove() // Remove X
        assertEquals(listOf("Y", "Z"), q2.toList())
        assertFailsWith<IllegalStateException>("remove twice after one next") { iter2.remove() }

        assertEquals("Y", iter2.next())
        // iter2.remove(); // Remove Y - not doing this to test removing last element
        assertEquals("Z", iter2.next())
        iter2.remove() // Remove Z
        assertEquals(listOf("Y"), q2.toList())
        assertFalse(iter2.hasNext())
    }

    @Test
    fun testToString() {
        val q = LinkedBlockingQueue<String>()
        assertEquals("[]", q.toString())
        q.add("A")
        assertEquals("[A]", q.toString())
        q.add("B")
        q.add("C")
        assertEquals("[A, B, C]", q.toString())
    }

    @Test
    fun testSize() {
        val q1 = LinkedBlockingQueue<Int>(5)
        assertEquals(0, q1.size)
        q1.offer(1)
        assertEquals(1, q1.size)
        q1.offer(2)
        assertEquals(2, q1.size)
        q1.offer(3)
        assertEquals(3, q1.size)
        assertEquals(1, q1.poll())
        assertEquals(2, q1.size)
        q1.offer(4)
        assertEquals(3, q1.size)
        q1.offer(5)
        assertEquals(4, q1.size)
        q1.offer(6)
        assertEquals(5, q1.size)
        assertFalse(q1.offer(7)) // q1 is full
        assertEquals(5, q1.size)

        assertEquals(2, q1.poll())
        assertEquals(4, q1.size)
        assertEquals(3, q1.poll())
        assertEquals(3, q1.size)
        assertEquals(4, q1.poll())
        assertEquals(2, q1.size)
        assertEquals(5, q1.poll())
        assertEquals(1, q1.size)
        assertEquals(6, q1.poll())
        assertEquals(0, q1.size)
        assertNull(q1.poll())
        assertEquals(0, q1.size)

        val q2 = LinkedBlockingQueue<Int>() // Unbounded
        assertEquals(false, q2.isBounded)
        assertEquals(0, q2.size)
        q2.offer(10)
        assertEquals(1, q2.size)
        assertEquals(10, q2.poll())
        assertEquals(0, q2.size)
    }
}
