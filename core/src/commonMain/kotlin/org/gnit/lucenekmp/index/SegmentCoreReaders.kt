package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.CompoundDirectory
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.index.IndexReader.CacheKey
import org.gnit.lucenekmp.index.IndexReader.ClosedListener
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.AtomicInteger
import okio.EOFException
import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch


/** Holds core readers that are shared (unchanged) when SegmentReader is cloned or reopened  */
class SegmentCoreReaders(
    dir: Directory,
    si: SegmentCommitInfo,
    context: IOContext
) {
    // Counts how many other readers share the core objects
    // (freqStream, proxStream, tis, etc.) of this reader;
    // when coreRef drops to 0, these core objects may be
    // closed.  A given instance of SegmentReader may be
    // closed, even though it shares core objects with other
    // SegmentReaders:
    @OptIn(ExperimentalAtomicApi::class)
    private val ref: AtomicInteger = AtomicInteger(1)

    val fields: FieldsProducer?
    val normsProducer: NormsProducer?

    val fieldsReaderOrig: StoredFieldsReader
    val termVectorsReaderOrig: TermVectorsReader?
    val pointsReader: PointsReader?
    val knnVectorsReader: KnnVectorsReader?
    val cfsReader: CompoundDirectory?
    val segment: String

    /**
     * fieldinfos for this core: means gen=-1. this is the exact fieldinfos these codec components saw
     * at write. in the case of DV updates, SR may hold a newer version.
     */
    val coreFieldInfos: FieldInfos

    private val coreClosedListeners: MutableSet<ClosedListener> =
        /*java.util.Collections.synchronizedSet*/mutableSetOf<ClosedListener>()
    private val coreListenersMutex = Mutex()

    @OptIn(ExperimentalAtomicApi::class)
    val refCount: Int
        get() = ref.load()

    @OptIn(ExperimentalAtomicApi::class)
    fun incRef() {
        var count: Int
        while ((ref.load().also { count = it }) > 0) {
            if (ref.compareAndSet(count, count + 1)) {
                return
            }
        }
        throw AlreadyClosedException("SegmentCoreReaders is already closed")
    }

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun decRef() {
        if (ref.decrementAndFetch() == 0) {
            try{
                IOUtils.close(
                    fields,
                    termVectorsReaderOrig,
                    fieldsReaderOrig,
                    cfsReader,
                    normsProducer,
                    pointsReader,
                    knnVectorsReader
                )
            }finally{
                this.notifyCoreClosedListeners()
            }
        }
    }

    private val cacheHelper: IndexReader.CacheHelper =
        object : IndexReader.CacheHelper {
            private val cacheKey: CacheKey =
                CacheKey()

            override val key: CacheKey
                get() = cacheKey

            override suspend fun addClosedListener(listener: ClosedListener) {
                coreListenersMutex.withLock {
                    coreClosedListeners.add(listener)
                }
            }
        }

    init {
        val codec: Codec = si.info.getCodec()
        val cfsDir: Directory
        // confusing name: if (cfs) it's the cfsdir, otherwise it's the segment's directory.
        var success = false

        try {
            if (si.info.useCompoundFile) {
                cfsReader = codec.compoundFormat().getCompoundReader(dir, si.info)
                cfsDir = cfsReader
            } else {
                cfsReader = null
                cfsDir = dir
            }

            segment = si.info.name

            coreFieldInfos = codec.fieldInfosFormat().read(cfsDir, si.info, "", context)

            val segmentReadState =
                SegmentReadState(cfsDir, si.info, coreFieldInfos, context)
            if (coreFieldInfos.hasPostings()) {
                val format: PostingsFormat = codec.postingsFormat()
                // Ask codec for its Fields
                fields = format.fieldsProducer(segmentReadState)
                checkNotNull(fields)
            } else {
                fields = null
            }

            // ask codec for its Norms:
            // TODO: since we don't write any norms file if there are no norms,
            // kinda jaky to assume the codec handles the case of no norms file at all gracefully!
            if (coreFieldInfos.hasNorms()) {
                normsProducer = codec.normsFormat().normsProducer(segmentReadState)
                checkNotNull(normsProducer)
            } else {
                normsProducer = null
            }

            fieldsReaderOrig =
                si.info
                    .getCodec()
                    .storedFieldsFormat()
                    .fieldsReader(cfsDir, si.info, coreFieldInfos, context)

            if (coreFieldInfos.hasTermVectors()) { // open term vector files only as needed
                termVectorsReaderOrig =
                    si.info
                        .getCodec()
                        .termVectorsFormat()
                        .vectorsReader(cfsDir, si.info, coreFieldInfos, context)
            } else {
                termVectorsReaderOrig = null
            }

            if (coreFieldInfos.hasPointValues()) {
                pointsReader = codec.pointsFormat().fieldsReader(segmentReadState)
            } else {
                pointsReader = null
            }

            if (coreFieldInfos.hasVectorValues()) {
                knnVectorsReader = codec.knnVectorsFormat().fieldsReader(segmentReadState)
            } else {
                knnVectorsReader = null
            }

            success = true
        } catch (e: EOFException) {
            throw CorruptIndexException("Problem reading index from $dir", dir.toString(), e)
        } catch (e: FileNotFoundException) {
            throw CorruptIndexException("Problem reading index from $dir", dir.toString(), e)
        } catch (e: NoSuchFileException) {
            throw CorruptIndexException("Problem reading index.", e.file, e)
        } finally {
            if (!success) {
                runBlocking { //for now I will put decRec in runBlocking but if there is problem such as deadlock, we will refactor.
                    decRef()
                }
            }
        }
    }

    fun getCacheHelper(): IndexReader.CacheHelper {
        return cacheHelper
    }

    private suspend fun notifyCoreClosedListeners() {
        coreListenersMutex.withLock {
            IOUtils.applyToAll(
                coreClosedListeners
            ) { listener: ClosedListener ->
                listener.onClose(cacheHelper.key)
            }
        }
    }

    override fun toString(): String {
        return "SegmentCoreReader($segment)"
    }
}
