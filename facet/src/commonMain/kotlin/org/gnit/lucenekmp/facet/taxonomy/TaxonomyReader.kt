/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.facet.taxonomy

import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.AlreadyClosedException
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.decrementAndFetch

/**
 * TaxonomyReader is the read-only interface with which the faceted-search library uses the taxonomy
 * during search time.
 *
 * A TaxonomyReader holds a list of categories. Each category has a serial number which we call
 * an "ordinal", and a hierarchical "path" name:
 *
 * <UL>
 *   <LI>The ordinal is an integer that starts at 0 for the first category (which is always the root
 *       category), and grows contiguously as more categories are added; Note that once a category
 *       is added, it can never be deleted.
 *   <LI>The path is a CategoryPath object specifying the category's position in the hierarchy.
 * </UL>
 *
 * <B>Notes about concurrent access to the taxonomy:</B>
 *
 * An implementation must allow multiple readers to be active concurrently with a single writer.
 * Readers follow so-called "point in time" semantics, i.e., a TaxonomyReader object will only see
 * taxonomy entries which were available at the time it was created. What the writer writes is only
 * available to (new) readers after the writer's commit() is called.
 *
 * In faceted search, two separate indices are used: the main Lucene index, and the taxonomy.
 * Because the main index refers to the categories listed in the taxonomy, it is important to open
 * the taxonomy *after* opening the main index, and it is also necessary to reopen() the taxonomy
 * after reopen()ing the main index.
 *
 * This order is important, otherwise it would be possible for the main index to refer to a
 * category which is not yet visible in the old snapshot of the taxonomy. Note that it is indeed
 * fine for the taxonomy to be opened after the main index - even a long time after. The reason is
 * that once a category is added to the taxonomy, it can never be changed or deleted, so there is no
 * danger that a "too new" taxonomy not being consistent with an older index.
 *
 * @lucene.experimental
 */
@OptIn(ExperimentalAtomicApi::class)
abstract class TaxonomyReader : AutoCloseable {
    /** An iterator over a category's children. */
    open class ChildrenIterator internal constructor(child: Int, siblings: ParallelTaxonomyArrays.IntArray) {
        private val siblings = siblings
        private var child = child

        /**
     * Return the next child ordinal, or [TaxonomyReader.INVALID_ORDINAL] if no more children.
     */
        fun next(): Int {
            val res = child
            if (child != INVALID_ORDINAL) {
                child = siblings.get(child)
            }
            return res
        }
    }

    /** Sole constructor. */
    constructor()

    companion object {
        /**
   * The root category (the category with the empty path) always has the ordinal 0, to which we give
   * a name ROOT_ORDINAL. [getOrdinal] of an empty path will always return `* ROOT_ORDINAL`, and [getPath] with `ROOT_ORDINAL` will return the empty path.
   */
        const val ROOT_ORDINAL = 0
        /**
   * Ordinals are always non-negative, so a negative ordinal can be used to signify an error.
   * Methods here return INVALID_ORDINAL (-1) in this case.
   */
        const val INVALID_ORDINAL = -1
        /**
   * If the taxonomy has changed since the provided reader was opened, open and return a new [TaxonomyReader]; else, return `null`. The new reader, if not `null`, will be the
   * same type of reader as the one given to this method.
   *
   * This method is typically far less costly than opening a fully new [TaxonomyReader] as
   * it shares resources with the provided [TaxonomyReader], when possible.
   */
        fun <T : TaxonomyReader> openIfChanged(oldTaxoReader: T): T? {
            @Suppress("UNCHECKED_CAST")
            val newTaxoReader = oldTaxoReader.doOpenIfChanged() as T?
            assert(newTaxoReader !== oldTaxoReader)
            return newTaxoReader
        }
    }

    @Volatile
    private var closed = false
    // set refCount to 1 at start
    private val refCount = AtomicInt(1)
    // KMP replacement for synchronized (this) in close().
    private val lock = ReentrantLock()

/** performs the actual task of closing the resources that are used by the taxonomy reader. */
    protected abstract fun doClose()

/**
   * Implements the actual opening of a new [TaxonomyReader] instance if the taxonomy has
   * changed.
   *
   * @see #openIfChanged(TaxonomyReader)
   */
    protected abstract fun doOpenIfChanged(): TaxonomyReader?

/** Throws [AlreadyClosedException] if this IndexReader is closed */
    protected fun ensureOpen() {
        if (getRefCount() <= 0) {
            throw AlreadyClosedException("this TaxonomyReader is closed")
        }
    }

    final override fun close() {
        if (!closed) {
            lock.withLock {
                if (!closed) {
                    decRef()
                    closed = true
                }
            }
        }
    }

/**
   * Expert: decreases the refCount of this TaxonomyReader instance. If the refCount drops to 0 this
   * taxonomy reader is closed.
   */
    fun decRef() {
        ensureOpen()
        val rc = refCount.decrementAndFetch()
        if (rc == 0) {
            var success = false
            try {
                doClose()
                closed = true
                success = true
            } finally {
                if (!success) {
                    // Put reference back on failure
                    refCount.incrementAndFetch()
                }
            }
        } else if (rc < 0) {
            throw IllegalStateException("too many decRef calls: refCount is $rc after decrement")
        }
    }

/**
   * Returns a [ParallelTaxonomyArrays] object which can be used to efficiently traverse the
   * taxonomy tree.
   */
    abstract fun getParallelTaxonomyArrays(): ParallelTaxonomyArrays

/** Returns an iterator over the children of the given ordinal. */
    open fun getChildren(ordinal: Int): ChildrenIterator {
        val arrays = getParallelTaxonomyArrays()
        val child = if (ordinal >= 0) arrays.children().get(ordinal) else INVALID_ORDINAL
        return ChildrenIterator(child, arrays.siblings())
    }

/**
   * Retrieve user committed data.
   *
   * @see TaxonomyWriter#setLiveCommitData(Iterable)
   */
    abstract fun getCommitUserData(): Map<String, String>

/**
   * Returns the ordinal of the category given as a path. The ordinal is the category's serial
   * number, an integer which starts with 0 and grows as more categories are added (note that once a
   * category is added, it can never be deleted).
   *
   * @return the category's ordinal or [INVALID_ORDINAL] if the category wasn't found.
   */
    abstract fun getOrdinal(categoryPath: FacetLabel): Int

/**
   * Returns the ordinals of the categories given as a path. The ordinal is the category's serial
   * number, an integer which starts with 0 and grows as more categories are added (note that once a
   * category is added, it can never be deleted).
   *
   * The implementation in [org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader] is generally faster than
   * iteratively calling [getOrdinal]
   *
   * @return array of the category's' ordinals or [INVALID_ORDINAL] if the category wasn't
   *     found.
   */
    open fun getBulkOrdinals(vararg categoryPath: FacetLabel): IntArray {
        // This is a slow default implementation. DirectoryTaxonomyReader overrides this method to make
        // it faster.
        val ords = IntArray(categoryPath.size)
        for (i in categoryPath.indices) {
            ords[i] = getOrdinal(categoryPath[i])
        }
        return ords
    }

    /** Returns ordinal for the dim + path. */
    open fun getOrdinal(dim: String, vararg path: String): Int {
        val fullPath = Array(path.size + 1) { "" }
        fullPath[0] = dim
        path.copyInto(fullPath, 1, 0, path.size)
        return getOrdinal(FacetLabel(*fullPath))
    }

/** Returns the path name of the category with the given ordinal. */
    abstract fun getPath(ordinal: Int): FacetLabel?

/**
   * Returns the path names of the list of ordinals associated with different categories.
   *
   * The implementation in [org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader] is generally faster than
   * the default implementation which iteratively calls [getPath]
   *
   * Note: this method may change (reorder elements) its parameter, you should avoid reusing the
   * parameter after the method is called.
   */
    open fun getBulkPath(vararg ordinals: Int): Array<FacetLabel?> {
        val facetLabels = arrayOfNulls<FacetLabel>(ordinals.size)
        for (i in ordinals.indices) {
            facetLabels[i] = getPath(ordinals[i])
        }
        return facetLabels
    }

/** Returns the current refCount for this taxonomy reader. */
    fun getRefCount(): Int = refCount.load()

/**
   * Returns the number of categories in the taxonomy. Note that the number of categories returned
   * is often slightly higher than the number of categories inserted into the taxonomy; This is
   * because when a category is added to the taxonomy, its ancestors are also added automatically
   * (including the root, which always get ordinal 0).
   */
    abstract fun getSize(): Int

/**
   * Expert: increments the refCount of this TaxonomyReader instance. RefCounts can be used to
   * determine when a taxonomy reader can be closed safely, i.e. as soon as there are no more
   * references. Be sure to always call a corresponding decRef(), in a finally clause; otherwise the
   * reader may never be closed.
   */
    fun incRef() {
        ensureOpen()
        refCount.incrementAndFetch()
    }

/**
   * Expert: increments the refCount of this TaxonomyReader instance only if it has not been closed
   * yet. Returns true on success.
   */
    fun tryIncRef(): Boolean {
        var count = refCount.load()
        while (count > 0) {
            if (refCount.compareAndSet(count, count + 1)) {
                return true
            }
            count = refCount.load()
        }
        return false
    }
}
