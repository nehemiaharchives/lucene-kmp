package org.gnit.lucenekmp.index

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.FieldInfosFormat
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOConsumer
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.computeIfAbsent
import org.gnit.lucenekmp.jdkport.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.max
import okio.IOException

// Used by IndexWriter to hold open SegmentReaders (for
// searching or merging), plus pending deletes and updates,
// for a given segment
class ReadersAndUpdates(
    // the major version this index was created with
    private val indexCreatedVersionMajor: Int,
    // Not final because we replace (clone) when we need to
    // change it and it's been shared:
    val info: SegmentCommitInfo,
    // How many further deletions we've done against
    // liveDocs vs when we loaded it or last wrote it:
    private val pendingDeletes: PendingDeletes
) {
    private val logger = KotlinLogging.logger {}
    private val rldMutex = Mutex()

    private fun <T> withRldLock(action: () -> T): T = runBlocking {
        rldMutex.withLock { action() }
    }

    private suspend fun <T> withRldLockSuspend(action: suspend () -> T): T {
        rldMutex.lock()
        try {
            return action()
        } finally {
            rldMutex.unlock()
        }
    }

    // Caller must already hold rldMutex
    private fun getReaderNoLock(context: IOContext): SegmentReader {
        if (reader == null) {
            reader = SegmentReader(info, indexCreatedVersionMajor, context)
            pendingDeletes.onNewReader(reader!!, info)
        }
        reader!!.incRef()
        return reader!!
    }

    // Tracks how many consumers are using this instance:
    @OptIn(ExperimentalAtomicApi::class)
    private val refCount: AtomicInteger = AtomicInteger(1)

    // Set once (null, and then maybe set, and never set again):
    private var reader: SegmentReader? = null

    // Indicates whether this segment is currently being merged. While a segment
    // is merging, all field updates are also registered in the
    // mergingDVUpdates map. Also, calls to writeFieldUpdates merge the
    // updates with mergingDVUpdates.
    // That way, when the segment is done merging, IndexWriter can apply the
    // updates on the merged segment too.
    /*@get:Synchronized*/
    var isMerging: Boolean = false
        private set

    // Holds resolved (to docIDs) doc values updates that have not yet been
    // written to the index
    private val pendingDVUpdates: MutableMap<String, MutableList<DocValuesFieldUpdates>> = HashMap()

    // Holds resolved (to docIDs) doc values updates that were resolved while
    // this segment was being merged; at the end of the merge we carry over
    // these updates (remapping their docIDs) to the newly merged segment
    private val mergingDVUpdates: MutableMap<String, MutableList<DocValuesFieldUpdates>> = HashMap()

    // Only set if there are doc values updates against this segment, and the index is sorted:
    var sortMap: Sorter.DocMap? = null

    @OptIn(ExperimentalAtomicApi::class)
    val ramBytesUsed: AtomicLong = AtomicLong(0)

    /**
     * Init from a previously opened SegmentReader.
     *
     *
     * NOTE: steals incoming ref from reader.
     */
    constructor(
        indexCreatedVersionMajor: Int,
        reader: SegmentReader,
        pendingDeletes: PendingDeletes
    ) : this(indexCreatedVersionMajor, reader.originalSegmentInfo, pendingDeletes) {
        this.reader = reader
        pendingDeletes.onNewReader(reader, info)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun incRef() {
        val rc: Int = refCount.incrementAndFetch()
        assert(rc > 1) { "seg=$info" }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun decRef() {
        val rc: Int = refCount.decrementAndFetch()
        assert(rc >= 0) { "seg=$info" }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun refCount(): Int {
        val rc: Int = refCount.get()
        assert(rc >= 0)
        return rc
    }

    /*@get:Synchronized*/
    val delCount: Int
        get() = pendingDeletes.delCount

    /*@Synchronized*/
    private fun assertNoDupGen(
        fieldUpdates: MutableList<DocValuesFieldUpdates>,
        update: DocValuesFieldUpdates
    ): Boolean {
        for (i in fieldUpdates.indices) {
            val oldUpdate: DocValuesFieldUpdates = fieldUpdates[i]
            if (oldUpdate.delGen == update.delGen) {
                throw AssertionError("duplicate delGen=" + update.delGen + " for seg=" + info)
            }
        }
        return true
    }

    /**
     * Adds a new resolved (meaning it maps docIDs to new values) doc values packet. We buffer these
     * in RAM and write to disk when too much RAM is used or when a merge needs to kick off, or a
     * commit/refresh.
     */
    /*@Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun addDVUpdate(update: DocValuesFieldUpdates) {
        require(update.finished) { "call finish first" }
        var fieldUpdates: MutableList<DocValuesFieldUpdates>? =
            pendingDVUpdates.computeIfAbsent(update.field) { `_`: String -> ArrayList() }
        assert(assertNoDupGen(fieldUpdates!!, update))

        ramBytesUsed.addAndFetch(update.ramBytesUsed())

        fieldUpdates.add(update)

        if (isMerging) {
            fieldUpdates = mergingDVUpdates[update.field]
            if (fieldUpdates == null) {
                fieldUpdates = ArrayList()
                mergingDVUpdates.put(update.field, fieldUpdates)
            }
            fieldUpdates.add(update)
        }
    }

    /*@get:Synchronized*/
    val numDVUpdates: Long
        get() {
            var count: Long = 0
            for (updates in pendingDVUpdates.values) {
                count += updates.size.toLong()
            }
            return count
        }

    /** Returns a [SegmentReader].  */
    /*@Synchronized*/
    @Throws(IOException::class)
    fun getReader(context: IOContext): SegmentReader {
        return withRldLock {
            getReaderNoLock(context)
        }
    }

    /*@Synchronized*/
    suspend fun release(sr: SegmentReader) {
        withRldLockSuspend {
            assert(info === sr.originalSegmentInfo)
            sr.decRef()
        }
    }

    /*@Synchronized*/
    suspend fun delete(docID: Int): Boolean {
        if (reader == null && pendingDeletes.mustInitOnDelete()) {
            getReader(IOContext.DEFAULT).decRef() // pass a reader to initialize the pending deletes
        }
        return pendingDeletes.delete(docID)
    }

    // NOTE: removes callers ref
    /*@Synchronized*/
    suspend fun dropReaders() {
        withRldLockSuspend {
            // TODO: can we somehow use IOUtils here...  problem is
            // we are calling .decRef not .close)...
            if (reader != null) {
                try {
                    reader!!.decRef()
                } finally {
                    reader = null
                }
            }

            decRef()
        }
    }

    /**
     * Returns a ref to a clone. NOTE: you should decRef() the reader when you're done (ie do not call
     * close()).
     */
    /*@Synchronized*/
    suspend fun getReadOnlyClone(context: IOContext): SegmentReader {
        return withRldLockSuspend {
            if (reader == null) {
                getReaderNoLock(context).decRef()
                checkNotNull(reader)
            }
            // force new liveDocs
            val liveDocs: Bits? = pendingDeletes.liveDocs
            if (liveDocs != null) {
                SegmentReader(
                    info, reader!!, liveDocs, pendingDeletes.hardLiveDocs, pendingDeletes.numDocs(), true
                )
            } else {
                // liveDocs == null and reader != null. That can only be if there are no deletes
                assert(reader!!.liveDocs == null)
                reader!!.incRef()
                reader!!
            }
        }
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    fun numDeletesToMerge(policy: MergePolicy): Int {
        return pendingDeletes.numDeletesToMerge(policy) { this.latestReader }
    }

    /*@get:Synchronized*/
    private val latestReader: CodecReader
        get() {
            if (this.reader == null) {
                // get a reader and dec the ref right away we just make sure we have a reader
                runBlocking { getReader(IOContext.DEFAULT).decRef() }
            }
            if (pendingDeletes.needsRefresh(reader!!)) {
                // we have a reader but its live-docs are out of sync. let's create a temporary one that we
                // never share
                runBlocking { swapNewReaderWithLatestLiveDocs() }
            }
            return reader!!
        }

    /*@get:Synchronized*/
    val liveDocs: Bits?
        /** Returns a snapshot of the live docs.  */
        get() = pendingDeletes.liveDocs

    /*@get:Synchronized*/
    val hardLiveDocs: Bits?
        /** Returns the live-docs bits excluding documents that are not live due to soft-deletes  */
        get() = pendingDeletes.hardLiveDocs

    /*@Synchronized*/
    fun dropChanges() {
        // Discard (don't save) changes when we are dropping
        // the reader; this is used only on the sub-readers
        // after a successful merge.  If deletes had
        // accumulated on those sub-readers while the merge
        // is running, by now we have carried forward those
        // deletes onto the newly merged segment, so we can
        // discard them on the sub-readers:
        pendingDeletes.dropChanges()
        dropMergingUpdates()
    }

    // Commit live docs (writes new _X_N.del files) and field updates (writes new
    // _X_N updates files) to the directory; returns true if it wrote any file
    // and false if there were no new deletes or updates to write:
    /*@Synchronized*/
    @Throws(IOException::class)
    fun writeLiveDocs(dir: Directory): Boolean {
        return pendingDeletes.writeLiveDocs(dir)
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    private fun handleDVUpdates(
        infos: FieldInfos,
        dir: Directory,
        dvFormat: DocValuesFormat,
        reader: SegmentReader,
        fieldFiles: MutableMap<Int, MutableSet<String>>,
        maxDelGen: Long,
        infoStream: InfoStream
    ) {
        for (ent in pendingDVUpdates.entries) {
            val field = ent.key
            val updates: MutableList<DocValuesFieldUpdates> = ent.value
            val type: DocValuesType = updates[0].type
            assert(
                type == DocValuesType.NUMERIC || type == DocValuesType.BINARY
            ) { "unsupported type: $type" }
            val updatesToApply: MutableList<DocValuesFieldUpdates> = ArrayList()
            var bytes: Long = 0
            for (update in updates) {
                if (update.delGen <= maxDelGen) {
                    // safe to apply this one
                    bytes += update.ramBytesUsed()
                    updatesToApply.add(update)
                }
            }
            if (updatesToApply.isEmpty()) {
                // nothing to apply yet
                continue
            }
            if (infoStream.isEnabled("BD")) {
                infoStream.message(
                    "BD",
                    "now write ${updatesToApply.size} pending numeric DV updates for field=$field, seg=$info, bytes=${bytes / 1024.0 / 1024.0} MB"
                )
            }
            val nextDocValuesGen: Long = info.nextDocValuesGen
            val segmentSuffix = nextDocValuesGen.toString(Character.MAX_RADIX.coerceIn(2, 36))
            val updatesContext = IOContext(FlushInfo(info.info.maxDoc(), bytes))
            val fieldInfo: FieldInfo = checkNotNull(infos.fieldInfo(field))
            fieldInfo.docValuesGen = nextDocValuesGen
            val fieldInfos = FieldInfos(arrayOf(fieldInfo))
            // separately also track which files were created for this gen
            val trackingDir = TrackingDirectoryWrapper(dir)
            val state =
                SegmentWriteState(
                    null, trackingDir, info.info, fieldInfos, null, updatesContext, segmentSuffix
                )
            dvFormat.fieldsConsumer(state).use { fieldsConsumer ->
                val updateSupplier: (FieldInfo) -> DocValuesFieldUpdates.Iterator =
                    { info: FieldInfo ->
                        require(info == fieldInfo) { "expected field info for field: " + fieldInfo.name + " but got: " + info.name }
                        val subs: Array<DocValuesFieldUpdates.Iterator> =
                            kotlin.arrayOfNulls<DocValuesFieldUpdates.Iterator>(updatesToApply.size) as Array<DocValuesFieldUpdates.Iterator>
                        for (i in subs.indices) {
                            subs[i] = updatesToApply[i].iterator()
                        }
                        DocValuesFieldUpdates.mergedIterator(subs)!!
                    }
                pendingDeletes.onDocValuesUpdate(fieldInfo, updateSupplier(fieldInfo))
                if (type == DocValuesType.BINARY) {
                    fieldsConsumer.addBinaryField(
                        fieldInfo,
                        object : EmptyDocValuesProducer() {
                            @Throws(IOException::class)
                            override fun getBinary(fieldInfoIn: FieldInfo): BinaryDocValues {
                                val iterator: DocValuesFieldUpdates.Iterator =
                                    updateSupplier(fieldInfo)
                                val mergedDocValues: MergedDocValues<BinaryDocValues> =
                                    MergedDocValues(
                                        DocValues.getBinary(reader, field),
                                        DocValuesFieldUpdates.Iterator.asBinaryDocValues(
                                            iterator
                                        ),
                                        iterator
                                    )
                                // Merge sort of the original doc values with updated doc values:
                                return object : BinaryDocValues() {
                                    @Throws(IOException::class)
                                    override fun binaryValue(): BytesRef? {
                                        return mergedDocValues.currentValuesSupplier!!.binaryValue()
                                    }

                                    override fun advanceExact(target: Int): Boolean {
                                        return mergedDocValues.advanceExact(target)
                                    }

                                    override fun docID(): Int {
                                        return mergedDocValues.docID()
                                    }

                                    @Throws(IOException::class)
                                    override fun nextDoc(): Int {
                                        return mergedDocValues.nextDoc()
                                    }

                                    override fun advance(target: Int): Int {
                                        return mergedDocValues.advance(target)
                                    }

                                    override fun cost(): Long {
                                        return mergedDocValues.cost()
                                    }
                                }
                            }
                        })
                } else {
                    // write the numeric updates to a new gen'd docvalues file
                    fieldsConsumer.addNumericField(
                        fieldInfo,
                        object : EmptyDocValuesProducer() {
                            @Throws(IOException::class)
                            override fun getNumeric(fieldInfoIn: FieldInfo): NumericDocValues {
                                val iterator: DocValuesFieldUpdates.Iterator =
                                    updateSupplier(fieldInfo)
                                val mergedDocValues: MergedDocValues<NumericDocValues> =
                                    MergedDocValues(
                                        DocValues.getNumeric(reader, field),
                                        DocValuesFieldUpdates.Iterator.asNumericDocValues(
                                            iterator
                                        ),
                                        iterator
                                    )
                                // Merge sort of the original doc values with updated doc values:
                                return object : NumericDocValues() {
                                    @Throws(IOException::class)
                                    override fun longValue(): Long {
                                        return mergedDocValues.currentValuesSupplier!!.longValue()
                                    }

                                    override fun advanceExact(target: Int): Boolean {
                                        return mergedDocValues.advanceExact(target)
                                    }

                                    override fun docID(): Int {
                                        return mergedDocValues.docID()
                                    }

                                    @Throws(IOException::class)
                                    override fun nextDoc(): Int {
                                        return mergedDocValues.nextDoc()
                                    }

                                    override fun advance(target: Int): Int {
                                        return mergedDocValues.advance(target)
                                    }

                                    override fun cost(): Long {
                                        return mergedDocValues.cost()
                                    }
                                }
                            }
                        })
                }
            }
            info.advanceDocValuesGen()
            assert(!fieldFiles.containsKey(fieldInfo.number))
            fieldFiles.put(fieldInfo.number, trackingDir.createdFiles)
        }
    }

    /**
     * This class merges the current on-disk DV with an incoming update DV instance and merges the two
     * instances giving the incoming update precedence in terms of values, in other words the values
     * of the update always wins over the on-disk version.
     */
    internal class MergedDocValues<DocValuesInstance : DocValuesIterator>(
        private val onDiskDocValues: DocValuesInstance,
        private val updateDocValues: DocValuesInstance,
        private val updateIterator: DocValuesFieldUpdates.Iterator
    ) : DocValuesIterator() {

        // merged docID
        private var docIDOut = -1

        // docID from our original doc values
        private var docIDOnDisk = -1

        // docID from our updates
        private var updateDocID = -1

        var currentValuesSupplier: DocValuesInstance? = null

        override fun docID(): Int {
            return docIDOut
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun advanceExact(target: Int): Boolean {
            throw UnsupportedOperationException()
        }

        override fun cost(): Long {
            return onDiskDocValues.cost()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            var hasValue: Boolean
            do {
                if (docIDOnDisk == docIDOut) {
                    docIDOnDisk = onDiskDocValues?.nextDoc() ?: NO_MORE_DOCS
                }
                if (updateDocID == docIDOut) {
                    updateDocID = updateDocValues.nextDoc()
                }
                if (docIDOnDisk < updateDocID) {
                    // no update to this doc - we use the on-disk values
                    docIDOut = docIDOnDisk
                    currentValuesSupplier = onDiskDocValues
                    hasValue = true
                } else {
                    docIDOut = updateDocID
                    if (docIDOut != NO_MORE_DOCS) {
                        currentValuesSupplier = updateDocValues
                        hasValue = updateIterator.hasValue()
                    } else {
                        hasValue = true
                    }
                }
            } while (hasValue == false)
            return docIDOut
        }
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    private fun writeFieldInfosGen(
        fieldInfos: FieldInfos,
        dir: Directory,
        infosFormat: FieldInfosFormat
    ): MutableSet<String> {
        val nextFieldInfosGen: Long = info.nextFieldInfosGen
        val segmentSuffix = nextFieldInfosGen.toString(Character.MAX_RADIX.coerceIn(2, 36))
        // we write approximately that many bytes (based on Lucene46DVF):
        // HEADER + FOOTER: 40
        // 90 bytes per-field (over estimating long name and attributes map)
        val estInfosSize: Long = 40 + 90L * fieldInfos.size()
        val infosContext = IOContext(FlushInfo(info.info.maxDoc(), estInfosSize))
        // separately also track which files were created for this gen
        val trackingDir = TrackingDirectoryWrapper(dir)
        infosFormat.write(trackingDir, info.info, segmentSuffix, fieldInfos, infosContext)
        info.advanceFieldInfosGen()
        return trackingDir.createdFiles
    }

    /*@Synchronized*/
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun writeFieldUpdates(
        dir: Directory,
        fieldNumbers: FieldInfos.FieldNumbers,
        maxDelGen: Long,
        infoStream: InfoStream
    ): Boolean {
        val startTimeNS: Long = System.nanoTime()
        val newDVFiles: MutableMap<Int, MutableSet<String>> = HashMap()
        var fieldInfosFiles: MutableSet<String>?
        var fieldInfos: FieldInfos?
        var any = false
        for (updates in pendingDVUpdates.values) {
            for (update in updates) {
                if (update.delGen <= maxDelGen && update.any()) {
                    any = true
                    break
                }
            }
        }

        if (any == false) {
            // no updates
            return false
        }

        // Do this so we can delete any created files on
        // exception; this saves all codecs from having to do it:
        val trackingDir = TrackingDirectoryWrapper(dir)

        var success = false
        try {
            val codec: Codec = info.info.codec

            // reader could be null e.g. for a just merged segment (from
            // IndexWriter.commitMergedDeletes).
            val reader: SegmentReader?
            if (this.reader == null) {
                reader = SegmentReader(
                    info,
                    indexCreatedVersionMajor,
                    IOContext.READONCE
                )
                pendingDeletes.onNewReader(reader, info)
            } else {
                reader = this.reader
            }

            try {
                // clone FieldInfos so that we can update their dvGen separately from
                // the reader's infos and write them to a new fieldInfos_gen file.
                var maxFieldNumber = -1
                val byName: MutableMap<String, FieldInfo> = HashMap()
                for (fi in reader!!.fieldInfos) {
                    // cannot use builder.add(fi) because it does not preserve
                    // the local field number. Field numbers can be different from
                    // the global ones if the segment was created externally (and added to
                    // this index with IndexWriter#addIndexes(Directory)).
                    byName.put(fi.name, cloneFieldInfo(fi, fi.number))
                    maxFieldNumber = max(fi.number, maxFieldNumber)
                }

                // create new fields with the right DV type
                for (updates in pendingDVUpdates.values) {
                    val update: DocValuesFieldUpdates = updates[0]
                    if (byName.containsKey(update.field)) {
                        // the field already exists in this segment
                        val fi: FieldInfo = byName[update.field]!!
                        assert(fi.docValuesType == update.type)
                    } else {
                        // the field is not present in this segment so we clone the global field
                        // (which is guaranteed to exist) and remaps its field number locally.
                        val fi: FieldInfo = checkNotNull(
                            fieldNumbers.constructFieldInfo(update.field, update.type, maxFieldNumber + 1)
                        )
                        maxFieldNumber++
                        byName.put(fi.name, fi)
                    }
                }
                fieldInfos =
                    FieldInfos(byName.values.toTypedArray<FieldInfo>())
                val docValuesFormat: DocValuesFormat = codec.docValuesFormat()

                handleDVUpdates(
                    fieldInfos, trackingDir, docValuesFormat, reader, newDVFiles, maxDelGen, infoStream
                )

                fieldInfosFiles = writeFieldInfosGen(fieldInfos, trackingDir, codec.fieldInfosFormat())
            } finally {
                if (reader !== this.reader) {
                    reader!!.close()
                }
            }

            success = true
        } finally {
            if (success == false) {
                // Advance only the nextWriteFieldInfosGen and nextWriteDocValuesGen, so
                // that a 2nd attempt to write will write to a new file
                info.advanceNextWriteFieldInfosGen()
                info.advanceNextWriteDocValuesGen()

                // Delete any partially created file(s):
                for (fileName in trackingDir.createdFiles) {
                    IOUtils.deleteFilesIgnoringExceptions(dir, fileName)
                }
            }
        }

        // Prune the now-written DV updates:
        var bytesFreed: Long = 0
        val it: MutableIterator<MutableMap.MutableEntry<String, MutableList<DocValuesFieldUpdates>>> =
            pendingDVUpdates.entries.iterator()
        while (it.hasNext()) {
            val ent: MutableMap.MutableEntry<String, MutableList<DocValuesFieldUpdates>> =
                it.next()
            var upto = 0
            val updates: MutableList<DocValuesFieldUpdates> = ent.value
            for (update in updates) {
                if (update.delGen > maxDelGen) {
                    // not yet applied
                    updates[upto] = update
                    upto++
                } else {
                    bytesFreed += update.ramBytesUsed()
                }
            }
            if (upto == 0) {
                it.remove()
            } else {
                updates.subList(upto, updates.size).clear()
            }
        }

        val bytes: Long = ramBytesUsed.addAndFetch(-bytesFreed)
        assert(bytes >= 0)

        // writing field updates succeeded
        checkNotNull(fieldInfosFiles)
        info.setFieldInfosFiles(fieldInfosFiles)

        // update the doc-values updates files. the files map each field to its set
        // of files, hence we copy from the existing map all fields w/ updates that
        // were not updated in this session, and add new mappings for fields that
        // were updated now.
        assert(newDVFiles.isEmpty() == false)
        for (e in info.docValuesUpdatesFiles.entries) {
            if (newDVFiles.containsKey(e.key) == false) {
                newDVFiles.put(e.key, e.value)
            }
        }
        info.docValuesUpdatesFiles = newDVFiles

        // if there is a reader open, reopen it to reflect the updates
        if (reader != null) {
            swapNewReaderWithLatestLiveDocs()
        }

        if (infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                "done write field updates for seg=info; took ${
                    (System.nanoTime() - startTimeNS) / TimeUnit.SECONDS.toNanos(
                        1
                    ).toDouble()
                }s; new files: $newDVFiles"
            )
        }
        return true
    }

    private fun cloneFieldInfo(
        fi: FieldInfo,
        fieldNumber: Int
    ): FieldInfo {
        return FieldInfo(
            fi.name,
            fieldNumber,
            fi.hasTermVectors(),
            fi.omitsNorms(),
            fi.hasPayloads(),
            fi.indexOptions,
            fi.docValuesType,
            fi.docValuesSkipIndexType(),
            fi.docValuesGen,
            HashMap(fi.attributes()),
            fi.pointDimensionCount,
            fi.pointIndexDimensionCount,
            fi.pointNumBytes,
            fi.vectorDimension,
            fi.vectorEncoding,
            fi.vectorSimilarityFunction,
            fi.isSoftDeletesField,
            fi.isParentField
        )
    }

    private suspend fun createNewReaderWithLatestLiveDocs(reader: SegmentReader): SegmentReader {
        checkNotNull(reader)
        /*assert(java.lang.Thread.holdsLock(this)) { java.lang.Thread.currentThread().getName() }*/ // this operation is jvm specific, not possible in kotlin common
        val newReader = SegmentReader(
            info,
            reader,
            pendingDeletes.liveDocs,
            pendingDeletes.hardLiveDocs,
            pendingDeletes.numDocs(),
            true
        )
        var success2 = false
        try {
            pendingDeletes.onNewReader(newReader, info)
            reader.decRef()
            success2 = true
        } finally {
            if (success2 == false) {
                newReader.decRef()
            }
        }
        return newReader
    }

    private suspend fun swapNewReaderWithLatestLiveDocs() {
        reader = createNewReaderWithLatestLiveDocs(reader!!)
    }

    /*@Synchronized*/
    fun setIsMerging() {
        // This ensures any newly resolved doc value updates while we are merging are
        // saved for re-applying after this segment is done merging:
        if (isMerging == false) {
            isMerging = true
            assert(mergingDVUpdates.isEmpty())
        }
    }

    /** Returns a reader for merge, with the latest doc values updates and deletions.  */
    /*@Synchronized*/
    suspend fun getReaderForMerge(
        context: IOContext,
        readerConsumer: IOConsumer<MergePolicy.MergeReader>
    ): MergePolicy.MergeReader {
        return withRldLockSuspend {
            // We must carry over any still-pending DV updates because they were not
            // successfully written, e.g. because there was a hole in the delGens,
            // or they arrived after we wrote all DVs for merge but before we set
            // isMerging here:
            for (ent in pendingDVUpdates.entries) {
                var mergingUpdates: MutableList<DocValuesFieldUpdates>? = mergingDVUpdates[ent.key]
                if (mergingUpdates == null) {
                    mergingUpdates = ArrayList()
                    mergingDVUpdates.put(ent.key, mergingUpdates)
                }
                mergingUpdates.addAll(ent.value)
            }

            var reader: SegmentReader = getReaderNoLock(context)
            if (pendingDeletes.needsRefresh(reader)
                || reader.segmentInfo.delGen != pendingDeletes.info.delGen
            ) {
                // beware of zombies:
                checkNotNull(pendingDeletes.liveDocs)
                reader = createNewReaderWithLatestLiveDocs(reader)
            }
            assert(pendingDeletes.verifyDocCounts(reader))
            val mergeReader: MergePolicy.MergeReader =
                MergePolicy.MergeReader(reader, pendingDeletes.hardLiveDocs)
            var success = false
            try {
                readerConsumer.accept(mergeReader)
                success = true
                mergeReader
            } finally {
                if (!success) {
                    reader.decRef()
                }
            }
        }
    }

    /**
     * Drops all merging updates. Called from IndexWriter after this segment finished merging (whether
     * successfully or not).
     */
    /*@Synchronized*/
    fun dropMergingUpdates() {
        mergingDVUpdates.clear()
        isMerging = false
    }

    /*@Synchronized*/
    fun getMergingDVUpdates(): MutableMap<String, MutableList<DocValuesFieldUpdates>> {
        // We must atomically (in single sync'd block) clear isMerging when we return the DV updates
        // otherwise we can lose updates:
        isMerging = false
        return mergingDVUpdates
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("ReadersAndLiveDocs(seg=").append(info)
        sb.append(" pendingDeletes=").append(pendingDeletes)
        return sb.toString()
    }

    /*@get:Synchronized*/
    val isFullyDeleted: Boolean
        get() = pendingDeletes.isFullyDeleted { this.latestReader }

    @Throws(IOException::class)
    fun keepFullyDeletedSegment(mergePolicy: MergePolicy): Boolean {
        return mergePolicy.keepFullyDeletedSegment { this.latestReader }
    }
}
