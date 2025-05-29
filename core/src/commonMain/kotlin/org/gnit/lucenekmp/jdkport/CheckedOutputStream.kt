package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * An output stream that also maintains a checksum of the data being
 * written. The checksum can then be used to verify the integrity of
 * the output data.
 *
 * @see Checksum
 *
 * @author      David Connelly
 * @since 1.1
 */
open class CheckedOutputStream(out: OutputStream, private val cksum: Checksum) :
    FilterOutputStream(out) {

    /**
     * Writes a byte. Will block until the byte is actually written.
     * @param b the byte to be written
     * @throws    IOException if an I/O error has occurred
     */
    override fun write(b: Int) {
        out!!.write(b)
        cksum.update(b)
    }

    /**
     * Writes an array of bytes. Will block until the bytes are
     * actually written.
     * @param b the data to be written
     * @param off the start offset of the data
     * @param len the number of bytes to be written
     * @throws    IOException if an I/O error has occurred
     */
    override fun write(b: ByteArray, off: Int, len: Int) {
        out!!.write(b, off, len)
        cksum.update(b, off, len)
    }

    val checksum: Checksum
        /**
         * Returns the Checksum for this output stream.
         * @return the Checksum
         */
        get() = cksum
}
