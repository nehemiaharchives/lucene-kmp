package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.ko.POS
import org.gnit.lucenekmp.analysis.morph.DictionaryEntryWriter
import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.Ported
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import okio.IOException
import org.gnit.lucenekmp.jdkport.OutputStream

/** Writes system dictionary entries. */
@Ported(from = "org.apache.lucene.analysis.ko.dict.TokenInfoDictionaryEntryWriter")
internal class TokenInfoDictionaryEntryWriter(size: Int) : DictionaryEntryWriter(size) {
    companion object {
        private const val ID_LIMIT = 8192
    }

    /**
     * put the entry in map
     *
     * mecab-ko-dic features
     *
     * 0   - surface
     * 1   - left cost
     * 2   - right cost
     * 3   - word cost
     * 4   - part of speech0+part of speech1+...
     * 5   - semantic class
     * 6   - T if the last character of the surface form has a coda, F otherwise
     * 7   - reading
     * 8   - POS type (*, Compound, Inflect, Preanalysis)
     * 9   - left POS
     * 10  - right POS
     * 11  - expression
     */
    override fun putEntry(entry: Array<String>): Int {
        val leftId = entry[1].toShort()
        val rightId = entry[2].toShort()
        val wordCost = entry[3].toShort()

        val posType = POS.resolveType(entry[8])
        val leftPOS: POS.Tag
        val rightPOS: POS.Tag
        if (posType == POS.Type.MORPHEME || posType == POS.Type.COMPOUND || entry[9] == "*") {
            leftPOS = POS.resolveTag(entry[4])
            rightPOS = leftPOS
        } else {
            leftPOS = POS.resolveTag(entry[9])
            rightPOS = POS.resolveTag(entry[10])
        }
        val reading = if (entry[7] == "*") "" else if (entry[0] == entry[7]) "" else entry[7]
        val expression = if (entry[11] == "*") "" else entry[11]

        // extend buffer if necessary
        val left = buffer.remaining()
        val worstCase = 9 + 2 * (expression.length + reading.length)
        if (worstCase > left) {
            val newBuffer = ByteBuffer.allocate(ArrayUtil.oversize(buffer.limit + worstCase - left, 1))
            buffer.flip()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            newBuffer.put(bytes)
            buffer = newBuffer
        }

        // add pos mapping
        val toFill = 1 + leftId - posDict.size
        for (i in 0 until toFill) {
            posDict.add(null)
        }
        val fullPOSData = leftPOS.name + "," + entry[5]
        val existing = posDict[leftId.toInt()]
        posDict[leftId.toInt()] = fullPOSData

        val morphemes: MutableList<KoMorphData.Morpheme> = ArrayList()
        var hasSinglePOS = (leftPOS == rightPOS)
        if (posType != POS.Type.MORPHEME && expression.isNotEmpty()) {
            val exprTokens = expression.split("+")
            for (exprToken in exprTokens) {
                val tokenSplit = exprToken.split("/")
                val surfaceForm = tokenSplit[0].trim()
                if (surfaceForm.isNotEmpty()) {
                    val exprTag = POS.resolveTag(tokenSplit[1])
                    morphemes.add(KoMorphData.Morpheme(exprTag, tokenSplit[0]))
                    if (leftPOS != exprTag) {
                        hasSinglePOS = false
                    }
                }
            }
        }

        var flags = 0
        if (hasSinglePOS) {
            flags = flags or TokenInfoMorphData.HAS_SINGLE_POS
        }
        if (posType == POS.Type.MORPHEME && reading.isNotEmpty()) {
            flags = flags or TokenInfoMorphData.HAS_READING
        }

        if (leftId >= ID_LIMIT) {
            throw IllegalArgumentException("leftId >= $ID_LIMIT: $leftId")
        }
        if (posType.ordinal >= 4) {
            throw IllegalArgumentException("posType.ordinal() >= 4: ${posType.name}")
        }
        buffer.putShort(((leftId.toInt() shl 2) or posType.ordinal).toShort())
        buffer.putShort(((rightId.toInt() shl 2) or flags).toShort())
        buffer.putShort(wordCost)

        if (posType == POS.Type.MORPHEME) {
            if (reading.isNotEmpty()) {
                writeString(reading)
            }
        } else {
            if (!hasSinglePOS) {
                buffer.put(rightPOS.ordinal.toByte())
            }
            buffer.put(morphemes.size.toByte())
            var compoundOffset = 0
            for (morpheme in morphemes) {
                if (!hasSinglePOS) {
                    buffer.put(morpheme.posTag.ordinal.toByte())
                }
                if (posType != POS.Type.INFLECT) {
                    buffer.put(morpheme.surfaceForm.length.toByte())
                    compoundOffset += morpheme.surfaceForm.length
                } else {
                    writeString(morpheme.surfaceForm)
                }
                require(compoundOffset <= entry[0].length) { entry.contentToString() }
            }
        }
        return buffer.position
    }

    private fun writeString(s: String) {
        buffer.put(s.length.toByte())
        for (i in s.indices) {
            buffer.putShort(s[i].code.toShort())
        }
    }

    @Throws(IOException::class)
    override fun writePosDict(bos: OutputStream, out: DataOutput) {
        out.writeVInt(posDict.size)
        for (s in posDict) {
            if (s == null) {
                out.writeByte(POS.Tag.UNKNOWN.ordinal.toByte())
            } else {
                val data = CSVUtil.parse(s)
                if (data.size != 2) {
                    throw IllegalArgumentException(
                        "Malformed pos/inflection: $s; expected 2 characters"
                    )
                }
                out.writeByte(POS.Tag.valueOf(data[0]).ordinal.toByte())
            }
        }
    }
}
