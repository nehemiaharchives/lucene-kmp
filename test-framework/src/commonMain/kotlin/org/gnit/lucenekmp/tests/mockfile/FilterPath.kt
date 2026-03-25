package org.gnit.lucenekmp.tests.mockfile

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.ProviderMismatchException
import org.gnit.lucenekmp.jdkport.ReentrantLock

/**
 * A `FilterPath` contains another `Path`, which it uses as its basic source of data, possibly
 * transforming the data along the way or providing additional functionality.
 *
 * Okio's `Path` is a final value type, so unlike Java NIO we cannot return a true path subtype
 * here. Instead this class keeps the same wrapper metadata and exposes helper functions used by
 * the mock filesystem ports.
 */
class FilterPath(
    /** The underlying `Path` instance. */
    private val wrappedDelegate: Path,
    /** The parent `FileSystem` for this path. */
    private val wrappedFileSystem: FilterFileSystem
) {
    /**
     * Get the underlying wrapped path.
     *
     * @return wrapped path.
     */
    fun getDelegate(): Path {
        return wrappedDelegate
    }

    fun unwrap(): Path {
        return wrappedDelegate
    }

    fun getFileSystem(): FilterFileSystem {
        return wrappedFileSystem
    }

    fun isAbsolute(): Boolean {
        return wrappedDelegate.isAbsolute
    }

    fun getRoot(): Path? {
        val root = wrappedDelegate.root ?: return null
        return wrap(root)
    }

    fun getFileName(): Path? {
        if (wrappedDelegate.isRoot) {
            return null
        }
        return wrap(wrappedDelegate.name.toPath())
    }

    fun getParent(): Path? {
        val parent = wrappedDelegate.parent ?: return null
        return wrap(parent)
    }

    fun getNameCount(): Int {
        return wrappedDelegate.segments.size
    }

    fun getName(index: Int): Path {
        return wrap(wrappedDelegate.segments[index].toPath())
    }

    fun subpath(beginIndex: Int, endIndex: Int): Path {
        val separator = separator()
        return wrap(wrappedDelegate.segments.subList(beginIndex, endIndex).joinToString(separator).toPath())
    }

    fun startsWith(other: Path): Boolean {
        val otherPath = toDelegate(other)
        return wrappedDelegate.root == otherPath.root &&
            wrappedDelegate.segments.size >= otherPath.segments.size &&
            wrappedDelegate.segments.subList(0, otherPath.segments.size) == otherPath.segments
    }

    fun startsWith(other: String): Boolean {
        return startsWith(other.toPath())
    }

    fun endsWith(other: Path): Boolean {
        val otherPath = toDelegate(other)
        if (otherPath.root != null && wrappedDelegate.root != otherPath.root) {
            return false
        }
        return wrappedDelegate.segments.size >= otherPath.segments.size &&
            wrappedDelegate.segments.takeLast(otherPath.segments.size) == otherPath.segments
    }

    fun endsWith(other: String): Boolean {
        return endsWith(other.toPath())
    }

    fun normalize(): Path {
        return wrap(wrappedDelegate.normalized())
    }

    fun resolve(other: Path): Path {
        return wrap(wrappedDelegate.resolve(toDelegate(other)))
    }

    fun resolve(other: String): Path {
        return wrap(wrappedDelegate.resolve(other))
    }

    fun resolveSibling(other: Path): Path {
        val parent = wrappedDelegate.parent
        return if (parent == null) {
            wrap(toDelegate(other))
        } else {
            wrap(parent.resolve(toDelegate(other)))
        }
    }

    fun resolveSibling(other: String): Path {
        val parent = wrappedDelegate.parent
        return if (parent == null) {
            wrap(other.toPath())
        } else {
            wrap(parent.resolve(other))
        }
    }

    fun relativize(other: Path): Path {
        return wrap(toDelegate(other).relativeTo(wrappedDelegate))
    }

    fun toUri(): String {
        return wrappedDelegate.toString()
    }

    override fun toString(): String {
        return wrappedDelegate.toString()
    }

    fun toAbsolutePath(): Path {
        return if (wrappedDelegate.isAbsolute) {
            wrap(wrappedDelegate)
        } else {
            wrap(wrappedDelegate.normalized())
        }
    }

    fun toRealPath(): Path {
        return wrap(wrappedFileSystem.canonicalize(wrappedDelegate))
    }

    fun toFile(): Nothing {
        TODO("tests.mockfile.FilterPath.toFile is not supported in KMP common code")
    }

    fun iterator(): Iterator<Path> {
        val iterator = wrappedDelegate.segments.iterator()
        return object : Iterator<Path> {
            override fun hasNext(): Boolean {
                return iterator.hasNext()
            }

            override fun next(): Path {
                return wrap(iterator.next().toPath())
            }
        }
    }

    fun compareTo(other: Path): Int {
        return wrappedDelegate.compareTo(toDelegate(other))
    }

    override fun hashCode(): Int {
        return 31 * wrappedDelegate.hashCode() + wrappedFileSystem.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilterPath) return false
        return wrappedDelegate == other.wrappedDelegate && wrappedFileSystem == other.wrappedFileSystem
    }

    protected fun wrap(other: Path): Path {
        return wrappedFileSystem.provider().wrapPath(other)
    }

    /** Override this to customize the unboxing of Path from various operations */
    protected fun toDelegate(path: Path): Path {
        val pathFileSystem = fileSystemOrNull(path)
        if (pathFileSystem != null && pathFileSystem != wrappedFileSystem) {
            throw ProviderMismatchException(
                "mismatch, expected: ${wrappedFileSystem.provider()::class}, got: ${pathFileSystem.provider()::class}"
            )
        }
        return unwrap(path)
    }

    private fun separator(): String {
        return if (wrappedDelegate.toString().contains('\\')) "\\" else "/"
    }

    companion object {
        private val fileSystemsByPath: MutableMap<String, FilterFileSystem> = mutableMapOf()
        private val fileSystemsByPathLock = ReentrantLock()

        fun wrap(path: Path, fileSystem: FilterFileSystem): Path {
            try {
                fileSystemsByPathLock.lock()
                fileSystemsByPath[path.normalized().toString()] = fileSystem
                Files.registerFileSystem(path, fileSystem)
            } finally {
                fileSystemsByPathLock.unlock()
            }
            return path
        }

        /**
         * Unwraps all `FilterPath`s, returning the innermost `Path`.
         *
         * WARNING: this is exposed for testing only!
         */
        fun unwrap(path: Path): Path {
            return path
        }

        fun fileSystemOrNull(path: Path): FilterFileSystem? {
            try {
                fileSystemsByPathLock.lock()
                var current: Path? = path.normalized()
                while (current != null) {
                    val fileSystem = fileSystemsByPath[current.toString()]
                    if (fileSystem != null) {
                        return fileSystem
                    }
                    current = current.parent
                }
                return null
            } finally {
                fileSystemsByPathLock.unlock()
            }
        }
    }
}

fun Path.getFileSystem(): FileSystem {
    return FilterPath.fileSystemOrNull(this) ?: Files.getFileSystem()
}
