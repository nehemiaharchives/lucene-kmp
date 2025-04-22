package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.IndexWriter.Companion.isCongruentSort
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Executor
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


/**
 * Holds common state used during segment merging.
 *
 * @lucene.experimental
 */
class MergeState {
    /** Maps document IDs from old segments to document IDs in the new segment  */
    val docMaps: Array<DocMap>?

    /** [SegmentInfo] of the newly merged segment.  */
    val segmentInfo: SegmentInfo

    /** [FieldInfos] of the newly merged segment.  */
    var mergeFieldInfos: FieldInfos? = null

    /** Stored field producers being merged  */
    val storedFieldsReaders: Array<StoredFieldsReader?>

    /** Term vector producers being merged  */
    val termVectorsReaders: Array<TermVectorsReader?>

    /** Norms producers being merged  */
    val normsProducers: Array<NormsProducer?>

    /** DocValues producers being merged  */
    val docValuesProducers: Array<DocValuesProducer?>

    /** FieldInfos being merged  */
    val fieldInfos: Array<FieldInfos?>

    /** Live docs for each reader  */
    val liveDocs: Array<Bits?>

    /** Postings to merge  */
    val fieldsProducers: Array<FieldsProducer?>

    /** Point readers to merge  */
    val pointsReaders: Array<PointsReader?>

    /** Vector readers to merge  */
    val knnVectorsReaders: Array<KnnVectorsReader?>

    /** Max docs per reader  */
    val maxDocs: IntArray

    /** InfoStream for debugging messages.  */
    val infoStream: InfoStream

    /** Executor for intra merge activity  */
    val intraMergeTaskExecutor: Executor?

    /** Indicates if the index needs to be sorted *  */
    var needsIndexSort: Boolean = false

    /** Sole constructor.  */
    internal constructor(
        readers: MutableList<CodecReader>,
        segmentInfo: SegmentInfo,
        infoStream: InfoStream,
        intraMergeTaskExecutor: Executor
    ) {
        verifyIndexSort(readers, segmentInfo)
        this.infoStream = infoStream
        val numReaders = readers.size
        this.intraMergeTaskExecutor = intraMergeTaskExecutor

        maxDocs = IntArray(numReaders)
        fieldsProducers = kotlin.arrayOfNulls<FieldsProducer>(numReaders)
        normsProducers = kotlin.arrayOfNulls<NormsProducer>(numReaders)
        storedFieldsReaders = kotlin.arrayOfNulls<StoredFieldsReader>(numReaders)
        termVectorsReaders = kotlin.arrayOfNulls<TermVectorsReader>(numReaders)
        docValuesProducers = kotlin.arrayOfNulls<DocValuesProducer>(numReaders)
        pointsReaders = kotlin.arrayOfNulls<PointsReader>(numReaders)
        knnVectorsReaders = kotlin.arrayOfNulls<KnnVectorsReader>(numReaders)
        fieldInfos = kotlin.arrayOfNulls<FieldInfos>(numReaders)
        liveDocs = kotlin.arrayOfNulls<Bits>(numReaders)

        var numDocs = 0
        for (i in 0..<numReaders) {
            val reader: CodecReader = readers.get(i)

            maxDocs[i] = reader.maxDoc()
            liveDocs[i] = reader.liveDocs
            fieldInfos[i] = reader.fieldInfos

            normsProducers[i] = reader.normsReader
            if (normsProducers[i] != null) {
                normsProducers[i] = normsProducers[i]!!.mergeInstance
            }

            docValuesProducers[i] = reader.docValuesReader
            if (docValuesProducers[i] != null) {
                docValuesProducers[i] = docValuesProducers[i]!!.mergeInstance
            }

            storedFieldsReaders[i] = reader.fieldsReader
            if (storedFieldsReaders[i] != null) {
                storedFieldsReaders[i] = storedFieldsReaders[i]!!.mergeInstance
            }

            termVectorsReaders[i] = reader.termVectorsReader
            if (termVectorsReaders[i] != null) {
                termVectorsReaders[i] = termVectorsReaders[i]!!.mergeInstance
            }

            fieldsProducers[i] = reader.postingsReader
            if (fieldsProducers[i] != null) {
                fieldsProducers[i] = fieldsProducers[i]!!.mergeInstance
            }

            pointsReaders[i] = reader.pointsReader
            if (pointsReaders[i] != null) {
                pointsReaders[i] = pointsReaders[i]!!.mergeInstance
            }

            knnVectorsReaders[i] = reader.vectorReader
            if (knnVectorsReaders[i] != null) {
                knnVectorsReaders[i] = knnVectorsReaders[i]!!.mergeInstance
            }

            numDocs += reader.numDocs()
        }

        segmentInfo.setMaxDoc(numDocs)

        this.segmentInfo = segmentInfo
        this.docMaps = buildDocMaps(readers, segmentInfo.getIndexSort())
    }

    // Remap docIDs around deletions
    private fun buildDeletionDocMaps(readers: MutableList<CodecReader>): Array<DocMap> {
        var totalDocs = 0
        val numReaders = readers.size
        val docMaps = kotlin.arrayOfNulls<DocMap>(numReaders)

        for (i in 0..<numReaders) {
            val reader: LeafReader = readers.get(i)
            val liveDocs: Bits? = reader.liveDocs

            val delDocMap: PackedLongValues?
            if (liveDocs != null) {
                delDocMap = removeDeletes(reader.maxDoc(), liveDocs)
            } else {
                delDocMap = null
            }

            val docBase = totalDocs
            docMaps[i] =
                DocMap { docID: Int ->
                    if (liveDocs == null) {
                        return@DocMap docBase + docID
                    } else if (liveDocs.get(docID)) {
                        return@DocMap docBase + delDocMap!!.get(docID.toLong()).toInt()
                    } else {
                        return@DocMap -1
                    }
                }
            totalDocs += reader.numDocs()
        }

        return docMaps as Array<DocMap>
    }

    @OptIn(ExperimentalTime::class)
    @Throws(IOException::class)
    private fun buildDocMaps(readers: MutableList<CodecReader>, indexSort: Sort): Array<DocMap>? {
        if (indexSort == null) {
            // no index sort ... we only must map around deletions, and rebase to the merged segment's
            // docID space
            return buildDeletionDocMaps(readers)
        } else {
            // do a merge sort of the incoming leaves:

            var result: Array<DocMap>?

            val duratoin = measureTime {
            result = MultiSorter.sort(indexSort, readers)
                if (result == null) {
                    // already sorted so we can switch back to map around deletions
                    return buildDeletionDocMaps(readers)
                } else {
                    needsIndexSort = true
                }
            }

            if (infoStream.isEnabled("SM")) {
                infoStream.message(
                    "SM",
                    "$duratoin to build merge sorted DocMaps"
                )
            }
            return result
        }
    }

    /** A map of doc IDs.  */
    fun interface DocMap {
        /** Return the mapped docID or -1 if the given doc is not mapped.  */
        fun get(docID: Int): Int
    }

    /** Create a new merge instance.  */
    constructor(
        docMaps: Array<DocMap>,
        segmentInfo: SegmentInfo,
        mergeFieldInfos: FieldInfos,
        storedFieldsReaders: Array<StoredFieldsReader?>,
        termVectorsReaders: Array<TermVectorsReader?>,
        normsProducers: Array<NormsProducer?>,
        docValuesProducers: Array<DocValuesProducer?>,
        fieldInfos: Array<FieldInfos?>,
        liveDocs: Array<Bits?>,
        fieldsProducers: Array<FieldsProducer?>,
        pointsReaders: Array<PointsReader?>,
        knnVectorsReaders: Array<KnnVectorsReader?>,
        maxDocs: IntArray,
        infoStream: InfoStream,
        intraMergeTaskExecutor: Executor,
        needsIndexSort: Boolean
    ) {
        this.docMaps = docMaps
        this.segmentInfo = segmentInfo
        this.mergeFieldInfos = mergeFieldInfos
        this.storedFieldsReaders = storedFieldsReaders
        this.termVectorsReaders = termVectorsReaders
        this.normsProducers = normsProducers
        this.docValuesProducers = docValuesProducers
        this.fieldInfos = fieldInfos
        this.liveDocs = liveDocs
        this.fieldsProducers = fieldsProducers
        this.pointsReaders = pointsReaders
        this.knnVectorsReaders = knnVectorsReaders
        this.maxDocs = maxDocs
        this.infoStream = infoStream
        this.intraMergeTaskExecutor = intraMergeTaskExecutor
        this.needsIndexSort = needsIndexSort
    }

    companion object {
        private fun verifyIndexSort(readers: MutableList<CodecReader>, segmentInfo: SegmentInfo) {
            val indexSort: Sort = segmentInfo.getIndexSort()
            if (indexSort == null) {
                return
            }
            for (leaf in readers) {
                val segmentSort: Sort = leaf.metaData.sort
                require(!(segmentSort == null || isCongruentSort(indexSort, segmentSort) == false)) {
                    ("index sort mismatch: merged segment has sort="
                            + indexSort
                            + " but to-be-merged segment has sort="
                            + (if (segmentSort == null) "null" else segmentSort))
                }
            }
        }

        fun removeDeletes(maxDoc: Int, liveDocs: Bits): PackedLongValues {
            val docMapBuilder: PackedLongValues.Builder =
                PackedLongValues.monotonicBuilder(PackedInts.COMPACT)
            var del = 0
            for (i in 0..<maxDoc) {
                docMapBuilder.add((i - del).toLong())
                if (liveDocs.get(i) == false) {
                    ++del
                }
            }
            return docMapBuilder.build()
        }
    }
}
