package org.gnit.lucenekmp.codecs.lucene99


import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.HnswGraphProvider
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.hnsw.HnswGraph
import org.gnit.lucenekmp.util.hnsw.HnswGraphSearcher
import org.gnit.lucenekmp.util.hnsw.OrdinalTranslatedKnnCollector
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.packed.DirectMonotonicReader
import org.gnit.lucenekmp.util.quantization.QuantizedByteVectorValues
import org.gnit.lucenekmp.util.quantization.QuantizedVectorsReader
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.math.ln
import kotlin.math.min

/**
 * Reads vectors from the index segments along with index data structures supporting KNN search.
 *
 * @lucene.experimental
 */
class Lucene99HnswVectorsReader : KnnVectorsReader, QuantizedVectorsReader, HnswGraphProvider {
    private val flatVectorsReader: FlatVectorsReader
    private val fieldInfos: FieldInfos
    private val fields: IntObjectHashMap<FieldEntry>
    private val vectorIndex: IndexInput

    constructor(state: SegmentReadState, flatVectorsReader: FlatVectorsReader) {
        this.fields = IntObjectHashMap()
        this.flatVectorsReader = flatVectorsReader
        var success = false
        this.fieldInfos = state.fieldInfos
        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, Lucene99HnswVectorsFormat.META_EXTENSION
            )
        var versionMeta = -1
        try {
            state.directory.openChecksumInput(metaFileName).use { meta ->
                var priorE: Throwable? = null
                try {
                    versionMeta =
                        CodecUtil.checkIndexHeader(
                            meta,
                            Lucene99HnswVectorsFormat.META_CODEC_NAME,
                            Lucene99HnswVectorsFormat.VERSION_START,
                            Lucene99HnswVectorsFormat.VERSION_CURRENT,
                            state.segmentInfo.getId(),
                            state.segmentSuffix
                        )
                    readFields(meta)
                } catch (exception: Throwable) {
                    priorE = exception
                } finally {
                    CodecUtil.checkFooter(meta, priorE)
                }
                this.vectorIndex =
                    openDataInput(
                        state,
                        versionMeta,
                        Lucene99HnswVectorsFormat.VECTOR_INDEX_EXTENSION,
                        Lucene99HnswVectorsFormat.VECTOR_INDEX_CODEC_NAME,
                        state.context.withReadAdvice(ReadAdvice.RANDOM)
                    )
                success = true
            }
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    private constructor(reader: Lucene99HnswVectorsReader, flatVectorsReader: FlatVectorsReader) {
        this.flatVectorsReader = flatVectorsReader
        this.fieldInfos = reader.fieldInfos
        this.fields = reader.fields
        this.vectorIndex = reader.vectorIndex
    }

    override val mergeInstance: KnnVectorsReader
        get() = Lucene99HnswVectorsReader(this, this.flatVectorsReader.mergeInstance)

    @Throws(IOException::class)
    override fun finishMerge() {
        flatVectorsReader.finishMerge()
    }

    @Throws(IOException::class)
    private fun readFields(meta: ChecksumIndexInput) {
        var fieldNumber: Int = meta.readInt()
        while (fieldNumber != -1) {
            val info: FieldInfo? = fieldInfos.fieldInfo(fieldNumber)
            if (info == null) {
                throw CorruptIndexException("Invalid field number: $fieldNumber", meta)
            }
            val fieldEntry = readField(meta, info)
            validateFieldEntry(info, fieldEntry)
            fields.put(info.number, fieldEntry)
            fieldNumber = meta.readInt()
        }
    }

    private fun validateFieldEntry(info: FieldInfo, fieldEntry: FieldEntry) {
        val dimension: Int = info.vectorDimension
        check(dimension == fieldEntry.dimension) {
            ("Inconsistent vector dimension for field=\""
                    + info.name
                    + "\"; "
                    + dimension
                    + " != "
                    + fieldEntry.dimension)
        }
    }

    @Throws(IOException::class)
    private fun readField(input: IndexInput, info: FieldInfo): FieldEntry {
        val vectorEncoding: VectorEncoding = readVectorEncoding(input)
        val similarityFunction: VectorSimilarityFunction = readSimilarityFunction(input)
        check(similarityFunction === info.vectorSimilarityFunction) {
            ("Inconsistent vector similarity function for field=\""
                    + info.name
                    + "\"; "
                    + similarityFunction
                    + " != "
                    + info.vectorSimilarityFunction)
        }
        return FieldEntry.Companion.create(input, vectorEncoding, info.vectorSimilarityFunction)
    }

    override fun ramBytesUsed(): Long {
        return (SHALLOW_SIZE
                + fields.ramBytesUsed()
                + flatVectorsReader.ramBytesUsed())
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        flatVectorsReader.checkIntegrity()
        CodecUtil.checksumEntireFile(vectorIndex)
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(field: String): FloatVectorValues {
        return flatVectorsReader.getFloatVectorValues(field)!!
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(field: String): ByteVectorValues {
        return flatVectorsReader.getByteVectorValues(field)!!
    }

    private fun getFieldEntry(field: String, expectedEncoding: VectorEncoding): FieldEntry {
        val info: FieldInfo? = fieldInfos.fieldInfo(field)
        val fieldEntry: FieldEntry? = fields[info!!.number]
        require(
            !(info == null || fieldEntry == null)
        ) { "field=\"$field\" not found" }
        require(fieldEntry.vectorEncoding === expectedEncoding) {
            ("field=\""
                    + field
                    + "\" is encoded as: "
                    + fieldEntry.vectorEncoding
                    + " expected: "
                    + expectedEncoding)
        }
        return fieldEntry
    }

    @Throws(IOException::class)
    override fun search(field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits) {
        val fieldEntry = getFieldEntry(field, VectorEncoding.FLOAT32)
        search(
            fieldEntry,
            knnCollector,
            acceptDocs
        ) { flatVectorsReader.getRandomVectorScorer(field, target) }
    }

    @Throws(IOException::class)
    override fun search(field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits) {
        val fieldEntry = getFieldEntry(field, VectorEncoding.BYTE)
        search(
            fieldEntry,
            knnCollector,
            acceptDocs
        ) { flatVectorsReader.getRandomVectorScorer(field, target) }
    }

    @Throws(IOException::class)
    private fun search(
        fieldEntry: FieldEntry,
        knnCollector: KnnCollector,
        acceptDocs: Bits,
        scorerSupplier: IOSupplier<RandomVectorScorer>
    ) {
        if (fieldEntry.size == 0 || knnCollector.k() == 0) {
            return
        }
        val scorer: RandomVectorScorer = scorerSupplier.get()
        val collector: KnnCollector =
            OrdinalTranslatedKnnCollector(knnCollector) { ord -> scorer.ordToDoc(ord) }
        val acceptedOrds: Bits? = scorer.getAcceptOrds(acceptDocs)
        val graph: HnswGraph = getGraph(fieldEntry)
        var doHnsw = knnCollector.k() < scorer.maxOrd()
        // Take into account if quantized E.g. some scorer cost
        var filteredDocCount = 0
        // The approximate number of vectors that would be visited if we did not filter
        val unfilteredVisit = (ln(graph.size().toDouble()) * knnCollector.k()).toInt()
        if (acceptDocs is BitSet) {
            // Use approximate cardinality as this is good enough, but ensure we don't exceed the graph
            // size as that is illogical
            filteredDocCount = min(acceptDocs.approximateCardinality(), graph.size())
            if (unfilteredVisit >= filteredDocCount) {
                doHnsw = false
            }
        }
        if (doHnsw) {
            HnswGraphSearcher.search(
                scorer, collector, getGraph(fieldEntry), acceptedOrds, filteredDocCount
            )
        } else {
            // if k is larger than the number of vectors, we can just iterate over all vectors
            // and collect them
            for (i in 0..<scorer.maxOrd()) {
                if (acceptedOrds == null || acceptedOrds.get(i)) {
                    if (knnCollector.earlyTerminated()) {
                        break
                    }
                    knnCollector.incVisitedCount(1)
                    knnCollector.collect(scorer.ordToDoc(i), scorer.score(i))
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun getGraph(field: String): HnswGraph {
        val info: FieldInfo? = fieldInfos.fieldInfo(field)
        val entry: FieldEntry? = fields[info!!.number]
        require(
            !(info == null || entry == null)
        ) { "field=\"$field\" not found" }
        return if (entry.vectorIndexLength > 0) {
            getGraph(entry)
        } else {
            HnswGraph.EMPTY
        }
    }

    @Throws(IOException::class)
    private fun getGraph(entry: FieldEntry): HnswGraph {
        return OffHeapHnswGraph(entry, vectorIndex)
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(flatVectorsReader, vectorIndex)
    }

    @Throws(IOException::class)
    override fun getQuantizedVectorValues(field: String): QuantizedByteVectorValues? {
        if (flatVectorsReader is QuantizedVectorsReader) {
            return (flatVectorsReader as QuantizedVectorsReader).getQuantizedVectorValues(field)
        }
        return null
    }

    override fun getQuantizationState(field: String): ScalarQuantizer? {
        if (flatVectorsReader is QuantizedVectorsReader) {
            return (flatVectorsReader as QuantizedVectorsReader).getQuantizationState(field)
        }
        return null
    }

    private class FieldEntry(
        val similarityFunction: VectorSimilarityFunction,
        val vectorEncoding: VectorEncoding,
        val vectorIndexOffset: Long,
        val vectorIndexLength: Long,
        val M: Int,
        val numLevels: Int,
        val dimension: Int,
        val size: Int,
        val nodesByLevel: Array<IntArray?>,  // for each level the start offsets in vectorIndex file from where to read neighbours
        offsetsMeta: DirectMonotonicReader.Meta,
        offsetsOffset: Long,
        offsetsBlockShift: Int,
        offsetsLength: Long
    ) {
        val offsetsMeta: DirectMonotonicReader.Meta
        val offsetsOffset: Long
        val offsetsBlockShift: Int
        val offsetsLength: Long

        init {
            this.offsetsMeta = offsetsMeta
            this.offsetsOffset = offsetsOffset
            this.offsetsBlockShift = offsetsBlockShift
            this.offsetsLength = offsetsLength
        }

        companion object {
            @Throws(IOException::class)
            fun create(
                input: IndexInput,
                vectorEncoding: VectorEncoding,
                similarityFunction: VectorSimilarityFunction
            ): FieldEntry {
                val vectorIndexOffset: Long /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readVLong()
                val vectorIndexLength: Long /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readVLong()
                val dimension: Int /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readVInt()
                val size: Int /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readInt()
                // read nodes by level
                val M: Int /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readVInt()
                val numLevels: Int /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readVInt()
                val nodesByLevel = kotlin.arrayOfNulls<IntArray>(numLevels)
                var numberOfOffsets: Long = 0
                val offsetsOffset: Long
                val offsetsBlockShift: Int
                val offsetsMeta: DirectMonotonicReader.Meta?
                val offsetsLength: Long
                for (level in 0..<numLevels) {
                    if (level > 0) {
                        val numNodesOnLevel: Int = input.readVInt()
                        numberOfOffsets += numNodesOnLevel.toLong()
                        nodesByLevel[level] = IntArray(numNodesOnLevel)
                        nodesByLevel[level]!![0] = input.readVInt()
                        for (i in 1..<numNodesOnLevel) {
                            nodesByLevel[level]!![i] = nodesByLevel[level]!![i - 1] + input.readVInt()
                        }
                    } else {
                        numberOfOffsets += size
                    }
                }
                if (numberOfOffsets > 0) {
                    offsetsOffset = input.readLong()
                    offsetsBlockShift = input.readVInt()
                    offsetsMeta = DirectMonotonicReader.loadMeta(input, numberOfOffsets, offsetsBlockShift)
                    offsetsLength = input.readLong()
                } else {
                    offsetsOffset = 0
                    offsetsBlockShift = 0
                    offsetsMeta = null
                    offsetsLength = 0
                }
                return FieldEntry(
                    similarityFunction,
                    vectorEncoding,
                    vectorIndexOffset,
                    vectorIndexLength,
                    M,
                    numLevels,
                    dimension,
                    size,
                    nodesByLevel,
                    offsetsMeta!!,
                    offsetsOffset,
                    offsetsBlockShift,
                    offsetsLength
                )
            }
        }
    }

    /** Read the nearest-neighbors graph from the index input  */
    private class OffHeapHnswGraph(entry: FieldEntry, vectorIndex: IndexInput) : HnswGraph() {
        val dataIn: IndexInput = vectorIndex.slice("graph-data", entry.vectorIndexOffset, entry.vectorIndexLength)
        val nodesByLevel: Array<IntArray?> = entry.nodesByLevel
        val numLevels: Int = entry.numLevels
        val entryNode: Int = if (numLevels > 1) nodesByLevel[numLevels - 1]!![0] else 0
        val size: Int = entry.size
        var arcCount: Int = 0
        var arcUpTo: Int = 0
        var arc: Int = 0
        private val maxConn: Int
        private val graphLevelNodeOffsets: DirectMonotonicReader
        private val graphLevelNodeIndexOffsets: LongArray

        // Allocated to be M*2 to track the current neighbors being explored
        private val currentNeighborsBuffer: IntArray

        init {
            val addressesData: RandomAccessInput =
                vectorIndex.randomAccessSlice(entry.offsetsOffset, entry.offsetsLength)
            this.graphLevelNodeOffsets =
                DirectMonotonicReader.getInstance(entry.offsetsMeta, addressesData)
            this.currentNeighborsBuffer = IntArray(entry.M * 2)
            this.maxConn = entry.M
            graphLevelNodeIndexOffsets = LongArray(numLevels)
            graphLevelNodeIndexOffsets[0] = 0
            for (i in 1..<numLevels) {
                // nodesByLevel is `null` for the zeroth level as we know its size
                val nodeCount = if (nodesByLevel[i - 1] == null) size else nodesByLevel[i - 1]!!.size
                graphLevelNodeIndexOffsets[i] = graphLevelNodeIndexOffsets[i - 1] + nodeCount
            }
        }

        @Throws(IOException::class)
        override fun seek(level: Int, targetOrd: Int) {
            val targetIndex =
                if (level == 0)
                    targetOrd
                else
                    Arrays.binarySearch(nodesByLevel[level]!!, 0, nodesByLevel[level]!!.size, targetOrd)
            require(
                targetIndex >= 0
            ) { "seek level=$level target=$targetOrd not found: $targetIndex" }
            // unsafe; no bounds checking
            dataIn.seek(graphLevelNodeOffsets.get(targetIndex + graphLevelNodeIndexOffsets[level]))
            arcCount = dataIn.readVInt()
            require(arcCount <= currentNeighborsBuffer.size) { "too many neighbors: $arcCount" }
            if (arcCount > 0) {
                currentNeighborsBuffer[0] = dataIn.readVInt()
                for (i in 1..<arcCount) {
                    currentNeighborsBuffer[i] = currentNeighborsBuffer[i - 1] + dataIn.readVInt()
                }
            }
            arc = -1
            arcUpTo = 0
        }

        override fun size(): Int {
            return size
        }

        @Throws(IOException::class)
        override fun nextNeighbor(): Int {
            if (arcUpTo >= arcCount) {
                return NO_MORE_DOCS
            }
            arc = currentNeighborsBuffer[arcUpTo]
            ++arcUpTo
            return arc
        }

        override fun neighborCount(): Int {
            return arcCount
        }

        @Throws(IOException::class)
        override fun numLevels(): Int {
            return numLevels
        }

        override fun maxConn(): Int {
            return maxConn
        }

        @Throws(IOException::class)
        override fun entryNode(): Int {
            return entryNode
        }

        override fun getNodesOnLevel(level: Int): NodesIterator {
            return if (level == 0) {
                ArrayNodesIterator(size())
            } else {
                ArrayNodesIterator(nodesByLevel[level]!!, nodesByLevel[level]!!.size)
            }
        }
    }

    companion object {
        private val SHALLOW_SIZE: Long = RamUsageEstimator.shallowSizeOfInstance(Lucene99HnswVectorsFormat::class)

        @Throws(IOException::class)
        private fun openDataInput(
            state: SegmentReadState,
            versionMeta: Int,
            fileExtension: String,
            codecName: String,
            context: IOContext
        ): IndexInput {
            val fileName: String =
                IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, fileExtension)
            val `in`: IndexInput = state.directory.openInput(fileName, context)
            var success = false
            try {
                val versionVectorData: Int =
                    CodecUtil.checkIndexHeader(
                        `in`,
                        codecName,
                        Lucene99HnswVectorsFormat.VERSION_START,
                        Lucene99HnswVectorsFormat.VERSION_CURRENT,
                        state.segmentInfo.getId(),
                        state.segmentSuffix
                    )
                if (versionMeta != versionVectorData) {
                    throw CorruptIndexException(
                        ("Format versions mismatch: meta="
                                + versionMeta
                                + ", "
                                + codecName
                                + "="
                                + versionVectorData),
                        `in`
                    )
                }
                CodecUtil.retrieveChecksum(`in`)
                success = true
                return `in`
            } finally {
                if (!success) {
                    IOUtils.closeWhileHandlingException(`in`)
                }
            }
        }

        // List of vector similarity functions. This list is defined here, in order
        // to avoid an undesirable dependency on the declaration and order of values
        // in VectorSimilarityFunction. The list values and order must be identical
        // to that of {@link o.a.l.c.l.Lucene94FieldInfosFormat#SIMILARITY_FUNCTIONS}.
        val SIMILARITY_FUNCTIONS: MutableList<VectorSimilarityFunction> = mutableListOf<VectorSimilarityFunction>(
            VectorSimilarityFunction.EUCLIDEAN,
            VectorSimilarityFunction.DOT_PRODUCT,
            VectorSimilarityFunction.COSINE,
            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
        )

        @Throws(IOException::class)
        fun readSimilarityFunction(input: DataInput): VectorSimilarityFunction {
            val i: Int = input.readInt()
            require(!(i < 0 || i >= SIMILARITY_FUNCTIONS.size)) { "invalid distance function: $i" }
            return SIMILARITY_FUNCTIONS[i]
        }

        @Throws(IOException::class)
        fun readVectorEncoding(input: DataInput): VectorEncoding {
            val encodingId: Int = input.readInt()
            if (encodingId < 0 || encodingId >= VectorEncoding.entries.size) {
                throw CorruptIndexException("Invalid vector encoding id: $encodingId", input)
            }
            return VectorEncoding.entries[encodingId]
        }
    }
}
