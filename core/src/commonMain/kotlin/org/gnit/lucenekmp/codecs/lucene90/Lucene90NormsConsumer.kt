package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.NormsConsumer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.lucene90.Lucene90NormsFormat.Companion.VERSION_CURRENT
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.IOUtils
import kotlin.math.max
import kotlin.math.min
import okio.IOException

/** Writer for [Lucene90NormsFormat]  */
internal class Lucene90NormsConsumer(
    state: SegmentWriteState,
    dataCodec: String,
    dataExtension: String,
    metaCodec: String,
    metaExtension: String
) : NormsConsumer() {
    var data: IndexOutput? = null
    var meta: IndexOutput? = null
    var maxDoc: Int = 0

    init {
        var success = false
        try {
            val dataName: String =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name, state.segmentSuffix, dataExtension
                )
            data = state.directory.createOutput(dataName, state.context)
            CodecUtil.writeIndexHeader(
                data!!, dataCodec, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix
            )
            val metaName: String =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name, state.segmentSuffix, metaExtension
                )
            meta = state.directory.createOutput(metaName, state.context)
            CodecUtil.writeIndexHeader(
                meta!!, metaCodec, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix
            )
            maxDoc = state.segmentInfo.maxDoc()
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
                meta?.writeInt(-1) // write EOF marker
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
    override fun addNormsField(field: FieldInfo, normsProducer: NormsProducer) {
        var values: NumericDocValues = normsProducer.getNorms(field)
        var numDocsWithValue = 0
        var min = Long.Companion.MAX_VALUE
        var max = Long.Companion.MIN_VALUE
        var doc: Int = values.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            numDocsWithValue++
            val v: Long = values.longValue()
            min = min(min, v)
            max = max(max, v)
            doc = values.nextDoc()
        }
        require(numDocsWithValue <= maxDoc)

        meta?.writeInt(field.number)

        if (numDocsWithValue == 0) {
            meta?.writeLong(-2) // docsWithFieldOffset
            meta?.writeLong(0L) // docsWithFieldLength
            meta?.writeShort(-1) // jumpTableEntryCount
            meta?.writeByte(-1) // denseRankPower
        } else if (numDocsWithValue == maxDoc) {
            meta?.writeLong(-1) // docsWithFieldOffset
            meta?.writeLong(0L) // docsWithFieldLength
            meta?.writeShort(-1) // jumpTableEntryCount
            meta?.writeByte(-1) // denseRankPower
        } else {
            val offset: Long = data!!.filePointer
            meta?.writeLong(offset) // docsWithFieldOffset
            values = normsProducer.getNorms(field)
            val jumpTableEntryCount =
                IndexedDISI.writeBitSet(values, data!!, IndexedDISI.DEFAULT_DENSE_RANK_POWER)
            meta?.writeLong(data!!.filePointer - offset) // docsWithFieldLength
            meta?.writeShort(jumpTableEntryCount)
            meta?.writeByte(IndexedDISI.DEFAULT_DENSE_RANK_POWER)
        }

        meta?.writeInt(numDocsWithValue)
        val numBytesPerValue = numBytesPerValue(min, max)

        meta?.writeByte(numBytesPerValue.toByte())
        if (numBytesPerValue == 0) {
            meta?.writeLong(min)
        } else {
            meta?.writeLong(data!!.filePointer) // normsOffset
            values = normsProducer.getNorms(field)
            writeValues(values, numBytesPerValue, data!!)
        }
    }

    private fun numBytesPerValue(min: Long, max: Long): Int {
        return if (min >= max) {
            0
        } else if (min >= Byte.Companion.MIN_VALUE && max <= Byte.Companion.MAX_VALUE) {
            1
        } else if (min >= Short.Companion.MIN_VALUE && max <= Short.Companion.MAX_VALUE) {
            2
        } else if (min >= Int.Companion.MIN_VALUE && max <= Int.Companion.MAX_VALUE) {
            4
        } else {
            8
        }
    }

    @Throws(IOException::class, AssertionError::class)
    private fun writeValues(values: NumericDocValues, numBytesPerValue: Int, out: IndexOutput) {
        var doc: Int = values.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            val value: Long = values.longValue()
            when (numBytesPerValue) {
                1 -> out.writeByte(value.toByte())
                2 -> out.writeShort(value.toShort())
                4 -> out.writeInt(value.toInt())
                8 -> out.writeLong(value)
                else -> throw AssertionError()
            }
            doc = values.nextDoc()
        }
    }
}
