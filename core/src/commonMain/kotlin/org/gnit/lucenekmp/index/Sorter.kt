package org.gnit.lucenekmp.index


import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.TimSorter
import org.gnit.lucenekmp.util.Version
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues

/**
 * Sorts documents of a given index by returning a permutation on the document IDs.
 *
 * @lucene.experimental
 */
class Sorter internal constructor(sort: Sort) {
    val sort: Sort

    /** Creates a new Sorter to sort the index with `sort`  */
    init {
        require(!sort.needsScores()) { "Cannot sort an index with a Sort that refers to the relevance score" }
        this.sort = sort
    }

    /**
     * A permutation of doc IDs. For every document ID between `0` and [ ][IndexReader.maxDoc], `oldToNew(newToOld(docID))` must return `docID`.
     */
    abstract class DocMap
    /** Sole constructor.  */
    protected constructor() {
        /** Given a doc ID from the original index, return its ordinal in the sorted index.  */
        abstract fun oldToNew(docID: Int): Int

        /** Given the ordinal of a doc ID, return its doc ID in the original index.  */
        abstract fun newToOld(docID: Int): Int

        /**
         * Return the number of documents in this map. This must be equal to the [ ][org.gnit.lucenekmp.index.LeafReader.maxDoc] of the [ ] which is sorted.
         */
        abstract fun size(): Int
    }

    private class DocValueSorter(private val docs: IntArray, private val comparator: IndexSorter.DocComparator) :
        TimSorter(docs.size / 64) {
        private val tmp: IntArray = IntArray(docs.size / 64)

        override fun compare(i: Int, j: Int): Int {
            return comparator.compare(docs[i], docs[j])
        }

        override fun swap(i: Int, j: Int) {
            val tmpDoc = docs[i]
            docs[i] = docs[j]
            docs[j] = tmpDoc
        }

        override fun copy(src: Int, dest: Int) {
            docs[dest] = docs[src]
        }

        override fun save(i: Int, len: Int) {
            /*java.lang.System.arraycopy(docs, i, tmp, 0, len)*/
            docs.copyInto(
                destination = tmp,
                destinationOffset = 0,
                startIndex = i,
                endIndex = i + len
            )
        }

        override fun restore(i: Int, j: Int) {
            docs[j] = tmp[i]
        }

        override fun compareSaved(i: Int, j: Int): Int {
            return comparator.compare(tmp[i], docs[j])
        }
    }

    /**
     * Returns a mapping from the old document ID to its new location in the sorted index.
     * Implementations can use the auxiliary [.sort] to compute
     * the old-to-new permutation given a list of documents and their corresponding values.
     *
     *
     * A return value of `null` is allowed and means that `reader` is already
     * sorted.
     *
     *
     * **NOTE:** deleted documents are expected to appear in the mapping as well, they will
     * however be marked as deleted in the sorted view.
     */
    @Throws(IOException::class)
    fun sort(reader: LeafReader): DocMap? {
        val fields: Array<SortField> = sort.sort
        val comparators = arrayOfNulls<IndexSorter.DocComparator>(fields.size)

        var comparatorWrapper: (IndexSorter.DocComparator) -> IndexSorter.DocComparator = { it }
        val metaData: LeafMetaData = reader.metaData
        val fieldInfos: FieldInfos = reader.fieldInfos

        if (metaData.hasBlocks && fieldInfos.parentField != null) {
            val parents: BitSet =
                BitSet.of(reader.getNumericDocValues(fieldInfos.parentField), reader.maxDoc())
            comparatorWrapper = { input ->
                object : IndexSorter.DocComparator {
                    override fun compare(docID1: Int, docID2: Int): Int {
                        return input.compare(
                            parents.nextSetBit(docID1),
                            parents.nextSetBit(docID2)
                        )
                    }
                }
            }
        }

        if (metaData.hasBlocks
            && fieldInfos.parentField == null && metaData.createdVersionMajor >= Version.LUCENE_10_0_0.major
        ) {
            throw CorruptIndexException(
                "parent field is not set but the index has blocks. indexCreatedVersionMajor: "
                        + metaData.createdVersionMajor,
                "Sorter"
            )
        }

        for (i in fields.indices) {
            val sorter: IndexSorter? = fields[i].getIndexSorter()
            requireNotNull(sorter) { "Cannot use sortfield + " + fields[i] + " to sort indexes" }
            comparators[i] = comparatorWrapper(sorter.getDocComparator(reader, reader.maxDoc()))
        }

        @Suppress("UNCHECKED_CAST")
        return sort(reader.maxDoc(), comparators as Array<IndexSorter.DocComparator>)
    }

    @Throws(IOException::class)
    fun sort(maxDoc: Int, comparators: Array<IndexSorter.DocComparator>): DocMap? {
        val comparator: IndexSorter.DocComparator = object : IndexSorter.DocComparator {
            override fun compare(docID1: Int, docID2: Int): Int {
                for (i in comparators.indices) {
                    val comp = comparators[i].compare(docID1, docID2)
                    if (comp != 0) {
                        return comp
                    }
                }
                return Int.compare(docID1, docID2) // docid order tiebreak
            }
        }

        return sort(maxDoc, comparator)
    }

    val iD: String
        /**
         * Returns the identifier of this [Sorter].
         *
         *
         * This identifier is similar to [Object.hashCode] and should be chosen so that two
         * instances of this class that sort documents likewise will have the same identifier. On the
         * contrary, this identifier should be different on different [sorts][Sort].
         */
        get() = sort.toString()

    override fun toString(): String {
        return this.iD
    }

    companion object {
        /** Check consistency of a [DocMap], useful for assertions.  */
        fun isConsistent(docMap: DocMap): Boolean {
            val maxDoc = docMap.size()
            for (i in 0..<maxDoc) {
                val newID = docMap.oldToNew(i)
                val oldID = docMap.newToOld(newID)
                require(newID >= 0 && newID < maxDoc) { "doc IDs must be in [0-$maxDoc[, got $newID" }
                require(
                    i == oldID
                ) { "mapping is inconsistent: $i --oldToNew--> $newID --newToOld--> $oldID" }
                if (i != oldID || newID < 0 || newID >= maxDoc) {
                    return false
                }
            }
            return true
        }

        /** Computes the old-to-new permutation over the given comparator.  */
        private fun sort(maxDoc: Int, comparator: IndexSorter.DocComparator): DocMap? {
            // check if the index is sorted
            var sorted = true
            for (i in 1..<maxDoc) {
                if (comparator.compare(i - 1, i) > 0) {
                    sorted = false
                    break
                }
            }
            if (sorted) {
                return null
            }

            // sort doc IDs
            val docs = IntArray(maxDoc)
            for (i in 0..<maxDoc) {
                docs[i] = i
            }

            val sorter = DocValueSorter(docs, comparator)
            // It can be common to sort a reader, add docs, sort it again, ... and in
            // that case timSort can save a lot of time
            sorter.sort(0, docs.size) // docs is now the newToOld mapping

            // The reason why we use MonotonicAppendingLongBuffer here is that it
            // wastes very little memory if the index is in random order but can save
            // a lot of memory if the index is already "almost" sorted
            val newToOldBuilder: PackedLongValues.Builder =
                PackedLongValues.monotonicBuilder(PackedInts.COMPACT)
            for (i in 0..<maxDoc) {
                newToOldBuilder.add(docs[i].toLong())
            }
            val newToOld: PackedLongValues = newToOldBuilder.build()

            // invert the docs mapping:
            for (i in 0..<maxDoc) {
                docs[newToOld.get(i.toLong()).toInt()] = i
            } // docs is now the oldToNew mapping


            val oldToNewBuilder: PackedLongValues.Builder =
                PackedLongValues.monotonicBuilder(PackedInts.COMPACT)
            for (i in 0..<maxDoc) {
                oldToNewBuilder.add(docs[i].toLong())
            }
            val oldToNew: PackedLongValues = oldToNewBuilder.build()

            return object : DocMap() {
                override fun oldToNew(docID: Int): Int {
                    return oldToNew.get(docID.toLong()).toInt()
                }

                override fun newToOld(docID: Int): Int {
                    return newToOld.get(docID.toLong()).toInt()
                }

                override fun size(): Int {
                    return maxDoc
                }
            }
        }
    }
}
