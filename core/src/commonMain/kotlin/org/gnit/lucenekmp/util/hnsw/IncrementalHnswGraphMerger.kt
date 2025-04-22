package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.HnswGraphProvider
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.KnnVectorValues.DocIndexIterator
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.internal.hppc.IntIntHashMap
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.InfoStream
import kotlinx.io.IOException
import org.gnit.lucenekmp.index.VectorEncoding
import kotlin.math.max

/**
 * This selects the biggest Hnsw graph from the provided merge state and initializes a new
 * HnswGraphBuilder with that graph as a starting point.
 *
 * @lucene.experimental
 */
open class IncrementalHnswGraphMerger(
    protected val fieldInfo: FieldInfo,
    protected val scorerSupplier: RandomVectorScorerSupplier,
    protected val M: Int,
    protected val beamWidth: Int
) : HnswGraphMerger {

    protected var initReader: KnnVectorsReader? = null
    protected var initDocMap: MergeState.DocMap? = null
    protected var initGraphSize: Int = 0

    /**
     * Adds a reader to the graph merger if it meets the following criteria: 1. Does not contain any
     * deleted docs 2. Is a HnswGraphProvider 3. Has the most docs of any previous reader that met the
     * above criteria
     */
    @Throws(IOException::class)
    override fun addReader(
        reader: KnnVectorsReader, docMap: MergeState.DocMap, liveDocs: Bits
    ): IncrementalHnswGraphMerger {
        if (hasDeletes(liveDocs) || reader !is HnswGraphProvider) {
            return this
        }
        val graph: HnswGraph? = (reader as HnswGraphProvider).getGraph(fieldInfo.name)
        if (graph == null || graph.size() == 0) {
            return this
        }
        var candidateVectorCount: Int
        when (fieldInfo.getVectorEncoding()) {
            VectorEncoding.BYTE -> {
                val byteVectorValues: ByteVectorValues? = reader.getByteVectorValues(fieldInfo.name)
                if (byteVectorValues == null) {
                    return this
                }
                candidateVectorCount = byteVectorValues.size()
            }

            VectorEncoding.FLOAT32 -> {
                val vectorValues: FloatVectorValues? = reader.getFloatVectorValues(fieldInfo.name)
                if (vectorValues == null) {
                    return this
                }
                candidateVectorCount = vectorValues.size()
            }
        }
        if (candidateVectorCount > initGraphSize) {
            initReader = reader
            initDocMap = docMap
            initGraphSize = candidateVectorCount
        }
        return this
    }

    /**
     * Builds a new HnswGraphBuilder using the biggest graph from the merge state as a starting point.
     * If no valid readers were added to the merge state, a new graph is created.
     *
     * @param mergedVectorValues vector values in the merged segment
     * @param maxOrd max num of vectors that will be merged into the graph
     * @return HnswGraphBuilder
     * @throws IOException If an error occurs while reading from the merge state
     */
    @Throws(IOException::class)
    protected fun createBuilder(mergedVectorValues: KnnVectorValues, maxOrd: Int): HnswBuilder {
        if (initReader == null) {
            return HnswGraphBuilder.create(
                scorerSupplier, M, beamWidth, HnswGraphBuilder.randSeed, maxOrd
            )
        }

        val initializerGraph: HnswGraph? = (initReader as HnswGraphProvider).getGraph(fieldInfo.name)
        if (initializerGraph!!.size() == 0) {
            return HnswGraphBuilder.create(
                scorerSupplier, M, beamWidth, HnswGraphBuilder.randSeed, maxOrd
            )
        }

        val initializedNodes: BitSet = FixedBitSet(maxOrd)
        val oldToNewOrdinalMap = getNewOrdMapping(mergedVectorValues, initializedNodes)
        return InitializedHnswGraphBuilder.fromGraph(
            scorerSupplier,
            beamWidth,
            HnswGraphBuilder.randSeed,
            initializerGraph,
            oldToNewOrdinalMap,
            initializedNodes,
            maxOrd
        )
    }

    @Throws(IOException::class)
    override fun merge(
        mergedVectorValues: KnnVectorValues, infoStream: InfoStream, maxOrd: Int
    ): OnHeapHnswGraph {
        val builder = createBuilder(mergedVectorValues, maxOrd)
        builder.setInfoStream(infoStream)
        return builder.build(maxOrd)
    }

    /**
     * Creates a new mapping from old ordinals to new ordinals and returns the total number of vectors
     * in the newly merged segment.
     *
     * @param mergedVectorValues vector values in the merged segment
     * @param initializedNodes track what nodes have been initialized
     * @return the mapping from old ordinals to new ordinals
     * @throws IOException If an error occurs while reading from the merge state
     */
    @Throws(IOException::class)
    protected fun getNewOrdMapping(
        mergedVectorValues: KnnVectorValues, initializedNodes: BitSet
    ): IntArray {

        val initializerIterator = when (fieldInfo.getVectorEncoding()) {
            VectorEncoding.BYTE -> initReader!!.getByteVectorValues(fieldInfo.name)!!.iterator()
            VectorEncoding.FLOAT32 -> initReader!!.getFloatVectorValues(fieldInfo.name)!!.iterator()
        }

        val newIdToOldOrdinal = IntIntHashMap(initGraphSize)
        var maxNewDocID = -1
        var docId: Int = initializerIterator.nextDoc()
        while (docId != NO_MORE_DOCS
        ) {
            val newId: Int = initDocMap!!.get(docId)
            maxNewDocID = max(newId, maxNewDocID)
            newIdToOldOrdinal.put(newId, initializerIterator.index())
            docId = initializerIterator.nextDoc()
        }

        if (maxNewDocID == -1) {
            return IntArray(0)
        }
        val oldToNewOrdinalMap = IntArray(initGraphSize)
        val mergedVectorIterator: DocIndexIterator = mergedVectorValues.iterator()
        var newDocId: Int = mergedVectorIterator.nextDoc()
        while (newDocId <= maxNewDocID
        ) {
            val hashDocIndex = newIdToOldOrdinal.indexOf(newDocId)
            if (newIdToOldOrdinal.indexExists(hashDocIndex)) {
                val newOrd: Int = mergedVectorIterator.index()
                initializedNodes.set(newOrd)
                oldToNewOrdinalMap[newIdToOldOrdinal.indexGet(hashDocIndex)] = newOrd
            }
            newDocId = mergedVectorIterator.nextDoc()
        }
        return oldToNewOrdinalMap
    }

    companion object {
        private fun hasDeletes(liveDocs: Bits?): Boolean {
            if (liveDocs == null) {
                return false
            }

            for (i in 0..<liveDocs.length()) {
                if (!liveDocs.get(i)) {
                    return true
                }
            }
            return false
        }
    }
}