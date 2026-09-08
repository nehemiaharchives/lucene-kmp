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
package org.gnit.lucenekmp.facet.taxonomy.directory

import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.taxonomy.*
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.*
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.jdkport.UnmodifiableMutableList
import org.gnit.lucenekmp.jdkport.assert
import okio.IOException
import kotlin.concurrent.Volatile

/**
 * A [TaxonomyReader] which retrieves stored taxonomy information from a [Directory].
 *
 * Reading from the on-disk index on every method call is too slow, so this implementation
 * employs caching: Some methods cache recent requests and their results, while other methods
 * prefetch all the data into memory and then provide answers directly from in-memory tables. See
 * the documentation of individual methods for comments on their performance.
 *
 * @lucene.experimental
 */
open class DirectoryTaxonomyReader : TaxonomyReader, Accountable {
    private val taxoWriter: DirectoryTaxonomyWriter?
    private val taxoEpoch: Long // used in doOpenIfChanged
    private val indexReader: DirectoryReader

    // We only store the fact that a category exists, not otherwise.
    // This is required because the caches are shared with new DTR instances
    // that are allocated from doOpenIfChanged. Therefore, if we only store
    // information about found categories, we cannot accidentally tell a new
    // generation of DTR that a category does not exist.
    // TODO: test DoubleBarrelLRUCache and consider using it instead
    private var ordinalCache: LRUHashMap<FacetLabel, Int>?
    private var categoryCache: LRUHashMap<Int, FacetLabel>?
    @Volatile private var taxoArrays: TaxonomyIndexArrays? = null
    private val lock = ReentrantLock()
    // Cache monitors must be shared with reopened readers just like the cache objects.
    private val ordinalCacheLock: ReentrantLock
    private val categoryCacheLock: ReentrantLock

/**
   * Expert: Use this method to explicitly force the [DirectoryTaxonomyReader] to use specific
   * parent/children arrays and caches.
   *
   * Called from [doOpenIfChanged]. If the taxonomy has been recreated, you should pass
   * `null` as the caches and parent/children arrays.
   *
   * @param indexReader An indexReader that is opened in the desired Directory
   * @param taxoWriter The [DirectoryTaxonomyWriter] from which to obtain newly added
   *     categories, in real-time.
   * @param ordinalCache a FacetLabel to Integer ordinal mapping if it already exists
   * @param categoryCache an ordinal to FacetLabel mapping if it already exists
   * @param taxoArrays taxonomy arrays that store the parent, siblings, children information
   */
    internal constructor(
        indexReader: DirectoryReader,
        taxoWriter: DirectoryTaxonomyWriter?,
        ordinalCache: LRUHashMap<FacetLabel, Int>?,
        categoryCache: LRUHashMap<Int, FacetLabel>?,
        taxoArrays: TaxonomyIndexArrays?,
        ordinalCacheLock: ReentrantLock = ReentrantLock(),
        categoryCacheLock: ReentrantLock = ReentrantLock()
    ) {
        this.indexReader = indexReader
        this.taxoWriter = taxoWriter
        this.taxoEpoch = taxoWriter?.getTaxonomyEpoch() ?: -1
        // use the same instance of the cache, note the protective code in getOrdinal and getPath
        this.ordinalCache = ordinalCache ?: LRUHashMap(DEFAULT_CACHE_VALUE)
        this.categoryCache = categoryCache ?: LRUHashMap(DEFAULT_CACHE_VALUE)
        this.taxoArrays = if (taxoArrays != null) TaxonomyIndexArrays(indexReader, taxoArrays) else null
        this.ordinalCacheLock = ordinalCacheLock
        this.categoryCacheLock = categoryCacheLock
    }

/**
   * Open for reading a taxonomy stored in a given [Directory].
   *
   * @param directory The [Directory] in which the taxonomy resides.
   * @throws CorruptIndexException if the Taxonomy is corrupt.
   * @throws IOException if another error occurred.
   */
    constructor(directory: Directory) {
        indexReader = openIndexReader(directory)
        taxoWriter = null
        taxoEpoch = -1
        // These are the default cache sizes; they can be configured after
        // construction with the cache's setMaxSize() method
        ordinalCache = LRUHashMap(DEFAULT_CACHE_VALUE)
        categoryCache = LRUHashMap(DEFAULT_CACHE_VALUE)
        ordinalCacheLock = ReentrantLock()
        categoryCacheLock = ReentrantLock()
    }

/**
   * Opens a [DirectoryTaxonomyReader] over the given [DirectoryTaxonomyWriter] (for
   * NRT).
   *
   * @param taxoWriter The [DirectoryTaxonomyWriter] from which to obtain newly added
   *     categories, in real-time.
   */
    constructor(taxoWriter: DirectoryTaxonomyWriter) {
        this.taxoWriter = taxoWriter
        taxoEpoch = taxoWriter.getTaxonomyEpoch()
        indexReader = openIndexReader(taxoWriter.getInternalIndexWriter())
        // These are the default cache sizes; they can be configured after
        // construction with the cache's setMaxSize() method
        ordinalCache = LRUHashMap(DEFAULT_CACHE_VALUE)
        categoryCache = LRUHashMap(DEFAULT_CACHE_VALUE)
        ordinalCacheLock = ReentrantLock()
        categoryCacheLock = ReentrantLock()
    }

    override fun doClose() {
        indexReader.close()
        taxoArrays = null
        // do not clear() the caches, as they may be used by other DTR instances.
        ordinalCache = null
        categoryCache = null
    }

    /**
   * Implements the opening of a new [DirectoryTaxonomyReader] instance if the taxonomy has
   * changed.
   *
   * **NOTE:** the returned [DirectoryTaxonomyReader] shares the ordinal and category
   * caches with this reader. This is not expected to cause any issues, unless the two instances
   * continue to live. The reader guarantees that the two instances cannot affect each other in
   * terms of correctness of the caches, however if the size of the cache is changed through [setCacheSize], it will affect both reader instances.
   */
    override fun doOpenIfChanged(): DirectoryTaxonomyReader? {
        ensureOpen()
        // This works for both NRT and non-NRT readers (i.e. an NRT reader remains NRT).
        val r2 = DirectoryReader.openIfChanged(indexReader)
        if (r2 == null) return null // no changes, nothing to do
        // check if the taxonomy was recreated
        var success = false
        try {
            var recreated = false
            if (taxoWriter == null) {
                // not NRT, check epoch from commit data
                val t1 = indexReader.indexCommit.userData[DirectoryTaxonomyWriter.INDEX_EPOCH]
                val t2 = r2.indexCommit.userData[DirectoryTaxonomyWriter.INDEX_EPOCH]
                if (t1 == null) {
                    if (t2 != null) recreated = true
                } else if (t1 != t2) {
                    // t1 != null and t2 must not be null b/c DirTaxoWriter always puts the commit data.
                    // it's ok to use String.equals because we require the two epoch values to be the same.
                    recreated = true
                }
            } else {
                // NRT, compare current taxoWriter.epoch() vs the one that was given at construction
                if (taxoEpoch != taxoWriter.getTaxonomyEpoch()) recreated = true
            }
            val newTaxoReader: DirectoryTaxonomyReader
            if (recreated) {
                // if recreated, do not reuse anything from this instance. the information
                // will be lazily computed by the new instance when needed.
                newTaxoReader = DirectoryTaxonomyReader(r2, taxoWriter, null, null, null)
            } else {
                newTaxoReader = DirectoryTaxonomyReader(r2, taxoWriter, ordinalCache, categoryCache, taxoArrays, ordinalCacheLock, categoryCacheLock)
            }
            success = true
            return newTaxoReader
        } finally {
            if (!success) IOUtils.closeWhileHandlingException(r2)
        }
    }

    /** Open the [DirectoryReader] from this [Directory]. */
    /** Open the [DirectoryReader] from this [IndexWriter]. */
    protected open fun openIndexReader(directory: Directory): DirectoryReader = DirectoryReader.open(directory)
    protected open fun openIndexReader(writer: IndexWriter): DirectoryReader = DirectoryReader.open(writer)
    /**
   * Expert: returns the underlying [DirectoryReader] instance that is used by this [TaxonomyReader].
   */
    fun getInternalIndexReader(): DirectoryReader {
        ensureOpen()
        return indexReader
    }

    override fun getParallelTaxonomyArrays(): ParallelTaxonomyArrays {
        ensureOpen()
        // By copying to a local variable we only perform a volatile read once (if it's not null)
        var arrays = taxoArrays
        if (arrays == null) {
            lock.withLock {
                arrays = taxoArrays
                if (arrays == null) {
                    arrays = TaxonomyIndexArrays(indexReader)
                    taxoArrays = arrays
                }
            }
        }
        return arrays!!
    }

    override fun getCommitUserData(): Map<String, String> {
        ensureOpen()
        return indexReader.indexCommit.userData
    }

    override fun getOrdinal(cp: FacetLabel): Int {
        ensureOpen()
        if (cp.length == 0) return ROOT_ORDINAL
        // First try to find the answer in the LRU cache:
        val res = ordinalCacheLock.withLock { ordinalCache!![cp] }
        if (res != null) {
            if (res < indexReader.maxDoc()) {
                // Since the cache is shared with DTR instances allocated from
                // doOpenIfChanged, we need to ensure that the ordinal is one that
                // this DTR instance recognizes.
                return res
            } else {
                // if we get here, it means that the category was found in the cache,
                // but is not recognized by this TR instance. Therefore, there's no
                // need to continue search for the path on disk, because we won't find
                // it there too.
                return INVALID_ORDINAL
            }
        }
        // If we're still here, we have a cache miss. We need to fetch the
        // value from disk, and then also put it in the cache:
        var ret = INVALID_ORDINAL
        val docs = MultiTerms.getTermPostingsEnum(indexReader, Consts.FULL, BytesRef(FacetsConfig.pathToString(cp.components, cp.length)), 0)
        if (docs != null && docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ret = docs.docID()
            ordinalCacheLock.withLock { ordinalCache!![cp] = ret }
        }
        return ret
    }

    override fun getBulkOrdinals(vararg categoryPaths: FacetLabel): IntArray {
        ensureOpen()
        if (categoryPaths.isEmpty()) return IntArray(0)
        if (categoryPaths.size == 1) return intArrayOf(getOrdinal(categoryPaths[0]))
        // First try to find results in the cache:
        val result = IntArray(categoryPaths.size)
        // Will grow when required, but never beyond categoryPaths.length
        var indexesMissingFromCache = IntArray(minOf(10, categoryPaths.size))
        var numberOfMissingFromCache = 0
        var cp: FacetLabel
        var res: Int?
        for (i in categoryPaths.indices) {
            cp = categoryPaths[i]
            res = ordinalCacheLock.withLock { ordinalCache!![cp] }
            if (res != null) {
                if (res < indexReader.maxDoc()) {
                    // Since the cache is shared with DTR instances allocated from
                    // doOpenIfChanged, we need to ensure that the ordinal is one that
                    // this DTR instance recognizes.
                    result[i] = res
                } else {
                    // if we get here, it means that the category was found in the cache,
                    // but is not recognized by this TR instance. Therefore, there's no
                    // need to continue search for the path on disk, because we won't find
                    // it there too.
                    result[i] = INVALID_ORDINAL
                }
            } else {
                indexesMissingFromCache = ArrayUtil.growInRange(indexesMissingFromCache, numberOfMissingFromCache + 1, categoryPaths.size)
                indexesMissingFromCache[numberOfMissingFromCache++] = i
            }
        }
        // all ordinals found in cache
        if (indexesMissingFromCache.isEmpty()) return result
        // If we're still here, we have at least one cache miss. We need to fetch the
        // value from disk, and then also put results in the cache

        // Create array of missing terms, and sort them so that later we scan terms dictionary
        // forward-only.
        // Note: similar functionality exists within BytesRefHash and BytesRefArray, but they don't
        // reuse BytesRefs and assign their own ords. It is cheaper to have custom implementation here.
        val termsToGet = Array(numberOfMissingFromCache) { i ->
            cp = categoryPaths[indexesMissingFromCache[i]]
            BytesRef(FacetsConfig.pathToString(cp.components, cp.length))
        }
        // sort both terms and their indexes in the input parameter
        val finalMissingFromCache = indexesMissingFromCache
        object : StringSorter(BytesRefComparator.NATURAL) {
            override fun swap(i: Int, j: Int) {
                val tmp = finalMissingFromCache[i]
                finalMissingFromCache[i] = finalMissingFromCache[j]
                finalMissingFromCache[j] = tmp
                val tmpBytes = termsToGet[i]
                termsToGet[i] = termsToGet[j]
                termsToGet[j] = tmpBytes
            }
            override fun get(builder: BytesRefBuilder, result: BytesRef, i: Int) {
                val ref = termsToGet[i]
                result.offset = ref.offset
                result.length = ref.length
                result.bytes = ref.bytes
            }
        }.sort(0, numberOfMissingFromCache)
        val te = MultiTerms.getTerms(indexReader, Consts.FULL)!!.iterator()
        var postings: PostingsEnum? = null
        var ord: Int
        var resIndex: Int
        for (i in 0 until numberOfMissingFromCache) {
            resIndex = indexesMissingFromCache[i]
            if (te.seekExact(termsToGet[i])) {
                postings = te.postings(postings, 0)
                if (postings != null && postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    ord = postings.docID()
                    result[resIndex] = ord
                } else result[resIndex] = INVALID_ORDINAL
            } else result[resIndex] = INVALID_ORDINAL
        }
        // populate cache
        ordinalCacheLock.withLock {
            for (i in 0 until numberOfMissingFromCache) {
                resIndex = indexesMissingFromCache[i]
                ord = result[resIndex]
                if (ord != INVALID_ORDINAL) ordinalCache!![categoryPaths[resIndex]] = ord
            }
        }
        return result
    }

    override fun getPath(ordinal: Int): FacetLabel {
        ensureOpen()
        // Since the cache is shared with DTR instances allocated from
        // doOpenIfChanged, we need to ensure that the ordinal is one that this DTR
        // instance recognizes. Therefore, we do this check up front, before we hit
        // the cache.
        checkOrdinalBounds(ordinal)
        val ordinalPath = getPathFromCache(ordinal)
        if (ordinalPath[0] != null) return ordinalPath[0]!!
        val readerIndex = ReaderUtil.subIndex(ordinal, indexReader.leaves())
        val leafReader = indexReader.leaves()[readerIndex].reader()
        val values = leafReader.getBinaryDocValues(Consts.FULL)
        val ret: FacetLabel
        if (values == null || values.advanceExact(ordinal - indexReader.leaves()[readerIndex].docBase) == false) {
            throw IllegalStateException()
        } else {
            // The index uses the BinaryDocValuesField format to store the mapping
            ret = FacetLabel(*FacetsConfig.stringToPath(values.binaryValue()!!.utf8ToString()))
        }
        categoryCacheLock.withLock { categoryCache!![ordinal] = ret }
        return ret
    }

    private fun getPathFromCache(vararg ordinals: Int): Array<FacetLabel?> {
        val facetLabels = arrayOfNulls<FacetLabel>(ordinals.size)
        // TODO LUCENE-10068: can we use an int-based hash impl, such as IntToObjectMap,
        // wrapped as LRU?
        categoryCacheLock.withLock {
            for (i in ordinals.indices) facetLabels[i] = categoryCache!![ordinals[i]]
        }
        return facetLabels
    }

    /**
   * Checks if the ordinals in the array are >=0 and < `* DirectoryTaxonomyReader#indexReader.maxDoc()`
   *
   * @param ordinals Integer array of ordinals
   * @throws IllegalArgumentException Throw an IllegalArgumentException if one of the ordinals is
   *     out of bounds
   */
    private fun checkOrdinalBounds(vararg ordinals: Int) {
        for (ordinal in ordinals) {
            if (ordinal !in 0 until indexReader.maxDoc()) {
                throw IllegalArgumentException("ordinal $ordinal is out of the range of the indexReader $indexReader. The maximum possible ordinal number is ${indexReader.maxDoc() - 1}")
            }
        }
    }

    /**
   * Returns an array of FacetLabels for a given array of ordinals.
   *
   * This API is generally faster than iteratively calling [getPath] over an array of
   * ordinals. It uses the [getPath] method iteratively when it detects that the index
   * was created using StoredFields (with no performance gains) and uses DocValues based iteration
   * when the index is based on BinaryDocValues. Lucene switched to BinaryDocValues in version 9.0
   *
   * @param ordinals Array of category ordinals that were added to the taxonomy index
   */
    override fun getBulkPath(vararg ordinals: Int): Array<FacetLabel?> {
        ensureOpen()
        checkOrdinalBounds(*ordinals)
        val ordinalsLength = ordinals.size
        val bulkPath = arrayOfNulls<FacetLabel>(ordinalsLength)
        // remember the original positions of ordinals before they are sorted
        val originalPosition = IntArray(ordinalsLength) { it }
        getPathFromCache(*ordinals)
        /* parallel sort the ordinals and originalPosition array based on the values in the ordinals array */
        object : InPlaceMergeSorter() {
            override fun swap(i: Int, j: Int) {
                var x = ordinals[i]
                ordinals[i] = ordinals[j]
                ordinals[j] = x
                x = originalPosition[i]
                originalPosition[i] = originalPosition[j]
                originalPosition[j] = x
            }
            override fun compare(i: Int, j: Int): Int = ordinals[i].compareTo(ordinals[j])
        }.sort(0, ordinalsLength)
        var readerIndex: Int
        var leafReaderMaxDoc = 0
        var leafReaderDocBase = 0
        var leafReader: LeafReader
        var leafReaderContext: LeafReaderContext
        var values: BinaryDocValues? = null
        val uncachedOrdinalPositions = IntArrayList()
        for (i in 0 until ordinalsLength) {
            if (bulkPath[originalPosition[i]] == null) {
                /*
        If ordinals[i] >= leafReaderDocBase + leafReaderMaxDoc then we find the next leaf that contains our ordinal.
        Remember: ordinals[i] operates in the global ordinal space and hence we add leafReaderDocBase to the leafReaderMaxDoc
        (which is the maxDoc of the specific leaf)
         */
                if (values == null || ordinals[i] >= leafReaderDocBase + leafReaderMaxDoc) {
                    readerIndex = ReaderUtil.subIndex(ordinals[i], indexReader.leaves())
                    leafReaderContext = indexReader.leaves()[readerIndex]
                    leafReader = leafReaderContext.reader()
                    leafReaderMaxDoc = leafReader.maxDoc()
                    leafReaderDocBase = leafReaderContext.docBase
                    values = leafReader.getBinaryDocValues(Consts.FULL)
                    /*
          If the index is constructed with the older StoredFields it will not have any BinaryDocValues field and will return null
           */
                    if (values == null) return super.getBulkPath(*ordinals)
                }
                // values is leaf specific, so you only advance till you reach the target within the leaf
                val success = values.advanceExact(ordinals[i] - leafReaderDocBase)
                assert(success)
                bulkPath[originalPosition[i]] = FacetLabel(*FacetsConfig.stringToPath(values.binaryValue()!!.utf8ToString()))
                uncachedOrdinalPositions.add(i)
            }
        }
        if (uncachedOrdinalPositions.isEmpty == false) {
            categoryCacheLock.withLock {
                for (i in uncachedOrdinalPositions) {
                    // add the value to the categoryCache after computation
                    categoryCache!![ordinals[i!!.value]] = bulkPath[originalPosition[i.value]]!!
                }
            }
        }
        return bulkPath
    }

    override fun getSize(): Int {
        ensureOpen()
        return indexReader.numDocs()
    }

    override fun ramBytesUsed(): Long = lock.withLock {
        ensureOpen()
        var ramBytesUsed = 0L
        if (taxoArrays != null) ramBytesUsed += taxoArrays!!.ramBytesUsed()
        categoryCacheLock.withLock { ramBytesUsed += BYTES_PER_CACHE_ENTRY * categoryCache!!.size }
        ordinalCacheLock.withLock { ramBytesUsed += BYTES_PER_CACHE_ENTRY * ordinalCache!!.size }
        ramBytesUsed
    }

    override val childResources: MutableCollection<Accountable>
        get() = lock.withLock {
            val resources = ArrayList<Accountable>()
            if (taxoArrays != null) resources.add(Accountables.namedAccountable("taxoArrays", taxoArrays!!))
            categoryCacheLock.withLock {
                resources.add(Accountables.namedAccountable("categoryCache", BYTES_PER_CACHE_ENTRY * categoryCache!!.size))
            }
            ordinalCacheLock.withLock {
                resources.add(Accountables.namedAccountable("ordinalCache", BYTES_PER_CACHE_ENTRY * ordinalCache!!.size))
            }
            UnmodifiableMutableList(resources)
        }

    /**
   * setCacheSize controls the maximum allowed size of each of the caches used by [getPath] and [getOrdinal].
   *
   * Currently, if the given size is smaller than the current size of a cache, it will not
   * shrink, and rather we be limited to its current size.
   *
   * @param size the new maximum cache size, in number of entries.
   */
    fun setCacheSize(size: Int) {
        ensureOpen()
        categoryCacheLock.withLock { categoryCache!!.setMaxSize(size) }
        ordinalCacheLock.withLock { ordinalCache!!.setMaxSize(size) }
    }

    /**
   * Returns ordinal -> label mapping, up to the provided max ordinal or number of ordinals,
   * whichever is smaller.
   */
    fun toString(max: Int): String {
        ensureOpen()
        val sb = StringBuilder()
        val limit = minOf(max, indexReader.maxDoc())
        for (i in 0 until limit) {
            try {
                val category: FacetLabel? = getPath(i)
                if (category == null) {
                    sb.append(i).append(": NULL!! \n")
                    continue
                }
                if (category.length == 0) {
                    sb.append(i).append(": EMPTY STRING!! \n")
                    continue
                }
                sb.append(i).append(": ").append(category).append("\n")
            } catch (e: IOException) {
                sb.append(i).append(": EXCEPTION!! \n")
            }
        }
        return sb.toString()
    }

    companion object {
        private const val DEFAULT_CACHE_VALUE = 4000
        // NOTE: very coarse estimate!
        private val BYTES_PER_CACHE_ENTRY = 4L * RamUsageEstimator.NUM_BYTES_OBJECT_REF + 4L * RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + 8L * Char.SIZE_BYTES
    }
}
