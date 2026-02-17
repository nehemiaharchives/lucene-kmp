package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.MutablePointTree
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.PointTree
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.IORunnable
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.bkd.BKDConfig
import org.gnit.lucenekmp.util.bkd.BKDWriter

/** Writes dimensional values  */
open class Lucene90PointsWriter(writeState: SegmentWriteState, maxPointsInLeafNode: Int, maxMBSortInHeap: Double) :
    PointsWriter() {
    /** Outputs used to write the BKD tree data files.  */
    protected var metaOut: IndexOutput? = null
    protected var indexOut: IndexOutput? = null
    protected val dataOut: IndexOutput

    val writeState: SegmentWriteState
    val maxPointsInLeafNode: Int
    val maxMBSortInHeap: Double
    private var finished = false

    /** Full constructor  */
    init {
        assert(writeState.fieldInfos!!.hasPointValues())
        this.writeState = writeState
        this.maxPointsInLeafNode = maxPointsInLeafNode
        this.maxMBSortInHeap = maxMBSortInHeap
        val dataFileName: String =
            IndexFileNames.segmentFileName(
                writeState.segmentInfo.name,
                writeState.segmentSuffix,
                Lucene90PointsFormat.DATA_EXTENSION
            )
        dataOut = writeState.directory.createOutput(dataFileName, writeState.context)
        var success = false
        try {
            CodecUtil.writeIndexHeader(
                dataOut,
                Lucene90PointsFormat.DATA_CODEC_NAME,
                Lucene90PointsFormat.VERSION_CURRENT,
                writeState.segmentInfo.getId(),
                writeState.segmentSuffix
            )

            val metaFileName: String =
                IndexFileNames.segmentFileName(
                    writeState.segmentInfo.name,
                    writeState.segmentSuffix,
                    Lucene90PointsFormat.META_EXTENSION
                )
            val createdMetaOut = writeState.directory.createOutput(metaFileName, writeState.context)
            metaOut = createdMetaOut
            CodecUtil.writeIndexHeader(
                createdMetaOut,
                Lucene90PointsFormat.META_CODEC_NAME,
                Lucene90PointsFormat.VERSION_CURRENT,
                writeState.segmentInfo.getId(),
                writeState.segmentSuffix
            )

            val indexFileName: String =
                IndexFileNames.segmentFileName(
                    writeState.segmentInfo.name,
                    writeState.segmentSuffix,
                    Lucene90PointsFormat.INDEX_EXTENSION
                )
            val createdIndexOut = writeState.directory.createOutput(indexFileName, writeState.context)
            indexOut = createdIndexOut
            CodecUtil.writeIndexHeader(
                createdIndexOut,
                Lucene90PointsFormat.INDEX_CODEC_NAME,
                Lucene90PointsFormat.VERSION_CURRENT,
                writeState.segmentInfo.getId(),
                writeState.segmentSuffix
            )

            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    /**
     * Uses the defaults values for `maxPointsInLeafNode` (512) and `maxMBSortInHeap`
     * (16.0)
     */
    constructor(writeState: SegmentWriteState) : this(
        writeState,
        BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE,
        BKDWriter.DEFAULT_MAX_MB_SORT_IN_HEAP.toDouble()
    )

    @Throws(IOException::class)
    override fun writeField(fieldInfo: FieldInfo, reader: PointsReader) {
        val metaOut = checkNotNull(metaOut) { "metaOut is not initialized" }
        val indexOut = checkNotNull(indexOut) { "indexOut is not initialized" }
        val values: PointTree = reader.getValues(fieldInfo.name)!!.pointTree

        val config =
            BKDConfig(
                fieldInfo.pointDimensionCount,
                fieldInfo.pointIndexDimensionCount,
                fieldInfo.pointNumBytes,
                maxPointsInLeafNode
            )

        BKDWriter(
            writeState.segmentInfo.maxDoc(),
            writeState.directory,
            writeState.segmentInfo.name,
            config,
            maxMBSortInHeap,
            values.size()
        ).use { writer ->
            if (values is MutablePointTree) {
                val finalizer: IORunnable? =
                    writer.writeField(
                        metaOut, indexOut, dataOut, fieldInfo.name, values
                    )
                if (finalizer != null) {
                    metaOut.writeInt(fieldInfo.number)
                    finalizer.run()
                }
                return
            }
            values.visitDocValues(
                object : IntersectVisitor {
                    override fun visit(docID: Int) {
                        throw IllegalStateException()
                    }

                    @Throws(IOException::class)
                    override fun visit(docID: Int, packedValue: ByteArray) {
                        writer.add(packedValue, docID)
                    }

                    override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                        return Relation.CELL_CROSSES_QUERY
                    }
                })

            // We could have 0 points on merge since all docs with dimensional fields may be deleted:
            val finalizer: IORunnable? = writer.finish(metaOut, indexOut, dataOut)
            if (finalizer != null) {
                metaOut.writeInt(fieldInfo.number)
                finalizer.run()
            }
        }
    }

    @Throws(IOException::class)
    override fun merge(mergeState: MergeState) {
        val metaOut = checkNotNull(metaOut) { "metaOut is not initialized" }
        val indexOut = checkNotNull(indexOut) { "indexOut is not initialized" }
        /*
     * If indexSort is activated and some of the leaves are not sorted the next test will catch that
     * and the non-optimized merge will run. If the readers are all sorted then it's safe to perform
     * a bulk merge of the points.
     */
        for (reader in mergeState.pointsReaders) {
            if (reader !is Lucene90PointsReader) {
                // We can only bulk merge when all to-be-merged segments use our format:
                super.merge(mergeState)
                return
            }
        }
        for (reader in mergeState.pointsReaders) {
            reader?.checkIntegrity()
        }

        for (fieldInfo in mergeState.mergeFieldInfos!!) {
            if (fieldInfo.pointDimensionCount != 0) {
                if (fieldInfo.pointDimensionCount == 1) {
                    // Worst case total maximum size (if none of the points are deleted):

                    var totMaxSize: Long = 0
                    for (i in 0..<mergeState.pointsReaders.size) {
                        val reader: PointsReader? = mergeState.pointsReaders[i]
                        if (reader != null) {
                            val readerFieldInfos: FieldInfos = mergeState.fieldInfos[i]!!
                            val readerFieldInfo: FieldInfo? = readerFieldInfos.fieldInfo(fieldInfo.name)
                            if (readerFieldInfo != null && readerFieldInfo.pointDimensionCount > 0) {
                                val values: PointValues? = reader.getValues(fieldInfo.name)
                                if (values != null) {
                                    totMaxSize += values.size()
                                }
                            }
                        }
                    }

                    val config =
                        BKDConfig(
                            fieldInfo.pointDimensionCount,
                            fieldInfo.pointIndexDimensionCount,
                            fieldInfo.pointNumBytes,
                            maxPointsInLeafNode
                        )

                    BKDWriter(
                        writeState.segmentInfo.maxDoc(),
                        writeState.directory,
                        writeState.segmentInfo.name,
                        config,
                        maxMBSortInHeap,
                        totMaxSize
                    ).use { writer ->
                        val pointValues: MutableList<PointValues> = ArrayList()
                        val docMaps: MutableList<MergeState.DocMap> = ArrayList()
                        for (i in 0..<mergeState.pointsReaders.size) {
                            val reader: PointsReader? = mergeState.pointsReaders[i]

                            if (reader != null) {
                                // we confirmed this up above

                                require(reader is Lucene90PointsReader)
                                val reader90: Lucene90PointsReader = reader

                                // NOTE: we cannot just use the merged fieldInfo.number (instead of resolving to
                                // this
                                // reader's FieldInfo as we do below) because field numbers can easily be different
                                // when addIndexes(Directory...) copies over segments from another index:
                                val readerFieldInfos: FieldInfos = mergeState.fieldInfos[i]!!
                                val readerFieldInfo: FieldInfo? = readerFieldInfos.fieldInfo(fieldInfo.name)
                                if (readerFieldInfo != null && readerFieldInfo.pointDimensionCount > 0) {
                                    val aPointValues: PointValues? = reader90.getValues(readerFieldInfo.name)
                                    if (aPointValues != null) {
                                        pointValues.add(aPointValues)
                                        docMaps.add(mergeState.docMaps!![i])
                                    }
                                }
                            }
                        }

                        val finalizer: IORunnable? = writer.merge(metaOut, indexOut, dataOut, docMaps, pointValues)
                        if (finalizer != null) {
                            metaOut.writeInt(fieldInfo.number)
                            finalizer.run()
                        }
                    }
                } else {
                    mergeOneField(mergeState, fieldInfo)
                }
            }
        }

        finish()
    }

    @Throws(IOException::class)
    override fun finish() {
        val metaOut = checkNotNull(metaOut) { "metaOut is not initialized" }
        val indexOut = checkNotNull(indexOut) { "indexOut is not initialized" }
        check(!finished) { "already finished" }
        finished = true
        metaOut.writeInt(-1)
        CodecUtil.writeFooter(indexOut)
        CodecUtil.writeFooter(dataOut)
        metaOut.writeLong(indexOut.filePointer)
        metaOut.writeLong(dataOut.filePointer)
        CodecUtil.writeFooter(metaOut)
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(metaOut, indexOut, dataOut)
    }
}
