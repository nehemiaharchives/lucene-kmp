package org.gnit.lucenekmp.index

import okio.IOException

/**
 * A FilterDirectoryReader wraps another DirectoryReader, allowing implementations to transform or
 * extend it.
 *
 * Subclasses should implement doWrapDirectoryReader to return an instance of the subclass.
 *
 * If the subclass wants to wrap the DirectoryReader's subreaders, it should also implement a
 * SubReaderWrapper subclass, and pass an instance to its super constructor.
 */
abstract class FilterDirectoryReader(
    /** The filtered DirectoryReader */
    protected val `in`: DirectoryReader,
    wrapper: SubReaderWrapper
) : DirectoryReader(`in`.directory(), wrapper.wrap(`in`.sequentialSubReaders), null) {

    /**
     * Factory class passed to FilterDirectoryReader constructor that allows subclasses to wrap the
     * filtered DirectoryReader's subreaders. You can use this to, e.g., wrap the subreaders with
     * specialised FilterLeafReader implementations.
     */
    abstract class SubReaderWrapper {
        /**
         * Wraps a list of LeafReaders
         *
         * @return an array of wrapped LeafReaders. The returned array might contain less elements
         * compared to the given reader list if an entire reader is filtered out.
         */
        protected open fun wrap(readers: List<out LeafReader>): Array<LeafReader> {
            val wrapped = ArrayList<LeafReader>(readers.size)
            for (reader in readers) {
                val wrap = wrap(reader)
                requireNotNull(wrap)
                wrapped.add(wrap)
            }
            return wrapped.toTypedArray()
        }

        /** Constructor */
        constructor()

        /**
         * Wrap one of the parent DirectoryReader's subreaders
         *
         * @param reader the subreader to wrap
         * @return a wrapped/filtered LeafReader
         */
        abstract fun wrap(reader: LeafReader): LeafReader
    }

    /**
     * Called by the doOpenIfChanged() methods to return a new wrapped DirectoryReader.
     *
     * Implementations should just return an instantiation of themselves, wrapping the passed in
     * DirectoryReader.
     *
     * @param in the DirectoryReader to wrap
     * @return the wrapped DirectoryReader
     */
    @Throws(IOException::class)
    protected abstract fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader

    @Throws(IOException::class)
    private fun wrapDirectoryReader(`in`: DirectoryReader?): DirectoryReader? {
        return if (`in` == null) null else doWrapDirectoryReader(`in`)
    }

    @Throws(IOException::class)
    override fun doOpenIfChanged(): DirectoryReader? {
        return wrapDirectoryReader(`in`.doOpenIfChanged())
    }

    @Throws(IOException::class)
    override fun doOpenIfChanged(commit: IndexCommit?): DirectoryReader? {
        return wrapDirectoryReader(`in`.doOpenIfChanged(commit))
    }

    @Throws(IOException::class)
    override fun doOpenIfChanged(writer: IndexWriter, applyAllDeletes: Boolean): DirectoryReader? {
        return wrapDirectoryReader(`in`.doOpenIfChanged(writer, applyAllDeletes))
    }

    override val version: Long
        get() = `in`.version

    override val isCurrent: Boolean
        @Throws(IOException::class)
        get() = `in`.isCurrent

    override val indexCommit: IndexCommit
        @Throws(IOException::class)
        get() = `in`.indexCommit

    @Throws(IOException::class)
    override fun doClose() {
        `in`.close()
    }

    /** Returns the wrapped DirectoryReader. */
    fun getDelegate(): DirectoryReader {
        return `in`
    }

    /**
     * A DelegatingCacheHelper is a CacheHelper specialization for implementing long-lived caching
     * behaviour for FilterDirectoryReader subclasses. It uses a unique CacheKey for the purpose of
     * implementing the onClose listener delegation for the reader.
     */
    protected class DelegatingCacheHelper(private val delegate: CacheHelper) : CacheHelper {
        private val cacheKey: CacheKey = CacheKey()

        override val key: CacheKey
            get() = cacheKey

        override suspend fun addClosedListener(listener: ClosedListener) {
            // here we wrap the listener and call it with our cache key
            // this is important since this key will be used to cache the reader and otherwise we won't
            // free caches etc.
            delegate.addClosedListener { _ -> listener.onClose(cacheKey) }
        }
    }

    companion object {
        /**
         * Get the wrapped instance by `reader` as long as this reader is an instance of
         * FilterDirectoryReader.
         */
        fun unwrap(reader: DirectoryReader): DirectoryReader {
            var r = reader
            while (r is FilterDirectoryReader) {
                r = r.getDelegate()
            }
            return r
        }
    }
}

