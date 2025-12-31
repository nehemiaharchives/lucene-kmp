package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.analysis.morph.DictionaryEntryWriter
import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import okio.IOException
import org.gnit.lucenekmp.jdkport.OutputStream

/** Writes system dictionary entries */
internal class TokenInfoDictionaryEntryWriter(size: Int) : DictionaryEntryWriter(size) {
    companion object {
        private const val ID_LIMIT = 8192
    }

    override fun putEntry(entry: Array<String>): Int {
        val leftId = entry[1].toShort()
        val rightId = entry[2].toShort()
        val wordCost = entry[3].toShort()

        val sb = StringBuilder()
        for (i in 4 until 8) {
            val part = entry[i]
            if (part != "*") {
                if (sb.isNotEmpty()) {
                    sb.append('-')
                }
                sb.append(part)
            }
        }
        val posData = sb.toString()
        require(posData.isNotEmpty()) { "POS fields are empty" }
        sb.setLength(0)
        sb.append(CSVUtil.quoteEscape(posData))
        sb.append(',')
        if (entry[8] != "*") {
            sb.append(CSVUtil.quoteEscape(entry[8]))
        }
        sb.append(',')
        if (entry[9] != "*") {
            sb.append(CSVUtil.quoteEscape(entry[9]))
        }
        val fullPOSData = sb.toString()

        val baseForm = entry[10]
        val reading = entry[11]
        val pronunciation = entry[12]

        val left = buffer.remaining()
        val worstCase = 4 + 3 + 2 * (baseForm.length + reading.length + pronunciation.length)
        if (worstCase > left) {
            val newBuffer = ByteBuffer.allocate(ArrayUtil.oversize(buffer.limit + worstCase - left, 1))
            buffer.flip()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            newBuffer.put(bytes)
            buffer = newBuffer
        }

        var flags = 0
        require(baseForm.isNotEmpty()) { "base form is empty" }
        if (baseForm != "*" && baseForm != entry[0]) {
            flags = flags or TokenInfoMorphData.HAS_BASEFORM
        }
        if (reading != toKatakana(entry[0])) {
            flags = flags or TokenInfoMorphData.HAS_READING
        }
        if (pronunciation != reading) {
            flags = flags or TokenInfoMorphData.HAS_PRONUNCIATION
        }

        require(leftId == rightId) { "rightId != leftId: $rightId $leftId" }
        require(leftId < ID_LIMIT) { "leftId >= $ID_LIMIT: $leftId" }

        val toFill = 1 + leftId - posDict.size
        for (i in 0 until toFill) {
            posDict.add(null)
        }

        val existing = posDict[leftId.toInt()]
        if (existing != null && existing != fullPOSData) {
            throw IllegalArgumentException("Multiple entries found for leftID=$leftId")
        }
        posDict[leftId.toInt()] = fullPOSData

        buffer.putShort(((leftId.toInt() shl 3) or flags).toShort())
        buffer.putShort(wordCost)

        if ((flags and TokenInfoMorphData.HAS_BASEFORM) != 0) {
            require(baseForm.length < 16) { "Length of base form $baseForm is >= 16" }
            val shared = sharedPrefix(entry[0], baseForm)
            val suffix = baseForm.length - shared
            buffer.put((shared shl 4 or suffix).toByte())
            for (i in shared until baseForm.length) {
                buffer.putShort(baseForm[i].code.toShort())
            }
        }

        if ((flags and TokenInfoMorphData.HAS_READING) != 0) {
            if (isKatakana(reading)) {
                buffer.put(((reading.length shl 1) or 1).toByte())
                writeKatakana(reading, buffer)
            } else {
                buffer.put((reading.length shl 1).toByte())
                for (i in reading.indices) {
                    buffer.putShort(reading[i].code.toShort())
                }
            }
        }

        if ((flags and TokenInfoMorphData.HAS_PRONUNCIATION) != 0) {
            if (isKatakana(pronunciation)) {
                buffer.put(((pronunciation.length shl 1) or 1).toByte())
                writeKatakana(pronunciation, buffer)
            } else {
                buffer.put((pronunciation.length shl 1).toByte())
                for (i in pronunciation.indices) {
                    buffer.putShort(pronunciation[i].code.toShort())
                }
            }
        }

        return buffer.position
    }

    private fun isKatakana(s: String): Boolean {
        for (i in s.indices) {
            val ch = s[i]
            if (ch < '\u30a0' || ch > '\u30ff') {
                return false
            }
        }
        return true
    }

    private fun writeKatakana(s: String, buffer: ByteBuffer) {
        for (i in s.indices) {
            buffer.put((s[i].code - 0x30A0).toByte())
        }
    }

    private fun toKatakana(s: String): String {
        val text = CharArray(s.length)
        for (i in s.indices) {
            val ch = s[i]
            text[i] = if (ch > '\u3040' && ch < '\u3097') {
                (ch.code + 0x60).toChar()
            } else {
                ch
            }
        }
        return text.concatToString()
    }

    private fun sharedPrefix(left: String, right: String): Int {
        val len = if (left.length < right.length) left.length else right.length
        for (i in 0 until len) if (left[i] != right[i]) return i
        return len
    }

    @Throws(IOException::class)
    override fun writePosDict(bos: OutputStream, out: DataOutput) {
        out.writeVInt(posDict.size)
        for (s in posDict) {
            if (s == null) {
                out.writeByte(0)
                out.writeByte(0)
                out.writeByte(0)
            } else {
                val data = CSVUtil.parse(s)
                if (data.size != 3) {
                    throw IllegalArgumentException("Malformed pos/inflection: $s; expected 3 characters")
                }
                out.writeString(data[0])
                out.writeString(data[1])
                out.writeString(data[2])
            }
        }
    }
}
