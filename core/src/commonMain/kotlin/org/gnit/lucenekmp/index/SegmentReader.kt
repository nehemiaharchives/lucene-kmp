package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldInfosFormat
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.internal.hppc.LongArrayList
import org.gnit.lucenekmp.internal.tests.SegmentReaderAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOUtils
import okio.IOException
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
/*import java.util.concurrent.CopyOnWriteArraySet*/

/**
 * IndexReader implementation over a single segment.
 *
 *
 * Instances pointing to the same segment (but with different deletes, etc) may share the same
 * core data.
 *
 * @lucene.experimental
 */
class SegmentReader : CodecReader {
    private val si: SegmentCommitInfo

    // this is the original SI that IW uses internally but it's mutated behind the scenes
    // and we don't want this SI to be used for anything. Yet, IW needs this to do maintenance
    // and lookup pooled readers etc.
    private val originalSi: SegmentCommitInfo
    override val metaData: LeafMetaData

    override var liveDocs: Bits?
        get(): Bits? {
            ensureOpen()
            return field
        }

    /**
     * Returns the live docs that are not hard-deleted. This is an expert API to be used with
     * soft-deletes to filter out document that hard deleted for instance due to aborted documents or
     * to distinguish soft and hard deleted documents ie. a rolled back tombstone.
     *
     * @lucene.experimental
     */
    val hardLiveDocs: Bits?

    // Normally set to si.maxDoc - si.delDocCount, unless we
    // were created as an NRT reader from IW, in which case IW
    // tells us the number of live docs:
    private val numDocs: Int

    val core: SegmentCoreReaders
    val segDocValues: SegmentDocValues

    /**
     * True if we are holding RAM only liveDocs or DV updates, i.e. the SegmentCommitInfo delGen
     * doesn't match our liveDocs.
     */
    val isNRT: Boolean

    val docValuesProducer: DocValuesProducer?

    override var fieldInfos: FieldInfos
        get(): FieldInfos {
            ensureOpen()
            return field
        }

    /**
     * Constructs a new SegmentReader with a new core.
     *
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    internal constructor(
        si: SegmentCommitInfo,
        createdVersionMajor: Int,
        context: IOContext
    ) {
        this.si = si.clone()
        this.originalSi = si
        this.metaData =
            LeafMetaData(
                createdVersionMajor,
                si.info.minVersion,
                si.info.indexSort,
                si.info.hasBlocks
            )

        // We pull liveDocs/DV updates from disk:
        this.isNRT = false

        core = SegmentCoreReaders(si.info.dir, si, context)
        segDocValues = SegmentDocValues()

        var success = false
        val codec: Codec = si.info.codec
        try {
            if (si.hasDeletions()) {
                // NOTE: the bitvector is stored using the regular directory, not cfs
                liveDocs =
                    codec.liveDocsFormat().readLiveDocs(directory(), si, IOContext.READONCE)
                hardLiveDocs = liveDocs!!
            } else {
                assert(si.delCount == 0)
                liveDocs = null
                hardLiveDocs = liveDocs
            }
            numDocs = si.info.maxDoc() - si.delCount

            fieldInfos = initFieldInfos()
            docValuesProducer = initDocValuesProducer()
            assert(assertLiveDocs(isNRT, hardLiveDocs, liveDocs))
            success = true
        } finally {
            // With lock-less commits, it's entirely possible (and
            // fine) to hit a FileNotFound exception above.  In
            // this case, we want to explicitly close any subset
            // of things that were opened so that we don't have to
            // wait for a GC to do so.
            if (!success) {
                doClose()
            }
        }
    }

    /**
     * Create new SegmentReader sharing core from a previous SegmentReader and using the provided
     * liveDocs, and recording whether those liveDocs were carried in ram (isNRT=true).
     */
    internal constructor(
        si: SegmentCommitInfo,
        sr: SegmentReader,
        liveDocs: Bits?,
        hardLiveDocs: Bits?,
        numDocs: Int,
        isNRT: Boolean
    ) {
        require(numDocs <= si.info.maxDoc()) { "numDocs=" + numDocs + " but maxDoc=" + si.info.maxDoc() }
        require(!(liveDocs != null && liveDocs.length() != si.info.maxDoc())) { "maxDoc=" + si.info.maxDoc() + " but liveDocs.size()=" + liveDocs!!.length() }
        this.si = si.clone()
        this.originalSi = si
        this.metaData = sr.metaData
        this.liveDocs = liveDocs
        this.hardLiveDocs = hardLiveDocs
        assert(assertLiveDocs(isNRT, hardLiveDocs, liveDocs))
        this.isNRT = isNRT
        this.numDocs = numDocs
        this.core = sr.core
        core.incRef()
        this.segDocValues = sr.segDocValues

        var success = false
        try {
            fieldInfos = initFieldInfos()
            docValuesProducer = initDocValuesProducer()
            success = true
        } finally {
            if (!success) {
                doClose()
            }
        }
    }

    /** init most recent DocValues for the current commit  */
    @Throws(IOException::class)
    private fun initDocValuesProducer(): DocValuesProducer? {
        if (!fieldInfos.hasDocValues()) {
            return null
        } else {
            val dir: Directory = core.cfsReader ?: si.info.dir
            return if (si.hasFieldUpdates()) {
                SegmentDocValuesProducer(si, dir, core.coreFieldInfos, fieldInfos, segDocValues)
            } else {
                // simple case, no DocValues updates
                segDocValues.getDocValuesProducer(-1L, si, dir, fieldInfos)
            }
        }
    }

    /** init most recent FieldInfos for the current commit  */
    @Throws(IOException::class)
    private fun initFieldInfos(): FieldInfos {
        if (!si.hasFieldUpdates()) {
            return core.coreFieldInfos
        } else {
            // updates always outside of CFS
            val fisFormat: FieldInfosFormat = si.info.codec.fieldInfosFormat()
            val segmentSuffix = si.fieldInfosGen.toString(Character.MAX_RADIX.coerceIn(2, 36))
            return fisFormat.read(si.info.dir, si.info, segmentSuffix, IOContext.READONCE)
        }
    }

    @Throws(IOException::class)
    override fun doClose() {
        // System.out.println("SR.close seg=" + si);
        try {
            runBlocking{
                core.decRef()
            }
        } finally {
            if (docValuesProducer is SegmentDocValuesProducer) {
                segDocValues.decRef(docValuesProducer.dvGens)
            } else if (docValuesProducer != null) {
                segDocValues.decRef(LongArrayList.from(-1L))
            }
        }
    }

    override fun numDocs(): Int {
        // Don't call ensureOpen() here (it could affect performance)
        return numDocs
    }

    override fun maxDoc(): Int {
        // Don't call ensureOpen() here (it could affect performance)
        return si.info.maxDoc()
    }

    override val termVectorsReader: TermVectorsReader?
        get() {
            ensureOpen()
            return core.termVectorsReaderOrig?.clone()
        }

    override val fieldsReader: StoredFieldsReader
        get() {
            ensureOpen()
            return core.fieldsReaderOrig.clone()
        }

    override val pointsReader: PointsReader?
        get() {
            ensureOpen()
            return core.pointsReader
        }

    override val normsReader: NormsProducer?
        get() {
            ensureOpen()
            return core.normsProducer
        }

    override val docValuesReader: DocValuesProducer?
        get() {
            ensureOpen()
            return docValuesProducer
        }

    override val vectorReader: KnnVectorsReader?
        get() = core.knnVectorsReader

    override val postingsReader: FieldsProducer?
        get() {
            ensureOpen()
            return core.fields
        }

    override fun toString(): String {
        // SegmentInfo.toString takes dir and number of
        // *pending* deletions; so we reverse compute that here:
        return si.toString(si.info.maxDoc() - numDocs - si.delCount)
    }

    val segmentName: String
        /** Return the name of the segment this reader is reading.  */
        get() = si.info.name

    val segmentInfo: SegmentCommitInfo
        /** Return the SegmentInfoPerCommit of the segment this reader is reading.  */
        get() = si

    /** Returns the directory this index resides in.  */
    fun directory(): Directory {
        // Don't ensureOpen here -- in certain cases, when a
        // cloned/reopened reader needs to commit, it may call
        // this method on the closed original reader
        return si.info.dir
    }

    private val readerClosedListeners: MutableSet<ClosedListener> = mutableSetOf()
        /*CopyOnWriteArraySet<ClosedListener>()*/
    private val readerClosedListenersLock = Mutex()

    override suspend fun notifyReaderClosedListeners() {
        readerClosedListenersLock.withLock {
            IOUtils.applyToAll(
                readerClosedListeners
            ) { l: ClosedListener ->
                l.onClose(readerCacheHelper.key)
            }
        }
    }

    override val readerCacheHelper: CacheHelper =
        object : CacheHelper {
            private val cacheKey: CacheKey =
                CacheKey()

            override val key: CacheKey
                get() = cacheKey

            override suspend fun addClosedListener(listener: ClosedListener) {
                ensureOpen()
                readerClosedListeners.add(listener)
            }
        }

    /**
     * Wrap the cache helper of the core to add ensureOpen() calls that make sure users do not
     * register closed listeners on closed indices.
     */
    override val coreCacheHelper: CacheHelper =
        object : CacheHelper {
            override val key: CacheKey
                get() = core.getCacheHelper().key

            override suspend fun addClosedListener(listener: ClosedListener) {
                ensureOpen()
                core.getCacheHelper().addClosedListener(listener)
            }
        }

    /*override fun getMetaData(): LeafMetaData {
        return metaData
    }*/

    val originalSegmentInfo: SegmentCommitInfo
        /**
         * Returns the original SegmentInfo passed to the segment reader on creation time. [ ][.getSegmentInfo] returns a clone of this instance.
         */
        get() = originalSi

    @Throws(IOException::class)
    override fun checkIntegrity() {
        super.checkIntegrity()
        core.cfsReader?.checkIntegrity()
    }

    companion object {
        private fun assertLiveDocs(
            isNRT: Boolean,
            hardLiveDocs: Bits?,
            liveDocs: Bits?
        ): Boolean {
            if (isNRT) {
                assert(
                    hardLiveDocs == null || liveDocs != null
                ) { " liveDocs must be non null if hardLiveDocs are non null" }
            } else {
                assert(hardLiveDocs === liveDocs) { "non-nrt case must have identical liveDocs" }
            }
            return true
        }

        init {
            TestSecrets.setSegmentReaderAccess(
                object : SegmentReaderAccess {
                    override fun getCore(segmentReader: SegmentReader): Any {
                        return segmentReader.core
                    }
                })
        }
    }
}
