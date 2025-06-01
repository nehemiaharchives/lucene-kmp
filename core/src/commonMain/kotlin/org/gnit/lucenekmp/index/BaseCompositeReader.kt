package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.set
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Base class for implementing [CompositeReader]s based on an array of sub-readers. The
 * implementing class has to add code for correctly refcounting and closing the sub-readers.
 *
 *
 * User code will most likely use [MultiReader] to build a composite reader on a set of
 * sub-readers (like several [DirectoryReader]s).
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
 *
 * @see MultiReader
 *
 * @lucene.internal
 */
abstract class BaseCompositeReader<R : IndexReader> protected constructor(
    subReaders: Array<R>,
    subReadersSorter: Comparator<R>
) : CompositeReader() {
    private val subReaders: Array<R>

    /** A comparator for sorting sub-readers  */
    protected val subReadersSorter: Comparator<R>

    private val starts: IntArray // 1st docno for each reader
    private val maxDoc: Int
    @OptIn(ExperimentalAtomicApi::class)
    private val numDocs: AtomicInteger = AtomicInteger(-1) // computed lazily

    /**
     * List view solely for [.getSequentialSubReaders], for effectiveness the array is used
     * internally.
     */
    private val subReadersList: List<R>

    /**
     * Constructs a `BaseCompositeReader` on the given subReaders.
     *
     * @param subReaders the wrapped sub-readers. This array is returned by [     ][.getSequentialSubReaders] and used to resolve the correct subreader for docID-based
     * methods. **Please note:** This array is **not** cloned and not protected for
     * modification, the subclass is responsible to do this.
     * @param subReadersSorter – a comparator for sorting sub readers. If not `null`, this
     * comparator is used to sort sub readers, before using the for resolving doc IDs.
     */
    init {
        if (subReadersSorter != null) {
            Arrays.sort<R>(subReaders, subReadersSorter)
        }
        this.subReaders = subReaders
        this.subReadersSorter = subReadersSorter
        this.subReadersList = subReaders.toList()
        starts = IntArray(subReaders.size + 1) // build starts array
        var maxDoc: Long = 0
        for (i in subReaders.indices) {
            starts[i] = maxDoc.toInt()
            val r: IndexReader = subReaders[i]
            maxDoc += r.maxDoc().toLong() // compute maxDocs
            r.registerParentReader(this)
        }

        if (maxDoc > IndexWriter.actualMaxDocs) {
            if (this is DirectoryReader) {
                // A single index has too many documents and it is corrupt (IndexWriter prevents this as of
                // LUCENE-6299)
                throw CorruptIndexException(
                    ("Too many documents: an index cannot exceed "
                            + IndexWriter.actualMaxDocs
                            + " but readers have total maxDoc="
                            + maxDoc),
                    subReaders.contentToString()
                )
            } else {
                // Caller is building a MultiReader and it has too many documents; this case is just illegal
                // arguments:
                throw IllegalArgumentException(
                    ("Too many documents: composite IndexReaders cannot exceed "
                            + IndexWriter.actualMaxDocs
                            + " but readers have total maxDoc="
                            + maxDoc)
                )
            }
        }

        this.maxDoc = Math.toIntExact(maxDoc)
        starts[subReaders.size] = this.maxDoc
    }

    @Throws(IOException::class)
    override fun termVectors(): TermVectors {
        ensureOpen()
        val subVectors: Array<TermVectors> =
            kotlin.arrayOfNulls<TermVectors>(subReaders.size) as Array<TermVectors>
        return object : TermVectors() {
            @Throws(IOException::class)
            override fun prefetch(docID: Int) {
                val i = readerIndex(docID) // find subreader num
                if (subVectors[i] == null) {
                    subVectors[i] = subReaders[i].termVectors()
                }
                subVectors[i].prefetch(docID - starts[i])
            }

            @Throws(IOException::class)
            override fun get(docID: Int): Fields? {
                val i = readerIndex(docID) // find subreader num
                // dispatch to subreader, reusing if possible
                if (subVectors[i] == null) {
                    subVectors[i] = subReaders[i].termVectors()
                }
                return subVectors[i].get(docID - starts[i])
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun numDocs(): Int {
        // Don't call ensureOpen() here (it could affect performance)
        // We want to compute numDocs() lazily so that creating a wrapper that hides
        // some documents isn't slow at wrapping time, but on the first time that
        // numDocs() is called. This can help as there are lots of use-cases of a
        // reader that don't involve calling numDocs().
        // However it's not crucial to make sure that we don't call numDocs() more
        // than once on the sub readers, since they likely cache numDocs() anyway,
        // hence the opaque read.
        // http://gee.cs.oswego.edu/dl/html/j9mm.html#opaquesec.
        var numDocs: Int = this.numDocs.load() /*.getOpaque()*/
        if (numDocs == -1) {
            numDocs = 0
            for (r in subReaders) {
                numDocs += r.numDocs()
            }
            assert(numDocs >= 0)
            this.numDocs.set(numDocs)
        }
        return numDocs
    }

    override fun maxDoc(): Int {
        // Don't call ensureOpen() here (it could affect performance)
        return maxDoc
    }

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        ensureOpen()
        val subFields: Array<StoredFields> =
            kotlin.arrayOfNulls<StoredFields>(subReaders.size) as Array<StoredFields>
        return object : StoredFields() {
            @Throws(IOException::class)
            override fun prefetch(docID: Int) {
                val i = readerIndex(docID) // find subreader num
                if (subFields[i] == null) {
                    subFields[i] = subReaders[i].storedFields()
                }
                subFields[i].prefetch(docID - starts[i])
            }

            @Throws(IOException::class)
            override fun document(docID: Int, visitor: StoredFieldVisitor) {
                val i = readerIndex(docID) // find subreader num
                // dispatch to subreader, reusing if possible
                if (subFields[i] == null) {
                    subFields[i] = subReaders[i].storedFields()
                }
                subFields[i].document(docID - starts[i], visitor)
            }
        }
    }

    @Throws(IOException::class)
    override fun docFreq(term: Term): Int {
        ensureOpen()
        var total = 0 // sum freqs in subreaders
        for (i in subReaders.indices) {
            val sub: Int = subReaders[i].docFreq(term)
            assert(sub >= 0)
            assert(sub <= subReaders[i].getDocCount(term.field()))
            total += sub
        }
        return total
    }

    @Throws(IOException::class)
    override fun totalTermFreq(term: Term): Long {
        ensureOpen()
        var total: Long = 0 // sum freqs in subreaders
        for (i in subReaders.indices) {
            val sub: Long = subReaders[i].totalTermFreq(term)
            assert(sub >= 0)
            assert(sub <= subReaders[i].getSumTotalTermFreq(term.field()))
            total += sub
        }
        return total
    }

    @Throws(IOException::class)
    override fun getSumDocFreq(field: String): Long {
        ensureOpen()
        var total: Long = 0 // sum doc freqs in subreaders
        for (reader in subReaders) {
            val sub: Long = reader.getSumDocFreq(field)
            assert(sub >= 0)
            assert(sub <= reader.getSumTotalTermFreq(field))
            total += sub
        }
        return total
    }

    @Throws(IOException::class)
    override fun getDocCount(field: String): Int {
        ensureOpen()
        var total = 0 // sum doc counts in subreaders
        for (reader in subReaders) {
            val sub: Int = reader.getDocCount(field)
            assert(sub >= 0)
            assert(sub <= reader.maxDoc())
            total += sub
        }
        return total
    }

    @Throws(IOException::class)
    override fun getSumTotalTermFreq(field: String): Long {
        ensureOpen()
        var total: Long = 0 // sum doc total term freqs in subreaders
        for (reader in subReaders) {
            val sub: Long = reader.getSumTotalTermFreq(field)
            assert(sub >= 0)
            assert(sub >= reader.getSumDocFreq(field))
            total += sub
        }
        return total
    }

    /** Helper method for subclasses to get the corresponding reader for a doc ID  */
    protected fun readerIndex(docID: Int): Int {
        require(!(docID < 0 || docID >= maxDoc)) { "docID must be >= 0 and < maxDoc=" + maxDoc + " (got docID=" + docID + ")" }
        return ReaderUtil.subIndex(docID, this.starts)
    }

    /** Helper method for subclasses to get the docBase of the given sub-reader index.  */
    protected fun readerBase(readerIndex: Int): Int {
        require(!(readerIndex < 0 || readerIndex >= subReaders.size)) { "readerIndex must be >= 0 and < getSequentialSubReaders().size()" }
        return this.starts[readerIndex]
    }

    override val sequentialSubReaders: List<R>
        get() = subReadersList
}
