package org.gnit.lucenekmp.jdkport

import okio.Buffer

/**
 * Extension function for Buffer to set a byte at an absolute position.
 *
 * This is a simpler implementation using Buffer's existing API that doesn't require
 * direct access to internal segments.
 */
fun Buffer.setByteAt(position: Long, value: Byte) {
    require(position >= 0) { "Position must be non-negative" }
    require(position < size) { "Position $position is beyond buffer size $size" }

    // Create a temporary copy to preserve the buffer state
    val copy = copy()

    // Clear this buffer
    clear()

    // Write the first portion unchanged
    if (position > 0) {
        copy.copyTo(this, 0, position)
    }

    // Write the modified byte
    writeByte(value.toInt())

    // Skip the byte in the original buffer
    copy.skip(position + 1)

    // Write the remainder of the original buffer
    copy.copyTo(this)

    // Cleanup
    copy.close()
}