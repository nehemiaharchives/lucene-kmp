package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.jdkport.SortedMap
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.Version

/**
 * An [LeafReader] which reads multiple, parallel indexes. Each index added must have the same
 * number of documents, but typically each contains different fields. Deletions are taken from the
 * first reader. Each document contains the union of the fields of all documents with the same
 * document number. When searching, matches for a query term are from the first index added that has
 * the field.
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
 * behavior*.
 */
open class ParallelLeafReader(
    private val closeSubReaders: Boolean,
    readers: Array<LeafReader>,
    storedFieldsReaders: Array<LeafReader>
) : LeafReader() {
    override lateinit var fieldInfos: FieldInfos
    private val parallelReaders: Array<LeafReader>
    private val storedFieldsReaders: Array<LeafReader>
    private val completeReaderSet: MutableSet<LeafReader> = mutableMapOf<LeafReader, Unit>().keys
    private val maxDoc: Int
    private val numDocs: Int
    private val hasDeletions: Boolean
    override lateinit var metaData: LeafMetaData
    private val tvFieldToReader: SortedMap<String, LeafReader> = TreeMap()
    private val fieldToReader: SortedMap<String, LeafReader> = TreeMap() // TODO needn't sort
    private val termsFieldToReader: MutableMap<String, LeafReader> = mutableMapOf()

    /**
     * Create a ParallelLeafReader based on the provided readers; auto-closes the given readers on
     * [.close].
     */
    constructor(vararg readers: LeafReader) : this(true, *readers)

    /** Create a ParallelLeafReader based on the provided readers.  */
    constructor(
        closeSubReaders: Boolean,
        vararg readers: LeafReader
    ) : this(
        closeSubReaders,
        Array(readers.size) { i -> readers[i] },
        Array(readers.size) { i -> readers[i] }
    )

    /**
     * Expert: create a ParallelLeafReader based on the provided readers and storedFieldReaders; when
     * a document is loaded, only storedFieldsReaders will be used.
     */
    init {
        require(!(readers.isEmpty() && storedFieldsReaders.isNotEmpty())) { "There must be at least one main reader if storedFieldsReaders are used." }
        this.parallelReaders = readers.copyOf()
        this.storedFieldsReaders = storedFieldsReaders.copyOf()
        if (parallelReaders.isNotEmpty()) {
            val first: LeafReader = parallelReaders[0]
            this.maxDoc = first.maxDoc()
            this.numDocs = first.numDocs()
            this.hasDeletions = first.hasDeletions()
        } else {
            this.numDocs = 0
            this.maxDoc = this.numDocs
            this.hasDeletions = false
        }
        completeReaderSet.addAll(this.parallelReaders)
        completeReaderSet.addAll(this.storedFieldsReaders)

        // check compatibility:
        for (reader in completeReaderSet) {
            require(reader.maxDoc() == maxDoc) { "All readers must have same maxDoc: " + maxDoc + "!=" + reader.maxDoc() }
        }
        val softDeletesField: String? =
            completeReaderSet.asSequence()
                .map { r -> r.fieldInfos.softDeletesField }
                .firstOrNull { it != null }

        val parentField: String? =
            completeReaderSet.asSequence()
                .map { r -> r.fieldInfos.parentField }
                .firstOrNull { it != null }
        // TODO: make this read-only in a cleaner way
        val builder: FieldInfos.Builder =
            FieldInfos.Builder(
                FieldInfos.FieldNumbers(
                    softDeletesField,
                    parentField
                )
            )

        var indexSort: Sort? = null
        var createdVersionMajor = -1

        // build FieldInfos and fieldToReader map:
        for (reader in this.parallelReaders) {
            val leafMetaData: LeafMetaData = reader.metaData

            val leafIndexSort: Sort? = leafMetaData.sort
            if (indexSort == null) {
                indexSort = leafIndexSort
            } else require(!(leafIndexSort != null && indexSort == leafIndexSort == false)) {
                ("cannot combine LeafReaders that have different index sorts: saw both sort="
                        + indexSort
                        + " and "
                        + leafIndexSort)
            }

            if (createdVersionMajor == -1) {
                createdVersionMajor = leafMetaData.createdVersionMajor
            } else require(createdVersionMajor == leafMetaData.createdVersionMajor) {
                ("cannot combine LeafReaders that have different creation versions: saw both version="
                        + createdVersionMajor
                        + " and "
                        + leafMetaData.createdVersionMajor)
            }

            val readerFieldInfos: FieldInfos = reader.fieldInfos
            for (fieldInfo in readerFieldInfos) {
                // NOTE: first reader having a given field "wins":
                if (!fieldToReader.containsKey(fieldInfo.name)) {
                    builder.add(fieldInfo, fieldInfo.docValuesGen)
                    fieldToReader[fieldInfo.name] = reader
                    // only add these if the reader responsible for that field name is the current:
                    // TODO consider populating 1st leaf with vectors even if the field name has been seen on
                    // a previous leaf
                    if (fieldInfo.hasTermVectors()) {
                        tvFieldToReader[fieldInfo.name] = reader
                    }
                    // TODO consider populating 1st leaf with terms even if the field name has been seen on a
                    // previous leaf
                    if (fieldInfo.indexOptions != IndexOptions.NONE) {
                        termsFieldToReader[fieldInfo.name] = reader
                    }
                }
            }
        }
        if (createdVersionMajor == -1) {
            // empty reader
            createdVersionMajor = Version.LATEST.major
        }

        var minVersion: Version? = Version.LATEST
        var hasBlocks = false
        for (reader in this.parallelReaders) {
            val leafVersion: Version? = reader.metaData.minVersion
            hasBlocks = hasBlocks or reader.metaData.hasBlocks
            if (leafVersion == null) {
                minVersion = null
                break
            } else if (minVersion!!.onOrAfter(leafVersion)) {
                minVersion = leafVersion
            }
        }

        fieldInfos = builder.finish()
        this.metaData = LeafMetaData(
            createdVersionMajor,
            minVersion,
            indexSort,
            hasBlocks
        )

        // do this finally so any Exceptions occurred before don't affect refcounts:
        for (reader in completeReaderSet) {
            if (!closeSubReaders) {
                reader.incRef()
            }
            reader.registerParentReader(this)
        }
    }

    override fun toString(): String {
        val buffer = StringBuilder("ParallelLeafReader(")
        val iter: MutableIterator<LeafReader> =
            completeReaderSet.iterator()
        while (iter.hasNext()) {
            buffer.append(iter.next())
            if (iter.hasNext()) buffer.append(", ")
        }
        return buffer.append(')').toString()
    }

    // Single instance of this, per ParallelReader instance
    private class ParallelFields : Fields() {
        val fields: MutableMap<String, Terms> =
            TreeMap()

        fun addField(fieldName: String, terms: Terms) {
            fields[fieldName] = terms
        }

        override fun iterator(): MutableIterator<String> {
            return fields.keys.iterator()
        }

        override fun terms(field: String?): Terms? {
            return fields[field]
        }

        override fun size(): Int {
            return fields.size
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * NOTE: the returned field numbers will likely not correspond to the actual field numbers in
     * the underlying readers, and codec metadata ([FieldInfo.getAttribute] will be
     * unavailable.
     */
    /*override fun getFieldInfos(): FieldInfos {
        return fieldInfos
    }*/

    override val liveDocs: Bits?
        get() {
            ensureOpen()
            return if (hasDeletions) parallelReaders[0].liveDocs else null
        }

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        ensureOpen()
        val leafReader: LeafReader? = termsFieldToReader[field]
        return if (leafReader == null) null else leafReader.terms(field)
    }

    override fun numDocs(): Int {
        // Don't call ensureOpen() here (it could affect performance)
        return numDocs
    }

    override fun maxDoc(): Int {
        // Don't call ensureOpen() here (it could affect performance)
        return maxDoc
    }

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        ensureOpen()
        val fields: Array<StoredFields> = Array(storedFieldsReaders.size) { i -> storedFieldsReaders[i].storedFields() }
        return object : StoredFields() {
            @Throws(IOException::class)
            override fun prefetch(docID: Int) {
                for (reader in fields) {
                    reader.prefetch(docID)
                }
            }

            @Throws(IOException::class)
            override fun document(
                docID: Int,
                visitor: StoredFieldVisitor
            ) {
                for (reader in fields) {
                    reader.document(docID, visitor)
                }
            }
        }
    }

    override val coreCacheHelper: CacheHelper?
        get() {
            // ParallelReader instances can be short-lived, which would make caching trappy
            // so we do not cache on them, unless they wrap a single reader in which
            // case we delegate
            if (parallelReaders.size == 1 && storedFieldsReaders.size == 1 && parallelReaders[0] === storedFieldsReaders[0]) {
                return parallelReaders[0].coreCacheHelper
            }
            return null
        }

    override val readerCacheHelper: CacheHelper?
        get() {
            // ParallelReader instances can be short-lived, which would make caching trappy
            // so we do not cache on them, unless they wrap a single reader in which
            // case we delegate
            if (parallelReaders.size == 1 && storedFieldsReaders.size == 1 && parallelReaders[0] === storedFieldsReaders[0]) {
                return parallelReaders[0].readerCacheHelper
            }
            return null
        }

    @Throws(IOException::class)
    override fun termVectors(): TermVectors {
        ensureOpen()

        val readerToTermVectors: MutableMap<LeafReader, TermVectors> =/*Identity*/HashMap()
        for (reader in parallelReaders) {
            if (reader.fieldInfos.hasTermVectors()) {
                val termVectors: TermVectors = reader.termVectors()
                readerToTermVectors.put(reader, termVectors)
            }
        }

        return object : TermVectors() {
            @Throws(IOException::class)
            override fun prefetch(docID: Int) {
                // Prefetch all vectors. Note that this may be wasteful if the consumer doesn't need to read
                // all the fields but we have no way to know what fields the consumer needs.
                for (termVectors in readerToTermVectors.values) {
                    termVectors.prefetch(docID)
                }
            }

            @Throws(IOException::class)
            override fun get(docID: Int): Fields? {
                var fields: ParallelFields? = null
                for (ent in tvFieldToReader.entries) {
                    val fieldName: String = ent.key
                    val termVectors: TermVectors? =
                        readerToTermVectors[ent.value]
                    val vector: Terms? = termVectors!!.get(docID, fieldName)
                    if (vector != null) {
                        if (fields == null) {
                            fields = ParallelFields()
                        }
                        fields.addField(fieldName, vector)
                    }
                }

                return fields
            }
        }
    }

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

    @Throws(IOException::class)
    override fun getNumericDocValues(field: String): NumericDocValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[field]
        return if (reader == null) null else reader.getNumericDocValues(field)
    }

    @Throws(IOException::class)
    override fun getBinaryDocValues(field: String): BinaryDocValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[field]
        return if (reader == null) null else reader.getBinaryDocValues(field)
    }

    @Throws(IOException::class)
    override fun getSortedDocValues(field: String): SortedDocValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[field]
        return if (reader == null) null else reader.getSortedDocValues(field)
    }

    @Throws(IOException::class)
    override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[field]
        return if (reader == null) null else reader.getSortedNumericDocValues(field)
    }

    @Throws(IOException::class)
    override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[field]
        return if (reader == null) null else reader.getSortedSetDocValues(field)
    }

    @Throws(IOException::class)
    override fun getDocValuesSkipper(field: String): DocValuesSkipper? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[field]
        return if (reader == null) null else reader.getDocValuesSkipper(field)
    }

    @Throws(IOException::class)
    override fun getNormValues(field: String): NumericDocValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[field]
        val values: NumericDocValues? =
            if (reader == null) null else reader.getNormValues(field)
        return values
    }

    @Throws(IOException::class)
    override fun getPointValues(fieldName: String): PointValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[fieldName]
        return if (reader == null) null else reader.getPointValues(fieldName)
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(fieldName: String): FloatVectorValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[fieldName]
        return if (reader == null) null else reader.getFloatVectorValues(fieldName)
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(fieldName: String): ByteVectorValues? {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[fieldName]
        return if (reader == null) null else reader.getByteVectorValues(fieldName)
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        fieldName: String,
        target: FloatArray,
        knnCollector: KnnCollector,
        acceptDocs: Bits?
    ) {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[fieldName]
        if (reader != null) {
            reader.searchNearestVectors(fieldName, target, knnCollector, acceptDocs)
        }
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        fieldName: String,
        target: ByteArray,
        knnCollector: KnnCollector,
        acceptDocs: Bits?
    ) {
        ensureOpen()
        val reader: LeafReader? = fieldToReader[fieldName]
        if (reader != null) {
            reader.searchNearestVectors(fieldName, target, knnCollector, acceptDocs)
        }
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        ensureOpen()
        for (reader in completeReaderSet) {
            reader.checkIntegrity()
        }
    }

    /** Returns the [LeafReader]s that were passed on init.  */
    fun getParallelReaders(): Array<LeafReader> {
        ensureOpen()
        return parallelReaders
    }
}
