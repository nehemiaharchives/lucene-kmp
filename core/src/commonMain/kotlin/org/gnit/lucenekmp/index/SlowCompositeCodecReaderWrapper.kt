package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.index.MultiDocValues.MultiSortedDocValues
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.Version

/**k
 * A merged [CodecReader] view of multiple [CodecReader]. This view is primarily
 * targeted at merging, not searching.
 */
internal class SlowCompositeCodecReaderWrapper private constructor(codecReaders: MutableList<CodecReader>) :
    CodecReader() {
    private val meta: LeafMetaData
    private val codecReaders: Array<CodecReader> = codecReaders.toTypedArray()
    private val docStarts: IntArray = IntArray(codecReaders.size + 1)
    override val fieldInfos: FieldInfos
    override val liveDocs: Bits?

    private fun docIdToReaderId(doc: Int): Int {
        Objects.checkIndex(doc, docStarts[docStarts.size - 1])
        var readerId: Int = Arrays.binarySearch(docStarts, doc)
        if (readerId < 0) {
            readerId = -2 - readerId
        }
        return readerId
    }

    override val fieldsReader: StoredFieldsReader
        get() {
            val readers: Array<StoredFieldsReader> =
                codecReaders.map { it.fieldsReader!! }.toTypedArray()
            return SlowCompositeStoredFieldsReaderWrapper(readers, docStarts)
        }

    // Remap FieldInfos to make sure consumers only see field infos from the composite reader, not
    // from individual leaves
    private fun remap(info: FieldInfo): FieldInfo? {
        return fieldInfos.fieldInfo(info.name)
    }

    private inner class SlowCompositeStoredFieldsReaderWrapper(
        private val readers: Array<StoredFieldsReader>,
        private val docStarts: IntArray
    ) : StoredFieldsReader() {

        @Throws(IOException::class)
        override fun close() {
            IOUtils.close(*readers)
        }

        override fun clone(): StoredFieldsReader {
            return SlowCompositeStoredFieldsReaderWrapper(
                readers.map { it.clone() }.toTypedArray(),
                docStarts
            )
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (reader in readers) {
                if (reader != null) {
                    reader.checkIntegrity()
                }
            }
        }

        @Throws(IOException::class)
        override fun prefetch(docID: Int) {
            val readerId = docIdToReaderId(docID)
            readers[readerId].prefetch(docID - docStarts[readerId])
        }

        @Throws(IOException::class)
        override fun document(docID: Int, visitor: StoredFieldVisitor) {
            val readerId = docIdToReaderId(docID)
            readers[readerId].document(
                docID - docStarts[readerId],
                object : StoredFieldVisitor() {
                    @Throws(IOException::class)
                    override fun needsField(fieldInfo: FieldInfo): Status? {
                        return visitor.needsField(remap(fieldInfo)!!)
                    }

                    @Throws(IOException::class)
                    override fun binaryField(fieldInfo: FieldInfo, value: ByteArray) {
                        visitor.binaryField(remap(fieldInfo)!!, value)
                    }

                    @Throws(IOException::class)
                    override fun stringField(fieldInfo: FieldInfo, value: String) {
                        visitor.stringField(remap(fieldInfo)!!, value)
                    }

                    @Throws(IOException::class)
                    override fun intField(fieldInfo: FieldInfo, value: Int) {
                        visitor.intField(remap(fieldInfo)!!, value)
                    }

                    @Throws(IOException::class)
                    override fun longField(fieldInfo: FieldInfo, value: Long) {
                        visitor.longField(remap(fieldInfo)!!, value)
                    }

                    @Throws(IOException::class)
                    override fun floatField(fieldInfo: FieldInfo, value: Float) {
                        visitor.floatField(remap(fieldInfo)!!, value)
                    }

                    @Throws(IOException::class)
                    override fun doubleField(fieldInfo: FieldInfo, value: Double) {
                        visitor.doubleField(remap(fieldInfo)!!, value)
                    }
                })
        }
    }

    override val termVectorsReader: TermVectorsReader
        get() {
            val readers: Array<TermVectorsReader> =
                codecReaders.map { it.termVectorsReader!! }.toTypedArray()
            return SlowCompositeTermVectorsReaderWrapper(readers, docStarts)
        }

    private inner class SlowCompositeTermVectorsReaderWrapper(
        private val readers: Array<TermVectorsReader>,
        private val docStarts: IntArray
    ) : TermVectorsReader() {

        @Throws(IOException::class)
        override fun close() {
            IOUtils.close(*readers)
        }

        override fun clone(): TermVectorsReader {
            return SlowCompositeTermVectorsReaderWrapper(
                readers.map { it.clone() }.toTypedArray(),
                docStarts
            )
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (reader in readers) {
                if (reader != null) {
                    reader.checkIntegrity()
                }
            }
        }

        @Throws(IOException::class)
        override fun prefetch(doc: Int) {
            val readerId = docIdToReaderId(doc)
            val reader: TermVectorsReader = readers[readerId]
            if (reader != null) {
                reader.prefetch(doc - docStarts[readerId])
            }
        }

        @Throws(IOException::class)
        override fun get(doc: Int): Fields? {
            val readerId = docIdToReaderId(doc)
            val reader: TermVectorsReader = readers[readerId]
            if (reader == null) {
                return null
            }
            return reader.get(doc - docStarts[readerId])
        }
    }

    override val normsReader: NormsProducer
        get() = SlowCompositeNormsProducer(codecReaders)

    private class SlowCompositeNormsProducer(private val codecReaders: Array<CodecReader>) :
        NormsProducer() {
        private val producers: Array<NormsProducer> = codecReaders.map { it.normsReader!! }.toTypedArray()

        @Throws(IOException::class)
        override fun close() {
            IOUtils.close(*producers)
        }

        @Throws(IOException::class)
        override fun getNorms(field: FieldInfo): NumericDocValues {
            return MultiDocValues.getNormValues(
                MultiReader(*codecReaders),
                field.name
            )!!
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (producer in producers) {
                if (producer != null) {
                    producer.checkIntegrity()
                }
            }
        }
    }

    private data class DocValuesSub<T : KnnVectorValues>(
        val sub: T?,
        val docStart: Int,
        val ordStart: Int
    ) {
        @Throws(IOException::class)
        fun copy(): DocValuesSub<T> {
            return DocValuesSub((sub?.copy()) as T?, docStart, ordStart)
        }
    }

    private class MergedDocIterator<T : KnnVectorValues>
        (subs: MutableList<DocValuesSub<T>>) : KnnVectorValues.DocIndexIterator() {
        val it: MutableIterator<DocValuesSub<T>> = subs.iterator()
        var current: DocValuesSub<T>
        var currentIterator: KnnVectorValues.DocIndexIterator
        var ord: Int = -1
        var doc: Int = -1

        init {
            current = it.next()
            currentIterator = currentIterator()!!
        }

        override fun docID(): Int {
            return doc
        }

        override fun index(): Int {
            return ord
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            while (true) {
                if (current.sub != null) {
                    val next: Int = currentIterator.nextDoc()
                    if (next != NO_MORE_DOCS) {
                        ++ord
                        return (current.docStart + next).also { doc = it }
                    }
                }
                if (!it.hasNext()) {
                    ord = NO_MORE_DOCS
                    return NO_MORE_DOCS.also { doc = it }
                }
                current = it.next()
                currentIterator = currentIterator()!!
                ord = current.ordStart - 1
            }
        }

        fun currentIterator(): KnnVectorValues.DocIndexIterator? {
            return if (current.sub != null) {
                current.sub!!.iterator()
            } else {
                null
            }
        }

        override fun cost(): Long {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }
    }

    override val docValuesReader: DocValuesProducer
        get() = SlowCompositeDocValuesProducerWrapper(codecReaders, docStarts)

    private class SlowCompositeDocValuesProducerWrapper(
        private val codecReaders: Array<CodecReader>,
        private val docStarts: IntArray
    ) : DocValuesProducer() {
        private val producers: Array<DocValuesProducer> = codecReaders.map { it.docValuesReader!! }.toTypedArray()
        private val cachedOrdMaps: MutableMap<String, OrdinalMap> = mutableMapOf()

        @Throws(IOException::class)
        override fun close() {
            IOUtils.close(*producers)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (producer in producers) {
                if (producer != null) {
                    producer.checkIntegrity()
                }
            }
        }

        @Throws(IOException::class)
        override fun getNumeric(field: FieldInfo): NumericDocValues {
            return MultiDocValues.getNumericValues(
                MultiReader(*codecReaders),
                field.name
            )!!
        }

        @Throws(IOException::class)
        override fun getBinary(field: FieldInfo): BinaryDocValues {
            return MultiDocValues.getBinaryValues(
                MultiReader(*codecReaders),
                field.name
            )!!
        }

        @Throws(IOException::class)
        override fun getSorted(field: FieldInfo): SortedDocValues {
            var map: OrdinalMap?
            // TODO in kmp, Synchronized can not be used, need to think what to do here
            //synchronized(cachedOrdMaps) {
            map = cachedOrdMaps[field.name]
            if (map == null) {
                // uncached, or not a multi dv
                val dv: SortedDocValues =
                    MultiDocValues.getSortedValues(
                        MultiReader(*codecReaders),
                        field.name
                    )!!
                if (dv is MultiSortedDocValues) {
                    map = dv.mapping
                    cachedOrdMaps.put(field.name, map)
                }
                return dv
            }
            //}
            val size = codecReaders.size
            val values: Array<SortedDocValues?> =
                kotlin.arrayOfNulls(size)
            var totalCost: Long = 0
            for (i in 0..<size) {
                val reader: LeafReader = codecReaders[i]
                var v: SortedDocValues? = reader.getSortedDocValues(field.name)
                if (v == null) {
                    v = DocValues.emptySorted()
                }
                values[i] = v
                totalCost += v.cost()
            }
            return MultiSortedDocValues(values as Array<SortedDocValues>, docStarts, map, totalCost)
        }

        @Throws(IOException::class)
        override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
            return MultiDocValues.getSortedNumericValues(
                MultiReader(*codecReaders),
                field.name
            )!!
        }

        @Throws(IOException::class)
        override fun getSortedSet(field: FieldInfo): SortedSetDocValues {
            var map: OrdinalMap?

            // TODO in kmp, Synchronized can not be used, need to think what to do here
            //synchronized(cachedOrdMaps) {
            map = cachedOrdMaps[field.name]
            if (map == null) {
                // uncached, or not a multi dv
                val dv: SortedSetDocValues =
                    MultiDocValues.getSortedSetValues(
                        MultiReader(*codecReaders),
                        field.name
                    )!!
                if (dv is MultiDocValues.MultiSortedSetDocValues) {
                    map = dv.mapping
                    cachedOrdMaps.put(field.name, map)
                }
                return dv
            }
            //}

            checkNotNull(map)
            val size = codecReaders.size
            val values: Array<SortedSetDocValues?> =
                kotlin.arrayOfNulls(size)
            var totalCost: Long = 0
            for (i in 0..<size) {
                val reader: LeafReader = codecReaders[i]
                var v: SortedSetDocValues? = reader.getSortedSetDocValues(field.name)
                if (v == null) {
                    v = DocValues.emptySortedSet()
                }
                values[i] = v
                totalCost += v.cost()
            }
            return MultiDocValues.MultiSortedSetDocValues(
                values as Array<SortedSetDocValues>,
                docStarts,
                map,
                totalCost
            )
        }

        @Throws(IOException::class)
        override fun getSkipper(field: FieldInfo): DocValuesSkipper {
            throw UnsupportedOperationException("This method is for searching not for merging")
        }
    }

    override val postingsReader: FieldsProducer
        get() {
            val producers: Array<FieldsProducer> = codecReaders.map { it.postingsReader!! }.toTypedArray()
            return SlowCompositeFieldsProducerWrapper(producers, docStarts)
        }

    private class SlowCompositeFieldsProducerWrapper(
        private val producers: Array<FieldsProducer>,
        docStarts: IntArray
    ) : FieldsProducer() {
        private val fields: MultiFields

        init {
            val subs: MutableList<Fields> = mutableListOf()
            val slices: MutableList<ReaderSlice> = mutableListOf()
            var i = 0
            for (producer in producers) {
                if (producer != null) {
                    subs.add(producer)
                    slices.add(ReaderSlice(docStarts[i], docStarts[i + 1], i))
                }
                i++
            }
            fields = MultiFields(subs.toTypedArray(), slices.toTypedArray())
        }

        override fun close() {
            IOUtils.close(*producers)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (producer in producers) {
                if (producer != null) {
                    producer.checkIntegrity()
                }
            }
        }

        override fun iterator(): MutableIterator<String> {
            return fields.iterator()
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            return fields.terms(field)
        }

        override fun size(): Int {
            return fields.size()
        }
    }

    override val pointsReader: PointsReader
        get() = SlowCompositePointsReaderWrapper(codecReaders, docStarts)

    private class PointValuesSub(val sub: PointValues, val docBase: Int)

    private class SlowCompositePointsReaderWrapper(
        private val codecReaders: Array<CodecReader>,
        private val docStarts: IntArray
    ) : PointsReader() {
        private val readers: Array<PointsReader> = codecReaders.map { it.pointsReader!! }.toTypedArray()

        @Throws(IOException::class)
        override fun close() {
            IOUtils.close(*readers)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (reader in readers) {
                if (reader != null) {
                    reader.checkIntegrity()
                }
            }
        }

        @Throws(IOException::class)
        override fun getValues(field: String): PointValues? {
            val values: MutableList<PointValuesSub> = mutableListOf()
            for (i in readers.indices) {
                val fi: FieldInfo? = codecReaders[i].fieldInfos.fieldInfo(field)
                if (fi != null && fi.pointDimensionCount > 0) {
                    val v: PointValues? = readers[i].getValues(field)
                    if (v != null) {
                        // Apparently FieldInfo can claim a field has points, yet the returned
                        // PointValues is null
                        values.add(PointValuesSub(v, docStarts[i]))
                    }
                }
            }
            if (values.isEmpty()) {
                return null
            }
            return object : PointValues() {
                override val pointTree: PointTree
                    get() = object : PointTree {
                        override fun clone(): PointTree {
                            return this
                        }

                        @Throws(IOException::class)
                        override fun visitDocValues(visitor: IntersectVisitor) {
                            for (sub in values) {
                                sub.sub.pointTree.visitDocValues(wrapIntersectVisitor(visitor, sub.docBase))
                            }
                        }

                        @Throws(IOException::class)
                        override fun visitDocIDs(visitor: IntersectVisitor) {
                            for (sub in values) {
                                sub.sub.pointTree.visitDocIDs(wrapIntersectVisitor(visitor, sub.docBase))
                            }
                        }

                        fun wrapIntersectVisitor(
                            visitor: IntersectVisitor,
                            docStart: Int
                        ): IntersectVisitor {
                            return object : IntersectVisitor {
                                @Throws(IOException::class)
                                override fun visit(docID: Int, packedValue: ByteArray) {
                                    visitor.visit(docStart + docID, packedValue)
                                }

                                @Throws(IOException::class)
                                override fun visit(docID: Int) {
                                    visitor.visit(docStart + docID)
                                }

                                override fun compare(
                                    minPackedValue: ByteArray,
                                    maxPackedValue: ByteArray
                                ): Relation {
                                    return visitor.compare(minPackedValue, maxPackedValue)
                                }
                            }
                        }

                        override fun size(): Long {
                            var size: Long = 0
                            for (sub in values) {
                                size += sub.sub.size()
                            }
                            return size
                        }

                        @Throws(IOException::class)
                        override fun moveToSibling(): Boolean {
                            return false
                        }

                        @Throws(IOException::class)
                        override fun moveToParent(): Boolean {
                            return false
                        }

                        @Throws(IOException::class)
                        override fun moveToChild(): Boolean {
                            return false
                        }

                        override val minPackedValue: ByteArray
                            get() {
                                try {
                                    var minPackedValue: ByteArray? = null
                                    for (sub in values) {
                                        if (minPackedValue == null) {
                                            minPackedValue = sub.sub.minPackedValue.copyOf()
                                        } else {
                                            val leafMinPackedValue: ByteArray = sub.sub.minPackedValue
                                            val numIndexDimensions: Int = sub.sub.numIndexDimensions
                                            val numBytesPerDimension: Int = sub.sub.bytesPerDimension
                                            val comparator: ArrayUtil.Companion.ByteArrayComparator =
                                                ArrayUtil.getUnsignedComparator(
                                                    numBytesPerDimension
                                                )
                                            for (i in 0..<numIndexDimensions) {
                                                if (comparator.compare(
                                                        leafMinPackedValue,
                                                        i * numBytesPerDimension,
                                                        minPackedValue,
                                                        i * numBytesPerDimension
                                                    )
                                                    < 0
                                                ) {
                                                    System.arraycopy(
                                                        leafMinPackedValue,
                                                        i * numBytesPerDimension,
                                                        minPackedValue,
                                                        i * numBytesPerDimension,
                                                        numBytesPerDimension
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    return minPackedValue!!
                                } catch (e: IOException) {
                                    throw UncheckedIOException(e)
                                }
                            }

                        override val maxPackedValue: ByteArray
                            get() {
                                try {
                                    var maxPackedValue: ByteArray? = null
                                    for (sub in values) {
                                        if (maxPackedValue == null) {
                                            maxPackedValue = sub.sub.maxPackedValue.copyOf()
                                        } else {
                                            val leafMinPackedValue: ByteArray = sub.sub.maxPackedValue
                                            val numIndexDimensions: Int = sub.sub.numIndexDimensions
                                            val numBytesPerDimension: Int = sub.sub.bytesPerDimension
                                            val comparator: ArrayUtil.Companion.ByteArrayComparator =
                                                ArrayUtil.getUnsignedComparator(
                                                    numBytesPerDimension
                                                )
                                            for (i in 0..<numIndexDimensions) {
                                                if (comparator.compare(
                                                        leafMinPackedValue,
                                                        i * numBytesPerDimension,
                                                        maxPackedValue,
                                                        i * numBytesPerDimension
                                                    )
                                                    > 0
                                                ) {
                                                    System.arraycopy(
                                                        leafMinPackedValue,
                                                        i * numBytesPerDimension,
                                                        maxPackedValue,
                                                        i * numBytesPerDimension,
                                                        numBytesPerDimension
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    return maxPackedValue!!
                                } catch (e: IOException) {
                                    throw UncheckedIOException(e)
                                }
                            }
                    }

                override val minPackedValue: ByteArray
                    get() = this.pointTree.minPackedValue

                override val maxPackedValue: ByteArray
                    get() = this.pointTree.maxPackedValue

                override val numDimensions: Int
                    get() = values[0].sub.numDimensions

                override val numIndexDimensions: Int
                    get() = values[0].sub.numIndexDimensions

                override val bytesPerDimension: Int
                    get() = values[0].sub.bytesPerDimension

                override fun size(): Long {
                    try {
                        return this.pointTree.size()
                    } catch (e: IOException) {
                        throw UncheckedIOException(e)
                    }
                }

                override val docCount: Int
                    get() {
                        var docCount = 0
                        for (sub in values) {
                            docCount += sub.sub.docCount
                        }
                        return docCount
                    }
            }
        }
    }

    override val vectorReader: KnnVectorsReader
        get() = SlowCompositeKnnVectorsReaderWrapper(codecReaders, docStarts)

    private class SlowCompositeKnnVectorsReaderWrapper(
        private val codecReaders: Array<CodecReader>,
        private val docStarts: IntArray
    ) : KnnVectorsReader() {
        private val readers: Array<KnnVectorsReader> = codecReaders.map { it.vectorReader!! }.toTypedArray()

        @Throws(IOException::class)
        override fun close() {
            IOUtils.close(*readers)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (reader in readers) {
                if (reader != null) {
                    reader.checkIntegrity()
                }
            }
        }

        @Throws(IOException::class)
        override fun getFloatVectorValues(field: String): FloatVectorValues {
            val subs: MutableList<DocValuesSub<FloatVectorValues>> = mutableListOf()
            var i = 0
            var dimension = -1
            var size = 0
            for (reader in codecReaders) {
                val values: FloatVectorValues? = reader.getFloatVectorValues(field)
                subs.add(DocValuesSub(values, docStarts[i], size))
                if (values != null) {
                    if (dimension == -1) {
                        dimension = values.dimension()
                    }
                    size += values.size()
                }
                i++
            }
            return MergedFloatVectorValues(dimension, size, subs)
        }

        inner class MergedFloatVectorValues(
            val dimension: Int,
            val size: Int,
            val subs: MutableList<DocValuesSub<FloatVectorValues>>
        ) : FloatVectorValues() {
            val iter: MergedDocIterator<FloatVectorValues> = MergedDocIterator(subs)
            // [0, start(1), ..., size] - we want the extra element
            // to avoid checking for out-of-array bounds
            val starts: IntArray = IntArray(subs.size + 1)
            var lastSubIndex: Int = 0

            init {
                for (i in subs.indices) {
                    starts[i] = subs[i].ordStart
                }
                starts[starts.size - 1] = size
            }

            override fun iterator(): MergedDocIterator<FloatVectorValues> {
                return iter
            }

            override fun dimension(): Int {
                return dimension
            }

            override fun size(): Int {
                return size
            }

            @Throws(IOException::class)
            override fun copy(): FloatVectorValues {
                val subsCopy: MutableList<DocValuesSub<FloatVectorValues>> = mutableListOf()
                for (sub in subs) {
                    subsCopy.add(sub.copy())
                }
                return MergedFloatVectorValues(dimension, size, subsCopy)
            }

            @Throws(IOException::class)
            override fun vectorValue(ord: Int): FloatArray {
                assert(ord >= 0 && ord < size)
                // We need to implement fully random-access API here in order to support callers like
                // SortingCodecReader that rely on it.
                lastSubIndex = findSub(ord, lastSubIndex, starts)
                val sub: DocValuesSub<FloatVectorValues> = subs[lastSubIndex]
                checkNotNull(sub.sub)
                return (sub.sub).vectorValue(ord - sub.ordStart)
            }
        }

        @Throws(IOException::class)
        override fun getByteVectorValues(field: String): ByteVectorValues {
            val subs: MutableList<DocValuesSub<ByteVectorValues>> = mutableListOf()
            var i = 0
            var dimension = -1
            var size = 0
            for (reader in codecReaders) {
                val values: ByteVectorValues? = reader.getByteVectorValues(field)
                subs.add(DocValuesSub(values, docStarts[i], size))
                if (values != null) {
                    if (dimension == -1) {
                        dimension = values.dimension()
                    }
                    size += values.size()
                }
                i++
            }
            return MergedByteVectorValues(
                dimension,
                size,
                subs
            )
        }

        class MergedByteVectorValues(
            val dimension: Int,
            val size: Int,
            val subs: MutableList<DocValuesSub<ByteVectorValues>>
        ) : ByteVectorValues() {
            val iter: MergedDocIterator<ByteVectorValues> = MergedDocIterator(subs)
            // [0, start(1), ..., size] - we want the extra element
            // to avoid checking for out-of-array bounds
            val starts: IntArray = IntArray(subs.size + 1)
            var lastSubIndex: Int = 0

            init {
                for (i in subs.indices) {
                    starts[i] = subs[i].ordStart
                }
                starts[starts.size - 1] = size
            }

            override fun iterator(): MergedDocIterator<ByteVectorValues> {
                return iter
            }

            override fun dimension(): Int {
                return dimension
            }

            override fun size(): Int {
                return size
            }

            @Throws(IOException::class)
            override fun vectorValue(ord: Int): ByteArray {
                assert(ord >= 0 && ord < size)
                // We need to implement fully random-access API here in order to support callers like
                // SortingCodecReader that rely on it.  We maintain lastSubIndex since we expect some
                // repetition.
                lastSubIndex = findSub(ord, lastSubIndex, starts)
                val sub: DocValuesSub<ByteVectorValues> = subs[lastSubIndex]
                return sub.sub!!.vectorValue(ord - sub.ordStart)
            }

            @Throws(IOException::class)
            override fun copy(): ByteVectorValues {
                val newSubs: MutableList<DocValuesSub<ByteVectorValues>> = mutableListOf()
                for (sub in subs) {
                    newSubs.add(sub.copy())
                }
                return MergedByteVectorValues(
                    dimension,
                    size,
                    newSubs
                )
            }
        }

        @Throws(IOException::class)
        override fun search(
            field: String,
            target: FloatArray,
            knnCollector: KnnCollector,
            acceptDocs: Bits
        ) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun search(
            field: String,
            target: ByteArray,
            knnCollector: KnnCollector,
            acceptDocs: Bits
        ) {
            throw UnsupportedOperationException()
        }

        companion object {
            private fun findSub(ord: Int, lastSubIndex: Int, starts: IntArray): Int {
                if (ord >= starts[lastSubIndex]) {
                    if (ord >= starts[lastSubIndex + 1]) {
                        return binarySearchStarts(starts, ord, lastSubIndex + 1, starts.size)
                    }
                } else {
                    return binarySearchStarts(starts, ord, 0, lastSubIndex)
                }
                return lastSubIndex
            }

            private fun binarySearchStarts(starts: IntArray, ord: Int, from: Int, to: Int): Int {
                var pos = Arrays.binarySearch(starts, from, to, ord)
                if (pos < 0) {
                    // subtract one since binarySearch returns an *insertion point*
                    return -2 - pos
                } else {
                    while (pos < starts.size - 1 && starts[pos + 1] == ord) {
                        // Arrays.binarySearch can return any of a sequence of repeated value
                        // but we always want the last one
                        ++pos
                    }
                    return pos
                }
            }
        }
    }

    override val coreCacheHelper: CacheHelper
        get() = /*null*/ throw UnsupportedOperationException("This method is for searching not for merging")

    /*override fun getFieldInfos(): FieldInfos {
        return fieldInfos
    }*/

    /*override fun getLiveDocs(): Bits {
        return liveDocs
    }*/

    override val metaData: LeafMetaData
        get() = meta

    var numDocs: Int = -1

    init {
        var i = 0
        var docStart = 0
        for (reader in codecReaders) {
            i++
            docStart += reader.maxDoc()
            docStarts[i] = docStart
        }
        var majorVersion = -1
        var minVersion: Version? = null
        var hasBlocks = false
        for (reader in codecReaders) {
            val readerMeta: LeafMetaData = reader.metaData
            if (majorVersion == -1) {
                majorVersion = readerMeta.createdVersionMajor
            } else require(majorVersion == readerMeta.createdVersionMajor) { "Cannot combine leaf readers created with different major versions" }
            if (minVersion == null) {
                minVersion = readerMeta.minVersion
            } else if (minVersion.onOrAfter(readerMeta.minVersion!!)) {
                minVersion = readerMeta.minVersion
            }
            hasBlocks = hasBlocks or readerMeta.hasBlocks
        }
        meta = LeafMetaData(majorVersion, minVersion, null, hasBlocks)
        val multiReader = MultiReader(*codecReaders.toTypedArray())
        fieldInfos = FieldInfos.getMergedFieldInfos(multiReader)
        liveDocs = MultiBits.getLiveDocs(multiReader)
    }

    // TODO in kmp, Synchronized can not be used, need to think what to do here
    /*@Synchronized*/
    override fun numDocs(): Int {
        // Compute the number of docs lazily, in case some leaves need to recompute it the first time it
        // is called, see BaseCompositeReader#numDocs.
        if (numDocs == -1) {
            numDocs = 0
            for (reader in codecReaders) {
                numDocs += reader.numDocs()
            }
        }
        return numDocs
    }

    override fun maxDoc(): Int {
        return docStarts[docStarts.size - 1]
    }

    override val readerCacheHelper: CacheHelper
        get() = /*null*/ throw UnsupportedOperationException("This method is for searching not for merging")

    companion object {
        @Throws(IOException::class)
        fun wrap(readers: MutableList<CodecReader>): CodecReader {
            return when (readers.size) {
                0 -> throw IllegalArgumentException("Must take at least one reader, got 0")
                1 -> readers[0]
                else -> SlowCompositeCodecReaderWrapper(readers)
            }
        }
    }
}
