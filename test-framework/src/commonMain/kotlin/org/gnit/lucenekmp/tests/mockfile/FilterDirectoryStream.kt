package org.gnit.lucenekmp.tests.mockfile

import okio.Path

/**
 * A `FilterDirectoryStream` contains another sequence of paths, which it uses as its basic source
 * of data, possibly transforming the data along the way or providing additional functionality.
 */
open class FilterDirectoryStream(
    /** The underlying directory-stream-like sequence. */
    protected val delegate: Sequence<Path>,
    /** The underlying `FileSystem` instance. */
    protected val fileSystem: FilterFileSystem
) : Sequence<Path> {
    open fun close() {
    }

    override fun iterator(): Iterator<Path> {
        val delegateIterator = delegate.iterator()
        return object : Iterator<Path> {
            override fun hasNext(): Boolean {
                return delegateIterator.hasNext()
            }

            override fun next(): Path {
                return fileSystem.provider().wrapPath(delegateIterator.next())
            }
        }
    }
}
