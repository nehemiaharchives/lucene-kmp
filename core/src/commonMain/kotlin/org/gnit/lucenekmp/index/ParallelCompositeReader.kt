package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits

/**
 * An [CompositeReader] which reads multiple, parallel indexes. Each index added must have the
 * same number of documents, and exactly the same number of leaves (with equal `maxDoc`), but
 * typically each contains different fields. Deletions are taken from the first reader. Each
 * document contains the union of the fields of all documents with the same document number. When
 * searching, matches for a query term are from the first index added that has the field.
 *
 *
 * This is useful, e.g., with collections that have large fields which change rarely and small
 * fields that change more frequently. The smaller fields may be re-indexed in a new index and both
 * indexes may be searched together.
 *
 *
 * **Warning:** It is up to you to make sure all indexes are created and modified
 * the same way. For example, if you add documents to one index, you need to add the same documents
 * in the same order to the other indexes. *Failure to do so will result in undefined
 * behavior*. A good strategy to create suitable indexes with [IndexWriter] is to use
 * [LogDocMergePolicy], as this one does not reorder documents during merging (like `TieredMergePolicy`) and triggers merges by number of documents per segment. If you use different
 * [MergePolicy]s it might happen that the segment structure of your index is no longer
 * predictable.
 */
class ParallelCompositeReader(
    private val closeSubReaders: Boolean,
    readers: Array<CompositeReader>,
    storedFieldReaders: Array<CompositeReader>
) : BaseCompositeReader<LeafReader>(
    prepareLeafReaders(readers, storedFieldReaders), null
) {
    private val completeReaderSet: MutableSet<IndexReader> = mutableSetOf()
    private val cacheHelper: CacheHelper?

    /**
     * Create a ParallelCompositeReader based on the provided readers; auto-closes the given readers
     * on [.close].
     */
    constructor(vararg readers: CompositeReader) : this(true, *readers)

    /** Create a ParallelCompositeReader based on the provided readers.  */
    constructor(
        closeSubReaders: Boolean,
        vararg readers: CompositeReader
    ) : this(
        closeSubReaders,
        Array(readers.size){ i -> readers[i] },
        Array(readers.size){ i -> readers[i] }
    )

    /**
     * Expert: create a ParallelCompositeReader based on the provided readers and storedFieldReaders;
     * when a document is loaded, only storedFieldsReaders will be used.
     */
    init {
        completeReaderSet.addAll(readers)
        completeReaderSet.addAll(storedFieldReaders)

        // update ref-counts (like MultiReader):
        if (!closeSubReaders) {
            for (reader in completeReaderSet) {
                reader.incRef()
            }
        }
        // finally add our own synthetic readers, so we close or decRef them, too (it does not matter
        // what we do)
        completeReaderSet.addAll(sequentialSubReaders)
        // ParallelReader instances can be short-lived, which would make caching trappy
        // so we do not cache on them, unless they wrap a single reader in which
        // case we delegate
        if (readers.size == 1 && storedFieldReaders.size == 1 && readers[0] === storedFieldReaders[0]) {
            cacheHelper = readers[0].readerCacheHelper
        } else {
            cacheHelper = null
        }
    }

    override val readerCacheHelper: CacheHelper?
        get() = cacheHelper

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun doClose() {
        var ioe: IOException? = null
        for (reader in completeReaderSet) {
            try {
                if (closeSubReaders) {
                    reader.close()
                } else {
                    runBlocking{ reader.decRef() }
                }
            } catch (e: IOException) {
                if (ioe == null) ioe = e
            }
        }
        // throw the first exception
        if (ioe != null) throw ioe
    }

    companion object {
        @Throws(IOException::class)
        private fun prepareLeafReaders(
            readers: Array<CompositeReader>,
            storedFieldsReaders: Array<CompositeReader>
        ): Array<LeafReader> {
            if (readers.isEmpty()) {
                require(storedFieldsReaders.isEmpty()) { "There must be at least one main reader if storedFieldsReaders are used." }
                return arrayOf()
            } else {
                val firstLeaves: MutableList<out LeafReaderContext> =
                    readers[0].leaves()

                // check compatibility:
                val maxDoc: Int = readers[0].maxDoc()
                val noLeaves = firstLeaves.size
                val leafMaxDoc = IntArray(noLeaves)
                for (i in 0..<noLeaves) {
                    val r: LeafReader = firstLeaves[i].reader()
                    leafMaxDoc[i] = r.maxDoc()
                }
                validate(readers, maxDoc, leafMaxDoc)
                validate(storedFieldsReaders, maxDoc, leafMaxDoc)

                // flatten structure of each Composite to just LeafReader[]
                // and combine parallel structure with ParallelLeafReaders:
                val wrappedLeaves: Array<LeafReader> = Array(noLeaves){ i ->
                    val subs: Array<LeafReader> = Array(readers.size){ j ->
                        readers[j].leaves()[i].reader()
                    }

                    val storedSubs: Array<LeafReader> = Array(storedFieldsReaders.size){ j ->
                        storedFieldsReaders[j].leaves()[i].reader()
                    }

                    // We pass true for closeSubs and we prevent touching of subreaders in doClose():
                    // By this the synthetic throw-away readers used here are completely invisible to
                    // ref-counting
                    object : ParallelLeafReader(true, subs, storedSubs) {
                        override fun doClose() {}
                        override fun terms(field: String?): Terms? {
                            TODO("Not yet implemented")
                        }

                        override fun searchNearestVectors(
                            field: String,
                            target: FloatArray,
                            knnCollector: KnnCollector,
                            acceptDocs: Bits?
                        ) {
                            TODO("Not yet implemented")
                        }

                        override fun searchNearestVectors(
                            field: String,
                            target: ByteArray,
                            knnCollector: KnnCollector,
                            acceptDocs: Bits?
                        ) {
                            TODO("Not yet implemented")
                        }
                    }
                }

                return wrappedLeaves
            }
        }

        private fun validate(
            readers: Array<CompositeReader>,
            maxDoc: Int,
            leafMaxDoc: IntArray
        ) {
            for (i in readers.indices) {
                val reader: CompositeReader = readers[i]
                val subs: MutableList<out LeafReaderContext> =
                    reader.leaves()
                require(reader.maxDoc() == maxDoc) { "All readers must have same maxDoc: " + maxDoc + "!=" + reader.maxDoc() }
                val noSubs = subs.size
                require(noSubs == leafMaxDoc.size) { "All readers must have same number of leaf readers" }
                for (subIDX in 0..<noSubs) {
                    val r: LeafReader = subs[subIDX].reader()
                    require(r.maxDoc() == leafMaxDoc[subIDX]) { "All leaf readers must have same corresponding subReader maxDoc" }
                }
            }
        }
    }
}
