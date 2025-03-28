package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.IndexSorter.ComparableProvider
import org.gnit.lucenekmp.index.MergeState.DocMap
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.util.Version
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues

internal object MultiSorter {
    /**
     * Does a merge sort of the leaves of the incoming reader, returning [DocMap] to map each
     * leaf's documents into the merged segment. The documents for each incoming leaf reader must
     * already be sorted by the same sort! Returns null if the merge sort is not needed (segments are
     * already in index sort order).
     */
    @Throws(IOException::class)
    fun sort(sort: Sort, readers: MutableList<CodecReader>): Array<DocMap>? {
        // TODO: optimize if only 1 reader is incoming, though that's a rare case

        val fields: Array<SortField> = sort.sort
        val comparables: Array<Array<ComparableProvider>?> =
            kotlin.arrayOfNulls<Array<ComparableProvider>>(fields.size)
        val reverseMuls = IntArray(fields.size)
        for (i in fields.indices) {
            val sorter: IndexSorter = fields[i].getIndexSorter()!!
            requireNotNull(sorter) { "Cannot use sort field " + fields[i] + " for index sorting" }
            comparables[i] = sorter.getComparableProviders(readers)
            for (j in readers.indices) {
                val codecReader = readers.get(j)
                val fieldInfos: FieldInfos = codecReader.fieldInfos
                val metaData: LeafMetaData = codecReader.metaData
                if (metaData.hasBlocks && fieldInfos.parentField != null) {
                    val parentDocs = checkNotNull(
                        codecReader.getNumericDocValues(fieldInfos.parentField)
                    ) {
                        ("parent field: "
                                + fieldInfos.parentField
                                + " must be present if index sorting is used with blocks")
                    }
                    val parents: BitSet = BitSet.of(parentDocs, codecReader.maxDoc())
                    val providers: Array<ComparableProvider> = comparables[i]!!
                    val provider: ComparableProvider = providers[j]
                    providers[j] =
                        object : ComparableProvider {
                            override fun getAsComparableLong(docId: Int): Long {
                                return provider.getAsComparableLong(parents.nextSetBit(docId))
                            }
                        }
                }
                if (metaData.hasBlocks
                    && fieldInfos.parentField == null && metaData.createdVersionMajor >= Version.LUCENE_10_0_0.major
                ) {
                    throw CorruptIndexException(
                        "parent field is not set but the index has blocks and uses index sorting. indexCreatedVersionMajor: "
                                + metaData.createdVersionMajor,
                        "IndexingChain"
                    )
                }
            }
            reverseMuls[i] = if (fields[i].reverse) -1 else 1
        }
        val leafCount = readers.size

        val queue: PriorityQueue<LeafAndDocID?> =
            object : PriorityQueue<LeafAndDocID?>(leafCount) {
                public override fun lessThan(a: LeafAndDocID?, b: LeafAndDocID?): Boolean {
                    for (i in comparables.indices) {
                        val cmp: Int =
                            Long.compare(a!!.valuesAsComparableLongs[i], b!!.valuesAsComparableLongs[i])
                        if (cmp != 0) {
                            return reverseMuls[i] * cmp < 0
                        }
                    }

                    // tie-break by docID natural order:
                    if (a!!.readerIndex != b!!.readerIndex) {
                        return a.readerIndex < b.readerIndex
                    } else {
                        return a.docID < b.docID
                    }
                }
            }

        val builders: Array<PackedLongValues.Builder?> = kotlin.arrayOfNulls<PackedLongValues.Builder>(leafCount)

        for (i in 0..<leafCount) {
            val reader = readers.get(i)
            val leaf =
                LeafAndDocID(i, reader.liveDocs, reader.maxDoc(), comparables.size)
            for (j in comparables.indices) {
                leaf.valuesAsComparableLongs[j] = comparables[j]!![i].getAsComparableLong(leaf.docID)
            }
            queue.add(leaf)
            builders[i] = PackedLongValues.monotonicBuilder(PackedInts.COMPACT)
        }

        // merge sort:
        var mappedDocID = 0
        var lastReaderIndex = 0
        var isSorted = true
        while (queue.size() != 0) {
            val top: LeafAndDocID = queue.top()!!
            if (lastReaderIndex > top.readerIndex) {
                // merge sort is needed
                isSorted = false
            }
            lastReaderIndex = top.readerIndex
            builders[top.readerIndex]!!.add(mappedDocID.toLong())
            if (top.liveDocs == null || top.liveDocs.get(top.docID)) {
                mappedDocID++
            }
            top.docID++
            if (top.docID < top.maxDoc) {
                for (j in comparables.indices) {
                    top.valuesAsComparableLongs[j] =
                        comparables[j]!![top.readerIndex].getAsComparableLong(top.docID)
                }
                queue.updateTop()
            } else {
                queue.pop()
            }
        }
        if (isSorted) {
            return null
        }

        val docMaps: Array<DocMap?> = kotlin.arrayOfNulls<DocMap>(leafCount)
        for (i in 0..<leafCount) {
            val remapped: PackedLongValues = builders[i]!!.build()
            val liveDocs: Bits? = readers[i].liveDocs
            docMaps[i] =
                DocMap { docID ->
                    if (liveDocs == null || liveDocs.get(docID)) {
                        return@DocMap remapped.get(docID.toLong()).toInt()
                    } else {
                        return@DocMap -1
                    }
                }
        }

        return docMaps as Array<DocMap>
    }

    private class LeafAndDocID(val readerIndex: Int, liveDocs: Bits?, maxDoc: Int, numComparables: Int) {
        val liveDocs: Bits?
        val maxDoc: Int
        val valuesAsComparableLongs: LongArray
        var docID: Int = 0

        init {
            this.liveDocs = liveDocs
            this.maxDoc = maxDoc
            this.valuesAsComparableLongs = LongArray(numComparables)
        }
    }
}
