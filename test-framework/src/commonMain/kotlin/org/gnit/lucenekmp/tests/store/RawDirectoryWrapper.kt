package org.gnit.lucenekmp.tests.store

import okio.IOException
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

/**
 * Delegates all operations, even optional ones, to the wrapped directory.
 *
 *
 * This class is used if you want the most realistic testing, but still with a checkindex on
 * close. If you want asserts and evil things, use MockDirectoryWrapper instead.
 */
class RawDirectoryWrapper(delegate: Directory) :
    BaseDirectoryWrapper(delegate) {
    @Throws(IOException::class)
    override fun copyFrom(
        from: Directory,
        src: String,
        dest: String,
        context: IOContext
    ) {
        `in`.copyFrom(from, src, dest, context)
    }

    @Throws(IOException::class)
    override fun openChecksumInput(name: String): ChecksumIndexInput {
        return `in`.openChecksumInput(name)
    }
}
