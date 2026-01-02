package org.gnit.lucenekmp.codecs

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.index.VectorEncoding.BYTE
import org.gnit.lucenekmp.index.VectorEncoding.FLOAT32
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.DocIDMerger
import org.gnit.lucenekmp.index.DocsWithFieldSet
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.KnnVectorValues.DocIndexIterator
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.internal.hppc.IntIntHashMap
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.IOFunction

/** Writes vectors to an index.  */
abstract class KnnVectorsWriter
/** Sole constructor  */
protected constructor() : Accountable, AutoCloseable {
    /** Add new field for indexing  */
    @Throws(IOException::class)
    abstract fun addField(fieldInfo: FieldInfo): KnnFieldVectorsWriter<*>

    /** Flush all buffered data on disk *  */
    @Throws(IOException::class)
    abstract fun flush(maxDoc: Int, sortMap: Sorter.DocMap?)

    /** Write field for merging  */
    @Throws(IOException::class)
    open fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
        when (fieldInfo.vectorEncoding) {
            BYTE -> {
                val byteWriter: KnnFieldVectorsWriter<ByteArray> =
                    addField(fieldInfo) as KnnFieldVectorsWriter<ByteArray>
                val mergedBytes: ByteVectorValues =
                    MergedVectorValues.mergeByteVectorValues(fieldInfo, mergeState)
                val iter: DocIndexIterator = mergedBytes.iterator()
                var doc: Int = iter.nextDoc()
                while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                    byteWriter.addValue(doc, mergedBytes.vectorValue(iter.index()))
                    doc = iter.nextDoc()
                }
            }

            FLOAT32 -> {
                val floatWriter: KnnFieldVectorsWriter<FloatArray> =
                    addField(fieldInfo) as KnnFieldVectorsWriter<FloatArray>
                val mergedFloats: FloatVectorValues =
                    MergedVectorValues.mergeFloatVectorValues(fieldInfo, mergeState)
                val iter: DocIndexIterator = mergedFloats.iterator()
                var doc: Int = iter.nextDoc()
                while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                    floatWriter.addValue(doc, mergedFloats.vectorValue(iter.index()))
                    doc = iter.nextDoc()
                }
            }
        }
    }

    /** Called once at the end before close  */
    @Throws(IOException::class)
    abstract fun finish()

    /**
     * Merges the segment vectors for all fields. This default implementation delegates to [ ][.mergeOneField], passing a [KnnVectorsReader] that combines the vector values and ignores
     * deleted documents.
     */
    @Throws(IOException::class)
    fun merge(mergeState: MergeState) {
        for (i in 0..<mergeState.fieldInfos.size) {
            val reader: KnnVectorsReader? = mergeState.knnVectorsReaders[i]
            require(reader != null || mergeState.fieldInfos[i]!!.hasVectorValues() == false)
            reader?.checkIntegrity()
        }

        for (fieldInfo in mergeState.mergeFieldInfos!!) {
            if (fieldInfo.hasVectorValues()) {
                if (mergeState.infoStream.isEnabled("VV")) {
                    mergeState.infoStream.message("VV", "merging " + mergeState.segmentInfo)
                }

                mergeOneField(fieldInfo, mergeState)

                if (mergeState.infoStream.isEnabled("VV")) {
                    mergeState.infoStream.message("VV", "merge done " + mergeState.segmentInfo)
                }
            }
        }
        finishMerge(mergeState)
        finish()
    }

    @Throws(IOException::class)
    private fun finishMerge(mergeState: MergeState) {
        for (reader in mergeState.knnVectorsReaders) {
            reader?.finishMerge()
        }
    }

    /** Tracks state of one sub-reader that we are merging  */
    internal class FloatVectorValuesSub(docMap: MergeState.DocMap, val values: FloatVectorValues) : DocIDMerger.Sub(docMap) {
        val iterator: DocIndexIterator = values.iterator()

        init {
            require(iterator.docID() == -1)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return iterator.nextDoc()
        }

        fun index(): Int {
            return iterator.index()
        }
    }

    class ByteVectorValuesSub(docMap: MergeState.DocMap, val values: ByteVectorValues) : DocIDMerger.Sub(docMap) {
        val iterator: DocIndexIterator = values.iterator()

        init {
            require(iterator.docID() == -1)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return iterator.nextDoc()
        }

        fun index(): Int {
            return iterator.index()
        }
    }

    /** View over multiple vector values supporting iterator-style access via DocIdMerger.  */
    object MergedVectorValues {
        private val logger = KotlinLogging.logger {}
        private fun validateFieldEncoding(fieldInfo: FieldInfo?, expected: VectorEncoding) {
            require(fieldInfo != null && fieldInfo.hasVectorValues())
            val fieldEncoding: VectorEncoding = fieldInfo.vectorEncoding
            if (fieldEncoding !== expected) {
                throw UnsupportedOperationException(
                    "Cannot merge vectors encoded as [$fieldEncoding] as $expected"
                )
            }
        }

        /**
         * Returns true if the fieldInfos has vector values for the field.
         *
         * @param fieldInfos fieldInfos for the segment
         * @param fieldName field name
         * @return true if the fieldInfos has vector values for the field.
         */
        fun hasVectorValues(fieldInfos: FieldInfos, fieldName: String): Boolean {
            if (!fieldInfos.hasVectorValues()) {
                return false
            }
            val info: FieldInfo? = fieldInfos.fieldInfo(fieldName)
            return info != null && info.hasVectorValues()
        }

        @Throws(IOException::class)
        private fun <V, S> mergeVectorValues(
            knnVectorsReaders: Array<KnnVectorsReader?>,
            docMaps: Array<MergeState.DocMap>?,
            mergingField: FieldInfo,
            sourceFieldInfos: Array<FieldInfos?>,
            valuesSupplier: IOFunction<KnnVectorsReader, V>,
            newSub: (MergeState.DocMap, V) -> S
        ): MutableList<S> {
            val subs: MutableList<S> = ArrayList()
            if (knnVectorsReaders.isEmpty() || docMaps == null) {
                return subs
            }

            for (i in knnVectorsReaders.indices) {
                val sourceFieldInfo = sourceFieldInfos[i] ?: continue
                if (hasVectorValues(sourceFieldInfo, mergingField.name) == false) {
                    continue
                }
                val knnVectorsReader = knnVectorsReaders[i] ?: continue
                val values = valuesSupplier.apply(knnVectorsReader)
                if (values != null) {
                    subs.add(newSub(docMaps[i], values))
                }
            }
            return subs
        }

        /** Returns a merged view over all the segment's [FloatVectorValues].  */
        @Throws(IOException::class)
        fun mergeFloatVectorValues(
            fieldInfo: FieldInfo, mergeState: MergeState
        ): FloatVectorValues {
            validateFieldEncoding(fieldInfo, FLOAT32)
            return MergedFloat32VectorValues(
                mergeVectorValues(
                    mergeState.knnVectorsReaders,
                    mergeState.docMaps,
                    fieldInfo,
                    mergeState.fieldInfos,
                    { knnVectorsReader -> knnVectorsReader.getFloatVectorValues(fieldInfo.name)!! }
                ) { docMap: MergeState.DocMap, values: FloatVectorValues ->
                    FloatVectorValuesSub(
                        docMap,
                        values
                    )
                },
                mergeState
            )
        }

        /** Returns a merged view over all the segment's [ByteVectorValues].  */
        @Throws(IOException::class)
        fun mergeByteVectorValues(fieldInfo: FieldInfo, mergeState: MergeState): ByteVectorValues {
            validateFieldEncoding(fieldInfo, BYTE)
            return MergedByteVectorValues(
                mergeVectorValues(
                    mergeState.knnVectorsReaders,
                    mergeState.docMaps,
                    fieldInfo,
                    mergeState.fieldInfos,
                    { knnVectorsReader -> knnVectorsReader.getByteVectorValues(fieldInfo.name)!! }
                ) { docMap: MergeState.DocMap, values: ByteVectorValues ->
                    ByteVectorValuesSub(
                        docMap,
                        values
                    )
                },
                mergeState
            )
        }

        internal class MergedFloat32VectorValues internal constructor(
            private val subs: MutableList<FloatVectorValuesSub>,
            mergeState: MergeState
        ) : FloatVectorValues() {
            private val docIdMerger: DocIDMerger<FloatVectorValuesSub> = DocIDMerger.of(subs, mergeState.needsIndexSort)
            private val size: Int
            private var docId = -1
            private var lastOrd = -1
            var current: FloatVectorValuesSub? = null
            private var lastValue: FloatArray? = null

            init {
                var totalSize = 0
                for (sub in subs) {
                    totalSize += sub.values.size()
                }
                size = totalSize
            }

            override fun iterator(): DocIndexIterator {
                return object : DocIndexIterator() {
                    private var index = -1

                    override fun docID(): Int {
                        return docId
                    }

                    override fun index(): Int {
                        return index
                    }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                current = docIdMerger.next()
                if (current == null) {
                    docId = NO_MORE_DOCS
                    index = NO_MORE_DOCS
                } else {
                    docId = current!!.mappedDocID
                    ++lastOrd
                    ++index
                    lastValue = null
                }
                return docId
            }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        throw UnsupportedOperationException()
                    }

                    override fun cost(): Long {
                        return size.toLong()
                    }
                }
            }

            @Throws(IOException::class)
            override fun vectorValue(ord: Int): FloatArray {
                check(ord == lastOrd) {
                    ("only supports forward iteration with a single iterator: ord="
                            + ord
                            + ", lastOrd="
                            + lastOrd)
                }
                if (current == null) {
                    throw IllegalStateException("vectorValue called with null current at ord=$ord")
                }
                val value = current!!.values.vectorValue(current!!.index())
                val dim = dimension()
                if (value.size != dim) {
                    throw IllegalStateException(
                        "unexpected vector dimension=${value.size} expected=$dim"
                    )
                }
                if (current!!.iterator.docID() != docId) {
                    logger.debug {
                        "doc mismatch: iteratorDoc=${current!!.iterator.docID()} mappedDoc=$docId ord=$ord"
                    }
                }
                var sum = 0.0
                for (i in 0 until dim) {
                    sum += value[i] * value[i]
                }
                if (sum == 0.0) {
                    logger.debug {
                        "zero vector encountered in merged values at ord=$ord docId=$docId " +
                            "subIndex=${current!!.index()} subDoc=${current!!.iterator.docID()} " +
                            "subMaxDoc=${current!!.values.size()} dim=$dim value=${value.contentToString()}"
                    }
                }
                return value
            }

            override fun size(): Int {
                return size
            }

            override fun dimension(): Int {
                return subs[0].values.dimension()
            }

            override fun ordToDoc(ord: Int): Int {
                throw UnsupportedOperationException()
            }

            override fun scorer(target: FloatArray): VectorScorer {
                throw UnsupportedOperationException()
            }

            override fun copy(): FloatVectorValues {
                throw UnsupportedOperationException()
            }
        }

        class MergedByteVectorValues internal constructor(
            private val subs: MutableList<ByteVectorValuesSub>,
            mergeState: MergeState
        ) : ByteVectorValues() {
            private val docIdMerger: DocIDMerger<ByteVectorValuesSub> = DocIDMerger.of(subs, mergeState.needsIndexSort)
            private val size: Int

            private var lastOrd = -1
            private var docId = -1
            var current: ByteVectorValuesSub? = null

            init {
                var totalSize = 0
                for (sub in subs) {
                    totalSize += sub.values.size()
                }
                size = totalSize
            }

            @Throws(IOException::class)
            override fun vectorValue(ord: Int): ByteArray {
                check(ord == lastOrd + 1) { "only supports forward iteration: ord=$ord, lastOrd=$lastOrd" }
                lastOrd = ord
                return current!!.values.vectorValue(current!!.index())
            }

            override fun iterator(): DocIndexIterator {
                return object : DocIndexIterator() {
                    private var index = -1

                    override fun docID(): Int {
                        return docId
                    }

                    override fun index(): Int {
                        return index
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        current = docIdMerger.next()
                        if (current == null) {
                            docId = NO_MORE_DOCS
                            index = NO_MORE_DOCS
                        } else {
                            docId = current!!.mappedDocID
                            ++index
                        }
                        return docId
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        throw UnsupportedOperationException()
                    }

                    override fun cost(): Long {
                        return size.toLong()
                    }
                }
            }

            override fun size(): Int {
                return size
            }

            override fun dimension(): Int {
                return subs[0].values.dimension()
            }

            override fun ordToDoc(ord: Int): Int {
                throw UnsupportedOperationException()
            }

            override fun scorer(target: ByteArray): VectorScorer {
                throw UnsupportedOperationException()
            }

            override fun copy(): ByteVectorValues {
                throw UnsupportedOperationException()
            }
        }
    }

    companion object {
        /**
         * Given old doc ids and an id mapping, maps old ordinal to new ordinal. Note: this method return
         * nothing and output are written to parameters
         *
         * @param oldDocIds the old or current document ordinals. Must not be null.
         * @param sortMap the document sorting map for how to make the new ordinals. Must not be null.
         * @param old2NewOrd int[] maps from old ord to new ord
         * @param new2OldOrd int[] maps from new ord to old ord
         * @param newDocsWithField set of new doc ids which has the value
         */
        @Throws(IOException::class)
        fun mapOldOrdToNewOrd(
            oldDocIds: DocsWithFieldSet,
            sortMap: Sorter.DocMap,
            old2NewOrd: IntArray?,
            new2OldOrd: IntArray?,
            newDocsWithField: DocsWithFieldSet?
        ) {
            // TODO: a similar function exists in IncrementalHnswGraphMerger#getNewOrdMapping
            //       maybe we can do a further refactoring
            requireNotNull(oldDocIds)
            requireNotNull(sortMap)
            require(old2NewOrd != null || new2OldOrd != null || newDocsWithField != null)
            require(old2NewOrd == null || old2NewOrd.size == oldDocIds.cardinality())
            require(new2OldOrd == null || new2OldOrd.size == oldDocIds.cardinality())
            val newIdToOldOrd = IntIntHashMap()
            val iterator: DocIdSetIterator = oldDocIds.iterator()
            val newDocIds = IntArray(oldDocIds.cardinality())
            var oldOrd = 0
            var oldDocId: Int = iterator.nextDoc()
            while (oldDocId != DocIdSetIterator.NO_MORE_DOCS
            ) {
                val newId: Int = sortMap.oldToNew(oldDocId)
                newIdToOldOrd.put(newId, oldOrd)
                newDocIds[oldOrd] = newId
                oldOrd++
                oldDocId = iterator.nextDoc()
            }

            Arrays.sort(newDocIds)
            var newOrd = 0
            for (newDocId in newDocIds) {
                val currOldOrd: Int = newIdToOldOrd.get(newDocId)
                if (old2NewOrd != null) {
                    old2NewOrd[currOldOrd] = newOrd
                }
                if (new2OldOrd != null) {
                    new2OldOrd[newOrd] = currOldOrd
                }
                if (newDocsWithField != null) {
                    newDocsWithField.add(newDocId)
                }
                newOrd++
            }
        }
    }
}
