package org.gnit.lucenekmp.codecs.lucene90

import kotlinx.io.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat.Companion.DIRECT_MONOTONIC_BLOCK_SHIFT
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat.Companion.NUMERIC_BLOCK_SHIFT
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat.Companion.NUMERIC_BLOCK_SIZE
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat.Companion.SKIP_INDEX_LEVEL_SHIFT
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat.Companion.SKIP_INDEX_MAX_LEVEL
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.EmptyDocValuesProducer
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.internal.hppc.LongHashSet
import org.gnit.lucenekmp.internal.hppc.LongIntHashMap
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.SortedSetSelector
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.ByteBuffersIndexOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.LongsRef
import org.gnit.lucenekmp.util.MathUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.compress.LZ4
import org.gnit.lucenekmp.util.compress.LZ4.FastCompressionHashTable
import org.gnit.lucenekmp.util.packed.DirectMonotonicWriter
import org.gnit.lucenekmp.util.packed.DirectWriter
import kotlin.math.max
import kotlin.math.min

/** writer for [Lucene90DocValuesFormat]  */
internal class Lucene90DocValuesConsumer(
    state: SegmentWriteState,
    skipIndexIntervalSize: Int,
    dataCodec: String,
    dataExtension: String,
    metaCodec: String,
    metaExtension: String
) : DocValuesConsumer() {
    var data: IndexOutput? = null
    var meta: IndexOutput? = null
    var maxDoc: Int = 0
    private var termsDictBuffer: ByteArray
    private var skipIndexIntervalSize = 0

    /** expert: Creates a new writer  */
    init {
        this.termsDictBuffer = ByteArray(1 shl 14)
        var success = false
        try {
            val dataName: String =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name, state.segmentSuffix, dataExtension
                )
            data = state.directory.createOutput(dataName, state.context)
            CodecUtil.writeIndexHeader(
                data!!,
                dataCodec,
                Lucene90DocValuesFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            val metaName: String =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name, state.segmentSuffix, metaExtension
                )
            meta = state.directory.createOutput(metaName, state.context)
            CodecUtil.writeIndexHeader(
                meta!!,
                metaCodec,
                Lucene90DocValuesFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            maxDoc = state.segmentInfo.maxDoc()
            this.skipIndexIntervalSize = skipIndexIntervalSize
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        var success = false
        try {
            if (meta != null) {
                meta!!.writeInt(-1) // write EOF marker
                CodecUtil.writeFooter(meta!!) // write checksum
            }
            if (data != null) {
                CodecUtil.writeFooter(data!!) // write checksum
            }
            success = true
        } finally {
            if (success) {
                IOUtils.close(data!!, meta!!)
            } else {
                IOUtils.closeWhileHandlingException(data!!, meta!!)
            }
            data = null
            meta = data
        }
    }

    @Throws(IOException::class)
    override fun addNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        meta?.writeInt(field.number)
        meta?.writeByte(Lucene90DocValuesFormat.NUMERIC)
        val producer: DocValuesProducer =
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
                    return DocValues.singleton(valuesProducer.getNumeric(field))
                }
            }
        if (field.docValuesSkipIndexType() !== DocValuesSkipIndexType.NONE) {
            writeSkipIndex(field, producer)
        }
        writeValues(field, producer, false)
    }

    private class MinMaxTracker {
        var min: Long = 0
        var max: Long = 0
        var numValues: Long = 0
        var spaceInBits: Long

        init {
            reset()
            spaceInBits = 0
        }

        fun reset() {
            min = Long.Companion.MAX_VALUE
            max = Long.Companion.MIN_VALUE
            numValues = 0
        }

        /** Accumulate a new value.  */
        fun update(v: Long) {
            min = min(min, v)
            max = max(max, v)
            ++numValues
        }

        /** Accumulate state from another tracker.  */
        fun update(other: MinMaxTracker) {
            min = min(min, other.min)
            max = max(max, other.max)
            numValues += other.numValues
        }

        /** Update the required space.  */
        fun finish() {
            if (max > min) {
                spaceInBits += DirectWriter.unsignedBitsRequired(max - min) * numValues
            }
        }

        /** Update space usage and get ready for accumulating values for the next block.  */
        fun nextBlock() {
            finish()
            reset()
        }
    }

    private class SkipAccumulator(var minDocID: Int) {
        var maxDocID: Int = 0
        var docCount: Int = 0
        var minValue: Long
        var maxValue: Long

        init {
            minValue = Long.Companion.MAX_VALUE
            maxValue = Long.Companion.MIN_VALUE
        }

        fun isDone(skipIndexIntervalSize: Int, valueCount: Int, nextValue: Long, nextDoc: Int): Boolean {
            if (docCount < skipIndexIntervalSize) {
                return false
            }
            // Once we reach the interval size, we will keep accepting documents if
            // - next doc value is not a multi-value
            // - current accumulator only contains a single value and next value is the same value
            // - the accumulator is dense and the next doc keeps the density (no gaps)
            return valueCount > 1 || minValue != maxValue || minValue != nextValue || docCount != nextDoc - minDocID
        }

        fun accumulate(value: Long) {
            minValue = min(minValue, value)
            maxValue = max(maxValue, value)
        }

        fun accumulate(other: SkipAccumulator) {
            require(minDocID <= other.minDocID && maxDocID < other.maxDocID)
            maxDocID = other.maxDocID
            minValue = min(minValue, other.minValue)
            maxValue = max(maxValue, other.maxValue)
            docCount += other.docCount
        }

        fun nextDoc(docID: Int) {
            maxDocID = docID
            ++docCount
        }

        companion object {
            fun merge(list: MutableList<SkipAccumulator>, index: Int, length: Int): SkipAccumulator {
                val acc = SkipAccumulator(list[index].minDocID)
                for (i in 0..<length) {
                    acc.accumulate(list[index + i])
                }
                return acc
            }
        }
    }

    @Throws(IOException::class)
    private fun writeSkipIndex(field: FieldInfo, valuesProducer: DocValuesProducer) {
        require(field.docValuesSkipIndexType() !== DocValuesSkipIndexType.NONE)
        val start: Long = data!!.filePointer
        val values: SortedNumericDocValues = valuesProducer.getSortedNumeric(field)
        var globalMaxValue = Long.Companion.MIN_VALUE
        var globalMinValue = Long.Companion.MAX_VALUE
        var globalDocCount = 0
        var maxDocId = -1
        val accumulators: MutableList<SkipAccumulator> = ArrayList()
        var accumulator: SkipAccumulator? = null
        val maxAccumulators = 1 shl (SKIP_INDEX_LEVEL_SHIFT * (SKIP_INDEX_MAX_LEVEL - 1))
        var doc: Int = values.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            val firstValue: Long = values.nextValue()
            if (accumulator != null
                && accumulator.isDone(skipIndexIntervalSize, values.docValueCount(), firstValue, doc)
            ) {
                globalMaxValue = max(globalMaxValue, accumulator.maxValue)
                globalMinValue = min(globalMinValue, accumulator.minValue)
                globalDocCount += accumulator.docCount
                maxDocId = accumulator.maxDocID
                accumulator = null
                if (accumulators.size == maxAccumulators) {
                    writeLevels(accumulators)
                    accumulators.clear()
                }
            }
            if (accumulator == null) {
                accumulator = SkipAccumulator(doc)
                accumulators.add(accumulator)
            }
            accumulator.nextDoc(doc)
            accumulator.accumulate(firstValue)
            var i = 1
            val end: Int = values.docValueCount()
            while (i < end) {
                accumulator.accumulate(values.nextValue())
                ++i
            }
            doc = values.nextDoc()
        }

        if (!accumulators.isEmpty()) {
            globalMaxValue = max(globalMaxValue, accumulator!!.maxValue)
            globalMinValue = min(globalMinValue, accumulator.minValue)
            globalDocCount += accumulator.docCount
            maxDocId = accumulator.maxDocID
            writeLevels(accumulators)
        }
        meta?.writeLong(start) // record the start in meta
        meta?.writeLong(data!!.filePointer - start) // record the length
        require(globalDocCount == 0 || globalMaxValue >= globalMinValue)
        meta?.writeLong(globalMaxValue)
        meta?.writeLong(globalMinValue)
        require(globalDocCount <= maxDocId + 1)
        meta?.writeInt(globalDocCount)
        meta?.writeInt(maxDocId)
    }

    @Throws(IOException::class)
    private fun writeLevels(accumulators: MutableList<SkipAccumulator>) {
        val accumulatorsLevels: MutableList<MutableList<SkipAccumulator>> =
            ArrayList(SKIP_INDEX_MAX_LEVEL)
        accumulatorsLevels.add(accumulators)
        for (i in 0..<SKIP_INDEX_MAX_LEVEL - 1) {
            accumulatorsLevels.add(buildLevel(accumulatorsLevels[i]))
        }
        val totalAccumulators = accumulators.size
        for (index in 0..<totalAccumulators) {
            // compute how many levels we need to write for the current accumulator
            val levels = getLevels(index, totalAccumulators)
            // write the number of levels
            data?.writeByte(levels.toByte())
            // write intervals in reverse order. This is done so we don't
            // need to read all of them in case of slipping
            for (level in levels - 1 downTo 0) {
                val accumulator =
                    accumulatorsLevels[level][index shr (SKIP_INDEX_LEVEL_SHIFT * level)]
                data?.writeInt(accumulator.maxDocID)
                data?.writeInt(accumulator.minDocID)
                data?.writeLong(accumulator.maxValue)
                data?.writeLong(accumulator.minValue)
                data?.writeInt(accumulator.docCount)
            }
        }
    }

    @Throws(IOException::class)
    private fun writeValues(field: FieldInfo, valuesProducer: DocValuesProducer, ords: Boolean): LongArray {
        var values: SortedNumericDocValues = valuesProducer.getSortedNumeric(field)
        val firstValue: Long = if (values.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            values.nextValue()
        } else {
            0L
        }
        values = valuesProducer.getSortedNumeric(field)
        var numDocsWithValue = 0
        val minMax = MinMaxTracker()
        val blockMinMax = MinMaxTracker()
        var gcd: Long = 0
        var uniqueValues: LongHashSet? = if (ords) null else LongHashSet()
        var doc: Int = values.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            var i = 0
            val count: Int = values.docValueCount()
            while (i < count) {
                val v: Long = values.nextValue()

                if (gcd != 1L) {
                    gcd = if (v < Long.Companion.MIN_VALUE / 2 || v > Long.Companion.MAX_VALUE / 2) {
                        // in that case v - minValue might overflow and make the GCD computation return
                        // wrong results. Since these extreme values are unlikely, we just discard
                        // GCD computation for them
                        1
                    } else {
                        MathUtil.gcd(gcd, v - firstValue)
                    }
                }

                blockMinMax.update(v)
                if (blockMinMax.numValues == NUMERIC_BLOCK_SIZE.toLong()) {
                    minMax.update(blockMinMax)
                    blockMinMax.nextBlock()
                }

                if (uniqueValues != null && uniqueValues.add(v) && uniqueValues.size() > 256) {
                    uniqueValues = null
                }
                ++i
            }

            numDocsWithValue++
            doc = values.nextDoc()
        }

        minMax.update(blockMinMax)
        minMax.finish()
        blockMinMax.finish()

        if (ords && minMax.numValues > 0) {
            check(minMax.min == 0L) { "The min value for ordinals should always be 0, got " + minMax.min }
            check(!(minMax.max != 0L && gcd != 1L)) { "GCD compression should never be used on ordinals, found gcd=$gcd" }
        }

        val numValues = minMax.numValues
        var min = minMax.min
        val max = minMax.max
        require(blockMinMax.spaceInBits <= minMax.spaceInBits)

        if (numDocsWithValue == 0) { // meta[-2, 0]: No documents with values
            meta?.writeLong(-2) // docsWithFieldOffset
            meta?.writeLong(0L) // docsWithFieldLength
            meta?.writeShort(-1) // jumpTableEntryCount
            meta?.writeByte(-1) // denseRankPower
        } else if (numDocsWithValue == maxDoc) { // meta[-1, 0]: All documents has values
            meta?.writeLong(-1) // docsWithFieldOffset
            meta?.writeLong(0L) // docsWithFieldLength
            meta?.writeShort(-1) // jumpTableEntryCount
            meta?.writeByte(-1) // denseRankPower
        } else { // meta[data.offset, data.length]: IndexedDISI structure for documents with values
            val offset: Long = data!!.filePointer
            meta?.writeLong(offset) // docsWithFieldOffset
            values = valuesProducer.getSortedNumeric(field)
            val jumpTableEntryCount: Short =
                IndexedDISI.writeBitSet(values, data!!, IndexedDISI.DEFAULT_DENSE_RANK_POWER)
            meta?.writeLong(data!!.filePointer - offset) // docsWithFieldLength
            meta?.writeShort(jumpTableEntryCount)
            meta?.writeByte(IndexedDISI.DEFAULT_DENSE_RANK_POWER)
        }

        meta?.writeLong(numValues)
        val numBitsPerValue: Int
        var doBlocks = false
        var encode: LongIntHashMap? = null
        if (min >= max) { // meta[-1]: All values are 0
            numBitsPerValue = 0
            meta?.writeInt(-1) // tablesize
        } else {
            if (uniqueValues != null && uniqueValues.size() > 1 && (DirectWriter.unsignedBitsRequired((uniqueValues.size() - 1).toLong())
                        < DirectWriter.unsignedBitsRequired((max - min) / gcd))
            ) {
                numBitsPerValue = DirectWriter.unsignedBitsRequired((uniqueValues.size() - 1).toLong())
                val sortedUniqueValues: LongArray = uniqueValues.toArray()
                Arrays.sort(sortedUniqueValues)
                meta?.writeInt(sortedUniqueValues.size) // tablesize
                for (v in sortedUniqueValues) {
                    meta?.writeLong(v) // table[] entry
                }
                encode = LongIntHashMap()
                for (i in sortedUniqueValues.indices) {
                    encode.put(sortedUniqueValues[i], i)
                }
                min = 0
                gcd = 1
            } else {
                uniqueValues = null
                // we do blocks if that appears to save 10+% storage
                doBlocks =
                    minMax.spaceInBits > 0 && blockMinMax.spaceInBits.toDouble() / minMax.spaceInBits <= 0.9
                if (doBlocks) {
                    numBitsPerValue = 0xFF
                    meta?.writeInt(-2 - NUMERIC_BLOCK_SHIFT) // tablesize
                } else {
                    numBitsPerValue = DirectWriter.unsignedBitsRequired((max - min) / gcd)
                    if (gcd == 1L && min > 0 && (DirectWriter.unsignedBitsRequired(max) == DirectWriter.unsignedBitsRequired(
                            max - min
                        ))
                    ) {
                        min = 0
                    }
                    meta?.writeInt(-1) // tablesize
                }
            }
        }

        meta?.writeByte(numBitsPerValue.toByte())
        meta?.writeLong(min)
        meta?.writeLong(gcd)
        val startOffset: Long = data!!.filePointer
        meta?.writeLong(startOffset) // valueOffset
        var jumpTableOffset: Long = -1
        if (doBlocks) {
            jumpTableOffset = writeValuesMultipleBlocks(valuesProducer.getSortedNumeric(field), gcd)
        } else if (numBitsPerValue != 0) {
            writeValuesSingleBlock(
                valuesProducer.getSortedNumeric(field), numValues, numBitsPerValue, min, gcd, encode
            )
        }
        meta?.writeLong(data!!.filePointer - startOffset) // valuesLength
        meta?.writeLong(jumpTableOffset)
        return longArrayOf(numDocsWithValue.toLong(), numValues)
    }

    @Throws(IOException::class)
    private fun writeValuesSingleBlock(
        values: SortedNumericDocValues,
        numValues: Long,
        numBitsPerValue: Int,
        min: Long,
        gcd: Long,
        encode: LongIntHashMap?
    ) {
        val writer: DirectWriter = DirectWriter.getInstance(data!!, numValues, numBitsPerValue)
        var doc: Int = values.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            var i = 0
            val count: Int = values.docValueCount()
            while (i < count) {
                val v: Long = values.nextValue()
                if (encode == null) {
                    writer.add((v - min) / gcd)
                } else {
                    writer.add(encode.get(v).toLong())
                }
                ++i
            }
            doc = values.nextDoc()
        }
        writer.finish()
    }

    // Returns the offset to the jump-table for vBPV
    @Throws(IOException::class)
    private fun writeValuesMultipleBlocks(values: SortedNumericDocValues, gcd: Long): Long {
        var offsets = LongArray(ArrayUtil.oversize(1, Long.SIZE_BYTES))
        var offsetsIndex = 0
        val buffer = LongArray(NUMERIC_BLOCK_SIZE)
        val encodeBuffer: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
        var upTo = 0
        var doc: Int = values.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            var i = 0
            val count: Int = values.docValueCount()
            while (i < count) {
                buffer[upTo++] = values.nextValue()
                if (upTo == NUMERIC_BLOCK_SIZE) {
                    offsets = ArrayUtil.grow(offsets, offsetsIndex + 1)
                    offsets[offsetsIndex++] = data!!.filePointer
                    writeBlock(buffer, NUMERIC_BLOCK_SIZE, gcd, encodeBuffer)
                    upTo = 0
                }
                ++i
            }
            doc = values.nextDoc()
        }
        if (upTo > 0) {
            offsets = ArrayUtil.grow(offsets, offsetsIndex + 1)
            offsets[offsetsIndex++] = data!!.filePointer
            writeBlock(buffer, upTo, gcd, encodeBuffer)
        }

        // All blocks has been written. Flush the offset jump-table
        val offsetsOrigo: Long = data!!.filePointer
        for (i in 0..<offsetsIndex) {
            data?.writeLong(offsets[i])
        }
        data?.writeLong(offsetsOrigo)
        return offsetsOrigo
    }

    @Throws(IOException::class)
    private fun writeBlock(values: LongArray, length: Int, gcd: Long, buffer: ByteBuffersDataOutput) {
        require(length > 0)
        var min = values[0]
        var max = values[0]
        for (i in 1..<length) {
            val v = values[i]
            require(Math.floorMod(values[i] - min, gcd) == 0L)
            min = min(min, v)
            max = max(max, v)
        }
        if (min == max) {
            data?.writeByte(0.toByte())
            data?.writeLong(min)
        } else {
            val bitsPerValue: Int = DirectWriter.unsignedBitsRequired((max - min) / gcd)
            buffer.reset()
            require(buffer.size() == 0L)
            val w: DirectWriter = DirectWriter.getInstance(buffer, length.toLong(), bitsPerValue)
            for (i in 0..<length) {
                w.add((values[i] - min) / gcd)
            }
            w.finish()
            data?.writeByte(bitsPerValue.toByte())
            data?.writeLong(min)
            data?.writeInt(Math.toIntExact(buffer.size()))
            buffer.copyTo(data!!)
        }
    }

    @Throws(IOException::class)
    override fun addBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        meta?.writeInt(field.number)
        meta?.writeByte(Lucene90DocValuesFormat.BINARY)

        var values: BinaryDocValues = valuesProducer.getBinary(field)
        var start: Long = data!!.filePointer
        meta?.writeLong(start) // dataOffset
        var numDocsWithField = 0
        var minLength = Int.Companion.MAX_VALUE
        var maxLength = 0
        var doc: Int = values.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            numDocsWithField++
            val v: BytesRef = values.binaryValue()!!
            val length: Int = v.length
            data?.writeBytes(v.bytes, v.offset, v.length)
            minLength = min(length, minLength)
            maxLength = max(length, maxLength)
            doc = values.nextDoc()
        }
        require(numDocsWithField <= maxDoc)
        meta?.writeLong(data!!.filePointer - start) // dataLength

        if (numDocsWithField == 0) {
            meta?.writeLong(-2) // docsWithFieldOffset
            meta?.writeLong(0L) // docsWithFieldLength
            meta?.writeShort(-1) // jumpTableEntryCount
            meta?.writeByte(-1) // denseRankPower
        } else if (numDocsWithField == maxDoc) {
            meta?.writeLong(-1) // docsWithFieldOffset
            meta?.writeLong(0L) // docsWithFieldLength
            meta?.writeShort(-1) // jumpTableEntryCount
            meta?.writeByte(-1) // denseRankPower
        } else {
            val offset: Long = data!!.filePointer
            meta?.writeLong(offset) // docsWithFieldOffset
            values = valuesProducer.getBinary(field)
            val jumpTableEntryCount: Short =
                IndexedDISI.writeBitSet(values, data!!, IndexedDISI.DEFAULT_DENSE_RANK_POWER)
            meta?.writeLong(data!!.filePointer - offset) // docsWithFieldLength
            meta?.writeShort(jumpTableEntryCount)
            meta?.writeByte(IndexedDISI.DEFAULT_DENSE_RANK_POWER)
        }

        meta?.writeInt(numDocsWithField)
        meta?.writeInt(minLength)
        meta?.writeInt(maxLength)
        if (maxLength > minLength) {
            start = data!!.filePointer
            meta?.writeLong(start)
            meta?.writeVInt(DIRECT_MONOTONIC_BLOCK_SHIFT)

            val writer: DirectMonotonicWriter =
                DirectMonotonicWriter.getInstance(
                    meta!!, data!!, numDocsWithField.toLong() + 1L, DIRECT_MONOTONIC_BLOCK_SHIFT
                )
            var addr: Long = 0
            writer.add(addr)
            values = valuesProducer.getBinary(field)
            var doc: Int = values.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS
            ) {
                addr += values.binaryValue()!!.length
                writer.add(addr)
                doc = values.nextDoc()
            }
            writer.finish()
            meta?.writeLong(data!!.filePointer - start)
        }
    }

    @Throws(IOException::class)
    override fun addSortedField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        meta?.writeInt(field.number)
        meta?.writeByte(Lucene90DocValuesFormat.SORTED)
        doAddSortedField(field, valuesProducer, false)
    }

    @Throws(IOException::class)
    private fun doAddSortedField(
        field: FieldInfo, valuesProducer: DocValuesProducer, addTypeByte: Boolean
    ) {
        val producer: DocValuesProducer =
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
                    val sorted: SortedDocValues = valuesProducer.getSorted(field)
                    val sortedOrds: NumericDocValues =
                        object : NumericDocValues() {
                            @Throws(IOException::class)
                            override fun longValue(): Long {
                                return sorted.ordValue().toLong()
                            }

                            @Throws(IOException::class)
                            override fun advanceExact(target: Int): Boolean {
                                return sorted.advanceExact(target)
                            }

                            override fun docID(): Int {
                                return sorted.docID()
                            }

                            @Throws(IOException::class)
                            override fun nextDoc(): Int {
                                return sorted.nextDoc()
                            }

                            @Throws(IOException::class)
                            override fun advance(target: Int): Int {
                                return sorted.advance(target)
                            }

                            override fun cost(): Long {
                                return sorted.cost()
                            }
                        }
                    return DocValues.singleton(sortedOrds)
                }
            }
        if (field.docValuesSkipIndexType() !== DocValuesSkipIndexType.NONE) {
            writeSkipIndex(field, producer)
        }
        if (addTypeByte) {
            meta?.writeByte(0.toByte()) // multiValued (0 = singleValued)
        }
        writeValues(field, producer, true)
        addTermsDict(DocValues.singleton(valuesProducer.getSorted(field)))
    }

    @Throws(IOException::class)
    private fun addTermsDict(values: SortedSetDocValues) {
        val size: Long = values.valueCount
        meta?.writeVLong(size)

        val blockMask = Lucene90DocValuesFormat.TERMS_DICT_BLOCK_LZ4_MASK
        val shift = Lucene90DocValuesFormat.TERMS_DICT_BLOCK_LZ4_SHIFT

        meta?.writeInt(DIRECT_MONOTONIC_BLOCK_SHIFT)
        val addressBuffer = ByteBuffersDataOutput()
        val addressOutput = ByteBuffersIndexOutput(addressBuffer, "temp", "temp")
        val numBlocks = (size + blockMask) ushr shift
        val writer: DirectMonotonicWriter =
            DirectMonotonicWriter.getInstance(
                meta!!, addressOutput, numBlocks, DIRECT_MONOTONIC_BLOCK_SHIFT
            )

        val previous = BytesRefBuilder()
        var ord: Long = 0
        var start: Long = data!!.filePointer
        var maxLength = 0
        var maxBlockLength = 0
        val iterator: TermsEnum = values.termsEnum()

        val ht = FastCompressionHashTable()
        var bufferedOutput = ByteArrayDataOutput(termsDictBuffer)
        var dictLength = 0

        var term: BytesRef? = iterator.next()
        while (term != null) {
            if ((ord and blockMask.toLong()) == 0L) {
                if (ord != 0L) {
                    // flush the previous block
                    val uncompressedLength =
                        compressAndGetTermsDictBlockLength(bufferedOutput, dictLength, ht)
                    maxBlockLength = max(maxBlockLength, uncompressedLength)
                    bufferedOutput.reset(termsDictBuffer)
                }

                writer.add(data!!.filePointer - start)
                // Write the first term both to the index output, and to the buffer where we'll use it as a
                // dictionary for compression
                data?.writeVInt(term.length)
                data?.writeBytes(term.bytes, term.offset, term.length)
                bufferedOutput = maybeGrowBuffer(bufferedOutput, term.length)
                bufferedOutput.writeBytes(term.bytes, term.offset, term.length)
                dictLength = term.length
            } else {
                val prefixLength: Int = StringHelper.bytesDifference(previous.get(), term)
                val suffixLength: Int = term.length - prefixLength
                require(
                    suffixLength > 0 // terms are unique
                )
                // Will write (suffixLength + 1 byte + 2 vint) bytes. Grow the buffer in need.
                bufferedOutput = maybeGrowBuffer(bufferedOutput, suffixLength + 11)
                bufferedOutput.writeByte(
                    (min(prefixLength, 15) or (min(15, suffixLength - 1) shl 4)).toByte()
                )
                if (prefixLength >= 15) {
                    bufferedOutput.writeVInt(prefixLength - 15)
                }
                if (suffixLength >= 16) {
                    bufferedOutput.writeVInt(suffixLength - 16)
                }
                bufferedOutput.writeBytes(term.bytes, term.offset + prefixLength, suffixLength)
            }
            maxLength = max(maxLength, term.length)
            previous.copyBytes(term)
            ++ord
            term = iterator.next()
        }
        // Compress and write out the last block
        if (bufferedOutput.position > dictLength) {
            val uncompressedLength =
                compressAndGetTermsDictBlockLength(bufferedOutput, dictLength, ht)
            maxBlockLength = max(maxBlockLength, uncompressedLength)
        }

        writer.finish()
        meta?.writeInt(maxLength)
        // Write one more int for storing max block length.
        meta?.writeInt(maxBlockLength)
        meta?.writeLong(start)
        meta?.writeLong(data!!.filePointer - start)
        start = data!!.filePointer
        addressBuffer.copyTo(data!!)
        meta?.writeLong(start)
        meta?.writeLong(data!!.filePointer - start)

        // Now write the reverse terms index
        writeTermsIndex(values)
    }

    @Throws(IOException::class)
    private fun compressAndGetTermsDictBlockLength(
        bufferedOutput: ByteArrayDataOutput, dictLength: Int, ht: FastCompressionHashTable
    ): Int {
        val uncompressedLength: Int = bufferedOutput.position - dictLength
        data?.writeVInt(uncompressedLength)
        LZ4.compressWithDictionary(termsDictBuffer, 0, dictLength, uncompressedLength, data!!, ht)
        return uncompressedLength
    }

    private fun maybeGrowBuffer(bufferedOutput: ByteArrayDataOutput, termLength: Int): ByteArrayDataOutput {
        var bufferedOutput: ByteArrayDataOutput = bufferedOutput
        val pos: Int = bufferedOutput.position
        val originalLength = termsDictBuffer.size
        if (pos + termLength >= originalLength - 1) {
            termsDictBuffer = ArrayUtil.grow(termsDictBuffer, originalLength + termLength)
            bufferedOutput = ByteArrayDataOutput(termsDictBuffer, pos, termsDictBuffer.size - pos)
        }
        return bufferedOutput
    }

    @Throws(IOException::class)
    private fun writeTermsIndex(values: SortedSetDocValues) {
        val size: Long = values.valueCount
        meta?.writeInt(Lucene90DocValuesFormat.TERMS_DICT_REVERSE_INDEX_SHIFT)
        var start: Long = data!!.filePointer

        val numBlocks = (1L + ((size + Lucene90DocValuesFormat.TERMS_DICT_REVERSE_INDEX_MASK)
                ushr Lucene90DocValuesFormat.TERMS_DICT_REVERSE_INDEX_SHIFT))
        val addressBuffer = ByteBuffersDataOutput()
        val writer: DirectMonotonicWriter
        ByteBuffersIndexOutput(addressBuffer, "temp", "temp").use { addressOutput ->
            writer =
                DirectMonotonicWriter.getInstance(
                    meta!!, addressOutput, numBlocks, DIRECT_MONOTONIC_BLOCK_SHIFT
                )
            val iterator: TermsEnum = values.termsEnum()
            val previous = BytesRefBuilder()
            var offset: Long = 0
            var ord: Long = 0
            var term: BytesRef? = iterator.next()
            while (term != null) {
                if ((ord and Lucene90DocValuesFormat.TERMS_DICT_REVERSE_INDEX_MASK.toLong()) == 0L) {
                    writer.add(offset)
                    val sortKeyLength: Int = if (ord == 0L) {
                        // no previous term: no bytes to write
                        0
                    } else {
                        StringHelper.sortKeyLength(previous.get(), term)
                    }
                    offset += sortKeyLength.toLong()
                    data?.writeBytes(term.bytes, term.offset, sortKeyLength)
                } else if ((ord and Lucene90DocValuesFormat.TERMS_DICT_REVERSE_INDEX_MASK.toLong())
                    == Lucene90DocValuesFormat.TERMS_DICT_REVERSE_INDEX_MASK.toLong()
                ) {
                    previous.copyBytes(term)
                }
                ++ord
                term = iterator.next()
            }
            writer.add(offset)
            writer.finish()
            meta?.writeLong(start)
            meta?.writeLong(data!!.filePointer - start)
            start = data!!.filePointer
            addressBuffer.copyTo(data!!)
            meta?.writeLong(start)
            meta?.writeLong(data!!.filePointer - start)
        }
    }

    @Throws(IOException::class)
    override fun addSortedNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        meta?.writeInt(field.number)
        meta?.writeByte(Lucene90DocValuesFormat.SORTED_NUMERIC)
        doAddSortedNumericField(field, valuesProducer, false)
    }

    @Throws(IOException::class)
    private fun doAddSortedNumericField(
        field: FieldInfo, valuesProducer: DocValuesProducer, ords: Boolean
    ) {
        if (field.docValuesSkipIndexType() !== DocValuesSkipIndexType.NONE) {
            writeSkipIndex(field, valuesProducer)
        }
        if (ords) {
            meta?.writeByte(1.toByte()) // multiValued (1 = multiValued)
        }
        val stats = writeValues(field, valuesProducer, ords)
        val numDocsWithField: Int = Math.toIntExact(stats[0])
        val numValues = stats[1]
        require(numValues >= numDocsWithField)

        meta?.writeInt(numDocsWithField)
        if (numValues > numDocsWithField) {
            val start: Long = data!!.filePointer
            meta?.writeLong(start)
            meta?.writeVInt(DIRECT_MONOTONIC_BLOCK_SHIFT)

            val addressesWriter: DirectMonotonicWriter =
                DirectMonotonicWriter.getInstance(
                    meta!!, data!!, numDocsWithField + 1L, DIRECT_MONOTONIC_BLOCK_SHIFT
                )
            var addr: Long = 0
            addressesWriter.add(addr)
            val values: SortedNumericDocValues = valuesProducer.getSortedNumeric(field)
            var doc: Int = values.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS
            ) {
                addr += values.docValueCount()
                addressesWriter.add(addr)
                doc = values.nextDoc()
            }
            addressesWriter.finish()
            meta?.writeLong(data!!.filePointer - start)
        }
    }

    @Throws(IOException::class)
    override fun addSortedSetField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        meta?.writeInt(field.number)
        meta?.writeByte(Lucene90DocValuesFormat.SORTED_SET)

        if (isSingleValued(valuesProducer.getSortedSet(field))) {
            doAddSortedField(
                field,
                object : EmptyDocValuesProducer() {
                    @Throws(IOException::class)
                    override fun getSorted(field: FieldInfo): SortedDocValues {
                        return SortedSetSelector.wrap(
                            valuesProducer.getSortedSet(field), SortedSetSelector.Type.MIN
                        )
                    }
                },
                true
            )
            return
        }

        doAddSortedNumericField(
            field,
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
                    val values: SortedSetDocValues = valuesProducer.getSortedSet(field)
                    return object : SortedNumericDocValues() {
                        var ords: LongArray = LongsRef.EMPTY_LONGS
                        var i: Int = 0
                        var docValueCount: Int = 0

                        @Throws(IOException::class)
                        override fun nextValue(): Long {
                            return ords[i++]
                        }

                        override fun docValueCount(): Int {
                            return docValueCount
                        }

                        @Throws(IOException::class)
                        override fun advanceExact(target: Int): Boolean {
                            throw UnsupportedOperationException()
                        }

                        override fun docID(): Int {
                            return values.docID()
                        }

                        @Throws(IOException::class)
                        override fun nextDoc(): Int {
                            val doc: Int = values.nextDoc()
                            if (doc != NO_MORE_DOCS) {
                                docValueCount = values.docValueCount()
                                ords = ArrayUtil.grow(ords, docValueCount)
                                for (j in 0..<docValueCount) {
                                    ords[j] = values.nextOrd()
                                }
                                i = 0
                            }
                            return doc
                        }

                        @Throws(IOException::class)
                        override fun advance(target: Int): Int {
                            throw UnsupportedOperationException()
                        }

                        override fun cost(): Long {
                            return values.cost()
                        }
                    }
                }
            },
            true
        )

        addTermsDict(valuesProducer.getSortedSet(field))
    }

    companion object {
        private fun buildLevel(accumulators: MutableList<SkipAccumulator>): MutableList<SkipAccumulator> {
            val levelSize = 1 shl SKIP_INDEX_LEVEL_SHIFT
            val collector: MutableList<SkipAccumulator> = ArrayList()
            var i = 0
            while (i < accumulators.size - levelSize + 1) {
                collector.add(SkipAccumulator.Companion.merge(accumulators, i, levelSize))
                i += levelSize
            }
            return collector
        }

        private fun getLevels(index: Int, size: Int): Int {
            if (Int.numberOfTrailingZeros(index) >= SKIP_INDEX_LEVEL_SHIFT) {
                // TODO: can we do it in constant time rather than linearly with SKIP_INDEX_MAX_LEVEL
                val left = size - index
                for (level in SKIP_INDEX_MAX_LEVEL - 1 downTo 1) {
                    val numberIntervals = 1 shl (SKIP_INDEX_LEVEL_SHIFT * level)
                    if (left >= numberIntervals && index % numberIntervals == 0) {
                        return level + 1
                    }
                }
            }
            return 1
        }

        @Throws(IOException::class)
        private fun isSingleValued(values: SortedSetDocValues): Boolean {
            if (DocValues.unwrapSingleton(values) != null) {
                return true
            }

            require(values.docID() == -1)
            var doc: Int = values.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                val docValueCount: Int = values.docValueCount()
                require(docValueCount > 0)
                if (docValueCount > 1) {
                    return false
                }
                doc = values.nextDoc()
            }
            return true
        }
    }
}
