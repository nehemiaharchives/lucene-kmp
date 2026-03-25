package org.gnit.lucenekmp.tests.mockfile

import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source

/**
 * A `FilterFileSystemProvider` contains another `FileSystem`, which it uses as its basic source of
 * data, possibly transforming the data along the way or providing additional functionality.
 */
abstract class FilterFileSystemProvider(
    /** The underlying `FileSystem` instance. */
    protected val delegate: FileSystem,
    /** The URI scheme for this provider. */
    protected val uriScheme: String
) {
    /** The underlying `FileSystem` instance. */
    protected val filteredFileSystem: FilterFileSystem = FilterFileSystem(this, delegate)

    fun getScheme(): String {
        return uriScheme
    }

    fun getFileSystem(uri: Any? = null): FilterFileSystem {
        return filteredFileSystem
    }

    fun getPath(uri: String): Path {
        return wrapPath(uri.toPath())
    }

    /** wraps a Path with provider-specific behavior */
    open fun wrapPath(path: Path): Path {
        return FilterPath.wrap(path, filteredFileSystem)
    }

    open fun canonicalize(path: Path): Path {
        return wrapPath(delegate.canonicalize(toDelegate(path)))
    }

    open fun metadataOrNull(path: Path): FileMetadata? {
        val metadataOrNull = delegate.metadataOrNull(toDelegate(path)) ?: return null
        val symlinkTarget = metadataOrNull.symlinkTarget ?: return metadataOrNull
        return metadataOrNull.copy(symlinkTarget = wrapPath(symlinkTarget))
    }

    open fun list(dir: Path): List<Path> {
        return delegate.list(toDelegate(dir)).map { wrapPath(it) }
    }

    open fun listOrNull(dir: Path): List<Path>? {
        return delegate.listOrNull(toDelegate(dir))?.map { wrapPath(it) }
    }

    open fun openReadOnly(file: Path): FileHandle {
        return delegate.openReadOnly(toDelegate(file))
    }

    open fun openReadWrite(
        file: Path,
        mustCreate: Boolean = false,
        mustExist: Boolean = false
    ): FileHandle {
        return delegate.openReadWrite(toDelegate(file), mustCreate, mustExist)
    }

    open fun source(file: Path): Source {
        return delegate.source(toDelegate(file))
    }

    open fun sink(file: Path, mustCreate: Boolean = false): Sink {
        return delegate.sink(toDelegate(file), mustCreate)
    }

    open fun appendingSink(file: Path, mustExist: Boolean = false): Sink {
        return delegate.appendingSink(toDelegate(file), mustExist)
    }

    open fun createDirectory(dir: Path, mustCreate: Boolean = false) {
        delegate.createDirectory(toDelegate(dir), mustCreate)
    }

    open fun atomicMove(source: Path, target: Path) {
        delegate.atomicMove(toDelegate(source), toDelegate(target))
    }

    open fun delete(path: Path, mustExist: Boolean = true) {
        delegate.delete(toDelegate(path), mustExist)
    }

    open fun onClose() {
    }

    internal open fun toDelegate(path: Path): Path {
        return FilterPath.unwrap(path)
    }
}

/**
 * A `FilterFileSystem` contains another `FileSystem`, which it uses as its basic source of data,
 * possibly transforming the data along the way or providing additional functionality.
 */
class FilterFileSystem(
    /** FileSystemProvider that created this FilterFileSystem */
    private val parent: FilterFileSystemProvider,
    delegate: FileSystem
) : ForwardingFileSystem(delegate) {
    fun provider(): FilterFileSystemProvider {
        return parent
    }

    /** Returns the `FileSystem` we wrap. */
    fun getDelegate(): FileSystem {
        return delegate
    }

    override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        return parent.toDelegate(path)
    }

    override fun onPathResult(path: Path, functionName: String): Path {
        return parent.wrapPath(path)
    }

    override fun canonicalize(path: Path): Path {
        return parent.canonicalize(path)
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        return parent.metadataOrNull(path)
    }

    override fun list(dir: Path): List<Path> {
        return parent.list(dir)
    }

    override fun listOrNull(dir: Path): List<Path>? {
        return parent.listOrNull(dir)
    }

    override fun openReadOnly(file: Path): FileHandle {
        return parent.openReadOnly(file)
    }

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
        return parent.openReadWrite(file, mustCreate, mustExist)
    }

    override fun source(file: Path): Source {
        return parent.source(file)
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        return parent.sink(file, mustCreate)
    }

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        return parent.appendingSink(file, mustExist)
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        parent.createDirectory(dir, mustCreate)
    }

    override fun atomicMove(source: Path, target: Path) {
        parent.atomicMove(source, target)
    }

    override fun delete(path: Path, mustExist: Boolean) {
        parent.delete(path, mustExist)
    }

    override fun close() {
        parent.onClose()
    }
}
