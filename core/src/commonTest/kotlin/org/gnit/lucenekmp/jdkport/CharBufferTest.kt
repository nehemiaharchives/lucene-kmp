package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.*

private val logger = KotlinLogging.logger {}

class CharBufferTest {

    @Test
    fun testAllocateBasic() {
        logger.debug { "Testing CharBuffer.allocate() with positive capacity" }
        val capacity = 10
        val buffer = CharBuffer.allocate(capacity)
        assertEquals(capacity, buffer.capacity, "Capacity should be as allocated")
        assertEquals(0, buffer.position(), "Position should be 0 initially")
        assertEquals(capacity, buffer.limit, "Limit should be equal to capacity initially")
        assertTrue(buffer.hasArray(), "Buffer should have an array")
        assertFalse(buffer.isReadOnly(), "Buffer should not be read-only by default")
    }

    @Test
    fun testAllocateZeroCapacity() {
        logger.debug { "Testing CharBuffer.allocate() with zero capacity" }
        val capacity = 0
        val buffer = CharBuffer.allocate(capacity)
        assertEquals(capacity, buffer.capacity, "Capacity should be 0")
        assertEquals(0, buffer.position(), "Position should be 0 initially")
        assertEquals(capacity, buffer.limit, "Limit should be 0 initially")
    }

    @Test
    fun testAllocateNegativeCapacity() {
        logger.debug { "Testing CharBuffer.allocate() with negative capacity" }
        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for negative capacity") {
            CharBuffer.allocate(-1)
        }
    }

    @Test
    fun testWrapCharArrayBasic() {
        logger.debug { "Testing CharBuffer.wrap(CharArray) basic" }
        val charArray = charArrayOf('a', 'b', 'c', 'd', 'e')
        val buffer = CharBuffer.wrap(charArray)

        assertEquals(charArray.size, buffer.capacity, "Capacity should be array size")
        assertEquals(0, buffer.position(), "Position should be 0")
        assertEquals(charArray.size, buffer.limit, "Limit should be array size")
        assertTrue(buffer.hasArray(), "Buffer should have an array")
        assertFalse(buffer.isReadOnly(), "Buffer should not be read-only")
        assertSame(charArray, buffer.array(), "array() should return the original array")
        assertEquals(0, buffer.arrayOffset(), "arrayOffset() should be 0 for basic wrap")

        // Test relative get
        for (i in charArray.indices) {
            assertEquals(charArray[i], buffer.get(), "Relative get at index $i should match original array")
        }
        // Test absolute get after resetting position
        buffer.position = 0 // Reset position
        for (i in charArray.indices) {
            assertEquals(charArray[i], buffer.getAbsolute(i), "Absolute get at index $i should match original array")
        }
        assertEquals(0, buffer.position(), "Position should remain unchanged after absolute gets")
    }

    @Test
    fun testWrapCharArrayWithOffsetAndLength() {
        logger.debug { "Testing CharBuffer.wrap(CharArray, offset, length)" }
        val charArray = charArrayOf('w', 'x', 'y', 'z')
        val offset = 1
        val length = 2 // Should wrap 'x', 'y'
        val buffer = CharBuffer.wrap(charArray, offset, length)

        assertEquals(length, buffer.capacity, "Capacity should be segment length")
        assertEquals(0, buffer.position(), "Position should be 0")
        assertEquals(length, buffer.limit, "Limit should be segment length")
        assertFalse(buffer.isReadOnly(), "Buffer should not be read-only")
        assertTrue(buffer.hasArray(), "Buffer should have an array")
        assertSame(charArray, buffer.array(), "array() should return the original array")
        assertEquals(offset, buffer.arrayOffset(), "arrayOffset() should be the specified offset")

        assertEquals('x', buffer.get(), "First char from relative get should be array[offset]")
        assertEquals('y', buffer.get(), "Second char from relative get should be array[offset + 1]")
        assertFalse(buffer.hasRemaining(), "Buffer should have no chars remaining after gets")

        buffer.position = 0 // Reset position
        assertEquals('x', buffer.getAbsolute(0), "Absolute get at index 0 should be array[offset]")
        assertEquals('y', buffer.getAbsolute(1), "Absolute get at index 1 should be array[offset + 1]")
        assertEquals(0, buffer.position(), "Position should not change after absolute get")
    }

    @Test
    fun testWrapCharArrayInvalidOffsetLength() {
        logger.debug { "Testing CharBuffer.wrap(CharArray) with invalid offset/length" }
        val charArray = charArrayOf('a', 'b', 'c', 'd', 'e')

        assertFailsWith<IndexOutOfBoundsException>("Negative offset") {
            CharBuffer.wrap(charArray, -1, 2)
        }
        assertFailsWith<IndexOutOfBoundsException>("Negative length") {
            CharBuffer.wrap(charArray, 0, -1)
        }
        assertFailsWith<IndexOutOfBoundsException>("offset + length > array.size") {
            CharBuffer.wrap(charArray, 1, charArray.size) // This will be offset 1 + length 5 > 5
        }
        // A more direct test for offset + length > array.size
        if (charArray.size > 1) { // Avoid issues with empty or very small arrays for this specific test line
            assertFailsWith<IndexOutOfBoundsException>("offset + length > array.size direct") {
                CharBuffer.wrap(charArray, charArray.size / 2, charArray.size / 2 + charArray.size) // Ensure length makes it exceed
            }
        }
        assertFailsWith<IndexOutOfBoundsException>("offset > array.size") {
            CharBuffer.wrap(charArray, charArray.size + 1, 0)
        }
        // Valid case: wrap empty segment at the end
        CharBuffer.wrap(charArray, charArray.size, 0)
    }

    @Test
    fun testWrapCharSequenceBasic() {
        logger.debug { "Testing CharBuffer.wrap(CharSequence) basic" }
        val sequence: CharSequence = "hello"
        val buffer = CharBuffer.wrap(sequence)

        assertEquals(sequence.length, buffer.capacity, "Capacity should be sequence length")
        assertEquals(0, buffer.position(), "Position should be 0")
        assertEquals(sequence.length, buffer.limit, "Limit should be sequence length")
        assertTrue(buffer.isReadOnly(), "Buffer from CharSequence should be read-only")
        assertFalse(buffer.hasArray(), "Read-only buffer from CharSequence should report !hasArray() due to readOnly flag")
        assertFailsWith<ReadOnlyBufferException>("array() should throw ReadOnlyBufferException for read-only buffer") {
            buffer.array()
        }
        assertEquals(0, buffer.arrayOffset(), "arrayOffset() should be 0 for new array from CharSequence")

        // Test relative get
        for (i in sequence.indices) {
            assertEquals(sequence[i], buffer.get(), "Relative get at index $i should match original sequence")
        }
        // Test absolute get after resetting position
        buffer.position = 0 // Reset position
        for (i in sequence.indices) {
            assertEquals(sequence[i], buffer.getAbsolute(i), "Absolute get at index $i should match original sequence")
        }
        assertEquals(0, buffer.position(), "Position should remain unchanged after absolute gets")
    }

    @Test
    fun testWrapCharSequenceWithStartAndEnd() {
        logger.debug { "Testing CharBuffer.wrap(CharSequence, start, end)" }
        val sequence: CharSequence = "abcdefgh"
        val start = 2 // 'c'
        val end = 5   // up to 'f' (exclusive), so 'c', 'd', 'e'
        val wrappedLength = end - start // 3. Characters are 'c', 'd', 'e'
        val buffer = CharBuffer.wrap(sequence, start, end)

        assertEquals(wrappedLength, buffer.capacity, "Capacity should be subsequence length")
        assertEquals(0, buffer.position(), "Position should be 0")
        assertEquals(wrappedLength, buffer.limit, "Limit should be subsequence length")
        assertTrue(buffer.isReadOnly(), "Buffer from CharSequence should be read-only")
        assertFalse(buffer.hasArray(), "Read-only buffer from CharSequence should report !hasArray()")
        assertFailsWith<ReadOnlyBufferException>("array() should throw ReadOnlyBufferException for read-only buffer") {
            buffer.array()
        }
        assertEquals(0, buffer.arrayOffset(), "arrayOffset() should be 0 for new array from CharSequence")

        assertEquals('c', buffer.get(), "First char from relative get")
        assertEquals('d', buffer.get(), "Second char from relative get")
        assertEquals('e', buffer.get(), "Third char from relative get")
        assertFalse(buffer.hasRemaining(), "Buffer should have no chars remaining after gets")

        buffer.position = 0 // Reset position
        assertEquals('c', buffer.getAbsolute(0), "Absolute get at index 0")
        assertEquals('d', buffer.getAbsolute(1), "Absolute get at index 1")
        assertEquals('e', buffer.getAbsolute(2), "Absolute get at index 2")
        assertEquals(0, buffer.position(), "Position should not change after absolute get")
    }

    @Test
    fun testWrapCharSequenceInvalidStartEnd() {
        logger.debug { "Testing CharBuffer.wrap(CharSequence) with invalid start/end" }
        val sequence: CharSequence = "hello"

        assertFailsWith<IndexOutOfBoundsException>("Negative start") {
            CharBuffer.wrap(sequence, -1, 2)
        }
        assertFailsWith<IndexOutOfBoundsException>("end > sequence.length") {
            CharBuffer.wrap(sequence, 0, sequence.length + 1)
        }
        assertFailsWith<IndexOutOfBoundsException>("start > end") {
            CharBuffer.wrap(sequence, 3, 2)
        }
        assertFailsWith<IndexOutOfBoundsException>("start > sequence.length") {
            CharBuffer.wrap(sequence, sequence.length + 1, sequence.length + 1)
        }
        // Valid case: wrap empty segment at the end
        CharBuffer.wrap(sequence, sequence.length, sequence.length)
    }

    @Test
    fun testGetBasic() {
        logger.debug { "Testing CharBuffer.get() basic functionality" }
        val buffer = CharBuffer.wrap(charArrayOf('a', 'b', 'c'))
        assertEquals(0, buffer.position(), "Initial position should be 0")

        assertEquals('a', buffer.get(), "First get()")
        assertEquals(1, buffer.position(), "Position after first get()")

        assertEquals('b', buffer.get(), "Second get()")
        assertEquals(2, buffer.position(), "Position after second get()")

        assertEquals('c', buffer.get(), "Third get()")
        assertEquals(3, buffer.position(), "Position after third get()")
        assertFalse(buffer.hasRemaining(), "Buffer should be empty after reading all chars")
    }

    @Test
    fun testGetUnderflow() {
        logger.debug { "Testing CharBuffer.get() underflow" }
        val buffer = CharBuffer.wrap(charArrayOf('x', 'y'))
        buffer.position = buffer.limit // Move position to the limit

        assertFailsWith<BufferUnderflowException>("get() at limit should throw BufferUnderflowException") {
            buffer.get()
        }
        assertEquals(buffer.limit, buffer.position(), "Position should not change after underflow")

        val emptyBuffer = CharBuffer.allocate(0)
        assertFailsWith<BufferUnderflowException>("get() on empty buffer should throw BufferUnderflowException") {
            emptyBuffer.get()
        }
    }

    @Test
    fun testPutBasic() {
        logger.debug { "Testing CharBuffer.put(Char) basic functionality" }
        val buffer = CharBuffer.allocate(3)

        assertSame(buffer, buffer.put('x'), "put() should return itself (1st put)")
        assertEquals(1, buffer.position(), "Position after first put()")

        buffer.put('y')
        assertEquals(2, buffer.position(), "Position after second put()")

        buffer.put('z')
        assertEquals(3, buffer.position(), "Position after third put()")
        assertEquals(3, buffer.limit, "Limit should remain capacity")

        // Verify content
        buffer.flip() // Prepare for reading
        assertEquals('x', buffer.get(), "Verify first char")
        assertEquals('y', buffer.get(), "Verify second char")
        assertEquals('z', buffer.get(), "Verify third char")
    }

    @Test
    fun testPutOverflow() {
        logger.debug { "Testing CharBuffer.put(Char) overflow" }
        val buffer = CharBuffer.allocate(2)
        buffer.put('a')
        buffer.put('b')
        assertEquals(buffer.limit, buffer.position(), "Position should be at limit")

        assertFailsWith<BufferOverflowException>("put() at limit should throw BufferOverflowException") {
            buffer.put('c')
        }
        assertEquals(buffer.limit, buffer.position(), "Position should not change after overflow")

        val emptyBuffer = CharBuffer.allocate(0)
        assertFailsWith<BufferOverflowException>("put() on empty buffer should throw BufferOverflowException") {
            emptyBuffer.put('x')
        }
    }

    @Test
    fun testPutOnReadOnlyBuffer() {
        logger.debug { "Testing CharBuffer.put(Char) on a read-only buffer" }
        val readOnlyBuffer = CharBuffer.wrap("test") // wrap(CharSequence) creates a read-only buffer
        assertTrue(readOnlyBuffer.isReadOnly(), "Buffer should be read-only")

        assertFailsWith<ReadOnlyBufferException>("put() on read-only buffer should throw ReadOnlyBufferException") {
            readOnlyBuffer.put('x')
        }
        assertEquals(0, readOnlyBuffer.position(), "Position should not change on read-only buffer put attempt")

        val allocatedBuffer = CharBuffer.allocate(5)
        val readOnlyView = allocatedBuffer.asReadOnlyBuffer()
        assertTrue(readOnlyView.isReadOnly(), "asReadOnlyBuffer() should create a read-only buffer")
        assertFailsWith<ReadOnlyBufferException>("put() on asReadOnlyBuffer() view should throw ReadOnlyBufferException") {
            readOnlyView.put('y')
        }
    }

    @Test
    fun testGetAbsoluteBasic() {
        logger.debug { "Testing CharBuffer.getAbsolute(index) basic functionality" }
        val chars = charArrayOf('x', 'y', 'z')
        val buffer = CharBuffer.wrap(chars)

        // Change position to ensure getAbsolute is not affected by it
        if (buffer.hasRemaining()) buffer.get()
        val currentPosition = buffer.position()

        assertEquals('x', buffer.getAbsolute(0), "getAbsolute(0)")
        assertEquals('y', buffer.getAbsolute(1), "getAbsolute(1)")
        assertEquals('z', buffer.getAbsolute(2), "getAbsolute(2)")

        assertEquals(currentPosition, buffer.position(), "Position should not change after getAbsolute")
    }

    @Test
    fun testGetAbsoluteOutOfBounds() {
        logger.debug { "Testing CharBuffer.getAbsolute(index) with out-of-bounds indices" }
        val buffer = CharBuffer.wrap(charArrayOf('a', 'b', 'c')) // limit=3, capacity=3

        assertFailsWith<IndexOutOfBoundsException>("getAbsolute(-1)") {
            buffer.getAbsolute(-1)
        }
        assertFailsWith<IndexOutOfBoundsException>("getAbsolute(limit)") {
            buffer.getAbsolute(buffer.limit) // Index 3 is out of bounds (0, 1, 2 are valid)
        }
        assertFailsWith<IndexOutOfBoundsException>("getAbsolute(capacity)") {
            buffer.getAbsolute(buffer.capacity) // Same as limit in this case
        }

        val emptyBuffer = CharBuffer.allocate(0)
        assertFailsWith<IndexOutOfBoundsException>("getAbsolute(0) on empty buffer") {
            emptyBuffer.getAbsolute(0)
        }

        val offsetBuffer = CharBuffer.wrap(charArrayOf('x', 'y', 'z', 'w'), 1, 2) // wraps 'y', 'z'. limit=2, capacity=2
        assertEquals('y', offsetBuffer.getAbsolute(0))
        assertEquals('z', offsetBuffer.getAbsolute(1))
        assertFailsWith<IndexOutOfBoundsException>("getAbsolute(limit) on offset buffer") {
            offsetBuffer.getAbsolute(offsetBuffer.limit) // Index 2
        }
    }

    @Test
    fun testPutAbsoluteBasic() {
        logger.debug { "Testing CharBuffer.putAbsolute(index, Char) basic functionality" }
        val buffer = CharBuffer.allocate(5)
        // Set some initial content using relative put then clear to reset position/limit for putAbsolute testing.
        // Note: After allocate, array has \u0000. We'll overwrite some.
        buffer.put('a').put('b').put('c').put('d').put('e') // position=5, limit=5
        buffer.clear() // position=0, limit=5 (capacity)

        // Set position to something other than 0 to ensure putAbsolute is not affected by it
        buffer.position = 1
        val originalPosition = buffer.position()

        assertSame(buffer, buffer.putAbsolute(0, 'V'), "putAbsolute(0) should return itself")
        buffer.putAbsolute(2, 'W')
        buffer.putAbsolute(4, 'X')

        assertEquals(originalPosition, buffer.position(), "Position should not change after putAbsolute")

        // Verify content
        assertEquals('V', buffer.getAbsolute(0), "Verify char at index 0")
        // Initial content from allocate + put was 'a','b','c','d','e'.
        // We used relative put then clear(). The array still holds this.
        assertEquals('b', buffer.getAbsolute(1), "Verify char at index 1 (should be original 'b')")
        assertEquals('W', buffer.getAbsolute(2), "Verify char at index 2")
        assertEquals('d', buffer.getAbsolute(3), "Verify char at index 3 (should be original 'd')")
        assertEquals('X', buffer.getAbsolute(4), "Verify char at index 4")
    }

    @Test
    fun testPutAbsoluteOutOfBounds() {
        logger.debug { "Testing CharBuffer.putAbsolute(index, Char) with out-of-bounds indices" }
        val buffer = CharBuffer.allocate(3) // limit=3, capacity=3

        assertFailsWith<IndexOutOfBoundsException>("putAbsolute(-1, 'a')") {
            buffer.putAbsolute(-1, 'a')
        }
        assertFailsWith<IndexOutOfBoundsException>("putAbsolute(limit, 'a')") {
            buffer.putAbsolute(buffer.limit, 'a') // Index 3 is out of bounds
        }
        assertFailsWith<IndexOutOfBoundsException>("putAbsolute(capacity, 'a')") {
            buffer.putAbsolute(buffer.capacity, 'a') // Same as limit
        }

        val emptyBuffer = CharBuffer.allocate(0)
        assertFailsWith<IndexOutOfBoundsException>("putAbsolute(0, 'a') on empty buffer") {
            emptyBuffer.putAbsolute(0, 'a')
        }

        // Test with a buffer that has an array via allocate
        val directArrayBuffer = CharBuffer.allocate(5)
        directArrayBuffer.limit = 3 // limit is 3, capacity is 5
        directArrayBuffer.putAbsolute(0, 'x') // OK
        directArrayBuffer.putAbsolute(2, 'z') // OK
        assertFailsWith<IndexOutOfBoundsException>("putAbsolute(limit) where limit < capacity") {
            // Absolute put checks against limit, not capacity. Index 3 is buffer.limit, so it's out of bounds.
            directArrayBuffer.putAbsolute(directArrayBuffer.limit, 'A')
        }
        assertFailsWith<IndexOutOfBoundsException>("putAbsolute(capacity) where limit < capacity") {
            directArrayBuffer.putAbsolute(directArrayBuffer.capacity, 'C') // Index 5 is out of bounds for capacity
        }
    }

    @Test
    fun testPutAbsoluteOnReadOnlyBuffer() {
        logger.debug { "Testing CharBuffer.putAbsolute(index, Char) on a read-only buffer" }
        val readOnlyBuffer = CharBuffer.wrap("test") // wrap(CharSequence) creates a read-only buffer
        assertTrue(readOnlyBuffer.isReadOnly(), "Buffer should be read-only")
        val originalPosition = readOnlyBuffer.position()

        assertFailsWith<ReadOnlyBufferException>("putAbsolute(0, 'X') on read-only buffer") {
            readOnlyBuffer.putAbsolute(0, 'X')
        }
        assertEquals(originalPosition, readOnlyBuffer.position(), "Position should not change on read-only buffer putAbsolute attempt")
        assertEquals('t', readOnlyBuffer.getAbsolute(0), "Content should not change")

        val allocatedBuffer = CharBuffer.allocate(5).put('h').put('e').put('l').put('l').put('o')
        allocatedBuffer.clear() // position 0, limit = capacity
        val readOnlyView = allocatedBuffer.asReadOnlyBuffer()
        assertTrue(readOnlyView.isReadOnly(), "asReadOnlyBuffer() should create a read-only buffer")
        val originalViewPosition = readOnlyView.position()

        assertFailsWith<ReadOnlyBufferException>("putAbsolute(1, 'Y') on asReadOnlyBuffer() view") {
            readOnlyView.putAbsolute(1, 'Y')
        }
        assertEquals(originalViewPosition, readOnlyView.position(), "Position of read-only view should not change")
        assertEquals('e', readOnlyView.getAbsolute(1), "Content of read-only view should not change")
        // Also check original buffer to ensure it wasn't modified through the read-only view
        assertEquals('e', allocatedBuffer.getAbsolute(1), "Original buffer content should also not change")
    }

    @Test
    fun testBulkGetBasic() {
        logger.debug { "Testing CharBuffer.get(CharArray, Int, Int) basic functionality" }
        val content = "abcdefgh"
        val buffer = CharBuffer.wrap(content.toCharArray())
        val destination = CharArray(5) { '?' } // Initialize with placeholder
        val dstOffset = 1
        val length = 3 // Read "abc"

        buffer.position = 0
        assertSame(buffer, buffer.get(destination, dstOffset, length), "get() should return itself")

        assertEquals(length, buffer.position(), "Position should advance by length")
        val expectedDest = charArrayOf('?', 'a', 'b', 'c', '?')
        assertContentEquals(expectedDest, destination, "Destination array content mismatch after reading 'abc'")

        // Read another segment "def" into beginning of a new array
        val nextDest = CharArray(length)
        // buffer position is now 3 (pointing at 'd'), length is 3
        buffer.get(nextDest, 0, length)
        assertEquals(length * 2, buffer.position(), "Position should advance again")
        assertContentEquals(charArrayOf('d', 'e', 'f'), nextDest, "Next destination array content mismatch after reading 'def'")
    }

    @Test
    fun testBulkGetFullBuffer() {
        logger.debug { "Testing CharBuffer.get(CharArray, Int, Int) to read full buffer" }
        val content = "short"
        val buffer = CharBuffer.wrap(content.toCharArray())
        val destination = CharArray(buffer.remaining())

        assertSame(buffer, buffer.get(destination, 0, buffer.remaining()), "get() should return itself")
        assertEquals(content.length, buffer.position(), "Position should be at limit")
        assertFalse(buffer.hasRemaining(), "Buffer should have no remaining elements")
        assertContentEquals(content.toCharArray(), destination, "Destination should match full buffer content")
    }

    @Test
    fun testBulkGetToEmptyDestination() {
        logger.debug { "Testing CharBuffer.get(CharArray, Int, Int) with length 0" }
        val buffer = CharBuffer.wrap("test".toCharArray())
        val destination = CharArray(5) { '?' }
        val originalDestination = destination.copyOf()
        val initialPosition = buffer.position()

        assertSame(buffer, buffer.get(destination, 0, 0), "get() with length 0 should return itself")
        assertEquals(initialPosition, buffer.position(), "Position should not change for length 0")
        assertContentEquals(originalDestination, destination, "Destination array should not change for length 0")
    }

    @Test
    fun testBulkGetUnderflow() {
        logger.debug { "Testing CharBuffer.get(CharArray, Int, Int) underflow" }
        val buffer = CharBuffer.wrap("abc".toCharArray())
        val destination = CharArray(5) { '?' }
        val originalDestination = destination.copyOf()
        buffer.position = 1 // remaining is 2 ('b', 'c')

        assertFailsWith<BufferUnderflowException>("Should throw BufferUnderflowException for length > remaining") {
            buffer.get(destination, 0, 3) // Requesting 3, remaining 2
        }
        assertEquals(1, buffer.position(), "Position should not change after underflow attempt")
        assertContentEquals(originalDestination, destination, "Destination array should not change after underflow attempt")

        // Test underflow when buffer is already at limit
        buffer.position = buffer.limit
        assertFailsWith<BufferUnderflowException>("Should throw BufferUnderflowException when position is at limit and length > 0") {
            buffer.get(destination, 0, 1)
        }
    }

    @Test
    fun testBulkGetDestinationOutOfBounds() {
        logger.debug { "Testing CharBuffer.get(CharArray, Int, Int) with destination out of bounds" }
        val buffer = CharBuffer.wrap("abcdef".toCharArray())
        val destination = CharArray(3)
        val initialPosition = buffer.position()

        assertFailsWith<IndexOutOfBoundsException>("dstOffset < 0") {
            buffer.get(destination, -1, 1)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change after dstOffset < 0")

        assertFailsWith<IndexOutOfBoundsException>("length < 0") {
            buffer.get(destination, 0, -1)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change after length < 0")
        
        assertFailsWith<IndexOutOfBoundsException>("dstOffset + length > destination.size") {
            buffer.get(destination, 1, 3) // 1 + 3 = 4 > 3
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change after dstOffset + length > destination.size")

        assertFailsWith<IndexOutOfBoundsException>("dstOffset > destination.size") {
            buffer.get(destination, destination.size + 1, 0)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change after dstOffset > destination.size with length 0")
    }

    // --- Bulk Put Tests ---

    // 1. put(src: CharArray, offset: Int, length: Int)
    @Test
    fun testBulkPutCharArrayBasic() {
        logger.debug { "Testing CharBuffer.put(CharArray, Int, Int) basic" }
        val buffer = CharBuffer.allocate(10)
        val source = "hello".toCharArray()
        val offset = 0
        val length = source.size

        assertSame(buffer, buffer.put(source, offset, length), "put() should return itself")
        assertEquals(length, buffer.position(), "Position should advance by length")

        buffer.flip()
        val content = CharArray(length)
        buffer.get(content, 0, length)
        assertContentEquals(source, content, "Buffer content mismatch")
    }

    @Test
    fun testBulkPutCharArrayFull() {
        logger.debug { "Testing CharBuffer.put(CharArray, Int, Int) to fill buffer" }
        val buffer = CharBuffer.allocate(5)
        val source = "world".toCharArray()

        buffer.put(source, 0, source.size)
        assertEquals(source.size, buffer.position(), "Position should be at limit")
        assertFalse(buffer.hasRemaining(), "Buffer should be full")

        buffer.flip()
        assertContentEquals(source, buffer.toString().toCharArray(), "Buffer content should match source array")
    }

    @Test
    fun testBulkPutCharArrayOverflow() {
        logger.debug { "Testing CharBuffer.put(CharArray, Int, Int) overflow" }
        val buffer = CharBuffer.allocate(3)
        val source = "longstring".toCharArray()
        val initialPosition = buffer.position()

        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException") {
            buffer.put(source, 0, source.size)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change after overflow")
    }

    @Test
    fun testBulkPutCharArraySourceOutOfBounds() {
        logger.debug { "Testing CharBuffer.put(CharArray, Int, Int) source out of bounds" }
        val buffer = CharBuffer.allocate(10)
        val source = "short".toCharArray()
        val initialPosition = buffer.position()

        assertFailsWith<IndexOutOfBoundsException>("offset < 0") {
            buffer.put(source, -1, 2)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")

        assertFailsWith<IndexOutOfBoundsException>("length < 0") {
            buffer.put(source, 0, -1)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")

        assertFailsWith<IndexOutOfBoundsException>("offset + length > src.size") {
            buffer.put(source, 0, source.size + 1)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")

        assertFailsWith<IndexOutOfBoundsException>("offset > src.size") {
            buffer.put(source, source.size + 1, 0)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")
    }

    @Test
    fun testBulkPutCharArrayOnReadOnlyBuffer() {
        logger.debug { "Testing CharBuffer.put(CharArray, Int, Int) on read-only buffer" }
        val buffer = CharBuffer.allocate(5).asReadOnlyBuffer()
        val source = "test".toCharArray()
        val initialPosition = buffer.position()

        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException") {
            buffer.put(source, 0, source.size)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")
    }

    // 2. put(src: CharBuffer)
    @Test
    fun testBulkPutCharBufferBasic() {
        logger.debug { "Testing CharBuffer.put(CharBuffer) basic" }
        val destBuffer = CharBuffer.allocate(10)
        val sourceBuffer = CharBuffer.wrap("hello".toCharArray())
        val sourceRemaining = sourceBuffer.remaining()

        assertSame(destBuffer, destBuffer.put(sourceBuffer), "put() should return itself")
        assertEquals(sourceRemaining, destBuffer.position(), "Destination position should advance by source.remaining()")
        assertEquals(sourceBuffer.limit, sourceBuffer.position(), "Source position should advance to its limit")
        assertFalse(sourceBuffer.hasRemaining(), "Source buffer should have no remaining elements")

        destBuffer.flip()
        assertEquals("hello", destBuffer.toString(), "Destination content mismatch")
    }

    @Test
    fun testBulkPutCharBufferOverflow() {
        logger.debug { "Testing CharBuffer.put(CharBuffer) overflow" }
        val destBuffer = CharBuffer.allocate(3)
        val sourceBuffer = CharBuffer.wrap("longstring".toCharArray())
        val destInitialPosition = destBuffer.position()
        val sourceInitialPosition = sourceBuffer.position()

        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException") {
            destBuffer.put(sourceBuffer)
        }
        assertEquals(destInitialPosition, destBuffer.position(), "Destination position should not change")
        assertEquals(sourceInitialPosition, sourceBuffer.position(), "Source position should not change")
    }

    @Test
    fun testBulkPutCharBufferToSelf() {
        logger.debug { "Testing CharBuffer.put(CharBuffer) to self" }
        val buffer = CharBuffer.allocate(10)
        // The current implementation uses a generic Exception.
        // Java NIO throws IllegalArgumentException.
        assertFailsWith<Exception>("Should throw Exception for putting buffer to itself") {
            buffer.put(buffer)
        }
    }

    @Test
    fun testBulkPutCharBufferOnReadOnlyDest() {
        logger.debug { "Testing CharBuffer.put(CharBuffer) on read-only destination" }
        val destBuffer = CharBuffer.allocate(10).asReadOnlyBuffer()
        val sourceBuffer = CharBuffer.wrap("test".toCharArray())
        val destInitialPosition = destBuffer.position()
        val sourceInitialPosition = sourceBuffer.position()

        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException") {
            destBuffer.put(sourceBuffer)
        }
        assertEquals(destInitialPosition, destBuffer.position(), "Destination position should not change")
        assertEquals(sourceInitialPosition, sourceBuffer.position(), "Source position should not change")
    }

    // 3. put(src: String)
    @Test
    fun testBulkPutStringBasic() {
        logger.debug { "Testing CharBuffer.put(String) basic" }
        val buffer = CharBuffer.allocate(10)
        val sourceString = "kotlin"
        
        assertSame(buffer, buffer.put(sourceString), "put() should return itself")
        assertEquals(sourceString.length, buffer.position(), "Position should advance by string length")
        
        buffer.flip()
        assertEquals(sourceString, buffer.toString(), "Buffer content mismatch")
    }

    @Test
    fun testBulkPutStringOverflow() {
        logger.debug { "Testing CharBuffer.put(String) overflow" }
        val buffer = CharBuffer.allocate(3)
        val sourceString = "toolong"
        val initialPosition = buffer.position()

        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException") {
            buffer.put(sourceString)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")
    }

    @Test
    fun testBulkPutStringOnReadOnlyBuffer() {
        logger.debug { "Testing CharBuffer.put(String) on read-only buffer" }
        val buffer = CharBuffer.allocate(10).asReadOnlyBuffer()
        val sourceString = "test"
        val initialPosition = buffer.position()

        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException") {
            buffer.put(sourceString)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")
    }

    // 4. put(src: String, start: Int, end: Int)
    @Test
    fun testBulkPutStringRangeBasic() {
        logger.debug { "Testing CharBuffer.put(String, Int, Int) basic" }
        val buffer = CharBuffer.allocate(10)
        val sourceString = "substringexample"
        val start = 3 // "string"
        val end = 9   // "string" -> length 6
        val expectedPutLength = end - start

        assertSame(buffer, buffer.put(sourceString, start, end), "put() should return itself")
        assertEquals(expectedPutLength, buffer.position(), "Position should advance by substring length")

        buffer.flip()
        assertEquals(sourceString.substring(start, end), buffer.toString(), "Buffer content mismatch")
    }

    @Test
    fun testBulkPutStringRangeOverflow() {
        logger.debug { "Testing CharBuffer.put(String, Int, Int) overflow" }
        val buffer = CharBuffer.allocate(3)
        val sourceString = "longsubstring"
        val start = 0
        val end = 5 // length 5
        val initialPosition = buffer.position()

        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException") {
            buffer.put(sourceString, start, end)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")
    }

    @Test
    fun testBulkPutStringRangeSourceOutOfBounds() {
        logger.debug { "Testing CharBuffer.put(String, Int, Int) source out of bounds" }
        val buffer = CharBuffer.allocate(10)
        val sourceString = "src" // length 3
        val initialPosition = buffer.position()

        assertFailsWith<IndexOutOfBoundsException>("start < 0") {
            buffer.put(sourceString, -1, 2)
        }
        assertEquals(initialPosition, buffer.position())

        assertFailsWith<IndexOutOfBoundsException>("end > src.length") {
            buffer.put(sourceString, 0, sourceString.length + 1)
        }
        assertEquals(initialPosition, buffer.position())

        assertFailsWith<IndexOutOfBoundsException>("start > end") {
            buffer.put(sourceString, 2, 1)
        }
        assertEquals(initialPosition, buffer.position())
        
        assertFailsWith<IndexOutOfBoundsException>("start > src.length") {
            buffer.put(sourceString, sourceString.length + 1, sourceString.length + 1)
        }
        assertEquals(initialPosition, buffer.position())
    }

    @Test
    fun testBulkPutStringRangeOnReadOnlyBuffer() {
        logger.debug { "Testing CharBuffer.put(String, Int, Int) on read-only buffer" }
        val buffer = CharBuffer.allocate(10).asReadOnlyBuffer()
        val sourceString = "readonlytest"
        val initialPosition = buffer.position()

        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException") {
            buffer.put(sourceString, 0, 4)
        }
        assertEquals(initialPosition, buffer.position(), "Position should not change")
    }
}
