package org.gnit.lucenekmp.jdkport

/**
 * ported from java.nio.ByteOrder
 */
class ByteOrder private constructor(private val name: String) {
    /**
     * Constructs a string describing this object.
     *
     *
     *  This method returns the string
     * `"BIG_ENDIAN"` for [.BIG_ENDIAN] and
     * `"LITTLE_ENDIAN"` for [.LITTLE_ENDIAN].
     *
     * @return  The specified string
     */
    override fun toString(): String {
        return name
    }

    companion object {
        /**
         * Constant denoting big-endian byte order.  In this order, the bytes of a
         * multibyte value are ordered from most significant to least significant.
         */
        val BIG_ENDIAN
                : ByteOrder = ByteOrder("BIG_ENDIAN")

        /**
         * Constant denoting little-endian byte order.  In this order, the bytes of
         * a multibyte value are ordered from least significant to most
         * significant.
         */
        val LITTLE_ENDIAN
                : ByteOrder = ByteOrder("LITTLE_ENDIAN")

        // Retrieve the native byte order. It's used early during bootstrap, and
        // must be initialized after BIG_ENDIAN and LITTLE_ENDIAN.
        private val NATIVE_ORDER =
            // TODO impossible to implement in kotlin common code, needs expect/actual pattern later to implement.
            // for now, hardcoding to LITTLE_ENDIAN
            /*if (jdk.internal.misc.Unsafe.getUnsafe().isBigEndian())
            BIG_ENDIAN
        else*/
            LITTLE_ENDIAN

        /**
         * Retrieves the native byte order of the underlying platform.
         *
         *
         *  This method is defined so that performance-sensitive Java code can
         * allocate direct buffers with the same byte order as the hardware.
         * Native code libraries are often more efficient when such buffers are
         * used.
         *
         * @return  The native byte order of the hardware upon which this Java
         * virtual machine is running
         */
        fun nativeOrder(): ByteOrder {
            return NATIVE_ORDER
        }
    }
}
