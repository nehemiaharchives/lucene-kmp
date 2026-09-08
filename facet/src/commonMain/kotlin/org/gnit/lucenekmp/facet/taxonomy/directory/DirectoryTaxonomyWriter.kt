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

import org.gnit.lucenekmp.document.*
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.taxonomy.FacetLabel
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyWriter
import org.gnit.lucenekmp.facet.taxonomy.writercache.LruTaxonomyWriterCache
import org.gnit.lucenekmp.facet.taxonomy.writercache.TaxonomyWriterCache
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.jdkport.assert
import okio.use
import okio.Path
import okio.BufferedSink
import okio.buffer
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.fetchAndIncrement

@OptIn(ExperimentalAtomicApi::class)
/**
 * [TaxonomyWriter] which uses a [Directory] to store the taxonomy information on disk,
 * and keeps an additional in-memory cache of some or all categories.
 *
 * In addition to the permanently-stored information in the [Directory], efficiency
 * dictates that we also keep an in-memory cache of <B>recently seen</B> or <B>all</B> categories,
 * so that we do not need to go back to disk for every category addition to see which ordinal this
 * category already has, if any. A [TaxonomyWriterCache] object determines the specific
 * caching algorithm used.
 *
 * This class offers some hooks for extending classes to control the [IndexWriter] instance
 * that is used. See [openIndexWriter].
 *
 * @lucene.experimental
 */
open class DirectoryTaxonomyWriter : TaxonomyWriter {
    private val dir: Directory
    private val indexWriter: IndexWriter
    private val cache: TaxonomyWriterCache
    private val cacheMisses = AtomicInt(0)
    private val nextID = AtomicInt(0)
    private val fullPathField: Field

    // Records the taxonomy index epoch, updated on replaceTaxonomy as well.
    private var indexEpoch: Long

    private var cacheMissesUntilFill = 11
    private var shouldFillCache = true

    // even though lazily initialized, not volatile so that access to it is
    // faster. we keep a volatile boolean init instead.
    private var readerManager: ReaderManager? = null
    @Volatile private var initializedReaderManager = false
    @Volatile private var shouldRefreshReaderManager = false

    /**
   * We call the cache "complete" if we know that every category in our taxonomy is in the cache.
   * When the cache is <B>not</B> complete, and we can't find a category in the cache, we still need
   * to look for it in the on-disk index; Therefore when the cache is not complete, we need to open
   * a "reader" to the taxonomy index. The cache becomes incomplete if it was never filled with the
   * existing categories, or if a put() to the cache ever returned true (meaning that some cached
   * data was cleared).
   */
    @Volatile private var cacheIsComplete: Boolean
    @Volatile private var isClosed = false
    @Volatile private var taxoArrays: TaxonomyIndexArrays? = null
    protected val lock = ReentrantLock()

/**
   * Construct a Taxonomy writer.
   *
   * @param directory The [Directory] in which to store the taxonomy. Note that the taxonomy
   *     is written directly to that directory (not to a subdirectory of it).
   * @param openMode Specifies how to open a taxonomy for writing: `APPEND` means open an
   *     existing index for append (failing if the index does not yet exist). `CREATE`
   *     means create a new index (first deleting the old one if it already existed). `
   *     APPEND_OR_CREATE` appends to an existing index if there is one, otherwise it creates
   *     a new index.
   * @param cache A [TaxonomyWriterCache] implementation which determines the in-memory
   *     caching policy. See for example [LruTaxonomyWriterCache]. If null or missing, [    .defaultTaxonomyWriterCache] is used.
   * @throws CorruptIndexException if the taxonomy is corrupted.
   * @throws LockObtainFailedException if the taxonomy is locked by another writer.
   * @throws IOException if another error occurred.
   */
    constructor(directory: Directory, openMode: OpenMode, cache: TaxonomyWriterCache?) {
        dir = directory
        val config = createIndexWriterConfig(openMode)
        indexWriter = openIndexWriter(dir, config)

        // verify (to some extent) that merge policy in effect would preserve category docids
        assert(indexWriter.config.mergePolicy !is TieredMergePolicy) {
            "for preserving category docids, merging none-adjacent segments is not allowed"
        }

        // after we opened the writer, and the index is locked, it's safe to check
        // the commit data and read the index epoch
        val actualOpenMode = config.openMode
        if (DirectoryReader.indexExists(directory) == false) {
            indexEpoch = 1
        } else {
            var epochStr: String? = null
            val infos = SegmentInfos.readLatestCommit(dir)
            val commitData = infos.userData
            if (commitData != null) epochStr = commitData[INDEX_EPOCH]
            // no commit data, or no epoch in it means an old taxonomy, so set its epoch to 1, for lack
            // of a better value.
            indexEpoch = epochStr?.toLong(16) ?: 1
        }
        if (actualOpenMode == OpenMode.CREATE) ++indexEpoch

        fullPathField = StringField(Consts.FULL, "", Field.Store.NO)
        nextID.store(indexWriter.getDocStats().maxDoc)
        this.cache = cache ?: defaultTaxonomyWriterCache()
        if (nextID.load() == 0) {
            cacheIsComplete = true
            // Make sure that the taxonomy always contain the root category
            // with category id 0.
            addCategory(FacetLabel())
        } else {
            // There are some categories on the disk, which we have not yet
            // read into the cache, and therefore the cache is incomplete.
            // We choose not to read all the categories into the cache now,
            // to avoid terrible performance when a taxonomy index is opened
            // to add just a single category. We will do it later, after we
            // notice a few cache misses.
            cacheIsComplete = false
        }
    }

    /** Returns the [TaxonomyWriterCache] in use by this writer. */

    fun getCache(): TaxonomyWriterCache = cache

    /**
   * Open internal index writer, which contains the taxonomy data.
   *
   * Extensions may provide their own [IndexWriter] implementation or instance. <br>
   * **NOTE:** the instance this method returns will be closed upon calling to [close].
   * <br>
   * **NOTE:** the merge policy in effect must not merge none adjacent segments. See comment in
   * [createIndexWriterConfig] for the logic behind this.
   *
   * @see #createIndexWriterConfig(IndexWriterConfig.OpenMode)
   * @param directory the [Directory] on top of which an [IndexWriter] should be opened.
   * @param config configuration for the internal index writer.
   */
    protected open fun openIndexWriter(directory: Directory, config: IndexWriterConfig): IndexWriter {
        return IndexWriter(directory, config)
    }

    /**
   * Create the [IndexWriterConfig] that would be used for opening the internal index writer.
   * <br>
   * Extensions can configure the [IndexWriter] as they see fit, including setting a [org.gnit.lucenekmp.index.MergeScheduler merge-scheduler], or [org.gnit.lucenekmp.index.IndexDeletionPolicy deletion-policy], different RAM size etc.<br>
   * <br>
   * **NOTE:** internal docids of the configured index must not be altered. For that, categories
   * are never deleted from the taxonomy index. In addition, merge policy in effect must not merge
   * none adjacent segments.
   *
   * @see #openIndexWriter(Directory, IndexWriterConfig)
   * @param openMode see [OpenMode]
   */
    protected open fun createIndexWriterConfig(openMode: OpenMode): IndexWriterConfig {
        // TODO: should we use a more optimized Codec?
        // The taxonomy has a unique structure, where each term is associated with one document

        // Make sure we use a MergePolicy which always merges adjacent segments and thus
        // keeps the doc IDs ordered as well (this is crucial for the taxonomy index).
        return IndexWriterConfig().setOpenMode(openMode).setMergePolicy(LogByteSizeMergePolicy())
    }

    /** Opens a [ReaderManager] from the internal [IndexWriter]. */

    private fun initReaderManager() {
        if (!initializedReaderManager) {
            lock.withLock {
                // verify that the taxo-writer hasn't been closed on us.
                ensureOpen()
                if (!initializedReaderManager) {
                    readerManager = ReaderManager(indexWriter, false, false)
                    shouldRefreshReaderManager = false
                    initializedReaderManager = true
                }
            }
        }
    }

/**
   * Creates a new instance with a default cache as defined by [defaultTaxonomyWriterCache].
   */
    constructor(directory: Directory, openMode: OpenMode) : this(directory, openMode, defaultTaxonomyWriterCache())
/** Create this with `OpenMode.CREATE_OR_APPEND`. */
    constructor(d: Directory) : this(d, OpenMode.CREATE_OR_APPEND)

    /**
   * Frees used resources as well as closes the underlying [IndexWriter], which commits
   * whatever changes made to it to the underlying [Directory].
   */

    override fun close() = lock.withLock {
        if (!isClosed) {
            commit()
            indexWriter.close()
            doClose()
        }
    }

    private fun doClose() {
        isClosed = true
        closeResources()
    }

    /**
   * A hook for extending classes to close additional resources that were used. The default
   * implementation closes the [IndexReader] as well as the [TaxonomyWriterCache]
   * instances that were used. <br>
   * **NOTE:** if you override this method, you should include a `super.closeResources()
   * ` call in your implementation.
   */

    protected open fun closeResources() = lock.withLock {
        if (initializedReaderManager) {
            readerManager!!.close()
            readerManager = null
            initializedReaderManager = false
        }
        cache.close()
    }

    /**
   * Look up the given category in the cache and/or the on-disk storage, returning the category's
   * ordinal, or a negative number in case the category does not yet exist in the taxonomy.
   */

    protected open fun findCategory(categoryPath: FacetLabel): Int = lock.withLock {
        // If we can find the category in the cache, or we know the cache is
        // complete, we can return the response directly from it
        var res = cache.get(categoryPath)
        if (res >= 0 || cacheIsComplete) return@withLock res

        cacheMisses.incrementAndFetch()
        // After a few cache misses, it makes sense to read all the categories
        // from disk and into the cache. The reason not to do this on the first
        // cache miss (or even when opening the writer) is that it will
        // significantly slow down the case when a taxonomy is opened just to
        // add one category. The idea only spending a long time on reading
        // after enough time was spent on cache misses is known as an "online
        // algorithm".
        perhapsFillCache()
        res = cache.get(categoryPath)
        if (res >= 0 || cacheIsComplete) {
            // if after filling the cache from the info on disk, the category is in it
            // or the cache is complete, return whatever cache.get returned.
            return@withLock res
        }

        // if we get here, it means the category is not in the cache, and it is not
        // complete, and therefore we must look for the category on disk.

        // We need to get an answer from the on-disk index.
        initReaderManager()
        var doc = -1
        val reader = readerManager!!.acquire()
        try {
            val catTerm = BytesRef(FacetsConfig.pathToString(categoryPath.components, categoryPath.length))
            var docs: PostingsEnum? = null // reuse
            for (ctx in reader.leaves()) {
                val terms = Terms.getTerms(ctx.reader(), Consts.FULL)
                // TODO: share per-segment TermsEnum here!
                val termsEnum = terms.iterator()
                if (termsEnum.seekExact(catTerm)) {
                    // liveDocs=null because the taxonomy has no deletes
                    docs = termsEnum.postings(docs, 0 /* freqs not required */)
                    // if the term was found, we know it has exactly one document.
                    doc = docs!!.nextDoc() + ctx.docBase
                    break
                }
            }
        } finally {
            readerManager!!.release(reader)
        }
        if (doc > 0) addToCache(categoryPath, doc)
        doc
    }

    override fun addCategory(categoryPath: FacetLabel): Int {
        ensureOpen()
        // check the cache outside the synchronized block. this results in better
        // concurrency when categories are there.
        var res = cache.get(categoryPath)
        if (res < 0) {
            // the category is not in the cache - following code cannot be executed in parallel.
            lock.withLock {
                res = findCategory(categoryPath)
                if (res < 0) {
                    // This is a new category, and we need to insert it into the index
                    // (and the cache). Actually, we might also need to add some of
                    // the category's ancestors before we can add the category itself
                    // (while keeping the invariant that a parent is always added to
                    // the taxonomy before its child). internalAddCategory() does all
                    // this recursively
                    res = internalAddCategory(categoryPath)
                }
            }
        }
        return res
    }

    /**
   * Add a new category into the index (and the cache), and return its new ordinal.
   *
   * Actually, we might also need to add some of the category's ancestors before we can add the
   * category itself (while keeping the invariant that a parent is always added to the taxonomy
   * before its child). We do this by recursion.
   */

    private fun internalAddCategory(cp: FacetLabel): Int {
        // Find our parent's ordinal (recursively adding the parent category
        // to the taxonomy if it's not already there). Then add the parent
        // ordinal as payloads (rather than a stored field; payloads can be
        // more efficiently read into memory in bulk by LuceneTaxonomyReader)
        var parent: Int
        if (cp.length > 1) {
            val parentPath = cp.subpath(cp.length - 1)
            parent = findCategory(parentPath)
            if (parent < 0) parent = internalAddCategory(parentPath)
        } else if (cp.length == 1) {
            parent = TaxonomyReader.ROOT_ORDINAL
        } else {
            parent = TaxonomyReader.INVALID_ORDINAL
        }
        return addCategoryDocument(cp, parent)
    }

    /**
   * Verifies that this instance wasn't closed, or throws [AlreadyClosedException] if it is.
   */

    protected fun ensureOpen() {
        if (isClosed) throw AlreadyClosedException("The taxonomy writer has already been closed")
    }

    /**
   * Child classes can implement this method to modify the document corresponding to a category path
   * before indexing it.
   *
   * @lucene.experimental
   */

    protected open fun enrichOrdinalDocument(d: Document, categoryPath: FacetLabel) {}

    /**
   * Note that the methods calling addCategoryDocument() are synchronized, so this method is
   * effectively synchronized as well.
   */

    private fun addCategoryDocument(categoryPath: FacetLabel, parent: Int): Int {
        val d = Document()
        /* Lucene 9 switches to NumericDocValuesField for storing parent ordinals */
        d.add(NumericDocValuesField(Consts.FIELD_PARENT_ORDINAL_NDV, parent.toLong()))
        val fieldPath = FacetsConfig.pathToString(categoryPath.components, categoryPath.length)
        fullPathField.setStringValue(fieldPath)
        /* Lucene 9 switches to BinaryDocValuesField for storing taxonomy categories */
        d.add(BinaryDocValuesField(Consts.FULL, BytesRef(fieldPath)))
        d.add(fullPathField)
        // add arbitrary ordinal data to the doc
        enrichOrdinalDocument(d, categoryPath)
        indexWriter.addDocument(d)
        val id = nextID.fetchAndIncrement()
        // added a category document, mark that ReaderManager is not up-to-date
        shouldRefreshReaderManager = true
        // also add to the parent array
        taxoArrays = getTaxoArrays().add(id, parent)
        // NOTE: this line must be executed last, or else the cache gets updated
        // before the parents array (LUCENE-4596)
        addToCache(categoryPath, id)
        return id
    }

    private fun addToCache(categoryPath: FacetLabel, id: Int) {
        if (cache.put(categoryPath, id)) {
            // If cache.put() returned true, it means the cache was limited in
            // size, became full, and parts of it had to be evicted. It is
            // possible that a relatively-new category that isn't yet visible
            // to our 'reader' was evicted, and therefore we must now refresh
            // the reader.
            refreshReaderManager()
            cacheIsComplete = false
        }
    }

    private fun refreshReaderManager() = lock.withLock {
        // this method is synchronized since it cannot happen concurrently with
        // addCategoryDocument -- when this method returns, we must know that the
        // reader manager's state is current. also, it sets shouldRefresh to false,
        // and this cannot overlap with addCatDoc too.
        // NOTE: since this method is synced, it can call maybeRefresh, instead of
        // maybeRefreshBlocking. If ever this is changed, make sure to change the
        // call too.
        if (shouldRefreshReaderManager && initializedReaderManager) {
            readerManager!!.maybeRefresh()
            shouldRefreshReaderManager = false
        }
    }

    override fun commit(): Long = lock.withLock {
        ensureOpen()
        // LUCENE-4972: if we always call setCommitData, we create empty commits
        val data = HashMap<String, String>()
        val iter = indexWriter.getLiveCommitData()
        if (iter != null) for (ent in iter) data[ent.key] = ent.value
        val epochStr = data[INDEX_EPOCH]
        if (epochStr == null || epochStr.toLong(16) != indexEpoch) {
            indexWriter.setLiveCommitData(combinedCommitData(indexWriter.getLiveCommitData()))
        }
        indexWriter.commit()
    }

    /** Combine original user data with the taxonomy epoch. */

    private fun combinedCommitData(commitData: Iterable<Map.Entry<String, String>>?): Iterable<MutableMap.MutableEntry<String, String>> {
        val m = HashMap<String, String>()
        if (commitData != null) for (ent in commitData) m[ent.key] = ent.value
        m[INDEX_EPOCH] = indexEpoch.toString(16)
        return m.entries
    }

    override fun setLiveCommitData(commitUserData: Iterable<Map.Entry<String, String>>) {
        indexWriter.setLiveCommitData(combinedCommitData(commitUserData))
    }

    override fun getLiveCommitData(): Iterable<Map.Entry<String, String>> = combinedCommitData(indexWriter.getLiveCommitData())

    /**
   * prepare most of the work needed for a two-phase commit. See [IndexWriter.prepareCommit].
   */

    override fun prepareCommit(): Long = lock.withLock {
        ensureOpen()
        // LUCENE-4972: if we always call setCommitData, we create empty commits
        val data = HashMap<String, String>()
        val iter = indexWriter.getLiveCommitData()
        if (iter != null) for (ent in iter) data[ent.key] = ent.value
        val epochStr = data[INDEX_EPOCH]
        if (epochStr == null || epochStr.toLong(16) != indexEpoch) {
            indexWriter.setLiveCommitData(combinedCommitData(indexWriter.getLiveCommitData()))
        }
        indexWriter.prepareCommit()
    }

    override fun getSize(): Int {
        ensureOpen()
        return nextID.load()
    }

    /**
   * Set the number of cache misses before an attempt is made to read the entire taxonomy into the
   * in-memory cache.
   *
   * This taxonomy writer holds an in-memory cache of recently seen categories to speed up
   * operation. On each cache-miss, the on-disk index needs to be consulted. When an existing
   * taxonomy is opened, a lot of slow disk reads like that are needed until the cache is filled, so
   * it is more efficient to read the entire taxonomy into memory at once. We do this complete read
   * after a certain number (defined by this method) of cache misses.
   *
   * If the number is set to `0`, the entire taxonomy is read into the cache on first use,
   * without fetching individual categories first.
   *
   * NOTE: it is assumed that this method is called immediately after the taxonomy writer has
   * been created.
   */

    fun setCacheMissesUntilFill(i: Int) {
        ensureOpen()
        cacheMissesUntilFill = i
    }

    // we need to guarantee that if several threads call this concurrently, only
    // one executes it, and after it returns, the cache is updated and is either
    // complete or not.
    private fun perhapsFillCache() = lock.withLock {
        if (cacheMisses.load() < cacheMissesUntilFill) return@withLock
        if (!shouldFillCache) {
            // we already filled the cache once, there's no need to re-fill it
            return@withLock
        }
        shouldFillCache = false
        initReaderManager()
        var aborted = false
        val reader = readerManager!!.acquire()
        try {
            var postingsEnum: PostingsEnum? = null
            for (ctx in reader.leaves()) {
                val terms = Terms.getTerms(ctx.reader(), Consts.FULL)
                // TODO: share per-segment TermsEnum here!
                val termsEnum = terms.iterator()
                while (termsEnum.next() != null) {
                    if (!cache.isFull()) {
                        val t = termsEnum.term()!!
                        // Since we guarantee uniqueness of categories, each term has exactly
                        // one document. Also, since we do not allow removing categories (and
                        // hence documents), there are no deletions in the index. Therefore, it
                        // is sufficient to call next(), and then doc(), exactly once with no
                        // 'validation' checks.
                        val cp = FacetLabel(*FacetsConfig.stringToPath(t.utf8ToString()))
                        postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.NONE.toInt())
                        val res = cache.put(cp, postingsEnum!!.nextDoc() + ctx.docBase)
                        assert(!res) { "entries should not have been evicted from the cache" }
                    } else {
                        // the cache is full and the next put() will evict entries from it, therefore abort
                        // the iteration.
                        aborted = true
                        break
                    }
                }
                if (aborted) break
            }
        } finally {
            readerManager!!.release(reader)
        }
        cacheIsComplete = !aborted
        if (cacheIsComplete) {
            lock.withLock {
                // everything is in the cache, so no need to keep readerManager open.
                // this block is executed in a sync block so that it works well with
                // initReaderManager called in parallel.
                readerManager!!.close()
                readerManager = null
                initializedReaderManager = false
            }
        }
    }

    private fun getTaxoArrays(): TaxonomyIndexArrays {
        // By copying to a local variable we only perform a volatile read once (if it's not null)
        var arrays = taxoArrays
        if (arrays == null) {
            lock.withLock {
                arrays = taxoArrays
                if (arrays == null) {
                    initReaderManager()
                    val reader = readerManager!!.acquire()
                    try {
                        arrays = TaxonomyIndexArrays(reader)
                    } finally {
                        readerManager!!.release(reader)
                    }
                    taxoArrays = arrays
                }
            }
        }
        return arrays!!
    }

    override fun getParent(ordinal: Int): Int {
        ensureOpen()
        // Note: the following if() just enforces that a user can never ask
        // for the parent of a nonexistent category - even if the parent array
        // was allocated bigger than it really needs to be.
        if (ordinal !in 0 until nextID.load()) throw IndexOutOfBoundsException("ordinal=$ordinal")
        val parents = getTaxoArrays().parents()
        assert(ordinal < parents.length()) { "requested ordinal ($ordinal); parents.length (${parents.length()}) !" }
        return parents.get(ordinal)
    }

    /**
   * Takes the categories from the given taxonomy directory, and adds the missing ones to this
   * taxonomy. Additionally, it fills the given [OrdinalMap] with a mapping from the original
   * ordinal to the new ordinal.
   */

    fun addTaxonomy(taxoDir: Directory, map: OrdinalMap) {
        ensureOpen()
        DirectoryReader.open(taxoDir).use { r ->
            val size = r.numDocs()
            map.setSize(size)
            var base = 0
            var docs: PostingsEnum? = null
            for (ctx in r.leaves()) {
                val ar = ctx.reader()
                val terms = ar.terms(Consts.FULL)!!
                // TODO: share per-segment TermsEnum here!
                val te = terms.iterator()
                while (te.next() != null) {
                    val cp = FacetLabel(*FacetsConfig.stringToPath(te.term()!!.utf8ToString()))
                    val ordinal = addCategory(cp)
                    docs = te.postings(docs, PostingsEnum.NONE.toInt())
                    map.addMapping(docs!!.nextDoc() + base, ordinal)
                }
                base += ar.maxDoc() // no deletions, so we're ok
            }
            map.addDone()
        }
    }

    /**
   * Mapping from old ordinal to new ordinals, used when merging indexes with separate taxonomies.
   *
   * addToTaxonomies() merges one or more taxonomies into the given taxonomy (this). An
   * OrdinalMap is filled for each of the added taxonomies, containing the new ordinal (in the
   * merged taxonomy) of each of the categories in the old taxonomy.
   *
   * There exist two implementations of OrdinalMap: MemoryOrdinalMap and DiskOrdinalMap. As their
   * names suggest, the former keeps the map in memory and the latter in a temporary disk file.
   * Because these maps will later be needed one by one (to remap the counting lists), not all at
   * the same time, it is recommended to put the first taxonomy's map in memory, and all the rest on
   * disk (later to be automatically read into memory one by one, when needed).
   */

    /** [OrdinalMap] maintained in memory */

    /** [OrdinalMap] maintained on file system */

    interface OrdinalMap {
/**
     * Set the size of the map. This MUST be called before addMapping(). It is assumed (but not
     * verified) that addMapping() will then be called exactly 'size' times, with different
     * origOrdinals between 0 and size-1.
     */
        fun setSize(size: Int)
/** Record a mapping. */
        fun addMapping(origOrdinal: Int, newOrdinal: Int)
/**
     * Call addDone() to say that all addMapping() have been done. In some implementations this
     * might free some resources.
     */
        fun addDone()
        /**
     * Return the map from the taxonomy's original (consecutive) ordinals to the new taxonomy's
     * ordinals. If the map has to be read from disk and ordered appropriately, it is done when
     * getMap() is called. getMap() should only be called once, and only when the map is actually
     * needed. Calling it will also free all resources that the map might be holding (such as
     * temporary disk space), other than the returned int[].
     */
        fun getMap(): IntArray
    }

    class MemoryOrdinalMap /** Sole constructor. */ constructor() : OrdinalMap {
        private lateinit var map: IntArray
        override fun setSize(taxonomySize: Int) { map = IntArray(taxonomySize) }
        override fun addMapping(origOrdinal: Int, newOrdinal: Int) { map[origOrdinal] = newOrdinal }
        override fun addDone() { /* nothing to do */ }
        override fun getMap(): IntArray = map
    }

    class DiskOrdinalMap /** Sole constructor. */ constructor(private val mapFile: Path) : OrdinalMap {
        private var map: IntArray? = null
        // Okio buffered streams retain the upstream big-endian int file representation.
        private var out: BufferedSink? = Files.getFileSystem(mapFile).sink(mapFile).buffer()
        override fun addMapping(origOrdinal: Int, newOrdinal: Int) {
            out!!.writeInt(origOrdinal)
            out!!.writeInt(newOrdinal)
        }
        override fun setSize(taxonomySize: Int) { out!!.writeInt(taxonomySize) }
        override fun addDone() {
            if (out != null) {
                out!!.close()
                out = null
            }
        }
        override fun getMap(): IntArray {
            if (map != null) return map!!
            addDone() // in case this wasn't previously called
            Files.getFileSystem(mapFile).source(mapFile).buffer().use { `in` ->
                map = IntArray(`in`.readInt())
                // NOTE: The current code assumes that the map is complete,
                // i.e. that every ordinal gets exactly one value. Otherwise,
                // we may run into an EOF here, or not read everything.
                for (i in map!!.indices) {
                    val origOrdinal = `in`.readInt()
                    val newOrdinal = `in`.readInt()
                    map!![origOrdinal] = newOrdinal
                }
            }
            // Delete the temporary file, which is no longer needed.
            Files.delete(mapFile)
            return map!!
        }
    }

    /**
   * Rollback changes to the taxonomy writer and closes the instance. Following this method the
   * instance becomes unusable (calling any of its API methods will yield an [AlreadyClosedException]).
   */

    override fun rollback() = lock.withLock {
        ensureOpen()
        indexWriter.rollback()
        doClose()
    }

    /**
   * Replaces the current taxonomy with the given one. This method should generally be called in
   * conjunction with [IndexWriter.addIndexes] to replace both the taxonomy and
   * the search index content.
   */

    fun replaceTaxonomy(taxoDir: Directory) = lock.withLock {
        // replace the taxonomy by doing IW optimized operations
        indexWriter.deleteAll()
        indexWriter.addIndexes(taxoDir)
        shouldRefreshReaderManager = true
        initReaderManager() // ensure that it's initialized
        refreshReaderManager()
        nextID.store(indexWriter.getDocStats().maxDoc)
        taxoArrays = null // must nullify so that it's re-computed next time it's needed
        // need to clear the cache, so that addCategory won't accidentally return
        // old categories that are in the cache.
        cache.clear()
        cacheIsComplete = false
        shouldFillCache = true
        cacheMisses.store(0)
        // update indexEpoch as a taxonomy replace is just like it has be recreated
        ++indexEpoch
        Unit
    }

    /**
   * Delete the taxonomy and reset all state for this writer.
   *
   * To keep using the same main index, you would have to regenerate the taxonomy, taking care
   * that ordinals are indexed in the same order as before. An example of this can be found in
   * [ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData].
   *
   * @lucene.experimental
   */

    internal fun deleteAll() = lock.withLock {
        indexWriter.deleteAll()
        shouldRefreshReaderManager = true
        initReaderManager() // ensure that it's initialized
        refreshReaderManager()
        nextID.store(0)
        taxoArrays = null // must nullify so that it's re-computed next time it's needed
        // need to clear the cache, so that addCategory won't accidentally return
        // old categories that are in the cache.
        cache.clear()
        cacheIsComplete = false
        shouldFillCache = true
        cacheMisses.store(0)
        // update indexEpoch as a taxonomy replace is just like it has be recreated
        ++indexEpoch
        Unit
    }

    /** Returns the [Directory] of this taxonomy writer. */

    fun getDirectory(): Directory = dir
    /**
   * Used by [DirectoryTaxonomyReader] to support NRT.
   *
   * **NOTE:** you should not use the obtained [IndexWriter] in any way, other than
   * opening an IndexReader on it, or otherwise, the taxonomy index may become corrupt!
   */
    internal fun getInternalIndexWriter(): IndexWriter = indexWriter
    /**
   * Expert: returns current index epoch, if this is a near-real-time reader. Used by [DirectoryTaxonomyReader] to support NRT.
   *
   * @lucene.internal
   */
    fun getTaxonomyEpoch(): Long = indexEpoch

    companion object {
        /**
   * Property name of user commit data that contains the index epoch. The epoch changes whenever the
   * taxonomy is recreated (i.e. opened with [OpenMode.CREATE].
   *
   * Applications should not use this property in their commit data because it will be overridden
   * by this taxonomy writer.
   */
        const val INDEX_EPOCH = "index.epoch"
        private const val DEFAULT_CACHE_SIZE = 4000
        /**
   * Defines the default [TaxonomyWriterCache] to use in constructors which do not specify
   * one.
   *
   * The current default is [LruTaxonomyWriterCache]
   */
        fun defaultTaxonomyWriterCache(): TaxonomyWriterCache = LruTaxonomyWriterCache(DEFAULT_CACHE_SIZE)
    }
}
