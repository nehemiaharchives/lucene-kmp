package org.gnit.lucenekmp.codecs.lucene95


import org.gnit.lucenekmp.codecs.lucene90.IndexedDISI
import org.gnit.lucenekmp.index.DocsWithFieldSet
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.util.packed.DirectMonotonicReader
import org.gnit.lucenekmp.util.packed.DirectMonotonicWriter
import okio.IOException

/**
 * Configuration for [DirectMonotonicReader] and [IndexedDISI] for reading sparse
 * vectors. The format in the static writing methods adheres to the Lucene95HnswVectorsFormat
 */
class OrdToDocDISIReaderConfiguration internal constructor(
    val size: Int,
// the following four variables used to read docIds encoded by IndexDISI
    // special values of docsWithFieldOffset are -1 and -2
    // -1 : dense
    // -2 : empty
    // other: sparse
    val jumpTableEntryCount: Short,
    // the following four variables used to read ordToDoc encoded by DirectMonotonicWriter
    // note that only spare case needs to store ordToDoc
    val addressesOffset: Long,
    val addressesLength: Long,
    val docsWithFieldOffset: Long,
    val docsWithFieldLength: Long,
    val denseRankPower: Byte,
    meta: DirectMonotonicReader.Meta?
) {
    val meta: DirectMonotonicReader.Meta?

    init {
        this.meta = meta
    }

    /**
     * @param dataIn the dataIn
     * @return the IndexedDISI for sparse values
     * @throws IOException thrown when reading data fails
     */
    @Throws(IOException::class)
    fun getIndexedDISI(dataIn: IndexInput): IndexedDISI {
        require(docsWithFieldOffset > -1)
        return IndexedDISI(
            dataIn,
            docsWithFieldOffset,
            docsWithFieldLength,
            jumpTableEntryCount.toInt(),
            denseRankPower,
            size.toLong()
        )
    }

    /**
     * @param dataIn the dataIn
     * @return the DirectMonotonicReader for sparse values
     * @throws IOException thrown when reading data fails
     */
    @Throws(IOException::class)
    fun getDirectMonotonicReader(dataIn: IndexInput): DirectMonotonicReader {
        require(docsWithFieldOffset > -1)
        val addressesData: RandomAccessInput =
            dataIn.randomAccessSlice(addressesOffset, addressesLength)
        return DirectMonotonicReader.getInstance(meta!!, addressesData)
    }

    val isEmpty: Boolean
        /**
         * @return If true, the field is empty, no vector values. If false, the field is either dense or
         * sparse.
         */
        get() = docsWithFieldOffset == -2L

    val isDense: Boolean
        /**
         * @return If true, the field is dense, all documents have values for a field. If false, the field
         * is sparse, some documents missing values.
         */
        get() = docsWithFieldOffset == -1L

    companion object {
        /**
         * Writes out the docsWithField and ordToDoc mapping to the outputMeta and vectorData
         * respectively. This is in adherence to the Lucene95HnswVectorsFormat.
         *
         *
         * Within outputMeta the format is as follows:
         *
         *
         *  * **[int8]** if equals to -2, empty - no vector values. If equals to -1, dense – all
         * documents have values for a field. If equals to 0, sparse – some documents missing
         * values.
         *  * DocIds were encoded by [IndexedDISI.writeBitSet]
         *  * OrdToDoc was encoded by [org.apache.lucene.util.packed.DirectMonotonicWriter], note
         * that only in sparse case
         *
         *
         *
         * Within the vectorData the format is as follows:
         *
         *
         *  * DocIds encoded by [IndexedDISI.writeBitSet],
         * note that only in sparse case
         *  * OrdToDoc was encoded by [org.apache.lucene.util.packed.DirectMonotonicWriter], note
         * that only in sparse case
         *
         *
         * @param outputMeta the outputMeta
         * @param vectorData the vectorData
         * @param count the count of docs with vectors
         * @param maxDoc the maxDoc for the index
         * @param docsWithField the docs contaiting a vector field
         * @throws IOException thrown when writing data fails to either output
         */
        @Throws(IOException::class)
        fun writeStoredMeta(
            directMonotonicBlockShift: Int,
            outputMeta: IndexOutput,
            vectorData: IndexOutput,
            count: Int,
            maxDoc: Int,
            docsWithField: DocsWithFieldSet
        ) {
            if (count == 0) {
                outputMeta.writeLong(-2) // docsWithFieldOffset
                outputMeta.writeLong(0L) // docsWithFieldLength
                outputMeta.writeShort(-1) // jumpTableEntryCount
                outputMeta.writeByte(-1) // denseRankPower
            } else if (count == maxDoc) {
                outputMeta.writeLong(-1) // docsWithFieldOffset
                outputMeta.writeLong(0L) // docsWithFieldLength
                outputMeta.writeShort(-1) // jumpTableEntryCount
                outputMeta.writeByte(-1) // denseRankPower
            } else {
                val offset: Long = vectorData.filePointer
                outputMeta.writeLong(offset) // docsWithFieldOffset
                val jumpTableEntryCount: Short =
                    IndexedDISI.writeBitSet(
                        docsWithField.iterator(), vectorData, IndexedDISI.DEFAULT_DENSE_RANK_POWER
                    )
                outputMeta.writeLong(vectorData.filePointer - offset) // docsWithFieldLength
                outputMeta.writeShort(jumpTableEntryCount)
                outputMeta.writeByte(IndexedDISI.DEFAULT_DENSE_RANK_POWER)

                // write ordToDoc mapping
                val start: Long = vectorData.filePointer
                outputMeta.writeLong(start)
                outputMeta.writeVInt(directMonotonicBlockShift)
                // dense case and empty case do not need to store ordToMap mapping
                val ordToDocWriter: DirectMonotonicWriter =
                    DirectMonotonicWriter.getInstance(
                        outputMeta, vectorData, count.toLong(), directMonotonicBlockShift
                    )
                val iterator: DocIdSetIterator = docsWithField.iterator()
                var doc: Int = iterator.nextDoc()
                while (doc != DocIdSetIterator.NO_MORE_DOCS
                ) {
                    ordToDocWriter.add(doc.toLong())
                    doc = iterator.nextDoc()
                }
                ordToDocWriter.finish()
                outputMeta.writeLong(vectorData.filePointer - start)
            }
        }

        /**
         * Reads in the necessary fields stored in the outputMeta to configure [ ] and [IndexedDISI].
         *
         * @param inputMeta the inputMeta, previously written to via [.writeStoredMeta]
         * @param size The number of vectors
         * @return the configuration required to read sparse vectors
         * @throws IOException thrown when reading data fails
         */
        @Throws(IOException::class)
        fun fromStoredMeta(inputMeta: IndexInput, size: Int): OrdToDocDISIReaderConfiguration {
            val docsWithFieldOffset: Long = inputMeta.readLong()
            val docsWithFieldLength: Long = inputMeta.readLong()
            val jumpTableEntryCount: Short = inputMeta.readShort()
            val denseRankPower: Byte = inputMeta.readByte()
            var addressesOffset: Long = 0
            var blockShift: Int
            var meta: DirectMonotonicReader.Meta? = null
            var addressesLength: Long = 0
            if (docsWithFieldOffset > -1) {
                addressesOffset = inputMeta.readLong()
                blockShift = inputMeta.readVInt()
                meta = DirectMonotonicReader.loadMeta(inputMeta, size.toLong(), blockShift)
                addressesLength = inputMeta.readLong()
            }
            return OrdToDocDISIReaderConfiguration(
                size,
                jumpTableEntryCount,
                addressesOffset,
                addressesLength,
                docsWithFieldOffset,
                docsWithFieldLength,
                denseRankPower,
                meta
            )
        }
    }
}
