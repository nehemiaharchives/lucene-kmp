package org.gnit.lucenekmp.index

import kotlinx.coroutines.Job
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.Version
import okio.IOException
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert

/**
 * The SegmentMerger class combines two or more Segments, represented by an IndexReader, into a
 * single Segment. Call the merge method to combine the segments.
 *
 * @see .merge
 */
internal class SegmentMerger(
    readers: MutableList<CodecReader>,
    segmentInfo: SegmentInfo,
    infoStream: InfoStream,
    dir: Directory,
    fieldNumbers: FieldInfos.FieldNumbers,
    context: IOContext,
    intraMergeTaskExecutor: Executor
) {
    private val directory: Directory

    private val codec: Codec

    private val context: IOContext

    val mergeState: MergeState
    private val fieldInfosBuilder: FieldInfos.Builder
    val mergeStateCreationThread: Job /*java.lang.Thread*/ // TODO Thread is not available in KMP so replacing with Job but not sure if this is correct

    // note, just like in codec apis Directory 'dir' is NOT the same as segmentInfo.dir!!
    init {
        require(context.context == IOContext.Context.MERGE) { "IOContext.context should be MERGE; got: " + context.context }
        mergeState = MergeState(readers, segmentInfo, infoStream, intraMergeTaskExecutor)
        mergeStateCreationThread = Job() /*java.lang.Thread.currentThread()*/ // TODO Thread is not available in KMP so replacing with Job but not sure if this is correct
        directory = dir
        this.codec = segmentInfo.codec
        this.context = context
        this.fieldInfosBuilder = FieldInfos.Builder(fieldNumbers)
        var minVersion: Version? = Version.LATEST
        for (reader in readers) {
            val leafMinVersion: Version? = reader.metaData.minVersion
            if (leafMinVersion == null) {
                minVersion = null
                break
            }
            if (minVersion!!.onOrAfter(leafMinVersion)) {
                minVersion = leafMinVersion
            }
        }
        assert(
            segmentInfo.minVersion == null
        ) { "The min version should be set by SegmentMerger for merged segments" }
        segmentInfo.minVersion = minVersion
        if (mergeState.infoStream.isEnabled("SM")) {
            if (segmentInfo.indexSort != null) {
                mergeState.infoStream.message(
                    "SM", "index sort during merge: " + segmentInfo.indexSort
                )
            }
        }
    }

    /** True if any merging should happen  */
    fun shouldMerge(): Boolean {
        return mergeState.segmentInfo.maxDoc() > 0
    }

    private fun mergeState(): MergeState {
        /*assert(java.lang.Thread.currentThread() === mergeStateCreationThread)*/ // TODO Thread is not available in KMP need to think what to check here
        return mergeState
    }

    /**
     * Merges the readers into the directory passed to the constructor
     *
     * @return The number of documents that were merged
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun merge(): MergeState {
        check(shouldMerge()) { "Merge would result in 0 document segment" }
        mergeFieldInfos()

        val numMerged = mergeWithLogging({ this.mergeFields() }, "stored fields")
        assert(
            numMerged == mergeState.segmentInfo.maxDoc()
        ) {
            ("numMerged="
                    + numMerged
                    + " vs mergeState.segmentInfo.maxDoc()="
                    + mergeState.segmentInfo.maxDoc())
        }

        val segmentWriteState =
            SegmentWriteState(
                mergeState.infoStream,
                directory,
                mergeState.segmentInfo,
                mergeState.mergeFieldInfos,
                null,
                context
            )
        val segmentReadState =
            SegmentReadState(
                directory,
                mergeState.segmentInfo,
                mergeState.mergeFieldInfos!!,
                IOContext.DEFAULT,
                segmentWriteState.segmentSuffix
            )

        if (mergeState.mergeFieldInfos!!.hasNorms()) {
            mergeWithLogging({ segmentWriteState: SegmentWriteState, segmentReadState: SegmentReadState ->
                this.mergeNorms(
                    segmentWriteState,
                    segmentReadState
                )
            }, segmentWriteState, segmentReadState, "norms", numMerged)
        }

        mergeWithLogging({ segmentWriteState: SegmentWriteState, segmentReadState: SegmentReadState ->
            this.mergeTerms(
                segmentWriteState,
                segmentReadState
            )
        }, segmentWriteState, segmentReadState, "postings", numMerged)

        if (mergeState.mergeFieldInfos!!.hasDocValues()) {
            mergeWithLogging(
                { segmentWriteState: SegmentWriteState, segmentReadState: SegmentReadState ->
                    this.mergeDocValues(
                        segmentWriteState,
                        segmentReadState
                    )
                }, segmentWriteState, segmentReadState, "doc values", numMerged
            )
        }

        if (mergeState.mergeFieldInfos!!.hasPointValues()) {
            mergeWithLogging({ segmentWriteState: SegmentWriteState, segmentReadState: SegmentReadState ->
                this.mergePoints(
                    segmentWriteState,
                    segmentReadState
                )
            }, segmentWriteState, segmentReadState, "points", numMerged)
        }

        if (mergeState.mergeFieldInfos!!.hasVectorValues()) {
            mergeWithLogging(
                { segmentWriteState: SegmentWriteState, segmentReadState: SegmentReadState ->
                    this.mergeVectorValues(
                        segmentWriteState,
                        segmentReadState
                    )
                },
                segmentWriteState,
                segmentReadState,
                "numeric vectors",
                numMerged
            )
        }

        if (mergeState.mergeFieldInfos!!.hasTermVectors()) {
            mergeWithLogging({ this.mergeTermVectors() }, "term vectors")
        }

        // write the merged infos
        mergeWithLogging(
            { segmentWriteState: SegmentWriteState, segmentReadState: SegmentReadState ->
                this.mergeFieldInfos(
                    segmentWriteState,
                    segmentReadState
                )
            }, segmentWriteState, segmentReadState, "field infos", numMerged
        )

        return mergeState
    }

    @Throws(IOException::class)
    private fun mergeFieldInfos(
        segmentWriteState: SegmentWriteState,
        segmentReadState: SegmentReadState
    ) {
        codec
            .fieldInfosFormat()
            .write(directory, mergeState.segmentInfo, "", mergeState.mergeFieldInfos!!, context)
    }

    @Throws(IOException::class)
    private fun mergeDocValues(
        segmentWriteState: SegmentWriteState,
        segmentReadState: SegmentReadState
    ) {
        val mergeState: MergeState = mergeState()
        codec.docValuesFormat().fieldsConsumer(segmentWriteState).use { consumer ->
            consumer.merge(mergeState)
        }
    }

    @Throws(IOException::class)
    private fun mergePoints(
        segmentWriteState: SegmentWriteState,
        segmentReadState: SegmentReadState
    ) {
        val mergeState: MergeState = mergeState()
        codec.pointsFormat().fieldsWriter(segmentWriteState).use { writer ->
            writer.merge(mergeState)
        }
    }

    @Throws(IOException::class)
    private fun mergeNorms(
        segmentWriteState: SegmentWriteState,
        segmentReadState: SegmentReadState
    ) {
        val mergeState: MergeState = mergeState()
        codec.normsFormat().normsConsumer(segmentWriteState).use { consumer ->
            consumer.merge(mergeState)
        }
    }

    @Throws(IOException::class)
    private fun mergeTerms(
        segmentWriteState: SegmentWriteState,
        segmentReadState: SegmentReadState
    ) {
        val norms: NormsProducer? = if (mergeState.mergeFieldInfos!!.hasNorms()) {
            codec.normsFormat().normsProducer(segmentReadState)
        } else {
            null
        }

        var normsMergeInstance: NormsProducer? = null

        if(norms != null) {
            // Use the merge instance in order to reuse the same IndexInput for all terms
            normsMergeInstance = norms.mergeInstance
        }

        if(mergeState.mergeFieldInfos!!.hasPostings()){
            val consumer = codec.postingsFormat().fieldsConsumer(segmentWriteState)
            consumer.merge(mergeState, normsMergeInstance!!)
        }
    }

    fun mergeFieldInfos() {
        for (readerFieldInfos in mergeState.fieldInfos) {
            if (readerFieldInfos != null) {
                for (fi in readerFieldInfos) {
                    fieldInfosBuilder.add(fi)
                }
            }
        }
        mergeState.mergeFieldInfos = fieldInfosBuilder.finish()
    }

    /**
     * Merge stored fields from each of the segments into the new one.
     *
     * @return The number of documents in all of the readers
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    private fun mergeFields(): Int {
        val mergeState: MergeState = mergeState()
        codec.storedFieldsFormat().fieldsWriter(directory, mergeState.segmentInfo, context).use { fieldsWriter ->
            return fieldsWriter.merge(mergeState)
        }
    }

    /**
     * Merge the TermVectors from each of the segments into the new one.
     *
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    private fun mergeTermVectors(): Int {
        val mergeState: MergeState = mergeState()
        codec.termVectorsFormat().vectorsWriter(directory, mergeState.segmentInfo, context).use { termVectorsWriter ->
            val numMerged: Int = termVectorsWriter.merge(mergeState)
            assert(numMerged == mergeState.segmentInfo.maxDoc())
            return numMerged
        }
    }

    @Throws(IOException::class)
    private fun mergeVectorValues(
        segmentWriteState: SegmentWriteState,
        segmentReadState: SegmentReadState
    ) {
        val mergeState: MergeState = mergeState()
        codec.knnVectorsFormat().fieldsWriter(segmentWriteState).use { writer ->
            writer.merge(mergeState)
        }
    }

    private fun interface Merger {
        @Throws(IOException::class)
        fun merge(): Int
    }

    private fun interface VoidMerger {
        @Throws(IOException::class)
        fun merge(
            segmentWriteState: SegmentWriteState,
            segmentReadState: SegmentReadState
        )
    }

    @Throws(IOException::class)
    private fun mergeWithLogging(merger: Merger, formatName: String): Int {
        var t0: Long = 0
        if (mergeState.infoStream.isEnabled("SM")) {
            t0 = System.nanoTime()
        }
        val numMerged = merger.merge()
        if (mergeState.infoStream.isEnabled("SM")) {
            mergeState.infoStream.message(
                "SM",
                (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
                    .toString() + " ms to merge "
                        + formatName
                        + " ["
                        + numMerged
                        + " docs]")
            )
        }
        return numMerged
    }

    @Throws(IOException::class)
    private fun mergeWithLogging(
        merger: VoidMerger,
        segmentWriteState: SegmentWriteState,
        segmentReadState: SegmentReadState,
        formatName: String,
        numMerged: Int
    ) {
        var t0: Long = 0
        if (mergeState.infoStream.isEnabled("SM")) {
            t0 = System.nanoTime()
        }
        merger.merge(segmentWriteState, segmentReadState)
        val t1: Long = System.nanoTime()
        if (mergeState.infoStream.isEnabled("SM")) {
            mergeState.infoStream.message(
                "SM",
                (TimeUnit.NANOSECONDS.toMillis(t1 - t0)
                    .toString() + " ms to merge "
                        + formatName
                        + " ["
                        + numMerged
                        + " docs]")
            )
        }
    }
}
