package org.gnit.lucenekmp.index

import kotlin.concurrent.Volatile
import kotlin.reflect.KClass


/**
 * Instances of this reader type can only be used to get stored fields from the underlying
 * LeafReaders, but it is not possible to directly retrieve postings. To do that, get the [ ] for all sub-readers via [.leaves].
 *
 *
 * IndexReader instances for indexes on disk are usually constructed with a call to one of the
 * static `DirectoryReader.open()` methods, e.g. [DirectoryReader.open].
 * [DirectoryReader] implements the `CompositeReader` interface, it is not possible to
 * directly get postings.
 *
 *
 * Concrete subclasses of IndexReader are usually constructed with a call to one of the static
 * `open()` methods, e.g. [DirectoryReader.open].
 *
 *
 * For efficiency, in this API documents are often referred to via *document numbers*,
 * non-negative integers which each name a unique document in the index. These document numbers are
 * ephemeral -- they may change as documents are added to and deleted from an index. Clients should
 * thus not rely on a given document having the same number between sessions.
 *
 *
 * <a id="thread-safety"></a>
 *
 *
 * **NOTE**: [IndexReader] instances are completely thread safe, meaning multiple
 * threads can call any of its methods, concurrently. If your application requires external
 * synchronization, you should **not** synchronize on the `IndexReader` instance; use
 * your own (non-Lucene) objects instead.
 */
abstract class CompositeReader
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : IndexReader() {
    @Volatile
    private var readerContext: CompositeReaderContext? = null // lazy init

    override fun toString(): String {
        val buffer = StringBuilder()
        // walk up through class hierarchy to get a non-empty simple name (anonymous classes have no
        // name):
        var clazz: KClass<*> = this::class
        /*while (clazz != null) {
            if (!clazz.isAnonymousClass) {
                buffer.append(clazz.simpleName)
                break
            }
            clazz = clazz.getSuperclass()
        }*/
        buffer.append(clazz.simpleName)

        buffer.append('(')
        val subReaders = checkNotNull(this.sequentialSubReaders)
        if (!subReaders.isEmpty()) {
            buffer.append(subReaders.get(0))
            var i = 1
            val c = subReaders.size
            while (i < c) {
                buffer.append(" ").append(subReaders.get(i))
                ++i
            }
        }
        buffer.append(')')
        return buffer.toString()
    }

    /**
     * Expert: returns the sequential sub readers that this reader is logically composed of. This
     * method may not return `null`.
     *
     *
     * **NOTE:** In contrast to previous Lucene versions this method is no longer public, code
     * that wants to get all [LeafReader]s this composite is composed of should use [ ][IndexReader.leaves].
     *
     * @see IndexReader.leaves
     */
    abstract val sequentialSubReaders: List<IndexReader>

    override val context: CompositeReaderContext
        get() {
            ensureOpen()
            // lazy init without thread safety for perf reasons: Building the readerContext twice does not
            // hurt!
            if (readerContext == null) {
                checkNotNull(this.sequentialSubReaders)
                readerContext = CompositeReaderContext.create(this)
            }
            return readerContext!!
        }
}
