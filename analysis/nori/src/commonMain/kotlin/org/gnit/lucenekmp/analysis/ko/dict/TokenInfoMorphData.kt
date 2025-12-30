package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.ko.POS
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.Ported
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.InputStreamDataInput
import org.gnit.lucenekmp.util.IOSupplier

/** Morphological information for system dictionary. */
open class TokenInfoMorphData(
    private val buffer: ByteBuffer,
    posResource: IOSupplier<InputStream>
) : KoMorphData {
    private val posDict: Array<POS.Tag>

    init {
        BufferedInputStream(posResource.get()).use { posInput ->
            val `in`: DataInput = InputStreamDataInput(posInput)
            CodecUtil.checkHeader(`in`, DictionaryConstants.POSDICT_HEADER, DictionaryConstants.VERSION, DictionaryConstants.VERSION)
            val posSize = `in`.readVInt()
            posDict = Array(posSize) { POS.Tag.UNKNOWN }
            for (j in 0 until posSize) {
                posDict[j] = POS.resolveTag(`in`.readByte())
            }
        }
    }

    override fun getLeftId(morphId: Int): Int {
        return (buffer.getShort(morphId).toInt() ushr 2)
    }

    override fun getRightId(morphId: Int): Int {
        return (buffer.getShort(morphId + 2).toInt() ushr 2)
    }

    override fun getWordCost(morphId: Int): Int {
        return buffer.getShort(morphId + 4).toInt()
    }

    override fun getPOSType(morphId: Int): POS.Type {
        val value = (buffer.getShort(morphId).toInt() and 3).toByte()
        return POS.resolveType(value)
    }

    override fun getLeftPOS(morphId: Int): POS.Tag = posDict[getLeftId(morphId)]

    override fun getRightPOS(morphId: Int): POS.Tag {
        val type = getPOSType(morphId)
        return if (type == POS.Type.MORPHEME || type == POS.Type.COMPOUND || hasSinglePOS(morphId)) {
            getLeftPOS(morphId)
        } else {
            val value = buffer.get(morphId + 6)
            POS.resolveTag(value)
        }
    }

    override fun getReading(morphId: Int): String? {
        return if (hasReadingData(morphId)) {
            val offset = morphId + 6
            readString(offset)
        } else {
            null
        }
    }

    override fun getMorphemes(
        morphId: Int,
        surfaceForm: CharArray,
        off: Int,
        len: Int
    ): Array<KoMorphData.Morpheme>? {
        val posType = getPOSType(morphId)
        if (posType == POS.Type.MORPHEME) {
            return null
        }
        var offset = morphId + 6
        val hasSinglePos = hasSinglePOS(morphId)
        if (!hasSinglePos) {
            offset++
        }
        val length = buffer.get(offset++).toInt()
        if (length == 0) {
            return null
        }
        val morphemes = Array(length) { KoMorphData.Morpheme(POS.Tag.UNKNOWN, "") }
        var surfaceOffset = 0
        val leftPOS = getLeftPOS(morphId)
        for (i in 0 until length) {
            val tag = if (hasSinglePos) leftPOS else POS.resolveTag(buffer.get(offset++))
            val form: String
            if (posType == POS.Type.INFLECT) {
                form = readString(offset)
                offset += form.length * 2 + 1
            } else {
                val formLen = buffer.get(offset++).toInt()
                form = run {
                    val offset1 = off + surfaceOffset
                    surfaceForm.concatToString(offset1, offset1 + formLen)
                }
                surfaceOffset += formLen
            }
            morphemes[i] = KoMorphData.Morpheme(tag, form)
        }
        return morphemes
    }

    private fun readString(offset: Int): String {
        var strOffset = offset
        val len = buffer.get(strOffset++).toInt() and 0xFF
        val text = CharArray(len)
        for (i in 0 until len) {
            val ch = buffer.getShort(strOffset + (i shl 1)).toInt().toChar()
            text[i] = ch
        }
        return text.concatToString()
    }

    private fun hasSinglePOS(wordId: Int): Boolean {
        return (buffer.getShort(wordId + 2).toInt() and HAS_SINGLE_POS) != 0
    }

    private fun hasReadingData(wordId: Int): Boolean {
        return (buffer.getShort(wordId + 2).toInt() and HAS_READING) != 0
    }

    companion object {
        /** flag that the entry has a single part of speech (leftPOS) */
        const val HAS_SINGLE_POS: Int = 1

        /** flag that the entry has reading data. otherwise reading is surface form */
        const val HAS_READING: Int = 2
    }
}
