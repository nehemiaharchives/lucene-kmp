package org.gnit.lucenekmp.jdkport

/**
 * A Kotlin common implementation of CRC32.
 *
 * The algorithm uses a precomputed table (with polynomial 0xEDB88320) and:
 * - Initializes crc to 0xFFFFFFFF (i.e. -1)
 * - For each byte, updates: crc = table[(crc xor byte) & 0xFF] xor (crc ushr 8)
 * - When getValue() is called, returns (crc xor 0xFFFFFFFF) masked to 32 bits.
 */
class CRC32 : Checksum {
    private var crc: Int = -1  // equivalent to 0xFFFFFFFF

    companion object {
        // Precomputed table for CRC32 using polynomial 0xEDB88320.
        private val table: IntArray = IntArray(256).apply {
            for (i in 0 until 256) {
                var c = i
                repeat(8) {
                    c = if ((c and 1) != 0) {
                        0xEDB88320.toInt() xor (c ushr 1)
                    } else {
                        c ushr 1
                    }
                }
                this[i] = c
            }
        }
    }

    /**
     * Updates the CRC32 checksum with the specified byte.
     * Only the low 8 bits of [b] are used.
     */
    override fun update(b: Int) {
        crc = table[(crc xor b) and 0xFF] xor (crc ushr 8)
    }

    /**
     * Updates the CRC32 checksum with the specified array of bytes.
     */
    override fun update(b: ByteArray, off: Int, len: Int) {
        for (i in off until (off + len)) {
            update(b[i].toInt() and 0xFF)
        }
    }

    /**
     * Returns the current CRC32 value.
     */
    override fun getValue(): Long {
        return (crc xor -1).toLong() and 0xffffffffL
    }

    /**
     * Resets the CRC32 checksum to its initial value.
     */
    override fun reset() {
        crc = -1
    }
}
