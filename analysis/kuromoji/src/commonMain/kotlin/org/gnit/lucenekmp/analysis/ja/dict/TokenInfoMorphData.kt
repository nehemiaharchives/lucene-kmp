package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.InputStreamDataInput
import org.gnit.lucenekmp.util.IOSupplier
import okio.IOException

/** Morphological information for system dictionary. */
open class TokenInfoMorphData(
    private val buffer: ByteBuffer,
    posResource: IOSupplier<InputStream>
) : JaMorphData {
    private val posDict: Array<String>
    private val inflTypeDict: Array<String?>
    private val inflFormDict: Array<String?>

    init {
        BufferedInputStream(posResource.get()).use { posInput ->
            val `in`: DataInput = InputStreamDataInput(posInput)
            CodecUtil.checkHeader(`in`, DictionaryConstants.POSDICT_HEADER, DictionaryConstants.VERSION, DictionaryConstants.VERSION)
            val posSize = `in`.readVInt()
            posDict = Array(posSize) { "" }
            inflTypeDict = Array(posSize) { null }
            inflFormDict = Array(posSize) { null }
            populatePosDict(`in`, posSize, posDict, inflTypeDict, inflFormDict)
        }
    }

    private fun populatePosDict(
        `in`: DataInput,
        posSize: Int,
        posDict: Array<String>,
        inflTypeDict: Array<String?>,
        inflFormDict: Array<String?>
    ) {
        for (j in 0 until posSize) {
            posDict[j] = `in`.readString()
            inflTypeDict[j] = `in`.readString()
            inflFormDict[j] = `in`.readString()
            if (inflTypeDict[j]?.isEmpty() == true) {
                inflTypeDict[j] = null
            }
            if (inflFormDict[j]?.isEmpty() == true) {
                inflFormDict[j] = null
            }
        }
    }

    override fun getLeftId(morphId: Int): Int {
        return (buffer.getShort(morphId).toInt() and 0xffff) ushr 3
    }

    override fun getRightId(morphId: Int): Int {
        return (buffer.getShort(morphId).toInt() and 0xffff) ushr 3
    }

    override fun getWordCost(morphId: Int): Int {
        return buffer.getShort(morphId + 2).toInt()
    }

    override fun getBaseForm(morphId: Int, surface: CharArray, off: Int, len: Int): String? {
        if (hasBaseFormData(morphId)) {
            var offset = baseFormOffset(morphId)
            val data = buffer.get(offset++).toInt() and 0xff
            val prefix = data ushr 4
            val suffix = data and 0xF
            val text = CharArray(prefix + suffix)
            surface.copyInto(text, 0, off, off + prefix)
            for (i in 0 until suffix) {
                text[prefix + i] = buffer.getShort(offset + (i shl 1)).toInt().toChar()
            }
            return text.concatToString()
        }
        return null
    }

    override fun getReading(morphId: Int, surface: CharArray, off: Int, len: Int): String? {
        if (hasReadingData(morphId)) {
            var offset = readingOffset(morphId)
            val readingData = buffer.get(offset++).toInt() and 0xff
            return readString(offset, readingData ushr 1, (readingData and 1) == 1)
        }
        val text = CharArray(len)
        for (i in 0 until len) {
            val ch = surface[off + i]
            text[i] = if (ch > 0x3040.toChar() && ch < 0x3097.toChar()) {
                (ch.code + 0x60).toChar()
            } else {
                ch
            }
        }
        return text.concatToString()
    }

    override fun getPartOfSpeech(morphId: Int): String? = posDict[getLeftId(morphId)]

    override fun getPronunciation(morphId: Int, surface: CharArray, off: Int, len: Int): String? {
        if (hasPronunciationData(morphId)) {
            var offset = pronunciationOffset(morphId)
            val pronunciationData = buffer.get(offset++).toInt() and 0xff
            return readString(offset, pronunciationData ushr 1, (pronunciationData and 1) == 1)
        }
        return getReading(morphId, surface, off, len)
    }

    override fun getInflectionType(morphId: Int): String? = inflTypeDict[getLeftId(morphId)]

    override fun getInflectionForm(wordId: Int): String? = inflFormDict[getLeftId(wordId)]

    private fun readingOffset(wordId: Int): Int {
        var offset = baseFormOffset(wordId)
        if (hasBaseFormData(wordId)) {
            val baseFormLength = buffer.get(offset++).toInt() and 0xF
            return offset + (baseFormLength shl 1)
        }
        return offset
    }

    private fun pronunciationOffset(wordId: Int): Int {
        if (hasReadingData(wordId)) {
            var offset = readingOffset(wordId)
            val readingData = buffer.get(offset++).toInt() and 0xff
            val readingLength = if ((readingData and 1) == 0) {
                readingData and 0xfe
            } else {
                readingData ushr 1
            }
            return offset + readingLength
        }
        return readingOffset(wordId)
    }

    private fun baseFormOffset(wordId: Int): Int = wordId + 4

    private fun hasBaseFormData(wordId: Int): Boolean {
        return (buffer.getShort(wordId).toInt() and HAS_BASEFORM) != 0
    }

    private fun hasReadingData(wordId: Int): Boolean {
        return (buffer.getShort(wordId).toInt() and HAS_READING) != 0
    }

    private fun hasPronunciationData(wordId: Int): Boolean {
        return (buffer.getShort(wordId).toInt() and HAS_PRONUNCIATION) != 0
    }

    private fun readString(offset: Int, length: Int, kana: Boolean): String {
        val text = CharArray(length)
        if (kana) {
            for (i in 0 until length) {
                text[i] = (0x30A0 + (buffer.get(offset + i).toInt() and 0xff)).toChar()
            }
        } else {
            for (i in 0 until length) {
                text[i] = buffer.getShort(offset + (i shl 1)).toInt().toChar()
            }
        }
        return text.concatToString()
    }

    companion object {
        /** flag that the entry has baseform data. otherwise it's not inflected (same as surface form) */
        const val HAS_BASEFORM: Int = 1

        /** flag that the entry has reading data. otherwise reading is surface form converted to katakana */
        const val HAS_READING: Int = 2

        /** flag that the entry has pronunciation data. otherwise pronunciation is the reading */
        const val HAS_PRONUNCIATION: Int = 4
    }
}
