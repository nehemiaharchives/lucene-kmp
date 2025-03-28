package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.math.min

/**
 * ported from java.util.zip.Checksum
 *
 * An interface representing a data checksum.
 */
interface Checksum {
    /**
     * Updates the current checksum with the specified byte.
     *
     * @param b the byte (0..255) to update the checksum with
     */
    fun update(b: Int)

    /**
     * Updates the current checksum with the specified array of bytes.
     *
     * This default implementation is equivalent to calling
     * [update] with the full array.
     *
     * @param b the array of bytes to update the checksum with
     * @throws NullPointerException if [b] is null
     */
    fun update(b: ByteArray) {
        update(b, 0, b.size)
    }

    /**
     * Updates the current checksum with a portion of an array of bytes.
     *
     * @param b the byte array to update the checksum with
     * @param off the start offset of the data
     * @param len the number of bytes to use for the update
     */
    fun update(b: ByteArray, off: Int, len: Int)

    /**
     * Updates the current checksum with the bytes from the specified buffer.
     *
     * The checksum is updated with the remaining bytes in the buffer.
     * Upon return, the buffer’s read position will have advanced to its end.
     *
     * For example, the implementation reads chunks (up to 4096 bytes at a time)
     * from the buffer and updates the checksum:
     *
     * ```kotlin
     * fun update(buffer: Buffer) {
     *     while (buffer.size > 0L) {
     *         // Process in chunks of up to 4096 bytes.
     *         val chunkSize = min(buffer.size.toInt(), 4096)
     *         val temp = ByteArray(chunkSize)
     *         buffer.read(temp, 0, chunkSize) // advances the buffer's read position
     *         update(temp, 0, chunkSize)
     *     }
     * }
     * ```
     *
     * @param buffer the Buffer to update the checksum with
     * @throws NullPointerException if [buffer] is null
     */
    fun update(buffer: Buffer) {
        // Process until the Buffer is empty.
        while (buffer.size > 0L) {
            val chunkSize = min(buffer.size.toInt(), 4096)
            // readByteArray(chunkSize) returns a new ByteArray with exactly chunkSize bytes.
            val temp = buffer.readByteArray(chunkSize)
            update(temp, 0, temp.size)
        }
    }

    /**
     * Returns the current checksum value.
     *
     * @return the current checksum value.
     */
    fun getValue(): Long

    /**
     * Resets the checksum to its initial value.
     */
    fun reset()
}