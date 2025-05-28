package org.gnit.lucenekmp.codecs


import okio.IOException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.Lock

/**
 * A read-only [Directory] that consists of a view over a compound file.
 *
 * @see CompoundFormat
 *
 * @lucene.experimental
 */
abstract class CompoundDirectory
/** Sole constructor.  */
protected constructor() : Directory() {
    /**
     * Checks consistency of this directory.
     *
     *
     * Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
     * against large data files.
     */
    @Throws(IOException::class)
    abstract fun checkIntegrity()

    /**
     * Not implemented
     *
     * @throws UnsupportedOperationException always: not supported by CFS
     */
    override fun deleteFile(name: String) {
        throw UnsupportedOperationException()
    }

    /**
     * Not implemented
     *
     * @throws UnsupportedOperationException always: not supported by CFS
     */
    override fun rename(from: String, to: String) {
        throw UnsupportedOperationException()
    }

    override fun syncMetaData() {}

    @Throws(IOException::class)
    override fun createOutput(name: String, context: IOContext): IndexOutput {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
        throw UnsupportedOperationException()
    }

    override fun sync(names: MutableCollection<String>) {
        throw UnsupportedOperationException()
    }

    override fun obtainLock(name: String): Lock {
        throw UnsupportedOperationException()
    }
}
