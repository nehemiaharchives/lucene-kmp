package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.InputStreamDataInput
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.IntsRef
import okio.IOException

/** Abstract dictionary base class. */
abstract class BinaryDictionary<T : MorphData>(
    targetMapResource: IOSupplier<InputStream>,
    dictResource: IOSupplier<InputStream>,
    targetMapCodecHeader: String,
    dictCodecHeader: String,
    dictCodecVersion: Int
) : Dictionary<T> {
    companion object {
        const val DICT_FILENAME_SUFFIX: String = "\$buffer.dat"
        const val TARGETMAP_FILENAME_SUFFIX: String = "\$targetMap.dat"
        const val POSDICT_FILENAME_SUFFIX: String = "\$posDict.dat"
    }

    private val targetMapOffsets: IntArray
    private val targetMap: IntArray
    protected val buffer: ByteBuffer

    init {
        BufferedInputStream(targetMapResource.get()).use { mapInput ->
            val `in`: DataInput = InputStreamDataInput(mapInput)
            CodecUtil.checkHeader(`in`, targetMapCodecHeader, dictCodecVersion, dictCodecVersion)
            targetMap = IntArray(`in`.readVInt())
            targetMapOffsets = IntArray(`in`.readVInt())
            populateTargetMap(`in`, targetMap, targetMapOffsets)
        }

        dictResource.get().use { dictInput ->
            val `in`: DataInput = InputStreamDataInput(dictInput)
            CodecUtil.checkHeader(`in`, dictCodecHeader, dictCodecVersion, dictCodecVersion)
            val size = `in`.readVInt()
            val bytes = ByteArray(size)
            `in`.readBytes(bytes, 0, size)
            buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer()
        }
    }

    @Throws(IOException::class)
    private fun populateTargetMap(`in`: DataInput, targetMap: IntArray, targetMapOffsets: IntArray) {
        var accum = 0
        var sourceId = 0
        for (ofs in targetMap.indices) {
            val value = `in`.readVInt()
            if ((value and 0x01) != 0) {
                targetMapOffsets[sourceId] = ofs
                sourceId++
            }
            accum += value ushr 1
            targetMap[ofs] = accum
        }
        if (sourceId + 1 != targetMapOffsets.size) {
            throw IOException(
                "targetMap file format broken; targetMap.length=${targetMap.size}, " +
                    "targetMapOffsets.length=${targetMapOffsets.size}, sourceId=$sourceId"
            )
        }
        targetMapOffsets[sourceId] = targetMap.size
    }

    fun lookupWordIds(sourceId: Int, ref: IntsRef) {
        ref.ints = targetMap
        ref.offset = targetMapOffsets[sourceId]
        // targetMapOffsets always has one more entry pointing behind last:
        ref.length = targetMapOffsets[sourceId + 1] - ref.offset
    }
}
