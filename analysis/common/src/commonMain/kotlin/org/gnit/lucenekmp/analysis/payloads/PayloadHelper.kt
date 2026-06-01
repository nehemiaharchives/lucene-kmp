package org.gnit.lucenekmp.analysis.payloads

/** Utility methods for encoding payloads. */
class PayloadHelper {
    companion object {
        fun encodeFloat(payload: Float): ByteArray {
            return encodeFloat(payload, ByteArray(4), 0)
        }

        fun encodeFloat(payload: Float, data: ByteArray, offset: Int): ByteArray {
            return encodeInt(payload.toBits(), data, offset)
        }

        fun encodeInt(payload: Int): ByteArray {
            return encodeInt(payload, ByteArray(4), 0)
        }

        fun encodeInt(payload: Int, data: ByteArray, offset: Int): ByteArray {
            data[offset] = (payload ushr 24).toByte()
            data[offset + 1] = (payload ushr 16).toByte()
            data[offset + 2] = (payload ushr 8).toByte()
            data[offset + 3] = payload.toByte()
            return data
        }

        /**
         * @see decodeFloat
         * @see encodeFloat
         * @return the decoded float
         */
        fun decodeFloat(bytes: ByteArray): Float {
            return decodeFloat(bytes, 0)
        }

        /**
         * Decode the payload that was encoded using [encodeFloat]. NOTE: the length of the
         * array must be at least offset + 4 long.
         *
         * @param bytes The bytes to decode
         * @param offset The offset into the array.
         * @return The float that was encoded
         * @see encodeFloat
         */
        fun decodeFloat(bytes: ByteArray, offset: Int): Float {
            return Float.fromBits(decodeInt(bytes, offset))
        }

        fun decodeInt(bytes: ByteArray, offset: Int): Int {
            return ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
        }
    }
}
